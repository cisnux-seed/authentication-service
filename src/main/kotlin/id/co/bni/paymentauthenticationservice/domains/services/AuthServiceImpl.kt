package id.co.bni.paymentauthenticationservice.domains.services

import id.co.bni.paymentauthenticationservice.applications.controllers.dtos.AuthResponse
import id.co.bni.paymentauthenticationservice.commons.configs.JwtProperties
import id.co.bni.paymentauthenticationservice.commons.exceptions.APIException
import id.co.bni.paymentauthenticationservice.commons.logger.Loggable
import id.co.bni.paymentauthenticationservice.domains.dtos.TokenResponse
import id.co.bni.paymentauthenticationservice.domains.dtos.UserAuth
import id.co.bni.paymentauthenticationservice.domains.entities.Authentication
import id.co.bni.paymentauthenticationservice.domains.entities.User
import id.co.bni.paymentauthenticationservice.domains.repositories.AuthenticationRepository
import id.co.bni.paymentauthenticationservice.domains.repositories.UserRepository
import id.co.bni.paymentauthenticationservice.domains.securities.TokenManager
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.security.SignatureException
import kotlinx.coroutines.Dispatchers
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
    private val authenticationRepository: AuthenticationRepository,
    private val jwtProperties: JwtProperties,
) :
    AuthService, Loggable {
    override suspend fun authenticate(user: UserAuth): AuthResponse = withContext(Dispatchers.Default) {
        val existedUser = userService.getByUsername(user.username) ?: throw APIException.UnauthenticatedException(
            message = "invalid email or password"
        )
        if (!encoder.matches(user.password, existedUser.password)) {
            throw APIException.UnauthenticatedException(
                message = "invalid email or password"
            )
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

        val authentication = Authentication(
            token = refreshToken,
            userId = existedUser.id!!,
        )

        val auth = authenticationRepository.insert(authentication) ?: throw APIException.InternalServerException(
            message = "failed to create authentication token"
        )

        AuthResponse(
            accessToken = accessToken,
            refreshToken = auth.token,
        )
    }

    override suspend fun register(user: User): String = withContext(Dispatchers.IO) {
        val isUsernameExists = userService.isUsernameAvailable(user.username)
        if (!isUsernameExists) {
            throw APIException.UnauthenticatedException(
                message = "username or email already exists"
            )
        }

        val isEmailExists = userService.isEmailAvailable(user.email)
        if (!isEmailExists) {
            throw APIException.UnauthenticatedException(
                message = "username or email already exists"
            )
        }

        val encodedUser = user.copy(password = encoder.encode(user.password))
        userRepository.insert(encodedUser)?.email ?: throw APIException.InternalServerException(
            message = "failed to create user"
        )
    }

    override suspend fun refresh(refreshToken: String): TokenResponse? = try {
        val username = tokenManager.extractUsername(secretKey = jwtProperties.refreshSecret, refreshToken)
        username?.let {
            val currentUser = userService.getByUsername(username)
                ?: throw APIException.UnauthenticatedException(
                    message = TOKEN_EXPIRED_MESSAGE
                )
            val isRefreshTokenExists = withContext(Dispatchers.IO){
                authenticationRepository.isExists(refreshToken)
            }

            log.debug("refresh token exists: {}, email: {}, currentUser: {}", isRefreshTokenExists, username, currentUser)

            if (isRefreshTokenExists && tokenManager.isValid(
                    secretKey = jwtProperties.refreshSecret,
                    refreshToken,
                    currentUser
                ) && username == currentUser.username
            ) {
                val accessToken = tokenManager.generate(
                    secretKey = jwtProperties.accessSecret,
                    user = currentUser,
                    expirationDate = Instant.now().plusMillis(jwtProperties.accessTokenExpiration)
                )
                TokenResponse(
                    accessToken = accessToken,
                )
            } else {
                throw APIException.UnauthenticatedException(
                    message = TOKEN_EXPIRED_MESSAGE
                )
            }
        } ?: throw APIException.UnauthenticatedException(
            message = TOKEN_EXPIRED_MESSAGE
        )
    } catch (_: ExpiredJwtException) {
        throw APIException.UnauthenticatedException(
            message = "token is expired"
        )
    } catch (_: MalformedJwtException) {
        throw APIException.UnauthenticatedException(
            message = "invalid token"
        )
    } catch (_: SignatureException){
        throw APIException.UnauthenticatedException(
            message = "invalid token signature"
        )
    }


    override suspend fun logout(refreshToken: String): Unit =
        authenticationRepository.deleteById(refreshToken)

    private companion object{
        const val TOKEN_EXPIRED_MESSAGE = "token is invalid or expired"
    }

}