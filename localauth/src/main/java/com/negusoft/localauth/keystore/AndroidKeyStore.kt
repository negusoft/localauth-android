package com.negusoft.localauth.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Calendar
import java.util.GregorianCalendar
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.security.auth.x500.X500Principal

class AndroidKeyStore {

    companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        fun initKeyStore(): KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
    }

    enum class KeyAlgorithm(val value: String) {
        AES(KeyProperties.KEY_ALGORITHM_AES)
    }
    enum class KeyPairAlgorithm(val value: String) {
        RSA(KeyProperties.KEY_ALGORITHM_RSA)
    }

    val keyStore: KeyStore = initKeyStore()

    // TODO: Use StrongBox when available
    fun generateSecretKey(spec: KeyGenParameterSpec, algorithm: KeyAlgorithm = KeyAlgorithm.AES): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            algorithm.value,
            KEYSTORE_PROVIDER
        )
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Generate a new key. If useStrongBox = true, It will try to create the key using StrongBox.
     * It will fall back to non StrongBox backed key if not available.
     */
    fun generateSecretKey(
        algorithm: KeyAlgorithm = KeyAlgorithm.AES,
        useStrongBox: Boolean = true,
        specBuilder: () -> KeyGenParameterSpec.Builder
    ): SecretKey {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !useStrongBox) {
            val spec = specBuilder().setStrongBoxBacked(false).build()
            return generateSecretKey(spec, algorithm)
        }
        try {
            val spec = specBuilder().setStrongBoxBacked(true).build()
            return generateSecretKey(spec, algorithm)
        } catch (e: StrongBoxUnavailableException) {
            val spec = specBuilder().setStrongBoxBacked(false).build()
            return generateSecretKey(spec, algorithm)
        }
    }

    @Throws
    fun getSecretKey(alias: String): SecretKey {
        val entry = keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    fun generateKeyPair(spec: KeyGenParameterSpec, algorithm: KeyPairAlgorithm = KeyPairAlgorithm.RSA): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            algorithm.value,
            KEYSTORE_PROVIDER
        )
        keyPairGenerator.initialize(spec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Generate a new key pair. If useStrongBox = true, It will try to create the key using StrongBox.
     * It will fall back to non StrongBox backed key if not available.
     */
    fun generateKeyPair(
        algorithm: KeyPairAlgorithm = KeyPairAlgorithm.RSA,
        useStrongBox: Boolean = true,
        specBuilder: () -> KeyGenParameterSpec.Builder
    ): KeyPair {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !useStrongBox) {
            val spec = specBuilder().setStrongBoxBacked(false).build()
            return generateKeyPair(spec, algorithm)
        }
        try {
            val spec = specBuilder().setStrongBoxBacked(true).build()
            return generateKeyPair(spec, algorithm)
        } catch (e: StrongBoxUnavailableException) {
            val spec = specBuilder().setStrongBoxBacked(false).build()
            return generateKeyPair(spec, algorithm)
        }
    }

    @Throws
    fun getKeyPair(alias: String): KeyPair {
        val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
        val privateKey = entry.privateKey
        val publicKey = entry.certificate.publicKey
        return KeyPair(publicKey, privateKey)
    }

    fun deleteKey(alias: String) {
        keyStore.deleteEntry(alias)
    }
}

fun KeyGenParameterSpec.Builder.setAES_GCM_NoPadding(): KeyGenParameterSpec.Builder = this
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256)


fun KeyGenParameterSpec.Builder.setStrongBoxBacked(useStrongbox: Boolean): KeyGenParameterSpec.Builder = this.run {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        setIsStrongBoxBacked(useStrongbox)
    } else {
        this
    }
}

fun KeyGenParameterSpec.Builder.setBiometricAuthenticated(invalidatedByBiometricEnrollment: Boolean = true): KeyGenParameterSpec.Builder = this
    .setUserAuthenticationRequired(true)
    .setInvalidatedByBiometricEnrollment(invalidatedByBiometricEnrollment)
    .run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            setUserAuthenticationValidityDurationSeconds(0)
        }
    }

fun KeyGenParameterSpec.Builder.setRSA_OAEPPadding(
    keySize: Int = 2048,
    issuer: String = "LocalAuth",
    serialNumber: Int = 1,
    validityInYears: Int = 10,
): KeyGenParameterSpec.Builder {
    val startDate = GregorianCalendar().time
    val endDate = GregorianCalendar().apply {
        add(Calendar.YEAR, validityInYears)
    }.time
    return this
        .setCertificateSerialNumber(BigInteger.valueOf(serialNumber.toLong()))
        .setCertificateSubject(X500Principal("CN=$issuer"))
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setKeySize(keySize)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
        .setCertificateNotBefore(startDate)
        .setCertificateNotAfter(endDate)
}