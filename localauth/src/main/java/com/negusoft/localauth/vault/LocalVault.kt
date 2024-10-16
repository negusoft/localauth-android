package com.negusoft.localauth.vault

import android.security.keystore.KeyProperties
import androidx.core.content.edit
import com.negusoft.localauth.crypto.CryptoUtils
import com.negusoft.localauth.crypto.CryptoUtilsRSA
import com.negusoft.localauth.keystore.KeyStoreAccess
import com.negusoft.localauth.persistence.ByteCoding
import com.negusoft.localauth.persistence.ByteCodingException
import com.negusoft.localauth.persistence.writeProperty
import com.negusoft.localauth.preferences.getByteArray
import com.negusoft.localauth.preferences.putByteArray
import com.negusoft.localauth.preferences.putEncrypted
import com.negusoft.localauth.vault.LocalVault.Companion
import com.negusoft.localauth.vault.lock.PinLock
import com.negusoft.localauth.vault.lock.PinLockException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.SecretKey


data class EncryptedValue(
    val encryptedKey: ByteArray?,
    val encryptedData: ByteArray
) {

    companion object {
        private const val ENCODING_VERSION: Byte = 0x00

        @Throws(LocalVaultException::class)
        fun decode(encoded: ByteArray): EncryptedValue {
            try {
                val decoder = ByteCoding.decode(encoded)
                if (!decoder.checkValueEquals(byteArrayOf(ENCODING_VERSION))) {
                    throw LocalVaultException("Wrong encoding version (${encoded[0]}).")
                }
                val encryptedKey = decoder.readProperty()
                val encryptedData = decoder.readFinal()
                return EncryptedValue(encryptedKey, encryptedData)
            } catch (e: ByteCodingException) {
                throw LocalVaultException("Failed to decode encrypted value.", e)
            }
        }
    }

    fun encode() = ByteCoding.encode(byteArrayOf(ENCODING_VERSION)) {
        writeProperty(encryptedKey)
        writeValue(encryptedData)
    }

}

class LocalVaultException(message: String, cause: Throwable? = null): Exception(message, cause)

class LocalVault private constructor(
    val publicKey: PublicKey
) {
    companion object {
        private const val ENCODING_VERSION: Byte = 0x00

        fun create(): OpenVault {
            // generate master key pair
            val masterKeyPair = CryptoUtilsRSA.generateKeyPair()
            val vault = LocalVault(masterKeyPair.public)
            return OpenVault(vault, masterKeyPair.private)
        }

        fun create(config: (OpenVault) -> Unit): LocalVault {
            // generate master key pair
            val masterKeyPair = CryptoUtilsRSA.generateKeyPair()
            val vault = LocalVault(masterKeyPair.public)
            val openVault = OpenVault(vault, masterKeyPair.private)
                .apply(config)

            openVault.close()

            return vault
        }

        fun restore(publicKey: PublicKey) = LocalVault(publicKey)

        @Throws(LocalVaultException::class)
        fun restore(encoded: ByteArray): LocalVault {
            val publicKey = decodePublicKey(encoded)
            return LocalVault(publicKey)
        }

        @Throws(LocalVaultException::class)
        private fun decodePublicKey(encoded: ByteArray): PublicKey {
            val decoder = ByteCoding.decode(encoded)
            if (!decoder.checkValueEquals(byteArrayOf(ENCODING_VERSION)))
                throw LocalVaultException("Wrong encoding version (${encoded[0]}).")
            
            val keyBytes = decoder.readFinal()
            return CryptoUtilsRSA.decodePublicKey(keyBytes) ?: throw LocalVaultException("Failed to decode public key")
        }
    }

    class OpenVault(
        val vault: LocalVault,
        private val privateKey: PrivateKey
    ) {
        @Throws(LocalVaultException::class)
        fun decrypt(encrypted: EncryptedValue): ByteArray {
            try {
                val encryptedData = encrypted.encryptedData
                val encryptedSecretKey = encrypted.encryptedKey

                // If no secret key -> Directly decrypt with the private key
                if (encryptedSecretKey == null) {
                    return CryptoUtilsRSA.decrypt(encryptedData, privateKey)
                }

                // Decrypt the secret
                val secretKeyEncoded = CryptoUtilsRSA.decrypt(encryptedSecretKey, privateKey)
                val secretKey = CryptoUtils.decodeSecretKey(secretKeyEncoded)

                // Use the secret key to decrypt the value
                return CryptoUtils.decrypt(encryptedData, secretKey)
            } catch (e: Throwable) {
                throw LocalVaultException("Failed to decrypt data.", e)
            }
        }

        @Throws(LocalVaultException::class)
        fun registerPinLock(lockId: String, pin: String): PinLock = try {
            PinLock.create(lockId, privateKey.encoded, pin)
        } catch (t: Throwable) {
            throw LocalVaultException("Failed to register lock.", t)
        }

        fun close() {
            // TODO
//            privateKey.destroy()
        }
    }

    @Throws(PinLockException::class, LocalVaultException::class)
    fun open(lock: PinLock, pin: String): OpenVault {
        val privateKeyBytes = lock.unlock(pin)
        val privateKey = CryptoUtilsRSA.decodePrivateKey(privateKeyBytes)
            ?: throw LocalVaultException("Failed to decode private key.")
        return OpenVault(this, privateKey)
    }

    /** Encrypt the given value with the public key and store it. */
    @Throws(LocalVaultException::class)
    fun encrypt(value: ByteArray): EncryptedValue {
        try {
            // If the that is small enough -> Encrypt it with the public key directly
            val maxDataSize = CryptoUtilsRSA.maxEncryptDataSize(publicKey) ?: 0
            if (value.size <= maxDataSize) {
                val encrypted = CryptoUtilsRSA.encrypt(value, publicKey)
                return EncryptedValue(null, encrypted)
            }

            // Generate a secret key to encrypt the value
            val secretKey = CryptoUtils.generateSecretKey()
            val encryptedData = CryptoUtils.encrypt(value, secretKey)

            // Encrypt the secret key with the public key
            val encryptedSecretKey = CryptoUtilsRSA.encrypt(secretKey.encoded, publicKey)

            return EncryptedValue(encryptedSecretKey, encryptedData)
        } catch (e: Throwable) {
            throw LocalVaultException("Failed to encrypt data.", e)
        }
    }

    fun encode() = ByteCoding.encode(byteArrayOf(ENCODING_VERSION)) {
        writeValue(publicKey.encoded)
    }
}