package com.zsz.zlivephoto.ui

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.zsz.zlivephoto.BuildConfig
import com.zsz.zlivephoto.R

/**
 * 应用当前是否处于「深色」主题（由「深色模式」三选 + 系统深浅色共同决定）。
 *
 * 为什么需要它：玻璃/选择器组件此前一律用 `isSystemInDarkTheme()`（读系统 uiMode），
 * 但用户可以在应用内手动选「深色/浅色」，此时系统可能仍是浅色，导致
 * `ZLivePhotoTheme` 已切成深色、玻璃组件却仍按浅色渲染 —— 白色背景板不变黑、
 * 玻璃模糊出白底的根因。统一从这里读，保证与主题同源。
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

// ImageToolBox 风格的颜色方案
private val LightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF0B0B0D),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFF08080A),
    surfaceContainerLow = Color(0xFF121214),
    surfaceContainer = Color(0xFF18181B),
    surfaceContainerHigh = Color(0xFF232326),
    surfaceContainerHighest = Color(0xFF2E2E32),
)

// ---------- 预制主题色（7 个，色相派生）+ 自定义色相 ----------
// 对基线紫方案做 HSV 色相替换：所有角色颜色换成所选色相，饱和度/明度档位
// 保持 MD3 原版的对比度结构，light/dark 两套各自适配深浅模式。
// 红色系（error/错误文字）不参与色相替换，保持警示语义。
// 第 8 位固定留给「自定义调色板」，不在此列表内。
internal val presetHues = floatArrayOf(
    262f, // 紫罗兰（默认，接近原版基线）
    222f, // 海洋蓝
    195f, // 青碧
    150f, // 森绿
    105f, // 橄榄
    45f,  // 琥珀
    25f   // 落日橙
)

internal val presetNameRes = intArrayOf(
    R.string.settings_preset_violet,
    R.string.settings_preset_ocean,
    R.string.settings_preset_teal,
    R.string.settings_preset_forest,
    R.string.settings_preset_olive,
    R.string.settings_preset_amber,
    R.string.settings_preset_sunset
)

/** 把颜色替换为指定色相（保留原饱和度/明度） */
private fun shiftHue(c: Color, hue: Float): Color {
    val hsv = floatArrayOf(0f, 0f, 0f)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    hsv[0] = hue
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** 当前生效色相：自定义色相优先，否则取预制色下标对应色相 */
internal fun themeHue(): Float =
    if (AppSettings.customHue >= 0f) AppSettings.customHue
    else presetHues[AppSettings.presetColor.coerceIn(0, presetHues.size - 1)]

/**
 * 动态取色（Android 12+）时从系统壁纸色提取主题色相：
 * 依次取主色/次色/第三色中第一个饱和度足够的颜色（hsv[1] > 0.15）的色相，
 * 全灰或读取失败返回 null（调用方回退到预制/自定义色）。
 * 与 Material3 的 dynamicLight/Dark 色板不同，这里只提取「单一色相」，
 * 让动态取色走与自定义调色板完全相同的 buildScheme 派生管线，
 * 保证应用内 UI 与桌面图标（就近映射 7 个预制色）始终同源同步。
 */
internal fun wallpaperHue(context: Context): Float? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return try {
        val colors = WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return null
        for (c in arrayOf(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor)) {
            if (c == null) continue
            val hsv = floatArrayOf(0f, 0f, 0f)
            android.graphics.Color.colorToHSV(c.toArgb(), hsv)
            if (hsv[1] > 0.15f) return hsv[0]
        }
        null
    } catch (_: Exception) {
        null
    }
}

/**
 * 当前实际生效的主题色相（UI 主题与桌面图标共用的单一来源）：
 * - 动态取色开启（非 go、Android 12+）：优先用缓存的 [AppSettings.liveDynamicHue]
 *   （壁纸变化/开关切换时更新，值变更会驱动 UI 重组）；缓存未就绪时直接读一次壁纸。
 * - 否则用预制色/自定义色相（themeHue）。
 */
internal fun currentHue(context: Context): Float {
    val dynamic = AppSettings.dynamicTheme &&
        BuildConfig.FLAVOR != "go" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    if (dynamic) {
        val cached = AppSettings.liveDynamicHue
        if (cached >= 0f) return cached
        return wallpaperHue(context) ?: themeHue()
    }
    return themeHue()
}

/** 预制色圆点（设置页选择器展示用）：该色相下 primary 的视觉近似 */
internal fun presetSwatch(index: Int): Color =
    shiftHue(LightColors.primary, presetHues[index.coerceIn(0, presetHues.size - 1)])

/** 指定色相的主题色预览（自定义调色板实时预览用） */
internal fun swatchForHue(hue: Float): Color =
    shiftHue(LightColors.primary, hue.coerceIn(0f, 360f))

