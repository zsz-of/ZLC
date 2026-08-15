package com.zsz.zlivephoto.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

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
 * 触觉反馈工具（学习 ImageToolBox 的 EnhancedHapticFeedback）
 */
@Composable
fun rememberHapticFeedback(): HapticController {
    val view = LocalView.current
    return remember(view) { HapticController(view) }
}

class HapticController(private val view: android.view.View) {
    /** 轻触反馈（选择、点击） */
    fun click() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** 长按反馈（删除、确认） */
    fun longPress() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
