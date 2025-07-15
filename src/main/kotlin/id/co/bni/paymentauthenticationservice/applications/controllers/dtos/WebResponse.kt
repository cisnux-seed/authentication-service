package id.co.bni.paymentauthenticationservice.applications.controllers.dtos

data class WebResponse<out T>(
    val meta: MetaResponse,
    val data: T? = null,
)
