package com.negusoft.localauth.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.negusoft.localauth.core.AuthManager
import com.negusoft.localauth.ui.account.AccountView
import com.negusoft.localauth.ui.login.LoginView
import kotlinx.serialization.Serializable

object Screen {
    @Serializable
    object LogIn

    @Serializable
    object AccountInfo
}

object MainNavigation {

    @Composable
    operator fun invoke(
        authManager: AuthManager,
        navController: NavHostController = rememberNavController()
    ) {
        val isLoggedIn = authManager.isLoggedIn.collectAsState().value
        LaunchedEffect(isLoggedIn) {
            if (!isLoggedIn) {
                navController.popBackStack(Screen.LogIn, inclusive = false)
            }
        }
        NavHost(
            navController = navController,
            startDestination = Screen.LogIn
        ) {
            composable<Screen.LogIn> {
                LoginView(authManager) {
                    navController.navigate(Screen.AccountInfo)
                }
            }
            composable<Screen.AccountInfo> {
                AccountView(authManager)
//                ButtonScreen("LocalAuthentication Screen") {
//                    navController.popBackStack()
//                }
            }
        }
    }

    @Composable
    fun ButtonScreen(text: String, onClick: () -> Unit) {
        Scaffold { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = onClick) {
                    Text(text)
                }
            }
        }
    }

}