package com.negusoft.localauth.crypto

import com.negusoft.localauth.crypto.RSA_ECB_OAEP_Cipher.Decrypter
import com.negusoft.localauth.crypto.RSA_ECB_OAEP_Cipher.Encrypter
import com.negusoft.localauth.persistence.ByteCoding
import java.security.GeneralSecurityException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

object Ciphers {
    val AES_GCM_NoPadding: AES_GCM_NoPaddingCipher get() = AES_GCM_NoPaddingCipher
    val RSA_ECB_OAEP: RSA_ECB_OAEP_Cipher get() = RSA_ECB_OAEP_Cipher
    val RSA_ECB_OAEPwithAES_GCM_NoPadding: RSA_ECB_OAEP_with_AES_GCM_NoPaddingCipher
        get() = RSA_ECB_OAEP_with_AES_GCM_NoPaddingCipher
}

object AES_GCM_NoPaddingCipher {

    private const val AES_GCM_NoPadding = "AES/GCM/NoPadding"
    private const val IV_SIZE_IN_BYTES = 12

    fun encrypter(key: SecretKey): Encrypter = Encrypter(key)

    /**
     * The encrypted data is passed to the constructor in order to extract the IV.
     * Call decrypt with the same data again to the decrypt method.
     */
    fun decrypter(key: SecretKey, encrypted: ByteArray): Decrypter = Decrypter(key, encrypted)

    fun encrypt(plaintext: ByteArray, key: SecretKey) = encrypter(key).encrypt(plaintext)
    fun decrypt(encryptedData: ByteArray, key: SecretKey) = decrypter(key, encryptedData).decrypt(encryptedData)

    class Encrypter(key: SecretKey) {

        private val cipher = Cipher.getInstance(AES_GCM_NoPadding).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }

        fun encrypt(plaintext: ByteArray): ByteArray {
            val outputSize = cipher.getOutputSize(plaintext.size)
            val output = ByteArray(IV_SIZE_IN_BYTES + outputSize)
            cipher.doFinal(plaintext, 0, plaintext.size, output, IV_SIZE_IN_BYTES)
            cipher.iv.copyInto(output, 0)
            return output
        }
    }

    class Decrypter(key: SecretKey, encryptedData: ByteArray) {
        private val iv = encryptedData.copyOfRange(0, IV_SIZE_IN_BYTES)
        private val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, this@Decrypter.iv))
        }

        fun decrypt(encryptedData: ByteArray): ByteArray {
            return cipher.doFinal(encryptedData,
                IV_SIZE_IN_BYTES, encryptedData.size - IV_SIZE_IN_BYTES
            )
        }
    }

}

fun AES_GCM_NoPaddingCipher.encryptWithPassword(password: String, plaintext: ByteArray): ByteArray {
    val salt = Keys.AES.generateSalt()
    val key = Keys.AES.deriveKeyFromPassword(password, salt)
    val privateKeyEncrypted = encrypt(plaintext, key)
    return salt + privateKeyEncrypted
}

