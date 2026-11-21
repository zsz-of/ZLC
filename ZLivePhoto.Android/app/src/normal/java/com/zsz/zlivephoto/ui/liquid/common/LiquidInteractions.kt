package com.zsz.zlivephoto.ui.liquid.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.LocalGlassColors

/**
 * 统一的按压缩放形变（q 弹）。
 *
 * 只监听指针按下/抬起，不消费事件，因此可以直接挂在 Glasmio / Material 组件外侧，
 * 与控件自身的点击、涟漪、玻璃按压高光叠加，不改变任何点击语义。
 *
 * 用于此前缺少按压反馈的位置：图标按钮、确认按钮、主界面推拉按钮等。
 */
fun Modifier.liquidPressScale(
    pressedScale: Float = 0.94f,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquidPressScale"
    )
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/**
 * 在 [active] 为 true（例如滑块正在拖动）时吞掉外层滚动容器的纵向输入。
 *
 * 设置页的 CRF 滑块放在 LazyColumn 里，斜向拖动时纵向分量会被列表抢走，
 * 表现为「拖滑块整页向上滚」。把本 Modifier 挂在滑块卡片上即可锁定页面滚动。
 */
fun Modifier.blockOuterScrollWhile(active: () -> Boolean): Modifier = composed {
    val connection = remember(active) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!active()) return Offset.Zero
                // 只有纵向分量需要拦截；横向仍交给滑块自身处理
                return if (available.y == 0f) Offset.Zero else Offset(0f, available.y)
            }
        }
    }
    this.nestedScroll(connection)
}

/**
 * Glasmio 风格进度条：细胶囊轨道 + 玻璃强调色进度。
 * 用于替换 Material 的 [androidx.compose.material3.LinearProgressIndicator]，
 * 让扫描/导入进度与玻璃界面保持同一套视觉语言。
 */
@Composable
fun LiquidProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val colors = LocalGlassColors.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(280),
        label = "liquidProgress"
    )
    Box(
        modifier
            .height(4.dp)
            .clip(CircleShape)
            // subtleStroke 是「浅色描边」语义（浅色下为 55% 白），当轨道填充会在白卡上完全看不见；
            // 轨道改用 separator（浅色 #E6E6E6 / 深色 17% 白），两种模式下都有可见对比度。
            .background(colors.separator)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(CircleShape)
                .background(colors.accent)
        )
    }
}

/** 不确定进度（扫描相册等）：一小段强调色在轨道内来回呼吸。 */
@Composable
fun LiquidIndeterminateProgressBar(modifier: Modifier = Modifier) {
    val colors = LocalGlassColors.current
    val transition = rememberInfiniteTransition(label = "liquidIndeterminate")
    val fraction by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liquidIndeterminateFraction"
    )
    Box(
        modifier
            .height(4.dp)
            .clip(CircleShape)
            .background(colors.separator)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.8f))
        )
    }
}
