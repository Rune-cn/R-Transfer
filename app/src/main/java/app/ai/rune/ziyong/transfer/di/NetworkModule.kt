// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.di

import app.ai.rune.ziyong.transfer.data.updater.repository.OnlineUpdateRepositoryImpl
import app.ai.rune.ziyong.transfer.domain.updater.repository.UpdateRepository
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private const val CONNECTION_TIMEOUT = 15L

val networkModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS) // Connection Timeout
            .readTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS) // Read Timeout
            .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS) // Write Timeout
            .followRedirects(true) // Allow Redirect
            .followSslRedirects(true) // Allow SSL Redirect
            .connectionSpecs(
                listOf(
                    ConnectionSpec.MODERN_TLS,
                ),
            )
            .build()
    }
}

val updateModule = module {
    singleOf(::OnlineUpdateRepositoryImpl) { bind<UpdateRepository>() }
}