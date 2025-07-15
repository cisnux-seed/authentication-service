package id.co.bni.paymentauthenticationservice.controllers

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class GreeterController {
    @GetMapping("/greet")
    suspend fun greet(): String {
        return "Hello, World!"
    }
}