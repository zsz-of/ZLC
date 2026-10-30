package com.zsz.zlivephoto.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 左缘右滑返回（照 FlClash 的页面返回手势）。
 *
 * 只有从屏幕**起始边缘** 24dp 内起手、且横向位移先越过触摸阈值时才接管手势；
 * 纵向滑动（列表滚动）与反向拖动都会直接放弃，不影响原有滚动体验。
 * 接管后按整屏宽度的比例回传进度（0..1），松手时超过 [completeThreshold] 视为返回，
 * 否则回弹取消。
 *
 * 与系统预测式返回并存：那条路径由 Activity 的 PredictiveBackHandler 负责，
 * 这里是同窗口内的手势替代，用于桌面启动器 / 三键导航等不派发返回手势的场景。
 * [rtl] 为 true 时镜像到右缘、向左滑动。
 */
fun Modifier.edgeSwipeBack(
    enabled: Boolean,
    rtl: Boolean,
    completeThreshold: Float = 0.3f,
    onProgress: (Float) -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit
): Modifier = this.pointerInput(enabled, rtl) {
    if (!enabled) return@pointerInput
    val edge = 24.dp.toPx()
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val atEdge = if (rtl) down.position.x >= size.width - edge else down.position.x <= edge
        if (!atEdge) return@awaitEachGesture
        var dragged = 0f
        var dyAcc = 0f
        var claimed = false
        var progress = 0f
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial)
                .changes.firstOrNull { it.id == down.id } ?: break
            val dx = change.position.x - change.previousPosition.x
            val dy = change.position.y - change.previousPosition.y
            if (!claimed) {
                dragged += dx
                dyAcc += dy
                val forward = if (rtl) -dragged else dragged
                // 纵向先过阈值 → 让位给列表滚动；反向拖动 → 放弃
                if (abs(dyAcc) > slop && abs(dyAcc) > abs(dragged)) break
                if (forward > slop) claimed = true else if (forward < -slop) break
            } else {
                dragged += dx
            }
            if (claimed) {
                // Initial 阶段消费：滚动容器在 Main 阶段就拿不到这串事件，
                // 拖动过程中列表不会跟着一起动
                change.consume()
                progress = ((if (rtl) -dragged else dragged) / (size.width.toFloat() * 0.75f))
                    .coerceIn(0f, 1f)
                onProgress(progress)
            }
            if (!change.pressed) break
        }
        if (claimed) {
            if (progress >= completeThreshold) onComplete() else onCancel()
        }
    }
}
