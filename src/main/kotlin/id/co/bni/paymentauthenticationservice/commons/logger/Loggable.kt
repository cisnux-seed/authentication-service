package id.co.bni.paymentauthenticationservice.commons.logger

import org.slf4j.LoggerFactory
import org.slf4j.Logger

interface Loggable {
    val log: Logger
        get() = LoggerFactory.getLogger(this::class.java)
}