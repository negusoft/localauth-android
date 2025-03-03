package com.negusoft.localauth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.negusoft.localauth.core.RefreshToken
import com.negusoft.localauth.core.restoreTokens
import com.negusoft.localauth.core.saveTokens
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
class MockAuthenticatorTest {

    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    val prefs = appContext.getSharedPreferences("test", Context.MODE_PRIVATE)

    @Test
    fun shouldPersistTokens() {
        prefs.saveTokens(mapOf(
            RefreshToken("token_1") to "username_1",
            RefreshToken("token_2") to "username_2"
        ))
        val restored = prefs.restoreTokens()
        assert(restored.size == 2)
        assert(restored[RefreshToken("token_1")] == "username_1")
        assert(restored[RefreshToken("token_2")] == "username_2")

    }
}