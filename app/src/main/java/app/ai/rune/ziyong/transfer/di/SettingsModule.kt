// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.ai.rune.ziyong.transfer.data.settings.local.datastore.AppDataStore
import app.ai.rune.ziyong.transfer.data.settings.provider.ThemeStateProviderImpl
import app.ai.rune.ziyong.transfer.data.settings.repository.AppSettingsRepositoryImpl
import app.ai.rune.ziyong.transfer.domain.settings.provider.ThemeStateProvider
import app.ai.rune.ziyong.transfer.domain.settings.repository.AppSettingsRepository
import app.ai.rune.ziyong.transfer.domain.settings.usecase.settings.UpdateSettingUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val settingsModule = module {
    // DataStore
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(
            migrations = listOf(
                SharedPreferencesMigration(androidContext(), "app"),
            ),
        ) {
            androidContext().preferencesDataStoreFile("app_settings")
        }
    }

    singleOf(::AppDataStore)

    single<AppSettingsRepository> {
        AppSettingsRepositoryImpl(
            appDataStore = get(),
            appScope = get(named("AppScope")),
        )
    }

    // Providers
    single<ThemeStateProvider> {
        ThemeStateProviderImpl(
            appSettingsRepo = get<AppSettingsRepository>(),
            appScope = get(named("AppScope")),
        )
    }

    // UseCases
    factoryOf(::UpdateSettingUseCase)
}