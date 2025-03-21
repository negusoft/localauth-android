package com.negusoft.localauth.coding

import com.negusoft.localauth.authenticator.LocalAuthenticator
import com.negusoft.localauth.authenticator.Property
import com.negusoft.localauth.lock.EncodedLockToken
import com.negusoft.localauth.vault.EncryptedValue
import com.negusoft.localauth.vault.LocalVault

private const val ENCODING: Byte = 0x00

/** Restore an existing Authenticator from the data produced by 'encode()'. */
@Throws(ByteCodingException::class)
fun LocalAuthenticator.Companion.restore(encoded: ByteArray): LocalAuthenticator {
    val decoder = ByteCoding.decode(encoded)
    if (!decoder.checkValueEquals(byteArrayOf(ENCODING))) {
        throw ByteCodingException("Wrong encoding version (${encoded[0]}).")
    }
    val id = decoder.readStringProperty() ?: throw ByteCodingException("Failed to decode 'id'.")
    val vault = decoder.readProperty()?.let { LocalVault.restore(it) }// ?: throw ByteCodingException("Failed to decode vault.")
    val secretEncrypted = decoder.readProperty()?.let(::EncryptedValue)
    val secretPropertyRegistry = decoder.readPropertyMap()
        .mapValues { EncryptedValue(it.value) }
        .toMutableMap()
    val publicPropertyRegistry = decoder.readPropertyMap()
        .mapValues { Property(it.value) }
        .toMutableMap()
    val lockRegistry = decoder.readPropertyMap()
        .mapValues { EncodedLockToken(it.value) }
        .toMutableMap()

    return LocalAuthenticator(id, vault, secretEncrypted, secretPropertyRegistry, publicPropertyRegistry, lockRegistry)
}

fun LocalAuthenticator.encode() = ByteCoding.encode(byteArrayOf(ENCODING)) {
    writeProperty(id)
    writeProperty(vault?.encode())
    writeProperty(secretEncrypted?.bytes)
    writePropertyMap(secretPropertyRegistry.mapValues { it.value.bytes })
    writePropertyMap(publicPropertyRegistry.mapValues { it.value.bytes })
    writePropertyMap(lockRegistry.mapValues { it.value.bytes })
}
