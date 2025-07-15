package id.co.bni.paymentauthenticationservice.infrastructures.repositories

import id.co.bni.paymentauthenticationservice.domains.entities.Authentication
import id.co.bni.paymentauthenticationservice.domains.repositories.AuthenticationRepository
import id.co.bni.paymentauthenticationservice.infrastructures.repositories.dao.AuthenticationDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.withContext
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

@Repository
class AuthRepositoryImpl(
    private val authDao: AuthenticationDao,
    private val template: R2dbcEntityTemplate,
    private val operator: TransactionalOperator
) :
    AuthenticationRepository {
    override suspend fun insert(authentication: Authentication): Authentication? = withContext(Dispatchers.IO) {
        operator.executeAndAwait {
            template.insert(authentication).log().awaitFirstOrNull()
        }
    }

    override suspend fun deleteById(token: String) = withContext(Dispatchers.IO) {
        authDao.deleteById(token)
    }

    override suspend fun findById(token: String): Authentication? = withContext(Dispatchers.IO) {
        authDao.findById(token)
    }

    override suspend fun isExists(token: String): Boolean = withContext(Dispatchers.IO) {
        authDao.existsById(token)
    }
}