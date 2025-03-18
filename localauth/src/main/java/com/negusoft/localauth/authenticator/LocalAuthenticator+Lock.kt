package com.negusoft.localauth.authenticator

import androidx.fragment.app.FragmentActivity
import com.negusoft.localauth.authenticator.LocalAuthenticator.Session
import com.negusoft.localauth.coding.encode
import com.negusoft.localauth.coding.restore
import com.negusoft.localauth.lock.BiometricLock
import com.negusoft.localauth.lock.EncodedLockToken
import com.negusoft.localauth.lock.LockProtected
import com.negusoft.localauth.lock.Password
import com.negusoft.localauth.lock.PasswordLock
import com.negusoft.localauth.lock.SimpleLock
import com.negusoft.localauth.lock.WrongPasswordException
import com.negusoft.localauth.lock.open
import com.negusoft.localauth.lock.registerBiometricLock
import com.negusoft.localauth.lock.registerPasswordLock
import com.negusoft.localauth.lock.registerSimpleLock
import com.negusoft.localauth.utils.toByteArray
import com.negusoft.localauth.utils.toInt
import com.negusoft.localauth.vault.LocalVault
import javax.crypto.Cipher

/************************** BIOMETRIC LOCK ************************************/

fun LocalAuthenticator.Editor.registerSimpleLock(lockId: String) {
    registerLock(
        lockId = lockId,
        encoder = { EncodedLockToken(it.encode()) }
    ) { authenticator, register ->
        register.registerSimpleLock("${authenticator.id}_$lockId")
    }
}
fun LocalAuthenticator.Unlockers.simpleLock() = object :
    LocalAuthenticator.Unlocker<SimpleLock.Token> {
    override fun decode(bytes: ByteArray) = SimpleLock.Token.restore(bytes)
    override fun unlock(
        local: LocalAuthenticator,
        lockId: String,
        token: SimpleLock.Token,
        protected: LockProtected
    ): LocalVault.OpenVault {
        return protected.open(token)
    }
}

fun LocalAuthenticator.authenticateWithSimpleLock(lockId: String)
        = authenticate(lockId, LocalAuthenticator.Unlockers.simpleLock())

fun <Result> LocalAuthenticator.authenticatedWithSimpleLock(lockId: String, session: Session.() -> Result)
        = authenticated(lockId, LocalAuthenticator.Unlockers.simpleLock(), session)

/************************** PASSWORD LOCK ************************************/

class WrongPasswordWithMaxAttemptsException(val attemptsRemaining: Int) : WrongPasswordException()

fun LocalAuthenticator.Editor.registerPasswordLock(lockId: String, password: Password, maxAttempts: Int? = null) {
    registerLock(
        lockId = lockId,
        encoder = { EncodedLockToken(it.encode()) }
    ) { authenticator, register ->
        val result = register.registerPasswordLock(password, "${authenticator.id}_$lockId")
        if (maxAttempts != null) {
            authenticator.resetPasswordLockRetryAttempts(lockId, maxAttempts)
        }
        return@registerLock result
    }
}

/**
 * When max attempts mechanism is used, the authenticator will be updated, so save() must be called
 * in order to save the changes.
 */
@Throws(WrongPasswordException::class, WrongPasswordWithMaxAttemptsException::class)
fun LocalAuthenticator.Unlockers.passwordLock(password: Password, maxAttempts: Int? = null) = object :
    LocalAuthenticator.Unlocker<PasswordLock.Token> {
    override fun decode(bytes: ByteArray) = PasswordLock.Token.restore(bytes)
    override fun unlock(
        local: LocalAuthenticator,
        lockId: String,
        token: PasswordLock.Token,
        protected: LockProtected
    ): LocalVault.OpenVault {
        val remainingAttempts = local.getPasswordLockRetryAttempts(lockId)
        if (remainingAttempts != null) {
            if (remainingAttempts <= 0)
                throw WrongPasswordWithMaxAttemptsException(remainingAttempts)
        }
        try {
            val result = protected.open(token, password)
            if (maxAttempts != null) {
                local.resetPasswordLockRetryAttempts(lockId, maxAttempts)
            }
            return result
        } catch (e: WrongPasswordWithMaxAttemptsException) {
            if (remainingAttempts == null)
                throw e

            val remainingNew = remainingAttempts - 1
            local.resetPasswordLockRetryAttempts(lockId, remainingNew)
            throw WrongPasswordWithMaxAttemptsException(remainingNew)
        }
    }
}

