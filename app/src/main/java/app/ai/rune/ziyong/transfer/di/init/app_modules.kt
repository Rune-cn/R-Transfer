// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.di.init

import app.ai.rune.ziyong.transfer.di.coreModule
import app.ai.rune.ziyong.transfer.di.gatewayModule
import app.ai.rune.ziyong.transfer.di.gatewayStartupModule
import app.ai.rune.ziyong.transfer.di.initModule
import app.ai.rune.ziyong.transfer.di.networkModule
import app.ai.rune.ziyong.transfer.di.serializationModule
import app.ai.rune.ziyong.transfer.di.settingsModule
import app.ai.rune.ziyong.transfer.di.updateModule
import app.ai.rune.ziyong.transfer.di.viewModelModule

val appModules = listOf(
    // 网关内核：引擎 / 加密存储 / 账号池（必须早于 UI 模块，UI 会注入它的 ViewModel）
    gatewayModule,
    gatewayStartupModule,
    viewModelModule,
    serializationModule,
    coreModule,
    settingsModule,
    networkModule,
    updateModule,
    initModule,
)