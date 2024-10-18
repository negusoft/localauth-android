package com.negusoft.localauth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.negusoft.localauth.ui.VaultListView
import com.negusoft.localauth.ui.VaultListViewModel
import com.negusoft.localauth.ui.theme.LocalAuthTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    val viewModel: VaultListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalAuthTheme {
                VaultListView(viewModel)
            }
        }
    }
}