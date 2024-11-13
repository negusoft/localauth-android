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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.negusoft.localauth.core.VaultManager
import com.negusoft.localauth.core.VaultModel
import com.negusoft.localauth.ui.theme.LocalAuthTheme
import com.negusoft.localauth.vault.LocalVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class VaultListViewModel(
    private val manager: VaultManager
): ViewModel() {

    val vaults = manager.vaults

    init {
        manager.initialize()
    }

    @OptIn(ExperimentalUuidApi::class)
    fun createVault() {
        manager.createVault(Uuid.random().toString(), "supersafepassword")
    }
//    fun getVaults() = manager.getVaults()

    fun deleteVault(vault: VaultModel) {
        manager.deleteVault(vault)
    }
}

object VaultListView {

    @Composable
    operator fun invoke(
        viewModel: VaultListViewModel,
        onSelected: (VaultModel) -> Unit
    ) {
        val vaults = viewModel.vaults.collectAsState()
        Content(
            vaults = vaults.value ?: listOf(),
            selectVault = onSelected,
            createVault = viewModel::createVault,
            deleteVault = viewModel::deleteVault
        )
    }

    @Composable
    fun Content(
        vaults: List<VaultModel>,
        selectVault: (VaultModel) -> Unit,
        createVault: () -> Unit,
        deleteVault: (VaultModel) -> Unit
    ) {
        Scaffold(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .background(MaterialTheme.colorScheme.background),
            topBar = { Toolbar() },
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
                        selectVault = selectVault,
                        deleteVault = deleteVault
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Toolbar() {
        LargeTopAppBar(title = { Text(text = "My Vaults") })
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun VaultListItem(
        vault: VaultModel,
        selectVault: (VaultModel) -> Unit,
        deleteVault: (VaultModel) -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onLongClick = { println("------- ASDF2"); deleteVault(vault) },
                    onClick = { println("------- ASDF"); selectVault(vault) }
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
            selectVault = {},
            createVault = {},
            deleteVault = {}
        )
    }
}

object SampleData {
    val vaults = listOf(
        VaultModel.vault("1234-1234-1234", "First", LocalVault.create{}),
        VaultModel.vault("2345-2345-2345", "Second", LocalVault.create{}),
        VaultModel.vault("3456-3456-3456", "Third", LocalVault.create{})
    )
}