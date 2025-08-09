package id.co.bni.paymentauthenticationservice.infrastructures.repositories

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveValueOperations
import reactor.core.publisher.Mono
import java.time.Duration
import kotlin.jvm.java
import kotlin.to

@ExtendWith(MockKExtension::class)
class CacheRepositoryTest {

    @MockK
    private lateinit var redisTemplate: ReactiveRedisTemplate<String, Any>

    @MockK
    private lateinit var objectMapper: ObjectMapper

    @MockK
    private lateinit var valueOps: ReactiveValueOperations<String, Any>

    @InjectMockKs
    private lateinit var cacheRepository: CacheRepositoryImpl

    // Test data class for ObjectMapper conversion tests
    data class TestData(
        val name: String,
        val value: Int
    )

    @Test
    fun `get should return cached value when key exists and type matches`() = runTest {
        // arrange
        val key = "test:key"
        val value = "test value"
        coEvery { redisTemplate.opsForValue() } returns valueOps
        coEvery { valueOps.get(key) } returns Mono.just(value)

        // act
        val result = cacheRepository.get(key, String::class.java)

        // assert
        assertEquals(value, result)
        coVerify(exactly = 1) { redisTemplate.opsForValue() }
        coVerify(exactly = 1) { valueOps.get(key) }
    }

    @Test
    fun `get should return null when key does not exist`() = runTest {
        // arrange
        val key = "nonexistent:key"
        coEvery { redisTemplate.opsForValue() } returns valueOps
        coEvery { valueOps.get(key) } returns Mono.empty()

        // act
        val result = cacheRepository.get(key, String::class.java)

        // assert
        assertNull(result)
        coVerify(exactly = 1) { redisTemplate.opsForValue() }
        coVerify(exactly = 1) { valueOps.get(key) }
    }

    @Test
    fun `get should use ObjectMapper when value is String and type conversion needed`() = runTest {
        // arrange
        val key = "test:key"
        val jsonValue = """{"name":"test","value":123}"""
        val expectedObject = TestData("test", 123)

        coEvery { redisTemplate.opsForValue() } returns valueOps
        coEvery { valueOps.get(key) } returns Mono.just(jsonValue)
        coEvery { objectMapper.readValue(jsonValue, TestData::class.java) } returns expectedObject

        // act
        val result = cacheRepository.get(key, TestData::class.java)

        // assert
        assertEquals(expectedObject, result)
        coVerify(exactly = 1) { redisTemplate.opsForValue() }
        coVerify(exactly = 1) { valueOps.get(key) }
        coVerify(exactly = 1) { objectMapper.readValue(jsonValue, TestData::class.java) }
    }

    @Test
    fun `get should use ObjectMapper convertValue when types don't match directly`() = runTest {
        // arrange
        val key = "test:key"
        val mapValue = mapOf("name" to "test", "value" to 123)
        val expectedObject = TestData("test", 123)

        coEvery { redisTemplate.opsForValue() } returns valueOps
        coEvery { valueOps.get(key) } returns Mono.just(mapValue)
        coEvery { objectMapper.convertValue(mapValue, TestData::class.java) } returns expectedObject

        // act
        val result = cacheRepository.get(key, TestData::class.java)

        // assert
        assertEquals(expectedObject, result)
        coVerify(exactly = 1) { redisTemplate.opsForValue() }
        coVerify(exactly = 1) { valueOps.get(key) }
        coVerify(exactly = 1) { objectMapper.convertValue(mapValue, TestData::class.java) }
    }

    @Test
    fun `set should store value with default TTL`() = runTest {
        // arrange
        val key = "test:key"
        val value = "test value"

        coEvery { redisTemplate.opsForValue() } returns valueOps
        coEvery { valueOps.set(key, value, Duration.ofMinutes(30)) } returns Mono.just(true)

        // act
        cacheRepository.set(key, value)

        // assert
        coVerify(exactly = 1) { redisTemplate.opsForValue() }
        coVerify(exactly = 1) { valueOps.set(key, value, Duration.ofMinutes(30)) }
    }

    @Test
    fun `set should store value with custom TTL`() = runTest {
        // arrange
        val key = "test:key"
        val value = "test value"
        val ttlMinutes = 60L

        coEvery { redisTemplate.opsForValue() } returns valueOps
        coEvery { valueOps.set(key, value, Duration.ofMinutes(ttlMinutes)) } returns Mono.just(true)

        // act
        cacheRepository.set(key, value, ttlMinutes)

        // assert
        coVerify(exactly = 1) { redisTemplate.opsForValue() }
        coVerify(exactly = 1) { valueOps.set(key, value, Duration.ofMinutes(ttlMinutes)) }
    }

    @Test
    fun `set should store complex object`() = runTest {
        // arrange
        val key = "test:key"
        val value = TestData("test", 123)

        coEvery { redisTemplate.opsForValue() } returns valueOps
        coEvery { valueOps.set(key, value, Duration.ofMinutes(30)) } returns Mono.just(true)

        // act
        cacheRepository.set(key, value)

        // assert
        coVerify(exactly = 1) { redisTemplate.opsForValue() }
        coVerify(exactly = 1) { valueOps.set(key, value, Duration.ofMinutes(30)) }
    }

    @Test
    fun `delete should return true when key is deleted successfully`() = runTest {
        // arrange
        val key = "test:key"
        coEvery { redisTemplate.delete(key) } returns Mono.just(1L)

        // act
        val result = cacheRepository.delete(key)

        // assert
        assertTrue(result)
        coVerify(exactly = 1) { redisTemplate.delete(key) }
    }

    @Test
    fun `delete should return false when key does not exist`() = runTest {
        // arrange
        val key = "nonexistent:key"
        coEvery { redisTemplate.delete(key) } returns Mono.just(0L)

        // act
        val result = cacheRepository.delete(key)

        // assert
        assertFalse(result)
        coVerify(exactly = 1) { redisTemplate.delete(key) }
    }

    @Test
    fun `delete should return false when Redis operation fails`() = runTest {
        // arrange
        val key = "test:key"
        coEvery { redisTemplate.delete(key) } throws kotlin.RuntimeException("Redis delete failed")

        // act
        val result = cacheRepository.delete(key)

        // assert
        assertFalse(result)
        coVerify(exactly = 1) { redisTemplate.delete(key) }
    }
}