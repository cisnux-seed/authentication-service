package id.co.bni.paymentauthenticationservice.aplications.controllers

import com.ninjasquad.springmockk.MockkBean
import id.co.bni.paymentauthenticationservice.applications.controllers.AuthController
import id.co.bni.paymentauthenticationservice.applications.controllers.dtos.AuthResponse
import id.co.bni.paymentauthenticationservice.applications.controllers.dtos.WebResponse
import id.co.bni.paymentauthenticationservice.commons.configs.JwtProperties
import id.co.bni.paymentauthenticationservice.commons.exceptions.APIException
import id.co.bni.paymentauthenticationservice.domains.dtos.TokenRefresh
import id.co.bni.paymentauthenticationservice.domains.dtos.TokenResponse
import id.co.bni.paymentauthenticationservice.domains.dtos.UserAuth
import id.co.bni.paymentauthenticationservice.domains.dtos.UserRegister
import id.co.bni.paymentauthenticationservice.domains.entities.User
import id.co.bni.paymentauthenticationservice.domains.services.AuthService
import id.co.bni.paymentauthenticationservice.domains.services.UserService
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.accept.RequestedContentTypeResolver
import org.springframework.web.reactive.function.BodyInserters
import kotlin.test.assertEquals

@WebFluxTest(AuthController::class)
@TestPropertySource(properties = [
    "jwt.access-secret=test-secret",
    "jwt.refresh-secret=test-refresh-secret",
    "jwt.access-token-expiration=3600000",
    "jwt.refresh-token-expiration=86400000"
])
class AuthControllerTest {

    @Autowired
    private lateinit var contentType: RequestedContentTypeResolver

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var authService: AuthService

    @MockkBean
    private lateinit var userService: UserService

    @Test
    fun `should register user successfully`() = runTest {
        // Given
        val userEmail = "test@example.com"
        val registerRequest = """
            {
                "username": "testuser",
                "email": "$userEmail",
                "phone": "08123456789",
                "password": "password123"
            }
        """.trimIndent()

        coEvery { authService.register(any()) } returns userEmail

        // When & Then
        webTestClient
            .post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(registerRequest)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.meta.code").isEqualTo("201")
            .jsonPath("$.meta.message").isEqualTo("user registered successfully")
            .jsonPath("$.data").isEqualTo(userEmail)

        // Verify
        coVerify(exactly = 1) { authService.register(any()) }
    }

    @Test
    fun `should login user successfully`() = runTest {
        // Given
        val loginRequest = """
            {
                "username": "testuser",
                "password": "password123"
            }
        """.trimIndent()

        val authResponse = AuthResponse(
            accessToken = "fake-access-token",
            refreshToken = "fake-refresh-token"
        )

        coEvery { authService.authenticate(any()) } returns authResponse

        // When & Then
        webTestClient
            .post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(loginRequest)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .consumeWith { response ->
                println("Response body: ${String(response.responseBody ?: byteArrayOf())}")
            }
            .jsonPath("$.meta.code").isEqualTo("200")
            .jsonPath("$.meta.message").isEqualTo("user logged in successfully 👍🏻👍🏻🫡")
            .jsonPath("$.data.access_token").isEqualTo("fake-access-token")
            .jsonPath("$.data.refresh_token").isEqualTo("fake-refresh-token")

        // Verify
        coVerify(exactly = 1) { authService.authenticate(any()) }
    }

    @Test
    fun `should refresh token successfully`() = runTest {
        // Given
        val refreshRequest = """
            {
                "refresh_token": "fake-refresh-token"
            }
        """.trimIndent()

        val tokenResponse = TokenResponse(
            accessToken = "new-access-token"
        )

        coEvery { authService.refresh("fake-refresh-token") } returns tokenResponse

        // When & Then
        webTestClient
            .put()
            .uri("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(refreshRequest)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.meta.code").isEqualTo("200")
            .jsonPath("$.meta.message").isEqualTo("refresh token successfully")
            .jsonPath("$.data.access_token").isEqualTo("new-access-token")

        // Verify
        coVerify(exactly = 1) { authService.refresh("fake-refresh-token") }
    }

    @Test
    fun `should logout user successfully`() = runTest {
        // Given
        coEvery { authService.logout(any()) } returns Unit

        webTestClient
            .method(HttpMethod.DELETE)
            .uri("/api/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"refresh_token": "fake-refresh-token"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.meta.code").isEqualTo("200")
            .jsonPath("$.data").isEqualTo("user logged out successfully")

        coVerify(exactly = 1) { authService.logout(any()) }
    }

    @Test
    fun `should return validation error for invalid register request`() = runTest {
        // Given - Invalid request with blank username
        val invalidRequest = """
            {
                "username": "",
                "email": "test@example.com",
                "phone": "08123456789",
                "password": "password123"
            }
        """.trimIndent()

        // When & Then
        webTestClient
            .post()
            .uri("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .exchange()
            .expectStatus().isBadRequest
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.meta.code").isEqualTo("400")
    }
}