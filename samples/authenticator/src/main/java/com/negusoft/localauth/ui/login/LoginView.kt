package com.negusoft.localauth.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.negusoft.localauth.core.AuthManager
import com.negusoft.localauth.core.InvalidRefreshTokenException
import com.negusoft.localauth.core.InvalidUsernameOrPasswordException
import com.negusoft.localauth.core.WrongPinCodeException
import com.negusoft.localauth.ui.common.PasswordInputDialog
import com.negusoft.localauth.ui.theme.LocalAuthTheme
import kotlinx.coroutines.flow.MutableStateFlow

object LoginView {

    @Composable
    operator fun invoke(
        authManager: AuthManager,
        onLogin: () -> Unit
    ) {
        val localAuthEnabled = remember { mutableStateOf(authManager.pinCodeRegistered) }
        if (localAuthEnabled.value) {
            LocalAuthView(
                authManager,
                onLogin = onLogin,
                onSignOut = { localAuthEnabled.value = false })
        } else {
            UserPasswordLoginView(authManager, onLogin)
        }
    }

    @Composable
    fun LocalAuthView(
        authManager: AuthManager,
        onLogin: () -> Unit,
        onSignOut: () -> Unit
    ) {
        val (pinCode, setPinCode) = remember { mutableStateOf("") }
        val (errorMessage, setErrorMessage) = remember { mutableStateOf<String?>(null) }

        fun loginWithPin() {
            try {
                setErrorMessage(null)
                authManager.login(pinCode)
                onLogin()
            } catch (e: WrongPinCodeException) {
                setPinCode("")
                setErrorMessage("Wrong PIN Code, ${e.remainingAttempts} remaining")
            } catch (e: InvalidRefreshTokenException) {
                setPinCode("")
                setErrorMessage("Invalid refresh token. Need to log in again :/")
            }
        }

        val activity = LocalContext.current as? FragmentActivity
        val loginWithBiometricAction: MutableState<(suspend () -> Unit)?> = remember { mutableStateOf(null) }
        LaunchedEffect(loginWithBiometricAction.value) {
            loginWithBiometricAction.value?.invoke()
        }
        fun onLoginWithBiometric() {
            loginWithBiometricAction.value = {
                try {
                    setErrorMessage(null)
                    authManager.loginWithBiometric(activity!!)
                    onLogin()
                } catch (e: InvalidRefreshTokenException) {
                    setPinCode("")
                    setErrorMessage("Invalid refresh token. Need to log in again :/")
                }
            }
        }

        LocalAuthView(
            pinCode = pinCode,
            setPinCode = setPinCode,
            errorMessage = errorMessage,
            onLoginWithPin = ::loginWithPin,
            onLoginWithBiometric = if (authManager.biometricRegistered) ::onLoginWithBiometric else null,
            onForgotPinCode = {
                authManager.signout()
                onSignOut()
            }
        )
    }

