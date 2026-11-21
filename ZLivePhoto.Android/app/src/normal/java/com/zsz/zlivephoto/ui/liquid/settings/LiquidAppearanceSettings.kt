package com.zsz.zlivephoto.ui.liquid.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.ui.AppSettings
import com.zsz.zlivephoto.ui.liquid.common.LiquidDropdownRow
import com.zsz.zlivephoto.ui.presetHues
import com.zsz.zlivephoto.ui.presetNameRes
import com.zsz.zlivephoto.ui.presetSwatch
import com.zsz.zlivephoto.ui.rememberHapticFeedback
import com.zsz.zlivephoto.ui.swatchForHue
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.systemGray4
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioCard
import io.github.neoruaa.glasmio.ui.GlasmioSectionTitle
import io.github.neoruaa.glasmio.ui.GlasmioSegmentedControl
import io.github.neoruaa.glasmio.ui.GlasmioSlider

/**
 * 外观（一级页内联）：标准版只保留玻璃外壳，因此这里只有两类：
 *
 * - 玻璃分类：液态玻璃外观（通透 / 纯色）；
 * - 主题分类：深浅色模式 + 预制主题色 / 自定义色相。
 *
 * 标准版已移除 MD3 支持、动态取色与「液态玻璃开关」，
 * 不再存在「关闭玻璃后回到 MD3」的分支。
 */
@Composable
internal fun AppearanceSettingsSection() {
    var glassSolid by remember { mutableStateOf(AppSettings.glassSolid) }

    GlassSettingsSection(
        glassSolid = glassSolid,
        onGlassSolidChange = {
            glassSolid = it
            AppSettings.setGlassModeValue(if (it) "solid" else "glass")
        }
    )
    ThemeSettingsSection()
}

/** 液态玻璃分类：玻璃外观（通透 / 纯色）。 */
@Composable
private fun GlassSettingsSection(
    glassSolid: Boolean,
    onGlassSolidChange: (Boolean) -> Unit
) {
    val clearLabel = stringResource(R.string.settings_glass_mode_clear)
    val solidLabel = stringResource(R.string.settings_glass_mode_solid)
    SettingsSection {
        LiquidDropdownRow(
            title = stringResource(R.string.settings_glass_mode_title),
            selected = if (glassSolid) "solid" else "glass",
            options = listOf("glass", "solid"),
            // label 非 @Composable，文案需提前解析
            label = { if (it == "solid") solidLabel else clearLabel },
            onSelected = { onGlassSolidChange(it == "solid") }
        )
    }
}

