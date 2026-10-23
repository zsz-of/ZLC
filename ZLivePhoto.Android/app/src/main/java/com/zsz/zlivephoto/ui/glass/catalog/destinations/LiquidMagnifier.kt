package com.zsz.zlivephoto.ui.glass.catalog.destinations

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.shadow.InnerShadow
import com.zsz.zlivephoto.ui.glass.catalog.shapes.Capsule
import com.zsz.zlivephoto.ui.glass.catalog.shapes.RoundedRectangle
import com.zsz.zlivephoto.ui.LocalDarkTheme

/**
 * 液态玻璃放大镜：一段文本 + 可拖拽光标 + 悬浮在光标上方的放大镜片。
 * 镜片通过 lens 折射 + 1.5x 缩放采样放大光标附近的文本内容。
 * 对应 Kyant0 演示中的「输入文字界面放大区域」组件。
 */
@Composable
fun LiquidMagnifier(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    text: String
) {
    // 深浅色跟随应用内主题源（LocalDarkTheme），与手动切换的深色模式保持同源
    val isLightTheme = !LocalDarkTheme.current
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val accentColor =
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val backgroundColor =
        if (isLightTheme) Color(0xFFFFFFFF)
        else Color(0xFF121212)

    val contentBackdrop = rememberLayerBackdrop()
    val cursorBackdrop = rememberLayerBackdrop()
    var offset by remember { mutableStateOf(Offset.Zero) }
    // 放大镜片相对光标上浮的高度（px）：提前用 Density 换算，供 graphicsLayer 与
    // onDrawBackdrop 内的 withTransform 共用（withTransform 的接收者无 Density）。
    val lift = with(LocalDensity.current) { 80f.dp.toPx() }

    Box(modifier) {
        BasicText(
            text,
            Modifier
                .layerBackdrop(contentBackdrop)
                .padding(24f.dp)
                .clip(RoundedRectangle(32f.dp))
                .background(backgroundColor.copy(alpha = 0.9f))
                .padding(24f.dp),
            style = TextStyle(contentColor, 16f.sp)
        )

        Box(
            Modifier
                .graphicsLayer {
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset += dragAmount
                    }
                }
                .layerBackdrop(cursorBackdrop)
                .background(accentColor, Capsule())
                .size(4f.dp, 24f.dp)
        )

        Box(
            Modifier
                .graphicsLayer {
                    translationX = offset.x
                    translationY = offset.y - lift
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, contentBackdrop, cursorBackdrop),
                    shape = { Capsule() },
                    effects = {
                        lens(
                            8f.dp.toPx(),
                            24f.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    innerShadow = { InnerShadow(radius = 16f.dp) },
                    onDrawBackdrop = { drawBackdrop ->
                        withTransform(
                            {
                                scale(1.5f, 1.5f)
                                translate(top = -lift)
                            },
                            drawBackdrop
                        )
                    }
                )
                .size(128f.dp, 96f.dp)
        )
    }
}
