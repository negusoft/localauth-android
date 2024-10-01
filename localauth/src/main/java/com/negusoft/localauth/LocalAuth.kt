package com.negusoft.localauth

import android.app.Activity
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.negusoft.localauth.crypto.CryptoUtils
import com.negusoft.localauth.crypto.CryptoUtilsRSA
import com.negusoft.localauth.crypto.PasswordEncryptedData
import com.negusoft.localauth.keystore.BiometricHelper
import com.negusoft.localauth.keystore.KeyStoreAccess
import com.negusoft.localauth.preferences.getByteArray
import com.negusoft.localauth.preferences.getStringEncrypted
import com.negusoft.localauth.preferences.putByteArray
import com.negusoft.localauth.preferences.putEncrypted
import java.security.PrivateKey
import java.security.PublicKey

object LocalAuth {
    fun createNewVault(
        prefs: SharedPreferences,
        key: String = "local_auth.vault"
    ): OpenVault {
        // generate master key pair
        val masterKeyPair = CryptoUtilsRSA.generateKeyPair()

        // TODO persist stuff
        savePublicKey(prefs, key, masterKeyPair.public)

        return OpenVault(
            publicKey = masterKeyPair.public,
            privateKey = masterKeyPair.private,
            prefs = prefs
        )
    }

    fun restoreVault(
        prefs: SharedPreferences,
        key: String = "local_auth.vault"
    ): ClosedVault? {
        val publicKey = getPublicKey(prefs, key) ?: return null
        return ClosedVault(
            publicKey = publicKey,
            prefs = prefs
        )
    }

    private fun savePublicKey(
        prefs: SharedPreferences,
        key: String,
        publicKey: PublicKey
    ) {
        prefs.edit {
            putByteArray(key, publicKey.encoded)
        }
    }

    private fun getPublicKey(prefs: SharedPreferences, key: String): PublicKey? {
        val encoded = prefs.getByteArray(key) ?: return null
        return CryptoUtilsRSA.decodePublicKey(encoded)
    }
}

class ClosedVault(
    val publicKey: PublicKey,
    private val prefs: SharedPreferences
) {
    fun openPinLock(pin: String): OpenVault? {
        // TODO use strongbox when available
        val pinCodeEntry = KeyStoreAccess.getEntry(
            alias = "local_auth.pin_lock",
            protection = KeyStoreAccess.Protection.DEFAULT,
            isStrongBoxBacked = false
        )
        val encryptedSecret = pinCodeEntry.get(prefs) ?: return null
        val privateKeyBytes = CryptoUtils.decryptWithPassword(pin, PasswordEncryptedData(encryptedSecret)) ?: return null
        val privateKey = CryptoUtilsRSA.decodePrivateKey(privateKeyBytes) ?: return null

        return OpenVault(publicKey, privateKey, prefs)
    }

    /** Encrypt the given value with the public key and store it. */
    fun saveValue(value: String, key: String) = prefs.edit {
        putEncrypted(key, value, publicKey)
    }
}

class OpenVault(
    val publicKey: PublicKey,
    val privateKey: PrivateKey,
    private val prefs: SharedPreferences
) {
    // TODO use strongbox when available
    private val pinCodeEntry = KeyStoreAccess.getEntry(
        alias = "local_auth.pin_lock",
        protection = KeyStoreAccess.Protection.DEFAULT,
        isStrongBoxBacked = false
    )
    // TODO use strongbox when available
    private val biometricEntry = KeyStoreAccess.getEntry(
        alias = "local_auth.biometric_lock",
        protection = KeyStoreAccess.Protection.BIOMETRIC,
        isStrongBoxBacked = false
    )


    fun registerPinLock(pin: String) {
        // Encrypt the master key with the pin code before storing it
        val privateKeyPasswordEncrypted = CryptoUtils.encryptWithPassword(pin, privateKey.encoded).bytes
        pinCodeEntry.store(privateKeyPasswordEncrypted, prefs)
    }

    /** Shows Biometric Prompt to confirm. Returns true on success, false otherwise. */
    suspend fun registerBiometricLock(activity: FragmentActivity, title: String, cancelText: String): Boolean {
        biometricEntry.delete()
        val success = biometricEntry.store(privateKey.encoded, prefs) { cipher ->
            return@store BiometricHelper.showBiometricPrompt(activity, cipher, title, cancelText)
        }

        return success
    }

    fun getValue(key: String): String? {
        return prefs.getStringEncrypted(key, privateKey)
    }

    fun closed(): ClosedVault {
        return ClosedVault(publicKey, prefs)
    }

}