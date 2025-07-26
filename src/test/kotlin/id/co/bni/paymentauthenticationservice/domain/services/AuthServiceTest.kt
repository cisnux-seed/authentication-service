package id.co.bni.paymentauthenticationservice.domain.services

import id.co.bni.paymentauthenticationservice.commons.configs.JwtProperties
import id.co.bni.paymentauthenticationservice.commons.exceptions.APIException
import id.co.bni.paymentauthenticationservice.domains.dtos.UserAuth
import id.co.bni.paymentauthenticationservice.domains.entities.Authentication
import id.co.bni.paymentauthenticationservice.domains.entities.User
import id.co.bni.paymentauthenticationservice.domains.repositories.AuthenticationRepository
import id.co.bni.paymentauthenticationservice.domains.repositories.UserRepository
import id.co.bni.paymentauthenticationservice.domains.securities.TokenManager
import id.co.bni.paymentauthenticationservice.domains.services.AuthServiceImpl
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
    private lateinit var authenticationRepository: AuthenticationRepository
    
    @MockK
    private lateinit var jwtProperties: JwtProperties
    
    @InjectMockKs
    private lateinit var authService: AuthServiceImpl
    
    @Test
    fun `authenticate should return AuthResponse when credentials are valid`() = runTest {
        // arrange
        val userAuth = UserAuth(username = "john", password = "password123")
        val existedUser = User(
            id = 1L,
            username = "john",
            phone = "123456789",
            email = "john@example.com",
            password = "encodedPassword"
        )
        val accessToken = "access.token.here"
        val refreshToken = "refresh.token.here"
        val authentication = Authentication(token = refreshToken, userId = 1L)
        
        coEvery { userService.getByUsername("john") } returns existedUser
        every { encoder.matches("password123", "encodedPassword") } returns true
        every { jwtProperties.accessSecret } returns "accessSecret"
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { jwtProperties.accessTokenExpiration } returns 3600000L
        every { jwtProperties.refreshTokenExpiration } returns 86400000L
        every { 
            tokenManager.generate(
                secretKey = "accessSecret",
                user = userAuth,
                expirationDate = any()
            ) 
        } returns accessToken
        every { 
            tokenManager.generate(
                secretKey = "refreshSecret",
                user = userAuth,
                expirationDate = any()
            ) 
        } returns refreshToken
        coEvery { authenticationRepository.insert(any()) } returns authentication
        
        // act
        val result = authService.authenticate(userAuth)
        
        // assert
        assertEquals(accessToken, result.accessToken)
        assertEquals(refreshToken, result.refreshToken)
        coVerify { userService.getByUsername("john") }
        verify { encoder.matches("password123", "encodedPassword") }
        coVerify { authenticationRepository.insert(any()) }
    }
    
    @Test
    fun `authenticate should throw UnauthenticatedException when user not found`() = runTest {
        // arrange
        val userAuth = UserAuth(username = "notfound", password = "password123")
        coEvery { userService.getByUsername("notfound") } returns null
        
        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.authenticate(userAuth)
        }
    }
    
    @Test
    fun `authenticate should throw UnauthenticatedException when password is wrong`() = runTest {
        // arrange
        val userAuth = UserAuth(username = "john", password = "wrongpassword")
        val existedUser = User(
            id = 1L,
            username = "john",
            phone = "123456789",
            email = "john@example.com",
            password = "encodedPassword"
        )
        
        coEvery { userService.getByUsername("john") } returns existedUser
        every { encoder.matches("wrongpassword", "encodedPassword") } returns false
        
        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.authenticate(userAuth)
        }
    }
    
    @Test
    fun `authenticate should throw InternalServerException when authentication insert fails`() = runTest {
        // arrange
        val userAuth = UserAuth(username = "john", password = "password123")
        val existedUser = User(
            id = 1L,
            username = "john",
            phone = "123456789",
            email = "john@example.com",
            password = "encodedPassword"
        )
        
        coEvery { userService.getByUsername("john") } returns existedUser
        every { encoder.matches("password123", "encodedPassword") } returns true
        every { jwtProperties.accessSecret } returns "accessSecret"
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { jwtProperties.accessTokenExpiration } returns 3600000L
        every { jwtProperties.refreshTokenExpiration } returns 86400000L
        every { tokenManager.generate(any(), any<UserAuth>(), any()) } returns "token"
        coEvery { authenticationRepository.insert(any()) } returns null
        
        // act & assert
        assertThrows<APIException.InternalServerException> {
            authService.authenticate(userAuth)
        }
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
        coVerify { userService.isUsernameAvailable("newuser") }
        coVerify { userService.isEmailAvailable("newuser@example.com") }
        verify { encoder.encode("password123") }
        coVerify { userRepository.insert(encodedUser) }
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
        val currentUser = User(
            id = 1L,
            username = "john",
            phone = "123456789",
            email = "john@example.com",
            password = "password"
        )
        val accessToken = "new.access.token"
        
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { jwtProperties.accessSecret } returns "accessSecret"
        every { jwtProperties.accessTokenExpiration } returns 3600000L
        every { tokenManager.extractUsername("refreshSecret", refreshToken) } returns username
        coEvery { userService.getByUsername(username) } returns currentUser
        coEvery { authenticationRepository.isExists(refreshToken) } returns true
        every { 
            tokenManager.isValid("refreshSecret", refreshToken, currentUser) 
        } returns true
        every { 
            tokenManager.generate(
                secretKey = "accessSecret",
                user = currentUser,
                expirationDate = any()
            ) 
        } returns accessToken
        
        // act
        val result = authService.refresh(refreshToken)
        
        // assert
        assertNotNull(result)
        assertEquals(accessToken, result.accessToken)
    }
    
    @Test
    fun `refresh should throw UnauthenticatedException when user not found`() = runTest {
        // arrange
        val refreshToken = "valid.refresh.token"
        val username = "notfound"
        
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { tokenManager.extractUsername("refreshSecret", refreshToken) } returns username
        coEvery { userService.getByUsername(username) } returns null
        
        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }
    }

    @Test
    fun `refresh should throw UnauthenticatedException when token payload is absent`() = runTest {
        // arrange
        val refreshToken = "valid.refresh.token"

        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { tokenManager.extractUsername("refreshSecret", refreshToken) } returns null

        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }
    }
    
    @Test
    fun `refresh should throw UnauthenticatedException when refresh token does not exist`() = runTest {
        // arrange
        val refreshToken = "nonexistent.token"
        val username = "john"
        val currentUser = User(
            id = 1L,
            username = "john",
            phone = "123456789",
            email = "john@example.com",
            password = "password"
        )
        
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { tokenManager.extractUsername("refreshSecret", refreshToken) } returns username
        coEvery { userService.getByUsername(username) } returns currentUser
        coEvery { authenticationRepository.isExists(refreshToken) } returns false
        
        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }
    }
    
    @Test
    fun `refresh should throw UnauthenticatedException when token is invalid`() = runTest {
        // arrange
        val refreshToken = "invalid.token"
        val username = "john"
        val currentUser = User(
            id = 1L,
            username = "john",
            phone = "123456789",
            email = "john@example.com",
            password = "password"
        )
        
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { tokenManager.extractUsername("refreshSecret", refreshToken) } returns username
        coEvery { userService.getByUsername(username) } returns currentUser
        coEvery { authenticationRepository.isExists(refreshToken) } returns true
        every { 
            tokenManager.isValid("refreshSecret", refreshToken, currentUser) 
        } returns false
        
        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }
    }
    
    @Test
    fun `refresh should throw UnauthenticatedException when token is expired`() = runTest {
        // arrange
        val refreshToken = "expired.token"
        
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { 
            tokenManager.extractUsername("refreshSecret", refreshToken) 
        } throws ExpiredJwtException(null, null, "Token expired")
        
        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }
    }
    
    @Test
    fun `refresh should throw UnauthenticatedException when token is malformed`() = runTest {
        // arrange
        val refreshToken = "malformed.token"
        
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { 
            tokenManager.extractUsername("refreshSecret", refreshToken) 
        } throws MalformedJwtException("Malformed token")
        
        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }
    }
    
    @Test
    fun `refresh should throw UnauthenticatedException when token signature is invalid`() = runTest {
        // arrange
        val refreshToken = "invalid.signature.token"
        
        every { jwtProperties.refreshSecret } returns "refreshSecret"
        every { 
            tokenManager.extractUsername("refreshSecret", refreshToken) 
        } throws SignatureException("Invalid signature")
        
        // act & assert
        assertThrows<APIException.UnauthenticatedException> {
            authService.refresh(refreshToken)
        }
    }
    
    @Test
    fun `logout should call deleteById on authentication repository`() = runTest {
        // arrange
        val refreshToken = "token.to.delete"
        coEvery { authenticationRepository.deleteById(refreshToken) } returns Unit
        
        // act
        authService.logout(refreshToken)
        
        // assert
        coVerify { authenticationRepository.deleteById(refreshToken) }
    }
}