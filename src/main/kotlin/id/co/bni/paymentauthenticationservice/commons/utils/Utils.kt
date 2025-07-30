package id.co.bni.paymentauthenticationservice.commons.utils

import id.co.bni.paymentauthenticationservice.commons.exceptions.APIException
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import kotlin.collections.firstOrNull

fun extractTokenFromJWT(request: ServerHttpRequest): String{
    println(request.headers.toString())
    val cookieToken = request.cookies["refresh-token"]?.firstOrNull()?.value
    if (cookieToken != null) {
        return cookieToken
    }

    throw APIException.UnauthenticatedException(HttpStatus.UNAUTHORIZED.value(), "no JWT found")
}