    @Composable
    fun LocalAuthView(
        pinCode: String,
        setPinCode: (String) -> Unit,
        errorMessage: String?,
        onLoginWithPin: () -> Unit,
        onLoginWithBiometric: (() -> Unit)?,
        onForgotPinCode: () -> Unit
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
                        text = "Welcome",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Enter PIN Code",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        label = { Text("PIN code") },
                        value = pinCode,
                        onValueChange = setPinCode
                    )
                    errorMessage?.let {
                        Text(
                            modifier = Modifier.padding(top = 8.dp),
                            text = it,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onLoginWithPin
                    ) {
                        Text("Log in")
                    }

                    if (onLoginWithBiometric != null) {
                        HorizontalDivider(Modifier.padding(vertical = 16.dp))

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onLoginWithBiometric
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                                Text("Log in with biometric")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onForgotPinCode
                    ) {
                        Text(text = "Forgot PIN code?")
                    }
                }
            }
        }
    }

    @Composable
    fun UserPasswordLoginView(
        authManager: AuthManager,
        onLoginSuccess: () -> Unit
    ) {
        val (username, setUsername) = remember { mutableStateOf("") }
        val (password, setPassword) = remember { mutableStateOf("") }
        val showError = remember { mutableStateOf(false) }
        val selectPinCodeRequest = remember { mutableStateOf<AuthManager.LoginResult?>(null) }
        val activateBiometric = remember { mutableStateOf<AuthManager.AuthSetup?>(null) }

        if (showError.value) {
            AlertDialog(
                onDismissRequest = { showError.value = false },
                text = { Text(text = "Login invalid username or password") },
                confirmButton = {
                    Button(
                        onClick = { showError.value = false }
                    ) {
                        Text(text = "Confirm")
                    }
                }
            )
        }

        fun onPinSelected(authSetup: AuthManager.AuthSetup) {
            // TODO check whether Biometric available
//            if ({biometric_not_available}) {
//                onLoginSuccess()
//                return
//            }
            activateBiometric.value = authSetup
        }

        selectPinCodeRequest.value?.let { loginResult ->
            PasswordInputDialog(
                title = "Select new password for quick login.",
                onDismissRequest = { selectPinCodeRequest.value = null },
                confirm = { pinCode ->
                    val setup = loginResult.startLocalAuthenticationSetup()
                    setup.registerPinLock(pinCode)
                    selectPinCodeRequest.value = null
                    onPinSelected(setup)
                }
            )
        }

        activateBiometric.value?.let { loginResult ->
            ActivateBiometricDialog(
                onDismiss = {
                    activateBiometric.value = null
                    onLoginSuccess()
                },
                confirm = {
                    loginResult.enableBiometricLogin()
                }
            )
        }

        fun onLoginClick() {
            try {
                val result = authManager.login(username, password)
                selectPinCodeRequest.value = result
            } catch (e: InvalidUsernameOrPasswordException) {
                showError.value = true
            }
        }

        UserPasswordLoginView(
            username = username,
            setUsername = setUsername,
            password = password,
            setPassword = setPassword,
            onLoginClick = ::onLoginClick
        )
    }

    @Composable
    fun UserPasswordLoginView(
        username: String,
        setUsername: (String) -> Unit,
        password: String,
        setPassword: (String) -> Unit,
        onLoginClick: () -> Unit = {}
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
                        text = "Welcome",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Enter PIN Code",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        label = { Text("Username") },
                        value = username,
                        onValueChange = setUsername
                    )
                    OutlinedTextField(
                        label = { Text("Password") },
                        value = password,
                        onValueChange = setPassword,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onLoginClick
                    ) {
                        Text("Log in")
                    }
                }
            }
        }
    }

//    @Composable
//    private fun SelectPasswordDialog(
//        onDismissRequest: () -> Unit,
//        confirm: (value: String) -> Unit
//    ) {
//        val valueField = remember { mutableStateOf("") }
//        AlertDialog(
//            onDismissRequest = onDismissRequest,
//            title = { Text(text = "Select new password for quick login.") },
//            text = {
//                Column {
//                    OutlinedTextField(
//                        value = valueField.value,
//                        onValueChange = { valueField.value = it },
//                        label = { Text("Password") }
//                    )
//                }
//            },
//            confirmButton = {
//                TextButton(
//                    onClick = {
//                        confirm(valueField.value)
//                        onDismissRequest()
//                    },
//                    content = { Text("Confirm") }
//                )
//            },
//            dismissButton = {
//                TextButton(onClick = onDismissRequest) { Text("Dismiss") }
//            }
//        )
//    }

    @Composable
    private fun ActivateBiometricDialog(
        onDismiss: () -> Unit,
        confirm: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "Activate biometric login") },
            text = { Text(text = "Activate biometric login") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm()
                        onDismiss()
                    },
                    content = { Text("Activate") }
                )
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        )
    }

}

@Composable
@Preview
fun LoginViewPreview() {
    LocalAuthTheme {
        LoginView.LocalAuthView(
            pinCode = "",
            setPinCode = {},
            errorMessage = "Error",
            onLoginWithPin = {},
            onLoginWithBiometric = {},
            onForgotPinCode = {}
        )
    }
}