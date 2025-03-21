package com.negusoft.localauth.authenticator

import com.negusoft.localauth.lock.EncodedLockToken
import com.negusoft.localauth.lock.LockProtected
import com.negusoft.localauth.lock.LockRegister
import com.negusoft.localauth.utils.toByteArray
import com.negusoft.localauth.vault.EncryptedValue
import com.negusoft.localauth.vault.LocalVault
import kotlinx.serialization.Serializable

class LocalAuthenticatorException(message: String, cause: Throwable? = null) : Exception(message, cause)

@JvmInline
@Serializable
value class Property(val bytes: ByteArray)

/**
 * Authenticator service, allows storing protected properties locally.
 *
 * It uses a lock mechanism to protect the data. At least one lock needs to be installed first. To write
 * a secret, no lock is required, but in order to read the secret you fist need to authenticate
 * using one of the previously registered locks.
 *
 * Allowed values are:
 *  - secret: Main secret.
 *  - secret properties: Secrets that can be referenced by a string id.
 *  - public properties: These are not protected by a lock. No need to authenticate to read them.
 *
 */
@Serializable
class LocalAuthenticator internal constructor(
    val id: String = "local_authenticator",
    internal var vault: LocalVault? = null,
    internal var secretEncrypted: EncryptedValue? = null,
    internal val secretPropertyRegistry: MutableMap<String, EncryptedValue> = mutableMapOf(),
    internal val publicPropertyRegistry: MutableMap<String, Property> = mutableMapOf(),
    internal val lockRegistry: MutableMap<String, EncodedLockToken> = mutableMapOf()
) {

    companion object {

        /** Create an empty Authenticator */
        fun create() = LocalAuthenticator()
    }

    inner class Editor(
        val authenticator: LocalAuthenticator,
        private val openVault: LocalVault.OpenVault
    ) {

        fun <Token> registerLock(
            lockId: String,
            encoder: (Token) -> EncodedLockToken,
            registration: (authenticator: LocalAuthenticator, register: LockRegister) -> Token
        ) {
            val register = object : LockRegister {
                override suspend fun <Token> registerLockSuspending(locker: suspend (ByteArray) -> Token): Token {
                    return openVault.registerLockSuspending(locker)
                }

                override fun <Token> registerLock(locker: (ByteArray) -> Token): Token {
                    return openVault.registerLock(locker)
                }

            }
            val token = registration(authenticator, register)
            lockRegistry[lockId] = encoder(token)
        }

        fun close() {
            openVault.close()
        }
    }

    inner class Session(
        val authenticator: LocalAuthenticator,
        private val openVault: LocalVault.OpenVault
    ) {
        @Throws(LocalAuthenticatorException::class)
        fun secret(): Property {
            val secretEncrypted = secretEncrypted ?: throw LocalAuthenticatorException("Secret not registered.")
            return Property(openVault.decrypt(secretEncrypted))
        }

        @Throws(LocalAuthenticatorException::class)
        fun secretProperty(id: String): Property {
            val propertyBytes = secretPropertyRegistry.get(id)
                ?: throw LocalAuthenticatorException("No property register for id '$id'.")
            return Property(openVault.decrypt(propertyBytes))
        }

        @Throws(LocalAuthenticatorException::class)
        fun decrypt(encrypted: EncryptedValue): Property {
            return Property(openVault.decrypt(encrypted))
        }

        fun edit(): Editor {
            return Editor(authenticator, openVault)
        }

        fun edit(edit: Editor.() -> Unit) {
            edit(Editor(authenticator, openVault))
        }
    }

    /**
     * Set up the local authentication session and store the secret. The current session will
     * be overridden.
     * Use the returned Editor to set up authentication methods.
     */
    fun initialize(
        secret: Property? = null,
        secretProperties: Map<String, Property>? = null,
        publicProperties: Map<String, Property>? = null
    ): Editor {
        val openVault = LocalVault.create()
        vault = openVault.vault
        if (secret != null) {
            updateSecret(secret)
        }
        secretProperties?.forEach { (id, value) ->
            updateSecretProperty(id, value)
        }
        publicProperties?.forEach { (id, value) ->
            updatePublicProperty(id, value)
        }
        return Editor(
            authenticator = this,
            openVault = openVault
        )
    }


    fun initialize(
        secret: Property? = null,
        secretProperties: Map<String, Property>? = null,
        publicProperties: Map<String, Property>? = null,
        editor: Editor.() -> Unit
    ) {
        initialize(secret, secretProperties, publicProperties).apply(editor)
    }

    @Throws(LocalAuthenticatorException::class)
    fun updateSecret(value: Property) {
        val vault = vault ?: throw LocalAuthenticatorException("Local Authenticator not initialized")
        secretEncrypted = vault.encrypt(value.bytes)
    }

    fun removeSecret() {
        secretEncrypted = null
    }

    @Throws(LocalAuthenticatorException::class)
    fun updateSecretProperty(id: String, value: Property) {
        val vault = vault ?: throw LocalAuthenticatorException("Local Authenticator not initialized")
        val valueEncrypted = vault.encrypt(value.bytes)
        secretPropertyRegistry[id] = valueEncrypted
    }

    fun removeSecretProperty(id: String) {
        secretPropertyRegistry.remove(id)
    }

    fun publicProperty(id: String): Property? = publicPropertyRegistry[id]

    fun updatePublicProperty(id: String, value: Property) {
        publicPropertyRegistry[id] = value
    }

    fun removePublicProperty(id: String) {
        publicPropertyRegistry.remove(id)
    }

    /** Encrypt the value. It will not be stored in the authenticator. */
    fun encrypt(value: ByteArray): EncryptedValue {
        val vault = vault ?: throw LocalAuthenticatorException("Local Authenticator not initialized")
        return vault.encrypt(value)
    }
    fun encrypt(property: Property): EncryptedValue = encrypt(property.bytes)

    fun lockEnabled(lockId: String) = lockRegistry.containsKey(lockId)

    fun unregisterLock(lockId: String) {
        lockRegistry.remove(lockId)
    }

    object Unlockers

    interface Unlocker<Token> {
        fun decode(bytes: ByteArray): Token
        fun unlock(local: LocalAuthenticator, lockId: String, token: Token, protected: LockProtected): LocalVault.OpenVault
    }

    interface UnlockerSuspending<Token> {
        fun decode(bytes: ByteArray): Token
        suspend fun unlock(local: LocalAuthenticator, lockId: String, token: Token, protected: LockProtected): LocalVault.OpenVault
    }

    @Throws(LocalAuthenticatorException::class)
    fun <Token> authenticate(lockId: String, unlocker: Unlocker<Token>): Session {
        val encodedToken = lockRegistry[lockId] ?: throw LocalAuthenticatorException("Not lock width id '$lockId'")
        val token: Token = try { unlocker.decode(encodedToken.bytes) } catch(t: Throwable) {
            throw LocalAuthenticatorException("Failed to decode lock token.", t)
        }

        val protected = object : LockProtected {
            override suspend fun openSuspending(unlocker: suspend () -> ByteArray): LocalVault.OpenVault {
                return vault?.openSuspending(unlocker)
                    ?: throw LocalAuthenticatorException("Local Authenticator not initialized")
            }

            override fun open(unlocker: () -> ByteArray): LocalVault.OpenVault {
                return vault?.open(unlocker)
                    ?: throw LocalAuthenticatorException("Local Authenticator not initialized")
            }
        }
        val openVault = unlocker.unlock(this, lockId, token, protected)

        return Session(this, openVault)
    }

    @Throws(LocalAuthenticatorException::class)
    suspend fun <Token> authenticateSuspending(lockId: String, unlocker: UnlockerSuspending<Token>): Session {
        val encodedToken = lockRegistry[lockId] ?: throw LocalAuthenticatorException("Not lock width id '$lockId'")
        val token: Token = try { unlocker.decode(encodedToken.bytes) } catch(t: Throwable) {
            throw LocalAuthenticatorException("Failed to decode lock token.", t)
        }

        val protected = object : LockProtected {
            override suspend fun openSuspending(unlocker: suspend () -> ByteArray): LocalVault.OpenVault {
                return vault?.openSuspending(unlocker)
                    ?: throw LocalAuthenticatorException("Local Authenticator not initialized")
            }

            override fun open(unlocker: () -> ByteArray): LocalVault.OpenVault {
                return vault?.open(unlocker)
                    ?: throw LocalAuthenticatorException("Local Authenticator not initialized")
            }
        }
        val openVault = unlocker.unlock(this, lockId, token, protected)

        return Session(this, openVault)
    }

    @Throws(LocalAuthenticatorException::class)
    fun <Token, Result> authenticated(
        lockId: String,
        unlocker: Unlocker<Token>,
        session: Session.() -> Result
    ): Result {
        val sessionContext = authenticate(lockId, unlocker)
        return session(sessionContext).also {
            // TODO close session explicitly ????
        }
    }

    @Throws(LocalAuthenticatorException::class)
    suspend fun <Token, Result> authenticatedSuspending(
        lockId: String,
        unlocker: UnlockerSuspending<Token>,
        session: suspend Session.() -> Result
    ): Result {
        val sessionContext = authenticateSuspending(lockId, unlocker)
        return session(sessionContext).also {
            // TODO close session explicitly ????
        }
    }

    @Throws(LocalAuthenticatorException::class)
    fun <Token> authenticatedSecret(lockId: String, unlocker: Unlocker<Token>): Property {
        return authenticated(lockId, unlocker) { secret() }
    }

    fun reset(resetVault: Boolean = false) {
        if (resetVault) {
            vault = null
        }
        secretEncrypted = null
        secretPropertyRegistry.clear()
        publicPropertyRegistry.clear()
        lockRegistry.clear()
    }

}