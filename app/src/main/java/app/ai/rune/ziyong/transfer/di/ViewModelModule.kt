// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.di

import app.ai.rune.ziyong.transfer.ui.page.main.settings.SettingsSharedViewModel
import app.ai.rune.ziyong.transfer.ui.page.main.settings.home.HomePageViewModel
import app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.PreferredViewModel
import app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.about.AboutViewModel
import app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.theme.ThemeSettingsViewModel
import app.ai.rune.ziyong.transfer.ui.page.main.settings.log.LogViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::SettingsSharedViewModel)
    viewModelOf(::HomePageViewModel)
    viewModelOf(::PreferredViewModel)
    viewModelOf(::ThemeSettingsViewModel)
    viewModelOf(::AboutViewModel)
    viewModelOf(::LogViewModel)
}