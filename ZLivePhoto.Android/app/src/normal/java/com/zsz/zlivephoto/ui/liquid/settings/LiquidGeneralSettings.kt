package com.zsz.zlivephoto.ui.liquid.settings

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.flavor.FlavorCapabilities
import com.zsz.zlivephoto.ui.AppSettings
import org.xmlpull.v1.XmlPullParser

/** 通用：启动检查更新、震动反馈。 */
@Composable
internal fun GeneralSettingsSection() {
    val isGo = FlavorCapabilities.isGo
    var checkOnStartup by remember { mutableStateOf(AppSettings.checkUpdateOnStartup) }
    var haptics by remember { mutableStateOf(AppSettings.hapticsEnabled) }

    SettingsSection(stringResource(R.string.settings_section_general)) {
        SwitchRow(
            title = stringResource(R.string.settings_startup_check_title),
            checked = checkOnStartup,
            onToggle = {
                checkOnStartup = it
                AppSettings.setStartupUpdateCheck(it)
            }
        )
        // go 轻量版：无马达反馈，隐藏对应开关
        if (!isGo) {
            SwitchRow(
                title = stringResource(R.string.settings_haptics_title),
                checked = haptics,
                onToggle = {
                    haptics = it
                    AppSettings.setHaptics(it)
                }
            )
        }
    }
}

/**
 * 读取 `res/xml/locales_config.xml` 声明的语种清单（与系统「应用语言」入口同源）。
 * 解析失败时退回 [FALLBACK_LOCALE_TAGS]，保证选择器仍然可用。
 */
internal fun loadLocaleTags(context: Context): List<String> {
    val tags = mutableListOf<String>()
    try {
        val parser: XmlResourceParser = context.resources.getXml(R.xml.locales_config)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "locale") {
                parser.getAttributeValue(ANDROID_NS, "name")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { tags += it }
            }
            event = parser.next()
        }
        parser.close()
    } catch (_: Exception) {
        // 资源异常（极端情况）：使用兜底清单
    }
    return tags.ifEmpty { FALLBACK_LOCALE_TAGS }
}

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/** [loadLocaleTags] 解析失败时的兜底（仅主流语种，非全量） */
private val FALLBACK_LOCALE_TAGS = listOf(
    "zh-Hans", "zh-Hant", "en", "ja", "ko", "es", "pt-BR", "fr",
    "de", "ru", "ar", "hi", "id", "tr", "vi", "th"
)
