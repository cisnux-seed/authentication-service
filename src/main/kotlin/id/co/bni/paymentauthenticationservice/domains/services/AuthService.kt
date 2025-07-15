package id.co.bni.paymentauthenticationservice.domains.services

import id.co.bni.paymentauthenticationservice.applications.controllers.dtos.AuthResponse
import id.co.bni.paymentauthenticationservice.domains.dtos.TokenResponse
import id.co.bni.paymentauthenticationservice.domains.dtos.UserAuth
import id.co.bni.paymentauthenticationservice.domains.entities.User


interface AuthService {
    suspend fun authenticate(user: UserAuth): AuthResponse
    suspend fun register(user: User): String
    suspend fun refresh(refreshToken: String): TokenResponse?
    suspend fun logout(refreshToken: String)
}