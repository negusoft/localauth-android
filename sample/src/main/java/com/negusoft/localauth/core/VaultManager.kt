package com.negusoft.localauth.core

import android.content.Context
import androidx.core.content.edit
import com.negusoft.localauth.vault.EncryptedValue
import com.negusoft.localauth.vault.LocalVault
import com.negusoft.localauth.vault.lock.PinLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class VaultManager(
    context: Context
) {
    private val prefs = context.getSharedPreferences("VaultManager", Context.MODE_PRIVATE)

    private var _vaults = MutableStateFlow<List<VaultModel>?>(null)
    val vaults = _vaults.asStateFlow()

    /**
     * Load vaults from storage if not already loaded.
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

    /**
     * Create a new vault with a PIN lock and save it to storage.
     */
    fun createVault(name: String, password: String): VaultModel {
        val lockId = "$name.pin"
        lateinit var pinLock: PinLock
        val vault = LocalVault.create { vault ->
            pinLock = vault.registerPinLock(lockId, password)
        }
        return VaultModel.vault(name, name, vault, pinLock).also {
            save(it)
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
        val encrypted = vault.vault.encrypt(value.toByteArray())
        return vault.modify(
            values = vault.secretValues + SecretValueModel(key, key, encrypted.encode())
        ).also { save(it) }
    }

    fun save(vault: VaultModel) {
        val currentVaults = getVaults()
        val currentIndex = currentVaults.indexOfFirst { it.id == vault.id }

        val newList = if (currentIndex <0) currentVaults + vault
        else currentVaults.toMutableList().apply { set(currentIndex, vault) }.toList()

        prefs.edit {
            val value = VaultList(newList)
            putString("vaultList", Json.encodeToString(value))
        }
        _vaults.value = newList
    }
}

@Serializable
data class VaultList(val vaults: List<VaultModel>)

@Serializable
class VaultModel private constructor(
    val id: String,
    val name: String,
    val encoded: ByteArray,
    private val pinLockEncoded: ByteArray? = null,
    val secretValues: List<SecretValueModel> = listOf(),
    @Transient private var _vault: LocalVault? = null
) {
    companion object {
        fun vault(id: String, name: String, vault: LocalVault, pinLock: PinLock? = null) =
            VaultModel(id, name, vault.encode(), pinLock?.encode(), _vault = vault)
    }

    val vault: LocalVault get() {
        _vault?.let { return it }
        return LocalVault.restore(encoded).also {
            _vault = it
        }
    }

    val pinLock: PinLock? get() = pinLockEncoded?.let { PinLock.restore(it) }

    fun readValueWithPin(value: SecretValueModel, pin: String): String {
        val pinLock = pinLock ?: throw IllegalStateException("No pin lock")

        val openVault = vault.open(pinLock, pin)
        val resultBytes = openVault.decrypt(value.encryptedValue)
        openVault.close()

        return resultBytes.toString(Charsets.UTF_8)
    }

    fun modify(
        name: String = this.name,
        pinLockEncoded: ByteArray? = this.pinLockEncoded,
        values: List<SecretValueModel> = this.secretValues
    ) = VaultModel(id, name, encoded, pinLockEncoded, values, _vault)
}

@Serializable
class SecretValueModel(
    val id: String,
    val description: String,
    val encoded: ByteArray
) {
    val encryptedValue: EncryptedValue get() = EncryptedValue.decode(encoded)
}