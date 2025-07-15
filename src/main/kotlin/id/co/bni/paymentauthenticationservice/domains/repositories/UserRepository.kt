package id.co.bni.paymentauthenticationservice.domains.repositories

import id.co.bni.paymentauthenticationservice.domains.entities.User

interface UserRepository {
    suspend fun findByEmail(email: String): User?
    suspend fun findByUsername(username: String): User?
    suspend fun insert(user: User): User?
    suspend fun update(user: User): User?
    suspend fun isUsernameExists(username: String): Boolean
    suspend fun isEmailExists(email: String): Boolean
}