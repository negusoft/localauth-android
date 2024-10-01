package com.negusoft.localauth.keystore

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

object BiometricHelper {

    suspend fun showBiometricPrompt(activity: FragmentActivity, cipher: Cipher, title: String, cancelText: String): Cipher? {
        return suspendCancellableCoroutine { continuation ->
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText(cancelText)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            val biometricPrompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int,
                                                       errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        continuation.resume(null)
                    }

                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        val cipher = result.cryptoObject?.cipher!!
                        continuation.resume(cipher)
                    }
                })
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
            continuation.invokeOnCancellation {
                biometricPrompt.cancelAuthentication()
            }
        }
    }

    /** Show the biometric prompt. Returns true on successful authentication, false otherwise. */
    suspend fun showBiometricPromptCheck(activity: FragmentActivity, title: String, cancelText: String): Boolean {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(cancelText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int,
                                                       errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        continuation.resume(false)
                    }

                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        continuation.resume(true)
                    }
                })
            biometricPrompt.authenticate(promptInfo)
            continuation.invokeOnCancellation {
                biometricPrompt.cancelAuthentication()
            }
        }
    }
}