package id.co.bni.paymentauthenticationservice.commons.configs

import id.co.bni.paymentauthenticationservice.commons.logger.Loggable
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.reactive.config.WebFluxConfigurer

@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class AuthenticationConfig(
) : WebFluxConfigurer, Loggable {


    @Bean
    fun encoder(): PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
}