package com.negusoft.localauth.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.negusoft.localauth.ui.details.VaultDetailsView
import com.negusoft.localauth.ui.details.VaultDetailsViewModel
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

object Screen {
    // Define first destination that doesn't take any arguments
    @Serializable
    object VaultList

    // Define second destination that takes a String parameter
    @Serializable
    data class VaultDetails(val vaultId: String)
}

object MainNavigation {

    @Composable
    operator fun invoke(
        navController: NavHostController = rememberNavController()
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.VaultList
        ) {
            composable<Screen.VaultList> {
                val viewModel = koinViewModel<VaultListViewModel>()
                VaultListView(
                    viewModel = viewModel,
                    onSelected = { navController.navigate(Screen.VaultDetails(it.id)) }
                )
            }
            composable<Screen.VaultDetails> {
                val route: Screen.VaultDetails = it.toRoute()
                val viewModel: VaultDetailsViewModel = koinViewModel { parametersOf(route.vaultId) }
                VaultDetailsView(viewModel, onUp = {navController.popBackStack() })
            }
        }
    }
}