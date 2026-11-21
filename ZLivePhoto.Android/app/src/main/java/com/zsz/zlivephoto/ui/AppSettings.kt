package com.zsz.zlivephoto.ui

import com.zsz.zlivephoto.flavor.FlavorCapabilities
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
    /** 预制主题色下标（0-6） */
    var presetColor by mutableStateOf(0)
        private set
    /** 自定义主题色相（-1f = 未自定义，用预制色）；0..360 为 HSV 色相 */
    var customHue by mutableStateOf(-1f)
        private set
    /** 启动时自动从 GitHub 检查更新 */
    var checkUpdateOnStartup by mutableStateOf(true)
        private set
    /** 按源文件夹层级输出：产物落到 `Pictures/Z-LivePhoto-Converter/<原相册名>/`；
     *  关闭时全部平铺在 `Pictures/Z-LivePhoto-Converter/` 下 */
    var preserveFolders by mutableStateOf(true)
        private set
    /** 替换模式：转换产物直接写回源文件本身（原位覆盖），不在相册生成新文件。
     *  破坏性操作，需「完全存储访问」；首次开启时弹一次风险提示（可勾选不再提示） */
    var replaceMode by mutableStateOf(false)
        private set
    /** 深色模式：system（跟随系统）/ dark（强制深色）/ light（强制浅色） */
    var themeMode by mutableStateOf("system")
        private set
    /** 相册选择器排序字段（AlbumSortField 的 name：DATE / SIZE / NAME），跨次保留 */
    var pickerSortField by mutableStateOf("DATE")
        private set
    /** 相册选择器排序顺序（true=正序） */
    var pickerSortAscending by mutableStateOf(false)
        private set
    /** 相册选择器媒体类型筛选（MediaFilter 的 name：ALL / LIVE / VIDEO / IMAGE） */
    var pickerMediaFilter by mutableStateOf("ALL")
        private set
    /** 用户「跳过此版本」记住的版本号（空串=未跳过）；远端版本与之相同时不再提示更新 */
    var skippedVersion by mutableStateOf("")
        private set
    /** 液态玻璃外观：`glass` = 通透（实时折射/模糊）；`solid` = 纯色（不透明面板） */
    var glassMode by mutableStateOf("glass")
        private set
    /** 通透度 0..1（历史值，保留用于内部玻璃渲染；UI 不再暴露滑条） */
    var glassOpacity by mutableStateOf(0.5f)
        private set

    /** 是否纯色模式（玻璃面板不再采样背景，直接使用主题表面色） */
    val glassSolid: Boolean
        get() = glassMode == "solid"

    /**
     * 全局模糊度 0..1（内部值，UI 已改为「通透 / 纯色」两档，不再提供滑条）。
     * 与 [glassOpacity] 互为反向：glassBlur = 1 - glassOpacity。
     */
    val glassBlur: Float
        get() = 1f - glassOpacity

    /**
     * 液态玻璃是否**实际生效**：标准版恒为玻璃外壳（系统能力 API 31+ 由构建时保证），
     * go 轻量版不使用玻璃组件。
     */
    val glassActive: Boolean
        get() = (!FlavorCapabilities.isGo) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("zlivephoto", Context.MODE_PRIVATE)
        hapticsEnabled = prefs.getBoolean("haptics_enabled", true)
        presetColor = prefs.getInt("preset_color", 0).coerceIn(0, 6)
        customHue = prefs.getFloat("custom_hue", -1f)
        checkUpdateOnStartup = prefs.getBoolean("check_update_startup", true)
        preserveFolders = prefs.getBoolean("preserve_folders", true)
        replaceMode = prefs.getBoolean("replace_mode", false)
        themeMode = prefs.getString("theme_mode", "system") ?: "system"
        pickerSortField = prefs.getString("picker_sort_field", "DATE") ?: "DATE"
        pickerSortAscending = prefs.getBoolean("picker_sort_ascending", false)
        pickerMediaFilter = prefs.getString("picker_media_filter", "ALL") ?: "ALL"
        skippedVersion = prefs.getString("skipped_version", "") ?: ""
        glassMode = prefs.getString("glass_mode", "glass") ?: "glass"
        glassOpacity = prefs.getFloat("glass_opacity", 0.5f).coerceIn(0f, 1f)
    }

    fun setHaptics(v: Boolean) {
        hapticsEnabled = v
        prefs.edit().putBoolean("haptics_enabled", v).apply()
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

    fun setPreserveFolderStructure(v: Boolean) {
        preserveFolders = v
        prefs.edit().putBoolean("preserve_folders", v).apply()
    }

    fun setReplaceModeEnabled(v: Boolean) {
        replaceMode = v
        prefs.edit().putBoolean("replace_mode", v).apply()
    }

    fun setThemeModeValue(mode: String) {
        themeMode = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    /** 记住相册选择器的排序字段 / 顺序 / 媒体筛选：下次打开选择器沿用，不再每次重置 */
    fun setPickerSort(field: String, ascending: Boolean) {
        pickerSortField = field
        pickerSortAscending = ascending
        prefs.edit()
            .putString("picker_sort_field", field)
            .putBoolean("picker_sort_ascending", ascending)
            .apply()
    }

    fun rememberPickerMediaFilter(filter: String) {
        pickerMediaFilter = filter
        prefs.edit().putString("picker_media_filter", filter).apply()
    }

    /** 切换玻璃外观：`glass`（通透）/ `solid`（纯色） */
    fun setGlassModeValue(mode: String) {
        glassMode = if (mode == "solid") "solid" else "glass"
        prefs.edit().putString("glass_mode", glassMode).apply()
    }

    fun setGlassOpacityValue(v: Float) {
        glassOpacity = v.coerceIn(0f, 1f)
        prefs.edit().putFloat("glass_opacity", glassOpacity).apply()
    }

    /** 设置全局模糊度（0=最清透，1=最强磨砂）；内部换算为通透度存储 */
    fun setGlassBlurValue(v: Float) {
        setGlassOpacityValue(1f - v.coerceIn(0f, 1f))
    }

    /** 记住「跳过此版本」：记录版本号，除非发布了更新的版本否则不再提示 */
    fun rememberSkippedVersion(version: String) {
        skippedVersion = version
        prefs.edit().putString("skipped_version", version).apply()
    }

    /** 远端版本是否因被用户跳过而不提示 */
    fun isVersionSkipped(remoteVersion: String): Boolean =
        skippedVersion.isNotEmpty() && skippedVersion == remoteVersion

    /** 某个「不再提示」弹窗是否已关闭（true=不再提示） */
    fun isReminderSuppressed(key: ReminderKey): Boolean =
        prefs.getBoolean(key.prefsKey, false)

    /** 设置某个「不再提示」弹窗的抑制状态 */
    fun setReminderSuppressed(key: ReminderKey, suppressed: Boolean) {
        prefs.edit().putBoolean(key.prefsKey, suppressed).apply()
    }
}
