package com.zsz.zlivephoto.core

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * 应用语言（每应用语言）读写与生效。
 *
 * 设计：
 * - 选择持久化在 `zlivephoto` SharedPreferences 的 `app_language`（空 = 跟随系统）。
 * - **Android 13+**：走系统 `LocaleManager.setApplicationLocales`（与
 *   `res/xml/locales_config.xml` 声明的 119 个语种配合，系统会自动重建界面）。
 * - **Android 13 以下**：由 [wrap] 在 `Application.attachBaseContext` 里按已保存语言包装
 *   Context，并在 [set] 时 `recreate()` 当前 Activity 使其生效。
 * - 兜底语言是 `res/values/` 的简体中文：设备语言不在 `locales_config.xml` 清单内时回落中文。
 */
object AppLanguage {
    private const val PREFS = "zlivephoto"
    private const val KEY_TAG = "app_language"

    /** 空串 = 跟随系统 */
    fun currentTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, "").orEmpty()

    /** 设置应用语言；tag 为空串表示跟随系统。 */
    fun set(context: Context, tag: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (tag.isEmpty()) {
            prefs.edit().remove(KEY_TAG).apply()
        } else {
            prefs.edit().putString(KEY_TAG, tag).apply()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val locales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(tag)
                context.getSystemService(LocaleManager::class.java)?.applicationLocales = locales
            } catch (_: Exception) {
                // 个别 ROM 缺该服务：退回重建路径
                recreateHost(context)
            }
        } else {
            // 13 以下由 wrap() 生效，需要重建界面
            recreateHost(context)
        }
    }

    /**
     * 找到承载界面的 Activity 并重建。
     * Compose 里的 `LocalContext.current` 通常是 `ContextThemeWrapper`（不是 Activity 本身），
     * 直接 `as? Activity` 会失败导致「切了语言要等下次启动才生效」，这里沿 baseContext 链解开包装。
     */
    private fun recreateHost(context: Context) {
        var ctx: Context? = context
        while (ctx != null) {
            if (ctx is Activity) {
                ctx.recreate()
                return
            }
            ctx = (ctx as? android.content.ContextWrapper)?.baseContext
        }
    }

    /**
     * 语言的**自称**显示名（例如 `zh-Hant` → 「繁體中文」、`bo` → 「བོད་སྐད་」）。
     * tag 为空串时返回系统当前语言的显示名（UI 侧「跟随系统」那一行由界面自己的资源文案负责）。
     */
    fun displayName(tag: String): String {
        val locale = if (tag.isEmpty()) Locale.getDefault() else Locale.forLanguageTag(tag)
        val name = locale.getDisplayName(locale)
        return name.ifEmpty { locale.toLanguageTag() }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /**
     * 供 `Application.attachBaseContext` 调用：按已保存语言包装 base Context。
     * 13 以下靠它让所有 Activity 拿到对应语言的资源；13+ 由系统 LocaleManager 负责，
     * 这里保持一致行为（即使系统未生效也不至于错乱）。
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
            config.setLocales(LocaleList(locale))
            base.createConfigurationContext(config)
        } catch (_: Exception) {
            base
        }
    }
}
