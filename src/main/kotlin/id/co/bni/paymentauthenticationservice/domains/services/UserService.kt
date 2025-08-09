package id.co.bni.paymentauthenticationservice.domains.services

import id.co.bni.paymentauthenticationservice.domains.entities.User

interface UserService {
    suspend fun getByUsername(username: String): User?
    suspend fun isUsernameAvailable(username: String): Boolean
    suspend fun isEmailAvailable(email: String): Boolean
    suspend fun invalidateUserCache(username: String)
}