fun AES_GCM_NoPaddingCipher.decryptWithPassword(password: String, encryptedData: ByteArray): ByteArray? {
    val salt = encryptedData.copyOfRange(0, Keys.AES.SALT_SIZE_IN_BYTES)
    val netEncryptedData = encryptedData.copyOfRange(Keys.AES.SALT_SIZE_IN_BYTES, encryptedData.size)
    val key = Keys.AES.deriveKeyFromPassword(password, salt)
    return try {
        decrypt(netEncryptedData, key)
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}

object RSA_ECB_OAEP_Cipher {

    private const val ENCRYPTION_ALGORITHM = "RSA/ECB/OAEPPadding"
    /** As specified in https://developer.android.com/guide/topics/security/cryptography#oaep-mgf1-digest */
    private val parameterSpecOAEP = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    private const val OAEP_PADDING_SIZE = 64

    fun encrypter(publicKey: PublicKey): Encrypter = Encrypter(publicKey)
    fun decrypter(privateKey: PrivateKey): Decrypter = Decrypter(privateKey)

    fun encrypt(plaintext: ByteArray, publicKey: PublicKey) = encrypter(publicKey).encrypt(plaintext)
    fun decrypt(ciphertext: ByteArray, privateKey: PrivateKey) = decrypter(privateKey).decrypt(ciphertext)

    /**
     * Max data that can be encrypted. {key_size_in_bytes} - {padding_size}
     * reference: https://crypto.stackexchange.com/questions/32692/what-is-the-typical-block-size-in-rsa
     */
    fun maxEncryptDataSize(publicKey: PublicKey): Int? {
        val modulus = (publicKey as? RSAPublicKey)?.modulus?.bitLength() ?: return null
        return (modulus / 8) - OAEP_PADDING_SIZE - 2
    }

    class Encrypter(publicKey: PublicKey) {

        private val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM).apply {
            init(Cipher.ENCRYPT_MODE, publicKey, parameterSpecOAEP)
        }

        fun encrypt(plaintext: ByteArray): ByteArray = cipher.doFinal(plaintext)
    }

    class Decrypter(privateKey: PrivateKey) {

        private val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM).apply {
            init(Cipher.DECRYPT_MODE, privateKey, parameterSpecOAEP)
        }

        fun decrypt(ciphertext: ByteArray): ByteArray {
            return cipher.doFinal(ciphertext)
        }

        suspend fun decrypt(ciphertext: ByteArray, authenticator: suspend (Cipher) -> Cipher): ByteArray {
            val unlockedCipher = authenticator(cipher)
            return unlockedCipher.doFinal(ciphertext)
        }
    }

}

object RSA_ECB_OAEP_with_AES_GCM_NoPaddingCipher {

    fun encrypter(publicKey: PublicKey): Encrypter = Encrypter(publicKey)
    fun decrypter(privateKey: PrivateKey): Decrypter = Decrypter(privateKey)

    fun encrypt(plaintext: ByteArray, publicKey: PublicKey) = encrypter(publicKey).encrypt(plaintext)
    fun decrypt(ciphertext: ByteArray, privateKey: PrivateKey) = decrypter(privateKey).decrypt(ciphertext)

    class Encrypter(private val publicKey: PublicKey) {
        fun encrypt(plaintext: ByteArray): ByteArray {
            val intermediateKey = Keys.AES.generateSecretKey()
            val ciphertext = AES_GCM_NoPaddingCipher.encrypt(plaintext, intermediateKey)
            val encryptedIntermediateKey = RSA_ECB_OAEP_Cipher.encrypt(intermediateKey.encoded, publicKey)
            return ByteCoding.encode {
                writeProperty(encryptedIntermediateKey)
                writeValue(ciphertext)
            }
        }
    }

    class Decrypter(private val privateKey: PrivateKey) {

        fun decrypt(ciphertext: ByteArray): ByteArray {
            val decoder = ByteCoding.decode(ciphertext)
            val encryptedIntermediateKey = decoder.readProperty()
                ?: throw GeneralSecurityException("Ciphertext format error: missing 'intermediate key'.")
            val valueCiphertext = decoder.readFinal()

            val intermediateKeyBytes = RSA_ECB_OAEP_Cipher.decrypt(encryptedIntermediateKey, privateKey)
            val intermediateKey = Keys.AES.decodeSecretKey(intermediateKeyBytes)
            return AES_GCM_NoPaddingCipher.decrypt(valueCiphertext, intermediateKey)
        }

        suspend fun decrypt(ciphertext: ByteArray, authenticator: suspend (Cipher) -> Cipher): ByteArray {
            val decoder = ByteCoding.decode(ciphertext)
            val encryptedIntermediateKey = decoder.readProperty()
                ?: throw GeneralSecurityException("Ciphertext format error: missing 'intermediate key'.")
            val valueCiphertext = decoder.readFinal()

            val intermediateKeyBytes = RSA_ECB_OAEP_Cipher.decrypter(privateKey).decrypt(encryptedIntermediateKey, authenticator)
            val intermediateKey = Keys.AES.decodeSecretKey(intermediateKeyBytes)
            return AES_GCM_NoPaddingCipher.decrypt(valueCiphertext, intermediateKey)
        }
    }
}