package com.negusoft.localauth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.negusoft.localauth.ui.MainNavigation
import com.negusoft.localauth.ui.vaultlist.VaultListViewModel
import com.negusoft.localauth.ui.theme.LocalAuthTheme
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.core.KoinApplication
import org.koin.dsl.KoinAppDeclaration

class MainActivity : ComponentActivity() {

    val viewModel: VaultListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
//            KoinApplication(application = koinConfig()) {
            KoinAndroidContext {
                LocalAuthTheme {
//                VaultListView(viewModel)
                    MainNavigation()
                }
            }
//            KoinApplication(::koinConfiguration) {
//            }
        }
    }



    fun koinConfig(): KoinAppDeclaration = {
        modules()
    }
    fun KoinApplication.koinConfig2() {

    }
//    fun KoinApplication.koinConfiguration()  {
//        // your configuration & modules here
////        modules(...)
//    }
}

fun KoinApplication.koinConfig3() {

}

//fun koinConfiguration() = koinApplication {
//    // your configuration & modules here
////    modules(...)
//}