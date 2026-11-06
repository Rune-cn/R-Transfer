// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.di

import app.ai.rune.ziyong.transfer.util.cache.AutoCacheSweeper
import app.ai.rune.ziyong.transfer.util.timber.LogController
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val initModule = module {
    singleOf(::LogController) { createdAtStart() }
    singleOf(::AutoCacheSweeper) { createdAtStart() }
}
