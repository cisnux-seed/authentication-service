package id.co.bni.paymentauthenticationservice.domains.services

import id.co.bni.paymentauthenticationservice.applications.controllers.dtos.AuthResponse
import id.co.bni.paymentauthenticationservice.commons.configs.JwtProperties
import id.co.bni.paymentauthenticationservice.commons.exceptions.APIException
import id.co.bni.paymentauthenticationservice.commons.logger.Loggable
import id.co.bni.paymentauthenticationservice.domains.dtos.TokenResponse
import id.co.bni.paymentauthenticationservice.domains.dtos.UserAuth
import id.co.bni.paymentauthenticationservice.domains.entities.User
import id.co.bni.paymentauthenticationservice.domains.repositories.UserRepository
import id.co.bni.paymentauthenticationservice.domains.securities.TokenManager
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.security.SignatureException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AuthServiceImpl(
    private val encoder: PasswordEncoder,
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val tokenManager: TokenManager,
    private val tokenBlacklistService: TokenBlacklistService,
    private val jwtProperties: JwtProperties,
) : AuthService, Loggable {

    override suspend fun authenticate(user: UserAuth): AuthResponse = withContext(Dispatchers.Default) {
        val existedUser = userService.getByUsername(user.username)
            ?: throw APIException.UnauthenticatedException(message = INVALID_EMAIL_OR_PASSWORD)

        if (!encoder.matches(user.password, existedUser.password)) {
            throw APIException.UnauthenticatedException(message = INVALID_EMAIL_OR_PASSWORD)
        }

        val accessToken = tokenManager.generate(
            secretKey = jwtProperties.accessSecret,
            user = user,
            expirationDate = Instant.now().plusMillis(jwtProperties.accessTokenExpiration)
        )

        val refreshToken = tokenManager.generate(
            secretKey = jwtProperties.refreshSecret,
            user = user,
            expirationDate = Instant.now().plusMillis(jwtProperties.refreshTokenExpiration)
        )

        tokenBlacklistService.storeRefreshToken(
            token = refreshToken,
            userId = existedUser.id!!,
            ttlMinutes = jwtProperties.refreshTokenExpiration / 60000 // Convert to minutes
        )

        log.info("User authenticated successfully: ${user.username}")

        AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    override suspend fun register(user: User): String = withContext(Dispatchers.IO) {
        val isUsernameExists = userService.isUsernameAvailable(user.username)
        if (!isUsernameExists) {
            throw APIException.UnauthenticatedException(message = EMAIL_OR_USERNAME_ALREADY_EXIST)
        }

        val isEmailExists = userService.isEmailAvailable(user.email)
        if (!isEmailExists) {
            throw APIException.UnauthenticatedException(message = EMAIL_OR_USERNAME_ALREADY_EXIST)
        }

        val encodedUser = user.copy(password = encoder.encode(user.password))
        val savedUser = userRepository.insert(encodedUser)
            ?: throw APIException.InternalServerException(message = "failed to create user")

        log.info("User registered successfully: ${savedUser.email}")
        savedUser.email
    }

    override suspend fun refresh(refreshToken: String): TokenResponse = try {
        // Check if token is blacklisted first
        if (tokenBlacklistService.isTokenBlacklisted(refreshToken)) {
            throw APIException.UnauthenticatedException(message = TOKEN_EXPIRED_MESSAGE)
        }

        val username = tokenManager.extractUsername(secretKey = jwtProperties.refreshSecret, refreshToken)
        username?.let {
            val currentUser = userService.getByUsername(username)
                ?: throw APIException.UnauthenticatedException(message = TOKEN_EXPIRED_MESSAGE)

            // Check if refresh token exists in Redis and is valid
            val isRefreshTokenValid = withContext(Dispatchers.IO) {
                tokenBlacklistService.isRefreshTokenValid(refreshToken)
            }

            log.debug("refresh token validation: exists=$isRefreshTokenValid, username=$username, currentUser=${currentUser.username}")

            if (isRefreshTokenValid &&
                tokenManager.isValid(secretKey = jwtProperties.refreshSecret, refreshToken, currentUser) &&
                username == currentUser.username
            ) {
                val accessToken = tokenManager.generate(
                    secretKey = jwtProperties.accessSecret,
                    user = currentUser,
                    expirationDate = Instant.now().plusMillis(jwtProperties.accessTokenExpiration)
                )

                log.info("Token refreshed successfully for user: $username")
                TokenResponse(accessToken = accessToken)
            } else {
                throw APIException.UnauthenticatedException(message = TOKEN_EXPIRED_MESSAGE)
            }
        } ?: throw APIException.UnauthenticatedException(message = TOKEN_EXPIRED_MESSAGE)
    } catch (_: ExpiredJwtException) {
        throw APIException.UnauthenticatedException(message = "token is expired")
    } catch (_: MalformedJwtException) {
        throw APIException.UnauthenticatedException(message = "invalid token")
    } catch (_: SignatureException) {
        throw APIException.UnauthenticatedException(message = "invalid token signature")
    }

    override suspend fun logout(refreshToken: String) {
        supervisorScope {
            launch {
                try {
                    tokenBlacklistService.blacklistToken(
                        token = refreshToken,
                        ttlMinutes = jwtProperties.refreshTokenExpiration / 60000
                    )
                    log.debug("Token blacklisted successfully: ${refreshToken.take(10)}...")
                } catch (e: Exception) {
                    log.error("Error during blacklisting token: ${refreshToken.take(10)}...", e)
                }
            }

            launch {
                try {
                    tokenBlacklistService.removeRefreshToken(refreshToken)
                    log.debug("Token removed successfully: ${refreshToken.take(10)}...")
                } catch (e: Exception) {
                    log.error("Error during token removal: ${refreshToken.take(10)}...", e)
                }
            }
        }
    }

    private companion object {
        const val TOKEN_EXPIRED_MESSAGE = "token is invalid or expired"
        const val INVALID_EMAIL_OR_PASSWORD = "email or password is invalid"
        const val EMAIL_OR_USERNAME_ALREADY_EXIST = "username or email already exists"
    }
}