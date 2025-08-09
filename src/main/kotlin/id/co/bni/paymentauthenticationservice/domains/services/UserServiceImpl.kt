package id.co.bni.paymentauthenticationservice.domains.services

import id.co.bni.paymentauthenticationservice.commons.constants.CacheKeys
import id.co.bni.paymentauthenticationservice.commons.logger.Loggable
import id.co.bni.paymentauthenticationservice.domains.entities.User
import id.co.bni.paymentauthenticationservice.domains.repositories.CacheRepository
import id.co.bni.paymentauthenticationservice.domains.repositories.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val cacheRepository: CacheRepository
) : UserService, Loggable {

    override suspend fun getByUsername(username: String): User? = withContext(USER_SCOPE) {
        val cacheKey = CacheKeys.userKey(username)
        var user = cacheRepository.get(cacheKey, User::class.java)

        if (user == null) {
            log.debug("User cache miss for username: $username")
            user = userRepository.findByUsername(username)

            if (user != null) {
                // Cache for 30 minutes
                cacheRepository.set(cacheKey, user, 30)
                log.debug("User cached for username: $username")
            }
        } else {
            log.debug("User cache hit for username: $username")
        }

        user
    }

    override suspend fun isUsernameAvailable(username: String): Boolean =
        !userRepository.isUsernameExists(username)

    override suspend fun isEmailAvailable(email: String): Boolean =
        !userRepository.isEmailExists(email)

    override suspend fun invalidateUserCache(username: String) {
        try {
            val cacheKey = CacheKeys.userKey(username)
            val deleted = cacheRepository.delete(cacheKey)
            log.info("User cache invalidation for username '$username': $deleted")
        } catch (e: Exception) {
            log.error("Failed to invalidate user cache for username: $username", e)
        }
    }

    private companion object{
        val USER_SCOPE = Dispatchers.IO
    }
}