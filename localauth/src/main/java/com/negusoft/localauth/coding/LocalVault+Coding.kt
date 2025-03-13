package com.negusoft.localauth.coding

import com.negusoft.localauth.crypto.Keys
import com.negusoft.localauth.vault.LocalVault

private const val ENCODING: Byte = 0x00

@Throws(ByteCodingException::class)
fun LocalVault.Companion.restore(encoded: ByteArray): LocalVault {
    val decoder = ByteCoding.decode(encoded)
    if (!decoder.checkValueEquals(byteArrayOf(ENCODING)))
        throw ByteCodingException("Wrong encoding version (${encoded[0]}).")

    val keyType = decoder.readStringProperty()
    assert(keyType.isNullOrBlank()) { "Invalid key type." }

    val keyBytes = decoder.readFinal()
    val publicKey = Keys.RSA.decodePublicKey(keyBytes) ?: throw ByteCodingException("Failed to decode RSA public key")

    return restore(publicKey, keyType)
}

fun LocalVault.encode() = ByteCoding.encode(byteArrayOf(ENCODING)) {
    writeProperty(keyType)
    writeValue(publicKey.encoded)
}
