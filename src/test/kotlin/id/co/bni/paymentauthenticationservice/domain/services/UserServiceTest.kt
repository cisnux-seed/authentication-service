package id.co.bni.paymentauthenticationservice.domain.services

import id.co.bni.paymentauthenticationservice.commons.constants.CacheKeys
import id.co.bni.paymentauthenticationservice.domains.entities.User
import id.co.bni.paymentauthenticationservice.domains.repositories.CacheRepository
import id.co.bni.paymentauthenticationservice.domains.repositories.UserRepository
import id.co.bni.paymentauthenticationservice.domains.services.UserServiceImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.*

@ExtendWith(MockKExtension::class)
class UserServiceTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var cacheRepository: CacheRepository

    @InjectMockKs
    private lateinit var userService: UserServiceImpl

    private val dummyUser = User(
        id = 1L,
        username = "john",
        phone = "123456789",
        email = "john@example.com",
        password = "password123"
    )

    @Test
    fun `getByUsername with cache hit should return cached user`() = runTest {
        // arrange
        val username = "john"
        val cacheKey = CacheKeys.userKey(username)
        coEvery { cacheRepository.get(cacheKey, User::class.java) } returns dummyUser

        // act
        val result = userService.getByUsername(username)

        // assert
        assertEquals(dummyUser, result)
        assertEquals("john", result?.username)
        assertEquals("john@example.com", result?.email)
        coVerify(exactly = 1) { cacheRepository.get(cacheKey, User::class.java) }
        coVerify(exactly = 0) { userRepository.findByUsername(any()) }
        coVerify(exactly = 0) { cacheRepository.set(any(), any(), any()) }
    }

    @Test
    fun `getByUsername with cache miss should fetch from database and cache result`() = runTest {
        // arrange
        val username = "john"
        val cacheKey = CacheKeys.userKey(username)
        val userSlot = slot<User>()

        coEvery { cacheRepository.get(cacheKey, User::class.java) } returns null
        coEvery { userRepository.findByUsername(username) } returns dummyUser
        coEvery { cacheRepository.set(cacheKey, capture(userSlot), 30) } returns Unit

        // act
        val result = userService.getByUsername(username)

        // assert
        assertEquals(dummyUser, result)
        assertEquals("john", result?.username)
        assertEquals("john@example.com", result?.email)

        // Verify cached user
        val cachedUser = userSlot.captured
        assertEquals(dummyUser, cachedUser)
        assertEquals("john", cachedUser.username)
        assertEquals("john@example.com", cachedUser.email)

        coVerify(exactly = 1) { cacheRepository.get(cacheKey, User::class.java) }
        coVerify(exactly = 1) { userRepository.findByUsername(username) }
        coVerify(exactly = 1) { cacheRepository.set(cacheKey, dummyUser, 30) }
    }

    @Test
    fun `getByUsername with cache miss and user not found should return null and not cache`() = runTest {
        // arrange
        val username = "notfound"
        val cacheKey = CacheKeys.userKey(username)

        coEvery { cacheRepository.get(cacheKey, User::class.java) } returns null
        coEvery { userRepository.findByUsername(username) } returns null

        // act
        val result = userService.getByUsername(username)

        // assert
        assertNull(result)
        coVerify(exactly = 1) { cacheRepository.get(cacheKey, User::class.java) }
        coVerify(exactly = 1) { userRepository.findByUsername(username) }
        coVerify(exactly = 0) { cacheRepository.set(any(), any(), any()) }
    }

    @Test
    fun `getByUsername should handle cache get errors gracefully`() = runTest {
        // arrange
        val username = "erroruser"
        val cacheKey = CacheKeys.userKey(username)

        coEvery { cacheRepository.get(cacheKey, User::class.java) } throws RuntimeException("Cache error")

        // act & assert - should throw the cache exception
        assertThrows<RuntimeException> {
            userService.getByUsername(username)
        }

        coVerify(exactly = 1) { cacheRepository.get(cacheKey, User::class.java) }
        coVerify(exactly = 0) { userRepository.findByUsername(any()) }
    }

    @Test
    fun `getByUsername should handle cache set errors gracefully`() = runTest {
        // arrange
        val username = "john"
        val cacheKey = CacheKeys.userKey(username)

        coEvery { cacheRepository.get(cacheKey, User::class.java) } returns null
        coEvery { userRepository.findByUsername(username) } returns dummyUser
        coEvery { cacheRepository.set(cacheKey, dummyUser, 30) } throws RuntimeException("Cache set error")

        // act & assert - should throw the cache exception
        assertThrows<RuntimeException> {
            userService.getByUsername(username)
        }

        coVerify(exactly = 1) { cacheRepository.get(cacheKey, User::class.java) }
        coVerify(exactly = 1) { userRepository.findByUsername(username) }
        coVerify(exactly = 1) { cacheRepository.set(cacheKey, dummyUser, 30) }
    }

    @Test
    fun `getByUsername should handle repository errors`() = runTest {
        // arrange
        val username = "erroruser"
        val cacheKey = CacheKeys.userKey(username)

        coEvery { cacheRepository.get(cacheKey, User::class.java) } returns null
        coEvery { userRepository.findByUsername(username) } throws RuntimeException("Database error")

        // act & assert
        assertThrows<RuntimeException> {
            userService.getByUsername(username)
        }

        coVerify(exactly = 1) { cacheRepository.get(cacheKey, User::class.java) }
        coVerify(exactly = 1) { userRepository.findByUsername(username) }
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
        coVerify(exactly = 1) { userRepository.isUsernameExists(username) }
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
        coVerify(exactly = 1) { userRepository.isUsernameExists(username) }
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
        coVerify(exactly = 1) { userRepository.isUsernameExists(username) }
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
        coVerify(exactly = 1) { userRepository.isEmailExists(email) }
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
        coVerify(exactly = 1) { userRepository.isEmailExists(email) }
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
        coVerify(exactly = 1) { userRepository.isEmailExists(email) }
    }

    @Test
    fun `invalidateUserCache should delete user cache key`() = runTest {
        // arrange
        val username = "testuser"
        val cacheKey = CacheKeys.userKey(username)
        coEvery { cacheRepository.delete(cacheKey) } returns true

        // act
        userService.invalidateUserCache(username)

        // assert
        coVerify(exactly = 1) { cacheRepository.delete(cacheKey) }
    }

    @Test
    fun `invalidateUserCache should handle cache delete failures gracefully`() = runTest {
        // arrange
        val username = "testuser"
        val cacheKey = CacheKeys.userKey(username)
        coEvery { cacheRepository.delete(cacheKey) } returns false

        // act - should not throw exception
        userService.invalidateUserCache(username)

        // assert
        coVerify(exactly = 1) { cacheRepository.delete(cacheKey) }
    }

    @Test
    fun `invalidateUserCache should handle cache delete errors gracefully`() = runTest {
        // arrange
        val username = "erroruser"
        val cacheKey = CacheKeys.userKey(username)
        coEvery { cacheRepository.delete(cacheKey) } throws RuntimeException("Cache error")

        // act - should not throw exception
        userService.invalidateUserCache(username)

        // assert
        coVerify(exactly = 1) { cacheRepository.delete(cacheKey) }
    }

    @Test
    fun `should handle empty username gracefully`() = runTest {
        // arrange
        val emptyUsername = ""
        val cacheKey = CacheKeys.userKey(emptyUsername)
        coEvery { cacheRepository.get(cacheKey, User::class.java) } returns null
        coEvery { userRepository.findByUsername(emptyUsername) } returns null

        // act
        val result = userService.getByUsername(emptyUsername)

        // assert
        assertNull(result)
        coVerify(exactly = 1) { cacheRepository.get(cacheKey, User::class.java) }
        coVerify(exactly = 1) { userRepository.findByUsername(emptyUsername) }
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
        coVerify(exactly = 1) { userRepository.isEmailExists(emptyEmail) }
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

        coVerify(exactly = 1) { userRepository.isUsernameExists(username) }
        coVerify(exactly = 1) { userRepository.isEmailExists(email) }
    }

    @Test
    fun `multiple calls to getByUsername should only hit database once when cached`() = runTest {
        // arrange
        val username = "cached_user"
        val cacheKey = CacheKeys.userKey(username)

        // First call: cache miss, fetch from DB
        coEvery { cacheRepository.get(cacheKey, User::class.java) } returnsMany listOf(null, dummyUser)
        coEvery { userRepository.findByUsername(username) } returns dummyUser
        coEvery { cacheRepository.set(cacheKey, dummyUser, 30) } returns Unit

        // act
        val result1 = userService.getByUsername(username)
        val result2 = userService.getByUsername(username)

        // assert
        assertEquals(dummyUser, result1)
        assertEquals(dummyUser, result2)

        // Database should only be called once
        coVerify(exactly = 1) { userRepository.findByUsername(username) }
        // Cache should be checked twice
        coVerify(exactly = 2) { cacheRepository.get(cacheKey, User::class.java) }
        // Cache should be set only once
        coVerify(exactly = 1) { cacheRepository.set(cacheKey, dummyUser, 30) }
    }

    @Test
    fun `getByUsername should use correct cache TTL of 30 minutes`() = runTest {
        // arrange
        val username = "ttl_test"
        val cacheKey = CacheKeys.userKey(username)

        coEvery { cacheRepository.get(cacheKey, User::class.java) } returns null
        coEvery { userRepository.findByUsername(username) } returns dummyUser
        coEvery { cacheRepository.set(cacheKey, dummyUser, 30) } returns Unit

        // act
        userService.getByUsername(username)

        // assert - verify TTL is exactly 30 minutes
        coVerify(exactly = 1) { cacheRepository.set(cacheKey, dummyUser, 30) }
    }

    @Test
    fun `getByUsername should handle special characters in username`() = runTest {
        // arrange
        val specialUsername = "user@test-123_456"
        val cacheKey = CacheKeys.userKey(specialUsername)
        val userWithSpecialUsername = dummyUser.copy(username = specialUsername)

        coEvery { cacheRepository.get(cacheKey, User::class.java) } returns null
        coEvery { userRepository.findByUsername(specialUsername) } returns userWithSpecialUsername
        coEvery { cacheRepository.set(cacheKey, userWithSpecialUsername, 30) } returns Unit

        // act
        val result = userService.getByUsername(specialUsername)

        // assert
        assertEquals(userWithSpecialUsername, result)
        assertEquals(specialUsername, result?.username)
        coVerify(exactly = 1) { cacheRepository.get(cacheKey, User::class.java) }
        coVerify(exactly = 1) { userRepository.findByUsername(specialUsername) }
        coVerify(exactly = 1) { cacheRepository.set(cacheKey, userWithSpecialUsername, 30) }
    }

    @Test
    fun `invalidateUserCache should work with special characters in username`() = runTest {
        // arrange
        val specialUsername = "user@test-123_456"
        val cacheKey = CacheKeys.userKey(specialUsername)
        coEvery { cacheRepository.delete(cacheKey) } returns true

        // act
        userService.invalidateUserCache(specialUsername)

        // assert
        coVerify(exactly = 1) { cacheRepository.delete(cacheKey) }
    }
}