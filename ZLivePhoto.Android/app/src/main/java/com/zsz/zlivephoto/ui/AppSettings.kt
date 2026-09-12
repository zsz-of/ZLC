package com.zsz.zlivephoto.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
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
    /** 动态取色当前跟随的壁纸主题色相（0..360；-1f=尚未取得/动态关闭，回退静态色）。
     *  仅内存态（不持久化）：供 UI 主题与桌面图标共用同一色相来源，保证二者始终同步。 */
    var liveDynamicHue by mutableStateOf(-1f)
        private set
    /** 启动时自动从 GitHub 检查更新 */
    var checkUpdateOnStartup by mutableStateOf(true)
        private set
    /** 深色模式：system（跟随系统）/ dark（强制深色）/ light（强制浅色） */
    var themeMode by mutableStateOf("system")
        private set
    /** 用户「跳过此版本」记住的版本号（空串=未跳过）；远端版本与之相同时不再提示更新 */
    var skippedVersion by mutableStateOf("")
        private set
    /** 用户「跳过此版本」记住的转码器（编码器）版本号（空串=未跳过）；与自动检查到的编码器版本相同时不再提示 */
    var skippedAddonVersion by mutableStateOf("")
        private set

    private lateinit var prefs: SharedPreferences
    private lateinit var appCtx: Context

    fun init(context: Context) {
        prefs = context.getSharedPreferences("zlivephoto", Context.MODE_PRIVATE)
        appCtx = context.applicationContext
        hapticsEnabled = prefs.getBoolean("haptics_enabled", true)
        bounceEnabled = prefs.getBoolean("bounce_enabled", true)
        dynamicTheme = prefs.getBoolean("dynamic_theme", true)
        presetColor = prefs.getInt("preset_color", 0).coerceIn(0, 6)
        customHue = prefs.getFloat("custom_hue", -1f)
        checkUpdateOnStartup = prefs.getBoolean("check_update_startup", true)
        themeMode = prefs.getString("theme_mode", "system") ?: "system"
        skippedVersion = prefs.getString("skipped_version", "") ?: ""
        skippedAddonVersion = prefs.getString("skipped_addon_version", "") ?: ""
        // go 轻量版：强制关闭马达反馈与按钮弹性动画（简化版无触感/无 q 弹）
        if (BuildConfig.FLAVOR == "go") {
            hapticsEnabled = false
            bounceEnabled = false
        }
        // 启动即同步一次动态取色的壁纸色相，保证首帧主题与图标都落到正确色
        syncDynamicHue()
    }

    /**
     * 动态取色开关或启动后同步内存态 [liveDynamicHue]（UI 主题与桌面图标共用的
     * 单一色相来源）。与自定义取色写 [customHue] 一样在改动发生的同一调用栈内
     * 同步完成 —— 退出到桌面时 onStop 的图标同步必然读到最新值，
     * 不会出现「开启动态取色后图标仍停在旧色」的窗口期。
     */
    private fun syncDynamicHue() {
        val dynamic = dynamicTheme &&
            BuildConfig.FLAVOR != "go" &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        liveDynamicHue = if (dynamic) wallpaperHue(appCtx) ?: -1f else -1f
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
        // 与自定义取色 setCustomHueValue 同一模式：改动当下就同步好共享色相，
        // 让 UI 主题与随后 onStop 的桌面图标同步都基于最新值（开/关都即时生效）。
        syncDynamicHue()
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

    /** 更新动态取色跟随的壁纸色相（-1f = 清除/回退静态色；仅内存，不写盘） */
    fun updateLiveDynamicHue(hue: Float) {
        liveDynamicHue = hue.coerceIn(-1f, 360f)
    }

    fun setStartupUpdateCheck(v: Boolean) {
        checkUpdateOnStartup = v
        prefs.edit().putBoolean("check_update_startup", v).apply()
    }

    fun setThemeModeValue(mode: String) {
        themeMode = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    /** 记住「跳过此版本」：记录版本号，除非发布了更新的版本否则不再提示 */
    fun rememberSkippedVersion(version: String) {
        skippedVersion = version
        prefs.edit().putString("skipped_version", version).apply()
    }

    /** 远端版本是否因被用户跳过而不提示 */
    fun isVersionSkipped(remoteVersion: String): Boolean =
        skippedVersion.isNotEmpty() && skippedVersion == remoteVersion

    /** 记住「跳过此转码器版本」：记录编码器版本号，除非发布了更新的编码器版本否则不再提示 */
    fun rememberSkippedAddonVersion(version: String) {
        skippedAddonVersion = version
        prefs.edit().putString("skipped_addon_version", version).apply()
    }

    /** 自动检查到的编码器版本是否因被用户跳过而不提示 */
    fun isAddonVersionSkipped(remoteVersion: String): Boolean =
        skippedAddonVersion.isNotEmpty() && skippedAddonVersion == remoteVersion

    /** 某个「不再提示」弹窗是否已关闭（true=不再提示） */
    fun isReminderSuppressed(key: ReminderKey): Boolean =
        prefs.getBoolean(key.prefsKey, false)

    /** 设置某个「不再提示」弹窗的抑制状态 */
    fun setReminderSuppressed(key: ReminderKey, suppressed: Boolean) {
        prefs.edit().putBoolean(key.prefsKey, suppressed).apply()
    }
}
