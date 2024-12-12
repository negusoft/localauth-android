package com.negusoft.localauth.vault.lock

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
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
import java.security.KeyPair
import javax.crypto.Cipher
import javax.crypto.SecretKey

class BiometricLockException(message: String, val reason: Reason = Reason.ERROR, cause: Throwable? = null)
    : VaultLockException(message, cause) {
        enum class Reason { CANCELLATION, ERROR }
    }

class BiometricLock(
    val keystoreAlias: String,
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
            privateKeyEncoded: ByteArray,
            useStrongBoxWhenAvailable: Boolean = true
        ): BiometricLock {
            try {
                val key = createKey(keystoreAlias, useStrongBoxWhenAvailable)
//                val intermediateKey = Keys.AES.generateSecretKey()
                val encryptedPrivateKey = Ciphers.RSA_ECB_OAEPwithAES_GCM_NoPadding.encrypt(privateKeyEncoded, key.public)
//                val encryptedPrivateKey = Ciphers.AES_GCM_NoPadding.encrypt(privateKeyEncoded, intermediateKey)
//                val encryptedIntermediateKey = Ciphers.RSA_ECB_OAEP.encrypt(intermediateKey.encoded, key.public)
                return BiometricLock(keystoreAlias, encryptedPrivateKey)
            } catch (e: Throwable) {
                throw BiometricLockException("Failed to create Biometric lock.", reason = BiometricLockException.Reason.ERROR, e)
            }
        }

        /**
         * Create StrongBox backed key. Fall back to non StrongBox backed key.
         */
        private fun createKey(keystoreAlias: String, useStrongBox: Boolean): KeyPair {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !useStrongBox) {
                val spec = keySpec(keystoreAlias, false)
                return AndroidKeyStore().generateKeyPair(spec)
            }
            try {
                val spec = keySpec(keystoreAlias, true)
                return AndroidKeyStore().generateKeyPair(spec)
            } catch (e: StrongBoxUnavailableException) {
                val spec = keySpec(keystoreAlias, false)
                return AndroidKeyStore().generateKeyPair(spec)
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
            val privateKeyBytes = Ciphers.RSA_ECB_OAEPwithAES_GCM_NoPadding.decrypter(key.private).decrypt(encryptedPrivateKey, authenticator)
//            val intermediateKeyBytes = Ciphers.RSA_ECB_OAEP.decrypter(key.private).decrypt(encryptedPrivateKey, authenticator)
//            val intermediateKey = Keys.AES.decodeSecretKey(intermediateKeyBytes)
//            val privateKeyBytes = Ciphers.AES_GCM_NoPadding.decrypt(encryptedPrivateKey, intermediateKey)
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
fun OpenVault.registerBiometricLock(
    id: String,
    useStrongBoxWhenAvailable: Boolean = true
): BiometricLock = registerLockSync { privateKeyEncoded ->
    BiometricLock.register(
        keystoreAlias = id,
        privateKeyEncoded = privateKeyEncoded,
        useStrongBoxWhenAvailable = useStrongBoxWhenAvailable
    )
}