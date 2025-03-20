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

    class PromptConfig(
        val title: String,
        val cancelText: String,
        val subtitle: String? = null,
        val description: String? = null,
        val confirmationRequired: Boolean? = null
    )

    suspend fun showBiometricPrompt(activity: FragmentActivity,  cipher: Cipher, config: PromptConfig): Cipher? {
        return suspendCancellableCoroutine { continuation ->
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(config.title)
                .setNegativeButtonText(config.cancelText)
                .let { if (config.subtitle != null) it.setSubtitle(config.subtitle) else it }
                .let { if (config.description != null) it.setDescription(config.description) else it }
                .let { if (config.confirmationRequired != null) it.setConfirmationRequired(config.confirmationRequired) else it }
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
    suspend fun showBiometricPromptCheck(activity: FragmentActivity, config: PromptConfig): Boolean {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(config.title)
            .setNegativeButtonText(config.cancelText)
            .let { if (config.subtitle != null) it.setSubtitle(config.subtitle) else it }
            .let { if (config.description != null) it.setDescription(config.description) else it }
            .let { if (config.confirmationRequired != null) it.setConfirmationRequired(config.confirmationRequired) else it }
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