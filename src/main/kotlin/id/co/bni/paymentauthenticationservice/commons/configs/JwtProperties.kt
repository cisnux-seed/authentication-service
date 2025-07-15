package id.co.bni.paymentauthenticationservice.commons.configs

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val accessSecret: String,
    val refreshSecret: String,
    val accessTokenExpiration: Long,
    val refreshTokenExpiration: Long,
)
