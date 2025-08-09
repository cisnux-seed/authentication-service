package id.co.bni.paymentauthenticationservice.commons.constants

object CacheKeys {
    fun userKey(username: String) = "user:$username"
    fun refreshTokenKey(token: String) = "refresh_token:$token"
    fun blacklistedTokenKey(token: String) = "blacklisted:$token"
}