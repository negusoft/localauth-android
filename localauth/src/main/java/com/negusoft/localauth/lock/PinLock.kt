package com.negusoft.localauth.lock

import android.security.keystore.KeyGenParameterSpec
import com.negusoft.localauth.crypto.Ciphers
import com.negusoft.localauth.crypto.decryptWithPassword
import com.negusoft.localauth.crypto.encryptWithPassword
import kotlinx.serialization.Serializable
import javax.crypto.SecretKey

class WrongPinException : LockException("Wrong PIN code.")

class PinLock private constructor(
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
        companion object {
//            private const val ENCODING_VERSION: Byte = 0x00

            /**
             * Restore the token from the data produced by 'encode()'.
             * @throws PinLockException on failure.
             */
            @Throws(LockException::class)
            fun restore(encoded: ByteArray) = KeyStoreLockCommons.Token.restore(encoded) { alias, method, encryptedSecret ->
                Token(alias, method, encryptedSecret)
            }
        }

        val lock: PinLock get() = restore(keystoreAlias, encryptionMethod)

        /** Encode the token to bytes. */
        fun encode() = KeyStoreLockCommons.Token.encode(keystoreAlias, encryptionMethod, encryptedSecret)
    }

    companion object {

        @Throws(LockException::class)
        fun create(
            keystoreAlias: String,
            useStrongBoxWhenAvailable: Boolean = true,
            specBuilder: () -> KeyGenParameterSpec.Builder = defaultKeySpecBuilder(keystoreAlias)
        ): PinLock {
            val key = createKey(useStrongBoxWhenAvailable, specBuilder)
            return PinLock(key, keystoreAlias, null)
        }

        fun restore(
            key: SecretKey,
            keyIdentifier: String,
            encryptionMethod: String?
        ): PinLock = PinLock(key, keyIdentifier, encryptionMethod)

        @Throws(LockException::class)
        fun restore(
            keystoreAlias: String,
            encryptionMethod: String? = null
        ): PinLock {
            val key = restoreKey(keystoreAlias)
            return PinLock(key, keystoreAlias, encryptionMethod)
        }

        @Throws(LockException::class)
        fun restore(token: Token) = restore(token.keystoreAlias, token.encryptionMethod)
    }

    fun lock(secret: ByteArray, password: String): Token {
        val secretPasswordEncrypted = Ciphers.AES_GCM_NoPadding.encryptWithPassword(password, secret)
        return super.lock(secretPasswordEncrypted) { alias, method, encryptedSecret ->
            Token(alias, method, encryptedSecret)
        }
    }

    fun unlock(token: Token, password: String): ByteArray = super.unlock(
        encryptedSecret = token.encryptedSecret,
        transformation = { secretPasswordEncrypted ->
            try {
                Ciphers.AES_GCM_NoPadding
                    .decryptWithPassword(password, secretPasswordEncrypted)
                    ?: throw WrongPinException()
            } catch (e: Throwable) {
                throw WrongPinException()
            }
        }
    )
}

/**
 * Opens the vault with the given lock.
 * Throws VaultLockException (or a a subclass of it) on lock failure.
 * It might throw a VaultException if an invalid key was decoded.
 */
fun LockProtected.open(token: PinLock.Token, password: String) = open {
    val lock = PinLock.restore(token)
    lock.unlock(token, password)
}
fun LockRegister.registerPinLock(
    password: String, keystoreAlias: String, useStrongBoxWhenAvailable: Boolean = true
): PinLock.Token = registerLock { privateKeyEncoded ->
    val lock = PinLock.create(keystoreAlias, useStrongBoxWhenAvailable)
    lock.lock(privateKeyEncoded, password)
}