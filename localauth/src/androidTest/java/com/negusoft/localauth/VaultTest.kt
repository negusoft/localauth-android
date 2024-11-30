package com.negusoft.localauth

import android.content.Context
import android.security.KeyStoreException
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.negusoft.localauth.vault.LocalVault
import com.negusoft.localauth.vault.lock.BiometricLock
import com.negusoft.localauth.vault.lock.BiometricLockException
import com.negusoft.localauth.vault.lock.PinLock
import com.negusoft.localauth.vault.lock.PinLockException
import com.negusoft.localauth.vault.lock.open
import com.negusoft.localauth.vault.lock.registerBiometricLock
import com.negusoft.localauth.vault.lock.registerPinLock
import kotlinx.coroutines.runBlocking

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before
import javax.crypto.IllegalBlockSizeException

/**
 * Test Vault logic.
 */
@RunWith(AndroidJUnit4::class)
class VaultTest {

    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    val prefs = appContext.getSharedPreferences("vault_test", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit { clear() }
    }

    @Test
    fun createEncryptDecrypt() {
        lateinit var encodedPinLock: ByteArray
        val vault = LocalVault.create { openVault ->
            val pinLock = openVault.registerPinLock("lockId", "12345")
            encodedPinLock = pinLock.encode()
        }

        val encoded = vault.encode()

        val restored = LocalVault.restore(encoded)
        val secret = "Hello, Vault!".toByteArray()
        val encryptedSecret = restored.encrypt(secret)

        val pinLock = PinLock.restore(encodedPinLock)
        val openVault = restored.open(pinLock, "12345")
        val decryptedSecret = openVault.decrypt(encryptedSecret)

        assert(decryptedSecret.contentEquals("Hello, Vault!".toByteArray()))
    }

    @Test
    fun testPinLock() {
        lateinit var pinLock: PinLock
        val vault = LocalVault.create { openVault ->
            pinLock = openVault.registerPinLock("lockId", "12345")
        }

        assertThrows(PinLockException::class.java) {
            vault.open(pinLock, "wrong")
        }

        vault.open(pinLock, "12345")
    }

    @Test
    fun testBiometricLock() {
        lateinit var biometricLock: BiometricLock
        val vault = LocalVault.create { openVault ->
            biometricLock = openVault.registerBiometricLock("lockId")
        }

        // The lock should fail if not authenticated with BiometricPrompt
        runBlocking {
            try {
                biometricLock.unlock { it }
                fail("Should have thrown")
            } catch (e: BiometricLockException) {
                assert(e.reason == BiometricLockException.Reason.ERROR)
                val cause = e.cause ?: error("No cause")
                assert(cause is IllegalBlockSizeException)
            }
        }

        // Open can't be tested since BiometricPrompt is not available in testing
    }
}