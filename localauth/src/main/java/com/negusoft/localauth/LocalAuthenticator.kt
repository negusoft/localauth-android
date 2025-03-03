package com.negusoft.localauth

import androidx.fragment.app.FragmentActivity
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
import com.negusoft.localauth.lock.BiometricLock
import com.negusoft.localauth.lock.LockProtected
import com.negusoft.localauth.lock.LockRegister
import com.negusoft.localauth.lock.PinLock
import com.negusoft.localauth.lock.open
import com.negusoft.localauth.lock.registerBiometricLock
import com.negusoft.localauth.lock.registerPinLock
import javax.crypto.Cipher

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
    private var secretEncrypted: EncryptedValue? = null,
    private val secretPropertyRegistry: MutableMap<String, EncryptedValue> = mutableMapOf(),
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
            val secretEncrypted = decoder.readProperty()?.let(::EncryptedValue)
            val secretPropertyRegistry = decoder.readPropertyMap()
                .mapValues { EncryptedValue(it.value) }
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
            return openVault.decrypt(secretEncrypted)
        }

        @Throws(LocalAuthenticatorException::class)
        fun secretProperty(id: String): ByteArray {
            val propertyBytes = secretPropertyRegistry.get(id)
                ?: throw LocalAuthenticatorException("No property register for id '$id'.")
            return openVault.decrypt(propertyBytes)
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
        secretEncrypted = vault.encrypt(value)
    }

    fun removeSecret() {
        secretEncrypted = null
    }

    @Throws(LocalAuthenticatorException::class)
    fun updateSecretProperty(id: String, value: ByteArray) {
        val vault = vault ?: throw LocalAuthenticatorException("Local Authenticator not initialized")
        val valueEncrypted = vault.encrypt(value)
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

    interface UnlockerSuspending<Token> {
        fun decode(bytes: ByteArray): Token
        suspend fun unlock(local: LocalAuthenticator, token: Token, protected: LockProtected): LocalVault.OpenVault
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
    suspend fun <Token> authenticateSuspending(lockId: String, unlocker: UnlockerSuspending<Token>): Session {
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
            writeProperty(secretEncrypted?.value)
            writePropertyMap(secretPropertyRegistry.mapValues { it.value.value })
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

// Locks: Password ---------

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
//fun LocalAuthenticator.authenticateWithBiometric(lockId: String)
//= authenticate(lockId, LocalAuthenticator.Unlockers.withPassword(password))

// Locks: Biometric ---------

fun LocalAuthenticator.Editor.registerBiometric(lockId: String) {
    registerLock(
        lockId = lockId,
        encoder = { it.encode() }
    ) { authenticator, register ->
        register.registerBiometricLock("${authenticator.id}_$lockId")
    }
}
fun LocalAuthenticator.Unlockers.withBiometric(authenticator: suspend (Cipher) -> Cipher) = object : LocalAuthenticator.UnlockerSuspending<BiometricLock.Token> {
    override fun decode(bytes: ByteArray) = BiometricLock.Token.restore(bytes)
    override suspend fun unlock(
        local: LocalAuthenticator,
        token: BiometricLock.Token,
        protected: LockProtected
    ): LocalVault.OpenVault {
        return protected.open(token, authenticator)
    }
}
fun LocalAuthenticator.Unlockers.withBiometric(activity: FragmentActivity) = object : LocalAuthenticator.UnlockerSuspending<BiometricLock.Token> {
    override fun decode(bytes: ByteArray) = BiometricLock.Token.restore(bytes)
    override suspend fun unlock(
        local: LocalAuthenticator,
        token: BiometricLock.Token,
        protected: LockProtected
    ): LocalVault.OpenVault {
        return protected.open(token, activity)
    }
}
suspend fun LocalAuthenticator.authenticateWithBiometric(lockId: String, authenticator: suspend (Cipher) -> Cipher)
        = authenticateSuspending(lockId, LocalAuthenticator.Unlockers.withBiometric(authenticator))
suspend fun LocalAuthenticator.authenticateWithBiometric(lockId: String, activity: FragmentActivity)
        = authenticateSuspending(lockId, LocalAuthenticator.Unlockers.withBiometric(activity))

suspend fun <Result> LocalAuthenticator.authenticatedWithBiometric(lockId: String, authenticator: suspend (Cipher) -> Cipher, session: suspend Session.() -> Result)
        = authenticatedSuspending(lockId, LocalAuthenticator.Unlockers.withBiometric(authenticator), session)
suspend fun <Result> LocalAuthenticator.authenticatedWithBiometric(lockId: String, activity: FragmentActivity, session: suspend Session.() -> Result)
        = authenticatedSuspending(lockId, LocalAuthenticator.Unlockers.withBiometric(activity), session)


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