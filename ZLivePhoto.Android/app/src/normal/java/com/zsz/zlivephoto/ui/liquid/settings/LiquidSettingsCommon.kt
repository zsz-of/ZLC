package com.zsz.zlivephoto.ui.liquid.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.ui.rememberHapticFeedback
import com.zsz.zlivephoto.ui.liquid.common.LiquidGlassIconCircle
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.scaffold.GlasmioScaffold
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import io.github.neoruaa.glasmio.ui.GlasmioSectionTitle
import io.github.neoruaa.glasmio.ui.GlasmioSwitch
import io.github.neoruaa.glasmio.ui.GlasmioTopBar
import androidx.compose.material3.MaterialTheme

/** 设置内容最大宽度：横竖屏都保持阅读宽度，避免横屏被拉得过宽。 */
internal val SettingsContentMaxWidth = 640.dp

/**
 * 页面滚动锁：滑块 / 颜色选择这类需要独占横向拖动的控件在按下时置 true，
 * 让外层设置页暂停纵向滚动，避免斜向拖动时整页跟着滚（表现为列表位置跳变）。
 */
internal class SettingsScrollLock {
    var locked by mutableStateOf(false)
}

internal val LocalSettingsScrollLock = compositionLocalOf { SettingsScrollLock() }

/**
 * 设置页统一骨架：Glasmio 顶栏 + 返回 + 单列滚动内容。
 * 设置项数量有限，用普通 Column 滚动即可（比 LazyColumn 更利于玻璃动画与测量）。
 *
 * [hostContentPadding] 非空时说明顶栏已由宿主（[LiquidSettingsHost]）统一绘制，
 * 本页只输出正文——这样一级/二级页切换时返回钮位置不变，不会跟着推拉。
 */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    embedded: Boolean = false,
    hostContentPadding: PaddingValues? = null,
    overlay: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollLock = remember { SettingsScrollLock() }
    CompositionLocalProvider(LocalSettingsScrollLock provides scrollLock) {
        when {
            hostContentPadding != null -> {
                Box(Modifier.fillMaxSize()) {
                    SettingsColumn(
                        title = null,
                        onBack = null,
                        contentPadding = hostContentPadding,
                        content = content
                    )
                    overlay()
                }
            }

            embedded -> {
                Box(Modifier.fillMaxSize()) {
                    SettingsColumn(title = title, onBack = onBack, content = content)
                    overlay()
                }
            }

            else -> {
                GlasmioScaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { SettingsTopBar(title, onBack) }
                ) { padding ->
                    Box(Modifier.fillMaxSize()) {
                        SettingsColumn(
                            title = null,
                            onBack = null,
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = padding.calculateTopPadding() + 4.dp,
                                // 页尾间距由最后一个 SettingsSection 的 18dp 提供，这里不再叠加
                                bottom = padding.calculateBottomPadding()
                            ),
                            content = content
                        )
                        overlay()
                    }
                }
            }
        }
    }
}

/** 设置页顶栏：返回圆钮 + 标题（一级 / 二级页共用同一个实例）。 */
@Composable
internal fun SettingsTopBar(title: String, onBack: (() -> Unit)?) {
    val haptic = rememberHapticFeedback()
    GlasmioTopBar(
        title = title,
        modifier = Modifier.statusBarsPadding(),
        navigation = if (onBack != null) {
            {
                LiquidGlassIconCircle(
                    onClick = {
                        haptic.click()
                        onBack()
                    }
                ) {
                    // 返回图标统一用 SfIcon（chevron.backward 在 RTL 下由库自动镜像），
                    // 不再混用 Material 的 ArrowBack。
                    SfIcon(
                        systemName = "chevron.backward",
                        contentDescription = stringResource(R.string.settings_cd_back),
                        size = 22.dp
                    )
                }
            }
        } else null
    )
}

/** 内嵌模式（横屏右栏）：保留一个轻量标题行 + 可滚动内容。 */
@Composable
private fun SettingsColumn(
    title: String?,
    onBack: (() -> Unit)?,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            // widthIn 必须在 fillMaxHeight 之前：fillMaxSize() 会先把宽度锁成父级固定值，
            // 之后再写 widthIn(max) 会被 constrainWidth 夹回原值（640dp 上限完全失效）。
            Modifier
                .widthIn(max = SettingsContentMaxWidth)
                .fillMaxHeight()
                .verticalScroll(
                    rememberScrollState(),
                    enabled = !LocalSettingsScrollLock.current.locked
                )
                .padding(contentPadding)
        ) {
            if (title != null) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            content()
        }
    }
}

/** 一节：分组标题 + 一组 Glasmio 分组行 + 节间距。 */
@Composable
internal fun SettingsSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (title != null) GlasmioSectionTitle(title)
    GlasmioGroupedList { content() }
    Spacer(Modifier.height(18.dp))
}

/**
 * 二级页入口行：标题 + 右侧 chevron。
 * 用于「外观 / 默认选项 / 关于」这类需要单独一页的设置分组。
 */
@Composable
internal fun SettingsEntryRow(title: String, onClick: () -> Unit) {
    val haptic = rememberHapticFeedback()
    SettingsSection {
        GlasmioListRow(
            title = title,
            onClick = {
                haptic.click()
                onClick()
            },
            trailing = {
                SfIcon(
                    systemName = "chevron.right",
                    contentDescription = null,
                    size = 16.dp,
                    tint = LocalGlassColors.current.secondaryContent
                )
            }
        )
    }
}

/**
 * 开关行：整行可点，右侧 Glasmio 开关；开启/关闭不同触感。
 * 按用户反馈，默认不再追加解释性副标题（只在状态必须解释时传 [subtitle]）。
 */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    val haptic = rememberHapticFeedback()
    val toggle: (Boolean) -> Unit = { v ->
        if (v) haptic.click() else haptic.soft()
        onToggle(v)
    }
    GlasmioListRow(
        title = title,
        subtitle = subtitle,
        onClick = { if (enabled) toggle(!checked) },
        enabled = enabled,
        trailing = {
            // 禁用时把 enabled 一并传下去：此前固定 enabled=true 只是为了避免行与开关双重降透明，
            // 但开关仍可拖拽 —— 拖完视觉状态与真实值不一致（看起来已打开，设置并未写入）。
            // 库在 enabled=false 时既忽略手势，也会把滑块降到 45%，语义正确。
            GlasmioSwitch(
                checked = checked,
                onCheckedChange = { if (enabled) toggle(it) },
                enabled = enabled
            )
        }
    )
}
