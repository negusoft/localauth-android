package com.negusoft.localauth.vault.lock

/**
 * Error related to lock related operation.
 * It may be subclassed by lock implementations to provide additional information.
 */
open class LockException(message: String, cause: Throwable? = null): Exception(message, cause)