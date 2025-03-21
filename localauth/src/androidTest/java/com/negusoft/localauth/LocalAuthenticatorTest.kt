package com.negusoft.localauth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.negusoft.localauth.authenticator.LocalAuthenticator
import com.negusoft.localauth.authenticator.Property
import com.negusoft.localauth.authenticator.WrongPasswordWithMaxAttemptsException
import com.negusoft.localauth.authenticator.asBoolean
import com.negusoft.localauth.authenticator.asInt
import com.negusoft.localauth.authenticator.asString
import com.negusoft.localauth.authenticator.authenticatedWithPasswordLock
import com.negusoft.localauth.authenticator.decode
import com.negusoft.localauth.authenticator.getPasswordLockRetryAttempts
import com.negusoft.localauth.authenticator.registerPasswordLock
import com.negusoft.localauth.coding.encode
import com.negusoft.localauth.coding.restore
import com.negusoft.localauth.lock.Password
import com.negusoft.localauth.lock.WrongPasswordException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAuthenticatorTest {

    @Test
    fun storeAndRetrieveSecret() {
        val authenticator = LocalAuthenticator.create()

        authenticator.initialize(Property("test")).apply {
            registerPasswordLock("password", Password("11111"))
        }
        authenticator.updateSecretProperty("testProperty", Property("secret_property"))
        authenticator.updatePublicProperty("testProperty", Property("public_property"))

        val secret = authenticator.authenticatedWithPasswordLock("password", Password("11111")) {
            secret().asString()
        }
        assertEquals(secret, "test")

        val secretProperty = authenticator
            .authenticatedWithPasswordLock("password", Password("11111")) { secretProperty("testProperty") }
            .asString()
        assertEquals(secretProperty, "secret_property")

        val publicProperty = authenticator.publicProperty("testProperty")?.asString()
        assertEquals(publicProperty, "public_property")

        assertTrue(authenticator.lockEnabled("password"))
    }


    @Test
    fun changePassword() {
        val authenticator = LocalAuthenticator.create()

        authenticator.initialize(Property("test")).apply {
            registerPasswordLock("password", Password("11111"))
        }

        authenticator.authenticatedWithPasswordLock("password", Password("11111")) {
            edit {
                registerPasswordLock("password", Password("22222"))
            }
        }

        try {
            authenticator.authenticatedWithPasswordLock("password", Password("11111")) {
                fail("Should have thrown an exception")
            }
            fail("Should have thrown an exception")
        } catch (e: WrongPasswordException) {
            // Expected
        }

        val secret = authenticator.authenticatedWithPasswordLock("password", Password("22222")) {
            secret().asString()
        }
        assertEquals(secret, "test")
    }


    @Test
    fun storeAndRestoreAuthenticator() {
        val encodedAuthenticator = LocalAuthenticator.create().apply {
            initialize(Property("test")).apply {
                registerPasswordLock("password", Password("11111"))
            }
            updateSecretProperty("testProperty", Property("secret_property"))
            updatePublicProperty("testProperty", Property("public_property"))
        }.encode()

        LocalAuthenticator.restore(encodedAuthenticator).let { decodedAuthenticator ->
            val secret = decodedAuthenticator.authenticatedWithPasswordLock("password", Password("11111")) {
                secret().asString()
            }
            assertEquals(secret, "test")

            val secretProperty = decodedAuthenticator
                .authenticatedWithPasswordLock("password", Password("11111")) { secretProperty("testProperty") }
                .asString()
            assertEquals(secretProperty, "secret_property")

            val publicProperty = decodedAuthenticator.publicProperty("testProperty")?.asString()
            assertEquals(publicProperty, "public_property")

            assertTrue(decodedAuthenticator.lockEnabled("password"))
        }
    }


    @Test
    fun serializeAndDeserializeAuthenticator() {
        val authenticator = LocalAuthenticator.create().apply {
            initialize(Property("test")).apply {
                registerPasswordLock("password", Password("11111"))
            }
            updateSecretProperty("testProperty", Property("secret_property"))
            updatePublicProperty("testProperty", Property("public_property"))
        }
        val encodedAuthenticator = Json.encodeToString(authenticator)

        Json.decodeFromString<LocalAuthenticator>(encodedAuthenticator).let { decodedAuthenticator ->
            val secret = decodedAuthenticator.authenticatedWithPasswordLock("password", Password("11111")) {
                secret().asString()
            }
            assertEquals(secret, "test")

            val secretProperty = decodedAuthenticator
                .authenticatedWithPasswordLock("password", Password("11111")) { secretProperty("testProperty") }
                .asString()
            assertEquals(secretProperty, "secret_property")

            val publicProperty = decodedAuthenticator.publicProperty("testProperty")?.asString()
            assertEquals(publicProperty, "public_property")

            assertTrue(decodedAuthenticator.lockEnabled("password"))
        }
    }

    @Test
    fun passwordMaxAttemptsTest() {
        val maxAttempts = 2
        val authenticator = LocalAuthenticator.create().apply {
            initialize(Property("test")).apply {
                registerPasswordLock("password", Password("11111"), maxAttempts)
            }
        }
        // First attempt
        try {
            authenticator.authenticatedWithPasswordLock("password", Password("wrong"), maxAttempts) {}
            fail()
        } catch (e: WrongPasswordWithMaxAttemptsException) {
            assertEquals(e.attemptsRemaining, 1)
            assertEquals(authenticator.getPasswordLockRetryAttempts("password"), 1)
        }
        // Second attempt
        try {
            authenticator.authenticatedWithPasswordLock("password", Password("wrong"), maxAttempts) {}
            fail()
        } catch (e: WrongPasswordWithMaxAttemptsException) {
            assertEquals(e.attemptsRemaining, 0)
            assertEquals(authenticator.getPasswordLockRetryAttempts("password"), 0)
        }
        // attemptsRemaining shouldn't go below 0.
        try {
            authenticator.authenticatedWithPasswordLock("password", Password("wrong"), maxAttempts) {}
            fail()
        } catch (e: WrongPasswordWithMaxAttemptsException) {
            assertEquals(e.attemptsRemaining, 0)
            assertEquals(authenticator.getPasswordLockRetryAttempts("password"), 0)
        }
        // Once attempts exhausted, it should fail even with the correct password.
        try {
            authenticator.authenticatedWithPasswordLock("password", Password("11111"), maxAttempts) {}
            fail()
        } catch (e: WrongPasswordWithMaxAttemptsException) {
            assertEquals(e.attemptsRemaining, 0)
            assertEquals(authenticator.getPasswordLockRetryAttempts("password"), 0)
        }
    }

    @Test
    fun passwordMaxAttemptsShouldResetOnSuccess() {
        val maxAttempts = 2
        val authenticator = LocalAuthenticator.create().apply {
            initialize(Property("test")).apply {
                registerPasswordLock("password", Password("11111"), maxAttempts)
            }
        }
        // First attempt -> Fail
        try {
            authenticator.authenticatedWithPasswordLock("password", Password("wrong"), maxAttempts) {}
            fail()
        } catch (e: WrongPasswordWithMaxAttemptsException) {
            assertEquals(e.attemptsRemaining, 1)
            assertEquals(authenticator.getPasswordLockRetryAttempts("password"), 1)
        }
        // First attempt -> Success and reset attempts
        try {
            authenticator.authenticatedWithPasswordLock("password", Password("11111"), maxAttempts) {}
            assertEquals(authenticator.getPasswordLockRetryAttempts("password"), 2)
        } catch (e: WrongPasswordWithMaxAttemptsException) {
            fail()
        }
        // Third attempt -> Fail with max attempts reset
        try {
            authenticator.authenticatedWithPasswordLock("password", Password("wrong"), maxAttempts) {}
            fail()
        } catch (e: WrongPasswordWithMaxAttemptsException) {
            assertEquals(e.attemptsRemaining, 1)
            assertEquals(authenticator.getPasswordLockRetryAttempts("password"), 1)
        }
    }

    @Test
    fun testPropertyAdapters() {
        // Generic
        data class GenericCheck(val value: String)
        Property(GenericCheck("Test")) { it.value.encodeToByteArray() }.let { property ->
            val decoded = property.decode { GenericCheck(it.decodeToString()) }
            assertEquals(GenericCheck("Test"), decoded)
        }

        // String
        assertEquals("test", Property("test").asString())
        assertEquals("", Property("").asString())

        // Int
        assertEquals(42, Property(42).asInt())
        assertEquals(0, Property(0).asInt())
        assertEquals(-42, Property(-42).asInt())
        assertEquals(Int.MAX_VALUE, Property(Int.MAX_VALUE).asInt())
        assertEquals(Int.MIN_VALUE, Property(Int.MIN_VALUE).asInt())

        // Boolean
        assert(Property(true).bytes.contentEquals(byteArrayOf(1)))
        assert(Property(false).bytes.contentEquals(byteArrayOf(0)))
        assert(Property(byteArrayOf(1)).asBoolean() == true)
        assert(Property(byteArrayOf(0)).asBoolean() == false)
    }


}