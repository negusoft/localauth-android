package com.negusoft.localauth.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.negusoft.localauth.crypto.CryptoUtils
import com.negusoft.localauth.crypto.CryptoUtilsRSA
import com.negusoft.localauth.vault.EncryptedValue
import java.security.KeyPair
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeyStoreAccess {
    fun createSecretKeyEntry(alias: String, strongBoxBacked: Boolean): SecretKeyEntry =
        SecretKeyEntry.newEntry(alias, strongBoxBacked)
    fun getSecretKeyEntry(alias: String): SecretKeyEntry? =
        SecretKeyEntry.getEntry(alias)
    fun getOrCreateSecretKeyEntry(alias: String, strongBoxBacked: Boolean): SecretKeyEntry =
        getSecretKeyEntry(alias) ?: createSecretKeyEntry(alias, strongBoxBacked)

    fun createKeyPairEntry(alias: String, strongBoxBacked: Boolean): KeyPairBiometricEntry =
        KeyPairBiometricEntry.newEntry(alias, strongBoxBacked)
    fun getKeyPairEntry(alias: String, strongBoxBacked: Boolean): KeyPairBiometricEntry? =
        KeyPairBiometricEntry.getEntry(alias)
    fun getOrCreateKeyPairEntry(alias: String, strongBoxBacked: Boolean): KeyPairBiometricEntry =
        KeyPairBiometricEntry.getEntry(alias) ?: createKeyPairEntry(alias, strongBoxBacked)

    fun deleteEntry(alias: String) {
        AndroidKeyStore().deleteEntry(alias)
    }
}

/**
 * Secret key entry: "AES/GCM/NoPadding"
 */
class SecretKeyEntry(val key: SecretKey) {
    companion object {
        private const val AES_GCM_NoPadding = "AES/GCM/NoPadding"
        private const val IV_SIZE_IN_BYTES = 12

        fun newEntry(alias: String, strongBoxBacked: Boolean): SecretKeyEntry {
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setAES_GCM_NoPadding()
                .setStrongBoxBacked(strongBoxBacked)
                .build()
            val key = AndroidKeyStore().generateSecretKey(spec)
            return SecretKeyEntry(key)
        }

        fun getEntry(alias: String): SecretKeyEntry? {
            try {
                val keyStore = AndroidKeyStore()
                val key = keyStore.getSecretKey(alias)
                return SecretKeyEntry(key)
            } catch (t: Throwable) {
                t.printStackTrace()
                return null
            }
        }
    }

    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_NoPadding).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
        return cipher.encrypt(data)
    }

    fun decrypt(encryptedData: ByteArray): ByteArray? {
        val iv = encryptedData.copyOfRange(0, IV_SIZE_IN_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
        return cipher.decrypt(encryptedData)
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
}

/**
 * Public/Private key entry: "RSA/ECB/OAEPPadding"
 * Authentication with the Biometric prompt is required for decryption. Not required
 * for encryption though.
 */
class KeyPairBiometricEntry(val keyPair: KeyPair) {
    companion object {
        fun newEntry(alias: String, strongBoxBacked: Boolean): KeyPairBiometricEntry {
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setRSA_OAEPPadding()
                .setBiometricAuthenticated()
                .setStrongBoxBacked(strongBoxBacked)
                .build()
            val keyPair = AndroidKeyStore().generateKeyPair(spec)
            return KeyPairBiometricEntry(keyPair)
        }

        fun getEntry(alias: String): KeyPairBiometricEntry? {
            try {
                val keyStore = AndroidKeyStore()
                val key = keyStore.getKeyPair(alias)
                return KeyPairBiometricEntry(key)
            } catch (t: Throwable) {
                t.printStackTrace()
                return null
            }
        }
    }

    fun encrypt(data: ByteArray): ByteArray {
        return CryptoUtilsRSA.encrypt(data, keyPair.public)
    }

    suspend fun decrypt(data: ByteArray, authenticator: suspend (Cipher) -> Cipher): ByteArray {
        val lockedCipher =  CryptoUtilsRSA.decryptCipher(keyPair.private)
        val unlockedCipher = authenticator(lockedCipher)
        return unlockedCipher.doFinal(data)
    }
}