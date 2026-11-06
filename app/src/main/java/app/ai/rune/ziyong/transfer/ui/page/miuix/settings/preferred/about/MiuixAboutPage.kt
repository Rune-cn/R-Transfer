// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalScrollBarApi::class)

package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.preferred.about
import app.ai.rune.ziyong.transfer.ui.page.miuix.widgets.MiuixBlockingLoadingDialog
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.preferred.MiuixUpdateSheet

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.BuildConfig
import app.ai.rune.ziyong.transfer.R
import app.ai.rune.ziyong.transfer.core.device.model.Level
import app.ai.rune.ziyong.transfer.core.env.AppConfig
import app.ai.rune.ziyong.transfer.ui.library.blend.ColorBlendToken
import app.ai.rune.ziyong.transfer.ui.library.effect.BgEffectBackground
import app.ai.rune.ziyong.transfer.ui.navigation.LocalNavigator
import app.ai.rune.ziyong.transfer.ui.navigation.Route
import app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.about.AboutAction
import app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.about.AboutEvent
import app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.about.AboutState
import app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.about.AboutViewModel
import app.ai.rune.ziyong.transfer.ui.page.miuix.widgets.ErrorDisplaySheet
import app.ai.rune.ziyong.transfer.ui.page.miuix.widgets.MiuixBackButton
import app.ai.rune.ziyong.transfer.ui.page.miuix.widgets.MiuixNavigationItemWidget
import app.ai.rune.ziyong.transfer.ui.page.miuix.widgets.MiuixSwitchWidget
import app.ai.rune.ziyong.transfer.ui.theme.AppTheme
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.window.WindowListPopup

// Unified entry point
@Composable
fun MiuixAboutPage(
    useBlur: Boolean,
    viewModel: AboutViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val showLoadingDialog = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showUpdateSheet by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        Timber.i("About page shown")
        viewModel.uiEvents.collect { event ->
            when (event) {
                is AboutEvent.ShowUpdateLoading -> showLoadingDialog.value = true

                is AboutEvent.HideUpdateLoading -> showLoadingDialog.value = false

                AboutEvent.ShowUpdateSheet -> {
                    showUpdateSheet = true
                }

                is AboutEvent.ShowMessage -> {
                    updateMessage = context.getString(event.messageResId)
                }

                else -> {}
            }
        }
    }

    MiuixAboutPageInternal(
        useBlur = useBlur,
        uiState = uiState,
        viewModel = viewModel,
    )

    // Update sheet: notes rendered with MiuixMarkdownText; download/open
    // actions land on the remote URL in the system browser.
    MiuixUpdateSheet(
        show = showUpdateSheet,
        updateInfo = uiState.updateInfo,
        channelLabel = when (AppConfig.LEVEL) {
            Level.STABLE -> stringResource(R.string.stable)
            Level.PREVIEW -> stringResource(R.string.preview)
            Level.UNSTABLE -> stringResource(R.string.unstable)
        },
        releaseNotes = uiState.updateInfo?.updateLog,
        onDismissRequest = { showUpdateSheet = false },
        onOpenReleasePage = { uriHandler.openUri(it) },
        onDownload = { info ->
            val url = info.downloadUrl.ifBlank { info.releaseUrl }
            if (url.isNotBlank()) uriHandler.openUri(url)
        },
    )

    MiuixBlockingLoadingDialog(
        visible = showLoadingDialog.value,
        text = stringResource(R.string.checking_update),
    )

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
}

