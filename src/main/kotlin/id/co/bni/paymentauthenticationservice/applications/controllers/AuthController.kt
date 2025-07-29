package id.co.bni.paymentauthenticationservice.applications.controllers

import id.co.bni.paymentauthenticationservice.applications.controllers.dtos.AuthResponse
import id.co.bni.paymentauthenticationservice.applications.controllers.dtos.MetaResponse
import id.co.bni.paymentauthenticationservice.applications.controllers.dtos.WebResponse
import id.co.bni.paymentauthenticationservice.commons.configs.JwtProperties
import id.co.bni.paymentauthenticationservice.commons.logger.Loggable
import id.co.bni.paymentauthenticationservice.domains.dtos.TokenRefresh
import id.co.bni.paymentauthenticationservice.domains.dtos.TokenResponse
import id.co.bni.paymentauthenticationservice.domains.dtos.UserAuth
import id.co.bni.paymentauthenticationservice.domains.dtos.UserRegister
import id.co.bni.paymentauthenticationservice.domains.entities.User
import id.co.bni.paymentauthenticationservice.domains.services.AuthService
import id.co.bni.paymentauthenticationservice.domains.services.UserService
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.UUID

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val userService: UserService,
    private val jwtProperties: JwtProperties
    ) : Loggable {
    @PostMapping(
        "/register", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun register(
        @RequestBody @Validated userRegister: UserRegister
    ): WebResponse<String> {
        val traceId = UUID.randomUUID().toString()

        return withContext(
            MDCContext(mapOf("traceId" to traceId))
        ) {
            log.info("user registering: $userRegister")
            val user = User(
                username = userRegister.username, email = userRegister.email, password = userRegister.password,
                phone = userRegister.phone
            )
            log.info(userService.toString())
            val userId = authService.register(user)

            log.info("user registered successfully: $userId")

            WebResponse(
                meta = MetaResponse(
                    code = HttpStatus.CREATED.value().toString(), message = "user registered successfully"
                ), data = userId
            )
        }
    }


    @PostMapping(
        "/login", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun login(
        @RequestBody @Validated userAuth: UserAuth
    ): ResponseEntity<WebResponse<AuthResponse>> {
        val traceId = UUID.randomUUID().toString()

        return withContext(
            MDCContext(mapOf("traceId" to traceId))
        ) {
            log.info("user logging in: $userAuth")

            val authResp = authService.authenticate(userAuth)

            val accessCookie = ResponseCookie.from("auth-token", authResp.accessToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(Duration.ofMillis(jwtProperties.accessTokenExpiration))
                .path("/")
                .build()

            val refreshCookie = ResponseCookie.from("refresh-token", authResp.refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(Duration.ofMillis(jwtProperties.refreshTokenExpiration))
                .path("/")
                .build()

            log.info("user logged in successfully: ${authResp.accessToken}")

            ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(
                    WebResponse(
                        meta = MetaResponse(
                            code = HttpStatus.OK.value().toString(),
                            message = "user logged in successfully 👍🏻👍🏻🫡"
                        ), data = authResp
                    )
                )
        }
    }

    @PutMapping(
        "/refresh", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun refresh(
        @RequestBody @Validated tokenRefresh: TokenRefresh
    ): ResponseEntity<WebResponse<TokenResponse>> {
        val traceId = UUID.randomUUID().toString()

        return withContext(
            MDCContext(mapOf("traceId" to traceId))
        ) {
            log.info("user refreshing token: ${tokenRefresh.refreshToken}")

            val tokenResp = authService.refresh(tokenRefresh.refreshToken)

            val accessCookie = ResponseCookie.from("auth-token", tokenResp.accessToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .maxAge(Duration.ofMinutes(15))
                .path("/")
                .build()

            ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .body(
                    WebResponse(
                        meta = MetaResponse(
                            code = HttpStatus.OK.value().toString(),
                            message = "refresh token successfully"
                        ), data = tokenResp
                    )
                )
        }
    }

    @DeleteMapping(
        "/logout", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    suspend fun logout(
        @RequestBody @Validated tokenRefresh: TokenRefresh
    ): WebResponse<String> {
        val traceId = UUID.randomUUID().toString()

        return withContext(MDCContext(mapOf("traceId" to traceId))) {
            log.info("user logging out: ${tokenRefresh.refreshToken}")

            authService.logout(tokenRefresh.refreshToken)

            WebResponse(
                meta = MetaResponse(
                    code = HttpStatus.OK.value().toString(), message = "user logged out successfully"
                ), data = "user logged out successfully"
            )
        }
    }
}
