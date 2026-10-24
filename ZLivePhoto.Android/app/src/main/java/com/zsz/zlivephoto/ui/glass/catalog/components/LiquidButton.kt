package com.zsz.zlivephoto.ui.glass.catalog.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.zsz.zlivephoto.ui.glass.catalog.shapes.Capsule
import com.zsz.zlivephoto.ui.glass.catalog.utils.InteractiveHighlight
import com.zsz.zlivephoto.ui.glass.animatedThemeColor
import com.zsz.zlivephoto.ui.glass.glassFusionParticipant
import com.zsz.zlivephoto.ui.glass.rememberGlassEffectCache
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 液态玻璃按钮：玻璃表面 + 按压力高光 + 拖拽形变（外轮廓拉伸/缩放）。
 * 按下沿触点径向浮起白色高光，拖拽时轮廓向拖拽方向液态拉伸，松手弹簧回弹。
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()

    // 效果链缓存：参数全静态，重跑时零原生 RenderEffect 分配
    val effectCache = rememberGlassEffectCache()

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }
    // 默认带一层主题化玻璃底色，与主界面 GlassActionButton 的视觉语言一致；
    // 调用方传 Color.Transparent 可保持清透文字按钮
    val effectiveTint = if (tint.isSpecified) tint else animatedThemeColor(
        Color(0xFF121212).copy(0.36f),
        Color(0xFFFAFAFA).copy(0.36f)
    )

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    effectCache.apply(
                        scope = this,
                        blurRadiusPx = 2f.dp.toPx(),
                        lensHeightPx = 12f.dp.toPx(),
                        lensAmountPx = 24f.dp.toPx(),
                        lensEnabled = true,
                        vibrancyEnabled = true
                    )
                },
                layerBlock = if (isInteractive) {
                    {
                        val width = size.width
                        val height = size.height

                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 4f.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX =
                            scale +
                                    maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                    (width / height).fastCoerceAtMost(1f)
                        scaleY =
                            scale +
                                    maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                    (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    if (effectiveTint.isSpecified) drawRect(effectiveTint)
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                }
            )
            .glassFusionParticipant(
                color = when {
                    surfaceColor.isSpecified && surfaceColor.alpha > 0f -> surfaceColor
                    effectiveTint.isSpecified && effectiveTint.alpha > 0f -> effectiveTint
                    else -> Color.White
                },
                active = { interactiveHighlight.pressProgress }
            )
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
            .heightIn(min = 40.dp)
            .padding(horizontal = 16f.dp),
        horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
