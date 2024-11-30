package com.negusoft.localauth.vault.lock

import com.negusoft.localauth.crypto.CryptoUtils
import com.negusoft.localauth.crypto.CryptoUtilsRSA
import com.negusoft.localauth.crypto.PasswordEncryptedData
import com.negusoft.localauth.keystore.KeyStoreAccess
import com.negusoft.localauth.persistence.ByteCoding
import com.negusoft.localauth.persistence.readStringProperty
import com.negusoft.localauth.persistence.writeProperty
import com.negusoft.localauth.vault.LocalVault
import com.negusoft.localauth.vault.LocalVault.OpenVault
import com.negusoft.localauth.vault.LocalVaultException

class PinLockException(message: String, cause: Throwable? = null)
    : VaultLockException(message, cause)

class PinLock(
    val keystoreAlias: String,
    private val encryptedSecret: ByteArray
): VaultLock<PinLock.Input>, VaultLockSync<PinLock.Input> {

    @JvmInline
    value class Input(val value: String)

    companion object {
        private const val ENCODING_VERSION: Byte = 0x00

        @Throws(PinLockException::class)
        internal fun register(
            keystoreAlias: String,
            privateKeyEncoded: ByteArray,
            pin: String
        ): PinLock {
            try {
                val privateKeyPasswordEncrypted = CryptoUtils.encryptWithPassword(pin, privateKeyEncoded).bytes
                val entry = KeyStoreAccess.getEntry(
                    alias = keystoreAlias,
                    protection = KeyStoreAccess.Protection.DEFAULT,
                    isStrongBoxBacked = false
                )
                val encryptedData = entry.encrypt(privateKeyPasswordEncrypted)
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
            val pinCodeEntry = KeyStoreAccess.getEntry(
                alias = keystoreAlias,
                protection = KeyStoreAccess.Protection.DEFAULT,
                isStrongBoxBacked = false
            )
            val encryptedSecret = pinCodeEntry.decrypt(encryptedSecret) ?: throw PinLockException("Keystore decryption failed, data might be corrupted.")
            val privateKeyBytes = CryptoUtils.decryptWithPassword(input.value, PasswordEncryptedData(encryptedSecret)) ?: throw PinLockException("Wrong PIN code.")
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