package com.negusoft.localauth.lock

import kotlinx.serialization.Serializable
import javax.crypto.SecretKey

/**
 * Simple lock implementation. IT uses the Android KeyStore to protect the secret so it is safe
 * enough for common use cases. It doesn't provide additional protections such as a password or
 * Biometric verification though.
 */
class SimpleLock private constructor(
    key: SecretKey,
    keyIdentifier: String,
    encryptionMethod: String?
): KeyStoreLockCommons.SecretKeyLock(key, keyIdentifier, encryptionMethod) {
    @Serializable
    data class Token(
        val keystoreAlias: String,
        val encryptionMethod: String?,
        val encryptedSecret: ByteArray
    ) {
        companion object

        val lock: SimpleLock get() = restore(keystoreAlias, encryptionMethod)
    }

    companion object {

        @Throws(LockException::class)
        fun create(
            key: SecretKey,
            keyIdentifier: String,
            encryptionMethod: String? = null
        ): SimpleLock {
            return SimpleLock(key, keyIdentifier, encryptionMethod)
        }

        @Throws(LockException::class)
        fun create(
            keystoreAlias: String,
            useStrongBoxWhenAvailable: Boolean = true
        ): SimpleLock {
            val key = createKeyAES_GCM_NoPadding(keystoreAlias, useStrongBoxWhenAvailable)
            return SimpleLock(key, keystoreAlias, null)
        }

        fun restore(
            key: SecretKey,
            keyIdentifier: String,
            encryptionMethod: String?
        ): SimpleLock = SimpleLock(key, keyIdentifier, encryptionMethod)

        @Throws(LockException::class)
        fun restore(
            keystoreAlias: String,
            encryptionMethod: String? = null
        ): SimpleLock {
            val key = restoreKey(keystoreAlias)
            return SimpleLock(key, keystoreAlias, encryptionMethod)
        }
        fun restore(token: Token) = restore(token.keystoreAlias, token.encryptionMethod)
    }


    fun lock(secret: ByteArray) = super.lock(secret) { alias, method, encryptedSecret ->
        Token(alias, method, encryptedSecret)
    }

    fun unlock(token: Token): ByteArray = super.unlock(token.encryptedSecret)
}

/**
 * Opens the vault with the given lock.
 * Throws VaultLockException (or a a subclass of it) on lock failure.
 * It might throw a VaultException if an invalid key was decoded.
 */
fun LockProtected.open(token: SimpleLock.Token) = open {
    val lock = SimpleLock.restore(token)
    lock.unlock(token)
}
fun LockRegister.registerSimpleLock(
    keystoreAlias: String, useStrongBoxWhenAvailable: Boolean = true
): SimpleLock.Token = registerLock { privateKeyEncoded ->
    val lock = SimpleLock.create(keystoreAlias, useStrongBoxWhenAvailable)
    lock.lock(privateKeyEncoded)
}