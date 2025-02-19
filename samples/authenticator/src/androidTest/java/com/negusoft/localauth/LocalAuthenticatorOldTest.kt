package com.negusoft.localauth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.negusoft.localauth.core.LocalAuthenticator
import com.negusoft.localauth.vault.lock.PinLockException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAuthenticatorOldTest {
    private val adapter = object : LocalAuthenticator.Adapter<String> {
        override fun encode(secret: String): ByteArray = secret.toByteArray()
        override fun decode(bytes: ByteArray): String = bytes.toString(Charsets.UTF_8)
    }

    @Test
    fun storeAndRetrieveSecret() {
        val authenticator = LocalAuthenticator.create(adapter)

        authenticator.initialize("test").apply {
            registerPassword("11111")
        }

        val secret = authenticator.authenticatedSecret("11111")
        assertEquals(secret, "test")
    }


    @Test
    fun changePassword() {
        val authenticator = LocalAuthenticator.create(adapter)

        authenticator.initialize("test").apply {
            registerPassword("11111")
        }

        authenticator.authenticated("11111") {
            edit {
                registerPassword("22222")
            }
        }

        try {
            authenticator.authenticatedSecret("11111")
            fail("Should have thrown an exception")
        } catch (e: PinLockException) {
            // Expected
        }

        val secret = authenticator.authenticatedSecret("22222")
        assertEquals(secret, "test")
    }
}