fun LocalAuthenticator.resetPasswordLockRetryAttempts(lockId: String, attempts: Int) {
    updatePublicProperty("${lockId}_attempts_remaining", attempts.toByteArray())
}
fun LocalAuthenticator.getPasswordLockRetryAttempts(lockId: String): Int? {
    return publicProperty("${lockId}_attempts_remaining")?.toInt()
}

fun LocalAuthenticator.authenticateWithPasswordLock(lockId: String, password: Password, maxAttempts: Int? = null)
        = authenticate(lockId, LocalAuthenticator.Unlockers.passwordLock(password, maxAttempts))

fun <Result> LocalAuthenticator.authenticatedWithPasswordLock(lockId: String, password: Password, maxAttempts: Int? = null, session: Session.() -> Result)
        = authenticated(lockId, LocalAuthenticator.Unlockers.passwordLock(password, maxAttempts), session)

/************************** BIOMETRIC LOCK ************************************/

fun LocalAuthenticator.Editor.registerBiometricLock(lockId: String) {
    registerLock(
        lockId = lockId,
        encoder = { EncodedLockToken(it.encode()) }
    ) { authenticator, register ->
        register.registerBiometricLock("${authenticator.id}_$lockId")
    }
}
fun LocalAuthenticator.Unlockers.biometricLock(authenticator: suspend (Cipher) -> Cipher) = object :
    LocalAuthenticator.UnlockerSuspending<BiometricLock.Token> {
    override fun decode(bytes: ByteArray) = BiometricLock.Token.restore(bytes)
    override suspend fun unlock(
        local: LocalAuthenticator,
        lockId: String,
        token: BiometricLock.Token,
        protected: LockProtected
    ): LocalVault.OpenVault {
        return protected.open(token, authenticator)
    }
}
fun LocalAuthenticator.Unlockers.biometricLock(activity: FragmentActivity) = object :
    LocalAuthenticator.UnlockerSuspending<BiometricLock.Token> {
    override fun decode(bytes: ByteArray) = BiometricLock.Token.restore(bytes)
    override suspend fun unlock(
        local: LocalAuthenticator,
        lockId: String,
        token: BiometricLock.Token,
        protected: LockProtected
    ): LocalVault.OpenVault {
        return protected.open(token, activity)
    }
}
suspend fun LocalAuthenticator.authenticateWithBiometricLock(lockId: String, authenticator: suspend (Cipher) -> Cipher)
        = authenticateSuspending(lockId, LocalAuthenticator.Unlockers.biometricLock(authenticator))
suspend fun LocalAuthenticator.authenticateWithBiometricLock(lockId: String, activity: FragmentActivity)
        = authenticateSuspending(lockId, LocalAuthenticator.Unlockers.biometricLock(activity))

suspend fun <Result> LocalAuthenticator.authenticatedWithBiometricLock(lockId: String, authenticator: suspend (Cipher) -> Cipher, session: suspend Session.() -> Result)
        = authenticatedSuspending(lockId, LocalAuthenticator.Unlockers.biometricLock(authenticator), session)
suspend fun <Result> LocalAuthenticator.authenticatedWithBiometricLock(lockId: String, activity: FragmentActivity, session: suspend Session.() -> Result)
        = authenticatedSuspending(lockId, LocalAuthenticator.Unlockers.biometricLock(activity), session)
