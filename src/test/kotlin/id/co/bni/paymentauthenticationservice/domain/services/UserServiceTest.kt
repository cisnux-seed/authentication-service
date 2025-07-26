package id.co.bni.paymentauthenticationservice.domain.services

import id.co.bni.paymentauthenticationservice.domains.entities.User
import id.co.bni.paymentauthenticationservice.domains.repositories.UserRepository
import id.co.bni.paymentauthenticationservice.domains.services.UserServiceImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.*

@ExtendWith(MockKExtension::class)
class UserServiceTest {
    
    @MockK
    private lateinit var userRepository: UserRepository
    
    @InjectMockKs
    private lateinit var userService: UserServiceImpl
    
    @Test
    fun `getByUsername should return user when found`() = runTest {
        // arrange
        val username = "john"
        val user = User(
            id = 1L,
            username = "john",
            phone = "123456789",
            email = "john@example.com",
            password = "password123"
        )
        coEvery { userRepository.findByUsername(username) } returns user
        
        // act
        val result = userService.getByUsername(username)
        
        // assert
        assertEquals(user, result)
        assertEquals("john", result?.username)
        assertEquals("john@example.com", result?.email)
        coVerify { userRepository.findByUsername(username) }
    }
    
    @Test
    fun `getByUsername should return null when user not found`() = runTest {
        // arrange
        val username = "notfound"
        coEvery { userRepository.findByUsername(username) } returns null
        
        // act
        val result = userService.getByUsername(username)
        
        // assert
        assertNull(result)
        coVerify { userRepository.findByUsername(username) }
    }
    
    @Test
    fun `getByUsername should handle repository errors`() = runTest {
        // arrange
        val username = "erroruser"
        coEvery { userRepository.findByUsername(username) } throws RuntimeException("Database error")
        
        // act & assert
        assertThrows<RuntimeException> {
            userService.getByUsername(username)
        }
        coVerify { userRepository.findByUsername(username) }
    }
    
    @Test
    fun `isUsernameAvailable should return true when username does not exist`() = runTest {
        // arrange
        val username = "available"
        coEvery { userRepository.isUsernameExists(username) } returns false
        
        // act
        val result = userService.isUsernameAvailable(username)
        
        // assert
        assertTrue(result)
        coVerify { userRepository.isUsernameExists(username) }
    }
    
    @Test
    fun `isUsernameAvailable should return false when username already exists`() = runTest {
        // arrange
        val username = "taken"
        coEvery { userRepository.isUsernameExists(username) } returns true
        
        // act
        val result = userService.isUsernameAvailable(username)
        
        // assert
        assertFalse(result)
        coVerify { userRepository.isUsernameExists(username) }
    }
    
    @Test
    fun `isUsernameAvailable should handle repository errors`() = runTest {
        // arrange
        val username = "errorcheck"
        coEvery { userRepository.isUsernameExists(username) } throws RuntimeException("Database error")
        
        // act & assert
        assertThrows<RuntimeException> {
            userService.isUsernameAvailable(username)
        }
        coVerify { userRepository.isUsernameExists(username) }
    }
    
    @Test
    fun `isEmailAvailable should return true when email does not exist`() = runTest {
        // arrange
        val email = "available@example.com"
        coEvery { userRepository.isEmailExists(email) } returns false
        
        // act
        val result = userService.isEmailAvailable(email)
        
        // assert
        assertTrue(result)
        coVerify { userRepository.isEmailExists(email) }
    }
    
    @Test
    fun `isEmailAvailable should return false when email already exists`() = runTest {
        // arrange
        val email = "taken@example.com"
        coEvery { userRepository.isEmailExists(email) } returns true
        
        // act
        val result = userService.isEmailAvailable(email)
        
        // assert
        assertFalse(result)
        coVerify { userRepository.isEmailExists(email) }
    }
    
    @Test
    fun `isEmailAvailable should handle repository errors`() = runTest {
        // arrange
        val email = "error@example.com"
        coEvery { userRepository.isEmailExists(email) } throws RuntimeException("Database error")
        
        // act & assert
        assertThrows<RuntimeException> {
            userService.isEmailAvailable(email)
        }
        coVerify { userRepository.isEmailExists(email) }
    }
    
    @Test
    fun `should handle empty username gracefully`() = runTest {
        // arrange
        val emptyUsername = ""
        coEvery { userRepository.findByUsername(emptyUsername) } returns null
        
        // act
        val result = userService.getByUsername(emptyUsername)
        
        // assert
        assertNull(result)
        coVerify { userRepository.findByUsername(emptyUsername) }
    }
    
    @Test
    fun `should handle empty email gracefully`() = runTest {
        // arrange
        val emptyEmail = ""
        coEvery { userRepository.isEmailExists(emptyEmail) } returns false
        
        // act
        val result = userService.isEmailAvailable(emptyEmail)
        
        // assert
        assertTrue(result)
        coVerify { userRepository.isEmailExists(emptyEmail) }
    }
    
    @Test
    fun `availability methods should return opposite of existence checks`() = runTest {
        // arrange
        val username = "testuser"
        val email = "test@example.com"
        
        // Test username availability logic
        coEvery { userRepository.isUsernameExists(username) } returns true
        coEvery { userRepository.isEmailExists(email) } returns false
        
        // act
        val usernameAvailable = userService.isUsernameAvailable(username)
        val emailAvailable = userService.isEmailAvailable(email)
        
        // assert - should be opposite of exists results
        assertFalse(usernameAvailable) // exists = true, so available = false
        assertTrue(emailAvailable)     // exists = false, so available = true
        
        coVerify { userRepository.isUsernameExists(username) }
        coVerify { userRepository.isEmailExists(email) }
    }
}