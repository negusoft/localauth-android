package com.negusoft.localauth.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.negusoft.localauth.core.AuthManager
import com.negusoft.localauth.core.WrongPinCodeException
import com.negusoft.localauth.ui.theme.LocalAuthTheme

object AccountView {

    @Composable
    operator fun invoke(
        authManager: AuthManager
    ) {
        val username = authManager.accessToken.collectAsState().value?.value
        val oldPasswordDialog = remember { mutableStateOf(false) }
        val changePasswordDialog = remember { mutableStateOf<AuthManager.ChangePassword?>(null) }
        val error = remember { mutableStateOf<String?>(null) }

        fun changePassword(password: String) {
            try {
                changePasswordDialog.value = authManager.changePassword(password)
            } catch (e: WrongPinCodeException) {
                error.value = "Wrong password, pleas try again"
            } finally {
                oldPasswordDialog.value = false
            }
        }

        if (oldPasswordDialog.value) {
            CurrentPasswordDialog(
                onDismissRequest = { oldPasswordDialog.value = false },
                confirm = ::changePassword
            )
        }
        changePasswordDialog.value?.let { changePassword ->
            NewPasswordDialog(
                onDismissRequest = { changePasswordDialog.value = null },
                confirm = { newPassword ->
                    changePassword.change(newPassword)
                    changePasswordDialog.value = null
                }
            )
        }
        error.value?.let {
            ErrorDialog(it, onDismiss = { error.value = null })
        }

        Content(
            username = username ?: "",
            onChangePassword = { oldPasswordDialog.value = true },
            onLogout = { authManager.logout() }
        )
    }

    @Composable
    fun CurrentPasswordDialog(
        onDismissRequest: () -> Unit,
        confirm: (value: String) -> Unit
    ) {
        val valueField = remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = "New secret value") },
            text = {
                Column {
                    OutlinedTextField(
                        value = valueField.value,
                        onValueChange = { valueField.value = it },
                        label = { Text("Key") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm(valueField.value)
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

    @Composable
    fun ErrorDialog(
        message: String,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = message) },
            confirmButton = {
                TextButton(
                    onClick = onDismiss,
                    content = { Text("Close") }
                )
            }
        )
    }

    @Composable
    fun NewPasswordDialog(
        onDismissRequest: () -> Unit,
        confirm: (value: String) -> Unit
    ) {
        val valueField = remember { mutableStateOf("") }
        val confirmationField = remember { mutableStateOf("") }
        val wrongConfirmationError = remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = "New secret value") },
            text = {
                Column {
                    OutlinedTextField(
                        value = valueField.value,
                        onValueChange = { valueField.value = it },
                        label = { Text("Key") }
                    )
                    OutlinedTextField(
                        value = confirmationField.value,
                        onValueChange = { confirmationField.value = it },
                        label = { Text("Value") }
                    )
                    if (wrongConfirmationError.value) {
                        Text(
                            text = "Passwords don't match.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (valueField.value != confirmationField.value) {
                            return@TextButton
                        }
                        confirm(valueField.value)
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

    @Composable
    fun Content(
        username: String,
        onChangePassword: () -> Unit,
        onLogout: () -> Unit
    ) {
        Scaffold { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.width(IntrinsicSize.Min),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Hello, $username!",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onChangePassword
                    ) {
                        Text(
                            "Change password",
                            textAlign = TextAlign.Center
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onLogout
                    ) {
                        Text("Log out")
                    }
                }
            }
        }
    }

}

@Composable
@Preview
fun AccountViewPreview() {
    LocalAuthTheme {
        AccountView.Content(
            username = "Username",
            onChangePassword = {},
            onLogout = {}
        )
    }
}