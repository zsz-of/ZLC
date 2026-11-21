package com.zsz.zlivephoto.ui.liquid.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.neoruaa.glasmio.core.LocalGlassDimensions
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial

/**
 * 顶栏浮动圆钮（返回 / 搜索 / 更多）。
 *
 * 与 [io.github.neoruaa.glasmio.ui.GlasmioIconButton] 的区别只有配方：
 * - 用 [NavigationGlassMaterial.Clear]（0.75pt 霜面、无阴影）而不是默认的 RegularSmall；
 *   RegularSmall 在浅色下是「25% 黑 + 25% 白 + 60% Plus」的实心灰底，
 *   压在照片或纯色页面上都像一块灰色圆片，看不出玻璃。
 * - 加一点 [opticalHighlightBoost]，顶边高光更明确。
 *
 * 交互（点击 / 按压高光 / 禁用态）仍由库的 GlassSurface 负责，不自绘材质。
 */
@Composable
internal fun LiquidGlassIconCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** null = 用库的图标钮标准尺寸 */
    size: Dp? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier.size(size ?: LocalGlassDimensions.current.iconButtonSize),
        backdrop = LocalGlassBackdrop.current,
        shape = CircleShape,
        emphasis = GlassEmphasis.Regular,
        material = NavigationGlassMaterial.Clear,
        enabled = enabled,
        opticalHighlightBoost = 0.35f,
        // 不参与顶栏 action group 的合并表面：每个钮各自是一块独立玻璃
        groupSurfaceSeparation = { 1f },
        onClick = onClick,
        content = content
    )
}
