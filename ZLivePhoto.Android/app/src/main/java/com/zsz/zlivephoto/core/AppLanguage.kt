package com.zsz.zlivephoto.core

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 应用语言（每应用语言）读写与生效。
 *
 * 设计：
 * - 选择持久化在 `zlivephoto` SharedPreferences 的 `app_language`（空 = 跟随系统）。
 * - **运行期切换不重建界面**：Compose 根壳的 `LanguageHost` 观察 [runtimeTag]，
 *   在菜单收起后把整页文字淡出/镜像后换成新语言（见 ui/LanguageHost.kt）；
 *   Activity 不再 `recreate()`，页面滚动位置、选中状态都保持不变。
 * - **冷启动**：由 [wrap] 在 `Application/Activity.attachBaseContext` 按已保存语言包装 Context。
 * - 兜底语言是 `res/values/` 的简体中文：设备语言不在 `locales_config.xml` 清单内时回落中文。
 */
object AppLanguage {
    private const val PREFS = "zlivephoto"
    private const val KEY_TAG = "app_language"

    /**
     * 运行期语言（Compose 可观察）。`null` = 尚未初始化，由 LanguageHost 用已保存值填充。
     * 空串表示「跟随系统」。
     */
    var runtimeTag: String? by mutableStateOf(null)
        private set

    /** 空串 = 跟随系统 */
    fun currentTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, "").orEmpty()

    /**
     * 设置应用语言；tag 为空串表示跟随系统。
     *
     * 只写偏好 + 更新 [runtimeTag]，**不调用 LocaleManager、不重建 Activity**：
     * 界面文字由 LanguageHost 在一段淡出/镜像动画后原地切换。
     */
    fun set(context: Context, tag: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (tag.isEmpty()) {
            prefs.edit().remove(KEY_TAG).apply()
        } else {
            prefs.edit().putString(KEY_TAG, tag).apply()
        }
        runtimeTag = tag
    }

    /** 语言的**自称**显示名（例如 `zh-Hant` → 「繁體中文」、`bo` → 「བོད་སྐད་」）。 */
    fun displayName(tag: String): String {
        // 文言（lzh）在部分系统 / 部分界面语言下 ICU 取不到本地化名称，
        // 会直接回落成代码「Lzh」（用户反馈）。语言列表一律显示各语言的自称名，
        // 这里直接给出「文言」。
        if (tag.equals("lzh", ignoreCase = true)) return "文言"
        val locale = if (tag.isEmpty()) Locale.getDefault() else Locale.forLanguageTag(tag)
        val name = locale.getDisplayName(locale)
        return name.ifEmpty { locale.toLanguageTag() }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /**
     * 供 `Application.attachBaseContext` / `Activity.attachBaseContext` 调用：按已保存语言
     * 包装 base Context。13 以下靠它让 Activity 拿到对应语言的资源；13+ 由系统
     * LocaleManager 负责，这里保持一致行为（即使系统未生效也不至于错乱）。
     *
     * ⚠️ 必须同时挂在 Application 和每个 Activity 上：只包装 Application 不会改变
     * Activity 的资源解析（系统为 Activity 单独创建 base Context），这正是
     * Go 轻量版（面向 Android 6–12）切语言无效的根因。
     */
    fun wrap(base: Context): Context {
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, "").orEmpty()
        if (tag.isEmpty()) return base
        return try {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            // setLocales / LocaleList 自 API 24 才有；Go 版 minSdk 23（Android 6.0），
            // 不加守卫会在真机上抛 NoSuchMethodError——它是 Error，下面的 catch 接不住
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocales(LocaleList(locale))
            }
            base.createConfigurationContext(config)
        } catch (_: Exception) {
            base
        }
    }
}
