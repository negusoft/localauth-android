package com.negusoft.localauth

import android.app.Application
import com.negusoft.localauth.core.VaultManager
import com.negusoft.localauth.ui.details.VaultDetailsViewModel
import com.negusoft.localauth.ui.vaultlist.VaultListViewModel
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
        viewModel { params -> VaultDetailsViewModel(params.getOrNull(), get()) }
//        singleOf(::UserRepositoryImpl) { bind<UserRepository>() }
//        factoryOf(::UserStateHolder)
    }
}