/** 按色相生成 ColorScheme（对基线方案整体替换色相，error 保持不变） */
internal fun buildScheme(hue: Float, dark: Boolean): ColorScheme {
    val base = if (dark) DarkColors else LightColors
    return base.copy(
        primary = shiftHue(base.primary, hue),
        onPrimary = base.onPrimary,
        primaryContainer = shiftHue(base.primaryContainer, hue),
        onPrimaryContainer = shiftHue(base.onPrimaryContainer, hue),
        secondary = shiftHue(base.secondary, hue),
        onSecondary = base.onSecondary,
        secondaryContainer = shiftHue(base.secondaryContainer, hue),
        onSecondaryContainer = shiftHue(base.onSecondaryContainer, hue),
        tertiary = shiftHue(base.tertiary, hue),
        onTertiary = base.onTertiary,
        tertiaryContainer = shiftHue(base.tertiaryContainer, hue),
        onTertiaryContainer = shiftHue(base.onTertiaryContainer, hue),
        surfaceVariant = shiftHue(base.surfaceVariant, hue),
        onSurfaceVariant = base.onSurfaceVariant,
        outline = shiftHue(base.outline, hue),
        outlineVariant = shiftHue(base.outlineVariant, hue),
        surfaceTint = shiftHue(base.surfaceTint, hue),
        inversePrimary = shiftHue(base.inversePrimary, hue),
        surfaceContainerLowest = shiftHue(base.surfaceContainerLowest, hue),
        surfaceContainerLow = shiftHue(base.surfaceContainerLow, hue),
        surfaceContainer = shiftHue(base.surfaceContainer, hue),
        surfaceContainerHigh = shiftHue(base.surfaceContainerHigh, hue),
        surfaceContainerHighest = shiftHue(base.surfaceContainerHighest, hue)
    )
}

@Composable
fun ZLivePhotoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // 动态取色（Material You，Android 12+）开启时跟随系统壁纸色板：
    // 直接用系统动态色板（dynamicLight/DarkColorScheme），壁纸差异在界面上有
    // 明显可感知的变化；关闭时回落到 buildScheme 的预制/自定义色相（themeHue）。
    // 这里始终读取一次 currentHue——它是 AppSettings.liveDynamicHue 等状态在
    // 组合期的读点，动态取色开关 / 壁纸颜色变化时驱动本主题重组、重建动态色板。
    val hue = currentHue(context)
    val target = when {
        AppSettings.dynamicTheme && BuildConfig.FLAVOR != "go" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> buildScheme(hue, dark = true)
        else -> buildScheme(hue, dark = false)
    }
    // 深色/浅色切换时逐色过渡（500ms），避免 Activity 重建瞬变
    val colorScheme = animateColorScheme(target)

    MaterialTheme(
        colorScheme = colorScheme,
        content = {
            CompositionLocalProvider(
                // MaterialTheme 自身**不**提供 LocalContentColor（只提供 colorScheme /
                // shapes / typography 等 6 个 local），根作用域的默认内容色是
                // Color.Black。玻璃模式下 Scaffold/Surface 的容器色一律传 Transparent，
                // 而 Scaffold 的 contentColor 默认值走 contentColorFor(Transparent) =
                // Unspecified → 回退到根作用域的黑色 → 深色模式下所有未显式着色的
                // 文字（设置页行标题 / 主页大标题 / 底部面板文字…）一律渲染成黑色，
                // 与深色背景融为一体（用户反馈「深色模式下部分字体为黑色」）。
                // 在主题根统一提供随主题 500ms 渐变的 onSurface，全树默认文字色即修正。
                LocalContentColor provides colorScheme.onSurface,
                LocalDarkTheme provides darkTheme
            ) {
                content()
            }
        }
    )
}

/** 对 ColorScheme 全部颜色做动画插值，实现主题切换的平滑过渡（ImageToolbox 式） */
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(500)

    @Composable
    fun anim(c: Color): Color = animateColorAsState(targetValue = c, animationSpec = spec, label = "schemeColor").value

    return target.copy(
        primary = anim(target.primary),
        onPrimary = anim(target.onPrimary),
        primaryContainer = anim(target.primaryContainer),
        onPrimaryContainer = anim(target.onPrimaryContainer),
        inversePrimary = anim(target.inversePrimary),
        secondary = anim(target.secondary),
        onSecondary = anim(target.onSecondary),
        secondaryContainer = anim(target.secondaryContainer),
        onSecondaryContainer = anim(target.onSecondaryContainer),
        tertiary = anim(target.tertiary),
        onTertiary = anim(target.onTertiary),
        tertiaryContainer = anim(target.tertiaryContainer),
        onTertiaryContainer = anim(target.onTertiaryContainer),
        background = anim(target.background),
        onBackground = anim(target.onBackground),
        surface = anim(target.surface),
        onSurface = anim(target.onSurface),
        surfaceVariant = anim(target.surfaceVariant),
        onSurfaceVariant = anim(target.onSurfaceVariant),
        surfaceTint = anim(target.surfaceTint),
        inverseSurface = anim(target.inverseSurface),
        inverseOnSurface = anim(target.inverseOnSurface),
        error = anim(target.error),
        onError = anim(target.onError),
        errorContainer = anim(target.errorContainer),
        onErrorContainer = anim(target.onErrorContainer),
        outline = anim(target.outline),
        outlineVariant = anim(target.outlineVariant),
        scrim = anim(target.scrim),
        surfaceBright = anim(target.surfaceBright),
        surfaceDim = anim(target.surfaceDim),
        surfaceContainer = anim(target.surfaceContainer),
        surfaceContainerHigh = anim(target.surfaceContainerHigh),
        surfaceContainerHighest = anim(target.surfaceContainerHighest),
        surfaceContainerLow = anim(target.surfaceContainerLow),
        surfaceContainerLowest = anim(target.surfaceContainerLowest)
    )
}
