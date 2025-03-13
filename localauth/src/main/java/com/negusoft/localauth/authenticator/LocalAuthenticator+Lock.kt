package com.negusoft.localauth.authenticator

import androidx.fragment.app.FragmentActivity
import com.negusoft.localauth.authenticator.LocalAuthenticator.Session
import com.negusoft.localauth.coding.encode
import com.negusoft.localauth.coding.restore
import com.negusoft.localauth.lock.BiometricLock
import com.negusoft.localauth.lock.EncodedLockToken
import com.negusoft.localauth.lock.LockProtected
import com.negusoft.localauth.lock.Password
import com.negusoft.localauth.lock.PinLock
import com.negusoft.localauth.lock.SimpleLock
import com.negusoft.localauth.lock.open
import com.negusoft.localauth.lock.registerBiometricLock
import com.negusoft.localauth.lock.registerPinLock
import com.negusoft.localauth.lock.registerSimpleLock
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

/************************** PIN LOCK ************************************/

fun LocalAuthenticator.Editor.registerPasswordLock(lockId: String, password: Password) {
    registerLock(
        lockId = lockId,
        encoder = { EncodedLockToken(it.encode()) }
    ) { authenticator, register ->
        register.registerPinLock(password, "${authenticator.id}_$lockId")
    }
}
fun LocalAuthenticator.Unlockers.passwordLock(password: Password) = object :
    LocalAuthenticator.Unlocker<PinLock.Token> {
    override fun decode(bytes: ByteArray) = PinLock.Token.restore(bytes)
    override fun unlock(
        local: LocalAuthenticator,
        token: PinLock.Token,
        protected: LockProtected
    ): LocalVault.OpenVault {
        return protected.open(token, password)
    }
}

fun LocalAuthenticator.authenticateWithPasswordLock(lockId: String, password: Password)
        = authenticate(lockId, LocalAuthenticator.Unlockers.passwordLock(password))

fun <Result> LocalAuthenticator.authenticatedWithPasswordLock(lockId: String, password: Password, session: Session.() -> Result)
        = authenticated(lockId, LocalAuthenticator.Unlockers.passwordLock(password), session)

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
