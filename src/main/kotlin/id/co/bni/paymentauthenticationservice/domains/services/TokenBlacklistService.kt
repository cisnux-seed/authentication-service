package id.co.bni.paymentauthenticationservice.domains.services

interface TokenBlacklistService {
    suspend fun blacklistToken(token: String, ttlMinutes: Long = 1440) // 24 hours default
    suspend fun isTokenBlacklisted(token: String): Boolean
    suspend fun storeRefreshToken(token: String, userId: Long, ttlMinutes: Long = 1440)
    suspend fun isRefreshTokenValid(token: String): Boolean
    suspend fun removeRefreshToken(token: String)
}