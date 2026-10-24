package com.zsz.zlivephoto.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
// matchParentSize 是 BoxScope 的成员扩展——导入容器接口才能用
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.zsz.zlivephoto.BuildConfig
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.ui.glass.GlassFusionScope
import com.zsz.zlivephoto.ui.glass.LiquidSegmentedTabs
import com.zsz.zlivephoto.ui.glass.LocalGlassBackdrop
import com.zsz.zlivephoto.ui.glass.LocalGlassEnabled
import com.zsz.zlivephoto.ui.glass.LocalGlassRootBackdrop
import com.zsz.zlivephoto.ui.glass.catalog.components.LiquidButton
import com.zsz.zlivephoto.ui.glass.catalog.components.LiquidSlider
import com.zsz.zlivephoto.ui.glass.catalog.components.LiquidToggle
import com.zsz.zlivephoto.ui.glass.liquidGlass

/**
 * 首次启动 / 升级完成后的引导（两页）：
 * ① 界面风格：液态玻璃 vs 原生界面——两张卡片**并排、同态、可试用**的鲜明对比，
 *    ​卡片各自强制渲染一种风格（不受当前设置影响），选中哪张就应用哪种；
 * ② 深浅色：跟随系统 / 浅色 / 深色三选，切换后整棵树立刻换主题（所见即所得）。
 *
 * 完成后 [onFinish] 交由 Activity 切回正常页面栈。
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    // 设备不支持 RenderEffect（API<31）或 go 轻量版：没有玻璃可选，直接进入深浅色页
    val glassSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        BuildConfig.FLAVOR != "go"
    var page by remember { mutableIntStateOf(if (glassSupported) 0 else 1) }
    // 「上了玻璃样式的车」：根采样层存在（用户选了玻璃且设备支持）时才用玻璃件
    val rootBackdrop = LocalGlassRootBackdrop.current
    val styleGlass = rootBackdrop != null
    // 预览控件共享状态：两块卡片联动——拖任意一侧，另一侧同步，差异一眼可辨
    var previewChecked by remember { mutableStateOf(true) }
    var previewProgress by remember { mutableFloatStateOf(0.45f) }
    val haptic = rememberHapticFeedback()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 步骤指示（两颗药丸）
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(2) { i ->
                Box(
                    Modifier
                        .size(width = 22.dp, height = 6.dp)
                        .background(
                            color = if (i == page) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(320)) { it / 4 * dir } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(320)) { -it / 4 * dir } + fadeOut(tween(180)))
            },
            label = "onboardingPage"
        ) { p ->
            if (p == 0) {
                StylePage(
                    styleGlass = AppSettings.glassEnabled,
                    onPick = { glass ->
                        haptic.click()
                        AppSettings.setGlassUIEnabled(glass)
                    },
                    previewChecked = previewChecked,
                    onPreviewChecked = { previewChecked = it },
                    previewProgress = previewProgress,
                    onPreviewProgress = { previewProgress = it }
                )
            } else {
                ThemePage(
                    glass = styleGlass,
                    backdrop = rootBackdrop,
                    previewChecked = previewChecked,
                    onPreviewChecked = { previewChecked = it },
                    previewProgress = previewProgress,
                    onPreviewProgress = { previewProgress = it }
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        // 底部导航：上一步（仅第二页） / 下一步 · 开始使用
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (page > 0) {
                OnboardingTextButton(
                    text = stringResource(R.string.onboarding_prev),
                    glass = styleGlass,
                    backdrop = rootBackdrop,
                    onClick = { haptic.click(); page = 0 }
                )
            }
            Spacer(Modifier.weight(1f))
            OnboardingPrimaryButton(
                text = if (page == 0) stringResource(R.string.onboarding_next)
                else stringResource(R.string.onboarding_finish),
                glass = styleGlass,
                backdrop = rootBackdrop,
                onClick = {
                    haptic.click()
                    if (page == 0) page = 1
                    else {
                        // 深浅色已在页面内即时写入；这里只记录「本版本已引导」
                        AppSettings.markOnboarded()
                        onFinish()
                    }
                }
            )
        }
    }
}

/** 风格页：两张并排卡片，一张恒为液态玻璃、一张恒为原生 MD3（对比不受当前选择影响） */
@Composable
private fun StylePage(
    styleGlass: Boolean,
    onPick: (Boolean) -> Unit,
    previewChecked: Boolean,
    onPreviewChecked: (Boolean) -> Unit,
    previewProgress: Float,
    onPreviewProgress: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.onboarding_style_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.onboarding_style_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onboarding_style_try),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PreviewCard(
                glass = true,
                title = stringResource(R.string.onboarding_style_glass),
                selected = styleGlass,
                onClick = { onPick(true) },
                previewChecked = previewChecked,
                onPreviewChecked = onPreviewChecked,
                previewProgress = previewProgress,
                onPreviewProgress = onPreviewProgress,
                modifier = Modifier.weight(1f)
            )
            PreviewCard(
                glass = false,
                title = stringResource(R.string.onboarding_style_md3),
                selected = !styleGlass,
                onClick = { onPick(false) },
                previewChecked = previewChecked,
                onPreviewChecked = onPreviewChecked,
                previewProgress = previewProgress,
                onPreviewProgress = onPreviewProgress,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 深浅色页：三选（跟随系统 / 浅色 / 深色）+ 所选风格在当前主题下的实时预览卡 */
@Composable
private fun ThemePage(
    glass: Boolean,
    backdrop: Backdrop?,
    previewChecked: Boolean,
    onPreviewChecked: (Boolean) -> Unit,
    previewProgress: Float,
    onPreviewProgress: (Float) -> Unit
) {
    val modes = listOf("system", "dark", "light")
    val labels = listOf(
        stringResource(R.string.settings_theme_system),
        stringResource(R.string.settings_theme_dark),
        stringResource(R.string.settings_theme_light)
    )
    val haptic = rememberHapticFeedback()
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.onboarding_theme_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.onboarding_theme_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        LiquidSegmentedTabs(
            labels = labels,
            selectedIndex = modes.indexOf(AppSettings.themeMode).coerceAtLeast(0),
            onSelect = {
                haptic.click()
                // 写入设置 → Activity 的 LaunchedEffect(themeMode) 立刻换主题，
                // 整页（含下方预览卡）即时呈现新主题
                AppSettings.setThemeModeValue(modes[it])
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        PreviewCard(
            glass = glass,
            title = stringResource(R.string.onboarding_preview_title),
            selected = true,
            onClick = null,
            previewChecked = previewChecked,
            onPreviewChecked = onPreviewChecked,
            previewProgress = previewProgress,
            onPreviewProgress = onPreviewProgress,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 风格预览卡：卡片外观与内部控件都按 [glass] **强制**渲染一种风格，
 * 与当前设置无关——这样无论选中哪一项，左右/上下的对比都始终鲜明。
 */
@Composable
private fun PreviewCard(
    glass: Boolean,
    title: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    previewChecked: Boolean,
    onPreviewChecked: (Boolean) -> Unit,
    previewProgress: Float,
    onPreviewProgress: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    // 卡片自身的采样源：卡片独立记录一条渐变，内部控件折射它——
    // 不依赖根采样层，因此用户选了 MD3（根层不提供采样源）时玻璃卡片照样是玻璃
    val cardBackdrop = rememberLayerBackdrop()
    val cs = MaterialTheme.colorScheme
    // 每张预览卡是独立界面层：卡内玻璃互相连接，两张卡之间不跨越融合
    GlassFusionScope {
    Box(modifier) {
        if (glass) {
            // matchParentSize（而非 fillMaxSize）：卡片整体在 verticalScroll 内，
            // fillMax 会在无界高度约束下撑成无限高；matchParentSize 不参与
            // 父节点测高，卡片高度仍由内容决定
            Box(
                Modifier
                    .matchParentSize()
                    .layerBackdrop(cardBackdrop)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                cs.primaryContainer,
                                cs.tertiaryContainer,
                                cs.secondaryContainer
                            )
                        )
                    )
            )
        }
        CompositionLocalProvider(
            LocalGlassEnabled provides glass,
            LocalGlassBackdrop provides if (glass) cardBackdrop else null,
            LocalGlassRootBackdrop provides if (glass) cardBackdrop else null
        ) {
            // 表面必须在 CompositionLocal 内构造：liquidGlass 是 composed 修饰符，
            // 在构造时就读 LocalGlassEnabled
            val surface = if (glass) {
                Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = shape,
                        backdropOverride = cardBackdrop,
                        // 卡片要透出底下的渐变，玻璃偏清透（后续可在设置里调通透度）
                        tintAlpha = 0.10f
                    )
            } else {
                Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceContainer, shape)
            }
            val selectedRing = if (selected && onClick != null) {
                Modifier.border(2.dp, cs.primary, shape)
            } else Modifier
            Box(
                surface
                    .then(selectedRing)
                    .then(
                        if (onClick != null) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onClick() }
                        else Modifier
                    )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            color = cs.onSurface,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected && onClick != null) {
                            // 选中态：实心小圆点（与分段控件的选中语言一致）
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(cs.primary, CircleShape)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // ── 开关 ──
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.onboarding_preview_switch),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        if (glass) {
                            LiquidToggle(
                                selected = { previewChecked },
                                onSelect = onPreviewChecked,
                                backdrop = cardBackdrop
                            )
                        } else {
                            Switch(
                                checked = previewChecked,
                                onCheckedChange = onPreviewChecked
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    // ── 滑条 ──
                    Text(
                        stringResource(R.string.onboarding_preview_slider),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                    if (glass) {
                        LiquidSlider(
                            value = { previewProgress },
                            onValueChange = onPreviewProgress,
                            valueRange = 0f..1f,
                            visibilityThreshold = 0.001f,
                            backdrop = cardBackdrop,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Slider(
                            value = previewProgress,
                            onValueChange = onPreviewProgress,
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // ── 按钮 ──
                    if (glass) {
                        LiquidButton(
                            onClick = {},
                            backdrop = cardBackdrop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Text(
                                stringResource(R.string.onboarding_preview_button),
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onSurface,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Text(
                                stringResource(R.string.onboarding_preview_button),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

/** 底部按钮：跟随所选风格（玻璃 = 液态玻璃按钮，原生 = MD3 按钮） */
@Composable
private fun OnboardingPrimaryButton(
    text: String,
    glass: Boolean,
    backdrop: Backdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (glass && backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier.height(44.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
        }
    } else {
        FilledTonalButton(onClick = onClick, modifier = modifier.height(44.dp)) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun OnboardingTextButton(
    text: String,
    glass: Boolean,
    backdrop: Backdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 文字按钮：玻璃态下同样使用液态玻璃按钮（低 tint），保持整体语言一致
    if (glass && backdrop != null) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            tint = Color.Transparent,
            modifier = modifier.height(44.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }
    } else {
        TextButton(onClick = onClick, modifier = modifier.height(44.dp)) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
