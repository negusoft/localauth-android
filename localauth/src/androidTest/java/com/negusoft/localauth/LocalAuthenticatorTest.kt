package com.negusoft.localauth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.negusoft.localauth.authenticator.LocalAuthenticator
import com.negusoft.localauth.authenticator.authenticatedWithPasswordLock
import com.negusoft.localauth.authenticator.initialize
import com.negusoft.localauth.authenticator.registerPasswordLock
import com.negusoft.localauth.coding.encode
import com.negusoft.localauth.coding.restore
import com.negusoft.localauth.lock.LockException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAuthenticatorTest {
    object Adapter {
        fun encode(secret: String): ByteArray = secret.toByteArray()
        fun decode(bytes: ByteArray): String = bytes.toString(Charsets.UTF_8)
    }

    @Test
    fun storeAndRetrieveSecret() {
        val authenticator = LocalAuthenticator.create()

        authenticator.initialize("test", Adapter::encode).apply {
            registerPasswordLock("password", "11111")
        }
        authenticator.updateSecretProperty("testProperty", "secret_property".toByteArray())
        authenticator.updatePublicProperty("testProperty", "public_property".toByteArray())

        val secret = authenticator.authenticatedWithPasswordLock("password", "11111") {
            Adapter.decode(secret())
        }
        assertEquals(secret, "test")

        val secretProperty = authenticator
            .authenticatedWithPasswordLock("password", "11111") { secretProperty("testProperty") }
            .decodeToString()
        assertEquals(secretProperty, "secret_property")

        val publicProperty = authenticator.publicProperty("testProperty")?.decodeToString()
        assertEquals(publicProperty, "public_property")

        assertTrue(authenticator.lockEnabled("password"))
    }


    @Test
    fun changePassword() {
        val authenticator = LocalAuthenticator.create()

        authenticator.initialize("test", Adapter::encode).apply {
            registerPasswordLock("password", "11111")
        }

        authenticator.authenticatedWithPasswordLock("password", "11111") {
            edit {
                registerPasswordLock("password", "22222")
            }
        }

        try {
            authenticator.authenticatedWithPasswordLock("password", "11111") {
                fail("Should have thrown an exception")
            }
            fail("Should have thrown an exception")
        } catch (e: LockException) {
            // Expected
        }

        val secret = authenticator.authenticatedWithPasswordLock("password", "22222") {
            Adapter.decode(secret())
        }
        assertEquals(secret, "test")
    }


    @Test
    fun storeAndRestoreAuthenticator() {
        val encodedAuthenticator = LocalAuthenticator.create().apply {
            initialize("test", Adapter::encode).apply {
                registerPasswordLock("password", "11111")
            }
            updateSecretProperty("testProperty", "secret_property".toByteArray())
            updatePublicProperty("testProperty", "public_property".toByteArray())
        }.encode()

        LocalAuthenticator.restore(encodedAuthenticator).let { decodedAuthenticator ->
            val secret = decodedAuthenticator.authenticatedWithPasswordLock("password", "11111") {
                Adapter.decode(secret())
            }
            assertEquals(secret, "test")

            val secretProperty = decodedAuthenticator
                .authenticatedWithPasswordLock("password", "11111") { secretProperty("testProperty") }
                .decodeToString()
            assertEquals(secretProperty, "secret_property")

            val publicProperty = decodedAuthenticator.publicProperty("testProperty")?.decodeToString()
            assertEquals(publicProperty, "public_property")

            assertTrue(decodedAuthenticator.lockEnabled("password"))
        }
    }


    @Test
    fun serializeAndDeserializeAuthenticator() {
        val authenticator = LocalAuthenticator.create().apply {
            initialize("test", Adapter::encode).apply {
                registerPasswordLock("password", "11111")
            }
            updateSecretProperty("testProperty", "secret_property".toByteArray())
            updatePublicProperty("testProperty", "public_property".toByteArray())
        }
        val encodedAuthenticator = Json.encodeToString(authenticator)

        Json.decodeFromString<LocalAuthenticator>(encodedAuthenticator).let { decodedAuthenticator ->
            val secret = decodedAuthenticator.authenticatedWithPasswordLock("password", "11111") {
                Adapter.decode(secret())
            }
            assertEquals(secret, "test")

            val secretProperty = decodedAuthenticator
                .authenticatedWithPasswordLock("password", "11111") { secretProperty("testProperty") }
                .decodeToString()
            assertEquals(secretProperty, "secret_property")

            val publicProperty = decodedAuthenticator.publicProperty("testProperty")?.decodeToString()
            assertEquals(publicProperty, "public_property")

            assertTrue(decodedAuthenticator.lockEnabled("password"))
        }
    }

}