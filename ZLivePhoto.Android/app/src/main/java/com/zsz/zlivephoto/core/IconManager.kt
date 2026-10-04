package com.zsz.zlivephoto.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.zsz.zlivephoto.BuildConfig
import com.zsz.zlivephoto.ui.currentHue
import com.zsz.zlivephoto.ui.presetHues
import kotlin.math.abs
import kotlin.math.min

/**
 * 动态桌面图标管理：通过 7 个 activity-alias（对应 7 个预制主题色）切换桌面入口图标。
 *
 * 平台限制：第三方应用无法在运行时直接修改 APK 内已固定的 launcher 图标，
 * 因此采用「预置多套图标 + activity-alias 切换」的方案：
 * - 7 套自适应图标（浅色/深色各一套背景渐变），颜色与 buildScheme 的色相派生一致；
 * - 当前主题色相（含动态取色/自定义/预制）就近映射到最近的一个预制色，
 *   启用对应 alias、禁用其余；色相来源统一走 ui.currentHue，与 UI 主题完全同源；
 * - 深色/浅色由 drawable / drawable-night 资源系统自动切换，无需代码干预。
 */
object IconManager {
    private const val ALIAS_COUNT = 7
    private const val TAG = "IconManager"

    /** 当前生效主题色对应的最近预制色下标（图标颜色离散，任意色相就近映射） */
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
     * 按当前主题色切换桌面图标对应的 activity-alias。
     * 需在应用启动时及主题色（dynamicTheme / presetColor / customHue）变化后调用。
     * 色相由 ui.currentHue 提供：动态取色开启时取壁纸色（同 UI 主题同源），
     * 关闭时取预制/自定义色相，保证「开/关动态取色」图标都能与界面同步切换。
     */
    fun apply(context: Context) {
        // go 轻量版：调整主题色时不切换桌面图标（保持默认图标，进一步简化）
        if (BuildConfig.FLAVOR == "go") return
        val target = nearestPresetIndex(currentHue(context))
        val pm = context.packageManager

        // 先启用目标 alias，并把它当作「成败开关」：
        // 旧实现按 0..6 顺序逐条切换，可能 6 条 disable 全部成功、而目标 enable 失败，
        // 结果一个入口都不剩（桌面图标彻底消失）。这里改为目标成功后再清理其余。
        val targetComp = ComponentName(context.packageName, aliasName(context, target))
        try {
            pm.setComponentEnabledSetting(
                targetComp,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (t: Throwable) {
            // 目标启用失败：保持现状（当前入口仍然可用），不做任何 disable。
            // 注意必须兜 Throwable：链接期错误是 Error 而非 Exception。
            Log.w(TAG, "启用桌面入口 alias$target 失败，保持现状", t)
            return
        }

        for (i in 0 until ALIAS_COUNT) {
            // MainActivityAlias0 是清单里的默认 LAUNCHER 入口（android:enabled="true"），
            // 绝大多数用户桌面上固定的就是它。一旦被 disable，已固定的图标会变成
            // 「点不开的死图标」——这正是 issue #1「安装后桌面程序图标失效」的症状路径。
            // 因此 alias0 永不禁用：代价是 target != 0 时应用列表里会多出一个默认色入口，
            // 但保证任何情况下桌面都有可用入口。
            if (i == target || i == 0) continue
            val comp = ComponentName(context.packageName, aliasName(context, i))
            try {
                pm.setComponentEnabledSetting(
                    comp,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (t: Throwable) {
                // 某些桌面环境可能暂未注册 alias，忽略单次失败（但留下日志便于排查）
                Log.w(TAG, "禁用桌面入口 alias$i 失败，忽略", t)
            }
        }
    }

    private fun aliasName(context: Context, i: Int): String =
        "${context.packageName}.MainActivityAlias$i"
}
