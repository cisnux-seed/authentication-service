package id.co.bni.paymentauthenticationservice.infrastructures.repositories

import id.co.bni.paymentauthenticationservice.domains.entities.Authentication
import id.co.bni.paymentauthenticationservice.infrastructures.repositories.dao.AuthenticationDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import kotlin.test.*;

@ExtendWith(MockKExtension::class)
class AuthRepositoryTest {
    @MockK
    private lateinit var authDao: AuthenticationDao

    @MockK
    private lateinit var template: R2dbcEntityTemplate

    @MockK
    private lateinit var operator: TransactionalOperator

    @InjectMockKs
    private lateinit var authRepository: AuthRepositoryImpl

    @Test
    fun `insert should return saved authentication`() = runTest {
        // arrange
        val auth = Authentication(token = "token123", userId = 1L)
        every { operator.execute<Authentication>(any()) } returns Flux.just(auth)

        // act
        val result = authRepository.insert(auth)

        // assert
        assertEquals("token123", result?.token)
        assertEquals(1L, result?.userId)
        assertNotNull(result?.createdAt)
    }

    @Test
    fun `insert should return null when fails`() = runTest {
        // arrange
        val auth = Authentication(token = "token123", userId = 1L)
        every { operator.execute<Authentication>(any()) } returns Flux.empty()

        // act
        val result = authRepository.insert(auth)

        // assert
        assertNull(result)
    }

    @Test
    fun `deleteById should call dao deleteById`() = runTest {
        // arrange
        coEvery { authDao.deleteById("token123") } returns Unit

        // act
        authRepository.deleteById("token123")

        // assert
        coVerify { authDao.deleteById("token123") }
    }

    @Test
    fun `findById should return authentication when found`() = runTest {
        // arrange
        val auth = Authentication(token = "token123", userId = 1L)
        coEvery { authDao.findById("token123") } returns auth

        // act
        val result = authRepository.findById("token123")

        // assert
        assertEquals("token123", result?.token)
        assertEquals(1L, result?.userId)
    }

    @Test
    fun `findById should return null when not found`() = runTest {
        // arrange
        coEvery { authDao.findById("token123") } returns null

        // act
        val result = authRepository.findById("token123")

        // assert
        assertNull(result)
    }

    @Test
    fun `isExists should return true when authentication exists`() = runTest {
        // arrange
        coEvery { authDao.existsById("token123") } returns true

        // act
        val result = authRepository.isExists("token123")

        // assert
        assertTrue(result)
    }

    @Test
    fun `isExists should return false when authentication does not exist`() = runTest {
        // arrange
        coEvery { authDao.existsById("token123") } returns false

        // act
        val result = authRepository.isExists("token123")

        // assert
        assertFalse(result)
    }

    @Test
    fun `should handle errors gracefully`() = runTest {
        // arrange
        coEvery { authDao.findById("token123") } throws RuntimeException("DB Error")

        // act & assert
        assertThrows<RuntimeException> {
            authRepository.findById("token123")
        }
    }
}