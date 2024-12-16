package com.negusoft.localauth.core

import com.negusoft.localauth.utils.mapState
import kotlinx.coroutines.flow.MutableStateFlow

class InvalidUsernameOrPasswordException: Exception("Invalid username or password.")
class InvalidRefreshTokenException: Exception("Invalid username or password.")
class WrongPinCodeException(val remainingAttempts: Int): Exception("Invalid PIN code.")
class PinNotRegisteredException: Exception("No PIN code registered.")


class AuthManager {

    data class LoginResult(
        private val manager: AuthManager,
        private val accessToken: AccessToken,
        private val refreshToken: RefreshToken
    ) {
        fun registerPinCode(pinCode: String) {
            manager.registerPinCode(pinCode, refreshToken)
        }
    }

    private val authenticator = MockAuthenticator()

    val accessToken = MutableStateFlow<AccessToken?>(null)
    val isLoggedIn = accessToken.mapState { it != null }

    private var pinCodeRegistry: Pair<String, RefreshToken>? = null
    val pinCodeRegistered: Boolean get() = pinCodeRegistry != null

    @Throws(InvalidUsernameOrPasswordException::class)
    fun login(username: String, password: String): LoginResult {
        when (val result = authenticator.autheticate(username, password)) {
            is AuthResult.Success -> {
                accessToken.value = result.accessToken
                return LoginResult(this, result.accessToken, result.refreshToken)
            }
            AuthResult.InvalidUsernameOrPassword -> throw InvalidUsernameOrPasswordException()
        }
    }

    @Throws(PinNotRegisteredException::class, WrongPinCodeException::class, InvalidRefreshTokenException::class)
    fun login(pinCode: String) {
        val pinCodeRegistry = pinCodeRegistry ?: throw PinNotRegisteredException()
        if (pinCodeRegistry.first != pinCode)
            throw WrongPinCodeException(remainingAttempts = 1)

        val refreshToken = pinCodeRegistry.second
        val authResult: RefreshAccessResult.Success = when (val authResult = authenticator.refreshAccess(refreshToken)) {
            is RefreshAccessResult.Success -> authResult
            RefreshAccessResult.InvalidRefreshToken -> throw InvalidRefreshTokenException()
        }

        // Token rotation
        authResult.refreshToken?.let { newRefreshToken ->
            this.pinCodeRegistry = pinCodeRegistry.first to newRefreshToken
        }

        accessToken.value = authResult.accessToken
    }

    fun logout() {
        accessToken.value = null
    }

    fun registerPinCode(pinCode: String, refreshToken: RefreshToken) {
        pinCodeRegistry = pinCode to refreshToken
    }

}

