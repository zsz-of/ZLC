package com.zsz.zlivephoto.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zsz.zlivephoto.BuildConfig

/**
 * 全局应用设置（设置页读写，各处直接观察）：
 * - Compose mutableStateOf 驱动 UI 即时生效（主题/触感/动画开关）
 * - 变更即时写回 SharedPreferences 持久化
 * - MainActivity.onCreate 中先 init() 再 setContent，保证首帧读到正确值
 */
object AppSettings {
    /** 震动反馈（click/soft/longPress/double 全部静默） */
    var hapticsEnabled by mutableStateOf(true)
        private set
    /** 按钮按下的大小弹性（q 弹）动画 */
    var bounceEnabled by mutableStateOf(true)
        private set
    /** Material You 动态取色（Android 12+ 跟随系统壁纸色）；关闭时用预制色 */
    var dynamicTheme by mutableStateOf(true)
        private set
    /** 预制主题色下标（0-6，dynamicTheme=false 时生效） */
    var presetColor by mutableStateOf(0)
        private set
    /** 自定义主题色相（-1f = 未自定义，用预制色）；0..360 为 HSV 色相 */
    var customHue by mutableStateOf(-1f)
        private set
    /** 启动时自动从 GitHub 检查更新 */
    var checkUpdateOnStartup by mutableStateOf(true)
        private set
    /** 深色模式：system（跟随系统）/ dark（强制深色）/ light（强制浅色） */
    var themeMode by mutableStateOf("system")
        private set

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("zlivephoto", Context.MODE_PRIVATE)
        hapticsEnabled = prefs.getBoolean("haptics_enabled", true)
        bounceEnabled = prefs.getBoolean("bounce_enabled", true)
        dynamicTheme = prefs.getBoolean("dynamic_theme", true)
        presetColor = prefs.getInt("preset_color", 0).coerceIn(0, 6)
        customHue = prefs.getFloat("custom_hue", -1f)
        checkUpdateOnStartup = prefs.getBoolean("check_update_startup", true)
        themeMode = prefs.getString("theme_mode", "system") ?: "system"
        // go 轻量版：强制关闭马达反馈与按钮弹性动画（简化版无触感/无 q 弹）
        if (BuildConfig.FLAVOR == "go") {
            hapticsEnabled = false
            bounceEnabled = false
        }
    }

    fun setHaptics(v: Boolean) {
        hapticsEnabled = v
        prefs.edit().putBoolean("haptics_enabled", v).apply()
    }

    fun setBounce(v: Boolean) {
        bounceEnabled = v
        prefs.edit().putBoolean("bounce_enabled", v).apply()
    }

    fun setUseDynamicTheme(v: Boolean) {
        dynamicTheme = v
        prefs.edit().putBoolean("dynamic_theme", v).apply()
    }

    fun setPresetColorIndex(index: Int) {
        presetColor = index.coerceIn(0, 6)
        customHue = -1f // 选预制色时清除自定义色相
        prefs.edit()
            .putInt("preset_color", presetColor)
            .remove("custom_hue")
            .apply()
    }

    fun setCustomHueValue(hue: Float) {
        customHue = hue.coerceIn(0f, 360f)
        prefs.edit().putFloat("custom_hue", customHue).apply()
    }

    fun setStartupUpdateCheck(v: Boolean) {
        checkUpdateOnStartup = v
        prefs.edit().putBoolean("check_update_startup", v).apply()
    }

    fun setThemeModeValue(mode: String) {
        themeMode = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    /** 某个「不再提示」弹窗是否已关闭（true=不再提示） */
    fun isReminderSuppressed(key: ReminderKey): Boolean =
        prefs.getBoolean(key.prefsKey, false)

    /** 设置某个「不再提示」弹窗的抑制状态 */
    fun setReminderSuppressed(key: ReminderKey, suppressed: Boolean) {
        prefs.edit().putBoolean(key.prefsKey, suppressed).apply()
    }
}
