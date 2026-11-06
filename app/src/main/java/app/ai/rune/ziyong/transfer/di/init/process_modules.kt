// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.di.init

import app.ai.rune.ziyong.transfer.di.coreModule
import app.ai.rune.ziyong.transfer.di.serializationModule

val processModules = listOf(
    serializationModule,
    coreModule,
)
