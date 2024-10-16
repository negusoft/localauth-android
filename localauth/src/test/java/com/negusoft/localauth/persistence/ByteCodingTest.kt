package com.negusoft.localauth.persistence

import org.junit.Test

import org.junit.Assert.*

class ByteCodingTest {

    private val sample0 = byteArrayOf(0x01, 0x02, 0x03)
    private val sample1 = byteArrayOf(0x11, 0x12, 0x13)
    private val sample2 = byteArrayOf(0x21, 0x22, 0x23)
    private val sample3 = byteArrayOf(0x31, 0x32, 0x33)

    @Test
    fun encode() {
        val encoded = ByteCoding.encode(prefix = sample0) {
            writeValue(sample1)
            writeProperty(sample2)
            writeValue(sample3)
        }
        assertTrue(encoded.contentEquals(byteArrayOf(*sample0, *sample1, 0x03, *sample2, *sample3)))
    }

    @Test
    fun decode() {
        val encoded = byteArrayOf(*sample0, *sample1, 0x03, *sample2, *sample3)

        val decoder = ByteCoding.decode(encoded)
        decoder.readValue(3).let { prefix ->
            assertTrue(prefix.contentEquals(sample0))
        }
        decoder.readValue(3).let { value ->
            assertTrue(value.contentEquals(sample1))
        }
        decoder.readProperty().let { value ->
            assertTrue(value.contentEquals(sample2))
        }
        decoder.readFinal().let { value ->
            assertTrue(value.contentEquals(sample3))
        }
    }

    @Test
    fun largeProperties() {
        ByteArray(Byte.MAX_VALUE.toInt()).let { validProperty ->
            val encoded = ByteCoding.encode {
                writeProperty(validProperty)
            }
            assertTrue(encoded.contentEquals(byteArrayOf(Byte.MAX_VALUE, *validProperty)))
        }

        ByteArray(Byte.MAX_VALUE.toInt() + 1).let { invalidProperty ->
            assertThrows(ByteCodingException::class.java) {
                ByteCoding.encode {
                    writeProperty(invalidProperty)
                }
                fail("Method should have thrown")
            }
        }
    }

    @Test
    fun checkBytes() {
        val encoded = ByteCoding.encode(prefix = sample0) {
            writeValue(sample1)
        }

        val decoder = ByteCoding.decode(encoded)
        assertTrue(decoder.checkValueEquals(sample0))
        assertFalse(decoder.checkValueEquals(sample0))
    }

    @Test
    fun decodingExceptions() {
        val encoded = byteArrayOf(*sample0, *sample1, 0x03, *sample2, *sample3)

        assertThrows(ByteCodingException::class.java) {
            ByteCoding.decode(encoded).readValue(42)
        }

        assertThrows(ByteCodingException::class.java) {
            val decoder = ByteCoding.decode(encoded)
            decoder.readValue(10)
            decoder.readProperty()
        }

        assertThrows(ByteCodingException::class.java) {
            val decoder = ByteCoding.decode(byteArrayOf(-0x01))
            decoder.readProperty()
        }
    }
}