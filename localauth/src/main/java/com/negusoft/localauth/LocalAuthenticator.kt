package com.negusoft.localauth

import com.negusoft.localauth.LocalAuthenticator.Editor
import com.negusoft.localauth.LocalAuthenticator.Session
import com.negusoft.localauth.LocalAuthenticator.Unlockers
import com.negusoft.localauth.persistence.ByteCoding
import com.negusoft.localauth.persistence.ByteCodingException
import com.negusoft.localauth.persistence.readStringProperty
import com.negusoft.localauth.persistence.writeProperty
import com.negusoft.localauth.persistence.writePropertyMap
import com.negusoft.localauth.vault.EncryptedValue
import com.negusoft.localauth.vault.LocalVault
import com.negusoft.localauth.vault.lock.LockProtected
import com.negusoft.localauth.vault.lock.LockRegister
import com.negusoft.localauth.vault.lock.PinLock
import com.negusoft.localauth.vault.lock.open
import com.negusoft.localauth.vault.lock.registerPinLock

class LocalAuthenticatorException(message: String, cause: Throwable? = null) : Exception(message, cause)

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
class LocalAuthenticator private constructor(
    val id: String = "local_authenticator",
    private var vault: LocalVault? = null,
    private var secretEncrypted: ByteArray? = null,
    private val secretPropertyRegistry: MutableMap<String, ByteArray> = mutableMapOf(),
    private val publicPropertyRegistry: MutableMap<String, ByteArray> = mutableMapOf(),
    private val lockRegistry: MutableMap<String, ByteArray> = mutableMapOf()
) {

    companion object {

        private const val ENCODING_VERSION: Byte = 0x00

        /** Create an empty Authenticator */
        fun create() = LocalAuthenticator()

        /** Restore an existing Authenticator from the data produced by 'encode()'. */
        fun restore(
            encoded: ByteArray
        ): LocalAuthenticator {
            val decoder = ByteCoding.decode(encoded)
            if (!decoder.checkValueEquals(byteArrayOf(ENCODING_VERSION))) {
                throw ByteCodingException("Wrong encoding version (${encoded[0]}).")
            }
            val id = decoder.readStringProperty() ?: throw ByteCodingException("Failed to decode 'id'.")
            val vault = decoder.readProperty()?.let { LocalVault.restore(it) } ?: throw ByteCodingException("Failed to decode vault.")
            val secretEncrypted = decoder.readProperty()
            val secretPropertyRegistry = decoder.readPropertyMap()
                .toMutableMap()
            val publicPropertyRegistry = decoder.readPropertyMap()
                .toMutableMap()
            val lockRegistry = decoder.readPropertyMap()
                .toMutableMap()

            return LocalAuthenticator(id, vault, secretEncrypted, secretPropertyRegistry, publicPropertyRegistry, lockRegistry)
        }
    }

    inner class Editor(
        val authenticator: LocalAuthenticator,
        private val openVault: LocalVault.OpenVault
    ) {

        fun <Token> registerLock(
            lockId: String,
            encoder: (Token) -> ByteArray,
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
        fun secret(): ByteArray {
            val secretEncrypted = secretEncrypted ?: throw LocalAuthenticatorException("Secret not registered.")
            return openVault.decrypt(EncryptedValue.decode(secretEncrypted))
        }

        @Throws(LocalAuthenticatorException::class)
        fun secretProperty(id: String): ByteArray {
            val propertyBytes = secretPropertyRegistry.get(id)
                ?: throw LocalAuthenticatorException("No property register for id '$id'.")
            return openVault.decrypt(EncryptedValue.decode(propertyBytes))
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
        secret: ByteArray? = null,
        secretProperties: Map<String, ByteArray>? = null,
        publicProperties: Map<String, ByteArray>? = null
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

    @Throws(LocalAuthenticatorException::class)
    fun updateSecret(value: ByteArray) {
        val vault = vault ?: throw LocalAuthenticatorException("Local Authenticator not initialized")
        secretEncrypted = vault.encrypt(value).encode()
    }

    fun removeSecret() {
        secretEncrypted = null
    }

    @Throws(LocalAuthenticatorException::class)
    fun updateSecretProperty(id: String, value: ByteArray) {
        val vault = vault ?: throw LocalAuthenticatorException("Local Authenticator not initialized")
        val valueEncrypted = vault.encrypt(value).encode()
        secretPropertyRegistry[id] = valueEncrypted
    }

    fun removeSecretProperty(id: String) {
        secretPropertyRegistry.remove(id)
    }

    fun publicProperty(id: String): ByteArray? = publicPropertyRegistry[id]

    fun updatePublicProperty(id: String, value: ByteArray) {
        publicPropertyRegistry[id] = value
    }

    fun removePublicProperty(id: String) {
        publicPropertyRegistry.remove(id)
    }

    fun lockEnabled(lockId: String) = lockRegistry.containsKey(lockId)

    fun unregisterLock(lockId: String) {
        lockRegistry.remove(lockId)
    }

    object Unlockers

    interface Unlocker<Token> {
        fun decode(bytes: ByteArray): Token
        fun unlock(local: LocalAuthenticator, token: Token, protected: LockProtected): LocalVault.OpenVault
    }

    @Throws(LocalAuthenticatorException::class)
    fun <Token> authenticate(lockId: String, unlocker: Unlocker<Token>): Session {
        val tokenBytes = lockRegistry[lockId] ?: throw LocalAuthenticatorException("Not lock width id '$lockId'")
        val token: Token = try { unlocker.decode(tokenBytes) } catch(t: Throwable) {
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
        val openVault = unlocker.unlock(this, token, protected)

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
    fun <Token> authenticatedSecret(lockId: String, unlocker: Unlocker<Token>): ByteArray {
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

    /** Encode the authenticator to bytes. */
    fun encode(): ByteArray {
        return ByteCoding.encode(prefix = byteArrayOf(ENCODING_VERSION)) {
            writeProperty(id)
            writeProperty(vault?.encode())
            writeProperty(secretEncrypted)
            writePropertyMap(secretPropertyRegistry)
            writePropertyMap(publicPropertyRegistry)
            writePropertyMap(lockRegistry)
        }
    }

}

fun <T> LocalAuthenticator.initialize(secret: T?, encoder: (T) -> ByteArray): Editor {
    val encoded = secret?.let(encoder)
    return initialize(encoded)
}
fun <T> LocalAuthenticator.updateSecret(secret: T, encoder: (T) -> ByteArray) {
    updateSecret(encoder(secret))
}
fun <T> LocalAuthenticator.Session.secret(decoder: (ByteArray) -> T): T {
    return decoder(secret())
}

// Locks ---------

fun LocalAuthenticator.Editor.registerPassword(lockId: String, password: String) {
    registerLock(
        lockId = lockId,
        encoder = { it.encode() }
    ) { authenticator, register ->
        register.registerPinLock(password, "${authenticator.id}_$lockId")
    }
}
fun LocalAuthenticator.Unlockers.withPassword(password: String) = object : LocalAuthenticator.Unlocker<PinLock.Token> {
    override fun decode(bytes: ByteArray) = PinLock.Token.restore(bytes)
    override fun unlock(
        local: LocalAuthenticator,
        token: PinLock.Token,
        protected: LockProtected
    ): LocalVault.OpenVault {
        return protected.open(token, password)
    }
}
fun LocalAuthenticator.authenticateWithPassword(lockId: String, password: String)
= authenticate(lockId, LocalAuthenticator.Unlockers.withPassword(password))


@Throws
fun LocalAuthenticator.authenticatedSecret(password: String) =
    authenticatedSecret("password", Unlockers.withPassword(password))

fun <T> LocalAuthenticator.authenticatedSecret(password: String, decoder: (ByteArray) -> T): T =
    decoder(authenticatedSecret(password))

@Throws
fun LocalAuthenticator.authenticate(password: String): Session =
    authenticate("password", Unlockers.withPassword(password))

@Throws
fun <T> LocalAuthenticator.authenticated(password: String, session: Session.() -> T): T =
    authenticated("password", Unlockers.withPassword(password), session)