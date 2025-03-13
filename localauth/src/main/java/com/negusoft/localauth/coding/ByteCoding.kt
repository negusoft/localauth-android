package com.negusoft.localauth.coding

import kotlin.experimental.and
import kotlin.experimental.or

class ByteCodingException(message: String, cause: Throwable? = null): Exception(message, cause)

object ByteCoding {

    /**
     * Create an array that concatenates the prefix and the chained the properties with size-value
     * format, followed by the 'content'.
     */
    @Deprecated("don't use")
    fun encode(prefix: ByteArray? = null, content: ByteArray, vararg properties: ByteArray): ByteArray {
        val prefixSize = prefix?.size ?: 0
        var resultSize = content.size + prefixSize
        properties.forEach { resultSize = resultSize + 1 + it.size }
        val result = ByteArray(resultSize)

        // Prefix
        prefix?.copyInto(result)

        // Properties
        var i = prefixSize
        properties.forEach { prop ->
            val size = prop.size
            if (size > UByte.MAX_VALUE.toInt())
                throw ByteCodingException("Property exceeds max size (${UByte.MAX_VALUE})")
            result[i] = size.toByte()
            prop.copyInto(result, destinationOffset = i + 1)
            i += size + 1
        }

        // Content
        content.copyInto(result, destinationOffset = i + 1)

        return result
    }

    fun encode(prefix: ByteArray? = null, config: EncoderContext.() -> Unit): ByteArray {
        return ByteEncoder(prefix)
            .also(config)
            .encode()
    }

    fun decode(bytes: ByteArray, startIndex: Int = 0) = ByteDecoder(bytes, startIndex)
}

interface EncoderContext {
    /** Add a property storing the size followed by the value */
    fun writeProperty(property: ByteArray?)
    /** Add a the value bytes directly. Use when the property's length is always the same. */
    fun writeValue(value: ByteArray)
    /** Add list of properties. It will write the size followed by each property. */
    fun writePropertyList(propertyList: List<ByteArray>)
}

/** Writes the the map as a list by alternating Key and Value properties. */
fun <K> EncoderContext.writePropertyMap(map: Map<K, ByteArray>, keyMapping: (K) -> ByteArray) {
    val list = map.flatMap { listOf(keyMapping(it.key), it.value) }
    writePropertyList(list)
}
/** Writes the the map as a list by alternating Key and Value properties. */
fun EncoderContext.writePropertyMap(map: Map<String, ByteArray>) = writePropertyMap(map) { it.toByteArray() }

/**
 * Encode/decode the size such that.
 * For every encoded byte, if the byte starts by 1, the value is
 * shifted 6 bits to the left and added to the next byte.
 */
class SizeCoder {
    companion object {
        const val VALUE_MASK: Int = 0x0000007F
        const val FLAG_MASK: Byte = 0x80.toByte()
    }

    fun encodeSize(size: Int): ByteArray {
        check(size >= 0) { "Negative values not allowed" }
        if (size == 0) return byteArrayOf(0)

        var reminder = size
        val lastByte = (reminder and VALUE_MASK).toByte()
        reminder = reminder ushr 7

        var result = byteArrayOf(lastByte)
        while (reminder != 0) {
            val byteValue = (reminder and VALUE_MASK).toByte() or FLAG_MASK
            reminder = reminder ushr 7
            result = byteArrayOf(byteValue, *result)
        }

        return result
    }

    fun decodeSize(data: ByteArray, startIndex: Int, bytesRead: (Int) -> Unit): Int {
        var result = 0
        var index = startIndex
        var byteCount = 0
        while (true) {
            val byte = data[index]
            val byteValue = byte.toInt() and VALUE_MASK
            result += byteValue
            byteCount += 1
            index += 1
            if (byte and FLAG_MASK == 0.toByte()) {
                bytesRead(byteCount)
                return result
            }
            result = result shl 7
        }
    }
}

