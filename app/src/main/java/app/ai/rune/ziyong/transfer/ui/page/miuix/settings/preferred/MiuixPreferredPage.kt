// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.preferred

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.R
import app.ai.rune.ziyong.transfer.core.device.model.Level
import app.ai.rune.ziyong.transfer.core.env.AppConfig
import app.ai.rune.ziyong.transfer.core.i18n.LocaleManager
import app.ai.rune.ziyong.transfer.ui.icons.AppIcons
import app.ai.rune.ziyong.transfer.ui.navigation.LocalNavigator
import app.ai.rune.ziyong.transfer.ui.navigation.Route
import app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.PreferredViewEvent
import app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.PreferredViewModel
import app.ai.rune.ziyong.transfer.ui.page.miuix.widgets.MiuixBlockingLoadingDialog
import app.ai.rune.ziyong.transfer.ui.page.miuix.widgets.MiuixNavigationItemWidget
import app.ai.rune.ziyong.transfer.ui.theme.SystemBarAppearance
import app.ai.rune.ziyong.transfer.ui.theme.applyWindowAppearance
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun MiuixPreferredPage(
    enableBlur: Boolean,
    viewModel: PreferredViewModel = koinViewModel(),
    title: String,
    outerPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    val revLevel = when (AppConfig.LEVEL) {
        Level.STABLE -> stringResource(id = R.string.stable)
        Level.PREVIEW -> stringResource(id = R.string.preview)
        Level.UNSTABLE -> stringResource(id = R.string.unstable)
    }
    val scrollBehavior = MiuixScrollBehavior()

    var updateMessage by remember { mutableStateOf<String?>(null) }
    var showUpdateLoading by remember { mutableStateOf(false) }
    var showUpdateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Timber.i("Settings page shown")
        viewModel.uiEvents.collect { event ->
            when (event) {
                is PreferredViewEvent.ShowMessage -> {
                    updateMessage = context.getString(event.messageResId)
                }

                PreferredViewEvent.ShowUpdateLoading -> {
                    showUpdateLoading = true
                }

                PreferredViewEvent.HideUpdateLoading -> {
                    showUpdateLoading = false
                }

                PreferredViewEvent.ShowUpdateSheet -> {
                    showUpdateSheet = true
                }
            }
        }
    }

    // Capture layout direction and horizontal safe insets for display cutouts in landscape
    val layoutDirection = LocalLayoutDirection.current

    val topBarBackdrop = rememberMiuixBlurBackdrop(enableBlur)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(topBarBackdrop),
                color = topBarBackdrop.getMiuixAppBarColor(),
                title = title,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(topBarBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                // Safely add horizontal paddings to avoid notches in landscape
                start = innerPadding.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = outerPadding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            item { Spacer(modifier = Modifier.size(12.dp)) }
            item { SmallTitle(stringResource(R.string.personalization)) }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    WindowDropdownPreference(
                        items = listOf(
                            stringResource(R.string.language_follow_system),
                            stringResource(R.string.language_simplified_chinese),
                            stringResource(R.string.language_english),
                        ),
                        selectedIndex = LocaleManager.indexOf(context),
                        title = stringResource(R.string.settings_language),
                        summary = stringResource(R.string.settings_language_desc),
                        onSelectedIndexChange = { index ->
                            val tag = LocaleManager.supportedTags[index]
                            if (tag != LocaleManager.currentTag(context)) {
                                LocaleManager.setTag(context, tag)
                                Timber.i("Language switch requested: %s", tag)
                                val activity = context.findActivity()
                                // recreate 前把“即将被销毁的窗口”也钉住，避免退场闪白
                                if (activity is ComponentActivity) {
                                    activity.applyWindowAppearance(
                                        isDark = SystemBarAppearance.isDark(activity),
                                        surfaceArgb = SystemBarAppearance.surfaceArgb(activity),
                                    )
                                }
                                activity?.recreate()
                            }
                        },
                    )
                    MiuixNavigationItemWidget(
                        icon = AppIcons.Theme,
                        title = stringResource(R.string.theme_settings),
                        description = stringResource(R.string.theme_settings_desc),
                        onClick = {
                            navigator.push(Route.Theme)
                        },
                    )
                }
            }
            item { SmallTitle(stringResource(R.string.other)) }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.about_detail),
                        summary = if (uiState.hasUpdate) {
                            stringResource(
                                R.string.update_available,
                                uiState.remoteVersion,
                            )
                        } else {
                            "$revLevel ${AppConfig.VERSION_NAME}"
                        },
                        summaryColor = BasicComponentColors(
                            color = if (uiState.hasUpdate) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            disabledColor = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                        ),
                        onClick = { navigator.push(Route.About) },
                    )
                }
            }
        }
    }

    // Update check result shown with miuix WindowDialog.
    WindowDialog(
        show = updateMessage != null,
        title = stringResource(R.string.get_update),
        summary = updateMessage.orEmpty(),
        onDismissRequest = { updateMessage = null },
    ) {
        TextButton(
            text = stringResource(R.string.cancel),
            onClick = { updateMessage = null },
        )
    }

    // Update result sheet: notes rendered with MiuixMarkdownText; download/open
    // actions land on the remote URL in the system browser.
    MiuixUpdateSheet(
        show = showUpdateSheet,
        updateInfo = uiState.updateInfo,
        channelLabel = revLevel,
        releaseNotes = uiState.updateInfo?.updateLog,
        onDismissRequest = { showUpdateSheet = false },
        onOpenReleasePage = { uriHandler.openUri(it) },
        onDownload = { info ->
            val url = info.downloadUrl.ifBlank { info.releaseUrl }
            if (url.isNotBlank()) uriHandler.openUri(url)
        },
    )

    MiuixBlockingLoadingDialog(
        visible = showUpdateLoading,
        text = stringResource(R.string.checking_update),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}