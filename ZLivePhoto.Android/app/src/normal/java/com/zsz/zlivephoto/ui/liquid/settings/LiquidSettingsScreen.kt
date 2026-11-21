package com.zsz.zlivephoto.ui.liquid.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.ui.liquid.common.LiquidSlideOverPage
import com.zsz.zlivephoto.ui.liquid.sponsor.SponsorEntrySection

/** 设置页层级：一级平铺，提醒 / 关于为二级页（外观已放回一级页）。 */
private enum class SettingsSubpage(val depth: Int) {
    ROOT(0),
    REMINDERS(1),
    ABOUT(1)
}

/**
 * 设置页：
 * - 一级：赞助（置顶）→ 外观（玻璃设置 / MD3 主题）→ 通用 → 语言 → 默认选项 → 输出 → 视频转码 → 关于；
 * - 二级：提醒、关于；
 * - 二级页使用与「主界面 → 设置」同一套整幅推入动画（右侧非线性滑入），
 *   并可用左向右拖拽手势返回。
 *
 * 顶栏（返回钮 + 标题）由宿主**统一绘制一次**、固定在原位：
 * 一级 / 二级页都只提供正文，切换时返回钮不会跟着内容一起位移
 * （此前二级页自带顶栏，推入时返回钮会横跨整屏滑过来）。
 */
@Composable
internal fun LiquidSettingsHost(
    onBack: () -> Unit,
    onSubpageChanged: (Boolean) -> Unit = {},
    // 请求宿主展示「检查更新 / 重新安装本版本」弹窗（弹窗渲染在应用根层级，不在设置页内）
    onRequestUpdate: () -> Unit = {}
) {
    var page by remember { mutableStateOf(SettingsSubpage.ROOT) }
    // 退出动画期间仍需渲染刚关闭的二级页
    var layerPage by remember { mutableStateOf<SettingsSubpage?>(null) }

    BackHandler(enabled = page != SettingsSubpage.ROOT) { page = SettingsSubpage.ROOT }
    LaunchedEffect(page) {
        if (page != SettingsSubpage.ROOT) layerPage = page
        onSubpageChanged(page != SettingsSubpage.ROOT)
    }

    // 顶栏固定高度：状态栏 + Glasmio 顶栏 54dp + 与内容的 4dp 间距
    val insets = WindowInsets.systemBars.asPaddingValues()
    val contentPadding = PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = insets.calculateTopPadding() + 58.dp,
        bottom = insets.calculateBottomPadding()
    )

    Box(Modifier.fillMaxSize()) {
        // 一级页常驻底层：转场期间不重建，滚动位置与状态得以保留
        SettingsPage(
            title = "",
            onBack = null,
            hostContentPadding = contentPadding
        ) {
            // 赞助单独提到一级页最顶端（用户要求）
            SponsorEntrySection()
            AppearanceSettingsSection()
            GeneralSettingsSection()
            LanguageSettingsSection()
            SettingsEntryRow(
                title = stringResource(R.string.settings_reminder_dialogs_title),
                onClick = { page = SettingsSubpage.REMINDERS }
            )
            OutputSettingsSection()
            TranscodeSettingsSection()
            SettingsEntryRow(
                title = stringResource(R.string.settings_section_about),
                onClick = { page = SettingsSubpage.ABOUT }
            )
        }

        // 二级页：整幅从右侧推入，支持点返回与拖拽返回
        LiquidSlideOverPage(
            visible = page != SettingsSubpage.ROOT,
            onDismiss = { page = SettingsSubpage.ROOT },
            onHidden = { layerPage = null }
        ) {
            when (layerPage) {
                SettingsSubpage.REMINDERS -> ReminderSettingsPage(
                    onBack = { page = SettingsSubpage.ROOT },
                    hostContentPadding = contentPadding
                )
                SettingsSubpage.ABOUT -> AboutSettingsPage(
                    onRequestUpdate = onRequestUpdate,
                    onBack = { page = SettingsSubpage.ROOT },
                    hostContentPadding = contentPadding
                )
                else -> Unit
            }
        }

        // 固定顶栏（绘制在最上层）：返回钮始终停在原位，只切换标题文本
        Box(Modifier.align(Alignment.TopCenter)) {
            SettingsTopBar(
                title = when (page) {
                    SettingsSubpage.REMINDERS -> stringResource(R.string.settings_reminder_dialogs_title)
                    SettingsSubpage.ABOUT -> stringResource(R.string.settings_section_about)
                    SettingsSubpage.ROOT -> stringResource(R.string.settings_title)
                },
                onBack = {
                    if (page != SettingsSubpage.ROOT) page = SettingsSubpage.ROOT else onBack()
                }
            )
        }
    }
}
