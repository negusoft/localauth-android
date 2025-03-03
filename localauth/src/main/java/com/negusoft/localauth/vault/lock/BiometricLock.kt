package com.negusoft.localauth.vault.lock

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.fragment.app.FragmentActivity
import com.negusoft.localauth.crypto.Ciphers
import com.negusoft.localauth.keystore.AndroidKeyStore
import com.negusoft.localauth.keystore.BiometricHelper
import com.negusoft.localauth.keystore.setBiometricAuthenticated
import com.negusoft.localauth.keystore.setRSA_OAEPPadding
import com.negusoft.localauth.keystore.setStrongBoxBacked
import com.negusoft.localauth.persistence.ByteCoding
import com.negusoft.localauth.persistence.readStringProperty
import com.negusoft.localauth.persistence.writeProperty
import com.negusoft.localauth.vault.LocalVault
import com.negusoft.localauth.vault.LocalVault.OpenVault
import com.negusoft.localauth.vault.LocalVaultException
import java.security.KeyPair
import javax.crypto.Cipher

class BiometricLockException(message: String, val reason: Reason = Reason.ERROR, cause: Throwable? = null)
    : LockException(message, cause) {
        enum class Reason { CANCELLATION, ERROR }
    }

class BiometricLock(
    val keystoreAlias: String
) {
    data class Token(
        val keystoreAlias: String,
        val encryptedSecret: ByteArray
    ) {
        val lock: BiometricLock get() = restore(keystoreAlias)

        companion object {
            private const val ENCODING_VERSION: Byte = 0x00

            /**
             * Restore the token from the data produced by 'encode()'.
             * @throws BiometricLockException on failure.
             */
            @Throws(BiometricLockException::class)
            fun restore(encoded: ByteArray): Token {
                val decoder = ByteCoding.decode(encoded)
                if (!decoder.checkValueEquals(byteArrayOf(ENCODING_VERSION))) {
                    throw BiometricLockException("Wrong encoding version (${encoded[0]}).")
                }
                val alias = decoder.readStringProperty() ?: throw BiometricLockException("Failed to decode 'alias'.")
                val encryptedSecret = decoder.readFinal()
                return Token(alias, encryptedSecret)
            }

        }

        /** Encode the token to bytes. */
        fun encode(): ByteArray {
            return ByteCoding.encode(prefix = byteArrayOf(ENCODING_VERSION)) {
                writeProperty(keystoreAlias)
                writeValue(encryptedSecret)
            }
        }
    }

    companion object {

        fun create(keystoreAlias: String, useStrongBoxWhenAvailable: Boolean = true): BiometricLock {
            try {
                createKey(keystoreAlias, useStrongBoxWhenAvailable)
                return BiometricLock(keystoreAlias)
            } catch (t: Throwable) {
                throw LockException("Failed to create Biometric lock.", t)
            }
        }

        fun restore(keystoreAlias: String) = BiometricLock(keystoreAlias)
        fun restore(token: Token) = BiometricLock(token.keystoreAlias)
    }


    fun lock(secret: ByteArray): Token {
        try {
            val keyPair = AndroidKeyStore().getKeyPair(keystoreAlias)
            val encryptedSecret = Ciphers.RSA_ECB_OAEPwithAES_GCM_NoPadding.encrypt(secret, keyPair.public)
            return Token(keystoreAlias, encryptedSecret)
        } catch (e: Throwable) {
            throw BiometricLockException("Failed to create Biometric lock.", reason = BiometricLockException.Reason.ERROR, e)
        }
    }

    suspend fun unlock(token: Token, authenticator: suspend (Cipher) -> Cipher): ByteArray {
        try {
            val keyPair = AndroidKeyStore().getKeyPair(token.keystoreAlias)
            return Ciphers.RSA_ECB_OAEPwithAES_GCM_NoPadding
                .decrypter(keyPair.private)
                .decrypt(token.encryptedSecret, authenticator)
        } catch (e: BiometricLockException) {
            throw e
        } catch (t: Throwable) {
            throw BiometricLockException("Failed to unlock secret with Biometric", reason = BiometricLockException.Reason.ERROR, t)
        }
    }

    suspend fun unlock(token: Token, activity: FragmentActivity): ByteArray = unlock(token) authenticator@{ lockedCipher ->
        return@authenticator BiometricHelper.showBiometricPrompt(
            activity = activity,
            cipher = lockedCipher,
            title = "Unlock vault",
            cancelText = "Cancel"
        ) ?: throw BiometricLockException("Biometric authentication cancelled.", reason = BiometricLockException.Reason.CANCELLATION)
    }

}

/**
 * Create StrongBox backed key. Fall back to non StrongBox backed key.
 */
private fun createKey(keystoreAlias: String, useStrongBox: Boolean): KeyPair {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !useStrongBox) {
        val spec = keySpec(keystoreAlias, false)
        return AndroidKeyStore().generateKeyPair(spec)
    }
    try {
        val spec = keySpec(keystoreAlias, true)
        return AndroidKeyStore().generateKeyPair(spec)
    } catch (e: StrongBoxUnavailableException) {
        val spec = keySpec(keystoreAlias, false)
        return AndroidKeyStore().generateKeyPair(spec)
    }
}

private fun keySpec(alias: String, strongBoxBacked: Boolean): KeyGenParameterSpec =
    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setRSA_OAEPPadding()
        .setBiometricAuthenticated()
        .setStrongBoxBacked(strongBoxBacked)
        .build()

/**
 * Opens the vault with the given lock.
 * Throws VaultLockException (or a a subclass of it) on lock failure.
 * It might throw a LocalVaultException if an invalid key was decoded.
 */
@Throws(BiometricLockException::class, LocalVaultException::class)
suspend fun LockProtected.open(
    token: BiometricLock.Token,
    authenticator: suspend (Cipher) -> Cipher
) = openSuspending {
    val lock = BiometricLock.restore(token)
    lock.unlock(token, authenticator)
}
@Throws(BiometricLockException::class, LocalVaultException::class)
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