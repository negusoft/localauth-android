package com.negusoft.localauth.vault.lock

import androidx.fragment.app.FragmentActivity
import com.negusoft.localauth.crypto.CryptoUtils
import com.negusoft.localauth.keystore.BiometricHelper
import com.negusoft.localauth.keystore.KeyStoreAccess
import com.negusoft.localauth.persistence.ByteCoding
import com.negusoft.localauth.persistence.readStringProperty
import com.negusoft.localauth.persistence.writeProperty
import com.negusoft.localauth.vault.LocalVault
import com.negusoft.localauth.vault.LocalVault.OpenVault
import com.negusoft.localauth.vault.LocalVaultException
import javax.crypto.Cipher

class BiometricLockException(message: String, val reason: Reason = Reason.ERROR, cause: Throwable? = null)
    : VaultLockException(message, cause) {
        enum class Reason { CANCELLATION, ERROR }
    }

class BiometricLock(
    val keystoreAlias: String,
    private val encryptedIntermediateKey: ByteArray,
    private val encryptedPrivateKey: ByteArray
): VaultLock<BiometricLock.Input> {

    @JvmInline
    value class Input(val activity: FragmentActivity)

    companion object {
        private const val ENCODING_VERSION: Byte = 0x00

        @Throws(BiometricLockException::class)
        internal fun register(
            keystoreAlias: String,
            privateKeyEncoded: ByteArray
        ): BiometricLock {
            try {
                val entry = KeyStoreAccess.createKeyPairEntry(keystoreAlias, false)
                val intermediateKey = CryptoUtils.generateSecretKey()
                val encryptedPrivateKey = CryptoUtils.encrypt(privateKeyEncoded, intermediateKey)
                val encryptedIntermediateKey = entry.encrypt(intermediateKey.encoded)
                return BiometricLock(keystoreAlias, encryptedIntermediateKey, encryptedPrivateKey)
            } catch (e: Throwable) {
                throw BiometricLockException("Failed to create Biometric lock.", reason = BiometricLockException.Reason.ERROR, e)
            }

//            val lockedCipher = CryptoUtilsRSA.encryptCipher(publicKey = entry.keyPair.public)
//            try {
//                val entry = KeyStoreAccessOld.getEntry(
//                    alias = keystoreAlias,
//                    protection = KeyStoreAccessOld.Protection.BIOMETRIC,
//                    isStrongBoxBacked = false
//                )
//                val lockedCipher = entry.getEncryptCipherAES()
//                val unlockedCipher = BiometricHelper.showBiometricPrompt(
//                    activity = activity,
//                    cipher = lockedCipher,
//                    title = "Unlock vault",
//                    cancelText = "Cancel"
//                ) ?: throw BiometricLockException("Biometric authentication cancelled.", reason = BiometricLockException.Reason.CANCELLATION)
//                val encryptedData = unlockedCipher.encrypt(privateKeyEncoded)
//                return BiometricLock(keystoreAlias, encryptedData)
//            } catch (t: Throwable) {
//                throw BiometricLockException("Failed to create Biometric lock.", reason = BiometricLockException.Reason.ERROR, t)
//            }
        }

//        @Throws(BiometricLockException::class)
//        internal suspend fun register(
//            keystoreAlias: String,
//            privateKeyEncoded: ByteArray,
//            activity: FragmentActivity
//        ): BiometricLock {
//            try {
//                val entry = KeyStoreAccessOld.getEntry(
//                    alias = keystoreAlias,
//                    protection = KeyStoreAccessOld.Protection.BIOMETRIC,
//                    isStrongBoxBacked = false
//                )
//                val lockedCipher = entry.getEncryptCipherAES()
//                val unlockedCipher = BiometricHelper.showBiometricPrompt(
//                    activity = activity,
//                    cipher = lockedCipher,
//                    title = "Unlock vault",
//                    cancelText = "Cancel"
//                ) ?: throw BiometricLockException("Biometric authentication cancelled.", reason = BiometricLockException.Reason.CANCELLATION)
//                val encryptedData = unlockedCipher.encrypt(privateKeyEncoded)
//                return BiometricLock(keystoreAlias, encryptedData)
//            } catch (t: Throwable) {
//                throw BiometricLockException("Failed to create Biometric lock.", reason = BiometricLockException.Reason.ERROR, t)
//            }
//        }

//        private const val IV_SIZE_IN_BYTES = 12
//        private fun Cipher.encrypt(data: ByteArray): ByteArray {
//            val outputSize = getOutputSize(data.size)
//            val output = ByteArray(IV_SIZE_IN_BYTES + outputSize)
//            doFinal(data, 0, data.size, output, IV_SIZE_IN_BYTES)
//            iv.copyInto(output, 0)
//            return output
//        }
//        private fun Cipher.decrypt(data: ByteArray): ByteArray? {
//            try {
//                return doFinal(data, IV_SIZE_IN_BYTES, data.size - IV_SIZE_IN_BYTES)
//            } catch (e: Throwable) {
//                e.printStackTrace()
//                return null
//            }
//        }

        /**
         * Restore the lock from the data produced by 'encode()'.
         * @throws BiometricLockException on failure.
         */
        @Throws(BiometricLockException::class)
        fun restore(encoded: ByteArray): BiometricLock {
            val decoder = ByteCoding.decode(encoded)
            if (!decoder.checkValueEquals(byteArrayOf(ENCODING_VERSION))) {
                throw BiometricLockException("Wrong encoding version (${encoded[0]}).")
            }
            val alias = decoder.readStringProperty() ?: throw BiometricLockException("Failed to decode 'alias'.")
            val encryptedIntermediateKey = decoder.readProperty() ?: throw BiometricLockException("Failed to decode 'intermediate key'.")
            val encryptedSecret = decoder.readFinal()
            return BiometricLock(alias, encryptedIntermediateKey, encryptedSecret)
        }
    }

    /** Encode the lock to bytes. */
    fun encode(): ByteArray {
        return ByteCoding.encode(prefix = byteArrayOf(ENCODING_VERSION)) {
            writeProperty(keystoreAlias)
            writeProperty(encryptedIntermediateKey)
            writeValue(encryptedPrivateKey)
        }
    }
    
    /**
     * Returns the private key bytes on success.
     */
    @Throws(BiometricLockException::class)
    override suspend fun unlock(input: Input): ByteArray {
        return unlock { lockedCipher ->
            return@unlock BiometricHelper.showBiometricPrompt(
                activity = input.activity,
                cipher = lockedCipher,
                title = "Unlock vault",
                cancelText = "Cancel"
            ) ?: throw BiometricLockException("Biometric authentication cancelled.", reason = BiometricLockException.Reason.CANCELLATION)
        }
    }

    @Throws(BiometricLockException::class)
    suspend fun unlock(authenticator: suspend (Cipher) -> Cipher): ByteArray {
        try {
            val entry = KeyStoreAccess.getKeyPairEntry(keystoreAlias, false)
                ?: throw BiometricLockException("No keystore entry for key '$keystoreAlias'.")
            val intermediateKeyBytes = entry.decrypt(
                data = encryptedIntermediateKey,
                authenticator = authenticator
            )
            val intermediateKey = CryptoUtils.decodeSecretKey(intermediateKeyBytes)
            val privateKeyBytes = CryptoUtils.decrypt(encryptedPrivateKey, intermediateKey)
            return privateKeyBytes
        } catch (e: BiometricLockException) {
            throw e
        } catch (t: Throwable) {
            throw BiometricLockException("Failed to unlock secret with Biometric", reason = BiometricLockException.Reason.ERROR, t)
        }
    }
}

/**
 * Opens the vault with the given lock.
 * Throws VaultLockException (or a a subclass of it) on lock failure.
 * It might throw a LocalVaultException if an invalid key was decoded.
 */
@Throws(BiometricLockException::class, LocalVaultException::class)
suspend fun LocalVault.open(activity: FragmentActivity, lock: BiometricLock): OpenVault {
    return open(lock, BiometricLock.Input(activity))
}
fun OpenVault.registerBiometricLock(id: String): BiometricLock = registerLockSync { privateKeyEncoded ->
    BiometricLock.register(
        keystoreAlias = id,
        privateKeyEncoded = privateKeyEncoded
    )
}