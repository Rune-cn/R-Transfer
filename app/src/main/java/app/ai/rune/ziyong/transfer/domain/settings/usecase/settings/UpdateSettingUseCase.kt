// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.domain.settings.usecase.settings

import app.ai.rune.ziyong.transfer.domain.settings.repository.AppSettingsRepository
import app.ai.rune.ziyong.transfer.domain.settings.repository.BooleanSetting
import app.ai.rune.ziyong.transfer.domain.settings.repository.IntSetting
import app.ai.rune.ziyong.transfer.domain.settings.repository.StringSetting

class UpdateSettingUseCase(private val appSettingsRepo: AppSettingsRepository) {
    suspend operator fun invoke(setting: BooleanSetting, value: Boolean) {
        appSettingsRepo.putBoolean(setting, value)
    }

    suspend operator fun invoke(setting: StringSetting, value: String) {
        appSettingsRepo.putString(setting, value)
    }

    suspend operator fun invoke(setting: IntSetting, value: Int) {
        appSettingsRepo.putInt(setting, value)
    }
}
