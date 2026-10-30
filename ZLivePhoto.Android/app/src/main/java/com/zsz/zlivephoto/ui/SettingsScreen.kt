package com.zsz.zlivephoto.ui

import android.content.Context
import android.content.Intent
import android.content.res.XmlResourceParser
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.BuildConfig
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.AppLanguage
import com.zsz.zlivephoto.core.FfmpegAddon
import com.zsz.zlivephoto.core.UpdateChecker
import com.zsz.zlivephoto.core.UpdateCheckResult
import com.zsz.zlivephoto.core.UpdateInfo
import com.zsz.zlivephoto.ui.glass.GlassBackButton
import com.zsz.zlivephoto.ui.glass.GlassDropdownMenu
import com.zsz.zlivephoto.ui.glass.GlassMenuRow
import com.zsz.zlivephoto.ui.glass.GlassSlider
import com.zsz.zlivephoto.ui.glass.GlassSwitch
import com.zsz.zlivephoto.ui.glass.GlassTopBar
import com.zsz.zlivephoto.ui.glass.LiquidDialog
import com.zsz.zlivephoto.ui.glass.LiquidRoundedRectangle
import com.zsz.zlivephoto.ui.glass.LiquidSegmentedTabs
import com.zsz.zlivephoto.ui.glass.LocalGlassBackdrop
import com.zsz.zlivephoto.ui.glass.LocalGlassRootBackdrop
import com.zsz.zlivephoto.ui.glass.LocalGlassEnabled
import com.zsz.zlivephoto.ui.glass.PublishPageBackdrop
import com.zsz.zlivephoto.ui.glass.TopBarFrost
import com.zsz.zlivephoto.ui.glass.catalog.components.LiquidSlider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import kotlin.math.roundToInt

private const val GITHUB_REPO_URL = "https://github.com/zsz-of/ZLC"

