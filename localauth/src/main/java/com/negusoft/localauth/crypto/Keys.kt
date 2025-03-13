package com.negusoft.localauth.crypto

import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Keys {
    object AES {
        const val SALT_SIZE_IN_BYTES = 8
        private const val PBK_ITERATIONS = 1000
        private const val PBE_ALGORITHM = "PBEwithSHA256and128BITAES-CBC-BC"

        /** Generate a random byte array for password.  */
        fun generateSalt() = ByteArray(SALT_SIZE_IN_BYTES).apply { SecureRandom().nextBytes(this) }

        fun deriveKeyFromPassword(password: String, salt: ByteArray, iterations: Int = PBK_ITERATIONS): SecretKey {
            val keySpec = PBEKeySpec(password.toCharArray(), salt, iterations)
            val secretKeyFactory: SecretKeyFactory = SecretKeyFactory.getInstance(PBE_ALGORITHM)
            return secretKeyFactory.generateSecret(keySpec)
        }

        /** Generate a AES secret key. */
        fun generateSecretKey(keySize: Int = 256): SecretKey
                = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES).apply {
            init(keySize)
        }.generateKey()

        /** Decode the secret key that was returned by SecretKey.getEncoded() */
        fun decodeSecretKey(bytes: ByteArray): SecretKey {
            return SecretKeySpec(bytes, 0, bytes.size, "AES")
        }
    }

    object RSA {
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

        /**
         * Decode the private key encoded in X509 format (eg. returned by PublicKey.getEncoded()).
         * If the algorithm is not specified, it will default to KeyProperties.KEY_ALGORITHM_RSA.
         */
        fun decodePublicKey(bytes: ByteArray, algorithm: String? = null): PublicKey? {
            val resolvedAlgorithm = algorithm ?: KeyProperties.KEY_ALGORITHM_RSA
            val keySpec = X509EncodedKeySpec(bytes)
            val keyFactory = KeyFactory.getInstance(resolvedAlgorithm)
            return keyFactory.generatePublic(keySpec)
        }

//        /** Decode the private key that was returned by PublicKey.getEncoded() */
//        fun decodePublicKey(bytes: ByteArray): PublicKey? {
//            val keySpec = X509EncodedKeySpec(bytes)
//            val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
//            return keyFactory.generatePublic(keySpec)
//        }
    }
}