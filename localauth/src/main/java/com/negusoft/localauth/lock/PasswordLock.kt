package com.negusoft.localauth.lock

import com.negusoft.localauth.crypto.Ciphers
import com.negusoft.localauth.crypto.decryptWithPassword
import com.negusoft.localauth.crypto.encryptWithPassword
import kotlinx.serialization.Serializable
import javax.crypto.SecretKey

open class WrongPasswordException : LockException("Wrong password.")

@JvmInline
value class Password(val value: String)

class PasswordLock private constructor(
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

        val lock: PasswordLock get() = restore(keystoreAlias, encryptionMethod)
    }

    companion object {

        @Throws(LockException::class)
        fun create(
            key: SecretKey,
            keyIdentifier: String,
            encryptionMethod: String? = null
        ): PasswordLock {
            return PasswordLock(key, keyIdentifier, encryptionMethod)
        }

        @Throws(LockException::class)
        fun create(
            keystoreAlias: String,
            useStrongBoxWhenAvailable: Boolean = true
        ): PasswordLock {
            val key = createKeyAES_GCM_NoPadding(keystoreAlias, useStrongBoxWhenAvailable)
            return PasswordLock(key, keystoreAlias, null)
        }

        fun restore(
            key: SecretKey,
            keyIdentifier: String,
            encryptionMethod: String?
        ): PasswordLock = PasswordLock(key, keyIdentifier, encryptionMethod)

        @Throws(LockException::class)
        fun restore(
            keystoreAlias: String,
            encryptionMethod: String? = null
        ): PasswordLock {
            val key = restoreKey(keystoreAlias)
            return PasswordLock(key, keystoreAlias, encryptionMethod)
        }

        @Throws(LockException::class)
        fun restore(token: Token) = restore(token.keystoreAlias, token.encryptionMethod)
    }

    fun lock(secret: ByteArray, password: Password): Token {
        val secretPasswordEncrypted = Ciphers.AES_GCM_NoPadding.encryptWithPassword(password.value, secret)
        return super.lock(secretPasswordEncrypted) { alias, method, encryptedSecret ->
            Token(alias, method, encryptedSecret)
        }
    }

    fun unlock(token: Token, password: Password): ByteArray = super.unlock(
        encryptedSecret = token.encryptedSecret,
        transformation = { secretPasswordEncrypted ->
            try {
                Ciphers.AES_GCM_NoPadding
                    .decryptWithPassword(password.value, secretPasswordEncrypted)
                    ?: throw WrongPasswordException()
            } catch (e: Throwable) {
                throw WrongPasswordException()
            }
        }
    )
}

/**
 * Opens the vault with the given lock.
 * Throws VaultLockException (or a a subclass of it) on lock failure.
 * It might throw a VaultException if an invalid key was decoded.
 */
fun LockProtected.open(token: PasswordLock.Token, password: Password) = open {
    val lock = PasswordLock.restore(token)
    lock.unlock(token, password)
}
fun LockRegister.registerPasswordLock(
    password: Password, keystoreAlias: String, useStrongBoxWhenAvailable: Boolean = true
): PasswordLock.Token = registerLock { privateKeyEncoded ->
    val lock = PasswordLock.create(keystoreAlias, useStrongBoxWhenAvailable)
    lock.lock(privateKeyEncoded, password)
}