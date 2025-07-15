package id.co.bni.paymentauthenticationservice.domains.securities

import id.co.bni.paymentauthenticationservice.domains.dtos.UserAuth
import id.co.bni.paymentauthenticationservice.domains.entities.User
import java.time.Instant

interface TokenManager {
    fun generate(
        secretKey: String,
        user: UserAuth,
        expirationDate: Instant,
        additionalClaims: Map<String, Any> = emptyMap()
    ): String

    fun generate(
        secretKey: String,
        user: User,
        expirationDate: Instant,
        additionalClaims: Map<String, Any> = emptyMap()
    ): String

    fun isValid(secretKey: String, token: String, user: User): Boolean
    fun extractEmail(secretKey: String, token: String): String?
}