package com.negusoft.localauth.preferences

import android.content.SharedPreferences
import com.negusoft.localauth.crypto.CryptoUtils
import com.negusoft.localauth.crypto.CryptoUtilsRSA
import java.security.*

/****************************************************************
 * SharePreferences extensions for storing encrypted data
 **************************************************************/

/**
 * Encrypt the data and store it.
 * When the data is longer than the keySize, an intermediate SecretKey is generated to bypass the
 * data size limitation
 */
fun SharedPreferences.Editor.putEncrypted(key: String, value: ByteArray, publicKey: PublicKey) {
    // If the that is small enough -> Encrypt it with the public key directly
    val maxDataSize = CryptoUtilsRSA.maxEncryptDataSize(publicKey) ?: 0
    if (value.size <= maxDataSize) {
        val encrypted = CryptoUtilsRSA.encrypt(value, publicKey)
        putByteArray(key, encrypted)
        remove("${key}_secret")
        return
    }

    // Generate a secret key to encrypt the value
    val secretKey = CryptoUtils.generateSecretKey()
    val encryptedData = CryptoUtils.encrypt(value, secretKey)

    // Encrypt the secret key with the public key
    val encryptedSecretKey = CryptoUtilsRSA.encrypt(secretKey.encoded, publicKey)

    // Store the data
    putByteArray(key, encryptedData)
    putByteArray("${key}_secret", encryptedSecretKey)
}

/** Get the decrypted data. */
fun SharedPreferences.getByteArrayEncrypted(key: String, privateKey: PrivateKey): ByteArray? {
    try {
        val encryptedData = getByteArray(key) ?: return null
        val encryptedSecretKey = getByteArray("${key}_secret")

        // If no secret key -> Directly decrypt with the private key
        if (encryptedSecretKey == null) {
            return CryptoUtilsRSA.decrypt(encryptedData, privateKey)
        }

        // Decrypt the secret
        val secretKeyEncoded = CryptoUtilsRSA.decrypt(encryptedSecretKey, privateKey)
        val secretKey = CryptoUtils.decodeSecretKey(secretKeyEncoded) ?: return null

        // Use the secret key to decrypt the value
        return CryptoUtils.decrypt(encryptedData, secretKey)
    } catch (e: Throwable) {
        return null
    }
}

// String
fun SharedPreferences.Editor.putEncrypted(key: String, value: String, publicKey: PublicKey) =
    putEncrypted(key, value.toByteArray(Charsets.UTF_8), publicKey)
fun SharedPreferences.getStringEncrypted(key: String, privateKey: PrivateKey): String? =
    getByteArrayEncrypted(key, privateKey)
        ?.let { String(it, Charsets.UTF_8) }
