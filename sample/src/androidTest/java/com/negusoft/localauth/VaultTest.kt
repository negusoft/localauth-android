package com.negusoft.localauth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

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
    fun createStoreAndRetrieve() {
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

    @Test
    fun testPinLock() {
        val vault = LocalAuth.createNewVault(prefs, "test_vault").apply {
            registerPinLock("12345")
        }.closed()

        vault.openPinLock("wrong").also { open ->
            assertNull(open)
        }
        vault.openPinLock("12345").also { open ->
            assertNotNull(open)
        }
    }

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