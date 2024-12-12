package com.negusoft.localauth.vault.lock

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.fragment.app.FragmentActivity
import com.negusoft.localauth.crypto.Ciphers
import com.negusoft.localauth.crypto.Keys
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
        private fun keySpec(alias: String, strongBoxBacked: Boolean): KeyGenParameterSpec =
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setRSA_OAEPPadding()
                .setBiometricAuthenticated()
                .setStrongBoxBacked(strongBoxBacked)
                .build()

        @Throws(BiometricLockException::class)
        internal fun register(
            keystoreAlias: String,
            privateKeyEncoded: ByteArray
        ): BiometricLock {
            try {
                val spec = keySpec(keystoreAlias, false)
                val key = AndroidKeyStore().generateKeyPair(spec)
                val intermediateKey = Keys.AES.generateSecretKey()
                val encryptedPrivateKey = Ciphers.AES_GCM_NoPadding.encrypt(privateKeyEncoded, intermediateKey)
                val encryptedIntermediateKey = Ciphers.RSA_ECB_OAEP.encrypt(intermediateKey.encoded, key.public)
                return BiometricLock(keystoreAlias, encryptedIntermediateKey, encryptedPrivateKey)
            } catch (e: Throwable) {
                throw BiometricLockException("Failed to create Biometric lock.", reason = BiometricLockException.Reason.ERROR, e)
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
            val key = AndroidKeyStore().getKeyPair(keystoreAlias)
            val intermediateKeyBytes = Ciphers.RSA_ECB_OAEP.decrypter(key.private).decrypt(encryptedPrivateKey, authenticator)
            val intermediateKey = Keys.AES.decodeSecretKey(intermediateKeyBytes)
            val privateKeyBytes = Ciphers.AES_GCM_NoPadding.decrypt(encryptedPrivateKey, intermediateKey)
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