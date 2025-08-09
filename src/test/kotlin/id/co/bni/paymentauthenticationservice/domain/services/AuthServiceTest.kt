package id.co.bni.paymentauthenticationservice.domain.services

import id.co.bni.paymentauthenticationservice.commons.configs.JwtProperties
import id.co.bni.paymentauthenticationservice.commons.exceptions.APIException
import id.co.bni.paymentauthenticationservice.domains.dtos.UserAuth
import id.co.bni.paymentauthenticationservice.domains.entities.User
import id.co.bni.paymentauthenticationservice.domains.repositories.UserRepository
import id.co.bni.paymentauthenticationservice.domains.securities.TokenManager
import id.co.bni.paymentauthenticationservice.domains.services.AuthServiceImpl
import id.co.bni.paymentauthenticationservice.domains.services.TokenBlacklistService
import id.co.bni.paymentauthenticationservice.domains.services.UserService
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.security.SignatureException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.*

@ExtendWith(MockKExtension::class)
class AuthServiceTest {

    @MockK
    private lateinit var encoder: PasswordEncoder

    @MockK
    private lateinit var userService: UserService

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var tokenManager: TokenManager

    @MockK
    private lateinit var tokenBlacklistService: TokenBlacklistService

    @MockK
    private lateinit var jwtProperties: JwtProperties

    @InjectMockKs
    private lateinit var authService: AuthServiceImpl

    private val dummyUserAuth = UserAuth(username = "john", password = "password123")
    private val dummyUser = User(
        id = 1L,
        username = "john",
        phone = "123456789",
        email = "john@example.com",
        password = "encodedPassword"
    )
    private val dummyAccessToken = "access.token.here"
    private val dummyRefreshToken = "refresh.token.here"

    @Test
    fun `authenticate should return AuthResponse when credentials are valid`() = runTest {
        // arrange
        coEvery { userService.getByUsername("john") } returns dummyUser
        every { encoder.matches("password123", "encodedPassword") } returns true
        every { jwtProperties.accessSecret } returns "accessSecret"
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { jwtProperties.accessTokenExpiration } returns 3600000L
        every { jwtProperties.refreshTokenExpiration } returns 86400000L
        every {
            tokenManager.generate(
                secretKey = "accessSecret",
                user = dummyUserAuth,
                expirationDate = any()
            )
        } returns dummyAccessToken
        every {
            tokenManager.generate(
                secretKey = "refreshSecret",
                user = dummyUserAuth,
                expirationDate = any()
            )
        } returns dummyRefreshToken
        coEvery {
            tokenBlacklistService.storeRefreshToken(
                token = dummyRefreshToken,
                userId = 1L,
                ttlMinutes = 1440 // 86400000ms / 60000 = 1440 minutes
            )
        } returns Unit

        // act
        val result = authService.authenticate(dummyUserAuth)

        // assert
        assertEquals(dummyAccessToken, result.accessToken)
        assertEquals(dummyRefreshToken, result.refreshToken)
        coVerify(exactly = 1) { userService.getByUsername("john") }
        verify(exactly = 1) { encoder.matches("password123", "encodedPassword") }
        coVerify(exactly = 1) {
            tokenBlacklistService.storeRefreshToken(
                token = dummyRefreshToken,
                userId = 1L,
                ttlMinutes = 1440
            )
        }
        // Verify old PostgreSQL authentication repository is NOT called
        coVerify(exactly = 0) { userRepository.insert(any()) }
    }

    @Test
    fun `authenticate should throw UnauthenticatedException when user not found`() = runTest {
        // arrange
        coEvery { userService.getByUsername("notfound") } returns null

        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.authenticate(UserAuth(username = "notfound", password = "password123"))
        }

