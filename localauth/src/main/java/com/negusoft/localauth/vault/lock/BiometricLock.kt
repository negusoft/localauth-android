package com.negusoft.localauth.vault.lock

import androidx.fragment.app.FragmentActivity
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
    private val encryptedSecret: ByteArray
): VaultLock<BiometricLock.Input> {

    @JvmInline
    value class Input(val activity: FragmentActivity)

    companion object {
        private const val ENCODING_VERSION: Byte = 0x00

        @Throws(BiometricLockException::class)
        internal suspend fun register(
            keystoreAlias: String,
            privateKeyEncoded: ByteArray,
            activity: FragmentActivity
        ): BiometricLock {
            try {
                val entry = KeyStoreAccess.getEntry(
                    alias = keystoreAlias,
                    protection = KeyStoreAccess.Protection.BIOMETRIC,
                    isStrongBoxBacked = false
                )
                val lockedCipher = entry.getEncryptCipherAES()
                val unlockedCipher = BiometricHelper.showBiometricPrompt(
                    activity = activity,
                    cipher = lockedCipher,
                    title = "Unlock vault",
                    cancelText = "Cancel"
                ) ?: throw BiometricLockException("Biometric authentication cancelled.", reason = BiometricLockException.Reason.CANCELLATION)
                val encryptedData = unlockedCipher.encrypt(privateKeyEncoded)
                return BiometricLock(keystoreAlias, encryptedData)
            } catch (t: Throwable) {
                throw BiometricLockException("Failed to create Biometric lock.", reason = BiometricLockException.Reason.ERROR, t)
            }
        }

        private const val IV_SIZE_IN_BYTES = 12
        private fun Cipher.encrypt(data: ByteArray): ByteArray {
            val outputSize = getOutputSize(data.size)
            val output = ByteArray(IV_SIZE_IN_BYTES + outputSize)
            doFinal(data, 0, data.size, output, IV_SIZE_IN_BYTES)
            iv.copyInto(output, 0)
            return output
        }
        private fun Cipher.decrypt(data: ByteArray): ByteArray? {
            try {
                return doFinal(data, IV_SIZE_IN_BYTES, data.size - IV_SIZE_IN_BYTES)
            } catch (e: Throwable) {
                e.printStackTrace()
                return null
            }
        }

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
            val encryptedSecret = decoder.readFinal()
            return BiometricLock(alias, encryptedSecret)
        }
    }

    /** Encode the lock to bytes. */
    fun encode(): ByteArray {
        return ByteCoding.encode(prefix = byteArrayOf(ENCODING_VERSION)) {
            writeProperty(keystoreAlias)
            writeValue(encryptedSecret)
        }
    }
    
    /**
     * Returns the secret on success.
     */
    @Throws(BiometricLockException::class)
    override suspend fun unlock(input: Input): ByteArray {
        try {
            val keystoreEntry = KeyStoreAccess.getEntry(
                alias = keystoreAlias,
                protection = KeyStoreAccess.Protection.BIOMETRIC,
                isStrongBoxBacked = false
            )
            val lockedCipher = keystoreEntry.getDecryptCipherAES(encryptedSecret) ?: throw BiometricLockException("No cipher available for key '$keystoreAlias'.")
            val unlockedCipher = BiometricHelper.showBiometricPrompt(
                activity = input.activity,
                cipher = lockedCipher,
                title = "Unlock vault",
                cancelText = "Cancel"
            ) ?: throw BiometricLockException("Biometric authentication cancelled.", reason = BiometricLockException.Reason.CANCELLATION)
            val privateKeyBytes = unlockedCipher.decrypt(encryptedSecret) ?: throw BiometricLockException("Failed to decrypt secret.")
            return privateKeyBytes
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
suspend fun OpenVault.registerBiometricLock(id: String, activity: FragmentActivity): BiometricLock = registerLock { privateKeyEncoded ->
    BiometricLock.register(
        keystoreAlias = id,
        privateKeyEncoded = privateKeyEncoded,
        activity = activity
    )
}