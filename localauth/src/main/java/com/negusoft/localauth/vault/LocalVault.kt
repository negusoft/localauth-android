package com.negusoft.localauth.vault

import com.negusoft.localauth.crypto.Ciphers
import com.negusoft.localauth.crypto.Keys
import com.negusoft.localauth.persistence.ByteCoding
import com.negusoft.localauth.persistence.readStringProperty
import com.negusoft.localauth.persistence.writeProperty
import com.negusoft.localauth.lock.LockException
import com.negusoft.localauth.lock.LockProtected
import com.negusoft.localauth.lock.LockRegister
import com.negusoft.localauth.serialization.PublicKeyX509Serializer
import kotlinx.serialization.Serializable
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

@JvmInline
@Serializable
value class EncryptedValue(val bytes: ByteArray)

class LocalVaultException(message: String, cause: Throwable? = null): Exception(message, cause)

@Serializable
class LocalVault private constructor(
    @Serializable(with = PublicKeyX509Serializer::class) val publicKey: PublicKey,
    val keyType: String?
): LockProtected {
    companion object {
        private const val ENCODING_VERSION: Byte = 0x00

        fun create(keyPair: KeyPair): OpenVault {
            val vault = LocalVault(keyPair.public, null)
            return OpenVault(vault, keyPair.private)
        }

        fun create(): OpenVault = create(Keys.RSA.generateKeyPair())

        fun create(keyPair: KeyPair, config: (OpenVault) -> Unit): LocalVault {
            val openVault = create(keyPair)
                .apply(config)

            openVault.close()

            return openVault.vault
        }

        fun create(config: (OpenVault) -> Unit): LocalVault = create(Keys.RSA.generateKeyPair(), config)

        fun restore(publicKey: PublicKey, keyType: String? = null) = LocalVault(publicKey, keyType)

        @Throws(LocalVaultException::class)
        fun restore(encoded: ByteArray): LocalVault {
            val decoder = ByteCoding.decode(encoded)
            if (!decoder.checkValueEquals(byteArrayOf(ENCODING_VERSION)))
                throw LocalVaultException("Wrong encoding version (${encoded[0]}).")

            val keyType = decoder.readStringProperty()
            assert(keyType.isNullOrBlank()) { "Invalid key type." }

            val keyBytes = decoder.readFinal()
            val publicKey = Keys.RSA.decodePublicKey(keyBytes) ?: throw LocalVaultException("Failed to decode public key")

            return LocalVault(publicKey, keyType)
        }
    }

    class OpenVault(
        val vault: LocalVault,
        internal val privateKey: PrivateKey
    ): LockRegister {
        @Throws(LocalVaultException::class)
        fun decrypt(encrypted: EncryptedValue): ByteArray {
            try {
                val decoder = ByteCoding.decode(encrypted.bytes)
                val method = decoder.readStringProperty()
                assert(method.isNullOrBlank()) { "Invalid encryption method." }
                val encryptedData = decoder.readFinal()
                return Ciphers.RSA_ECB_OAEPwithAES_GCM_NoPadding.decrypt(encryptedData, privateKey)
            } catch (e: Throwable) {
                throw LocalVaultException("Failed to decrypt data.", e)
            }
        }

        /**
         * Register a generic lock.
         * The registration creates the lock from the private key bytes, it can
         * throw an exception if the registration was not possible.
         */
        @Throws(LockException::class)
        override suspend fun <Token> registerLockSuspending(locker: suspend (ByteArray) -> Token): Token {
            return locker(privateKey.encoded)
        }

        /**
         * Register a generic lock synchronously.
         * The registration creates the lock from the private key bytes, it can
         * throw an exception if the registration was not possible.
         */
        @Throws(LockException::class)
        override fun <Token> registerLock(locker: (ByteArray) -> Token): Token {
            return locker(privateKey.encoded)
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
    internal fun openSuspending(privateKeyBytes: ByteArray): OpenVault {
        val privateKey = Keys.RSA.decodePrivateKey(privateKeyBytes)
            ?: throw LocalVaultException("Failed to decode private key.")
        return OpenVault(this, privateKey)
    }

    /**
     * Opens the vault with the some unlocker method.
     * The unlocker should return the private key bytes.
     * Throws VaultLockException (or a a subclass of it) on lock failure.
     * It might throw a VaultException if an invalid key was decoded.
     */
    @Throws(LockException::class, LocalVaultException::class)
    override suspend fun openSuspending(unlocker: suspend () -> ByteArray): OpenVault {
        val privateKeyBytes = unlocker()
        return openSuspending(privateKeyBytes)
    }

    /** Synchronous version of openSuspending(). */
    @Throws(LockException::class, LocalVaultException::class)
    override fun open(unlocker: () -> ByteArray): OpenVault {
        val privateKeyBytes = unlocker()
        return openSuspending(privateKeyBytes)
    }

    /** Encrypt the given value with the public key and store it. */
    @Throws(LocalVaultException::class)
    fun encrypt(value: ByteArray, method: String? = null): EncryptedValue {
        try {
            assert(method.isNullOrBlank()) { "Invalid encryption method."}
            val encrypted = Ciphers.RSA_ECB_OAEPwithAES_GCM_NoPadding.encrypt(value, publicKey)
            val bytes = ByteCoding.encode {
                writeProperty(method)
                writeValue(encrypted)
            }
            return EncryptedValue(bytes)
        } catch (e: Throwable) {
            throw LocalVaultException("Failed to encrypt data.", e)
        }
    }

    fun encode() = ByteCoding.encode(byteArrayOf(ENCODING_VERSION)) {
        writeProperty(keyType)
        writeValue(publicKey.encoded)
    }
}