package id.co.bni.paymentauthenticationservice.domains.repositories

import id.co.bni.paymentauthenticationservice.domains.entities.Authentication

interface AuthenticationRepository{
    suspend fun insert(authentication: Authentication): Authentication?
    suspend fun deleteById(token: String)
    suspend fun findById(token: String): Authentication?
    suspend fun isExists(token: String): Boolean
}

