package com.negusoft.localauth.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.negusoft.localauth.core.AuthManager
import com.negusoft.localauth.core.WrongPinCodeException
import com.negusoft.localauth.ui.common.PasswordInputDialog
import com.negusoft.localauth.ui.theme.LocalAuthTheme

object AccountView {

    @Composable
    operator fun invoke(
        authManager: AuthManager
    ) {
        val username = authManager.accessToken.collectAsState().value?.value

        val error = remember { mutableStateOf<String?>(null) }
        error.value?.let {
            ErrorDialog(it, onDismiss = { error.value = null })
        }

        val onChangePassword = authManager.onChangePasswordHandler(
            onError = { error.value = "Wrong password, pleas try again" }
        )

        val biometricEnabled = remember { mutableStateOf(authManager.biometricRegistered) }
        val onToggleBiometric = authManager.onToggleBiometricHandler(
            onChanged = { enabled -> biometricEnabled.value = enabled },
            onError = { error.value = "Wrong PIN code, pleas try again" }
        )

        Content(
            username = username ?: "",
            onChangePassword = onChangePassword,
            biometricEnabled = biometricEnabled.value,
            onToggleBiometric = onToggleBiometric,
            onLogout = { authManager.logout() }
        )
    }

    @Composable
    private fun AuthManager.onChangePasswordHandler(
        onError: (String) -> Unit
    ): () -> Unit {
        val oldPasswordDialog = remember { mutableStateOf(false) }
        val changePasswordDialog = remember { mutableStateOf<AuthManager.ChangePassword?>(null) }

        fun doChangePassword(password: String) {
            try {
                changePasswordDialog.value = this.changePassword(password)
            } catch (e: WrongPinCodeException) {
                onError("Wrong password, pleas try again")
            } finally {
                oldPasswordDialog.value = false
            }
        }

        if (oldPasswordDialog.value) {
            PasswordInputDialog(
                title = "Enter current password.",
                onDismissRequest = { oldPasswordDialog.value = false },
                confirm = ::doChangePassword
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

        return { oldPasswordDialog.value = true }
    }

    @Composable
    private fun AuthManager.onToggleBiometricHandler(
        onChanged: (Boolean) -> Unit,
        onError: (String) -> Unit
    ): (Boolean) -> Unit {
        val biometricEnabled = remember { mutableStateOf(biometricRegistered) }

        val enableBiometricDialog = remember { mutableStateOf(false) }
        if (enableBiometricDialog.value) {
            PasswordInputDialog(
                title = "Enter PIN code to enable biometric login.",
                onDismissRequest = { enableBiometricDialog.value = false },
                confirm = { pinCode ->
                    try {
                        enableBiometricLogin(pinCode)
                        biometricEnabled.value = true
                        onChanged(true)
                    } catch (e: WrongPinCodeException) {
                        onError("Wrong PIN code, pleas try again")
                    }
                }
            )
        }
        return { enabled ->
            if (enabled) {
                enableBiometricDialog.value = true
            } else {
                disableBiometricLogin()
                onChanged(false)
            }
        }
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
            title = { Text(text = "New password") },
            text = {
                Column {
                    OutlinedTextField(
                        value = valueField.value,
                        onValueChange = { valueField.value = it },
                        label = { Text("Password") }
                    )
                    OutlinedTextField(
                        value = confirmationField.value,
                        onValueChange = { confirmationField.value = it },
                        label = { Text("Confirmation") }
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
        biometricEnabled: Boolean,
        onToggleBiometric: (Boolean) -> Unit,
        onChangePassword: () -> Unit,
        onLogout: () -> Unit
    ) {
        Scaffold(
            topBar = { TopAppBar(username) }
        ){ padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .width(IntrinsicSize.Min),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Authentication methods:", style = MaterialTheme.typography.titleSmall)
                    AuthMethodPassword(onChangePassword)
                    AuthMethodBiometric(biometricEnabled, onToggleBiometric)

                    HorizontalDivider(Modifier.padding(vertical = 16.dp))

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


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TopAppBar(
        username: String
    ) {
        LargeTopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            title = {
                Text(
                    "Hello, $username!",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = { /* do something */ }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Localized description"
                    )
                }
            },
        )
    }

    @Composable
    fun AuthMethodPassword(
        onChange: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Password", style = MaterialTheme.typography.bodyLarge)
            TextButton(
                modifier = Modifier.heightIn(min = 32.dp),
                onClick = onChange,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Change password")
            }
        }
    }

    @Composable
    fun AuthMethodBiometric(
        enabled: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Biometric login", style = MaterialTheme.typography.bodyLarge)
            Switch(enabled, onCheckedChange = onToggle)
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
            biometricEnabled = false,
            onToggleBiometric = {},
            onLogout = {}
        )
    }
}