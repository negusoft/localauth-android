package com.negusoft.localauth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.negusoft.localauth.crypto.Ciphers
import com.negusoft.localauth.crypto.Keys
import com.negusoft.localauth.keystore.AndroidKeyStore
import com.negusoft.localauth.keystore.setRSA_OAEPPadding
import com.negusoft.localauth.keystore.setStrongBoxBacked
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test cryptographic operations
 */
@RunWith(AndroidJUnit4::class)
class CryptoTest {

    val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun cipherAESGCM_NoPadding() {
        val plaintext = "Hello, cipher!".toByteArray()
        val secretKey = Keys.AES.generateSecretKey()
        val encrypted = Ciphers.AES_GCM_NoPadding.encrypt(plaintext, secretKey)
        val decrypted = Ciphers.AES_GCM_NoPadding.decrypt(encrypted, secretKey)
        assert(plaintext.contentEquals(decrypted))
    }

    @Test
    fun cipherRSA() {
        val plaintext = "Hello, cipher!".toByteArray()
        val keyPair = Keys.RSA.generateKeyPair()
        val encrypted = Ciphers.RSA_ECB_OAEP.encrypt(plaintext, keyPair.public)
        val decrypted = Ciphers.RSA_ECB_OAEP.decrypt(encrypted, keyPair.private)
        assert(plaintext.contentEquals(decrypted))
    }

    @Test
    fun cipherRSAwithAES() {
        val plaintext = "Hello, cipher!".toByteArray()
        val keyPair = Keys.RSA.generateKeyPair()
        val encrypted = Ciphers.RSA_ECB_OAEPwithAES_GCM_NoPadding.encrypt(plaintext, keyPair.public)
        val decrypted = Ciphers.RSA_ECB_OAEPwithAES_GCM_NoPadding.decrypt(encrypted, keyPair.private)
        assert(plaintext.contentEquals(decrypted))
    }

    @Test
    fun cipherRSAwithAESfromKeyStore() {
        val plaintext = "Hello, cipher!".toByteArray()
        val spec = KeyGenParameterSpec.Builder("testSpec", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setRSA_OAEPPadding()
//            .setBiometricAuthenticated()
            .setStrongBoxBacked(false)
            .build()
        val keyPair = AndroidKeyStore().generateKeyPair(spec)
        val encrypted = Ciphers.RSA_ECB_OAEPwithAES_GCM_NoPadding.encrypt(plaintext, keyPair.public)
        val decrypted = Ciphers.RSA_ECB_OAEPwithAES_GCM_NoPadding.decrypt(encrypted, keyPair.private)
        assert(plaintext.contentEquals(decrypted))
    }
}