package com.negusoft.localauth.vault.lock

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.negusoft.localauth.crypto.Ciphers
import com.negusoft.localauth.crypto.decryptWithPassword
import com.negusoft.localauth.crypto.encryptWithPassword
import com.negusoft.localauth.keystore.AndroidKeyStore
import com.negusoft.localauth.keystore.setAES_GCM_NoPadding
import com.negusoft.localauth.keystore.setStrongBoxBacked
import com.negusoft.localauth.persistence.ByteCoding
import com.negusoft.localauth.persistence.readStringProperty
import com.negusoft.localauth.persistence.writeProperty
import com.negusoft.localauth.vault.LocalVault
import com.negusoft.localauth.vault.LocalVault.OpenVault
import javax.crypto.SecretKey

open class PinLockException(message: String, cause: Throwable? = null)
    : LockException(message, cause)
class WrongPinException : PinLockException("Wrong PIN code.")

class PinLock private constructor(
    val keystoreAlias: String
) {
    data class Token(
        val keystoreAlias: String,
        val encryptedSecret: ByteArray
    ) {
        companion object {
            private const val ENCODING_VERSION: Byte = 0x00

            /**
             * Restore the token from the data produced by 'encode()'.
             * @throws PinLockException on failure.
             */
            @Throws(PinLockException::class)
            fun restore(encoded: ByteArray): Token {
                val decoder = ByteCoding.decode(encoded)
                if (!decoder.checkValueEquals(byteArrayOf(ENCODING_VERSION))) {
                    throw PinLockException("Wrong encoding version (${encoded[0]}).")
                }
                val alias = decoder.readStringProperty() ?: throw PinLockException("Failed to decode 'alias'.")
                val encryptedSecret = decoder.readFinal()
                return Token(alias, encryptedSecret)
            }

        }
        val lock: PinLock get() = PinLock(keystoreAlias)

        /** Encode the token to bytes. */
        fun encode(): ByteArray {
            return ByteCoding.encode(prefix = byteArrayOf(ENCODING_VERSION)) {
                writeProperty(keystoreAlias)
                writeValue(encryptedSecret)
            }
        }
    }

    companion object {

        fun create(keystoreAlias: String, useStrongBoxWhenAvailable: Boolean = true): PinLock {
            try {
                createKey(keystoreAlias, useStrongBoxWhenAvailable)
                return PinLock(keystoreAlias)
            } catch (t: Throwable) {
                throw LockException("Failed to create PIN lock.", t)
            }
        }

        fun restore(keystoreAlias: String) = PinLock(keystoreAlias)
        fun restore(token: Token) = PinLock(token.keystoreAlias)
    }


    fun lock(secret: ByteArray, password: String): Token {
        try {
            val key = AndroidKeyStore().getSecretKey(keystoreAlias)
            val secretPasswordEncrypted = Ciphers.AES_GCM_NoPadding.encryptWithPassword(password, secret)
            val encryptedSecret = Ciphers.AES_GCM_NoPadding.encrypter(key).encrypt(secretPasswordEncrypted)
            return Token(keystoreAlias, encryptedSecret)
        } catch (t: Throwable) {
            throw PinLockException("Failed to lock secret with PIN lock.", t)
        }
    }

    fun unlock(token: Token, password: String): ByteArray {
        try {
            val key = AndroidKeyStore().getSecretKey(keystoreAlias)
            val secretPasswordEncrypted = Ciphers.AES_GCM_NoPadding
                .decrypter(key, token.encryptedSecret)
                .decrypt(token.encryptedSecret)
            val privateKeyBytes = Ciphers.AES_GCM_NoPadding
                .decryptWithPassword(password, secretPasswordEncrypted)
                ?: throw WrongPinException()
            return privateKeyBytes
        } catch (t: Throwable) {
            throw PinLockException("Failed to unlock secret with PIN", t)
        }
    }
}


/**
 * Create StrongBox backed key. Fall back to non StrongBox backed key.
 */
private fun createKey(keystoreAlias: String, useStrongBox: Boolean): SecretKey {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !useStrongBox) {
        val spec = keySpec(keystoreAlias, false)
        return AndroidKeyStore().generateSecretKey(spec)
    }
    try {
        val spec = keySpec(keystoreAlias, true)
        return AndroidKeyStore().generateSecretKey(spec)
    } catch (e: StrongBoxUnavailableException) {
        val spec = keySpec(keystoreAlias, false)
        return AndroidKeyStore().generateSecretKey(spec)
    }
}

private fun keySpec(alias: String, strongBoxBacked: Boolean) =
    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setAES_GCM_NoPadding()
        .setStrongBoxBacked(strongBoxBacked)
        .build()

/**
 * Opens the vault with the given lock.
 * Throws VaultLockException (or a a subclass of it) on lock failure.
 * It might throw a VaultException if an invalid key was decoded.
 */
fun LocalVault.open(token: PinLock.Token, password: String) = open {
    val lock = PinLock.restore(token)
    lock.unlock(token, password)
}
fun OpenVault.registerPinLock(
    password: String, keystoreAlias: String, useStrongBoxWhenAvailable: Boolean = true
): PinLock.Token = registerLock { privateKeyEncoded ->
    val lock = PinLock.create(keystoreAlias, useStrongBoxWhenAvailable)
    lock.lock(privateKeyEncoded, password)
}