/** 主题分类：深浅色模式 + 预制主题色 / 自定义色相。 */
@Composable
private fun ThemeSettingsSection() {
    val haptic = rememberHapticFeedback()
    var showCustomPalette by remember { mutableStateOf(false) }

    var themeMode by remember { mutableStateOf(AppSettings.themeMode) }
    var presetIndex by remember { mutableStateOf(AppSettings.presetColor) }
    var customHue by remember { mutableStateOf(AppSettings.customHue) }

    CustomPaletteDialog(
        visible = showCustomPalette,
        onDismiss = { showCustomPalette = false },
        onApply = { hue ->
            customHue = hue
            AppSettings.setCustomHueValue(hue)
        }
    )

    SettingsSection(stringResource(R.string.settings_section_theme)) {
        val modes = listOf("system", "dark", "light")
        // 分段控件自带 Capsule 底与 32dp 固定高度，直接贴边放进 GlasmioGroupedList 会被
        // 外层卡片 26dp 圆角裁掉左右上角（出现缺口）。按分组行内缩 16dp / 上下 10dp 包一层。
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            GlasmioSegmentedControl(
                items = listOf(
                    modes[0] to stringResource(R.string.settings_theme_system),
                    modes[1] to stringResource(R.string.settings_theme_dark),
                    modes[2] to stringResource(R.string.settings_theme_light)
                ),
                selected = modes[modes.indexOf(themeMode).coerceAtLeast(0)],
                onSelected = {
                    themeMode = it
                    AppSettings.setThemeModeValue(it)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    ThemePresetCard(
        enabled = true,
        presetIndex = presetIndex,
        customActive = customHue >= 0f,
        onPickPreset = {
            haptic.click()
            presetIndex = it
            customHue = -1f
            AppSettings.setPresetColorIndex(it)
        },
        onOpenCustom = {
            haptic.click()
            showCustomPalette = true
        }
    )

    Spacer(Modifier.height(18.dp))
}

/** 主题色卡片：4×2 圆点（7 个预制色 + 自定义入口）。 */
@Composable
private fun ThemePresetCard(
    enabled: Boolean,
    presetIndex: Int,
    customActive: Boolean,
    onPickPreset: (Int) -> Unit,
    onOpenCustom: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        GlasmioSectionTitle(stringResource(R.string.settings_preset_colors_title))
        GlasmioCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
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
                                    enabled = enabled,
                                    onClick = onOpenCustom,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                PresetColorDot(
                                    index = idx,
                                    selected = !customActive && presetIndex == idx,
                                    enabled = enabled,
                                    onClick = { onPickPreset(idx) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    if (row == 0) Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

/** 自定义主题色弹窗：任意色相选择（主题色只按色相派生）。 */
@Composable
private fun CustomPaletteDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onApply: (Float) -> Unit
) {
    val haptic = rememberHapticFeedback()
    var hue by remember {
        mutableStateOf(
            if (AppSettings.customHue >= 0f) AppSettings.customHue
            else presetHues[AppSettings.presetColor]
        )
    }
    LaunchedEffect(visible) {
        if (visible) {
            hue = if (AppSettings.customHue >= 0f) AppSettings.customHue
            else presetHues[AppSettings.presetColor]
        }
    }
    GlasmioAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_custom_color_title),
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.settings_btn_cancel),
                role = GlasmioAlertButtonRole.Cancel,
                onClick = {
                    haptic.click()
                    onDismiss()
                }
            ),
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.settings_btn_apply),
                onClick = {
                    haptic.click()
                    onApply(hue)
                    onDismiss()
                }
            )
        )
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(ContinuousRoundedRectangle(12.dp))
                    .background(swatchForHue(hue))
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.settings_hue_value, hue.toInt()),
                style = IosTypography.caption1,
                color = LocalGlassColors.current.secondaryContent
            )
            GlasmioSlider(
                value = hue,
                onValueChange = { hue = it },
                valueRange = 0f..360f
            )
        }
    }
}

/** 预制主题色圆点：选中态 primary 外圈 + 对勾（尺寸固定，避免布局抽动）。 */
@Composable
private fun PresetColorDot(
    index: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swatch = presetSwatch(index)
    val haptic = rememberHapticFeedback()
    // 环与描边统一走玻璃色板：选中环用强调色，描边用卡片自身底色
    val colors = LocalGlassColors.current
    val ringColor by animateColorAsState(
        targetValue = if (selected) colors.accent else Color.Transparent,
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
    // 禁用态仅在此处降一次透明度，圆点内部不再叠加
    Column(
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp).border(2.dp, ringColor, CircleShape))
            Box(
                Modifier
                    .size(30.dp)
                    .graphicsLayer {
                        scaleX = dotScale
                        scaleY = dotScale
                    }
                    .clip(CircleShape)
                    .background(swatch)
                    .border(2.dp, colors.elevatedBackground, CircleShape)
                    .clickable(enabled = enabled) {
                        haptic.click()
                        onClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    SfIcon(
                        systemName = "checkmark",
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
            style = IosTypography.caption2,
            color = if (selected) colors.accent else colors.secondaryContent,
            // 4 列等宽后每列只有约 70dp：西语等长名称若不限行会撑破卡片被裁掉，
            // 这里允许两行 + 居中，长名完整显示、极长名才省略。
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 自定义调色板入口（预制色第 8 位）：调色板图标，选中态与预制色点一致。 */
@Composable
private fun PaletteLauncherDot(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    val colors = LocalGlassColors.current
    val ringColor by animateColorAsState(
        targetValue = if (selected) colors.accent else Color.Transparent,
        animationSpec = spring(),
        label = "paletteRingColor"
    )
    Column(
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(40.dp).border(2.dp, ringColor, CircleShape))
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colors.systemGray4)
                    .border(2.dp, colors.elevatedBackground, CircleShape)
                    .clickable(enabled = enabled) {
                        haptic.click()
                        onClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                SfIcon(
                    systemName = "paintpalette",
                    contentDescription = stringResource(R.string.settings_cd_custom_palette),
                    tint = if (selected) colors.accent else colors.secondaryContent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_custom),
            style = IosTypography.caption2,
            color = if (selected) colors.accent else colors.secondaryContent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
