package com.negusoft.localauth.ui

import android.graphics.drawable.VectorDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import com.negusoft.localauth.R
import com.negusoft.localauth.core.VaultManager
import com.negusoft.localauth.ui.theme.LocalAuthTheme

class VaultDetailsViewModel(
    private val vaultId: String,
    private val manager: VaultManager
): ViewModel() {

    val title: String get() = vaultId
//    val vaults = MutableStateFlow(manager.getVaults())
}

object VaultDetailsView {

    @Composable
    operator fun invoke(
        viewModel: VaultDetailsViewModel,
        onUp: () -> Unit
    ) {
        Content(
            title = viewModel.title,
            onUp = onUp
        )
    }

    @Composable
    fun Content(
        title: String,
        onUp: () -> Unit
    ) {
        Scaffold(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            topBar = { AppBar(title, onUp) }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                Text(text = title)
            }
        }
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
        VaultDetailsView.Content(
            title = "TITLE",
            onUp = {}
        )
    }
}