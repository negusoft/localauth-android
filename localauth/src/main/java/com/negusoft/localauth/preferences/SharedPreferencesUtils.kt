package com.negusoft.localauth.preferences

import android.content.SharedPreferences
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Extensions to read/write ByteArrays
@OptIn(ExperimentalEncodingApi::class)
fun SharedPreferences.getByteArray(key: String): ByteArray? = getString(key, null)?.let { Base64.decode(it) }
@OptIn(ExperimentalEncodingApi::class)
fun SharedPreferences.Editor.putByteArray(key: String, value: ByteArray) {
    val string = Base64.encode(value)
    putString(key, string)
}