package com.negusoft.localauth.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.negusoft.localauth.core.OpenVaultModel
import com.negusoft.localauth.core.SecretValueModel
import com.negusoft.localauth.core.VaultManager
import com.negusoft.localauth.ui.details.VaultDetailsView.NewValueDialog
import com.negusoft.localauth.ui.theme.LocalAuthTheme
import kotlinx.coroutines.flow.MutableStateFlow

class VaultDetailsViewModel(
    vaultId: String,
    private val manager: VaultManager
): ViewModel() {

    val title: String get() = vault.value.id

    val isOpen = MutableStateFlow(false)
    private var openVault: OpenVaultModel? = null

    val vault = MutableStateFlow(
        manager.getVaultById(vaultId) ?: error("No vault for id $vaultId")
    )

    val readValues = MutableStateFlow(mapOf<String, String>())

    /// PIN LOCK =======================

    fun enablePinLock() {
        val openVault = openVault ?: error("Vault is not open")
        openVault.registerPinLock(vault.value.id, "supersafepassword")
        manager.save(openVault.vault)
        vault.value = openVault.vault
    }

    fun disablePinLock() {
        val openVault = openVault ?: error("Vault is not open")
        openVault.removePinLock()
        manager.save(openVault.vault)
        vault.value = openVault.vault
    }

    fun unlockWithPinCode(pin: String = "supersafepassword") {
        openVault = vault.value.open(pin)
        isOpen.value = true
    }

    /// VALUES =======================

    fun createSecretValue(key: String, value: String) {
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

    fun delete() {
        manager.deleteVault(vault.value)
    }

}

object VaultDetailsView {

    class SecretItem(
        val id: String,
        val description: String,
        val value: String?
    )

    @Composable
    operator fun invoke(
        viewModel: VaultDetailsViewModel,
        onUp: () -> Unit
    ) {
        val vault = viewModel.vault.collectAsState()
        val readValues = viewModel.readValues.collectAsState()
        val secretItems = remember {
            derivedStateOf {
                vault.value.secretValues.map {
                    val value = readValues.value[it.id]
                    SecretItem(it.id, it.description, value)
                }
            }
        }

        val showNewValueDialog = remember { mutableStateOf(false) }
        if (showNewValueDialog.value) {
            NewValueDialog(
                onDismissRequest = { showNewValueDialog.value = false },
                createValue = { key, value ->
                    viewModel.createSecretValue(key, value)
                    showNewValueDialog.value = false
                }
            )
        }

        Content(
            title = viewModel.title,
            secrets = secretItems.value,
            open = viewModel.isOpen.collectAsState().value,
            onNewValue = { showNewValueDialog.value = true },
            onReadValue = { item ->
                val secret = vault.value.secretValues.find { item.id == it.id }!!
                viewModel.readSecretValue(secret)
            },
            onUp = onUp,
            onDelete = {
                viewModel.delete()
                onUp()
            },
            onUnlockWithPin = viewModel::unlockWithPinCode,
            onEnablePinLock = viewModel::enablePinLock,
            onDisablePinLock = viewModel::disablePinLock,
            onUnlockWithBiometric = { TODO() }
        )
    }

    @Composable
    fun Content(
        title: String,
        secrets: List<SecretItem>,
        open: Boolean,
        onNewValue: () -> Unit,
        onReadValue: (SecretItem) -> Unit,
        onUp: () -> Unit,
        onDelete: () -> Unit,
        onUnlockWithPin: () -> Unit,
        onEnablePinLock: () -> Unit,
        onDisablePinLock: () -> Unit,
        onUnlockWithBiometric: () -> Unit
    ) {
        Scaffold(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            topBar = { AppBar(title, onUp, onDelete) }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    LockedStateBar(open)
                }
                item {
                    LocksBar(
                        open = open,
                        pinLockEnabled = true,
                        biometricLockEnabled = false,
                        onUnlockWithPin = onUnlockWithPin,
                        onEnablePinLock = onEnablePinLock,
                        onDisablePinLock = onDisablePinLock,
                        onUnlockWithBiometric = onUnlockWithBiometric
                    )
                }
                item {
                    Text(modifier = Modifier.padding(top = 16.dp), text = "Secret values", style = MaterialTheme.typography.headlineSmall)
                }
                items(secrets) { item ->
                    SecretItemView(item, open) {
                        onReadValue(item)
                    }
                    HorizontalDivider()
                }
                item {
                    TextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        onClick = onNewValue) {
                        Text(text = "+ New secure value", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    @Composable
    fun LockedStateBar(open: Boolean) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            val imageVector = if (open) Icons.Filled.Lock else Icons.Filled.Lock
            val text = if (open) "Open" else "Locked"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text)
                Icon(imageVector, contentDescription = text)
            }
        }
    }

    @Composable
    fun LocksBar(
        open: Boolean,
        pinLockEnabled: Boolean,
        biometricLockEnabled: Boolean,
        onUnlockWithPin: () -> Unit,
        onEnablePinLock: () -> Unit,
        onDisablePinLock: () -> Unit,
        onUnlockWithBiometric: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LockView(
                modifier = Modifier.weight(1f),
                title = "Pin lock",
                enabled = pinLockEnabled,
                onEnable = onEnablePinLock,
                onDisable = onDisablePinLock,
                open = open,
                onUnlock = onUnlockWithPin,
                color = Color.Red
            )
            LockView(
                modifier = Modifier.weight(1f),
                title = "Biometric lock",
                enabled = biometricLockEnabled,
                onEnable = {},
                onDisable = {},
                open = open,
                onUnlock = onUnlockWithBiometric,
                color = Color.Green
            )
        }
    }

    @Composable
    fun SecretItemView(
        item: SecretItem,
        open: Boolean,
        onReadValue: () -> Unit
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = item.id, style = MaterialTheme.typography.titleMedium)
                Text(text = item.description, style = MaterialTheme.typography.bodyMedium)
            }
            when {
                !open -> Text(text = "*****", style = MaterialTheme.typography.titleMedium)
                item.value != null -> Text(text = item.value, style = MaterialTheme.typography.titleMedium)
                else -> TextButton(onClick = onReadValue) {
                    Text(text = "Read value")
                }
            }
        }
    }

    @Composable
    fun NewValueDialog(
        onDismissRequest: () -> Unit,
        createValue: (key: String, value: String) -> Unit
    ) {
        val keyField = remember { mutableStateOf("") }
        val valueField = remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = "New secret value") },
            text = {
                Column {
                    OutlinedTextField(
                        value = keyField.value,
                        onValueChange = { keyField.value = it },
                        label = { Text("Key") }
                    )
                    OutlinedTextField(
                        value = valueField.value,
                        onValueChange = { valueField.value = it },
                        label = { Text("Value") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        createValue(keyField.value, valueField.value)
                        onDismissRequest()
                    },
                    content = { Text("Confirm") }
                )
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) { Text("Dismiss") }
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppBar(
        title: String,
        onUp: () -> Unit,
        onDelete: () -> Unit
    ) {
        LargeTopAppBar(
            navigationIcon = {
                IconButton(onClick = onUp) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "back")
                }
            },
            title = { Text(text = title) },
            actions = {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete vault"
                    )
                }
            }
        )
    }

}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    LocalAuthTheme {
        val showNewValueDialog = remember { mutableStateOf(false) }
        if (showNewValueDialog.value) {
            NewValueDialog(
                onDismissRequest = { showNewValueDialog.value = false },
                createValue = { _, _ -> }
            )
        }

        val secretValue = remember { mutableStateOf<String?>(null) }
        secretValue.value?.let { value ->
            AlertDialog(
                onDismissRequest = { secretValue.value = null },
                confirmButton = { },
                text = { Text(text = value) }
            )
        }

        VaultDetailsView.Content(
            title = "TITLE",
            secrets = listOf(
                VaultDetailsView.SecretItem("id1", "desc1", null),
                VaultDetailsView.SecretItem("id2", "desc2", "value3"),
                VaultDetailsView.SecretItem("id3", "desc3", null),
            ),
            open = false,
            onNewValue = { showNewValueDialog.value = true },
            onReadValue = { secretValue.value = it.id },
            onUp = {},
            onDelete = {},
            onUnlockWithPin = {},
            onEnablePinLock = {},
            onDisablePinLock = {},
            onUnlockWithBiometric = {}
        )
    }
}