package com.negusoft.localauth.crypto

import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

private const val ENCRYPTION_ALGORITHM = "RSA/ECB/OAEPPadding"
/** As specified in https://developer.android.com/guide/topics/security/cryptography#oaep-mgf1-digest */
private val parameterSpecOAEP = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
private const val OAEP_PADDING_SIZE = 64

/**
 * Key management and encryption/decryption. Using RSA public key cryptography.
 */
object CryptoUtilsRSA {

    /**
     * Max data that can be encrypted. {key_size_in_bytes} - {padding_size}
     * reference: https://crypto.stackexchange.com/questions/32692/what-is-the-typical-block-size-in-rsa
     */
    fun maxEncryptDataSize(publicKey: PublicKey): Int? {
        val modulus = (publicKey as? RSAPublicKey)?.modulus?.bitLength() ?: return null
        return (modulus / 8) - OAEP_PADDING_SIZE - 2
    }

    /**
     * Encrypt data using the given public key.
     */
    fun encrypt(plaintext: ByteArray, publicKey: PublicKey): ByteArray {
        return Cipher.getInstance(ENCRYPTION_ALGORITHM).apply {
            init(Cipher.ENCRYPT_MODE, publicKey, parameterSpecOAEP)
        }.doFinal(plaintext)
    }

    /**
     * Decrypt data using the given private key.
     */
    fun decrypt(ciphertext: ByteArray, privateKey: PrivateKey): ByteArray {
        return Cipher.getInstance(ENCRYPTION_ALGORITHM).apply {
            init(Cipher.DECRYPT_MODE, privateKey, parameterSpecOAEP)
        }.doFinal(ciphertext)
    }

    /**
     * Generate a RSA public/private key pair.
     * The keySize in bytes (keySize=256 -> rsa-modulus=2048). THis will limit the maximum size
     * of the data that can be encrypted with it.
     */
    fun generateKeyPair(keySize: Int = 256): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA
        )
        keyPairGenerator.initialize(keySize * 8)
        return keyPairGenerator.generateKeyPair()
    }

    /** Decode the private key that was returned by PrivateKey.getEncoded() */
    fun decodePrivateKey(bytes: ByteArray): PrivateKey? {
        val keySpec = PKCS8EncodedKeySpec(bytes)
        val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
        return keyFactory.generatePrivate(keySpec)
    }

    /** Decode the private key that was returned by PublicKey.getEncoded() */
    fun decodePublicKey(bytes: ByteArray): PublicKey? {
        val keySpec = X509EncodedKeySpec(bytes)
        val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
        return keyFactory.generatePublic(keySpec)
    }
}