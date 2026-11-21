package com.zsz.zlivephoto.ui.liquid.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.AppLanguage
import com.zsz.zlivephoto.ui.AppSettings
import com.zsz.zlivephoto.ui.ReminderKey
import com.zsz.zlivephoto.ui.liquid.common.LiquidDropdownRow
import com.zsz.zlivephoto.ui.rememberHapticFeedback
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioGroupedTableFooter
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import io.github.neoruaa.glasmio.ui.GlasmioSectionTitle
import io.github.neoruaa.glasmio.ui.GlasmioSwitch

/** 语言：下拉直接切换，「跟随系统」外一律显示各语言的自称名（不随界面语言变化）。 */
@Composable
internal fun LanguageSettingsSection() {
    val context = LocalContext.current
    val followSystem = stringResource(R.string.app_language_follow_system)
    var languageTag by remember { mutableStateOf(AppLanguage.currentTag(context)) }
    val localeTags = remember(context) { loadLocaleTags(context) }
    val options = listOf("") + localeTags

    SettingsSection(stringResource(R.string.settings_section_language)) {
        LiquidDropdownRow(
            title = stringResource(R.string.app_language_title),
            selected = languageTag,
            options = options,
            label = { tag ->
                if (tag.isEmpty()) followSystem else AppLanguage.displayName(tag)
            },
            onSelected = { tag ->
                languageTag = tag
                AppLanguage.set(context, tag)
            }
        )
    }
}

/** 「不再提示弹窗」入口（完整清单在子页）。 */
@Composable
internal fun ReminderEntrySection(onOpenReminders: () -> Unit) {
    val haptic = rememberHapticFeedback()
    SettingsSection(stringResource(R.string.settings_section_defaults)) {
        GlasmioListRow(
            title = stringResource(R.string.settings_reminder_dialogs_title),
            onClick = {
                haptic.click()
                onOpenReminders()
            }
        )
    }
}

/**
 * 「默认选项」子页：按分类列出所有带「不再提示」的弹窗。
 * 每条右侧开关：开 = 不再提示；关 = 下次触发仍弹窗。
 */
@Composable
internal fun ReminderSettingsPage(
    onBack: () -> Unit,
    embedded: Boolean = false,
    /** 非空：顶栏由设置宿主统一绘制（二级页切换时返回钮不动） */
    hostContentPadding: PaddingValues? = null
) {
    SettingsPage(
        title = stringResource(R.string.settings_reminder_dialogs_title),
        onBack = onBack,
        embedded = embedded,
        hostContentPadding = hostContentPadding
    ) {
        for ((category, keys) in ReminderKey.entries.groupBy { it.category }) {
            GlasmioSectionTitle(category)
            GlasmioGroupedList {
                keys.forEach { key ->
                    ReminderSettingRow(key = key)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        GlasmioGroupedTableFooter(stringResource(R.string.settings_reminder_hint))
    }
}

/** 单条「不再提示」设置行：标题/摘要 + 开关。 */
@Composable
private fun ReminderSettingRow(key: ReminderKey) {
    val haptic = rememberHapticFeedback()
    // 本地 state 驱动开关即时重绘（AppSettings 持久化值不触发重组）
    var checked by remember(key) { mutableStateOf(AppSettings.isReminderSuppressed(key)) }
    val toggle: (Boolean) -> Unit = { v ->
        if (v) haptic.click() else haptic.soft()
        checked = v
        AppSettings.setReminderSuppressed(key, v)
    }
    GlasmioListRow(
        title = key.title,
        subtitle = key.message.replace('\n', ' '),
        onClick = { toggle(!checked) },
        trailing = {
            GlasmioSwitch(checked = checked, onCheckedChange = toggle)
        }
    )
}
