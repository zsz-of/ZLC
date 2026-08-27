package com.zsz.zlivephoto.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * ImageToolbox 的 FancyTransitionEasing：轻微过冲的贝塞尔曲线，
 * 用于所有滑动/形变过渡（0.5s 的按钮移动动画）。
 */
val FancyEasing = CubicBezierEasing(0.48f, 0.19f, 0.05f, 1.03f)

/** ImageToolbox 的 AlphaEasing：透明度过渡专用曲线 */
val AlphaEasing = CubicBezierEasing(0.4f, 0.4f, 0.17f, 0.9f)

/**
 * 按下时缩放动画（学习 ImageToolBox 的 ScaleOnTap）
 * 按下时缩小到 0.95，松开弹回 1.0，带弹性效果
 */
fun Modifier.scaleOnPress(
    pressedScale: Float = 0.95f
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 按钮按压反馈状态：
 * - 按下瞬间立即触发震动（而非释放时）
 * - 点击后播放固定序列动画（不受抬手状态直接控制）：
 *   缩放 q 弹一次。序列时长固定，抬手不打断
 *
 * 用法：把 [interactionSource] 传给按钮、[shape] 用 RoundedCornerShape(corner)、
 * modifier 叠加 [scaleModifier]。震动与序列均由按下事件自动触发。
 */
class PressFeedback internal constructor(
    val interactionSource: MutableInteractionSource,
    val corner: Dp,
    val scale: Float
) {
    /** 叠加到按钮 modifier 上的缩放效果 */
    val scaleModifier: Modifier
        get() = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/**
 * 记忆一个按压反馈状态。
 * @param baseCorner 常态圆角（如 16.dp），固定不变（圆角曲率动画已移除）
 * @param pressedCorner 已废弃，保留参数仅为兼容调用方
 * @param hapticOnPress 按下瞬间是否触发清脆震动；
 *   开关类组件传 false（开启=清脆 / 关闭=柔和的差异化反馈由状态切换回调触发）
 */
@Composable
fun rememberPressFeedback(
    baseCorner: Dp = 16.dp,
    pressedCorner: Dp = 6.dp,
    hapticOnPress: Boolean = true
): PressFeedback {
    val haptic = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val scaleAnim = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // 按下瞬间立即震动 + 启动固定序列动画：
    // 序列不受抬手状态直接控制（抬手时动画按自身时间线继续走完）
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                if (hapticOnPress) haptic.click()
                scope.launch {
                    // 缩放：q 弹一次
                    scaleAnim.animateTo(0.965f, tween(90))
                    scaleAnim.animateTo(
                        1f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }
        }
    }
    return remember(interactionSource, scaleAnim.value) {
        PressFeedback(interactionSource, baseCorner, scaleAnim.value)
    }
}

/**
 * 观察一个 [interactionSource] 的按压状态，按下瞬间立即震动（项 5）。
 * 用于自带 clickable 的行/容器：把同一 interactionSource 传给
 * `Modifier.clickable(interactionSource, indication, onClick)` 即可。
 */
@Composable
fun PressHapticEffect(interactionSource: MutableInteractionSource) {
    val haptic = rememberHapticFeedback()
    val pressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        if (pressed) haptic.click()
    }
}

/**
 * 触觉反馈工具（学习 ImageToolBox 的 EnhancedHapticFeedback）
 */
@Composable
fun rememberHapticFeedback(): HapticController {
    val view = LocalView.current
    return remember(view) { HapticController(view) }
}

class HapticController(private val view: android.view.View) {
    /** 清脆反馈（选择、点击、开关开启） */
    fun click() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** 柔和反馈（开关关闭等轻量状态变化） */
    fun soft() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** 长按反馈（删除、确认） */
    fun longPress() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /** 快速连续两下清脆反馈（清空/处理完成的批量清理提示） */
    fun double() {
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        h.postDelayed({
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }, 90)
    }
}

/**
 * 现代 MD3 风格复选框（弹窗用）：
 * - 20dp 圆角方框，勾选时容器色从透明动画填充到 primary
 * - 勾号按路径进度描边动画（180ms）
 * - 未勾选显示 2dp 轮廓线（onSurfaceVariant）
 */
@Composable
fun Md3Checkbox(
    checked: Boolean,
    modifier: Modifier = Modifier
) {
    val checkProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "md3CheckStroke"
    )
    val fillColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "md3CheckFill"
    )
    val outlineColor = if (checked) Color.Transparent
        else MaterialTheme.colorScheme.onSurfaceVariant
    val checkColor = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(fillColor)
            .border(2.dp, outlineColor, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (checkProgress > 0f) {
            Canvas(modifier = Modifier.size(14.dp)) {
                val w = size.width
                val h = size.height
                val checkPath = Path().apply {
                    moveTo(w * 0.20f, h * 0.55f)
                    lineTo(w * 0.42f, h * 0.78f)
                    lineTo(w * 0.82f, h * 0.26f)
                }
                val measure = PathMeasure().apply { setPath(checkPath, false) }
                val drawn = Path()
                measure.getSegment(0f, measure.length * checkProgress, drawn, true)
                drawPath(
                    drawn,
                    color = checkColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
