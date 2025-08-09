package id.co.bni.paymentauthenticationservice.domains.repositories

interface CacheRepository {
    suspend fun <T> get(key: String, type: Class<T>): T?
    suspend fun set(key: String, value: Any, ttlMinutes: Long = 30)
    suspend fun delete(key: String): Boolean
    suspend fun isExists(key: String): Boolean
}