package id.co.bni.paymentauthenticationservice.domain.services

import id.co.bni.paymentauthenticationservice.commons.constants.CacheKeys
import id.co.bni.paymentauthenticationservice.domains.repositories.CacheRepository
import id.co.bni.paymentauthenticationservice.domains.services.TokenBlacklistServiceImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.*

@ExtendWith(MockKExtension::class)
class TokenBlacklistServiceTest {

    @MockK
    private lateinit var cacheRepository: CacheRepository

    @InjectMockKs
    private lateinit var tokenBlacklistService: TokenBlacklistServiceImpl

    @Test
    fun `blacklistToken should store token in cache with correct TTL`() = runTest {
        // arrange
        val token = "test.refresh.token"
        val ttlMinutes = 1440L
        val blacklistKey = CacheKeys.blacklistedTokenKey(token)
        coEvery { cacheRepository.set(blacklistKey, "blacklisted", ttlMinutes) } returns Unit

        // act
        tokenBlacklistService.blacklistToken(token, ttlMinutes)

        // assert
        coVerify { cacheRepository.set(blacklistKey, "blacklisted", ttlMinutes) }
    }

    @Test
    fun `isTokenBlacklisted should return true when token exists in blacklist`() = runTest {
        // arrange
        val token = "blacklisted.token"
        val blacklistKey = CacheKeys.blacklistedTokenKey(token)
        coEvery { cacheRepository.isExists(blacklistKey) } returns true

        // act
        val result = tokenBlacklistService.isTokenBlacklisted(token)

        // assert
        assertTrue(result)
        coVerify { cacheRepository.isExists(blacklistKey) }
    }

    @Test
    fun `isTokenBlacklisted should return false when token does not exist in blacklist`() = runTest {
        // arrange
        val token = "clean.token"
        val blacklistKey = CacheKeys.blacklistedTokenKey(token)
        coEvery { cacheRepository.isExists(blacklistKey) } returns false

        // act
        val result = tokenBlacklistService.isTokenBlacklisted(token)

        // assert
        assertFalse(result)
        coVerify { cacheRepository.isExists(blacklistKey) }
    }

    @Test
    fun `storeRefreshToken should store token data with correct structure`() = runTest {
        // arrange
        val token = "refresh.token"
        val userId = 123L
        val ttlMinutes = 1440L
        val refreshTokenKey = CacheKeys.refreshTokenKey(token)
        coEvery { cacheRepository.set(refreshTokenKey, any(), ttlMinutes) } returns Unit

        // act
        tokenBlacklistService.storeRefreshToken(token, userId, ttlMinutes)

        // assert
        coVerify { 
            cacheRepository.set(
                eq(refreshTokenKey), 
                any(),
                ttlMinutes
            ) 
        }
    }

    @Test
    fun `isRefreshTokenValid should return true when token exists and is not blacklisted`() = runTest {
        // arrange
        val token = "valid.refresh.token"
        val refreshTokenKey = CacheKeys.refreshTokenKey(token)
        val blacklistKey = CacheKeys.blacklistedTokenKey(token)
        
        coEvery { cacheRepository.isExists(refreshTokenKey) } returns true
        coEvery { cacheRepository.isExists(blacklistKey) } returns false

        // act
        val result = tokenBlacklistService.isRefreshTokenValid(token)

        // assert
        assertTrue(result)
        coVerify { cacheRepository.isExists(refreshTokenKey) }
        coVerify { cacheRepository.isExists(blacklistKey) }
    }

    @Test
    fun `isRefreshTokenValid should return false when token is blacklisted`() = runTest {
        // arrange
        val token = "blacklisted.refresh.token"
        val refreshTokenKey = CacheKeys.refreshTokenKey(token)
        val blacklistKey = CacheKeys.blacklistedTokenKey(token)
        
        coEvery { cacheRepository.isExists(refreshTokenKey) } returns true
        coEvery { cacheRepository.isExists(blacklistKey) } returns true

        // act
        val result = tokenBlacklistService.isRefreshTokenValid(token)

        // assert
        assertFalse(result)
    }

    @Test
    fun `isRefreshTokenValid should return false when token does not exist`() = runTest {
        // arrange
        val token = "nonexistent.token"
        val refreshTokenKey = CacheKeys.refreshTokenKey(token)
        val blacklistKey = CacheKeys.blacklistedTokenKey(token)
        
        coEvery { cacheRepository.isExists(refreshTokenKey) } returns false
        coEvery { cacheRepository.isExists(blacklistKey) } returns false

        // act
        val result = tokenBlacklistService.isRefreshTokenValid(token)

        // assert
        assertFalse(result)
    }

    @Test
    fun `removeRefreshToken should delete token from cache`() = runTest {
        // arrange
        val token = "token.to.remove"
        val refreshTokenKey = CacheKeys.refreshTokenKey(token)
        coEvery { cacheRepository.delete(refreshTokenKey) } returns true

        // act
        tokenBlacklistService.removeRefreshToken(token)

        // assert
        coVerify { cacheRepository.delete(refreshTokenKey) }
    }

    @Test
    fun `blacklistToken should handle cache errors gracefully`() = runTest {
        // arrange
        val token = "error.token"
        val blacklistKey = CacheKeys.blacklistedTokenKey(token)
        coEvery { cacheRepository.set(blacklistKey, "blacklisted", any()) } throws RuntimeException("Cache error")

        // act - should not throw exception
        tokenBlacklistService.blacklistToken(token)

        // assert
        coVerify { cacheRepository.set(blacklistKey, "blacklisted", any()) }
    }

    @Test
    fun `isTokenBlacklisted should return false on cache errors`() = runTest {
        // arrange
        val token = "error.token"
        val blacklistKey = CacheKeys.blacklistedTokenKey(token)
        coEvery { cacheRepository.isExists(blacklistKey) } throws RuntimeException("Cache error")

        // act
        val result = tokenBlacklistService.isTokenBlacklisted(token)

        // assert
        assertFalse(result) // Should return false on errors for safety
        coVerify { cacheRepository.isExists(blacklistKey) }
    }
}
