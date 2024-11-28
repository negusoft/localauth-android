package com.negusoft.localauth.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.negusoft.localauth.ui.details.VaultDetailsView
import com.negusoft.localauth.ui.details.VaultDetailsViewModel
import com.negusoft.localauth.ui.vaultlist.VaultListView
import com.negusoft.localauth.ui.vaultlist.VaultListViewModel
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

object Screen {
    @Serializable
    object VaultList

    @Serializable
    data class VaultDetails(val vaultId: String)

    @Serializable
    object VaultCreate
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
                    onSelected = { navController.navigate(Screen.VaultDetails(it.id)) },
                    onCreate = { navController.navigate(Screen.VaultCreate) }
                )
            }
            composable<Screen.VaultDetails> {
                val route: Screen.VaultDetails = it.toRoute()
                val viewModel: VaultDetailsViewModel = koinViewModel { parametersOf(route.vaultId) }
                VaultDetailsView(viewModel, onUp = {navController.popBackStack() })
            }
            composable<Screen.VaultCreate> {
                val viewModel: VaultDetailsViewModel = koinViewModel()
                VaultDetailsView(viewModel, onUp = {navController.popBackStack() })
            }
        }
    }
}