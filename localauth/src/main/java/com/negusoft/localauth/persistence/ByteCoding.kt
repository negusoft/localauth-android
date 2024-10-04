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

    fun decode():
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
        val size = property.size
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

class ByteDecoder(startIndex: Int, val bytes: ByteArray) {
    private var i = startIndex

    /** Read a property in length-value format. It returns null for 0 length properties. */
    fun readProperty(): ByteArray? {
        val size = bytes[i].toInt()
        i += 1
        val result = when {
            size == 0 -> null
            size < 0 -> throw ByteCodingException("Invalid property size ($size)")
            else -> bytes.copyOfRange(fromIndex = i, toIndex = i + size)
        }
        i += size

        return result
    }

    fun readValue(size: Int): ByteArray {
        val result = bytes.copyOfRange(fromIndex = i, toIndex = i + size)
        i += size
        return result
    }
}