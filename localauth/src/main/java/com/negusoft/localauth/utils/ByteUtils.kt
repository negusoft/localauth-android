package com.negusoft.localauth.utils

fun ByteArray.toInt(offset: Int = 0): Int {
    val bytes = this
    return (bytes[offset + 0].toInt() shl 24) or
            (bytes[offset + 1].toInt() and 0xff shl 16) or
            (bytes[offset + 2].toInt() and 0xff shl 8) or
            (bytes[offset + 3].toInt() and 0xff)
}
fun Int.toByteArray(offset: Int = 0): ByteArray {
    val bytes = ByteArray(Int.SIZE_BYTES)
    bytes[offset + 0] = (this shr 24).toByte()
    bytes[offset + 1] = (this shr 16).toByte()
    bytes[offset + 2] = (this shr 8).toByte()
    bytes[offset + 3] = this.toByte()
    return bytes
}