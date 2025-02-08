package com.negusoft.localauth

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.negusoft.localauth.ui.MainNavigation
import com.negusoft.localauth.ui.theme.LocalAuthTheme
import org.koin.androidx.compose.KoinAndroidContext

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KoinAndroidContext {
                LocalAuthTheme {
                    MainNavigation()
                }
            }
        }
    }

}