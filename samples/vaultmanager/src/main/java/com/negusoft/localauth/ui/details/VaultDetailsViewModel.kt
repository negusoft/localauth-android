package com.negusoft.localauth.ui.details

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.negusoft.localauth.core.OpenVaultModel
import com.negusoft.localauth.core.SecretValueModel
import com.negusoft.localauth.core.VaultManager
import com.negusoft.localauth.core.VaultModel
import com.negusoft.localauth.keystore.BiometricHelper
import com.negusoft.localauth.lock.BiometricPromptCancelledException
import com.negusoft.localauth.lock.LockException
import com.negusoft.localauth.lock.Password
import com.negusoft.localauth.ui.common.ErrorModel
import com.negusoft.localauth.ui.common.RetryErrorModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Edit vault with given vault id. If the id is null, a new vault is created.
 * Modifications need to be saved or they will be lost.
 */
class VaultDetailsViewModel(
    vaultId: String?,
    private val manager: VaultManager
): ViewModel() {

    val isOpen = MutableStateFlow(false)
    private var openVault: OpenVaultModel? = null

    val vault: MutableStateFlow<VaultModel>

    val readValues = MutableStateFlow(mapOf<String, String>())

    val saveRequired = MutableStateFlow(false)
    val pinInput = MutableStateFlow<PinInputModel?>(null)
    val titleInput = MutableStateFlow<TitleInputModel?>(null)

    val errorNoLocks = MutableStateFlow<ErrorModel?>(null)
    val errorWrongPin = MutableStateFlow<RetryErrorModel?>(null)
    val errorBiometricFailed = MutableStateFlow<ErrorModel?>(null)

    init {
        if (vaultId != null) {
            val vaultForId = manager.getVaultById(vaultId) ?: error("No vault for id $vaultId")
            vault = MutableStateFlow(vaultForId)
        } else {
            val newVault = manager.createVault()
            openVault = newVault
            vault = MutableStateFlow(newVault.vault)
            isOpen.value = true
            saveRequired.value = true
        }
    }

    /// PIN LOCK =======================

    fun enablePinLock() {
        val openVault = openVault ?: error("Vault is not open")
        pinInput.value = PinInputModel(
            type = PinInputModel.Type.REGISTER,
            onInput = {
                doEnablePinLock(it)
                pinInput.value = null
            },
            onCancel = { pinInput.value = null }
        )
    }

    private fun doEnablePinLock(pin: Password) {
        val openVault = openVault ?: error("Vault is not open")
        openVault.registerPinLock(pin)
        saveRequired.value = true
        vault.value = openVault.vault
    }

    fun disablePinLock() {
        val openVault = openVault ?: error("Vault is not open")
        openVault.removePinLock()
        saveRequired.value = true
        vault.value = openVault.vault
    }

    fun unlockWithPinCode() {
        pinInput.value = PinInputModel(
            type = PinInputModel.Type.UNLOCK,
            onInput = ::doUnlockWithPinCode,
            onCancel = { pinInput.value = null }
        )
    }

    private fun doUnlockWithPinCode(pin: Password) {
        try {
            openVault = vault.value.open(pin)
            isOpen.value = true
        } catch (e: LockException) {
            errorWrongPin.value = RetryErrorModel(
                retry = {
                    unlockWithPinCode()
                    errorWrongPin.value = null
                },
                dismiss = { errorWrongPin.value = null }
            )
        } finally {
            pinInput.value = null
        }
    }

    /// BIOMETRIC LOCK =======================

    fun enableBiometricLock(activity: FragmentActivity) {
        val openVault = openVault ?: error("Vault is not open")
        openVault.registerBiometricLock()
        saveRequired.value = true
        vault.value = openVault.vault
    }

    fun disableBiometricLock() {
        val openVault = openVault ?: error("Vault is not open")
        openVault.removeBiometricLock()
        saveRequired.value = true
        vault.value = openVault.vault
    }

    fun unlockWithBiometric(activity: FragmentActivity, promptConfig: BiometricHelper.PromptConfig) {
        viewModelScope.launch {
            try {
                openVault = vault.value.openBiometric(activity, promptConfig)
                isOpen.value = true
            } catch (e: BiometricPromptCancelledException) {
                // no-op
            } catch (e: LockException) {
                e.printStackTrace()
                errorBiometricFailed.value = ErrorModel(
                    dismiss = { errorBiometricFailed.value = null }
                )
            }
        }
    }

    /// VALUES =======================

    fun createSecretValue(key: String, value: String) {
        saveRequired.value = true
        vault.value = manager.newSecretValue(vault.value, key, value)
    }

    /** Read secret value using the open vault */
    fun readSecretValue(value: SecretValueModel): String {
        val vault = openVault ?: error("Vault is not open")
        return vault.readValue(value).also {
            readValues.value += (value.id to it)
        }
    }

    /// MISC =======================

    fun changeVaultName() {
        titleInput.value = TitleInputModel(
            initialValue = vault.value.name,
            onInput = { value ->
                doSetVaultName(value)
                titleInput.value = null
            },
            onCancel = { titleInput.value = null }
        )
    }

    private fun doSetVaultName(name: String) {
        vault.value = vault.value.modify(name = name)
        saveRequired.value = true
    }

    fun save() {
        if (!vault.value.hasAnyVaults) {
            errorNoLocks.value = ErrorModel { errorNoLocks.value = null }
            return
        }
        manager.save(vault.value)
        saveRequired.value = false
    }

    fun delete() {
        manager.deleteVault(vault.value)
    }

}

class PinInputModel(
    val type: Type,
    private val onInput: (Password) -> Unit,
    private val onCancel: () -> Unit
) {
    enum class Type { REGISTER, UNLOCK }
    val input = MutableStateFlow("")
    fun confirm() { onInput(Password(input.value)) }
    fun cancel() { onCancel() }
}

class TitleInputModel(
    initialValue: String,
    private val onInput: (String) -> Unit,
    private val onCancel: () -> Unit
) {
    val input = MutableStateFlow(initialValue)
    fun confirm() { onInput(input.value) }
    fun cancel() { onCancel() }
}