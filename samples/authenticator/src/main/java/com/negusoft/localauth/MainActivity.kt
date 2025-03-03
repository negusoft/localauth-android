package com.negusoft.localauth

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import com.negusoft.localauth.core.AuthManager
import com.negusoft.localauth.ui.MainNavigation
import com.negusoft.localauth.ui.theme.LocalAuthTheme
import org.koin.androidx.compose.KoinAndroidContext

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val authManager = remember { AuthManager(this) }
            KoinAndroidContext {
                LocalAuthTheme {
                    MainNavigation(authManager)
                }
            }
        }
    }

}