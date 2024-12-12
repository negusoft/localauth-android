package com.negusoft.localauth.vault.lock

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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

open class PinLockException(message: String, cause: Throwable? = null)
    : VaultLockException(message, cause)
class WrongPinException : PinLockException("Wrong PIN code.")

class PinLock(
    val keystoreAlias: String,
    private val encryptedSecret: ByteArray
): VaultLock<PinLock.Input>, VaultLockSync<PinLock.Input> {

    @JvmInline
    value class Input(val value: String)

    companion object {
        private const val ENCODING_VERSION: Byte = 0x00

        private fun keySpec(alias: String, strongBoxBacked: Boolean) =
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setAES_GCM_NoPadding()
                .setStrongBoxBacked(strongBoxBacked)
                .build()

        @Throws(PinLockException::class)
        internal fun register(
            keystoreAlias: String,
            privateKeyEncoded: ByteArray,
            pin: String
        ): PinLock {
            try {
                val privateKeyPasswordEncrypted = Ciphers.AES_GCM_NoPadding.encryptWithPassword(pin, privateKeyEncoded)
                val spec = keySpec(keystoreAlias, false)
                val key = AndroidKeyStore().generateSecretKey(spec)
                val encryptedData = Ciphers.AES_GCM_NoPadding.encrypter(key).encrypt(privateKeyPasswordEncrypted)
                return PinLock(keystoreAlias, encryptedData)
            } catch (t: Throwable) {
                throw PinLockException("Failed to create PIN lock.", t)
            }
        }

        /**
         * Restore the lock from the data produced by 'encode()'.
         * @throws PinLockException on failure.
         */
        @Throws(PinLockException::class)
        fun restore(encoded: ByteArray): PinLock {
            val decoder = ByteCoding.decode(encoded)
            if (!decoder.checkValueEquals(byteArrayOf(ENCODING_VERSION))) {
                throw PinLockException("Wrong encoding version (${encoded[0]}).")
            }
            val alias = decoder.readStringProperty() ?: throw PinLockException("Failed to decode 'alias'.")
            val encryptedSecret = decoder.readFinal()
            return PinLock(alias, encryptedSecret)
        }
    }

    /** Encode the pin lock to bytes. */
    fun encode(): ByteArray {
        return ByteCoding.encode(prefix = byteArrayOf(ENCODING_VERSION)) {
            writeProperty(keystoreAlias)
            writeValue(encryptedSecret)
        }
    }

    /**
     * Returns the secret on success.
     */
    @Throws(PinLockException::class)
    override suspend fun unlock(input: Input): ByteArray
        = unlockSync(input)

    /**
     * Returns the secret on success.
     */
    override fun unlockSync(input: Input): ByteArray {
        try {
            val key = AndroidKeyStore().getSecretKey(keystoreAlias)
            val encryptedSecret = Ciphers.AES_GCM_NoPadding.decrypter(key, encryptedSecret).decrypt(encryptedSecret)
            val privateKeyBytes = Ciphers.AES_GCM_NoPadding.decryptWithPassword(input.value, encryptedSecret)
                ?: throw WrongPinException()
            return privateKeyBytes
        } catch (t: Throwable) {
            throw PinLockException("Failed to unlock secret with PIN", t)
        }
    }
}

/**
 * Opens the vault with the given lock.
 * Throws VaultLockException (or a a subclass of it) on lock failure.
 * It might throw a VaultException if an invalid key was decoded.
 */
fun LocalVault.open(lock: PinLock, pin: String): OpenVault {
    return openSync(lock, PinLock.Input(pin))
}
fun OpenVault.registerPinLock(id: String, pin: String): PinLock = registerLockSync { privateKeyEncoded ->
    PinLock.register(
        keystoreAlias = id,
        privateKeyEncoded = privateKeyEncoded,
        pin = pin
    )
}