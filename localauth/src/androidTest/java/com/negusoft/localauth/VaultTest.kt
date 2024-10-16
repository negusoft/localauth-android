package com.negusoft.localauth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.negusoft.localauth.vault.LocalVault
import com.negusoft.localauth.vault.lock.PinLock
import com.negusoft.localauth.vault.lock.PinLockException

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before

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

    // ------------ OLD

    @Test
    fun createStoreAndRetrieveOld() {
        // Set up new Vault with pin lock
        LocalAuth.createNewVault(prefs, "test_vault").apply {
            registerPinLock("12345")
        }

        // Restore vault
        val vault = LocalAuth.restoreVault(prefs, "test_vault")
            ?: error("Vault not restored.")

        // Save sample value
        vault.saveValue("Hello, Vault!", "test_value_key")
        assertTrue(prefs.contains("test_value_key"))

        // Open vault with pin code
        val openVault = vault.openPinLock("12345")
            ?: error("Vault not opened.")

        val savedValue = openVault.getValue("test_value_key")
        assertEquals("Hello, Vault!", savedValue)
    }

//    @Test
//    fun testPinLockOld() {
//        val vault = LocalAuth.createNewVault(prefs, "test_vault").apply {
//            registerPinLock("12345")
//        }.closed()
//
//        vault.openPinLock("wrong").also { open ->
//            assertNull(open)
//        }
//        vault.openPinLock("12345").also { open ->
//            assertNotNull(open)
//        }
//    }

    @Test
    fun testNoLock() {
        val vault = LocalAuth.createNewVault(prefs, "test_vault").closed()

        vault.openPinLock("any").also { open ->
            assertNull(open)
        }
    }

//    @Test
//    fun testBiometricLock() {
//        val vault = LocalAuth.createNewVault(prefs, "test_vault").apply {
//            getActivity()
//            InstrumentationRegistry.getInstrumentation().newActivity()
//            registerBiometricLock(appContext)
//        }.closed()
//
//        vault.openPinLock("wrong").also { open ->
//            assertNull(open)
//        }
//        vault.openPinLock("12345").also { open ->
//            assertNotNull(open)
//        }
//    }
}