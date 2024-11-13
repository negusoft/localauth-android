package com.negusoft.localauth

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.negusoft.localauth.core.VaultManager

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class VaultManagerTest {

    val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testCreateEncryptDecrypt() {
        val manager = VaultManager(appContext)

        val vault = manager.createVault("vault1", "12345")
        val modifiedVault = manager.newSecretValue(vault, "key", "value")

        assertTrue(modifiedVault.secretValues.size == 1)
        assertTrue(modifiedVault.locks.size == 1)

        val secretValueRef = modifiedVault.secretValues[0]
        val pinLock = modifiedVault.locks[0]
        val secretValue = modifiedVault.readValueWithPin(secretValueRef, pinLock, "12345")

        assertTrue(secretValue == "value")
    }
}