package id.co.bni.paymentauthenticationservice.infrastructures.repositories

import id.co.bni.paymentauthenticationservice.domains.entities.User
import id.co.bni.paymentauthenticationservice.domains.repositories.UserRepository
import id.co.bni.paymentauthenticationservice.infrastructures.repositories.dao.UserDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

@Repository
class UserRepositoryImpl(
    private val userDAO: UserDao,
    private val operator: TransactionalOperator
) : UserRepository {

    override suspend fun findByEmail(email: String): User? = withContext(Dispatchers.IO) {
        userDAO.findByEmail(email)
    }

    override suspend fun findByUsername(username: String): User? = withContext(Dispatchers.IO) {
        userDAO.findByUsername(username)
    }

    override suspend fun insert(user: User): User? = withContext(Dispatchers.IO) {
        operator.executeAndAwait {
            userDAO.save(user)
        }
    }

    override suspend fun update(user: User): User? = withContext(Dispatchers.IO) {
        operator.executeAndAwait {
            userDAO.save(user)
        }
    }

    override suspend fun isUsernameExists(username: String): Boolean = withContext(Dispatchers.IO) {
            userDAO.existsByUsername(username)
        }


    override suspend fun isEmailExists(email: String): Boolean = withContext(Dispatchers.IO){
            userDAO.existsByEmail(email)
        }
}