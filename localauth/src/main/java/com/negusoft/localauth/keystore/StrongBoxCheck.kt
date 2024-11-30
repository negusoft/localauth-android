package com.negusoft.localauth.keystore

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.annotation.RequiresApi
import java.security.ProviderException
import java.util.UUID

class StrongBoxCheck {
    private var strongBoxAvailable: Boolean? = null

    fun isAvailable(context: Context): Boolean {
        strongBoxAvailable?.let { return it }
        return checkStrongBoxAvailable(context).also {
            strongBoxAvailable = it
        }
    }

    private fun checkStrongBoxAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            return false

        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE))
            return false

        // The hasSystemFeature method is not reliable and some device may fail to create
        // a key, so we need to check by creating one.
        return manualCheck()

    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun manualCheck(): Boolean {
        val alias = UUID.randomUUID().toString()
        val keyStore = AndroidKeyStore()
        try {
            val keySpec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setAES_GCM_NoPadding()
                .setStrongBoxBacked(true)
                .build()
            keyStore.generateSecretKey(keySpec)
            return true
        } catch (e: StrongBoxUnavailableException) {
            e.printStackTrace()
            return false
        } catch (e: ProviderException) {
            e.printStackTrace()
            return false
        } finally {
            keyStore.deleteEntry(alias)
        }
    }
}