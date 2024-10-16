package com.negusoft.localauth.persistence

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
}

class ByteEncoder(
    val prefix: ByteArray? = null
) : EncoderContext {
    private var components = mutableListOf<ByteArray>()

    /**
     * Add a property storing the size followed by the value.
     * A null property is stored as a 0 sized property:
     * writeProperty(null) === writeProperty(byteArrayOf())
     */
    override fun writeProperty(property: ByteArray?) {
        val size = property?.size ?: 0
        if (size > Byte.MAX_VALUE.toInt())
            throw ByteCodingException("Property exceeds max size (${Byte.MAX_VALUE})")

        components.add(byteArrayOf(size.toByte()))
        property?.let { components.add(it) }
    }

    /** Add a the value bytes directly. Use when the property's length is always the same. */
    override fun writeValue(value: ByteArray) {
        components.add(value)
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

    /** Read a property in length-value format. It returns null for 0 length properties. */
    @Throws(ByteCodingException::class)
    fun readProperty(): ByteArray? {
        val size = readSize()
        return readBytes(size)
    }

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
            val size = bytes[i].toInt()
            if (size < 0)
                throw ByteCodingException("Invalid property size read ($size)")
            i += 1
            return size
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
fun EncoderContext.writeProperty(value: String) { writeProperty(value.toByteArray(Charsets.UTF_8)) }