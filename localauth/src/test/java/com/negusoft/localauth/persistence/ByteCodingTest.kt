package com.negusoft.localauth.persistence

import com.negusoft.localauth.coding.ByteCoding
import com.negusoft.localauth.coding.ByteCodingException
import com.negusoft.localauth.coding.SizeCoder
import com.negusoft.localauth.coding.writePropertyMap
import org.junit.Test

import org.junit.Assert.*

class ByteCodingTest {

    private val sample0 = byteArrayOf(0x01, 0x02, 0x03)
    private val sample1 = byteArrayOf(0x11, 0x12, 0x13)
    private val sample2 = byteArrayOf(0x21, 0x22, 0x23)
    private val sample3 = byteArrayOf(0x31, 0x32, 0x33)

    @Test
    fun encodeBasics() {
        val encoded = ByteCoding.encode(prefix = sample0) {
            writeValue(sample1)
            writeProperty(sample2)
            writeValue(sample3)
        }
        assertTrue(encoded.contentEquals(byteArrayOf(*sample0, *sample1, 0x03, *sample2, *sample3)))
    }

    @Test
    fun decodeBasics() {
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
    fun encodeList() {
        val list = listOf(sample0, sample1, sample2, sample3)
        val encoded = ByteCoding.encode {
            writePropertyList(list)
        }
        assertTrue(encoded.contentEquals(byteArrayOf(0x04, 0x03, *sample0, 0x03, *sample1, 0x03, *sample2, 0x03, *sample3)))
    }

    @Test
    fun decodeList() {
        val encoded = byteArrayOf(0x04, 0x03, *sample0, 0x03, *sample1, 0x03, *sample2, 0x03, *sample3)

        val decoder = ByteCoding.decode(encoded)
        decoder.readPropertyList().let { list ->
            assertEquals(list.size, 4)
            listOf(sample0, sample1, sample2, sample3).forEachIndexed { index, bytes ->
                assertTrue(list[index].contentEquals(bytes))
            }
        }
    }

    @Test
    fun encodeMap() {
        val map = mapOf(
            sample0.decodeToString() to sample1,
            sample2.decodeToString() to sample3
        )
        val encoded = ByteCoding.encode {
            writePropertyMap(map)
        }
        assertTrue(encoded.contentEquals(byteArrayOf(0x04, 0x03, *sample0, 0x03, *sample1, 0x03, *sample2, 0x03, *sample3)))
    }

    @Test
    fun decodeMap() {
        val encoded = byteArrayOf(0x04, 0x03, *sample0, 0x03, *sample1, 0x03, *sample2, 0x03, *sample3)

        ByteCoding.decode(encoded).readPropertyMap().let { map ->
            assertEquals(map.size, 2)
            assertTrue(map[sample0.decodeToString()].contentEquals(sample1))
            assertTrue(map[sample2.decodeToString()].contentEquals(sample3))
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

        ByteArray(Byte.MAX_VALUE.toInt() + 1).let { validProperty ->
            val encoded = ByteCoding.encode {
                writeProperty(validProperty)
            }
            assertTrue(encoded.contentEquals(byteArrayOf(0x81.toByte(), 0, *validProperty)))
        }
    }

    @Test
    fun numberEncoding() {
        fun check(size: Int, expectedEncodedSize: Int) {
            val sizeCoder = SizeCoder()
            val encoded = sizeCoder.encodeSize(size)
            assertEquals(expectedEncodedSize, encoded.size)

            var count = 0
            val decoded = sizeCoder.decodeSize(encoded, 0) { count = it }
            assertEquals(size, decoded)
            assertEquals(expectedEncodedSize, count)
        }

        check(0, 1)
        check(50, 1)
        check(127, 1)
        check(128, 2)
        check(1000, 2)
        check(1000000, 3)
        check(10000000, 4)
        check(100000000, 4)
        check(1000000000, 5)
        check(Int.MAX_VALUE, 5)

        assertThrows(IllegalStateException::class.java) {
            check(-1, 0)
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

    @Test
    fun nullAndEmptyByteArrays() {
        val encoded = ByteCoding.encode {
            writeProperty(null)
            writeProperty(byteArrayOf())
        }
        val decoder = ByteCoding.decode(encoded)

        assertNull(decoder.readProperty())
        assertNull(decoder.readProperty())
    }
}