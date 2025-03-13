package com.negusoft.localauth.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.negusoft.localauth.authenticator.LocalAuthenticator
import com.negusoft.localauth.authenticator.authenticatedWithBiometricLock
import com.negusoft.localauth.coding.encode
import com.negusoft.localauth.coding.restore
import com.negusoft.localauth.authenticator.initialize
import com.negusoft.localauth.lock.LockException
import com.negusoft.localauth.preferences.getByteArray
import com.negusoft.localauth.preferences.putByteArray
import com.negusoft.localauth.authenticator.registerBiometricLock
import com.negusoft.localauth.authenticator.registerPasswordLock
import com.negusoft.localauth.authenticator.updateSecret
import com.negusoft.localauth.utils.mapState
import kotlinx.coroutines.flow.MutableStateFlow

class InvalidUsernameOrPasswordException: Exception("Invalid username or password.")
class InvalidRefreshTokenException: Exception("Invalid refresh token.")
class WrongPinCodeException(val remainingAttempts: Int): Exception("Invalid PIN code.")
class PinNotRegisteredException: Exception("No PIN code registered.")
class BiometricNotRegisteredException: Exception("No PIN code registered.")

private const val LOCK_PASSWORD = "password"
private const val LOCK_BIOMETRIC = "biometric"
private const val PREFERENCE_LOCAL_AUTHENTICATOR = "LOCAL_AUTHENTICATOR"

class AuthManager(
    private val prefs: SharedPreferences
) {

    constructor(context: Context): this(
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    )

    data class LoginResult(
        private val manager: AuthManager,
        private val accessToken: AccessToken,
        private val refreshToken: RefreshToken
    ) {
        fun startLocalAuthenticationSetup(): AuthSetup {
           return manager.startLocalAuthenticationSetup(refreshToken)
        }
    }

    /** Register locks and call finish() when when done. */
    data class AuthSetup(
        private val manager: AuthManager,
        private val editor: LocalAuthenticator.Editor
    ) {
        fun registerPinLock(pinCode: String) {
            manager.enablePinLogin(editor, pinCode)
        }
        fun enableBiometricLogin() {
            manager.enableBiometricLogin(editor)
        }
    }

    class ChangePassword(private val onChange: (String) -> Unit) {
        fun change(newPassword: String) = onChange(newPassword)
    }

    private val authenticator = MockAuthenticator(prefs)

    val accessToken = MutableStateFlow<AccessToken?>(null)
    val isLoggedIn = accessToken.mapState { it != null }

    private var localAuthenticator: LocalAuthenticator
    val pinCodeRegistered: Boolean get() = localAuthenticator.lockEnabled(LOCK_PASSWORD)
    val biometricRegistered: Boolean get() = localAuthenticator.lockEnabled(LOCK_BIOMETRIC)

    object Adapter {
        fun encode(secret: RefreshToken): ByteArray = secret.value.toByteArray()
        fun decode(bytes: ByteArray): RefreshToken = RefreshToken(bytes.toString(Charsets.UTF_8))
    }

    init {
        val encoded = prefs.getByteArray(PREFERENCE_LOCAL_AUTHENTICATOR)
        localAuthenticator = if (encoded != null)
            LocalAuthenticator.restore(encoded)
        else LocalAuthenticator.create()
    }

    private fun save() {
        prefs.edit {
            putByteArray(PREFERENCE_LOCAL_AUTHENTICATOR, localAuthenticator.encode())
        }
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
            val refreshToken = localAuthenticator.authenticatedSecret(pinCode, Adapter::decode)
            loginWithRefreshToken(refreshToken)
        } catch (e: LockException) {
            throw WrongPinCodeException(remainingAttempts = 0)
        }
    }

    @Throws(BiometricNotRegisteredException::class, WrongPinCodeException::class, InvalidRefreshTokenException::class)
    suspend fun loginWithBiometric(activity: FragmentActivity) {
        val refreshTokenEncoded = localAuthenticator.authenticatedWithBiometricLock(LOCK_BIOMETRIC, activity) { secret() }
        val refreshToken = Adapter.decode(refreshTokenEncoded)
        loginWithRefreshToken(refreshToken)
    }

    @Throws(InvalidRefreshTokenException::class)
    private fun loginWithRefreshToken(refreshToken: RefreshToken) {
        val authResult: RefreshAccessResult.Success = when (val authResult = authenticator.refreshAccess(refreshToken)) {
            is RefreshAccessResult.Success -> authResult
            RefreshAccessResult.InvalidRefreshToken -> throw InvalidRefreshTokenException()
        }

        // Token rotation
        authResult.refreshToken?.let { newRefreshToken ->
            localAuthenticator.updateSecret(newRefreshToken, Adapter::encode)
            save()
        }

        accessToken.value = authResult.accessToken
    }

    fun logout() {
        accessToken.value = null
    }

    fun signout() {
        localAuthenticator.reset()
        accessToken.value = null
        save()
    }

    fun startLocalAuthenticationSetup(refreshToken: RefreshToken): AuthSetup {
        val editor = localAuthenticator.initialize(refreshToken, Adapter::encode)
        return AuthSetup(this, editor)
    }

    fun enablePinLogin(editor: LocalAuthenticator.Editor, pinCode: String) {
        editor.registerPasswordLock(LOCK_PASSWORD, pinCode)
        save()
    }

    fun enableBiometricLogin(editor: LocalAuthenticator.Editor) {
        editor.registerBiometricLock(LOCK_BIOMETRIC)
        save()
    }

    @Throws
    fun enableBiometricLogin(currentPassword: String) {
        try {
            val session = localAuthenticator.authenticate(currentPassword)
            session.edit {
                registerBiometricLock(LOCK_BIOMETRIC)
                save()
            }
        } catch (e: LockException) {
            throw WrongPinCodeException(remainingAttempts = 0)
        }
    }

    fun disableBiometricLogin() {
        localAuthenticator.unregisterLock(LOCK_BIOMETRIC)
        save()
    }

    @Throws
    fun changePassword(current: String): ChangePassword {
        try {
            val session = localAuthenticator.authenticate(current)
            return ChangePassword { newPassword ->
                session.edit {
                    registerPasswordLock(LOCK_PASSWORD, newPassword)
                    save()
                }
            }
        } catch (e: LockException) {
            throw WrongPinCodeException(remainingAttempts = 0)
        }
    }

}

