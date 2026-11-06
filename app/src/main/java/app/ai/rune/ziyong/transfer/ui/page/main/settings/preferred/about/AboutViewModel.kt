// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.about

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ai.rune.ziyong.transfer.R
import app.ai.rune.ziyong.transfer.domain.settings.repository.AppSettingsRepository
import app.ai.rune.ziyong.transfer.domain.settings.repository.BooleanSetting
import app.ai.rune.ziyong.transfer.domain.settings.usecase.settings.UpdateSettingUseCase
import app.ai.rune.ziyong.transfer.domain.updater.repository.UpdateRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class AboutViewModel(
    private val context: Context,
    appSettingsRepo: AppSettingsRepository,
    private val updateRepo: UpdateRepository,
    private val updateSetting: UpdateSettingUseCase,
) : ViewModel() {

    private val _uiEvents = MutableSharedFlow<AboutEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val uiEvents = _uiEvents.asSharedFlow()

    private val _appIcon = MutableStateFlow<ImageBitmap?>(null)
    private val _latestCrashSummary = MutableStateFlow<String?>(null)

    val state: StateFlow<AboutState> = combine(
        appSettingsRepo.preferencesFlow,
        updateRepo.updateInfoFlow,
        _appIcon,
        _latestCrashSummary,
    ) { prefs, updateInfo, appIcon, crashSummary ->
        AboutState(
            hasUpdate = prefs.allowInternetAccess && (updateInfo?.hasUpdate ?: false),
            remoteVersion = if (prefs.allowInternetAccess) updateInfo?.remoteVersion.orEmpty() else "",
            autoCheckUpdateOnStart = prefs.autoCheckUpdateOnStart,
            appIcon = appIcon,
            updateInfo = updateInfo,
            latestCrashSummary = crashSummary,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AboutState(),
    )

    init {
        loadAppIcon()
        loadLatestCrash()
    }

    fun dispatch(action: AboutAction) {
        when (action) {
            is AboutAction.CheckUpdate -> checkUpdate(force = true)
            is AboutAction.SetAutoCheckUpdateOnStart -> viewModelScope.launch {
                updateSetting(BooleanSetting.AutoCheckUpdateOnStart, action.enabled)
            }
        }
    }

    private fun checkUpdate(force: Boolean = false) = viewModelScope.launch {
        Timber.i("About update check (force=%s)", force)
        _uiEvents.emit(AboutEvent.ShowUpdateLoading)
        val info = runCatching { updateRepo.checkUpdate(force) }
            .onFailure { e -> Timber.e(e, "Update check failed") }
            .getOrNull()
        _uiEvents.emit(AboutEvent.HideUpdateLoading)
        if (info?.hasUpdate == true) {
            Timber.i("Update found: %s", info.remoteVersion)
            _uiEvents.emit(AboutEvent.ShowUpdateSheet)
        } else {
            Timber.i("No update available")
            _uiEvents.emit(AboutEvent.ShowMessage(R.string.check_update_latest_shell))
        }
    }

    private fun loadAppIcon() = viewModelScope.launch(Dispatchers.IO) {
        _appIcon.value = runCatching {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val bitmap: Bitmap = drawable.toBitmap(512, 512)
            bitmap.asImageBitmap()
        }.getOrNull()
    }

    private fun loadLatestCrash() = viewModelScope.launch(Dispatchers.IO) {
        val dir = File(context.cacheDir, "crashes")
        val latest = dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.maxByOrNull { it.lastModified() }
        _latestCrashSummary.value = latest?.takeIf { it.exists() }
            ?.useLines { it.firstOrNull() }
    }
}