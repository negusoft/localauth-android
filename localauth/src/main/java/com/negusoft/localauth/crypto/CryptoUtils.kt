package com.negusoft.localauth.crypto

import android.security.keystore.KeyProperties
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val SALT_SIZE_IN_BYTES = 8
private const val PBK_ITERATIONS = 1000
private const val ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"
private const val PBE_ALGORITHM = "PBEwithSHA256and128BITAES-CBC-BC"
private const val IV_SIZE_IN_BYTES = 12
private const val TAG_SIZE_IN_BYTES = 16

// TODO turn into 'value class'
class PasswordEncryptedData(val bytes: ByteArray) {
    val salt: ByteArray get() = bytes.copyOfRange(0, SALT_SIZE_IN_BYTES)
    val iv: ByteArray get() = bytes.copyOfRange(SALT_SIZE_IN_BYTES, SALT_SIZE_IN_BYTES + IV_SIZE_IN_BYTES)
    val data: ByteArray get() = bytes.copyOfRange(SALT_SIZE_IN_BYTES + IV_SIZE_IN_BYTES, bytes.size)
}

/**
 * Key management and encryption/decryption
 */
object CryptoUtils {

    /** Returns the size of the encrypted data after calling 'encrypt' with a SecretKey */
    fun encryptedDataSize(plaintextSize: Int): Int = IV_SIZE_IN_BYTES + plaintextSize + TAG_SIZE_IN_BYTES

    /**
     * Encrypt data using the given password (AES-GCM encryption).
     * Returns an array with the [IV(12) + ENCRYPTED_BYTES(n) + TAG(16)]
     */
    fun encrypt(plaintext: ByteArray, secretKey: SecretKey, output: ByteArray, outputOffset: Int = 0): Int {
        val cipher: Cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val writtenBytesCount = cipher.doFinal(plaintext, 0, plaintext.size, output, outputOffset + IV_SIZE_IN_BYTES)

        cipher.iv.copyInto(output, outputOffset)

        return writtenBytesCount
    }

    /** See the other encrypt method. */
    fun encrypt(plaintext: ByteArray, secretKey: SecretKey): ByteArray {
        val outputSize = encryptedDataSize(plaintext.size)
        val output = ByteArray(outputSize)
        encrypt(plaintext, secretKey, output, 0)
        return output
    }

    /**
     * Decrypt data using the given password (AES-GCM encryption).
     * The ciphertext musut have the following format: [IV(12) + ENCRYPTED_BYTES(n) + TAG(16)]
     */
    fun decrypt(ciphertext: ByteArray, ciphertextOffset: Int, secretKey: SecretKey): ByteArray? {
        try {
            val params = GCMParameterSpec(8 * TAG_SIZE_IN_BYTES, ciphertext, ciphertextOffset, IV_SIZE_IN_BYTES)
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM).apply {
                init(Cipher.DECRYPT_MODE, secretKey, params)
            }
            return cipher.doFinal(ciphertext, ciphertextOffset + IV_SIZE_IN_BYTES, ciphertext.size - IV_SIZE_IN_BYTES - ciphertextOffset)
        } catch (e: Throwable) {
            e.printStackTrace()
            return null
        }
    }

    /** See the other decrypt method. */
    fun decrypt(ciphertext: ByteArray, secretKey: SecretKey): ByteArray? =
        decrypt(ciphertext, 0, secretKey)

    /** Use PBE algorithm to derive a secret key from the given password */
    fun privateKeyWithPassword(password: String, salt: ByteArray, iterations: Int): SecretKey {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, iterations)
        val secretKeyFactory: SecretKeyFactory = SecretKeyFactory.getInstance(PBE_ALGORITHM)
        return secretKeyFactory.generateSecret(keySpec)
    }

    /** Encrypt data using the given password (AES-GCM encryption) */
    fun encryptWithPassword(password: String, plaintext: ByteArray): PasswordEncryptedData {
        val salt = ByteArray(SALT_SIZE_IN_BYTES).apply { SecureRandom().nextBytes(this) }
        val key = privateKeyWithPassword(password, salt, PBK_ITERATIONS)

        val encryptedData = ByteArray( SALT_SIZE_IN_BYTES + encryptedDataSize(plaintext.size))
        encrypt(plaintext, key, encryptedData, SALT_SIZE_IN_BYTES)

        salt.copyInto(encryptedData, 0)

        return PasswordEncryptedData(encryptedData)
    }

    /** Decrypt data using the given password (AES-GCM encryption) */
    fun decryptWithPassword(password: String, encryptedData: PasswordEncryptedData ): ByteArray? {
        val key = privateKeyWithPassword(password, encryptedData.salt, PBK_ITERATIONS)
        return decrypt(encryptedData.bytes, SALT_SIZE_IN_BYTES, key)
    }

    /** Decode the secret key that was returned by SecretKey.getEncoded() */
    fun decodeSecretKey(bytes: ByteArray): SecretKey? {
        return SecretKeySpec(bytes, 0, bytes.size, "AES")
    }

    /** Generate a AES secret key. */
    fun generateSecretKey(keySize: Int = 256): SecretKey
            = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES).apply {
        init(keySize)
    }.generateKey()
}