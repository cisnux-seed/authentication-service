package id.co.bni.paymentauthenticationservice.domains.entities

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("authentications")
data class Authentication(
    @Id
    val token: String,
    @Column("user_id")
    val userId: Long,
    @Column("created_at")
    val createdAt: Instant = Instant.now()
)
