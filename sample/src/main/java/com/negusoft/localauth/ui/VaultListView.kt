package com.negusoft.localauth.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.negusoft.localauth.core.VaultManager
import com.negusoft.localauth.core.VaultModel
import com.negusoft.localauth.ui.theme.LocalAuthTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@HiltViewModel
class VaultListViewModel @Inject constructor(
    private val manager: VaultManager
): ViewModel() {

    val vaults = MutableStateFlow(manager.getVaults())

    @OptIn(ExperimentalUuidApi::class)
    fun createVault() {
        manager.createVault(Uuid.random().toString(), "supersafepassword")
        vaults.value = manager.getVaults()
    }
//    fun getVaults() = manager.getVaults()

    fun deleteVault(vault: VaultModel) {
        manager.deleteVault(vault)
        vaults.value = manager.getVaults()
    }
}

object VaultListView {

    @Composable
    operator fun invoke(
        viewModel: VaultListViewModel
    ) {
        val vaults = viewModel.vaults.collectAsState()
        Content(
            vaults = vaults.value,
            createVault = viewModel::createVault,
            deleteVault = viewModel::deleteVault
        )
    }

    @Composable
    fun Content(
        vaults: List<VaultModel>,
        createVault: () -> Unit,
        deleteVault: (VaultModel) -> Unit
    ) {
        Scaffold(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            floatingActionButton = {
                FloatingActionButton(onClick = createVault) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Create vault")
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vaults) { vault ->
                    VaultListItem(
                        vault = vault,
                        deleteVault = deleteVault
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun VaultListItem(
        vault: VaultModel,
        deleteVault: (VaultModel) -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onLongClick = { println("------- ASDF2"); deleteVault(vault) },
                    onClick = { println("------- ASDF");  }
                ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = vault.name, style = MaterialTheme.typography.headlineSmall)
                Text(text = vault.id, style = MaterialTheme.typography.titleSmall)
            }
        }
    }

}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    LocalAuthTheme {
        VaultListView.Content(
            vaults = SampleData.vaults,
            createVault = {},
            deleteVault = {}
        )
    }
}

object SampleData {
    val vaults = listOf(
        VaultModel.vault("1234-1234-1234", "First"),
        VaultModel.vault("2345-2345-2345", "Second"),
        VaultModel.vault("3456-3456-3456", "Third")
    )
}