package id.co.bni.paymentauthenticationservice.infrastructures.repositories.dao

import id.co.bni.paymentauthenticationservice.domains.entities.Authentication
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Component

@Component
interface AuthenticationDao : CoroutineCrudRepository<Authentication, String>

