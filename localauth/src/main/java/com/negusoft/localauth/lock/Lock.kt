package com.negusoft.localauth.lock

import com.negusoft.localauth.vault.LocalVault.OpenVault
import com.negusoft.localauth.vault.LocalVaultException

/**
 * Error related to lock related operation.
 * It may be subclassed by lock implementations to provide additional information.
 */
open class LockException(message: String, cause: Throwable? = null): Exception(message, cause)


interface LockRegister {

    /**
     * Register a generic lock.
     * The registration creates the lock from the private key bytes, it can
     * throw an exception if the registration was not possible.
     */
    @Throws(LockException::class)
    suspend fun <Token> registerLockSuspending(locker: suspend (ByteArray) -> Token): Token

    /**
     * Register a generic lock synchronously.
     * The registration creates the lock from the private key bytes, it can
     * throw an exception if the registration was not possible.
     */
    @Throws(LockException::class)
    fun <Token> registerLock(locker: (ByteArray) -> Token): Token
}

interface LockProtected {

    /**
     * Opens the vault with the some unlocker method.
     * The unlocker should return the private key bytes.
     * Throws VaultLockException (or a a subclass of it) on lock failure.
     * It might throw a VaultException if an invalid key was decoded.
     */
    @Throws(LockException::class, LocalVaultException::class)
    suspend fun openSuspending(unlocker: suspend () -> ByteArray): OpenVault

    /** Synchronous version of openSuspending(). */
    @Throws(LockException::class, LocalVaultException::class)
    fun open(unlocker: () -> ByteArray): OpenVault

}