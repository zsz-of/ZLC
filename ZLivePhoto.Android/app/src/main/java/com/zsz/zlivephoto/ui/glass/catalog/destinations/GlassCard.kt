package com.zsz.zlivephoto.ui.glass.catalog.destinations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.zsz.zlivephoto.ui.glass.catalog.shapes.RoundedRectangle

/**
 * 列表 / 设置页使用的「背景板模糊框」：圆角玻璃面板，带饱和度提升（vibrancy）
 * 与边缘折射（lens），是 Kyant0 演示中 ScrollContainer / LazyScrollContainer
 * 列表项的可复用版本。
 */
@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 32.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { RoundedRectangle(cornerRadius) },
            effects = {
                vibrancy()
                lens(16f.dp.toPx(), 32f.dp.toPx())
            }
        ),
        content = content
    )
}
