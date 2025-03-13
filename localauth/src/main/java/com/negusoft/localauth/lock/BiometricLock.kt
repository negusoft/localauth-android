package com.negusoft.localauth.lock

import android.security.keystore.KeyGenParameterSpec
import androidx.fragment.app.FragmentActivity
import com.negusoft.localauth.keystore.BiometricHelper
import com.negusoft.localauth.vault.LocalVaultException
import kotlinx.serialization.Serializable
import java.security.KeyPair
import javax.crypto.Cipher

//@Deprecated("deleteme")
//class BiometricLockException(
//    message: String, val reason: Reason = Reason.ERROR, cause: Throwable? = null
//) : LockException(message, cause) {
//    enum class Reason { CANCELLATION, ERROR }
//}
class BiometricPromptCancelledException(message: String) : LockException(message)

class BiometricLock(
    keyPair: KeyPair,
    keyIdentifier: String,
    encryptionMethod: String?
): KeyStoreLockCommons.KeyPairLock(keyPair, keyIdentifier, encryptionMethod) {
    @Serializable
    data class Token(
        val keystoreAlias: String,
        val encryptionMethod: String?,
        val encryptedSecret: ByteArray
    ) {
        companion object

        val lock: BiometricLock get() = restore(keystoreAlias, encryptionMethod)
    }

    companion object {

        @Throws(LockException::class)
        fun create(
            keystoreAlias: String,
            useStrongBoxWhenAvailable: Boolean = true,
            specBuilder: () -> KeyGenParameterSpec.Builder = defaultKeySpecBuilder(keystoreAlias)
        ): BiometricLock {
            val keyPair = KeyStoreLockCommons.KeyPairLock.createKeyPair(useStrongBoxWhenAvailable, specBuilder)
            return BiometricLock(keyPair, keystoreAlias, null)
        }

        fun restore(
            keyPair: KeyPair,
            keyIdentifier: String,
            encryptionMethod: String?
        ): BiometricLock = BiometricLock(keyPair, keyIdentifier, encryptionMethod)

        @Throws(LockException::class)
        fun restore(
            keystoreAlias: String,
            encryptionMethod: String? = null
        ): BiometricLock {
            val keyPair = restoreKeyPair(keystoreAlias)
            return BiometricLock(keyPair, keystoreAlias, encryptionMethod)
        }
        fun restore(token: Token) = restore(token.keystoreAlias, token.encryptionMethod)
    }


    @Throws(LockException::class)
    fun lock(secret: ByteArray) = super.lock(secret) { alias, method, encryptedSecret ->
        Token(alias, method, encryptedSecret)
    }

    @Throws(LockException::class, BiometricPromptCancelledException::class)
    suspend fun unlock(token: Token, authenticator: suspend (Cipher) -> Cipher): ByteArray =
        super.unlock(token.encryptedSecret, authenticator)

    suspend fun unlock(token: Token, activity: FragmentActivity): ByteArray = unlock(token) authenticator@{ lockedCipher ->
        return@authenticator BiometricHelper.showBiometricPrompt(
            activity = activity,
            cipher = lockedCipher,
            title = "Unlock vault",
            cancelText = "Cancel"
        ) ?: throw BiometricPromptCancelledException("Biometric authentication cancelled.")
    }
}

/**
 * Opens the vault with the given lock.
 * Throws VaultLockException (or a a subclass of it) on lock failure.
 * It might throw a LocalVaultException if an invalid key was decoded.
 */
@Throws(BiometricPromptCancelledException::class, LocalVaultException::class)
suspend fun LockProtected.open(
    token: BiometricLock.Token,
    authenticator: suspend (Cipher) -> Cipher
) = openSuspending {
    val lock = BiometricLock.restore(token)
    lock.unlock(token, authenticator)
}
@Throws(LockException::class, LocalVaultException::class)
suspend fun LockProtected.open(
    token: BiometricLock.Token,
    activity: FragmentActivity
) = openSuspending {
    val lock = BiometricLock.restore(token)
    lock.unlock(token, activity)
}
fun LockRegister.registerBiometricLock(
    keystoreAlias: String, useStrongBoxWhenAvailable: Boolean = true
) : BiometricLock.Token = registerLock { privateKeyEncoded ->
    val lock = BiometricLock.create(keystoreAlias, useStrongBoxWhenAvailable)
    lock.lock(privateKeyEncoded)
}