class ByteEncoder(
    val prefix: ByteArray? = null
) : EncoderContext {
    private val components = mutableListOf<ByteArray>()
    private val sizeCoder = SizeCoder()

    /**
     * Add a property storing the size followed by the value.
     * A null property is stored as a 0 sized property:
     * writeProperty(null) === writeProperty(byteArrayOf())
     */
    override fun writeProperty(property: ByteArray?) {
        val size = property?.size ?: 0
        val sizeBytes = sizeCoder.encodeSize(size)
        components.add(sizeBytes)

        property?.let { components.add(it) }
    }

    /** Add a the value bytes directly. Use when the property's length is always the same. */
    override fun writeValue(value: ByteArray) {
        components.add(value)
    }

    /** Writes the the map as a list by alternating Key and Value properties. */
    override fun writePropertyList(propertyList: List<ByteArray>) {
        val sizeBytes = sizeCoder.encodeSize(propertyList.size)
        components.add(sizeBytes)
        propertyList.forEach {
            writeProperty(it)
        }
    }

    fun encode(): ByteArray {
        val prefixSize = prefix?.size ?: 0
        val resultSize = components.fold(prefixSize) { acc, component -> acc + component.size}

        return ByteArray(resultSize).also { result ->
            prefix?.copyInto(result)

            var i = prefixSize
            components.forEach {
                it.copyInto(result, destinationOffset = i)
                i += it.size
            }
        }
    }
}

class ByteDecoder(val bytes: ByteArray, startIndex: Int) {
    private var i = startIndex
    private val sizeCoder = SizeCoder()

    /** Read a property in length-value format. It returns null for 0 length properties. */
    @Throws(ByteCodingException::class)
    fun readProperty(): ByteArray? {
        val size = readSize()
        if (size == 0) return null
        return readBytes(size)
    }

    /** Read a list of properties. Fires read the size, then read the properties in a loop. */
    @Throws(ByteCodingException::class)
    fun readPropertyList(): List<ByteArray> {
        val size = readSize()
        return List(size) { readProperty() ?: byteArrayOf() }
    }

    /** Read a map property. It returns empty map for 0 length properties. */
    @Throws(ByteCodingException::class)
    fun <K> readPropertyMap(keyMapping: (ByteArray) -> K): Map<K, ByteArray> {
        val list = readPropertyList()
        val size = list.size / 2

        val iterator = list.iterator()
        val result = hashMapOf<K, ByteArray>()
        for (i in 0 until size) {
            val key = keyMapping(iterator.next())
            result[key] = iterator.next()
        }
        return result
    }

    /** Read a map property. It returns empty map for 0 length properties. */
    @Throws(ByteCodingException::class)
    fun readPropertyMap(): Map<String, ByteArray> = readPropertyMap { it.decodeToString() }

    /** Read the number of bytes specified. */
    @Throws(ByteCodingException::class)
    fun readValue(size: Int) = readBytes(size)


    /**
     * Read the sample length as the provided array and compare the contents.
     * Returns true if the bytes are the same, false otherwise.
     */
    @Throws(ByteCodingException::class)
    fun checkValueEquals(bytes: ByteArray): Boolean {
        val value = readBytes(bytes.size)
        return value.contentEquals(bytes)
    }

    /** Read the number of bytes specified. */
    @Throws(ByteCodingException::class)
    private fun readBytes(size: Int): ByteArray {
        try {
            val result = bytes.copyOfRange(fromIndex = i, toIndex = i + size)
            i += size
            return result
        } catch (e: IndexOutOfBoundsException) {
            throw ByteCodingException("Not enough data. Requested $size byte but only ${bytes.size - i} available (index=$i)", e)
        }
    }

    /**
     * Read the size of the property.
     * Stored in one byte, will throw an error if the value can't be read or is a negative value.
     */
    @Throws(ByteCodingException::class)
    private fun readSize(): Int {
        try {
            val size = sizeCoder.decodeSize(bytes, i) {
                i += it
            }
            if (size < 0)
                throw ByteCodingException("Invalid property size read ($size)")
            return size
        } catch (e: IllegalStateException) {
            throw ByteCodingException("Failed to read property size")
        } catch (e: IndexOutOfBoundsException) {
            throw ByteCodingException("Not enough data to read property size", e)
        }
    }

    /** Read the remaining bytes. */
    fun readFinal(): ByteArray {
        val result = bytes.copyOfRange(fromIndex = i, toIndex = bytes.size)
        i = bytes.size
        return result
    }
}

// String
fun ByteDecoder.readStringProperty(): String? = readProperty()?.toString(Charsets.UTF_8)
fun EncoderContext.writeProperty(value: String?) { writeProperty(value?.toByteArray(Charsets.UTF_8)) }