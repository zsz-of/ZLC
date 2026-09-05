package com.zsz.zlivephoto.core

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.zsz.zlivephoto.BuildConfig
import com.zsz.zlivephoto.ui.AppSettings
import com.zsz.zlivephoto.ui.presetHues
import com.zsz.zlivephoto.ui.themeHue
import kotlin.math.abs
import kotlin.math.min

/**
 * 动态桌面图标管理：通过 7 个 activity-alias（对应 7 个预制主题色）切换桌面入口图标。
 *
 * 平台限制：第三方应用无法在运行时直接修改 APK 内已固定的 launcher 图标，
 * 因此采用「预置多套图标 + activity-alias 切换」的方案：
 * - 7 套自适应图标（浅色/深色各一套背景渐变），颜色与 buildScheme 的色相派生一致；
 * - 当前主题色（含自定义色相 / Material You 壁纸色）映射到最近的一个预制色，
 *   启用对应 alias、禁用其余；
 * - 深色/浅色由 drawable / drawable-night 资源系统自动切换，无需代码干预。
 */
object IconManager {
    private const val ALIAS_COUNT = 7

    /** 当前生效主题色对应的最近预制色下标（图标颜色离散，自定义色相就近映射） */
    private fun nearestPresetIndex(hue: Float): Int {
        var best = 0
        var bestDist = Float.MAX_VALUE
        presetHues.forEachIndexed { i, h ->
            val raw = abs(h - hue)
            val dist = min(raw, 360f - raw) // 色相环环形距离
            if (dist < bestDist) {
                bestDist = dist
                best = i
            }
        }
        return best
    }

    /**
     * 当前主题色的实际色相：
     * - Material You 动态取色（Android 12+）开启时，取系统壁纸色（主色/次色/第三色）的色相；
     * - 否则用预制色 / 自定义色相（themeHue）。
     * 壁纸主色偏灰（饱和度极低）时依次尝试次色/第三色，仍全灰或读取失败才回退到预制色，
     * 避免「主色恰好是黑白灰 → 图标颜色不动」的情况。
     */
    private fun effectiveHue(context: Context): Float {
        if (AppSettings.dynamicTheme && BuildConfig.FLAVOR != "go" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val colors = WallpaperManager.getInstance(context)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                if (colors != null) {
                    val candidates = arrayOf(
                        colors.primaryColor,
                        colors.secondaryColor,
                        colors.tertiaryColor
                    )
                    for (c in candidates) {
                        if (c == null) continue
                        val hsv = floatArrayOf(0f, 0f, 0f)
                        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
                        if (hsv[1] > 0.15f) return hsv[0]
                    }
                }
            } catch (_: Exception) {
                // 壁纸色读取失败时回退到预制色
            }
        }
        return themeHue()
    }

    /**
     * 按当前主题色切换桌面图标对应的 activity-alias。
     * 需在应用启动时及主题色（dynamicTheme / presetColor / customHue）变化后调用。
     */
    fun apply(context: Context) {
        // go 轻量版：调整主题色时不切换桌面图标（保持默认图标，进一步简化）
        if (BuildConfig.FLAVOR == "go") return
        val target = nearestPresetIndex(effectiveHue(context))
        val pm = context.packageManager
        for (i in 0 until ALIAS_COUNT) {
            val comp = ComponentName(context.packageName, "${context.packageName}.MainActivityAlias$i")
            val state = if (i == target) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            try {
                pm.setComponentEnabledSetting(comp, state, PackageManager.DONT_KILL_APP)
            } catch (_: Exception) {
                // 某些桌面环境可能暂未注册 alias，忽略单次失败
            }
        }
    }
}
