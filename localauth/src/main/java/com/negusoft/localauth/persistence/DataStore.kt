package com.negusoft.localauth.persistence

import android.content.SharedPreferences
import androidx.core.content.edit
import com.negusoft.localauth.preferences.getByteArray
import com.negusoft.localauth.preferences.putByteArray

/**
 * Data storage strategy. Stores/retrieves binary values by key.
 */
interface DataStore {
    fun get(key: String): ByteArray?
    fun set(key: String, value: ByteArray?)
    fun contains(key: String): Boolean
    fun remove(key: String)
}

operator fun DataStore.get(key: String): ByteArray? = get(key)
operator fun DataStore.set(key: String, data: ByteArray?) { set(key, data) }

class InMemoryDataStore : DataStore  {
    private val data = mutableMapOf<String, ByteArray?>()

    override fun get(key: String): ByteArray? = data.get(key)

    override fun set(key: String, value: ByteArray?) {
        data[key] = value
    }

    override fun contains(key: String): Boolean = data.contains(key)

    override fun remove(key: String) {
        data.remove(key)
    }
}

class SharedPreferencesDataStore(private val prefs: SharedPreferences): DataStore {
    override fun get(key: String): ByteArray? = prefs.getByteArray(key)

    override fun set(key: String, value: ByteArray?) {
        prefs.edit {
            if (value == null)
                remove(key)
            else
                putByteArray(key, value)
        }
    }

    override fun contains(key: String): Boolean = prefs.contains(key)

    override fun remove(key: String) {
        prefs.edit { remove(key) }
    }

}