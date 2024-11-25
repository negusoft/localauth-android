package com.negusoft.localauth.ui.details

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.negusoft.localauth.ui.details.VaultDetailsView.NewValueDialog
import com.negusoft.localauth.ui.theme.LocalAuthTheme

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

        val pinInput = viewModel.pinInput.collectAsState()
        pinInput.value?.let { input ->
            PinInputDialog(
                title = when(input.type) {
                    PinInputModel.Type.REGISTER -> "Register pin code"
                    PinInputModel.Type.UNLOCK -> "Unlock with pin code"
                },
                confirmText = when(input.type) {
                    PinInputModel.Type.REGISTER -> "Register"
                    PinInputModel.Type.UNLOCK -> "Unlock"
                },
                input = input.input.collectAsState().value,
                onInputChange = { input.input.value = it },
                onInput = input::confirm,
                onDismiss = input::cancel
            )
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

        viewModel.errorNoLocks.collectAsState().value?.let { error ->
            AlertDialog(
                title = { Text(text = "No locks registered") },
                text = { Text(text = "A vault with no locks can't be opened, rendering it useless.") },
                confirmButton = { TextButton(onClick = { error.dismiss() }) { Text(text = "Got it")} },
                onDismissRequest = { error.dismiss() },
            )
        }

        viewModel.errorWrongPin.collectAsState().value?.let { error ->
            AlertDialog(
                title = { Text(text = "Wrong password") },
                text = { Text(text = "Please enter the correct password.") },
                confirmButton = { TextButton(onClick = { error.retry() }) { Text(text = "Try again")} },
                onDismissRequest = { error.dismiss() },
            )
        }

        Content(
            title = viewModel.title,
            secrets = secretItems.value,
            open = viewModel.isOpen.collectAsState().value,
            pinLockEnabled = vault.value.pinLockEnabled,
            biometricLockEnabled = false,
            saveRequired = viewModel.saveRequired.collectAsState().value,
            onNewValue = { showNewValueDialog.value = true },
            onReadValue = { item ->
                val secret = vault.value.secretValues.find { item.id == it.id }!!
                viewModel.readSecretValue(secret)
            },
            onUp = onUp,
            onSave = viewModel::save,
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
        pinLockEnabled: Boolean,
        biometricLockEnabled: Boolean,
        saveRequired: Boolean,
        onNewValue: () -> Unit,
        onReadValue: (SecretItem) -> Unit,
        onUp: () -> Unit,
        onSave: () -> Unit,
        onDelete: () -> Unit,
        onUnlockWithPin: () -> Unit,
        onEnablePinLock: () -> Unit,
        onDisablePinLock: () -> Unit,
        onUnlockWithBiometric: () -> Unit
    ) {
        Scaffold(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            topBar = { AppBar(title, onUp, onDelete) },
            floatingActionButton = {
                FloatingActionButton(onClick = onSave) {
                    AnimatedContent(saveRequired) { saveRequired ->
                        if (saveRequired) {
                            Row(modifier = Modifier.padding(16.dp)) {
                                Text(text = "Save")
                            }
                        } else {
                            Icon(painter = rememberVectorPainter(Icons.Outlined.Check), contentDescription = "Saved")
                        }
                    }
                }
            }
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
                        pinLockEnabled = pinLockEnabled,
                        biometricLockEnabled = biometricLockEnabled,
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
    fun PinInputDialog(
        title: String,
        confirmText: String,
        input: String,
        onInputChange: (String) -> Unit,
        onInput: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = title) },
            text = {
                Column {
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        label = { Text("Pin code") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onInput,
                    content = { Text(confirmText) }
                )
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
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

    @Deprecated("Don't use")
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

        val saveRequired = remember { mutableStateOf(false) }

        VaultDetailsView.Content(
            title = "TITLE",
            secrets = listOf(
                VaultDetailsView.SecretItem("id1", "desc1", null),
                VaultDetailsView.SecretItem("id2", "desc2", "value3"),
                VaultDetailsView.SecretItem("id3", "desc3", null),
            ),
            open = true,
            pinLockEnabled = false,
            biometricLockEnabled = false,
            saveRequired = saveRequired.value,
            onNewValue = { showNewValueDialog.value = true },
            onReadValue = { secretValue.value = it.id },
            onUp = {},
            onSave = { saveRequired.value = !saveRequired.value },
            onDelete = {},
            onUnlockWithPin = {},
            onEnablePinLock = {},
            onDisablePinLock = {},
            onUnlockWithBiometric = {}
        )
    }
}