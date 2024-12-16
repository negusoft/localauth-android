package com.negusoft.localauth.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.negusoft.localauth.ui.common.InputDialog
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
        val activity = LocalContext.current as FragmentActivity

        val saveRequired = viewModel.saveRequired.collectAsState()
        val showDiscardChangesDialog = remember { mutableStateOf(false) }
        BackHandler(saveRequired.value) {
            showDiscardChangesDialog.value = true
        }
        if (showDiscardChangesDialog.value) {
            DiscardChangesConfirmationDialog(
                onDismiss = { showDiscardChangesDialog.value = false },
                onConfirm = {
                    onUp()
                    showDiscardChangesDialog.value = false
                }
            )
        }
        fun onUpWithConfirmation() {
            if (saveRequired.value)
                showDiscardChangesDialog.value = true
            else
                onUp()
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

        val titleInput = viewModel.titleInput.collectAsState()
        titleInput.value?.let { input ->
            TitleInputDialog(input)
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
            title = vault.value.name,
            subtitle = vault.value.id,
            secrets = secretItems.value,
            open = viewModel.isOpen.collectAsState().value,
            pinLockEnabled = vault.value.pinLockEnabled,
            biometricLockEnabled = vault.value.biometricLockEnabled,
            saveRequired = viewModel.saveRequired.collectAsState().value,
            onNewValue = { showNewValueDialog.value = true },
            onReadValue = { item ->
                val secret = vault.value.secretValues.find { item.id == it.id }!!
                viewModel.readSecretValue(secret)
            },
            onUp = ::onUpWithConfirmation,
            onSave = viewModel::save,
            onDelete = {
                viewModel.delete()
                onUp()
            },
            onTitleSelected = viewModel::changeVaultName,
            onUnlockWithPin = viewModel::unlockWithPinCode,
            onEnablePinLock = viewModel::enablePinLock,
            onDisablePinLock = viewModel::disablePinLock,
            onUnlockWithBiometric = { viewModel.unlockWithBiometric(activity) },
            onEnableBiometricLock = { viewModel.enableBiometricLock(activity) },
            onDisableBiometricLock = viewModel::disableBiometricLock,
        )
    }

    @Composable
    fun Content(
        title: String,
        subtitle: String,
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
        onTitleSelected: () -> Unit,
        onUnlockWithPin: () -> Unit,
        onEnablePinLock: () -> Unit,
        onDisablePinLock: () -> Unit,
        onUnlockWithBiometric: () -> Unit,
        onEnableBiometricLock: () -> Unit,
        onDisableBiometricLock: () -> Unit,
    ) {
        Scaffold(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            topBar = { AppBar(title, subtitle, onUp, onDelete, onTitleSelected) },
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
                        onUnlockWithBiometric = onUnlockWithBiometric,
                        onEnableBiometricLock = onEnableBiometricLock,
                        onDisableBiometricLock = onDisableBiometricLock
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
    fun DiscardChangesConfirmationDialog(
        onDismiss: () -> Unit,
        onConfirm: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "Discard changes?") },
            text = {
                Text(text = "Changes since the last save will be lost.")
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    content = { Text("Discard changes") }
                )
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
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
    fun TitleInputDialog(input: TitleInputModel) {
        InputDialog(
            title = "Edit title",
            inputLabel = "title",
            confirmText = "Confirm",
            dismissText = "Dismiss",
            input = input.input.collectAsState().value,
            onInputChange = { input.input.value = it },
            onConfirm = input::confirm,
            onDismiss = input::cancel
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
        onUnlockWithBiometric: () -> Unit,
        onEnableBiometricLock: () -> Unit,
        onDisableBiometricLock: () -> Unit,
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
                onEnable = onEnableBiometricLock,
                onDisable = onDisableBiometricLock,
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppBar(
        title: String,
        subtitle: String,
        onUp: () -> Unit,
        onDelete: () -> Unit,
        onTitleSelected: () -> Unit
    ) {
        LargeTopAppBar(
            navigationIcon = {
                IconButton(onClick = onUp) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "back")
                }
            },
            title = {
                Column(
                    modifier = Modifier.clickable { onTitleSelected() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = title,
                            maxLines = 1,
                        )
                        Icon(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp),
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit title",
                        )
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            },
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
            subtitle = "asdf-asdf-asdf-asdf",
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
            onTitleSelected = {},
            onUnlockWithPin = {},
            onEnablePinLock = {},
            onDisablePinLock = {},
            onUnlockWithBiometric = {},
            onEnableBiometricLock = {},
            onDisableBiometricLock = {}
        )
    }
}