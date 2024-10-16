package com.negusoft.localauth.vault.lock

import com.negusoft.localauth.crypto.CryptoUtils
import com.negusoft.localauth.crypto.PasswordEncryptedData
import com.negusoft.localauth.keystore.KeyStoreAccess
import com.negusoft.localauth.persistence.ByteCoding
import com.negusoft.localauth.persistence.readStringProperty
import com.negusoft.localauth.persistence.writeProperty

class PinLockException(message: String, cause: Throwable? = null): Exception(message, cause)

class PinLock(val keystoreAlias: String, private val encryptedSecret: ByteArray) {

    companion object {
        private const val ENCODING_VERSION: Byte = 0x00

        @Throws(PinLockException::class)
        fun create(keystoreAlias: String, secret: ByteArray, pin: String): PinLock {
            try {
                val privateKeyPasswordEncrypted = CryptoUtils.encryptWithPassword(pin, secret).bytes
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

    /**
     * Returns the secret on success.
     */
    @Throws(PinLockException::class)
    fun unlock(pin: String): ByteArray {
        try {
            val pinCodeEntry = KeyStoreAccess.getEntry(
                alias = keystoreAlias,
                protection = KeyStoreAccess.Protection.DEFAULT,
                isStrongBoxBacked = false
            )
            val encryptedSecret = pinCodeEntry.decrypt(encryptedSecret) ?: throw PinLockException("Keystore decryption failed, data might be corrupted.")
            val privateKeyBytes = CryptoUtils.decryptWithPassword(pin, PasswordEncryptedData(encryptedSecret)) ?: throw PinLockException("Wrong PIN code.")
            return privateKeyBytes
        } catch (t: Throwable) {
            throw PinLockException("Failed to unlock secret with PIN", t)
        }
    }

    fun encode(): ByteArray {
        return ByteCoding.encode(prefix = byteArrayOf(ENCODING_VERSION)) {
            writeProperty(keystoreAlias)
            writeValue(encryptedSecret)
        }
    }
}