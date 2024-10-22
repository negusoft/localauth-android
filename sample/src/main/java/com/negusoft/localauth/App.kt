package com.negusoft.localauth

import android.app.Application
import androidx.annotation.Keep
import com.negusoft.localauth.core.VaultManager
import com.negusoft.localauth.ui.VaultDetailsViewModel
import com.negusoft.localauth.ui.VaultListViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class App: Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin{
            androidLogger()
            androidContext(this@App)
            modules(appModule)
        }
    }

    val appModule = module {
        single { VaultManager(get()) }
        viewModel { VaultListViewModel(get()) }
        viewModel { params -> VaultDetailsViewModel(params.get(), get()) }
//        singleOf(::UserRepositoryImpl) { bind<UserRepository>() }
//        factoryOf(::UserStateHolder)
    }
}