package com.negusoft.localauth

import android.content.Context
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.negusoft.localauth.coding.encode
import com.negusoft.localauth.coding.restore
import com.negusoft.localauth.vault.LocalVault
import com.negusoft.localauth.lock.BiometricLock
import com.negusoft.localauth.lock.LockException
import com.negusoft.localauth.lock.Password
import com.negusoft.localauth.lock.PasswordLock
import com.negusoft.localauth.lock.SimpleLock
import com.negusoft.localauth.lock.open
import com.negusoft.localauth.lock.registerBiometricLock
import com.negusoft.localauth.lock.registerPasswordLock
import com.negusoft.localauth.lock.registerSimpleLock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.Cipher
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
        lateinit var passwordLockToken: PasswordLock.Token
        val vault = LocalVault.create { openVault ->
            passwordLockToken = openVault.registerPasswordLock(Password("12345"), "lockId")
        }

        val encoded = vault.encode()

        val restored = LocalVault.restore(encoded)
        val secret = "Hello, Vault!".toByteArray()
        val encryptedSecret = restored.encrypt(secret)

        val openVault = restored.open(passwordLockToken, Password("12345"))
        val decryptedSecret = openVault.decrypt(encryptedSecret)

        assert(decryptedSecret.contentEquals("Hello, Vault!".toByteArray()))
    }

    @Test
    fun serializeDeserialize() {
        lateinit var passwordLockToken: PasswordLock.Token
        val vault = LocalVault.create { openVault ->
            passwordLockToken = openVault.registerPasswordLock(Password("12345"), "lockId")
        }

        val encoded = Json.encodeToString(vault)

        val restored = Json.decodeFromString<LocalVault>(encoded)
        val secret = "Hello, Vault!".toByteArray()
        val encryptedSecret = restored.encrypt(secret)

        val openVault = restored.open(passwordLockToken, Password("12345"))
        val decryptedSecret = openVault.decrypt(encryptedSecret)

        assert(decryptedSecret.contentEquals("Hello, Vault!".toByteArray()))
    }

    @Test
    fun testSimpleLock() {
        lateinit var token: SimpleLock.Token
        val vault = LocalVault.create { openVault ->
            token = openVault.registerSimpleLock("lockId")
        }

        vault.open(token)
    }

    @Test
    fun testPasswordLock() {
        lateinit var passwordLockToken: PasswordLock.Token
        val vault = LocalVault.create { openVault ->
            passwordLockToken = openVault.registerPasswordLock(Password("12345"), "lockId")
        }

        assertThrows(LockException::class.java) {
            vault.open(passwordLockToken, Password("wrong"))
        }

        vault.open(passwordLockToken, Password("12345"))
    }

    @Test
    fun testBiometricLock() {
        lateinit var biometricLockToken: BiometricLock.Token
        val vault = LocalVault.create { openVault ->
            biometricLockToken = openVault.registerBiometricLock("lockId")
        }

        // The lock should fail if not authenticated with BiometricPrompt
        runBlocking {
            try {
                vault.open(biometricLockToken) { c: Cipher -> c }
                fail("Should have thrown")
            } catch (e: LockException) {
                val cause = e.cause ?: error("No cause")
                assert(cause is IllegalBlockSizeException)
            }
        }

        // Open can't be tested since BiometricPrompt is not available in testing
    }
}