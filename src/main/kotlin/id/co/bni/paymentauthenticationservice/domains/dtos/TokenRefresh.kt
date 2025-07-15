package id.co.bni.paymentauthenticationservice.domains.dtos

import jakarta.validation.constraints.NotBlank

data class TokenRefresh(
    @field:NotBlank(message = "refreshToken cannot be blank")
    val refreshToken: String
)
