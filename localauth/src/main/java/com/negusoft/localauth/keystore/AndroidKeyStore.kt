package com.negusoft.localauth.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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

    fun generateSecretKey(spec: KeyGenParameterSpec, algorithm: KeyAlgorithm = KeyAlgorithm.AES): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            algorithm.value,
            KEYSTORE_PROVIDER
        )
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
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

    @Throws
    fun getKeyPair(alias: String): KeyPair {
        val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
        val privateKey = entry.privateKey
        val publicKey = entry.certificate.publicKey
        return KeyPair(publicKey, privateKey)
    }

    fun deleteEntry(alias: String) {
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

fun KeyGenParameterSpec.Builder.setBiometricAuthenticated(): KeyGenParameterSpec.Builder = this
    .setUserAuthenticationRequired(true)
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