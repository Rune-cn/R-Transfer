// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.di

import app.ai.rune.ziyong.transfer.core.reflection.ReflectionProvider
import app.ai.rune.ziyong.transfer.core.reflection.ReflectionProviderImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val coreModule = module {
    // Provide a global coroutine scope for application-level background tasks
    single<CoroutineScope>(named("AppScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    singleOf(::ReflectionProviderImpl) { bind<ReflectionProvider>() }
}
