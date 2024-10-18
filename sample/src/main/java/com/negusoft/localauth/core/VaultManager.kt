package com.negusoft.localauth.core

import android.content.Context
import androidx.core.content.edit
import com.negusoft.localauth.persistence.SharedPreferencesDataStore
import com.negusoft.localauth.vault.LocalVault
import com.negusoft.localauth.vault.create
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Singleton
class VaultManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("VaultManager", Context.MODE_PRIVATE)
    private val datastore = SharedPreferencesDataStore(prefs)

//    val vaults: List<VaultModel> by lazy {
//        val json = prefs.getString("vaults", null) ?: return emptyList()
//        return Json.decodeFromString<VaultList>(json).vaults
//    }

    private var _vaults: List<VaultModel>? = null

    fun getVaults(): List<VaultModel> {
        _vaults?.let { return it }
        val json = prefs.getString("vaultList", null) ?: return emptyList()
        return Json.decodeFromString<VaultList>(json).vaults.also {
            _vaults = it
        }
    }

    /**
     * Create new Vault. Call save() in order to persist it, or it will be lost otherwise.
     */
//    fun createVault(): VaultModel {
//        val vault = LocalVault.create()
//        return VaultModel.vault(randomId(), name = "", vault)
//    }

    fun createVault(name: String, password: String): VaultModel {
        val lockId = "$name.pin"
        val vault = LocalVault.create(datastore, name) { vault ->
            vault.registerPinLock(lockId, password)
        }
        return VaultModel.vault(name, name, listOf(LockModel(lockId, LockModel.Type.PIN)), vault).also {
            val list = getVaults() + it
            prefs.edit {
                val value = VaultList(list)
                putString("vaultList", Json.encodeToString(value))
            }
            _vaults = list
        }
    }

    fun deleteVault(vault: VaultModel) {
        val list = getVaults().filter { it.id != vault.id }
        prefs.edit {
            val value = VaultList(list)
            putString("vaultList", Json.encodeToString(value))
        }
        _vaults = list
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun randomId(): String = Uuid.random().toString()

    fun save(vault: VaultModel) {
        TODO()
    }
}

@Serializable
data class VaultList(val vaults: List<VaultModel>)

@Serializable
class VaultModel private constructor(
    val id: String,
    var name: String,
    val locks: List<LockModel>,
    @Transient private var _vault: LocalVault? = null,
    @Transient private val open: LocalVault.OpenVault? = null
) {
    companion object {
        fun vault(id: String, name: String, locks: List<LockModel> = listOf(), vault: LocalVault? = null) =
            VaultModel(id, name, locks, vault, null)
        fun vault(id: String, name: String, locks: List<LockModel> = listOf(), openVault: LocalVault.OpenVault) =
            VaultModel(id, name, locks, openVault.vault, openVault)
    }

    val isOpen: Boolean = open != null
    val vault: LocalVault get() {
        _vault?.let { return it }
        return fetchVault().also {
            _vault = it
        }
    }

    private fun fetchVault(): LocalVault = TODO()
}

@Serializable
class LockModel(
    val id: String,
    val type: Type
) {
    enum class Type { PIN, BIOMETRIC }

}