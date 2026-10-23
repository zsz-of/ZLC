package com.zsz.zlivephoto.ui.glass.catalog.destinations

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.zsz.zlivephoto.ui.glass.catalog.shapes.RoundedRectangle
import com.zsz.zlivephoto.ui.glass.catalog.utils.scale
import com.zsz.zlivephoto.ui.LocalDarkTheme
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sign

/**
 * 自适应亮度玻璃面板：采样自身折射的背景亮度，动态在「黑字 / 白字」间平滑切换，
 * 并根据亮度微调玻璃的明暗与模糊，保证玻璃面板上的文字始终可读。
 * 这是 Kyant0 演示「根据背景颜色自动改变文本颜色」的可复用实现。
 */
@Composable
fun AdaptiveLuminanceGlass(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(contentColor: Color) -> Unit
) {
    // 深浅色跟随应用内主题源（LocalDarkTheme）：用户手动切深色而系统仍浅色时，
    // 初始内容色必须按应用主题取，否则首帧会出现「深色底 + 黑字」不可读
    val isLightTheme = !LocalDarkTheme.current

    val layer = rememberGraphicsLayer()
    val luminanceAnimation = remember(isLightTheme) {
        Animatable(if (isLightTheme) 1f else 0f)
    }
    var contentColor by remember(isLightTheme) {
        mutableStateOf(if (isLightTheme) Color.Black else Color.White)
    }
    val contentColorAnimation by animateColorAsState(
        targetValue = contentColor,
        animationSpec = tween(1000),
        label = "contentColor"
    )
    LaunchedEffect(layer) {
        val buffer = IntArray(25)
        while (isActive) {
            val imageBitmap = layer.toImageBitmap()
            val thumbnail = imageBitmap.scale(5, 5)
            thumbnail.readPixels(buffer)
            val averageLuminance =
                buffer.sumOf { argb ->
                    val r = (argb shr 16 and 0xFF) / 255f
                    val g = (argb shr 8 and 0xFF) / 255f
                    val b = (argb and 0xFF) / 255f
                    0.2126 * r + 0.7152 * g + 0.0722 * b
                } / buffer.size
            launch {
                contentColor = if (averageLuminance > 0.5f) Color.Black else Color.White
            }
            luminanceAnimation.animateTo(
                averageLuminance.toFloat(),
                tween(1000)
            )
        }
    }

    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { RoundedRectangle(24f.dp) },
            effects = {
                val l = (luminanceAnimation.value * 2f - 1f).let { sign(it) * it * it }
                colorControls(
                    brightness =
                        if (l > 0f) lerp(0.1f, 0.5f, l)
                        else lerp(0.1f, -0.2f, -l),
                    contrast =
                        if (l > 0f) lerp(1f, 0f, l)
                        else 1f,
                    saturation = 1.5f
                )
                blur(
                    if (l > 0f) lerp(8f.dp.toPx(), 16f.dp.toPx(), l)
                    else lerp(8f.dp.toPx(), 2f.dp.toPx(), -l)
                )
                lens(24f.dp.toPx(), size.minDimension / 2f, depthEffect = true)
            },
            highlight = { Highlight.Plain },
            onDrawBackdrop = { drawBackdrop ->
                drawBackdrop()
                layer.record { drawBackdrop() }
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        content(contentColorAnimation)
    }
}
