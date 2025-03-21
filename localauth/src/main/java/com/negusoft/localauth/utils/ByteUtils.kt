package com.negusoft.localauth.utils

// Int conversion
fun ByteArray.toInt(offset: Int = 0): Int {
    val bytes = this
    return (bytes[offset + 0].toInt() shl 24) or
            (bytes[offset + 1].toInt() and 0xff shl 16) or
            (bytes[offset + 2].toInt() and 0xff shl 8) or
            (bytes[offset + 3].toInt() and 0xff)
}
fun Int.toByteArray(): ByteArray {
    val bytes = ByteArray(Int.SIZE_BYTES)
    bytes[0] = (this shr 24).toByte()
    bytes[1] = (this shr 16).toByte()
    bytes[2] = (this shr 8).toByte()
    bytes[3] = this.toByte()
    return bytes
}

// Boolean conversion: 0 -> false, else -> true
fun ByteArray.toBoolean(offset: Int = 0): Boolean {
    return get(offset + 0).toInt() != 0
}
fun Boolean.toByteArray(): ByteArray {
    return if (this) byteArrayOf(1) else byteArrayOf(0)
}