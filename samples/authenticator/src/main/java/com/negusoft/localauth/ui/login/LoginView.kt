package com.negusoft.localauth.ui.login

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.negusoft.localauth.core.AuthManager
import com.negusoft.localauth.core.AuthResult
import com.negusoft.localauth.core.InvalidUsernameOrPasswordException
import com.negusoft.localauth.core.MockAuthenticator
import com.negusoft.localauth.core.WrongPinCodeException
import com.negusoft.localauth.ui.theme.LocalAuthTheme

class LoginViewModel {

}

object LoginView {

    @Composable
    operator fun invoke() {
        val authManager = remember { AuthManager() }

        val isLoggedIn = authManager.isLoggedIn.collectAsState().value
        if (isLoggedIn) {
            UserInfoView(authManager)
        } else if (authManager.pinCodeRegistered) {
            LocalAuthView(authManager)
        } else {
            UserPasswordLoginView(authManager)
        }
    }

    @Composable
    fun UserInfoView(
        authManager: AuthManager
    ) {
        val username = authManager.accessToken.collectAsState().value!!
        UserInfoView(
            username = username.value,
            onLogout = authManager::logout
        )
    }

    @Composable
    fun UserInfoView(
        username: String,
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
                        style = MaterialTheme.typography.titleMedium
                    )
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

    @Composable
    fun LocalAuthView(
        authManager: AuthManager
    ) {
        val (pinCode, setPinCode) = remember { mutableStateOf("") }
        val (errorMessage, setErrorMessage) = remember { mutableStateOf<String?>(null) }

        fun login() {
            try {
                setErrorMessage(null)
                authManager.login(pinCode)
            } catch (e: WrongPinCodeException) {
                setPinCode("")
                setErrorMessage("Wrong PIN Code, ${e.remainingAttempts} remaining")
            }
        }
        LocalAuthView(
            pinCode = pinCode,
            setPinCode = setPinCode,
            errorMessage = errorMessage,
            onLogin = ::login,
            onForgotPinCode = { authManager.logout() }
        )
    }

    @Composable
    fun LocalAuthView(
        pinCode: String,
        setPinCode: (String) -> Unit,
        errorMessage: String?,
        onLogin: () -> Unit,
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
                        onClick = onLogin
                    ) {
                        Text("Log in")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
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
        authManager: AuthManager
    ) {
        val (username, setUsername) = remember { mutableStateOf("") }
        val (password, setPassword) = remember { mutableStateOf("") }
        val showError = remember { mutableStateOf(false) }

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

        fun onLogin() {
            try {
                val result = authManager.login(username, password)
                result.registerPinCode("11111")
            } catch (e: InvalidUsernameOrPasswordException) {
                showError.value = true
            }
        }

        UserPasswordLoginView(
            username = username,
            setUsername = setUsername,
            password = password,
            setPassword = setPassword,
            onLoginClick = ::onLogin
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

}

@Composable
@Preview
fun LoginViewPreview() {
    LocalAuthTheme {
//        LoginView.UserPasswordLoginView(
//            username = "username",
//            setUsername = {},
//            password = "password",
//            setPassword = {}
//        )

//        LoginView.UserInfoView(
//            username = "username",
//            onLogout = {}
//        )

        LoginView.LocalAuthView(
            pinCode = "",
            setPinCode = {},
            errorMessage = "Error",
            onLogin = {},
            onForgotPinCode = {}
        )
    }
}