        coVerify(exactly = 1) { userService.getByUsername("notfound") }
        coVerify(exactly = 0) { tokenBlacklistService.storeRefreshToken(any(), any(), any()) }
    }

    @Test
    fun `authenticate should throw UnauthenticatedException when password is wrong`() = runTest {
        // arrange
        val wrongUserAuth = UserAuth(username = "john", password = "wrongpassword")
        coEvery { userService.getByUsername("john") } returns dummyUser
        every { encoder.matches("wrongpassword", "encodedPassword") } returns false

        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.authenticate(wrongUserAuth)
        }

        coVerify(exactly = 1) { userService.getByUsername("john") }
        verify(exactly = 1) { encoder.matches("wrongpassword", "encodedPassword") }
        coVerify(exactly = 0) { tokenBlacklistService.storeRefreshToken(any(), any(), any()) }
    }

    @Test
    fun `authenticate should handle token storage failures gracefully`() = runTest {
        // arrange
        coEvery { userService.getByUsername("john") } returns dummyUser
        every { encoder.matches("password123", "encodedPassword") } returns true
        every { jwtProperties.accessSecret } returns "accessSecret"
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { jwtProperties.accessTokenExpiration } returns 3600000L
        every { jwtProperties.refreshTokenExpiration } returns 86400000L
        every { tokenManager.generate(any(), any<UserAuth>(), any()) } returns "token"
        coEvery {
            tokenBlacklistService.storeRefreshToken(any(), any(), any())
        } throws RuntimeException("Redis connection failed")

        // act & assert - should propagate the error
        assertThrows<RuntimeException> {
            authService.authenticate(dummyUserAuth)
        }

        coVerify(exactly = 1) { tokenBlacklistService.storeRefreshToken(any(), any(), any()) }
    }

    @Test
    fun `register should return email when user registration is successful`() = runTest {
        // arrange
        val newUser = User(
            username = "newuser",
            phone = "123456789",
            email = "newuser@example.com",
            password = "password123"
        )
        val encodedUser = newUser.copy(password = "encodedPassword")
        val savedUser = encodedUser.copy(id = 1L)

        coEvery { userService.isUsernameAvailable("newuser") } returns true
        coEvery { userService.isEmailAvailable("newuser@example.com") } returns true
        every { encoder.encode("password123") } returns "encodedPassword"
        coEvery { userRepository.insert(encodedUser) } returns savedUser

        // act
        val result = authService.register(newUser)

        // assert
        assertEquals("newuser@example.com", result)
        coVerify(exactly = 1) { userService.isUsernameAvailable("newuser") }
        coVerify(exactly = 1) { userService.isEmailAvailable("newuser@example.com") }
        verify(exactly = 1) { encoder.encode("password123") }
        coVerify(exactly = 1) { userRepository.insert(encodedUser) }
    }

    @Test
    fun `register should throw UnauthenticatedException when username already exists`() = runTest {
        // arrange
        val newUser = User(
            username = "existinguser",
            phone = "123456789",
            email = "new@example.com",
            password = "password123"
        )

        coEvery { userService.isUsernameAvailable("existinguser") } returns false

        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.register(newUser)
        }

        coVerify(exactly = 1) { userService.isUsernameAvailable("existinguser") }
        coVerify(exactly = 0) { userService.isEmailAvailable(any()) }
    }

    @Test
    fun `register should throw UnauthenticatedException when email already exists`() = runTest {
        // arrange
        val newUser = User(
            username = "newuser",
            phone = "123456789",
            email = "existing@example.com",
            password = "password123"
        )

        coEvery { userService.isUsernameAvailable("newuser") } returns true
        coEvery { userService.isEmailAvailable("existing@example.com") } returns false

        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.register(newUser)
        }

        coVerify(exactly = 1) { userService.isUsernameAvailable("newuser") }
        coVerify(exactly = 1) { userService.isEmailAvailable("existing@example.com") }
    }

    @Test
    fun `register should throw InternalServerException when user insert fails`() = runTest {
        // arrange
        val newUser = User(
            username = "newuser",
            phone = "123456789",
            email = "new@example.com",
            password = "password123"
        )
        val encodedUser = newUser.copy(password = "encodedPassword")

        coEvery { userService.isUsernameAvailable("newuser") } returns true
        coEvery { userService.isEmailAvailable("new@example.com") } returns true
        every { encoder.encode("password123") } returns "encodedPassword"
        coEvery { userRepository.insert(encodedUser) } returns null

        // act & assert
        assertThrows<APIException.InternalServerException> {
            authService.register(newUser)
        }
    }

    @Test
    fun `refresh should return TokenResponse when refresh token is valid`() = runTest {
        // arrange
        val refreshToken = "valid.refresh.token"
        val username = "john"
        val accessToken = "new.access.token"

        coEvery { tokenBlacklistService.isTokenBlacklisted(refreshToken) } returns false
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { jwtProperties.accessSecret } returns "accessSecret"
        every { jwtProperties.accessTokenExpiration } returns 3600000L
        every { tokenManager.extractUsername("refreshSecret", refreshToken) } returns username
        coEvery { userService.getByUsername(username) } returns dummyUser
        coEvery { tokenBlacklistService.isRefreshTokenValid(refreshToken) } returns true
        every {
            tokenManager.isValid("refreshSecret", refreshToken, dummyUser)
        } returns true
        every {
            tokenManager.generate(
                secretKey = "accessSecret",
                user = dummyUser,
                expirationDate = any()
            )
        } returns accessToken

        // act
        val result = authService.refresh(refreshToken)

        // assert
        assertNotNull(result)
        assertEquals(accessToken, result.accessToken)
        coVerify(exactly = 1) { tokenBlacklistService.isTokenBlacklisted(refreshToken) }
        coVerify(exactly = 1) { tokenBlacklistService.isRefreshTokenValid(refreshToken) }
    }

    @Test
    fun `refresh should throw UnauthenticatedException when token is blacklisted`() = runTest {
        // arrange
        val refreshToken = "blacklisted.token"
        coEvery { tokenBlacklistService.isTokenBlacklisted(refreshToken) } returns true

        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }

        coVerify(exactly = 1) { tokenBlacklistService.isTokenBlacklisted(refreshToken) }
        // Should not proceed to other validations
        coVerify(exactly = 0) { tokenBlacklistService.isRefreshTokenValid(any()) }
    }

    @Test
    fun `refresh should throw UnauthenticatedException when user not found`() = runTest {
        // arrange
        val refreshToken = "valid.refresh.token"
        val username = "notfound"

        coEvery { tokenBlacklistService.isTokenBlacklisted(refreshToken) } returns false
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { tokenManager.extractUsername("refreshSecret", refreshToken) } returns username
        coEvery { userService.getByUsername(username) } returns null

        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }

        coVerify(exactly = 1) { tokenBlacklistService.isTokenBlacklisted(refreshToken) }
        coVerify(exactly = 1) { userService.getByUsername(username) }
    }

    @Test
    fun `refresh should throw UnauthenticatedException when token payload is absent`() = runTest {
        // arrange
        val refreshToken = "invalid.payload.token"

        coEvery { tokenBlacklistService.isTokenBlacklisted(refreshToken) } returns false
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { tokenManager.extractUsername("refreshSecret", refreshToken) } returns null

        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }

        coVerify(exactly = 1) { tokenBlacklistService.isTokenBlacklisted(refreshToken) }
    }

    @Test
    fun `refresh should throw UnauthenticatedException when refresh token is not valid in Redis`() = runTest {
        // arrange
        val refreshToken = "invalid.redis.token"
        val username = "john"

        coEvery { tokenBlacklistService.isTokenBlacklisted(refreshToken) } returns false
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { tokenManager.extractUsername("refreshSecret", refreshToken) } returns username
        coEvery { userService.getByUsername(username) } returns dummyUser
        coEvery { tokenBlacklistService.isRefreshTokenValid(refreshToken) } returns false

        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }

        coVerify(exactly = 1) { tokenBlacklistService.isTokenBlacklisted(refreshToken) }
        coVerify(exactly = 1) { tokenBlacklistService.isRefreshTokenValid(refreshToken) }
    }

    @Test
    fun `refresh should throw UnauthenticatedException when JWT token is invalid`() = runTest {
        // arrange
        val refreshToken = "invalid.jwt.token"
        val username = "john"

        coEvery { tokenBlacklistService.isTokenBlacklisted(refreshToken) } returns false
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { tokenManager.extractUsername("refreshSecret", refreshToken) } returns username
        coEvery { userService.getByUsername(username) } returns dummyUser
        coEvery { tokenBlacklistService.isRefreshTokenValid(refreshToken) } returns true
        every {
            tokenManager.isValid("refreshSecret", refreshToken, dummyUser)
        } returns false

        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }

        coVerify(exactly = 1) { tokenBlacklistService.isRefreshTokenValid(refreshToken) }
        verify(exactly = 1) { tokenManager.isValid("refreshSecret", refreshToken, dummyUser) }
    }

    @Test
    fun `refresh should throw UnauthenticatedException when username mismatch`() = runTest {
        // arrange
        val refreshToken = "valid.token.wrong.user"
        val tokenUsername = "alice"
        val userFromDb = dummyUser.copy(username = "john") // Different username

        coEvery { tokenBlacklistService.isTokenBlacklisted(refreshToken) } returns false
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { tokenManager.extractUsername("refreshSecret", refreshToken) } returns tokenUsername
        coEvery { userService.getByUsername(tokenUsername) } returns userFromDb
        coEvery { tokenBlacklistService.isRefreshTokenValid(refreshToken) } returns true
        every {
            tokenManager.isValid("refreshSecret", refreshToken, userFromDb)
        } returns true

        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }

        // Username mismatch: tokenUsername="alice" vs userFromDb.username="john"
    }

    @Test
    fun `refresh should throw UnauthenticatedException when token is expired`() = runTest {
        // arrange
        val refreshToken = "expired.token"

        coEvery { tokenBlacklistService.isTokenBlacklisted(refreshToken) } returns false
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every {
            tokenManager.extractUsername("refreshSecret", refreshToken)
        } throws ExpiredJwtException(null, null, "Token expired")

        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }

        coVerify(exactly = 1) { tokenBlacklistService.isTokenBlacklisted(refreshToken) }
    }

    @Test
    fun `refresh should throw UnauthenticatedException when token is malformed`() = runTest {
        // arrange
        val refreshToken = "malformed.token"

        coEvery { tokenBlacklistService.isTokenBlacklisted(refreshToken) } returns false
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every {
            tokenManager.extractUsername("refreshSecret", refreshToken)
        } throws MalformedJwtException("Malformed token")

        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }

        coVerify(exactly = 1) { tokenBlacklistService.isTokenBlacklisted(refreshToken) }
    }

    @Test
    fun `refresh should throw UnauthenticatedException when token signature is invalid`() = runTest {
        // arrange
        val refreshToken = "invalid.signature.token"

        coEvery { tokenBlacklistService.isTokenBlacklisted(refreshToken) } returns false
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every {
            tokenManager.extractUsername("refreshSecret", refreshToken)
        } throws SignatureException("Invalid signature")

        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }

        coVerify(exactly = 1) { tokenBlacklistService.isTokenBlacklisted(refreshToken) }
    }

    @Test
    fun `logout should blacklist token and remove from active tokens`() = runTest {
        // arrange
        val refreshToken = "token.to.logout"
        every { jwtProperties.refreshTokenExpiration } returns 86400000L
        coEvery { tokenBlacklistService.blacklistToken(refreshToken, 1440) } returns Unit
        coEvery { tokenBlacklistService.removeRefreshToken(refreshToken) } returns Unit

        // act
        authService.logout(refreshToken)

        // assert - both operations should be called
        coVerify(exactly = 1) { tokenBlacklistService.blacklistToken(refreshToken, 1440) }
        coVerify(exactly = 1) { tokenBlacklistService.removeRefreshToken(refreshToken) }
    }

    @Test
    fun `logout should handle blacklist service errors gracefully`() = runTest {
        // arrange
        val refreshToken = "error.token"
        every { jwtProperties.refreshTokenExpiration } returns 86400000L
        coEvery { tokenBlacklistService.blacklistToken(any(), any()) } throws RuntimeException("Blacklist failed")
        coEvery { tokenBlacklistService.removeRefreshToken(any()) } returns Unit

        // act & assert - should not throw exception even if blacklist service fails
        assertDoesNotThrow {
            authService.logout(refreshToken)
        }
    }

    @Test
    fun `logout should calculate correct TTL for blacklisting`() = runTest {
        // arrange
        val refreshToken = "test.token"
        val refreshTokenExpirationMs = 7200000L // 2 hours in ms
        val expectedTtlMinutes = 120L // 2 hours in minutes

        every { jwtProperties.refreshTokenExpiration } returns refreshTokenExpirationMs
        coEvery { tokenBlacklistService.blacklistToken(refreshToken, expectedTtlMinutes) } returns Unit
        coEvery { tokenBlacklistService.removeRefreshToken(refreshToken) } returns Unit

        // act
        authService.logout(refreshToken)

        // assert - verify TTL calculation: 7200000ms / 60000 = 120 minutes
        coVerify(exactly = 1) { tokenBlacklistService.blacklistToken(refreshToken, expectedTtlMinutes) }
        coVerify(exactly = 1) { tokenBlacklistService.removeRefreshToken(refreshToken) }
    }

    @Test
    fun `logout should continue removing token even if blacklisting fails`() = runTest {
        // arrange
        val refreshToken = "partial.failure.token"
        every { jwtProperties.refreshTokenExpiration } returns 86400000L
        coEvery { tokenBlacklistService.blacklistToken(any(), any()) } throws RuntimeException("Blacklist failed")
        coEvery { tokenBlacklistService.removeRefreshToken(refreshToken) } returns Unit

        // act
        authService.logout(refreshToken)

        // assert - removeRefreshToken should still be called despite blacklist failure
        coVerify(exactly = 1) { tokenBlacklistService.blacklistToken(any(), any()) }
        coVerify(exactly = 1) { tokenBlacklistService.removeRefreshToken(refreshToken) }
    }

    @Test
    fun `authenticate should use cached user service`() = runTest {
        // arrange
        coEvery { userService.getByUsername("john") } returns dummyUser
        every { encoder.matches("password123", "encodedPassword") } returns true
        every { jwtProperties.accessSecret } returns "accessSecret"
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { jwtProperties.accessTokenExpiration } returns 3600000L
        every { jwtProperties.refreshTokenExpiration } returns 86400000L
        every { tokenManager.generate(any(), any<UserAuth>(), any()) } returns "token"
        coEvery { tokenBlacklistService.storeRefreshToken(any(), any(), any()) } returns Unit

        // act
        authService.authenticate(dummyUserAuth)

        // assert - uses UserService (which has caching) instead of UserRepository directly
        coVerify(exactly = 1) { userService.getByUsername("john") }
        coVerify(exactly = 0) { userRepository.findByUsername(any()) }
    }

    @Test
    fun `refresh should use cached user service`() = runTest {
        // arrange
        val refreshToken = "valid.refresh.token"
        val username = "john"

        coEvery { tokenBlacklistService.isTokenBlacklisted(refreshToken) } returns false
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { jwtProperties.accessSecret } returns "accessSecret"
        every { jwtProperties.accessTokenExpiration } returns 3600000L
        every { tokenManager.extractUsername("refreshSecret", refreshToken) } returns username
        coEvery { userService.getByUsername(username) } returns dummyUser
        coEvery { tokenBlacklistService.isRefreshTokenValid(refreshToken) } returns true
        every { tokenManager.isValid("refreshSecret", refreshToken, dummyUser) } returns true
        every { tokenManager.generate(any(), any<User>(), any()) } returns "new.token"

        // act
        authService.refresh(refreshToken)

        // assert - uses UserService (which has caching) instead of UserRepository directly
        coVerify(exactly = 1) { userService.getByUsername(username) }
        coVerify(exactly = 0) { userRepository.findByUsername(any()) }
    }

    @Test
    fun `authenticate should calculate correct TTL for refresh token storage`() = runTest {
        // arrange
        val refreshTokenExpirationMs = 86400000L // 24 hours in ms
        val expectedTtlMinutes = 1440L // 24 hours in minutes

        coEvery { userService.getByUsername("john") } returns dummyUser
        every { encoder.matches("password123", "encodedPassword") } returns true
        every { jwtProperties.accessSecret } returns "accessSecret"
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { jwtProperties.accessTokenExpiration } returns 3600000L
        every { jwtProperties.refreshTokenExpiration } returns refreshTokenExpirationMs
        every { tokenManager.generate(any(), any<UserAuth>(), any()) } returns "token"
        coEvery {
            tokenBlacklistService.storeRefreshToken(any(), any(), expectedTtlMinutes)
        } returns Unit

        // act
        authService.authenticate(dummyUserAuth)

        // assert - verify TTL calculation: 86400000ms / 60000 = 1440 minutes
        coVerify(exactly = 1) {
            tokenBlacklistService.storeRefreshToken(any(), any(), expectedTtlMinutes)
        }
    }
}