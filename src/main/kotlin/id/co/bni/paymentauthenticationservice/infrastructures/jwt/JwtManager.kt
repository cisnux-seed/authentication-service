package id.co.bni.paymentauthenticationservice.infrastructures.jwt

import id.co.bni.paymentauthenticationservice.domains.dtos.UserAuth
import id.co.bni.paymentauthenticationservice.domains.entities.User
import id.co.bni.paymentauthenticationservice.domains.securities.TokenManager
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date

@Service
class JwtManager : TokenManager {
    override fun generate(
        secretKey: String,
        user: UserAuth,
        expirationDate: Instant,
        additionalClaims: Map<String, Any>
    ): String {
        val secretKeyBytes = secretKey.toByteArray()
        val secret = Keys.hmacShaKeyFor(secretKeyBytes)
        val allClaims = additionalClaims.toMutableMap()
        allClaims["sub"] = user.username
        allClaims["iss"] = "https://bni.co.id"
        allClaims["iat"] = Date.from(Instant.now())
        allClaims["exp"] = Date.from(expirationDate)

        return Jwts.builder()
            .claims(allClaims)
            .signWith(secret)
            .compact()
    }

    override fun generate(
        secretKey: String,
        user: User,
        expirationDate: Instant,
        additionalClaims: Map<String, Any>
    ): String {
        val secretKeyBytes = secretKey.toByteArray()
        val secret = Keys.hmacShaKeyFor(secretKeyBytes)
        val allClaims = additionalClaims.toMutableMap()
        allClaims["sub"] = user.username
        allClaims["iss"] = "https://bni.co.id"
        allClaims["iat"] = Date.from(Instant.now())
        allClaims["exp"] = Date.from(expirationDate)

        return Jwts.builder()
            .claims(allClaims)
            .signWith(secret)
            .compact()
    }


    override fun isValid(
        secretKey: String,
        token: String,
        user: User
    ): Boolean {
        val username = extractUsername(secretKey, token)
        return username != null && username == user.username && !isExpired(secretKey, token)
    }

    override fun extractUsername(secretKey: String, token: String): String? = getAllClaims(secretKey, token)
        .subject

    private fun isExpired(secretKey: String, token: String): Boolean = getAllClaims(secretKey, token)
        .expiration
        .toInstant()
        .isBefore(Instant.now())

    private fun getAllClaims(secretKey: String, token: String): Claims {
        val secretKeyBytes = secretKey.toByteArray()
        val secret = Keys.hmacShaKeyFor(secretKeyBytes)
        val parser = Jwts.parser()
            .verifyWith(secret)
            .build()

        return parser
            .parseSignedClaims(token)
            .payload
    }
}