@Composable
private fun MiuixAboutPageInternal(
    useBlur: Boolean,
    uiState: AboutState,
    viewModel: AboutViewModel,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val level = when (AppConfig.LEVEL) {
        Level.STABLE -> stringResource(id = R.string.stable)
        Level.PREVIEW -> stringResource(id = R.string.preview)
        Level.UNSTABLE -> stringResource(id = R.string.unstable)
    }

    val versionInfoText = stringResource(
        id = R.string.app_version_info_format,
        level,
        AppConfig.VERSION_NAME,
        AppConfig.VERSION_CODE,
    )
    val blurSupported = useBlur && isRuntimeShaderSupported()
    val topBarBackdrop = rememberMiuixBlurBackdrop(blurSupported)

    // Scroll state and progress calculations for animations
    val lazyListState = rememberLazyListState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var logoHeightPx by remember { mutableIntStateOf(0) }

    val scrollProgress by remember {
        derivedStateOf {
            if (logoHeightPx <= 0) {
                0f
            } else {
                val index = lazyListState.firstVisibleItemIndex
                val offset = lazyListState.firstVisibleItemScrollOffset
                if (index > 0) 1f else (offset.toFloat() / logoHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                modifier = Modifier.miuixBlurEffect(
                    backdrop = topBarBackdrop,
                    enabled = scrollProgress == 1f,
                ),
                title = stringResource(id = R.string.about),
                scrollBehavior = topAppBarScrollBehavior,
                color = if (topBarBackdrop != null && scrollProgress == 1f) {
                    Color.Transparent
                } else {
                    MiuixTheme.colorScheme.surface.copy(alpha = if (scrollProgress == 1f) 1f else 0f)
                },
                titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = scrollProgress),
                defaultWindowInsetsPadding = false,
                navigationIcon = { MiuixBackButton(onClick = { navigator.pop() }) },
            )
        },
    ) { innerPadding ->
        Box(modifier = topBarBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier) {
            AboutContentBody(
                padding = innerPadding,
                uiState = uiState,
                versionInfoText = versionInfoText,
                lazyListState = lazyListState,
                scrollProgressProvider = { scrollProgress },
                useBlur = blurSupported,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                onLogoHeightChanged = { logoHeightPx = it },
                onCheckUpdate = { viewModel.dispatch(AboutAction.CheckUpdate) },
                onSetAutoCheckUpdateOnStart = { viewModel.dispatch(AboutAction.SetAutoCheckUpdateOnStart(it)) },
                onDirectUpdate = {
                    val url = uiState.updateInfo?.releaseUrl.orEmpty()
                    if (url.isNotBlank()) uriHandler.openUri(url)
                },
                uriHandler = uriHandler,
                context = context,
            )
        }
    }
}

