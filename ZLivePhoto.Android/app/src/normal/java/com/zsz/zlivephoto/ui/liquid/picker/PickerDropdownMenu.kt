package com.zsz.zlivephoto.ui.liquid.picker

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import com.zsz.zlivephoto.R
import io.github.neoruaa.glasmio.ui.GlasmioCascadingMenu
import io.github.neoruaa.glasmio.ui.GlasmioCascadingMenuItem

/**
 * 选择器「更多」的级联菜单：直接使用库的 [GlasmioCascadingMenu]，不再自绘。
 *
 * [anchorRect] 是「更多」按钮的**窗口坐标**矩形。传入后库会用 `morphFromAnchor`
 * 把按钮矩形形变成菜单面板（iOS 26 的菜单形变），一级行点开时二级页在同一次形变里
 * 原地长出，收起时反向播放一次。自绘形变会与库的动画叠加成两套，必须交给库。
 */
@Composable
internal fun PickerCascadingMenu(
    anchorRect: Rect,
    items: List<GlasmioCascadingMenuItem>,
    onDismiss: () -> Unit,
    title: String = "",
    onTriggerAlphaChanged: ((Float) -> Unit)? = null,
) {
    GlasmioCascadingMenu(
        anchorBounds = anchorRect,
        items = items,
        title = title,
        expandedDescription = stringResource(R.string.screen_menu_expanded),
        collapsedDescription = stringResource(R.string.screen_menu_collapsed),
        onDismiss = onDismiss,
        // 右上角锚点：菜单右缘贴住按钮右缘向下展开
        expandFromAnchor = Alignment.End,
        morphFromAnchor = true,
        // 触发按钮与菜单共用一次形变：按钮随面板长出同步淡出并缩小，
        // 不再干放在原地（否则形变与按钮互不相干，观感割裂）
        onTriggerAlphaChanged = onTriggerAlphaChanged,
    )
}
