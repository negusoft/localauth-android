package com.negusoft.localauth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import com.negusoft.localauth.R
import com.negusoft.localauth.core.LockedValueReference
import com.negusoft.localauth.core.VaultManager
import com.negusoft.localauth.ui.VaultDetailsView.NewValueDialog
import com.negusoft.localauth.ui.theme.LocalAuthTheme
import kotlinx.coroutines.flow.MutableStateFlow

class VaultDetailsViewModel(
    vaultId: String,
    private val manager: VaultManager
): ViewModel() {

    val title: String get() = vault.value.id

    val vault = MutableStateFlow(
        manager.getVaultById(vaultId) ?: error("No vault for id $vaultId")
    )

    fun delete() {
        manager.deleteVault(vault.value)
    }

    fun createSecretValue(key: String, value: String) {
        vault.value = manager.newSecretValue(vault.value, key, value)
    }

}

object VaultDetailsView {

    @Composable
    operator fun invoke(
        viewModel: VaultDetailsViewModel,
        onUp: () -> Unit
    ) {
        val vault = viewModel.vault.collectAsState()
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
            values = vault.value.values,
            onNewValue = { showNewValueDialog.value = true },
            onUp = onUp,
            onDelete = {
                viewModel.delete()
                onUp()
            }
        )
    }

    @Composable
    fun Content(
        title: String,
        values: List<LockedValueReference>,
        onNewValue: () -> Unit,
        onUp: () -> Unit,
        onDelete: () -> Unit
    ) {
        Scaffold(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            topBar = { AppBar(title, onUp) }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(values) { value ->
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(text = value.id)
                        Text(text = value.description)
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
                item {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        onClick = onDelete
                    ) {
                        Text(text = "DELETE")
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
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
//        BasicAlertDialog(
//            onDismissRequest = onDismissRequest
//        ) {
//            Text(text = "New dialog")
//        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppBar(title: String, onUp: () -> Unit) {
        LargeTopAppBar(
            navigationIcon = {
                IconButton(onClick = onUp) {
                    Icon(painterResource(id = R.drawable.ic_back_24), contentDescription = "back")
                }
            },
            title = { Text(text = title) }
        )
    }

}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    LocalAuthTheme {
        val showNewValueDialog = remember { mutableStateOf(true) }
        if (showNewValueDialog.value) {
            NewValueDialog(
                onDismissRequest = { showNewValueDialog.value = false },
                createValue = { _, _ -> }
            )
        }

        VaultDetailsView.Content(
            title = "TITLE",
            values = listOf(
                LockedValueReference("id1", "desc1"),
                LockedValueReference("id2", "desc2"),
                LockedValueReference("id3", "desc3"),
            ),
            onNewValue = { showNewValueDialog.value = true },
            onUp = {},
            onDelete = {}
        )
    }
}