package com.negusoft.localauth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.negusoft.localauth.core.LocalAuthenticator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAuthenticatorTest {
    @Test
    fun storeAndRetrieveSecret() {
        val authenticator = LocalAuthenticator.create(
            adapter = object : LocalAuthenticator.Adapter<String> {
                override fun encode(secret: String): ByteArray = secret.toByteArray()
                override fun decode(bytes: ByteArray): String = bytes.toString(Charsets.UTF_8)
            }
        )

        authenticator.initialize("test").apply {
            registerPassword("11111")
        }

        val secret = authenticator.authenticateWithPassword("11111")
        assertEquals(secret, "test")
    }
}