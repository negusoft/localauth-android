package com.negusoft.localauth.lock

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.negusoft.localauth.crypto.Ciphers
import com.negusoft.localauth.keystore.AndroidKeyStore
import com.negusoft.localauth.keystore.setAES_GCM_NoPadding
import com.negusoft.localauth.keystore.setBiometricAuthenticated
import com.negusoft.localauth.keystore.setRSA_OAEPPadding
import com.negusoft.localauth.coding.ByteCoding
import com.negusoft.localauth.coding.readStringProperty
import com.negusoft.localauth.coding.writeProperty
import java.security.KeyPair
import javax.crypto.Cipher
import javax.crypto.SecretKey

object KeyStoreLockCommons {

    fun interface TokenAdapter<T> {
        fun toToken(alias: String, encryptionMethod: String?, encryptedSecret: ByteArray): T
    }

//    object Token {
////        private const val ENCODING_VERSION: Byte = 0x00
////
////        @Throws(LockException::class)
////        fun <T> restore(
////            encoded: ByteArray,
////            adapter: TokenAdapter<T>
////        ): T {
////            val decoder = ByteCoding.decode(encoded)
////            if (!decoder.checkValueEquals(byteArrayOf(ENCODING_VERSION))) {
////                throw LockException("Wrong encoding version (${encoded[0]}).")
////            }
////            val alias = decoder.readStringProperty() ?: throw LockException("Failed to decode 'alias'.")
////            val method = decoder.readStringProperty()
////            val encryptedSecret = decoder.readFinal()
////            return adapter.toToken(alias, method, encryptedSecret)
////        }
//
////        fun encode(alias: String, encryptionMethod: String?, encryptedSecret: ByteArray): ByteArray {
////            return ByteCoding.encode(prefix = byteArrayOf(ENCODING_VERSION)) {
////                writeProperty(alias)
////                writeProperty(encryptionMethod)
////                writeValue(encryptedSecret)
////            }
////        }
//
//    }

    open class SecretKeyLock(
        private val key: SecretKey,
        private val keyIdentifier: String,
        private val encryptionMethod: String?
    ) {
        companion object {

            @JvmStatic
            @Throws(LockException::class)
            protected fun createKey(
                useStrongBoxWhenAvailable: Boolean,
                specBuilder: () -> KeyGenParameterSpec.Builder
            ): SecretKey = try {
                AndroidKeyStore().generateSecretKey(useStrongBox = useStrongBoxWhenAvailable, specBuilder = specBuilder)
            } catch (t: Throwable) {
                throw LockException("Failed to create lock.", t)
            }

            @JvmStatic
            @Throws(LockException::class)
            protected fun restoreKey(keystoreAlias: String): SecretKey = try {
                AndroidKeyStore().getSecretKey(keystoreAlias)
            } catch (t: Throwable) {
                throw LockException("Failed to create lock.", t)
            }

            @JvmStatic
            protected fun defaultKeySpecBuilder(keystoreAlias: String): () -> KeyGenParameterSpec.Builder = {
                KeyGenParameterSpec.Builder(keystoreAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setAES_GCM_NoPadding()
            }

        }

        protected fun <T> lock(secret: ByteArray, adapter: TokenAdapter<T>): T {
            try {
                assert(encryptionMethod.isNullOrBlank()) { "Invalid encryption method." }
                val encryptedSecret = Ciphers.AES_GCM_NoPadding.encrypter(key).encrypt(secret)
                return adapter.toToken(keyIdentifier, encryptionMethod, encryptedSecret)
            } catch (t: Throwable) {
                throw LockException("Failed to lock secret with simple lock.", t)
            }
        }

        protected fun unlock(encryptedSecret: ByteArray) = unlock(encryptedSecret) { it }
        protected fun unlock(
            encryptedSecret: ByteArray,
            transformation: (ByteArray) -> ByteArray
        ): ByteArray {
            try {
                assert(encryptionMethod.isNullOrBlank()) { "Invalid encryption method." }
                val privateKeyBytes = Ciphers.AES_GCM_NoPadding
                    .decrypter(key, encryptedSecret)
                    .decrypt(encryptedSecret)
                return transformation(privateKeyBytes)
            } catch (e: LockException) {
                throw e
            } catch (t: Throwable) {
                throw LockException("Failed to unlock secret.", t)
            }
        }
    }


    open class KeyPairLock(
        private val keyPair: KeyPair,
        private val keyIdentifier: String,
        private val encryptionMethod: String?
    ) {
        companion object {

            @JvmStatic
            @Throws(LockException::class)
            protected fun createKeyPair(
                useStrongBoxWhenAvailable: Boolean,
                specBuilder: () -> KeyGenParameterSpec.Builder
            ): KeyPair = try {
                AndroidKeyStore().generateKeyPair(useStrongBox = useStrongBoxWhenAvailable, specBuilder = specBuilder)
            } catch (t: Throwable) {
                throw LockException("Failed to create lock.", t)
            }

            @JvmStatic
            @Throws(LockException::class)
            protected fun restoreKeyPair(keystoreAlias: String): KeyPair = try {
                AndroidKeyStore().getKeyPair(keystoreAlias)
            } catch (t: Throwable) {
                throw LockException("Failed to create lock.", t)
            }

            @JvmStatic
            protected fun defaultKeySpecBuilder(keystoreAlias: String): () -> KeyGenParameterSpec.Builder = {
                KeyGenParameterSpec.Builder(keystoreAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setRSA_OAEPPadding()
                    .setBiometricAuthenticated()
            }

        }

        @Throws(LockException::class)
        protected fun <T> lock(secret: ByteArray, adapter: TokenAdapter<T>): T {
            try {
                assert(encryptionMethod.isNullOrBlank()) { "Invalid encryption method." }
                val encryptedSecret = Ciphers.RSA_ECB_OAEPwithAES_GCM_NoPadding.encrypt(secret, keyPair.public)
                return adapter.toToken(keyIdentifier, encryptionMethod, encryptedSecret)
            } catch (e: Throwable) {
                throw LockException("Failed to create Biometric lock.", e)
            }
        }

        protected suspend fun unlock(
            encryptedSecret: ByteArray,
            authenticator: suspend (Cipher) -> Cipher
        ): ByteArray {
            try {
                return Ciphers.RSA_ECB_OAEPwithAES_GCM_NoPadding
                    .decrypter(keyPair.private)
                    .decrypt(encryptedSecret, authenticator)
            } catch (e: LockException) {
                throw e
            } catch (t: Throwable) {
                throw LockException("Failed to unlock secret with Biometric", t)
            }
        }
    }

}