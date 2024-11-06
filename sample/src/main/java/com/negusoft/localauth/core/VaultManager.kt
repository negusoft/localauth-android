package com.negusoft.localauth.core

import android.content.Context
import androidx.core.content.edit
import com.negusoft.localauth.persistence.SharedPreferencesDataStore
import com.negusoft.localauth.vault.LocalVault
import com.negusoft.localauth.vault.create
import com.negusoft.localauth.vault.restore
import com.negusoft.localauth.vault.setEncrypted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class VaultManager(
    context: Context
) {
    private val prefs = context.getSharedPreferences("VaultManager", Context.MODE_PRIVATE)
    private val datastore = SharedPreferencesDataStore(prefs)

//    val vaults: List<VaultModel> by lazy {
//        val json = prefs.getString("vaults", null) ?: return emptyList()
//        return Json.decodeFromString<VaultList>(json).vaults
//    }

    private var _vaults = MutableStateFlow<List<VaultModel>?>(null)
    val vaults = _vaults.asStateFlow()

    /**
     * Load vaults form storage if not already loaded.
     */
    fun initialize() {
        if (_vaults.value != null)
            return
        _vaults.value = prefs.getString("vaultList", null)?.let { json ->
            Json.decodeFromString<VaultList>(json).vaults
        } ?: listOf()
    }

    /** Return the current vaults value. */
    private fun getVaults(): List<VaultModel> {
        initialize()
        return _vaults.value!!
    }

    fun getVaultById(id: String): VaultModel? =
        getVaults().firstOrNull { it.id == id }

//    fun getVaults(): List<VaultModel> {
//        _vaults?.let { return it }
//        val json = prefs.getString("vaultList", null) ?: return emptyList()
//        return Json.decodeFromString<VaultList>(json).vaults.also {
//            _vaults = it
//        }
//    }

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
        return VaultModel.vault(name, name, vault.encode(), listOf(LockModel(lockId, LockModel.Type.PIN)), listOf(), vault).also {
            val list = getVaults() + it
            prefs.edit {
                val value = VaultList(list)
                putString("vaultList", Json.encodeToString(value))
            }
            _vaults.value = list
        }
    }

    fun deleteVault(vault: VaultModel) {
        val list = getVaults().filter { it.id != vault.id }
        prefs.edit {
            val value = VaultList(list)
            putString("vaultList", Json.encodeToString(value))
        }
        _vaults.value = list
    }

    fun newSecretValue(vault: VaultModel, key: String, value: String): VaultModel {
        // TODO check override value
        vault.vault.setEncrypted(
            value = value.toByteArray(),
            dataStore = datastore,
            key = key
        )
        val modifiedVault = vault.copy(
            values = vault.values + LockedValueReference(key, key)
        )
        val vaults = vaults.value?.toMutableList() ?: return modifiedVault
        val vaultIndex = vaults.indexOfFirst { it.id == vault.id }
        if (vaultIndex >= 0) {
            vaults[vaultIndex] = modifiedVault
            _vaults.value = vaults
        }
        return modifiedVault
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
data class VaultModel private constructor(
    val id: String,
    val name: String,
    val encoded: ByteArray,
    val locks: List<LockModel> = listOf(),
    val values: List<LockedValueReference> = listOf(),
    @Transient private var _vault: LocalVault? = null,
    @Transient private val open: LocalVault.OpenVault? = null
) {
    companion object {
        fun vault(id: String, name: String, encoded: ByteArray, locks: List<LockModel> = listOf(), values: List<LockedValueReference> = listOf(), vault: LocalVault? = null) =
            VaultModel(id, name, encoded, locks, values, vault, null)
        fun vault(id: String, name: String, encoded: ByteArray, locks: List<LockModel> = listOf(), values: List<LockedValueReference> = listOf(), openVault: LocalVault.OpenVault) =
            VaultModel(id, name, encoded, locks, values, openVault.vault, openVault)
    }

    val isOpen: Boolean = open != null
    val vault: LocalVault get() {
        _vault?.let { return it }
        return LocalVault.restore(encoded).also {
            _vault = it
        }
    }
}

@Serializable
class LockModel(
    val id: String,
    val type: Type
) {
    enum class Type { PIN, BIOMETRIC }
}

@Serializable
class LockedValueReference(
    val id: String,
    val description: String
)