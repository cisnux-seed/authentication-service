package id.co.bni.paymentauthenticationservice.domains.services

import id.co.bni.paymentauthenticationservice.commons.logger.Loggable
import id.co.bni.paymentauthenticationservice.domains.repositories.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

@Service
class UserServiceImpl(
    private val userRepository: UserRepository
) : UserService, Loggable {
    override suspend fun getByUsername(username: String) = withContext(Dispatchers.IO) {
        userRepository.findByUsername(username)
    }

    override suspend fun isUsernameAvailable(username: String): Boolean = !userRepository.isUsernameExists(username)

    override suspend fun isEmailAvailable(email: String): Boolean = !userRepository.isEmailExists(email)
}