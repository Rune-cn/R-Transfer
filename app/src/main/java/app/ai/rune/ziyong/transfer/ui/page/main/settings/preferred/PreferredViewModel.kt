// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ai.rune.ziyong.transfer.R
import app.ai.rune.ziyong.transfer.domain.settings.repository.AppSettingsRepository
import app.ai.rune.ziyong.transfer.domain.settings.repository.BooleanSetting
import app.ai.rune.ziyong.transfer.domain.settings.usecase.settings.UpdateSettingUseCase
import app.ai.rune.ziyong.transfer.domain.updater.repository.UpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class PreferredViewModel(
    appSettingsRepo: AppSettingsRepository,
    private val updateRepo: UpdateRepository,
    private val updateSetting: UpdateSettingUseCase,
) : ViewModel() {

    private val _uiEvents = MutableSharedFlow<PreferredViewEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val uiEvents = _uiEvents.asSharedFlow()

    val state: StateFlow<PreferredViewState> = combine(
        appSettingsRepo.preferencesFlow,
        updateRepo.updateInfoFlow,
    ) { prefs, updateInfo ->
        PreferredViewState(
            allowInternetAccess = prefs.allowInternetAccess,
            hasUpdate = prefs.allowInternetAccess && (updateInfo?.hasUpdate ?: false),
            remoteVersion = if (prefs.allowInternetAccess) updateInfo?.remoteVersion.orEmpty() else "",
            autoCheckUpdateOnStart = prefs.autoCheckUpdateOnStart,
            useBlur = prefs.useBlur,
            updateInfo = updateInfo,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PreferredViewState(),
    )

    fun dispatch(action: PreferredViewAction) {
        when (action) {
            is PreferredViewAction.ChangeInternetAccess -> viewModelScope.launch {
                updateSetting(BooleanSetting.AllowInternetAccess, action.enabled)
            }

            PreferredViewAction.CheckUpdate -> checkUpdate(force = true)

            is PreferredViewAction.SetAutoCheckUpdateOnStart -> viewModelScope.launch {
                updateSetting(BooleanSetting.AutoCheckUpdateOnStart, action.enabled)
            }

            is PreferredViewAction.SetUseBlur -> viewModelScope.launch {
                updateSetting(BooleanSetting.UiUseBlur, action.enabled)
            }
        }
    }

    private fun checkUpdate(force: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        // legado-style 4-stage: loading -> result -> sheet / toast (+ onFinally)
        Timber.i("Update check requested (force=%s)", force)
        _uiEvents.emit(PreferredViewEvent.ShowUpdateLoading)
        val info = runCatching { updateRepo.checkUpdate(force) }
            .onFailure { e -> Timber.e(e, "Update check failed") }
            .getOrNull()
        _uiEvents.emit(PreferredViewEvent.HideUpdateLoading)
        if (info?.hasUpdate == true) {
            Timber.i("Update found: %s", info.remoteVersion)
            _uiEvents.emit(PreferredViewEvent.ShowUpdateSheet)
        } else {
            Timber.i("No update available")
            _uiEvents.emit(PreferredViewEvent.ShowMessage(R.string.check_update_latest_shell))
        }
    }
}