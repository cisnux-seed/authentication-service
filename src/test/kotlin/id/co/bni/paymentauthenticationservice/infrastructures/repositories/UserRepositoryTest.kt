package id.co.bni.paymentauthenticationservice.infrastructures.repositories

import id.co.bni.paymentauthenticationservice.domains.entities.User
import id.co.bni.paymentauthenticationservice.infrastructures.repositories.dao.UserDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import kotlin.test.*

@ExtendWith(MockKExtension::class)
class UserRepositoryTest {

    @MockK
    private lateinit var userDAO: UserDao

    @InjectMockKs
    private lateinit var userRepository: UserRepositoryImpl

    @Test
    fun `findByEmail should return user when found`() = runTest {
        // arrange
        val user = User(
            id = 1L,
            username = "john",
            phone = "123456789",
            email = "john@example.com",
            password = "password123"
        )
        coEvery { userDAO.findByEmail("john@example.com") } returns user

        // act
        val result = userRepository.findByEmail("john@example.com")

        // assert
        assertEquals(1L, result?.id)
        assertEquals("john", result?.username)
        assertEquals("john@example.com", result?.email)
        assertEquals("password123", result?.password)
        assertEquals("123456789", result?.phone)
        assertNotNull(result?.createdAt)
        assertNotNull(result?.updatedAt)
    }

    @Test
    fun `findByEmail should return null when not found`() = runTest {
        // arrange
        coEvery { userDAO.findByEmail("notfound@example.com") } returns null

        // act
        val result = userRepository.findByEmail("notfound@example.com")

        // assert
        assertNull(result)
    }

    @Test
    fun `findByUsername should return user when found`() = runTest {
        // arrange
        val user = User(
            id = 2L,
            username = "jane",
            phone = "987654321",
            email = "jane@example.com",
            password = "password456"
        )
        coEvery { userDAO.findByUsername("jane") } returns user

        // act
        val result = userRepository.findByUsername("jane")

        // assert
        assertEquals(2L, result?.id)
        assertEquals("jane", result?.username)
        assertEquals("jane@example.com", result?.email)
    }

    @Test
    fun `findByUsername should return null when not found`() = runTest {
        // arrange
        coEvery { userDAO.findByUsername("notfound") } returns null

        // act
        val result = userRepository.findByUsername("notfound")

        // assert
        assertNull(result)
    }

    @Test
    fun `insert should return saved user`() = runTest {
        // arrange
        val newUser = User(
            username = "newuser",
            phone = "555123456",
            email = "newuser@example.com",
            password = "newpassword"
        )
        val savedUser = newUser.copy(id = 3L)
        coEvery { userDAO.save(newUser) } returns savedUser

        // act
        val result = userRepository.insert(newUser)

        // assert
        assertEquals(3L, result?.id)
        assertEquals("newuser", result?.username)
        assertEquals("newuser@example.com", result?.email)
        coVerify { userDAO.save(newUser) }
    }

    @Test
    fun `update should return updated user`() = runTest {
        // arrange
        val existingUser = User(
            id = 1L,
            username = "updated",
            phone = "999888777",
            email = "updated@example.com",
            password = "newpassword",
            updatedAt = Instant.now()
        )
        coEvery { userDAO.save(existingUser) } returns existingUser

        // act
        val result = userRepository.update(existingUser)

        // assert
        assertEquals(1L, result?.id)
        assertEquals("updated", result?.username)
        assertEquals("updated@example.com", result?.email)
        coVerify { userDAO.save(existingUser) }
    }

    @Test
    fun `isUsernameExists should return true when username exists`() = runTest {
        // arrange
        coEvery { userDAO.existsByUsername("existing") } returns true

        // act
        val result = userRepository.isUsernameExists("existing")

        // assert
        assertTrue(result)
    }

    @Test
    fun `isUsernameExists should return false when username does not exist`() = runTest {
        // arrange
        coEvery { userDAO.existsByUsername("nonexisting") } returns false

        // act
        val result = userRepository.isUsernameExists("nonexisting")

        // assert
        assertFalse(result)
    }

    @Test
    fun `isEmailExists should return true when email exists`() = runTest {
        // arrange
        coEvery { userDAO.existsByEmail("existing@example.com") } returns true

        // act
        val result = userRepository.isEmailExists("existing@example.com")

        // assert
        assertTrue(result)
    }

    @Test
    fun `isEmailExists should return false when email does not exist`() = runTest {
        // arrange
        coEvery { userDAO.existsByEmail("nonexisting@example.com") } returns false

        // act
        val result = userRepository.isEmailExists("nonexisting@example.com")

        // assert
        assertFalse(result)
    }

    @Test
    fun `should handle errors gracefully`() = runTest {
        // arrange
        coEvery { userDAO.findByEmail("error@example.com") } throws RuntimeException("DB Error")

        // act & assert
        assertThrows<RuntimeException> {
            userRepository.findByEmail("error@example.com")
        }
    }
}