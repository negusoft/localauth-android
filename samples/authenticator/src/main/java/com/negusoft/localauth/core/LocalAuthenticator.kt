package com.negusoft.localauth.core

import com.negusoft.localauth.persistence.ByteCoding
import com.negusoft.localauth.persistence.readStringProperty
import com.negusoft.localauth.persistence.writeProperty
import com.negusoft.localauth.vault.EncryptedValue
import com.negusoft.localauth.vault.LocalVault
import com.negusoft.localauth.vault.lock.PinLockException
import com.negusoft.localauth.vault.lock.PinLock
import com.negusoft.localauth.vault.lock.open
import com.negusoft.localauth.vault.lock.registerPinLock

/**
 * WIP
 * TODO:
 *  - Add secret properties
 *  - Add public properties
 *  - Generalize auth method
 *  - No State as separate class
 *  - Generic secret vs ByteArray???
 */
@Deprecated("Use new LocalAuthenticator")
class LocalAuthenticator2<Secret> private constructor(
    state: State,
    val id: String = "local_authenticator",
    private val adapter: Adapter<Secret>
) {

    companion object {

        private const val ENCODING_VERSION: Byte = 0x00

        fun <Secret> create(
            adapter: Adapter<Secret>
        ): LocalAuthenticator2<Secret> {
            return LocalAuthenticator2(
                state = State(),
                adapter = adapter
            )
        }

        fun <Secret> createOld(
            adapter: Adapter<Secret>
        ): LocalAuthenticator2<Secret>.Editor {
            val openVault = LocalVault.create()
            val authenticator = LocalAuthenticator2(
                state = State(openVault.vault),
                adapter = adapter
            )
            return authenticator.Editor(
                authenticator = authenticator,
                openVault = openVault
            )
        }

        fun <Secret> restore(
            encoded: ByteArray,
            adapter: Adapter<Secret>
        ): LocalAuthenticator2<Secret> {
            val decoder = ByteCoding.decode(encoded)
            if (!decoder.checkValueEquals(byteArrayOf(ENCODING_VERSION))) {
                throw PinLockException("Wrong encoding version (${encoded[0]}).")
            }
            val id = decoder.readStringProperty() ?: throw Exception("Failed to decode 'id'.")
            val state = State(
                vault = decoder.readProperty()?.let { LocalVault.restore(it) } ?: throw Exception("Failed to decode vault."),
                secretEncrypted = decoder.readProperty(),
                passwordToken = decoder.readProperty()
            )

            return LocalAuthenticator2(state, id, adapter)
        }
    }

    data class State(
        val vault: LocalVault? = null,
        val secretEncrypted: ByteArray? = null,
        val passwordToken: ByteArray? = null,
    )

    inner class Editor(
        val authenticator: LocalAuthenticator2<Secret>,
        private val openVault: LocalVault.OpenVault
    ) {
        fun registerPassword(password: String) {
            val token = openVault.registerPinLock(password, "${authenticator.id}_password")
//            val lock = openVault.registerPinLock("${authenticator.id}_password", password)
            authenticator.state = authenticator.state.copy(
                vault = openVault.vault,
                passwordToken = token.encode()
            )
        }

        fun close() {
            openVault.close()
        }
    }

    inner class Session(
        val authenticator: LocalAuthenticator2<Secret>,
        private val openVault: LocalVault.OpenVault
    ) {
        fun secret(): Secret {
            val secretEncrypted = state.secretEncrypted ?: throw Exception("Secret not registered.")
            val secretBytes = openVault.decrypt(EncryptedValue.decode(secretEncrypted))
            return adapter.decode(secretBytes)
        }

        fun edit(): Editor {
            return Editor(authenticator, openVault)
        }

        fun edit(edit: Editor.() -> Unit) {
            edit(Editor(authenticator, openVault))
        }
    }

    interface Adapter<Secret> {
        fun encode(secret: Secret): ByteArray
        fun decode(bytes: ByteArray): Secret
    }

    var state = state
        private set

    val passwordRegistered: Boolean get() = state.passwordToken != null

    /**
     * Set up the local authentication session and store the secret. The current session will
     * be overridden.
     * Use the returned Editor to set up authentication methods.
     */
    fun initialize(secret: Secret? = null): Editor {
        val openVault = LocalVault.create()
        state = State(openVault.vault)
        if (secret != null) {
            updateSecret(secret)
        }
        return Editor(
            authenticator = this,
            openVault = openVault
        )
    }

    fun updateSecret(secret: Secret) {
        val vault = state.vault ?: throw Exception("Local Authenticator not initialized")
        val secretBytes = adapter.encode(secret)
        val secretEncrypted = vault.encrypt(secretBytes).encode()
        state = state.copy(secretEncrypted = secretEncrypted)
    }

    fun editWithPassword(password: String): Editor {
        val vault = state.vault ?: throw Exception("Local Authenticator not initialized")
        val pinTokenBytes = state.passwordToken ?: throw Exception("Password not registered.")
        val pinToken = PinLock.Token.restore(pinTokenBytes)
        val openVault = vault.open(pinToken, password)
        return Editor(this, openVault)
    }

    fun unregisterPassword() {
        // TODO destroy key from keystore
        state = state.copy(passwordToken = null)
    }

    @Throws
    fun authenticatedSecret(password: String): Secret {
        return authenticated(password) { secret() }
    }


    @Throws
    fun authenticate(password: String): Session {
        val vault = state.vault ?: throw Exception("Local Authenticator not initialized")
        val passwordLockBytes = state.passwordToken ?: throw Exception("Password not registered.")
        val pinToken = PinLock.Token.restore(passwordLockBytes)
        val openVault = vault.open(pinToken, password)
        return Session(this, openVault)
    }

    @Throws
    fun <T> authenticated(password: String, session: Session.() -> T): T {
        val sessionContext = authenticate(password)
        return session(sessionContext).also {
            // TODO close session explicitly ????
        }
    }

    fun reset() {
        state = State(state.vault, secretEncrypted = null)
        unregisterPassword()
    }

    /** Encode the authenticator to bytes. */
    fun encode(): ByteArray {
        return ByteCoding.encode(prefix = byteArrayOf(ENCODING_VERSION)) {
            writeProperty(id)
            writeProperty(state.vault?.encode())
            writeProperty(state.secretEncrypted)
            writeProperty(state.passwordToken)
        }
    }

}