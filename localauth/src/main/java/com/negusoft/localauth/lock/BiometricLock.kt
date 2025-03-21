package com.negusoft.localauth.lock

import androidx.fragment.app.FragmentActivity
import com.negusoft.localauth.keystore.BiometricHelper
import com.negusoft.localauth.vault.LocalVaultException
import kotlinx.serialization.Serializable
import java.security.KeyPair
import javax.crypto.Cipher

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

        /** Create with default params. */
        @Throws(LockException::class)
        fun create(
            keystoreAlias: String,
            useStrongBoxWhenAvailable: Boolean = true,
            invalidatedByBiometricEnrollment: Boolean = true
        ): BiometricLock {
            val keyPair = KeyStoreLockCommons.KeyPairLock.createKeyPairRSA_OAEPPadding_BiometricAuth(keystoreAlias, useStrongBoxWhenAvailable, invalidatedByBiometricEnrollment)
            return BiometricLock(keyPair, keystoreAlias, null)
        }

        @Throws(LockException::class)
        fun create(
            keyPair: KeyPair,
            keyIdentifier: String,
            encryptionMethod: String? = null
        ): BiometricLock {
            return BiometricLock(keyPair, keyIdentifier, encryptionMethod)
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

    suspend fun unlock(
        token: Token,
        activity: FragmentActivity,
        promptConfig: BiometricHelper.PromptConfig
    ): ByteArray = unlock(token) authenticator@{ lockedCipher ->
        return@authenticator BiometricHelper.showBiometricPrompt(
            activity = activity,
            cipher = lockedCipher,
            config = promptConfig
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
    activity: FragmentActivity,
    promptConfig: BiometricHelper.PromptConfig
) = openSuspending {
    val lock = BiometricLock.restore(token)
    lock.unlock(token, activity, promptConfig)
}
fun LockRegister.registerBiometricLock(
    keystoreAlias: String,
    useStrongBoxWhenAvailable: Boolean = true,
    invalidatedByBiometricEnrollment: Boolean = true
) : BiometricLock.Token = registerLock { privateKeyEncoded ->
    val lock = BiometricLock.create(keystoreAlias, useStrongBoxWhenAvailable, invalidatedByBiometricEnrollment)
    lock.lock(privateKeyEncoded)
}