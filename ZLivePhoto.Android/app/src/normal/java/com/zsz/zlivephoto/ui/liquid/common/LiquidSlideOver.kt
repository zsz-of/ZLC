package com.zsz.zlivephoto.ui.liquid.common

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.ui.FancyEasing
import io.github.neoruaa.glasmio.core.LocalGlasmioSwipeBackExclusions
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * 手势返回状态：进度语义固定为「关闭进度」。
 *
 * - `0f` = 页面完全贴合屏幕（完全展开）；
 * - `1f` = 页面被完全推出屏幕右侧（已返回）。
 *
 * 手势期间直接写 [progress] 跟手；程序化进入/退出用 [animateTo] 补间，
 * 两者共用同一个数值，不会出现「手势一套、动画一套」的割裂。
 */
@Stable
internal class LiquidSwipeBackState {
    var progress by mutableFloatStateOf(0f)
    var dragging by mutableStateOf(false)
    var animating by mutableStateOf(false)

    suspend fun animateTo(target: Float, spec: AnimationSpec<Float>) {
        animating = true
        try {
            animate(initialValue = progress, targetValue = target, animationSpec = spec) { v, _ ->
                progress = v
            }
        } finally {
            animating = false
        }
    }
}

/**
 * iOS 式全屏滑入页面（用于相册选择页、设置二级页）。
 *
 * - 进入：从屏幕右侧整幅推入（[FancyEasing] 非线性），不再有「短位移 + 圆角形变」；
 * - 退出：可点返回按钮，也可在非滑块 / 非横向滚动区域从左向右拖拽，
 *   页面跟手被推出屏幕，松手按位移 + 速度判定完成或取消。
 *
 * 判定是否可开始拖拽时查询 [LocalGlasmioSwipeBackExclusions]，
 * 滑块、横向滚动列表等已通过 `Modifier.swipeBackExcluded()` 登记的区域不抢手势。
 */
@Composable
internal fun LiquidSlideOverPage(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onHidden: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var present by remember { mutableStateOf(false) }
    val state = remember { LiquidSwipeBackState() }
    val rtlDir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    LaunchedEffect(visible) {
        if (visible) {
            if (!present) {
                state.progress = 1f
                present = true
                state.animateTo(0f, tween(450, easing = FancyEasing))
            }
        } else if (present) {
            if (!state.dragging) {
                state.animateTo(1f, tween(360, easing = FancyEasing))
            }
            present = false
            state.progress = 0f
            onHidden()
        }
    }
    if (!present) return
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = state.progress * size.width * rtlDir
            }
            .liquidSwipeBackDrag(
                state = state,
                enabled = true,
                rtlDir = rtlDir,
                onCommit = onDismiss
            )
    ) {
        // 页面自带不透明底色：推拉时下层（一级页）内容不会从页面中间透出来
        // （此前二级页背景透明，拖动返回时整页呈现半透明观感）
        com.zsz.zlivephoto.ui.glass.GlassBackgroundFill(Modifier.fillMaxSize())
        content()
    }
}

/**
 * 横向拖拽返回手势。
 *
 * 只在「横向意图」明确、且子组件没有消费事件时接管；接管后页面进度完全跟随手指，
 * 松手时按位移比例与瞬时速度决定完成还是弹回（与 iOS 的完成判定同源）。
 */
internal fun Modifier.liquidSwipeBackDrag(
    state: LiquidSwipeBackState,
    enabled: Boolean,
    rtlDir: Int,
    onCommit: () -> Unit
): Modifier = composed {
    val registry = LocalGlasmioSwipeBackExclusions.current
    val scope = rememberCoroutineScope()
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    if (!enabled) Modifier else this
        .onGloballyPositioned { coordinates[0] = it }
        .pointerInput(state, rtlDir, registry, scope) {
            val sign = rtlDir.toFloat()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                if (state.dragging || state.animating) return@awaitEachGesture
                val root = coordinates[0]?.takeIf { it.isAttached } ?: return@awaitEachGesture
                // 滑块 / 横向列表等登记区域自己处理横向拖动
                if (registry?.hitTest(root.localToWindow(down.position)) == true) {
                    return@awaitEachGesture
                }
                val extent = size.width.toFloat().coerceAtLeast(1f)
                val slop = max(viewConfiguration.touchSlop, 10.dp.toPx())
                // 接管阈值比系统 touchSlop 更小：用户反馈「手势返回不容易触发」，
                // 手指稍微横移就应进入拖拽，而不是被判定为点击/纵向滚动。
                val claimSlop = minOf(slop, 6.dp.toPx())
                val velocity = VelocityTracker()
                velocity.addPosition(down.uptimeMillis, down.position)
                var travel = 0f
                var cross = 0f
                var claimed = false
                // 争夺阶段：横向位移超过 slop 且明显大于纵向才接管
                while (!claimed) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: return@awaitEachGesture
                    if (!change.pressed || event.changes.count { it.pressed } != 1) {
                        return@awaitEachGesture
                    }
                    if (change.isConsumed) return@awaitEachGesture
                    velocity.addPosition(change.uptimeMillis, change.position)
                    travel += sign * change.positionChangeIgnoreConsumed().x
                    cross += change.positionChangeIgnoreConsumed().y
                    if (travel < -slop || (abs(cross) > slop && abs(cross) > abs(travel))) {
                        return@awaitEachGesture
                    }
                    if (travel > claimSlop) claimed = true
                }
                state.dragging = true
                var progress = state.progress.coerceIn(0f, 1f)
                var cancelled = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.changes.count { it.pressed } > 1) {
                            cancelled = true
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        velocity.addPosition(change.uptimeMillis, change.position)
                        progress = (progress + sign * change.positionChangeIgnoreConsumed().x / extent)
                            .coerceIn(0f, 1f)
                        state.progress = progress
                        change.consume()
                        if (!change.pressed) break
                    }
                } finally {
                    state.dragging = false
                }
                val releaseVelocity = sign * velocity.calculateVelocity().x / extent
                // 距离 + 速度联合判定（iOS 式投影落点），阈值刻意压低：
                // - 快速轻扫即使行程很小，也会按 0.22s 的惯性投影继续前进，从而完成返回；
                // - 反向快速甩回（速度为负）会把投影拉回阈值以下，正确取消；
                // - 慢速轻推约 1/5 屏宽即可完成返回。
                val projected = if (releaseVelocity.isNaN()) progress
                else progress + releaseVelocity * 0.22f
                val commit = !cancelled && (projected > 0.16f || progress > 0.20f)
                if (commit) {
                    onCommit()
                } else {
                    scope.launch { state.animateTo(0f, tween(260, easing = FancyEasing)) }
                }
            }
        }
}