/**
 * 设置页入口：负责各子页（0=主设置，1=不再提示弹窗，2=关于）的
 * 进入/退出过渡与返回键层级。主内容委托给 SettingsMainContent。
 *
 * 返回层级照搬 FlClash（`CommonPopScope` + `Navigator.canPop`）：
 * 二级页的返回先弹回设置根页，设置根页再返回才是回主页；
 * 进入二级页的动画仍用本项目的推拉（slideIn/slideOut），不改。
 *
 * @param onSubpageChanged 二级页开合状态回传（宿主据此在二级页期间关闭
 *   预测式返回动画，把返回完全交给本页的层级处理，与 FlClash 一致）
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onSubpageChanged: (Boolean) -> Unit = {}) {
    var subpage by remember { mutableStateOf(0) }
    // 设置主内容滚动位置提升到此处，子页返回后保持进入前的滚动位置
    val settingsScrollState = rememberScrollState()
    // 任一子页打开时，系统返回键先回到设置页（而非直接回主页）
    BackHandler(enabled = subpage != 0) { subpage = 0 }
    // 二级页状态回传宿主（宿主据此暂停最外层预测式返回动画）
    LaunchedEffect(subpage) { onSubpageChanged(subpage != 0) }
    // RTL：子页推拉方向跟随布局方向（阿语/希伯来语下整体反号，否则前进/后退会反着滑）
    val rtlDir = if (
        androidx.compose.ui.platform.LocalLayoutDirection.current ==
        androidx.compose.ui.unit.LayoutDirection.Rtl
    ) -1 else 1
    // 左缘右滑返回（FlClash 页面返回手势）：二级页可退，设置根页交给宿主处理
    var subpageSwipe by remember { mutableFloatStateOf(0f) }
    AnimatedContent(
        targetState = subpage,
        modifier = Modifier
            .fillMaxSize()
            .edgeSwipeBack(
                enabled = BuildConfig.FLAVOR != "go" && subpage != 0,
                rtl = rtlDir < 0,
                onProgress = { subpageSwipe = it },
                onComplete = {
                    subpageSwipe = 0f
                    subpage = 0
                },
                onCancel = { subpageSwipe = 0f }
            )
            .graphicsLayer {
                val p = subpageSwipe
                if (p > 0f) {
                    scaleX = 1f - p * 0.06f
                    scaleY = 1f - p * 0.06f
                    translationX = p * size.width * 0.18f * rtlDir
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
            },
        transitionSpec = {
            if (targetState == 0) {
                (slideInHorizontally(tween(300, easing = FancyEasing)) { -it * rtlDir } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(300, easing = FancyEasing)) { it * rtlDir } + fadeOut(tween(200)))
            } else {
                (slideInHorizontally(tween(300, easing = FancyEasing)) { it * rtlDir } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(300, easing = FancyEasing)) { -it * rtlDir } + fadeOut(tween(200)))
            }
        },
        label = "settingsSubNav"
    ) { page ->
        when (page) {
            0 -> SettingsMainContent(
                scrollState = settingsScrollState,
                onBack = onBack,
                onOpenReminder = { subpage = 1 },
                onOpenAbout = { subpage = 2 }
            )
            1 -> ReminderSettingsScreen(onBack = { subpage = 0 })
            else -> AboutScreen(onBack = { subpage = 0 })
        }
    }
}

/**
 * 设置类页面统一外壳：顶栏改为**覆盖层**，内容（含状态栏区域）从屏幕顶部开始绘制，
 * 滚动时从顶栏下方穿过；顶栏背后由 [TopBarFrost] 叠加「上强下弱」的渐进高斯模糊，
 * 用来区分常驻顶栏与滚动内容。未启用玻璃时退回原不透明 TopAppBar 行为。
 *
 * [overlay] 槽位渲染在**页面采样层之外**（与 TopBarFrost 同一作用域）：
 * 页面自己的弹窗放这里，玻璃才能采样「根渐变 + 本页内容」且不会采样到自身
 * （弹窗改同窗口覆盖层后必须如此，见 [LiquidDialog]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    scrollState: ScrollState,
    overlay: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalGlassEnabled.current
    // 采样层：把整页滚动内容记录一份，顶栏的渐进模糊采样真实内容
    val pageBackdrop = if (glass) rememberLayerBackdrop() else null
    // 发布给应用级弹窗（渲染在本页之外）采样「根 + 本页」：同窗口坐标对正、
    // 模糊实时（用户反馈 #5/#6），复用本录制零开销
    PublishPageBackdrop(pageBackdrop)
    val topBarTotal = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier)
                // FlClash 设置页背景板：整页铺 SurfaceLevel.page 底色
                // （浅色 = surfaceContainer 灰紫调 / 深色 = surface 近纯黑）。
                // 放在 layerBackdrop **之内**，玻璃顶栏的渐进模糊采样的就是
                // 「页面底 + 滚动上来的卡片」，与 FlClash 的观感一致。
                .background(flPageColor())
        ) {
            Scaffold(
                // 页面底已由外层绘制：玻璃模式下容器透明，非玻璃模式同用页面底色
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { _ ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        // 先纵向滚动、后加内边距：顶部留白属于滚动内容，会随列表一起滚走，
                        // 内容因此能真正穿过顶栏下方
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp)
                        .padding(top = topBarTotal + 8.dp, bottom = 8.dp),
                    content = content
                )
            }
        }
        CompositionLocalProvider(
            LocalGlassBackdrop provides (pageBackdrop ?: LocalGlassBackdrop.current)
        ) {
            TopBarFrost(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                if (glass) {
                    GlassTopBar(
                        title = {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        titleStartPadding = 20.dp,
                        navigationIcon = {
                            GlassBackButton(
                                onBack = onBack,
                                contentDescription = stringResource(R.string.settings_cd_back)
                            )
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text(title, fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            GlassBackButton(
                                onBack = onBack,
                                contentDescription = stringResource(R.string.settings_cd_back)
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
            // 页面级弹窗覆盖层（采样层之外，见 [SettingsPageScaffold] 的 overlay 参数）
            overlay()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainContent(
    scrollState: ScrollState,
    onBack: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    val context = LocalContext.current

    var showCustomPalette by remember { mutableStateOf(false) }
    // 应用语言：currentTag 为空串表示跟随系统
    var appLanguageTag by remember { mutableStateOf(AppLanguage.currentTag(context)) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // 展开菜单（深浅色 / 编码器 / 压缩预设）：当前被点开的那一行
    var selectMenu by remember { mutableStateOf<SettingsSelectMenu?>(null) }
    // 各选择行的根坐标锚点：onGloballyPositioned 回填，点开时取最近一次位置
    var themeRowAnchor by remember { mutableStateOf(Rect.Zero) }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        scrollState = scrollState,
        overlay = {
            // 自定义调色板：任意色相选择（主题色只按色相派生，饱和度/明度沿用 MD3 基线）
            // 打开时重置为当前生效色相（覆盖层常驻组合，hue 不会像旧版 if 包裹那样每次重建）
            var hue by remember {
                mutableStateOf(if (AppSettings.customHue >= 0f) AppSettings.customHue else presetHues[AppSettings.presetColor])
            }
            LaunchedEffect(showCustomPalette) {
                if (showCustomPalette) {
                    hue = if (AppSettings.customHue >= 0f) AppSettings.customHue
                    else presetHues[AppSettings.presetColor]
                }
            }
            LiquidDialog(
                visible = showCustomPalette,
                onDismissRequest = { showCustomPalette = false },
                title = { Text(stringResource(R.string.settings_custom_color_title)) },
                text = {
                    Column {
                        // 实时预览
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(swatchForHue(hue))
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.settings_hue_value, hue.toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        GlassSlider(
                            value = hue,
                            onValueChange = { hue = it },
                            valueRange = 0f..360f
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        haptic.click()
                        AppSettings.setCustomHueValue(hue)
                        showCustomPalette = false
                    }) { Text(stringResource(R.string.settings_btn_apply)) }
                },
                dismissButton = {
                    FilledTonalButton(onClick = {
                        haptic.click()
                        showCustomPalette = false
                    }) { Text(stringResource(R.string.settings_btn_cancel)) }
                }
            )

            // 应用语言选择弹窗：顶部「跟随系统」，下面是 locales_config.xml 声明的全部语种，当前项打勾
            val localeTags = remember(context) { loadLocaleTags(context) }
            LiquidDialog(
                visible = showLanguageDialog,
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(R.string.settings_language_dialog_title), fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(
                        Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            stringResource(R.string.settings_language_count, localeTags.size + 1),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        LanguageOptionRow(
                            label = stringResource(R.string.app_language_follow_system),
                            selected = appLanguageTag.isEmpty(),
                            onClick = {
                                haptic.click()
                                AppLanguage.set(context, "")
                                appLanguageTag = AppLanguage.currentTag(context)
                                showLanguageDialog = false
                            }
                        )
                        for (tag in localeTags) {
                            LanguageOptionRow(
                                label = AppLanguage.displayName(tag),
                                selected = appLanguageTag == tag,
                                onClick = {
                                    haptic.click()
                                    AppLanguage.set(context, tag)
                                    appLanguageTag = AppLanguage.currentTag(context)
                                    showLanguageDialog = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    FilledTonalButton(onClick = {
                        haptic.soft()
                        showLanguageDialog = false
                    }) { Text(stringResource(R.string.settings_btn_close)) }
                }
            )

            // 展开菜单浮层：定位在触发行的正下方（画在滚动容器之外，
            // 既不会被列表裁掉，也不会随列表滚动跑位）
            Box(Modifier.fillMaxSize()) {
                val menu = selectMenu
                GlassDropdownMenu(
                    visible = menu != null,
                    anchor = menu?.anchor,
                    onDismiss = {
                        haptic.soft()
                        selectMenu = null
                    }
                ) {
                    menu?.options?.forEachIndexed { i, label ->
                        GlassMenuRow(
                            label = label,
                            selected = i == menu.selectedIndex,
                            onClick = {
                                menu.onSelect(i)
                                selectMenu = null
                            }
                        )
                    }
                }
            }
        }
    ) {
            // ── 通用 ──
            SectionTitle(stringResource(R.string.settings_section_general))
            val isGo = BuildConfig.FLAVOR == "go"
            SettingsRowsGroup(
                buildList {
                    add { s ->
                        SettingsSwitchRow(
                            shape = s,
                            title = stringResource(R.string.settings_startup_check_title),
                            subtitle = null,
                            checked = AppSettings.checkUpdateOnStartup,
                            leading = {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onToggle = { AppSettings.setStartupUpdateCheck(it) }
                        )
                    }
                    // go 轻量版：无马达反馈与按钮弹性动画，隐藏对应开关
                    if (!isGo) {
                        add { s ->
                            SettingsSwitchRow(
                                shape = s,
                                title = stringResource(R.string.settings_haptics_title),
                                subtitle = null,
                                checked = AppSettings.hapticsEnabled,
                                onToggle = { AppSettings.setHaptics(it) }
                            )
                        }
                        add { s ->
                            SettingsSwitchRow(
                                shape = s,
                                title = stringResource(R.string.settings_bounce_title),
                                subtitle = null,
                                // 液态玻璃界面下不生效：开关显示为关且不可打开（避免与玻璃形变叠加）
                                checked = AppSettings.bounceActive,
                                enabled = !AppSettings.glassActive,
                                onToggle = { AppSettings.setBounce(it) }
                            )
                        }
                    }
                }
            )

            // ── 外观 ──
            SectionTitle(stringResource(R.string.settings_section_appearance))
            // 深浅色三选：FlClash 式展开菜单（点击行后在该行下方长出玻璃选项卡）
            val themeModes = listOf("system", "dark", "light")
            val themeLabels = listOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_dark),
                stringResource(R.string.settings_theme_light)
            )
            val themeIndex = themeModes.indexOf(AppSettings.themeMode).coerceAtLeast(0)
            SettingsRowsGroup(
                listOf(
                    { s ->
                        SettingsSelectRow(
                            shape = s,
                            title = stringResource(R.string.settings_theme_mode_title),
                            value = themeLabels[themeIndex],
                            expanded = selectMenu?.id == "theme",
                            onAnchor = { themeRowAnchor = it },
                            onClick = {
                                haptic.click()
                                selectMenu = SettingsSelectMenu(
                                    id = "theme",
                                    anchor = themeRowAnchor,
                                    options = themeLabels,
                                    selectedIndex = themeIndex,
                                    onSelect = { AppSettings.setThemeModeValue(themeModes[it]) }
                                )
                            }
                        )
                    }
                )
            )
            if (!isGo) {
                Spacer(Modifier.height(4.dp))
                SettingsRowsGroup(
                    listOf(
                        { s ->
                            SettingsSwitchRow(
                                shape = s,
                                title = stringResource(R.string.settings_dynamic_theme_title),
                                subtitle = null,
                                checked = AppSettings.dynamicTheme,
                                onToggle = { AppSettings.setUseDynamicTheme(it) }
                            )
                        }
                    )
                )
            }
            Spacer(Modifier.height(4.dp))
            // 预制主题色（动态取色开启时禁用；go 版无动态取色，始终可选）
            val presetEnabled = isGo || !AppSettings.dynamicTheme
            val customActive = AppSettings.customHue >= 0f
            Column(
                Modifier
                    .fillMaxWidth()
                    .cardSurface(RoundedCornerShape(16.dp))
                    .alpha(if (presetEnabled) 1f else 0.38f)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(stringResource(R.string.settings_preset_colors_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (presetEnabled) {
                        stringResource(
                            R.string.settings_preset_current,
                            if (customActive) stringResource(R.string.settings_custom)
                            else stringResource(presetNameRes[AppSettings.presetColor])
                        )
                    } else {
                        stringResource(R.string.settings_preset_disabled_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                // 4 列两行布局：7 个预制色 + 1 个自定义调色板
                for (row in 0 until 2) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0 until 4) {
                            val idx = row * 4 + col
                            if (idx == 7) {
                                PaletteLauncherDot(
                                    selected = customActive,
                                    enabled = presetEnabled,
                                    onClick = {
                                        haptic.click()
                                        showCustomPalette = true
                                    }
                                )
                            } else {
                                PresetColorDot(
                                    index = idx,
                                    selected = !customActive && AppSettings.presetColor == idx,
                                    enabled = presetEnabled,
                                    onClick = {
                                        haptic.click()
                                        AppSettings.setPresetColorIndex(idx)
                                    }
                                )
                            }
                        }
                    }
                    if (row == 0) Spacer(Modifier.height(12.dp))
                }
            }

            // ── 液态玻璃界面 ──
            // 能力门槛与 AppSettings.glassActive 一致：go 轻量版与 API<31 不支持（退回原生 MD3）
            val glassSupported = !isGo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            Spacer(Modifier.height(4.dp))
            SettingsRowsGroup(
                listOf(
                    { s ->
                        SettingsSwitchRow(
                            shape = s,
                            title = stringResource(R.string.settings_glass_title),
                            subtitle = if (glassSupported) null
                            else stringResource(R.string.settings_glass_unsupported),
                            checked = glassSupported && AppSettings.glassEnabled,
                            enabled = glassSupported,
                            onToggle = { AppSettings.setGlassUIEnabled(it) }
                        )
                    }
                )
            )
            // 通透度滑条：仅在玻璃可用且已开启时可调（越低越不透明、磨砂越强）
            if (glassSupported && AppSettings.glassEnabled) {
                Spacer(Modifier.height(4.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .cardSurface(RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_glass_opacity_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    // 用根采样层：本页整块内容已被 layerBackdrop 记录，
                    // 若滑条采样页面层会把自身画进去 → 反馈式频闪
                    val backdrop = LocalGlassRootBackdrop.current
                    if (backdrop != null) {
                        LiquidSlider(
                            value = { AppSettings.glassOpacity },
                            onValueChange = { AppSettings.setGlassOpacityValue(it) },
                            valueRange = 0f..1f,
                            visibilityThreshold = 0.01f,
                            backdrop = backdrop
                        )
                    } else {
                        GlassSlider(
                            value = AppSettings.glassOpacity,
                            onValueChange = { AppSettings.setGlassOpacityValue(it) },
                            valueRange = 0f..1f,
                            steps = 9
                        )
                    }
                }
            }

            // ── 语言 ──
            // 清单与系统「应用语言」同源：res/xml/locales_config.xml 声明的 119 个语种 +「跟随系统」
            SectionTitle(stringResource(R.string.settings_section_language))
            SettingsRowsGroup(
                listOf(
                    { s ->
                        SettingsActionRow(
                            shape = s,
                            title = stringResource(R.string.app_language_title),
                            subtitle = if (appLanguageTag.isEmpty()) {
                                stringResource(R.string.app_language_follow_system)
                            } else {
                                AppLanguage.displayName(appLanguageTag)
                            },
                            icon = {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                haptic.click()
                                showLanguageDialog = true
                            }
                        )
                    }
                )
            )

            // ── 默认选项 ──
            SectionTitle(stringResource(R.string.settings_section_defaults))
            SettingsRowsGroup(
                listOf(
                    { s ->
                        SettingsActionRow(
                            shape = s,
                            title = stringResource(R.string.settings_reminder_dialogs_title),
                            subtitle = stringResource(R.string.settings_reminder_dialogs_subtitle),
                            icon = { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                haptic.click()
                                onOpenReminder()
                            }
                        )
                    }
                )
            )

            // ── 输出 ──
            SectionTitle(stringResource(R.string.settings_section_output))
            SettingsRowsGroup(
                listOf(
                    { s ->
                        SettingsSwitchRow(
                            shape = s,
                            title = stringResource(R.string.settings_preserve_folders_title),
                            subtitle = stringResource(R.string.settings_preserve_folders_subtitle),
                            checked = AppSettings.preserveFolders,
                            leading = {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onToggle = { AppSettings.setPreserveFolderStructure(it) }
                        )
                    }
                )
            )

            // ── 内置转码器参数（go 版隐藏）──
            TranscoderAddonSection(
                openMenuId = selectMenu?.id,
                onOpenSelect = { selectMenu = it }
            )

            // ── 关于 ──
            SectionTitle(stringResource(R.string.settings_section_about))
            SettingsRowsGroup(
                listOf(
                    { s ->
                        SettingsActionRow(
                            shape = s,
                            title = stringResource(R.string.settings_about_title),
                            subtitle = stringResource(R.string.settings_about_subtitle),
                            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                haptic.click()
                                onOpenAbout()
                            }
                        )
                    }
                )
            )
            Spacer(Modifier.height(24.dp))
    }

}

/**
 * 「默认选项」子页：按分类列出所有带「不再提示」的弹窗。
 * 每条右侧开关：开 = 不再提示；关 = 下次触发仍弹窗（无查看/预览弹窗）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSettingsScreen(onBack: () -> Unit) {
    val haptic = rememberHapticFeedback()

    SettingsPageScaffold(
        title = stringResource(R.string.settings_reminder_dialogs_title),
        onBack = onBack,
        scrollState = rememberScrollState()
    ) {
            // 每类一张 FlClash 式分组卡（组内行透明 + 内缩分隔线），与主设置页一致
            for ((category, keys) in ReminderKey.entries.groupBy { it.category }) {
                SectionTitle(category)
                SettingsRowsGroup(
                    keys.map { key -> { s -> ReminderSettingRow(key = key, shape = s) } }
                )
            }
            Text(
                stringResource(R.string.settings_reminder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 24.dp)
            )
    }
}

/**
 * 「关于」子页：不再平铺在设置里，而是独立子界面。
 * 分组顺序（鸣谢板块位于开源许可证之前）：
 * 应用（检查更新点一次查一次 / 版本）→ 鸣谢与赞助（支付宝赞助、@毛血旺o 酷安主页、
 * AI 开发伙伴）→ 开源与授权（GitHub 开源地址 / 开源许可证书本图标）。
 * 赞助点击后先弹感谢/谢绝学生的确认窗，吊起支付宝失败则单独提示并致谢。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val haptic = rememberHapticFeedback()
    val context = LocalContext.current

    // 手动检查更新：每次点击触发一次网络检查（正在检查时忽略重复点击）
    var checking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    // 最近一次拉取到的「最新 release」（无论是否比当前新）。
    // 「重新安装本版本」仅当确认“当前已是最新版”时才允许点击，需要依赖这里的最新版本信息。
    var latestInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    // 更新弹窗状态：发现新版本（4 按钮）/ 说明子弹窗 / 下载进度 / 蓝奏失败回退
    val updateFlow = rememberUpdateFlow()
    val checkScope = rememberCoroutineScope()

    /** 拉取最新版并刷新状态；[presentIfNewer] 为 true（用户主动点「检查更新」）时发现新版直接弹窗 */
    suspend fun performCheck(presentIfNewer: Boolean) {
        val info = UpdateChecker.fetchLatest()
        if (info == null) {
            updateResult = UpdateCheckResult.NetworkError
        } else {
            latestInfo = info
            val newer = UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)
            updateResult = if (newer) UpdateCheckResult.Update(info) else UpdateCheckResult.UpToDate
            // 手动检查不套用「跳过此版本」：用户主动点查就给出弹窗
            if (newer && presentIfNewer) updateFlow.present(info)
        }
    }

    // 进入关于页先静默拉一次最新版本信息（不自动弹窗），让「重新安装本版本」的可用状态有据可依
    LaunchedEffect(Unit) {
        checking = true
        performCheck(presentIfNewer = false)
        checking = false
    }
    // 支付宝赞助相关弹窗
    var showSponsorDialog by remember { mutableStateOf(false) }
    var alipayMissing by remember { mutableStateOf(false) }

    SettingsPageScaffold(
        title = stringResource(R.string.settings_about_title),
        onBack = onBack,
        scrollState = rememberScrollState(),
        overlay = {
            // 手动检查发现新版本：4 按钮更新弹窗（说明子弹窗/下载进度/蓝奏失败回退统一在此渲染）
            UpdateFlowHosts(updateFlow, vibrate = { haptic.click() })

            // 支付宝赞助致谢弹窗（无「不再提示」）：感谢 + 礼貌谢绝学生
            LiquidDialog(
                visible = showSponsorDialog,
                onDismissRequest = { showSponsorDialog = false },
                title = { Text(stringResource(R.string.settings_sponsor_dialog_title)) },
                text = {
                    Text(stringResource(R.string.settings_sponsor_dialog_text))
                },
                confirmButton = {
                    Button(onClick = {
                        haptic.click()
                        showSponsorDialog = false
                        // 成功拉起支付宝收款码则交给支付宝；失败（含未安装）单独提示
                        if (!launchAlipay(context)) alipayMissing = true
                    }) { Text(stringResource(R.string.settings_btn_sponsor)) }
                },
                dismissButton = {
                    FilledTonalButton(onClick = {
                        haptic.soft()
                        showSponsorDialog = false
                    }) { Text(stringResource(R.string.settings_btn_close)) }
                }
            )

            // 支付宝未能吊起：说明未安装，并再次感谢用户好意
            LiquidDialog(
                visible = alipayMissing,
                onDismissRequest = { alipayMissing = false },
                title = { Text(stringResource(R.string.settings_alipay_missing_title)) },
                text = {
                    Text(stringResource(R.string.settings_alipay_missing_text))
                },
                confirmButton = {
                    Button(onClick = {
                        haptic.click()
                        alipayMissing = false
                    }) { Text(stringResource(R.string.settings_btn_ok)) }
                }
            )
        }
    ) {
            // ── 应用 ──
            SectionTitle(stringResource(R.string.settings_section_app))
            SettingsRowsGroup(
                listOf(
                    { s ->
                        SettingsActionRow(
                            shape = s,
                            title = stringResource(R.string.settings_check_update_title),
                            subtitle = stringResource(R.string.settings_check_update_subtitle),
                            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailing = {
                                Text(
                                    when (val r = updateResult) {
                                        null -> if (checking) stringResource(R.string.settings_checking) else ""
                                        is UpdateCheckResult.Update -> stringResource(R.string.settings_update_available, r.info.version)
                                        UpdateCheckResult.UpToDate -> stringResource(R.string.settings_up_to_date)
                                        UpdateCheckResult.NetworkError -> stringResource(R.string.settings_network_error)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                if (!checking) {
                                    haptic.click()
                                    checking = true
                                    checkScope.launch {
                                        performCheck(presentIfNewer = true)
                                        checking = false
                                    }
                                }
                            }
                        )
                    },
                    { s ->
                        // 重新安装本版本：仅在“当前已是最新版”时可用（此时最新版信息就是当前版本）。
                        // 若存在更新版本则禁用，引导用户先用「检查更新」升级，避免覆盖安装把新版本顶掉。
                        val info = latestInfo
                        val upToDate = info != null &&
                            !UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)
                        val newerVersion = if (info != null && !upToDate) info.version else null
                        SettingsActionRow(
                            shape = s,
                            title = stringResource(R.string.settings_reinstall_title),
                            subtitle = if (newerVersion == null) {
                                stringResource(R.string.settings_reinstall_subtitle)
                            } else {
                                stringResource(R.string.settings_reinstall_newer_hint, newerVersion)
                            },
                            icon = {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = if (upToDate) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailing = {
                                if (latestInfo == null && checking) {
                                    Text(
                                        stringResource(R.string.settings_checking),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            enabled = upToDate,
                            onClick = {
                                val latest = latestInfo
                                if (latest != null && upToDate && !checking) {
                                    haptic.click()
                                    // 已确认为最新版：直接用持有信息弹出「重新安装本版本」弹窗，不再重复请求网络
                                    updateFlow.startReinstall(latest)
                                }
                            }
                        )
                    },
                    { s ->
                        SettingsInfoRow(
                            shape = s,
                            title = stringResource(R.string.settings_version_title),
                            value = stringResource(R.string.settings_version_value, BuildConfig.VERSION_NAME)
                        )
                    }
                )
            )

            // ── 鸣谢与赞助 ──
            // 用户要求「鸣谢板块」放在「开源许可证」前面
            SectionTitle(stringResource(R.string.settings_section_credits))
            SettingsRowsGroup(
                listOf(
                    { s ->
                        // 支付宝赞助：先弹致谢确认窗，再决定是否拉起支付宝
                        SettingsActionRow(
                            shape = s,
                            title = stringResource(R.string.settings_alipay_title),
                            subtitle = stringResource(R.string.settings_alipay_subtitle),
                            icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                haptic.click()
                                showSponsorDialog = true
                            }
                        )
                    },
                    { s ->
                        // 酷安用户 @毛血旺o：点击跳浏览器打开其主页
                        SettingsActionRow(
                            shape = s,
                            title = "@毛血旺o",
                            subtitle = stringResource(R.string.settings_coolapk_subtitle),
                            icon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                haptic.click()
                                openInBrowser(context, "https://www.coolapk.com/u/2910471")
                            }
                        )
                    },
                    { s ->
                        // AI IDE 与 AI 模型的鸣谢
                        SettingsCreditRow(
                            shape = s,
                            title = "Trae · GML · DeepSeek · Kimi",
                            subtitle = stringResource(R.string.settings_ai_credits_subtitle)
                        )
                    }
                )
            )

            // ── 开源与授权 ──
            SectionTitle(stringResource(R.string.settings_section_opensource))
            SettingsRowsGroup(
                listOf(
                    { s ->
                        SettingsActionRow(
                            shape = s,
                            title = stringResource(R.string.settings_github_title),
                            subtitle = GITHUB_REPO_URL.removePrefix("https://"),
                            icon = { Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                haptic.click()
                                openInBrowser(context, GITHUB_REPO_URL)
                            }
                        )
                    },
                    { s ->
                        // 开源许可证：书本图标
                        SettingsInfoRow(
                            shape = s,
                            title = stringResource(R.string.settings_license_title),
                            value = "GPL-3.0",
                            leading = {
                                Icon(
                                    Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                )
            )
            Spacer(Modifier.height(24.dp))
    }
}

/** 静态鸣谢行（非点击）：标题 + 摘要 */
@Composable
private fun SettingsCreditRow(shape: RoundedCornerShape, title: String, subtitle: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .cardSurface(shape)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 单条「不再提示」设置行：标题/摘要 + 开关；整行可点 + 弹性动画，开关状态即时刷新 */
@Composable
private fun ReminderSettingRow(key: ReminderKey, shape: RoundedCornerShape) {
    val fb = rememberPressFeedback(hapticOnPress = false)
    val haptic = rememberHapticFeedback()
    // 本地 state 驱动 Switch 即时重绘（AppSettings 持久化值不触发重组，此前开关不刷新）
    var checked by remember(key) { mutableStateOf(AppSettings.isReminderSuppressed(key)) }
    val toggle: (Boolean) -> Unit = { v ->
        if (v) haptic.click() else haptic.soft()
        checked = v
        AppSettings.setReminderSuppressed(key, v)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .cardSurface(shape)
            .clickable(
                interactionSource = fb.interactionSource,
                indication = null
            ) { toggle(!checked) }
            .then(fb.scaleModifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(key.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                key.message.replace('\n', ' '),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        GlassSwitch(
            checked = checked,
            onCheckedChange = toggle
        )
    }
}

/**
 * FlClash 的分层底色（`SurfaceLevel.page` / `SurfaceLevel.card`）：
 * 页面底与卡片各占一档，靠底色差而不是投影表达层次——
 * 浅色页底是 surfaceContainer（灰紫调）、卡片是 surfaceBright（近白）；
 * 深色页底是 surface（近纯黑）、卡片是 surfaceContainer（深灰）。
 */
@Composable
internal fun flPageColor(): Color = if (LocalDarkTheme.current)
    MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer

@Composable
internal fun flCardColor(): Color = if (LocalDarkTheme.current)
    MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceBright

/** 当前行是否位于「单卡分组」内：是则行自身不再画底色（由分组卡统一提供） */
private val LocalInSettingsCard = staticCompositionLocalOf { false }

/**
 * 设置卡片背景：固定使用 FlClash 的卡片档底色，不参与液态玻璃采样，也不投射阴影。
 * 位于 [SettingsRowsGroup] 内时返回原 Modifier——底色与圆角由那张分组卡统一负责。
 */
@Composable
private fun Modifier.cardSurface(
    shape: Shape,
    color: Color = flCardColor()
): Modifier = if (LocalInSettingsCard.current) this else background(color, shape)

/** 分组标题（与卡片左缘对齐：不再额外缩进，避免标题比卡片内缩一档） */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp)
    )
}

/**
 * FlClash 设置页的「背景板 + 分类卡片」：
 * 一个分组就是**一张**超椭圆卡片（[LiquidRoundedRectangle]，16dp），
 * 组内各行透明、行之间用一条内缩 1dp 分隔线。
 * 旧版是「每行一张小卡、行间 4dp 露底」的 SplitListGroup 风格，
 * 用户要求改成 FlClash 那种整块分类卡。
 */
@Composable
private fun SettingsRowsGroup(rows: List<@Composable (RoundedCornerShape) -> Unit>) {
    val cardShape = LiquidRoundedRectangle(16.dp)
    CompositionLocalProvider(LocalInSettingsCard provides true) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(flCardColor(), cardShape)
                .clip(cardShape)
        ) {
            rows.forEachIndexed { index, row ->
                if (index > 0) SettingsRowDivider()
                // 组内行自身不画底色，形状由分组卡统一裁剪：这里给 0 圆角即可
                row(RoundedCornerShape(0.dp))
            }
        }
    }
}

/** 分组卡内的行分隔线（左右各内缩 16dp，与 FlClash / iOS 设置页一致） */
@Composable
private fun SettingsRowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}

/**
 * 开关行：整行可点 + 行级弹性动画；Switch 使用自身默认 interactionSource，
 * 其滑块按压动画与行级弹性动画解耦，避免共享 interactionSource 时
 * 滑块在动画中断/开关自身被切换时卡在放大态无法复原。
 * 开启=清脆震动 / 关闭=柔和震动（与删除原图行一致的差异化反馈）。
 */
@Composable
private fun SettingsSwitchRow(
    shape: RoundedCornerShape,
    title: String,
    subtitle: String?,
    checked: Boolean,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    val fb = rememberPressFeedback(hapticOnPress = false)
    val haptic = rememberHapticFeedback()
    val toggle: (Boolean) -> Unit = { v ->
        if (v) haptic.click() else haptic.soft()
        onToggle(v)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .cardSurface(shape)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(
                interactionSource = fb.interactionSource,
                indication = null,
                enabled = enabled
            ) { toggle(!checked) }
            .then(fb.scaleModifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        GlassSwitch(
            checked = checked,
            onCheckedChange = toggle,
            enabled = enabled
        )
    }
}

/** 信息展示行（版本号 / 许可证），可带前置小图标 */
@Composable
private fun SettingsInfoRow(
    shape: RoundedCornerShape,
    title: String,
    value: String,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .cardSurface(shape)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 可点击操作行（检查更新 / GitHub 链接），带弹性动画；[enabled]=false 时禁用（半透明、不可点）。
 * [danger]=true 时背景板与文字改用 error 配色（与主页「清空」按钮同款背景板），用于破坏性操作。
 */
@Composable
private fun SettingsActionRow(
    shape: RoundedCornerShape,
    title: String,
    subtitle: String?,
    icon: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val fb = rememberPressFeedback(hapticOnPress = false)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(
                if (danger) Modifier.background(MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                else Modifier.cardSurface(shape)
            )
            .then(
                if (enabled) {
                    Modifier
                        .clickable(
                            interactionSource = fb.interactionSource,
                            indication = null
                        ) { onClick() }
                        .then(fb.scaleModifier)
                } else {
                    Modifier
                }
            )
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (danger) MaterialTheme.colorScheme.onError else Color.Unspecified
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (danger) {
                        MaterialTheme.colorScheme.onError.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/**
 * 展开菜单的状态：某一行被点开时，把它在**根坐标系**的矩形与选项一起交给
 * 页面级浮层 [GlassDropdownMenu]（浮层画在滚动容器之外，才不会被列表裁掉、
 * 也不会随滚动跑位）。
 * [id] 用来判断「哪一行处于展开态」（箭头旋转），避免按选项内容比对。
 */
private class SettingsSelectMenu(
    val id: String,
    val anchor: Rect,
    val options: List<String>,
    val selectedIndex: Int,
    val onSelect: (Int) -> Unit
)

/**
 * 可展开选择行（FlClash 设置页的「点击展开菜单」版式）：
 * 左侧标题（+可选副标题），右侧当前值 + 箭头，点一下在该行下方弹出玻璃菜单。
 * 箭头在菜单展开期间旋转 90°（与 [GlassMenuEntry] 一致）。
 */
@Composable
private fun SettingsSelectRow(
    shape: RoundedCornerShape,
    title: String,
    value: String,
    subtitle: String? = null,
    expanded: Boolean = false,
    enabled: Boolean = true,
    onAnchor: (Rect) -> Unit,
    onClick: () -> Unit
) {
    val fb = rememberPressFeedback(hapticOnPress = false)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "settingsSelectChevron"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .cardSurface(shape)
            .alpha(if (enabled) 1f else 0.38f)
            .onGloballyPositioned { onAnchor(it.boundsInRoot()) }
            .clickable(
                interactionSource = fb.interactionSource,
                indication = null,
                enabled = enabled
            ) { onClick() }
            .then(fb.scaleModifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp).rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 内置转码器（ffmpeg）：随 APK 打包为 native 库，无需下载/校验/解压/更新，
 * 设置页提供「转码方式」三选一与转码参数。
 * - go 轻量版不含内置转码器，整个区块隐藏；
 * - 转码方式默认「仅重封装容器」（只换容器不重新编码）；
 * - 参数（CRF/编码器/预设）默认 cq18 / H.265 / slow，仅「重新编码」时生效。
 *
 * 编码器与压缩预设按 FlClash 式展开菜单呈现：点击行后把 [SettingsSelectMenu]
 * 交给页面级浮层（[openMenuId] 用于让对应行的箭头进入展开态）。
 */
@Composable
private fun TranscoderAddonSection(
    openMenuId: String?,
    onOpenSelect: (SettingsSelectMenu) -> Unit
) {
    if (BuildConfig.FLAVOR == "go") return

    SectionTitle(stringResource(R.string.settings_section_transcode))

    SettingsRowsGroup(
        listOf(
            { s ->
                SettingsInfoRow(
                    shape = s,
                    title = stringResource(R.string.settings_transcoder_title),
                    value = stringResource(R.string.settings_transcoder_value, FfmpegAddon.BUNDLED_VERSION)
                )
            }
        )
    )

    // 转码方式三选：演示版 BOTTOM tabs（液态玻璃胶囊 + 可拖拽指示器 + 按压力高光）
    Spacer(Modifier.height(12.dp))
    val transcodeModes = listOf(FfmpegAddon.MODE_ENCODE, FfmpegAddon.MODE_REMUX, FfmpegAddon.MODE_OFF)
    val transcodeLabels = listOf(
        stringResource(R.string.settings_transcode_mode_encode),
        stringResource(R.string.settings_transcode_mode_remux),
        stringResource(R.string.settings_transcode_mode_off)
    )
    LiquidSegmentedTabs(
        labels = transcodeLabels,
        selectedIndex = transcodeModes.indexOf(FfmpegAddon.mode).coerceAtLeast(0),
        onSelect = { FfmpegAddon.updateMode(transcodeModes[it]) }
    )

    Spacer(Modifier.height(12.dp))
    val encoding = FfmpegAddon.mode == FfmpegAddon.MODE_ENCODE
    val crfCardShape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, crfCardShape)
            .alpha(if (encoding) 1f else 0.38f)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(stringResource(R.string.settings_transcode_params_title), style = MaterialTheme.typography.titleSmall)
        Text(
            if (encoding) stringResource(R.string.settings_transcode_params_subtitle)
            else stringResource(R.string.settings_transcode_params_disabled),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.settings_crf_value, FfmpegAddon.crf),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val backdrop = LocalGlassRootBackdrop.current
        if (backdrop != null) {
            // CRF 是整数量（10..30）：换回与「模糊程度」同款的阻尼弹簧滑条
            // （用户反馈：做成和上面选择模糊程度一样的就好了）。value 用本地
            // 连续状态——若把取整后的值回填滑条，阻尼动画的 snapshotFlow 会
            // 追着整数跑造成抖动；内部动画保持连续，只在写回时四舍五入，
            // 显示与传给 ffmpeg 的都是整数。玻璃可用时即使转码关闭也保持
            // 液态玻璃外观（仅置灰禁手势），不退回 MD3 滑条。
            var crfContinuous by remember { mutableStateOf(FfmpegAddon.crf.toFloat()) }
            LiquidSlider(
                value = { crfContinuous },
                onValueChange = { v ->
                    crfContinuous = v
                    val i = v.roundToInt().coerceIn(10, 30)
                    if (i != FfmpegAddon.crf) FfmpegAddon.updateCrf(i)
                },
                valueRange = 10f..30f,
                visibilityThreshold = 0.01f,
                backdrop = backdrop,
                enabled = encoding
            )
        } else {
            // 无玻璃回落：同样不分档位（用户反馈：CRF 滑条不需要分档）
            GlassSlider(
                value = FfmpegAddon.crf.toFloat(),
                onValueChange = { v -> FfmpegAddon.updateCrf(v.roundToInt()) },
                valueRange = 10f..30f,
                enabled = encoding
            )
        }
        Text(
            stringResource(R.string.settings_crf_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 编码器 / 压缩预设：FlClash 式展开菜单（同样只在「完整转码」时可改）
    Spacer(Modifier.height(8.dp))
    val codecs = listOf("h265", "h264")
    val codecLabels = listOf("H.265", "H.264")
    val codecIndex = codecs.indexOf(FfmpegAddon.codec).coerceAtLeast(0)
    val presetIndex = FfmpegAddon.PRESETS.indexOf(FfmpegAddon.preset).coerceAtLeast(0)
    var encoderAnchor by remember { mutableStateOf(Rect.Zero) }
    var presetAnchor by remember { mutableStateOf(Rect.Zero) }
    SettingsRowsGroup(
        listOf(
            { s ->
                SettingsSelectRow(
                    shape = s,
                    title = stringResource(R.string.settings_encoder_title),
                    value = codecLabels[codecIndex],
                    expanded = openMenuId == "encoder",
                    enabled = encoding,
                    onAnchor = { encoderAnchor = it },
                    onClick = {
                        onOpenSelect(
                            SettingsSelectMenu(
                                id = "encoder",
                                anchor = encoderAnchor,
                                options = codecLabels,
                                selectedIndex = codecIndex,
                                onSelect = { FfmpegAddon.updateCodec(codecs[it]) }
                            )
                        )
                    }
                )
            },
            { s ->
                SettingsSelectRow(
                    shape = s,
                    title = stringResource(R.string.settings_compress_preset_title),
                    value = FfmpegAddon.preset,
                    expanded = openMenuId == "preset",
                    enabled = encoding,
                    onAnchor = { presetAnchor = it },
                    onClick = {
                        onOpenSelect(
                            SettingsSelectMenu(
                                id = "preset",
                                anchor = presetAnchor,
                                options = FfmpegAddon.PRESETS,
                                selectedIndex = presetIndex,
                                onSelect = { FfmpegAddon.updatePreset(FfmpegAddon.PRESETS[it]) }
                            )
                        )
                    }
                )
            }
        )
    )
}

/** 预制主题色圆点：选中态 primary 外圈 + 对勾。
 *  外圈/色点均固定尺寸（40dp / 30dp），选中变化只动画颜色与色点缩放，
 *  不改变布局高度，避免选中态外圈展开把下半列表顶出小幅抽动。 */
@Composable
private fun PresetColorDot(
    index: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val swatch = presetSwatch(index)
    val haptic = rememberHapticFeedback()
    val ringColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(),
        label = "presetRingColor"
    )
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "presetDotScale"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 固定 40dp 外框，选中指示圈固定 40dp，仅颜色变化（不改变布局）
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .border(2.dp, ringColor, CircleShape)
            )
            // 色点：选中时在固定边界内做图形层缩放，不影响布局
            Box(
                Modifier
                    .size(30.dp)
                    .graphicsLayer {
                        scaleX = dotScale
                        scaleY = dotScale
                    }
                    .clip(CircleShape)
                    .background(swatch)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable(enabled = enabled) {
                        haptic.click()
                        onClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(presetNameRes[index]),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(presetNameRes[index]),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 自定义调色板入口（预制色第 8 位）：调色板图标，选中态与预制色点一致 */
@Composable
private fun PaletteLauncherDot(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    val ringColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(),
        label = "paletteRingColor"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .border(2.dp, ringColor, CircleShape)
            )
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable(enabled = enabled) {
                        haptic.click()
                        onClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = stringResource(R.string.settings_cd_custom_palette),
                    tint = if (selected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_custom),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 跳系统浏览器打开链接 */
private fun openInBrowser(context: android.content.Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {}
}

/**
 * 拉起支付宝收款码页。
 * @return true=已成功交给支付宝处理；false=未安装支付宝或拉起失败（不自动跳官网，
 *         由调用方弹窗提示并致谢）。
 * Android 11+ 需在 Manifest 的 <queries> 声明 com.eg.android.AlipayGphone，
 * 否则 resolveActivity 恒为 null。
 */
private fun launchAlipay(context: android.content.Context): Boolean {
    return try {
        val rawUrl = "https://qr.alipay.com/fkx11041w0pjbbnivcyli91"
        val encoded = java.net.URLEncoder.encode(rawUrl, "UTF-8")
        val scheme = "alipays://platformapi/startapp?appId=20000067&url=$encoded"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            false // 未安装支付宝
        } else {
            context.startActivity(intent)
            true
        }
    } catch (_: Exception) {
        false // 拉起失败
    }
}

/** 语言列表中的一行：显示该语言的**自称**名，当前项高亮并在尾部打勾 */
@Composable
private fun LanguageOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 读取 `res/xml/locales_config.xml` 声明的语种清单（与系统「应用语言」入口同源，
 * 当前 119 项）。解析失败时退回 [FALLBACK_LOCALE_TAGS]，保证选择器仍然可用。
 */
private fun loadLocaleTags(context: Context): List<String> {
    val tags = mutableListOf<String>()
    try {
        val parser: XmlResourceParser = context.resources.getXml(R.xml.locales_config)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "locale") {
                parser.getAttributeValue(ANDROID_NS, "name")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { tags += it }
            }
            event = parser.next()
        }
        parser.close()
    } catch (_: Exception) {
        // 资源异常（极端情况）：使用兜底清单
    }
    return tags.ifEmpty { FALLBACK_LOCALE_TAGS }
}

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/** [loadLocaleTags] 解析失败时的兜底（仅主流语种，非全量） */
private val FALLBACK_LOCALE_TAGS = listOf(
    "zh-Hans", "zh-Hant", "en", "ja", "ko", "es", "pt-BR", "fr",
    "de", "ru", "ar", "hi", "id", "tr", "vi", "th"
)
