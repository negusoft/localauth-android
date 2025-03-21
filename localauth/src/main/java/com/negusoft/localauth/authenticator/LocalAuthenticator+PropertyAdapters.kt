package com.negusoft.localauth.authenticator

import com.negusoft.localauth.authenticator.LocalAuthenticator.Editor
import com.negusoft.localauth.utils.toBoolean
import com.negusoft.localauth.utils.toByteArray
import com.negusoft.localauth.utils.toInt

// Generic
fun <T> Property(value: T, encoder: (T) -> ByteArray) = Property(encoder(value))
fun <T> Property.decode(decoder: (ByteArray) -> T): T = decoder(bytes)

// String (UTF-8)
fun Property(value: String) = Property(value.encodeToByteArray())
fun Property.asString(): String = bytes.decodeToString()

// Int
fun Property(value: Int) = Property(value.toByteArray())
fun Property.asInt(): Int = bytes.toInt()

// Boolean
fun Property(value: Boolean) = Property(value.toByteArray())
fun Property.asBoolean(): Boolean = bytes.toBoolean()