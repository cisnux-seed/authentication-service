package id.co.bni.paymentauthenticationservice.applications.controllers.dtos

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
)
