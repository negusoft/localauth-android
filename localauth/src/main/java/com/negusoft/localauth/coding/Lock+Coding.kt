package com.negusoft.localauth.coding

import com.negusoft.localauth.lock.BiometricLock
import com.negusoft.localauth.lock.KeyStoreLockCommons.TokenAdapter
import com.negusoft.localauth.lock.PasswordLock
import com.negusoft.localauth.lock.SimpleLock

private const val ENCODING: Byte = 0x00

/************************** COMMON ************************************/

@Throws(ByteCodingException::class)
private fun <T> restoreKeyStoreLockToken(
    encoded: ByteArray,
    adapter: TokenAdapter<T>
): T {
    val decoder = ByteCoding.decode(encoded)
    if (!decoder.checkValueEquals(byteArrayOf(ENCODING))) {
        throw ByteCodingException("Wrong encoding version (${encoded[0]}).")
    }
    val alias = decoder.readStringProperty() ?: throw ByteCodingException("Failed to decode 'alias'.")
    val method = decoder.readStringProperty()
    val encryptedSecret = decoder.readFinal()
    return adapter.toToken(alias, method, encryptedSecret)
}
private fun encodeKeyStoreLockToken(alias: String, encryptionMethod: String?, encryptedSecret: ByteArray): ByteArray {
    return ByteCoding.encode(prefix = byteArrayOf(ENCODING)) {
        writeProperty(alias)
        writeProperty(encryptionMethod)
        writeValue(encryptedSecret)
    }
}


/************************** SIMPLE LOCK ************************************/

/**
 * Restore the token from the data produced by 'encode()'.
 * @throws ByteCodingException on failure.
 */
@Throws(ByteCodingException::class)
fun SimpleLock.Token.Companion.restore(encoded: ByteArray) = restoreKeyStoreLockToken(encoded) { alias, method, encryptedSecret ->
    SimpleLock.Token(alias, method, encryptedSecret)
}
/** Encode the token to bytes. */
fun SimpleLock.Token.encode(): ByteArray = encodeKeyStoreLockToken(keystoreAlias, encryptionMethod, encryptedSecret)


/************************** PASSWORD LOCK ************************************/

/**
 * Restore the token from the data produced by 'encode()'.
 * @throws ByteCodingException on failure.
 */
@Throws(ByteCodingException::class)
fun PasswordLock.Token.Companion.restore(encoded: ByteArray) = restoreKeyStoreLockToken(encoded) { alias, method, encryptedSecret ->
    PasswordLock.Token(alias, method, encryptedSecret)
}
/** Encode the token to bytes. */
fun PasswordLock.Token.encode(): ByteArray = encodeKeyStoreLockToken(keystoreAlias, encryptionMethod, encryptedSecret)


/************************** BIOMETRIC LOCK ************************************/

/**
 * Restore the token from the data produced by 'encode()'.
 * @throws ByteCodingException on failure.
 */
@Throws(ByteCodingException::class)
fun BiometricLock.Token.Companion.restore(encoded: ByteArray) = restoreKeyStoreLockToken(encoded) { alias, method, encryptedSecret ->
    BiometricLock.Token(alias, method, encryptedSecret)
}
/** Encode the token to bytes. */
fun BiometricLock.Token.encode(): ByteArray = encodeKeyStoreLockToken(keystoreAlias, encryptionMethod, encryptedSecret)