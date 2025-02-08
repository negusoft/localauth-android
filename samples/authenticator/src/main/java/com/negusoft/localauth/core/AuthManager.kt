package com.negusoft.localauth.core

import com.negusoft.localauth.utils.mapState
import com.negusoft.localauth.vault.lock.PinLockException
import kotlinx.coroutines.flow.MutableStateFlow

class InvalidUsernameOrPasswordException: Exception("Invalid username or password.")
class InvalidRefreshTokenException: Exception("Invalid refresh token.")
class WrongPinCodeException(val remainingAttempts: Int): Exception("Invalid PIN code.")
class PinNotRegisteredException: Exception("No PIN code registered.")


class AuthManager {

    data class LoginResult(
        private val manager: AuthManager,
        private val accessToken: AccessToken,
        private val refreshToken: RefreshToken
    ) {
        fun setupLocalAuthentication(pinCode: String) {
            manager.setupLocalAuthentication(pinCode, refreshToken)
        }
    }

    class ChangePassword(
        private val session: LocalAuthenticator<RefreshToken>.Session
    ) {
        fun change(newPassword: String) {
            session.edit {
                registerPassword(newPassword)
            }
        }
    }

    private val authenticator = MockAuthenticator()

    val accessToken = MutableStateFlow<AccessToken?>(null)
    val isLoggedIn = accessToken.mapState { it != null }

    private var localAuthenticator: LocalAuthenticator<RefreshToken>
    val pinCodeRegistered: Boolean get() = localAuthenticator.passwordRegistered

    class RefreshTokenAdapter: LocalAuthenticator.Adapter<RefreshToken> {
        override fun encode(secret: RefreshToken): ByteArray = secret.value.toByteArray()
        override fun decode(bytes: ByteArray): RefreshToken = RefreshToken(bytes.toString(Charsets.UTF_8))
    }

    init {
        // TODO restore local authenticator
        localAuthenticator = LocalAuthenticator.create(RefreshTokenAdapter())
    }

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
        try {
            val refreshToken = localAuthenticator.authenticatedSecret(pinCode)
            val authResult: RefreshAccessResult.Success = when (val authResult = authenticator.refreshAccess(refreshToken)) {
                is RefreshAccessResult.Success -> authResult
                RefreshAccessResult.InvalidRefreshToken -> throw InvalidRefreshTokenException()
            }

            // Token rotation
            authResult.refreshToken?.let { newRefreshToken ->
                localAuthenticator.updateSecret(newRefreshToken)
            }

            accessToken.value = authResult.accessToken
        } catch (e: PinLockException) {
            throw WrongPinCodeException(remainingAttempts = 0)
        }
    }

    fun logout() {
        accessToken.value = null
    }

    fun setupLocalAuthentication(pinCode: String, refreshToken: RefreshToken) {
        val editor = localAuthenticator.initialize(refreshToken)
        editor.registerPassword(pinCode)
        editor.close()
    }

    @Throws
    fun changePassword(current: String): ChangePassword {
        try {
            val session = localAuthenticator.authenticate(current)
            return ChangePassword(session)
        } catch (e: PinLockException) {
            throw WrongPinCodeException(remainingAttempts = 0)
        }
    }

}

