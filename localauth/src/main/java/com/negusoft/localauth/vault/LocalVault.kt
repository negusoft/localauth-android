package com.negusoft.localauth.vault

import com.negusoft.localauth.crypto.Ciphers
import com.negusoft.localauth.crypto.Keys
import com.negusoft.localauth.persistence.ByteCoding
import com.negusoft.localauth.persistence.ByteCodingException
import com.negusoft.localauth.vault.lock.VaultLock
import com.negusoft.localauth.vault.lock.VaultLockException
import com.negusoft.localauth.vault.lock.VaultLockSync
import java.security.PrivateKey
import java.security.PublicKey

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
            val masterKeyPair = Keys.RSA.generateKeyPair()
            val vault = LocalVault(masterKeyPair.public)
            return OpenVault(vault, masterKeyPair.private)
        }

        fun create(config: (OpenVault) -> Unit): LocalVault {
            // generate master key pair
            val masterKeyPair = Keys.RSA.generateKeyPair()
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
            return Keys.RSA.decodePublicKey(keyBytes) ?: throw LocalVaultException("Failed to decode public key")
        }
    }

    class OpenVault(
        val vault: LocalVault,
        internal val privateKey: PrivateKey
    ) {
        @Throws(LocalVaultException::class)
        fun decrypt(encrypted: EncryptedValue): ByteArray {
            try {
                val encryptedData = encrypted.encryptedData
                val encryptedSecretKey = encrypted.encryptedKey

                // If no secret key -> Directly decrypt with the private key
                if (encryptedSecretKey == null) {
                    return Ciphers.RSA_ECB_OAEP.decrypt(encryptedData, privateKey)
                }

                // Decrypt the secret
                val secretKeyEncoded = Ciphers.RSA_ECB_OAEP.decrypt(encryptedSecretKey, privateKey)
                val secretKey = Keys.AES.decodeSecretKey(secretKeyEncoded)

                // Use the secret key to decrypt the value
                return Ciphers.AES_GCM_NoPadding.decrypt(encryptedData, secretKey)
            } catch (e: Throwable) {
                throw LocalVaultException("Failed to decrypt data.", e)
            }
        }

        /**
         * Register a generic lock.
         * The registration creates the lock from the private key bytes, it can
         * throw an exception if the registration was not possible.
         */
        internal suspend fun <Input, Vault: VaultLock<Input>> registerLock(
            registration: suspend (ByteArray) -> Vault
        ): Vault {
            val privateKeyBytes = privateKey.encoded
            return registration(privateKeyBytes)
        }

        /**
         * Register a generic lock synchronously.
         * The registration creates the lock from the private key bytes, it can
         * throw an exception if the registration was not possible.
         */
        internal fun <Input, Vault: VaultLock<Input>> registerLockSync(
            registration: (ByteArray) -> Vault
        ): Vault {
            val privateKeyBytes = privateKey.encoded
            return registration(privateKeyBytes)
        }

        fun close() {
            // TODO
//            privateKey.destroy()
        }
    }

    /**
     * Opens the vault with the given lock.
     * It might throw a VaultException if an invalid key was decoded.
     */
    @Throws(LocalVaultException::class)
    internal fun open(privateKeyBytes: ByteArray): OpenVault {
        val privateKey = Keys.RSA.decodePrivateKey(privateKeyBytes)
            ?: throw LocalVaultException("Failed to decode private key.")
        return OpenVault(this, privateKey)
    }

    /**
     * Opens the vault with the given lock.
     * Throws VaultLockException (or a a subclass of it) on lock failure.
     * It might throw a VaultException if an invalid key was decoded.
     */
    @Throws(VaultLockException::class, LocalVaultException::class)
    suspend fun <Input> open(lock: VaultLock<Input>, input: Input): OpenVault {
        val privateKeyBytes = lock.unlock(input)
        return open(privateKeyBytes)
    }

    /** Synchronous version of open(). */
    @Throws(VaultLockException::class, LocalVaultException::class)
    fun <Input> openSync(lock: VaultLockSync<Input>, input: Input): OpenVault {
        val privateKeyBytes = lock.unlockSync(input)
        return open(privateKeyBytes)
    }

    /** Encrypt the given value with the public key and store it. */
    @Throws(LocalVaultException::class)
    fun encrypt(value: ByteArray): EncryptedValue {
        try {
            // If the data is small enough -> Encrypt it with the public key directly
            val maxDataSize = Ciphers.RSA_ECB_OAEP.maxEncryptDataSize(publicKey) ?: 0
            if (value.size <= maxDataSize) {
                val encrypted = Ciphers.RSA_ECB_OAEP.encrypt(value, publicKey)
                return EncryptedValue(null, encrypted)
            }

            // Generate a secret key to encrypt the value
            val secretKey = Keys.AES.generateSecretKey()
            val encryptedData = Ciphers.AES_GCM_NoPadding.encrypt(value, secretKey)

            // Encrypt the secret key with the public key
            val encryptedSecretKey = Ciphers.RSA_ECB_OAEP.encrypt(secretKey.encoded, publicKey)

            return EncryptedValue(encryptedSecretKey, encryptedData)
        } catch (e: Throwable) {
            throw LocalVaultException("Failed to encrypt data.", e)
        }
    }

    fun encode() = ByteCoding.encode(byteArrayOf(ENCODING_VERSION)) {
        writeValue(publicKey.encoded)
    }
}