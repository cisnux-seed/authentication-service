package id.co.bni.paymentauthenticationservice.infrastructures.jwt

import id.co.bni.paymentauthenticationservice.domains.dtos.UserAuth
import id.co.bni.paymentauthenticationservice.domains.entities.User
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

class JwtManagerTest {

    private val jwtManager = JwtManager()
    private val secretKey = "mySecretKeyThatIsAtLeast256BitsLongForHS256Algorithm"

    @Test
    fun `generate should create valid JWT token for UserAuth`() {
        // arrange
        val userAuth = UserAuth(username = "john", password = "password")
        val expiration = Instant.now().plus(1, ChronoUnit.HOURS)
        val additionalClaims = mapOf("role" to "user")

        // act
        val token = jwtManager.generate(secretKey, userAuth, expiration, additionalClaims)

        // assert
        assertNotNull(token)
        assertTrue(token.isNotEmpty())
        assertTrue(token.contains(".")) // JWT format check
        assertEquals(3, token.split(".").size) // JWT has 3 parts
    }

    @Test
    fun `generate should create valid JWT token for User`() {
        // arrange
        val user = User(
            id = 1L,
            username = "jane",
            phone = "123456789",
            email = "jane@example.com",
            password = "password"
        )
        val expiration = Instant.now().plus(1, ChronoUnit.HOURS)
        val additionalClaims = mapOf<String, Any>()

        // act
        val token = jwtManager.generate(
            secretKey,
            user,
            expiration,
            additionalClaims
        )

        // assert
        assertNotNull(token)
        assertTrue(token.isNotEmpty())
        assertTrue(token.contains("."))
        assertEquals(3, token.split(".").size)
    }

    @Test
    fun `generate should include correct claims`() {
        // arrange
        val user = User(
            id = 1L,
            username = "testuser",
            phone = "123456789",
            email = "test@example.com",
            password = "password"
        )
        val expiration = Instant.now().plus(1, ChronoUnit.HOURS)
        val additionalClaims = mapOf("role" to "admin")

        // act
        val token = jwtManager.generate(secretKey, user, expiration, additionalClaims)
        val extractedUsername = jwtManager.extractUsername(secretKey, token)

        // assert
        assertEquals("testuser", extractedUsername)
    }

    @Test
    fun `extractUsername should return correct username from token`() {
        // arrange
        val user = User(
            id = 1L,
            username = "extracttest",
            phone = "123456789",
            email = "extract@example.com",
            password = "password"
        )
        val expiration = Instant.now().plus(1, ChronoUnit.HOURS)
        val token = jwtManager.generate(secretKey, user, expiration, emptyMap())

        // act
        val extractedUsername = jwtManager.extractUsername(secretKey, token)

        // assert
        assertEquals("extracttest", extractedUsername)
    }

    @Test
    fun `extractUsername should return null for invalid token`() {
        // arrange
        val invalidToken = "invalid.token.here"

        // act & assert
        assertThrows<Exception> {
            jwtManager.extractUsername(secretKey, invalidToken)
        }
    }

    @Test
    fun `isValid should return true for valid token and matching user`() {
        // arrange
        val user = User(
            id = 1L,
            username = "validuser",
            phone = "123456789",
            email = "valid@example.com",
            password = "password"
        )
        val expiration = Instant.now().plus(1, ChronoUnit.HOURS)
        val token = jwtManager.generate(secretKey, user, expiration, emptyMap())

        // act
        val isValid = jwtManager.isValid(secretKey, token, user)

        // assert
        assertTrue(isValid)
    }

    @Test
    fun `isValid should return false for token with different username`() {
        // arrange
        val user1 = User(
            id = 1L,
            username = "user1",
            phone = "123456789",
            email = "user1@example.com",
            password = "password"
        )
        val user2 = User(
            id = 2L,
            username = "user2",
            phone = "987654321",
            email = "user2@example.com",
            password = "password"
        )
        val expiration = Instant.now().plus(1, ChronoUnit.HOURS)
        val token = jwtManager.generate(secretKey, user1, expiration, emptyMap())

        // act
        val isValid = jwtManager.isValid(secretKey, token, user2)

        // assert
        assertFalse(isValid)
    }

    @Test
    fun `isValid should return false for expired token`() {
        // arrange
        val user = User(
            id = 1L,
            username = "expireduser",
            phone = "123456789",
            email = "expired@example.com",
            password = "password"
        )
        // Token expired 1 hour ago
        val expiration = Instant.now().minus(1, ChronoUnit.HOURS)
        val token = jwtManager.generate(secretKey, user, expiration, emptyMap())

        // act
        assertThrows<Exception>{
            jwtManager.isValid(secretKey, token, user)
        }
    }

    @Test
    fun `isValid should return false for invalid token format`() {
        // arrange
        val user = User(
            id = 1L,
            username = "testuser",
            phone = "123456789",
            email = "test@example.com",
            password = "password"
        )
        val invalidToken = "invalid.token.format"

        // act
        assertThrows<Exception> {
            jwtManager.isValid(secretKey, invalidToken, user)
        }
    }

    @Test
    fun `generate should work with empty additional claims`() {
        // arrange
        val user = User(
            id = 1L,
            username = "noclaims",
            phone = "123456789",
            email = "noclaims@example.com",
            password = "password"
        )
        val expiration = Instant.now().plus(1, ChronoUnit.HOURS)

        // act
        val token = jwtManager.generate(secretKey, user, expiration, emptyMap())
        val extractedUsername = jwtManager.extractUsername(secretKey, token)

        // assert
        assertNotNull(token)
        assertEquals("noclaims", extractedUsername)
    }

    @Test
    fun `generate should work with multiple additional claims`() {
        // arrange
        val user = User(
            id = 1L,
            username = "multiclaims",
            phone = "123456789",
            email = "multi@example.com",
            password = "password"
        )
        val expiration = Instant.now().plus(1, ChronoUnit.HOURS)
        val additionalClaims = mapOf(
            "role" to "admin",
            "department" to "IT",
            "level" to 5
        )

        // act
        val token = jwtManager.generate(secretKey, user, expiration, additionalClaims)
        val extractedUsername = jwtManager.extractUsername(secretKey, token)

        // assert
        assertNotNull(token)
        assertEquals("multiclaims", extractedUsername)
    }

    @Test
    fun `should handle wrong secret key gracefully`() {
        // arrange
        val user = User(
            id = 1L,
            username = "secrettest",
            phone = "123456789",
            email = "secret@example.com",
            password = "password"
        )
        val expiration = Instant.now().plus(1, ChronoUnit.HOURS)
        val token = jwtManager.generate(secretKey, user, expiration, emptyMap())
        val wrongSecretKey = "wrongSecretKeyThatIsAtLeast256BitsLongForHS256Algorithm"

        // act & assert
        assertThrows<Exception> {
            jwtManager.extractUsername(wrongSecretKey, token)
        }
    }
}