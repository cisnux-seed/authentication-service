package id.co.bni.paymentauthenticationservice.domains.services

import id.co.bni.paymentauthenticationservice.commons.constants.CacheKeys
import id.co.bni.paymentauthenticationservice.commons.logger.Loggable
import id.co.bni.paymentauthenticationservice.domains.repositories.CacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

@Service
class TokenBlacklistServiceImpl(
    private val cacheRepository: CacheRepository
) : TokenBlacklistService, Loggable {

    override suspend fun blacklistToken(token: String, ttlMinutes: Long) = withContext(TOKEN_BLACKLIST_SCOPE) {
        try {
            val blacklistKey = CacheKeys.blacklistedTokenKey(token)
            cacheRepository.set(blacklistKey, "blacklisted", ttlMinutes)
            log.info("Token blacklisted successfully: ${token.take(10)}...")
        } catch (e: Exception) {
            log.error("Failed to blacklist token: ${token.take(10)}...", e)
        }
    }

    override suspend fun isTokenBlacklisted(token: String): Boolean = withContext(TOKEN_BLACKLIST_SCOPE) {
        try {
            val blacklistKey = CacheKeys.blacklistedTokenKey(token)
            val isBlacklisted = cacheRepository.isExists(blacklistKey)
            log.debug("Token blacklist check for ${token.take(10)}...: $isBlacklisted")
            isBlacklisted
        } catch (e: Exception) {
            log.error("Error checking token blacklist status: ${token.take(10)}...", e)
            false
        }
    }

    override suspend fun storeRefreshToken(token: String, userId: Long, ttlMinutes: Long) = withContext(TOKEN_BLACKLIST_SCOPE) {
        try {
            val refreshTokenKey = CacheKeys.refreshTokenKey(token)
            val tokenData = mapOf(
                "userId" to userId,
                "createdAt" to System.currentTimeMillis(),
                "token" to token
            )
            cacheRepository.set(refreshTokenKey, tokenData, ttlMinutes)
            log.info("Refresh token stored successfully for user: $userId")
        } catch (e: Exception) {
            log.error("Failed to store refresh token for user: $userId", e)
        }
    }

    override suspend fun isRefreshTokenValid(token: String): Boolean = withContext(TOKEN_BLACKLIST_SCOPE) {
        try {
            val refreshTokenKey = CacheKeys.refreshTokenKey(token)
            val tokenExists = cacheRepository.isExists(refreshTokenKey)
            val isBlacklisted = isTokenBlacklisted(token)
            
            val isValid = tokenExists && !isBlacklisted
            log.debug("Refresh token validation for ${token.take(10)}...: exists=$tokenExists, blacklisted=$isBlacklisted, valid=$isValid")
            
            isValid
        } catch (e: Exception) {
            log.error("Error validating refresh token: ${token.take(10)}...", e)
            false
        }
    }

    override suspend fun removeRefreshToken(token: String) = withContext(TOKEN_BLACKLIST_SCOPE) {
        try {
            val refreshTokenKey = CacheKeys.refreshTokenKey(token)
            val deleted = cacheRepository.delete(refreshTokenKey)
            log.info("Refresh token removal for ${token.take(10)}...: $deleted")
        } catch (e: Exception) {
            log.error("Failed to remove refresh token: ${token.take(10)}...", e)
        }
    }

    private companion object {
        val TOKEN_BLACKLIST_SCOPE = Dispatchers.IO
    }
}