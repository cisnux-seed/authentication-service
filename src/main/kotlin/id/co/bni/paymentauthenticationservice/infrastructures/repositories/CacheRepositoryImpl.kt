@file:Suppress("UNCHECKED_CAST")

package id.co.bni.paymentauthenticationservice.infrastructures.repositories

import com.fasterxml.jackson.databind.ObjectMapper
import id.co.bni.paymentauthenticationservice.commons.exceptions.APIException
import id.co.bni.paymentauthenticationservice.commons.logger.Loggable
import id.co.bni.paymentauthenticationservice.domains.repositories.CacheRepository
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.setAndAwait
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class CacheRepositoryImpl(
    private val redisTemplate: ReactiveRedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) : CacheRepository, Loggable {

    override suspend fun <T> get(key: String, type: Class<T>): T? {
        return try {
            val value = redisTemplate.opsForValue()[key].awaitFirstOrNull()
            when {
                value == null -> null
                type.isAssignableFrom(value::class.java) -> value as T
                value is String -> objectMapper.readValue(value, type)
                else -> objectMapper.convertValue(value, type)
            }
        } catch (e: Exception) {
            log.error("Error getting cache key: $key", e)
            throw APIException.InternalServerException(
                message = "internal server error",
                statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value()
            )
        }
    }

    override suspend fun set(key: String, value: Any, ttlMinutes: Long) {
        try {
            redisTemplate.opsForValue()
                .setAndAwait(key, value, Duration.ofMinutes(ttlMinutes))
        } catch (e: Exception) {
            log.error("Error setting cache key: $key", e)
            throw APIException.InternalServerException(
                message = "internal server error",
                statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value()
            )
        }
    }

    override suspend fun delete(key: String): Boolean {
        return try {
            redisTemplate.delete(key).awaitSingle() > 0
        } catch (e: Exception) {
            log.error("Error deleting cache key: $key", e)
            false
        }
    }

    override suspend fun isExists(key: String): Boolean {
        return try {
            redisTemplate.hasKey(key).awaitSingle()
        } catch (e: Exception) {
            log.error("Error checking cache key existence: $key", e)
            false
        }
    }
}