package com.negusoft.localauth.vault.lock

/**
 * Error related to LocalVault's lock related operation.
 * Ii may be subclassed by VaultLock implementations to provide additional information.
 */
open class VaultLockException(message: String, cause: Throwable? = null): Exception(message, cause)

interface VaultLock<Input> {

    /**
     * Returns the secret key bytes on success.
     * Throws VaultLockException (or a a subclass of it) on failure.
     */
    @Throws(VaultLockException::class)
    suspend fun unlock(input: Input): ByteArray
}

/** Synchronous version of VaultLock. The method are not suspending. */
interface VaultLockSync<Input> {

    /**
     * Returns the secret key bytes on success.
     * Throws VaultLockException (or a a subclass of it) on failure.
     */
    @Throws(VaultLockException::class)
    fun unlockSync(input: Input): ByteArray
}