@Composable
private fun AboutContentBody(
    padding: PaddingValues,
    uiState: AboutState,
    versionInfoText: String,
    lazyListState: LazyListState,
    scrollProgressProvider: () -> Float,
    useBlur: Boolean,
    topAppBarScrollBehavior: ScrollBehavior,
    onLogoHeightChanged: (Int) -> Unit,
    onCheckUpdate: () -> Unit,
    onSetAutoCheckUpdateOnStart: (Boolean) -> Unit,
    onDirectUpdate: () -> Unit,
    uriHandler: UriHandler,
    context: Context,
) {
    val layoutDirection = LocalLayoutDirection.current
    val isDark = AppTheme.isDark

    // Shell mode: dev texture-set sheet removed.
    var blurEnable by remember(useBlur) { mutableStateOf(useBlur) }
    val dynamicBackground = remember { mutableStateOf(isRuntimeShaderSupported()) }
    val effectBackground = remember { mutableStateOf(isRuntimeShaderSupported()) }
    var isOs3Effect by remember { mutableStateOf(true) }

    var blurRadius by remember { mutableFloatStateOf(60f) }
    var noiseCoefficient by remember { mutableFloatStateOf(BlurDefaults.NoiseCoefficient) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }

    val backdrop = rememberMiuixBlurBackdrop(useBlur)
    val surface = MiuixTheme.colorScheme.surface.copy(alpha = 0.6f)

    // Blend configurations map
    val blendConfigs = remember(isDark) {
        mapOf(
            "Default" to if (isDark) ColorBlendToken.Overlay_Extra_Thin_Dark else ColorBlendToken.Pured_Regular_Light,
            "None" to emptyList(),
            "SrcOver" to listOf(BlendColorEntry(surface, BlurBlendMode.SrcOver)),
            "Screen" to listOf(BlendColorEntry(surface, BlurBlendMode.Screen)),
            "Multiply" to listOf(BlendColorEntry(surface, BlurBlendMode.Multiply)),
            "Overlay" to listOf(BlendColorEntry(surface, BlurBlendMode.Overlay)),
            "Soft Light" to listOf(BlendColorEntry(surface, BlurBlendMode.SoftLight)),
            "Linear Light" to listOf(BlendColorEntry(surface, BlurBlendMode.LinearLight)),
            "Linear Light Grey" to listOf(BlendColorEntry(surface, BlurBlendMode.LinearLightWithGreyscale)),
            "Linear Light Lab" to listOf(BlendColorEntry(surface, BlurBlendMode.LinearLightLab)),
            "Lab Lighten" to listOf(BlendColorEntry(surface, BlurBlendMode.LabLightenWithGreyscale)),
            "Lab Darken" to listOf(BlendColorEntry(surface, BlurBlendMode.LabDarkenWithGreyscale)),
            "MI Difference" to listOf(BlendColorEntry(surface, BlurBlendMode.MiDifference)),
            "MI Color Dodge" to listOf(BlendColorEntry(surface, BlurBlendMode.MiColorDodge)),
            "MI Color Burn" to listOf(BlendColorEntry(surface, BlurBlendMode.MiColorBurn)),
            "Plus Lighter" to listOf(BlendColorEntry(surface, BlurBlendMode.PlusLighter)),
            "Plus Darker" to listOf(BlendColorEntry(surface, BlurBlendMode.PlusDarker)),
        )
    }

    val configEntries = blendConfigs.entries.toList()
    var blendModeIndex by remember { mutableIntStateOf(0) }
    val currentConfigValue = configEntries.getOrNull(blendModeIndex)?.value ?: emptyList()

    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }

    // Layout state
    val density = LocalDensity.current
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    val displayCutoutInsets = WindowInsets.displayCutout.asPaddingValues()
    val horizontalSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()

    val listContentPadding = PaddingValues(
        start = horizontalSafeInsets.calculateStartPadding(layoutDirection) + displayCutoutInsets.calculateLeftPadding(layoutDirection),
        top = padding.calculateTopPadding(),
        end = horizontalSafeInsets.calculateEndPadding(layoutDirection) + displayCutoutInsets.calculateRightPadding(layoutDirection),
        bottom = padding.calculateBottomPadding(),
    )

    val logoPadding = PaddingValues(
        top = padding.calculateTopPadding() + 40.dp,
        start = horizontalSafeInsets.calculateStartPadding(layoutDirection) + displayCutoutInsets.calculateLeftPadding(layoutDirection),
        end = horizontalSafeInsets.calculateEndPadding(layoutDirection) + displayCutoutInsets.calculateRightPadding(layoutDirection),
    )

    BgEffectBackground(
        dynamicBackground = dynamicBackground.value,
        isOs3Effect = isOs3Effect,
        modifier = Modifier.fillMaxSize(),
        bgModifier = backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier,
        effectBackground = effectBackground.value,
        alpha = { 1f - scrollProgressProvider() },
    ) {
        // Sticky animated header section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 52.dp,
                    start = logoPadding.calculateStartPadding(layoutDirection),
                    end = logoPadding.calculateEndPadding(layoutDirection),
                )
                .onSizeChanged { size ->
                    with(density) { logoHeightDp = size.height.toDp() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        val iconProgress = ((scrollProgressProvider() - 0.35f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1 - iconProgress
                        scaleX = 1 - (iconProgress * 0.05f)
                        scaleY = 1 - (iconProgress * 0.05f)
                    },
            ) {
                if (backdrop != null) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_monochrome),
                        modifier = Modifier
                            .requiredSize(160.dp)
                            .textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = 200f,
                                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                colors = BlurColors(blendColors = logoBlend),
                                contentBlendMode = BlendMode.DstIn,
                                enabled = blurEnable,
                            ),
                        contentDescription = null,
                    )
                } else if (uiState.appIcon != null) {
                    Image(
                        bitmap = uiState.appIcon,
                        modifier = Modifier.size(80.dp),
                        contentDescription = stringResource(id = R.string.app_name),
                    )
                }
            }

            Text(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp)
                    .graphicsLayer {
                        val projectNameProgress = ((scrollProgressProvider() - 0.20f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1 - projectNameProgress
                        scaleX = 1 - (projectNameProgress * 0.05f)
                        scaleY = 1 - (projectNameProgress * 0.05f)
                    }
                    .then(
                        if (backdrop != null) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = 150f,
                                noiseCoefficient = noiseCoefficient,
                                colors = BlurColors(blendColors = logoBlend),
                                contentBlendMode = ComposeBlendMode.DstIn,
                                enabled = blurEnable,
                            )
                        } else {
                            Modifier
                        },
                    ),
                text = stringResource(id = R.string.app_name),
                fontWeight = FontWeight.Bold,
                fontSize = 35.sp,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val versionCodeProgress = ((scrollProgressProvider() - 0.05f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1 - versionCodeProgress
                        scaleX = 1 - (versionCodeProgress * 0.05f)
                        scaleY = 1 - (versionCodeProgress * 0.05f)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    text = versionInfoText,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                if (uiState.hasUpdate) {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = stringResource(R.string.update_available, uiState.remoteVersion),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Scrollable content area
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = listContentPadding,
        ) {
            // Transparent spacer matching logo height
            item(key = "logoSpacer") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(
                            logoHeightDp + 52.dp + logoPadding.calculateTopPadding() - listContentPadding.calculateTopPadding() + 126.dp,
                        )
                        .onSizeChanged { size ->
                            onLogoHeightChanged(size.height)
                        },

                    contentAlignment = Alignment.TopCenter,
                    content = { },
                )
            }

            item(key = "about_content") {
                Column(
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .padding(bottom = listContentPadding.calculateBottomPadding()),
                ) {
                    SmallTitle(stringResource(R.string.about))

                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .then(
                                if (backdrop != null) {
                                    Modifier.textureBlur(
                                        backdrop = backdrop,
                                        shape = RoundedCornerShape(16.dp),
                                        blurRadius = blurRadius,
                                        noiseCoefficient = noiseCoefficient,
                                        colors = BlurColors(
                                            blendColors = currentConfigValue,
                                            brightness = brightness,
                                            contrast = contrast,
                                            saturation = saturation,
                                        ),
                                        enabled = blurEnable,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        colors = CardDefaults.defaultColors(
                            if (backdrop != null && blurEnable) MiuixTheme.colorScheme.surfaceContainer.copy(alpha = BLUR_CARD_ALPHA) else MiuixTheme.colorScheme.surfaceContainer,
                            Color.Transparent,
                        ),
                    ) {
                        MiuixNavigationItemWidget(
                            title = stringResource(R.string.get_source_code),
                            description = stringResource(R.string.get_source_code_detail),
                            onClick = { uriHandler.openUri("https://github.com/Rune-cn/miuix-example") },
                        )
                        // Shell: update actions moved to the '更新' section below.
                    }

                    SmallTitle(stringResource(R.string.settings_update))
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .then(
                                if (backdrop != null) {
                                    Modifier.textureBlur(
                                        backdrop = backdrop,
                                        shape = RoundedCornerShape(16.dp),
                                        blurRadius = blurRadius,
                                        noiseCoefficient = noiseCoefficient,
                                        colors = BlurColors(
                                            blendColors = currentConfigValue,
                                            brightness = brightness,
                                            contrast = contrast,
                                            saturation = saturation,
                                        ),
                                        enabled = blurEnable,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        colors = CardDefaults.defaultColors(
                            if (backdrop != null && blurEnable) MiuixTheme.colorScheme.surfaceContainer.copy(alpha = BLUR_CARD_ALPHA) else MiuixTheme.colorScheme.surfaceContainer,
                            Color.Transparent,
                        ),
                    ) {
                        MiuixNavigationItemWidget(
                            title = stringResource(R.string.get_update),
                            description = if (uiState.hasUpdate) {
                                stringResource(R.string.update_available, uiState.remoteVersion)
                            } else {
                                stringResource(R.string.get_update_detail)
                            },
                            onClick = onCheckUpdate,
                        )
                        MiuixSwitchWidget(
                            title = stringResource(R.string.auto_check_update_on_start),
                            description = stringResource(R.string.auto_check_update_on_start_desc),
                            checked = uiState.autoCheckUpdateOnStart,
                            onCheckedChange = onSetAutoCheckUpdateOnStart,
                        )
                        if (uiState.hasUpdate) {
                            MiuixNavigationItemWidget(
                                title = stringResource(R.string.get_update_directly),
                                description = stringResource(R.string.get_update_directly_desc),
                                onClick = onDirectUpdate,
                            )
                        }
                    }

                    if (true) { // Shell: debug section always visible
                        SmallTitle(stringResource(R.string.debug))
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                                .then(
                                    if (backdrop != null) {
                                        Modifier.textureBlur(
                                            backdrop = backdrop,
                                            shape = RoundedCornerShape(16.dp),
                                            blurRadius = blurRadius,
                                            noiseCoefficient = noiseCoefficient,
                                            colors = BlurColors(
                                                blendColors = currentConfigValue,
                                                brightness = brightness,
                                                contrast = contrast,
                                                saturation = saturation,
                                            ),
                                            enabled = blurEnable,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                            colors = CardDefaults.defaultColors(
                                if (backdrop != null && blurEnable) MiuixTheme.colorScheme.surfaceContainer.copy(alpha = BLUR_CARD_ALPHA) else MiuixTheme.colorScheme.surfaceContainer,
                                Color.Transparent,
                            ),
                        ) {
                            BasicComponent(
                                title = stringResource(R.string.crash_logs),
                                summary = uiState.latestCrashSummary
                                    ?: stringResource(R.string.crash_logs_empty),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(lazyListState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            trackPadding = listContentPadding,
        )
    }

    // Shell mode: dev texture-set sheet removed.

}

@Suppress("FunctionName")
@Composable
private fun ValueText(text: String) {
    Text(
        text = text,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
    )
}

// Frosted-glass card tint used while blur is enabled. Kept clearly
// translucent so cards stay visible even if the blur backdrop cannot
// paint on a given device (never fully transparent, never solid).
private const val BLUR_CARD_ALPHA = 0.45f
