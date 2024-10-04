package com.negusoft.localauth.keystore

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.core.content.edit
import com.negusoft.localauth.preferences.getByteArray
import com.negusoft.localauth.preferences.putByteArray
import java.security.*
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.*

/**
 * Encrypt and store encrypted data using the KeyStore and AES secret key cryptography.
 */
object KeyStoreAccess {

    private const val IV_SIZE_IN_BYTES = 12

    enum class Protection { DEFAULT, BIOMETRIC }

    private val strongBoxCheck = StrongBoxCheck()

    private class StrongBoxCheck {
        private var strongBoxAvailable: Boolean? = null

        fun isAvailable(context: Context): Boolean {
            strongBoxAvailable?.let { return it }
            return checkStrongBoxAvailable(context).also {
                strongBoxAvailable = it
            }
        }

        private fun checkStrongBoxAvailable(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                return false

            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE))
                return false

            // The hasSystemFeature feature is not reliable and some device may fail to create
            // a key, to we need to check by creating one.
            val alias = UUID.randomUUID().toString()
            val keyStore = loadAndroidKeyStore()
            try {
                val keySpecWithStrongBox = aesKeySpec(alias, true)
                generateKeyAES(keySpecWithStrongBox)
                return true
            } catch (e: StrongBoxUnavailableException) {
                e.printStackTrace()
                return false
            } catch (e: ProviderException) {
                e.printStackTrace()
                return false
            } finally {
                keyStore.deleteEntry(alias)
            }
        }
    }

    class Entry(private val spec: KeyGenParameterSpec) {

        fun encrypt(data: ByteArray): ByteArray {
            val alias = spec.keystoreAlias

            // Encrypt the data with using the keystore entry
            val cipher = getEncryptCipherAES(spec)
            return cipher.encrypt(data)
        }

        /**
         * Store the data in the shared preference.
         * The keys in the shared preference are prefixed by the entry alias.
         */
        fun store(data: ByteArray, preferences: SharedPreferences) {
            val alias = spec.keystoreAlias

            // Encrypt the data with using the keystore entry
            val cipher = getEncryptCipherAES(spec)
            val encryptedData = cipher.encrypt(data)

            preferences.edit {
                putByteArray(alias, encryptedData)
            }
        }

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
         * Store the data in the shared preference.
         * The keys in the shared preference are prefixed by the entry alias.
         * The transformations provides the original data, the alias and the preferences editor. The
         * result of the transformation will be encrypted and stored.
         */
        fun store(data: ByteArray, preferences: SharedPreferences, transformation: (ByteArray, String, SharedPreferences.Editor) -> ByteArray) {
            preferences.edit {
                val alias = spec.keystoreAlias

                val transformedData = transformation(data, alias, this)

                // Encrypt the secret key with biometric protection
                val cipher = getEncryptCipherAES(spec)
                val encryptedData = cipher.encrypt(transformedData)

                putByteArray(alias, encryptedData)
            }
        }

        /**
         * Use it when the Entry is protected with Biometrics. The authenticator should show a
         * BiometricPrompt with the given cypher and return the resulting cypher.
         * Returns true if the data was successfully stored, false otherwise.
         */
        suspend fun store(data: ByteArray, preferences: SharedPreferences, authenticator: suspend (Cipher) -> Cipher?): Boolean {
            val alias = spec.keystoreAlias
            val lockedCipher = getEncryptCipherAES(spec)
            val cipher = authenticator(lockedCipher) ?: return false

            preferences.edit {
                val encryptedData = cipher.encrypt(data)
                putByteArray(alias, encryptedData)
            }

            return true
        }

        fun decrypt(encryptedData: ByteArray): ByteArray? {
            val iv = encryptedData.copyOfRange(0, IV_SIZE_IN_BYTES)
            // Decrypt the data using the keystore entry
            val cipher = getDecryptCipherAES(spec, iv) ?: return null
            return cipher.decrypt(encryptedData)
        }

        /** Get the decrypted bytes from shored preferences. */
        fun get(preferences: SharedPreferences): ByteArray? {
            val alias = spec.keystoreAlias
            val encryptedData = preferences.getByteArray(alias) ?: return null
            val iv = encryptedData.copyOfRange(0, IV_SIZE_IN_BYTES)
            // Decrypt the data using the keystore entry
            val cipher = getDecryptCipherAES(spec, iv) ?: return null
            return cipher.decrypt(encryptedData)
        }

        /**
         * Get the decrypted bytes from shored preferences.
         * The provides the alias and the editor to store additional data in the settings.
         */
        fun get(preferences: SharedPreferences, transformation: (ByteArray, String, SharedPreferences) -> ByteArray?): ByteArray? {
            val alias = spec.keystoreAlias
            val encryptedData = preferences.getByteArray(alias) ?: return null
            val iv = encryptedData.copyOfRange(0, IV_SIZE_IN_BYTES)

            // Decode the secret key with the cipher
            val cipher = getDecryptCipherAES(spec, iv) ?: return null
            val decryptedData = cipher.decrypt(encryptedData) ?: return null

            return transformation(decryptedData, alias, preferences)
        }

        /**
         * Use it when the Entry is protected with Biometrics. The authenticator should show a
         * BiometricPrompt with the given cypher and return the resulting cypher.
         */
        suspend fun get(preferences: SharedPreferences, authenticator: suspend (Cipher) -> Cipher?): ByteArray? {
            val alias = spec.keystoreAlias
            val encryptedData = preferences.getByteArray(alias) ?: return null
            val iv = encryptedData.copyOfRange(0, IV_SIZE_IN_BYTES)

            // Decode the secret key with the cipher
            val lockedCipher = getDecryptCipherAES(spec, iv) ?: return null
            val cipher = authenticator(lockedCipher) ?: return null
            return cipher.decrypt(encryptedData)
        }

        /**
         * Remove the key from the key store. Any data encrypted with this entry before will no longer be accessible.
         */
        fun delete() {
            loadAndroidKeyStore().deleteEntry(spec.keystoreAlias)
        }

    }

    const val androidKeyStoreProvider = "AndroidKeyStore"

    fun getEntry(alias: String, protection: Protection = Protection.DEFAULT, context: Context): Entry {
        val strongBoxAvailable = strongBoxCheck.isAvailable(context)
        return  getEntry(alias, protection, strongBoxAvailable)
    }

    fun getEntry(alias: String, protection: Protection = Protection.DEFAULT, isStrongBoxBacked: Boolean = false): Entry {
        val keySpec = when (protection) {
            Protection.DEFAULT -> aesKeySpec(alias, isStrongBoxBacked)
            Protection.BIOMETRIC -> aesKeyWithBiometricsSpec(alias, isStrongBoxBacked)
        }
        return Entry(keySpec)
    }

    private fun baseRsaKeySpecBuilder(alias: String, isStrongBoxBacked: Boolean): KeyGenParameterSpec.Builder =
        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .run {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setIsStrongBoxBacked(isStrongBoxBacked)
                } else {
                    this
                }
            }

    private fun aesKeySpec(alias: String, isStrongBoxBacked: Boolean): KeyGenParameterSpec =
        baseRsaKeySpecBuilder(alias, isStrongBoxBacked).build()

    private fun aesKeyWithBiometricsSpec(alias: String, isStrongBoxBacked: Boolean): KeyGenParameterSpec =
        baseRsaKeySpecBuilder(alias, isStrongBoxBacked)
            .setUserAuthenticationRequired(true)
            .run {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setInvalidatedByBiometricEnrollment(true)
                } else {
                    this
                }
            }
            .run {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()

    /** Instantiate and load the Android KeyStore */
    private fun loadAndroidKeyStore(): KeyStore {
        return KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }

    private fun getEncryptCipherAES(spec: KeyGenParameterSpec): Cipher {
        val keyEntry = getOrCreateKeyAES(spec)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, keyEntry.secretKey)
        }
    }

    private fun getDecryptCipherAES(spec: KeyGenParameterSpec, iv: ByteArray): Cipher? {
        val alias = spec.keystoreAlias
        val keyEntry = loadAndroidKeyStore()
            .getEntry(alias, null) as? KeyStore.SecretKeyEntry
            ?: return null

        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, keyEntry.secretKey, GCMParameterSpec(128, iv))
        }
    }

    /** Return the specified key, creating it if no present. */
    private fun getOrCreateKeyAES(spec: KeyGenParameterSpec): KeyStore.SecretKeyEntry {
        val keyStore = loadAndroidKeyStore()
        if (!keyStore.containsAlias(spec.keystoreAlias)) {
            generateKeyAES(spec)
        }
        return keyStore.getEntry(spec.keystoreAlias, null) as KeyStore.SecretKeyEntry
    }

    /** Create the specified key in the android keystore. */
    private fun generateKeyAES(keyGenParameterSpec: KeyGenParameterSpec): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            androidKeyStoreProvider
        )
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

}