package io.github.neoruaa.glasmio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.applyGlassDragScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 菜单按住拖动选择的状态（本地补丁新增）。
 *
 * 一次按下可以跨行移动：被按住的行跟随手指做有限形变，高亮随手指换行，松手落在当前行。
 * 对应 FLClash / PrismUI 里 `_pressed` 行索引 + 行拉伸的交互。
 */
@Stable
class MenuDragSelection internal constructor(private val scope: CoroutineScope) {
    /** 当前被按住的选项下标；-1 表示没有。 */
    var pressedIndex by mutableIntStateOf(-1)
        internal set

    /** 手指是否仍按在菜单上（供调用方做悬停计时）。 */
    var pressing by mutableStateOf(false)
        internal set

    /** 相对按下点的位移，用于拖动形变。 */
    var dragOffset by mutableStateOf(Offset.Zero)
        internal set

    /** 每换到一行就自增，供调用方做"停在某一行上悬停多久"的一次性计时。 */
    var hoverToken by mutableIntStateOf(0)
        internal set

    private val press = Animatable(0f, 0.001f)

    /** 0..1 的按住进度，形变与高亮都乘它。 */
    val pressProgress: Float get() = press.value

    internal fun start(index: Int) {
        pressedIndex = index
        pressing = true
        dragOffset = Offset.Zero
        hoverToken++
        scope.launch {
            press.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f))
        }
    }

    internal fun moveTo(index: Int, offset: Offset) {
        if (index != pressedIndex) {
            pressedIndex = index
            hoverToken++
        }
        dragOffset = offset
    }

    internal fun stop() {
        pressing = false
        scope.launch {
            press.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 320f, visibilityThreshold = 0.001f))
            if (!pressing) {
                pressedIndex = -1
                dragOffset = Offset.Zero
            }
        }
    }

    internal fun cancel() {
        pressing = false
        pressedIndex = -1
        dragOffset = Offset.Zero
        scope.launch { press.snapTo(0f) }
    }
}

@Composable
fun rememberMenuDragSelection(): MenuDragSelection {
    val scope = rememberCoroutineScope()
    return remember(scope) { MenuDragSelection(scope) }
}

/**
 * 菜单行：按住时只补一层与 GlasmioMenuItem 一致的黑色按压底
 * （行自带的按压手势会被菜单级手势接管，所以这里补画）。
 *
 * 不再对单行做缩放形变：用户要求「无论如何拉扯，都是整个菜单在形变，
 * 而不是里面的某一项形变」，菜单整体的液体形变由菜单面板负责。
 *
 * [suppressed] 为 true 时不画按压态（一级菜单在二级面板展开后把按住对象交给二级面板）。
 */
fun Modifier.menuDragRow(
    index: Int,
    selection: MenuDragSelection,
    suppressed: Boolean = false,
): Modifier = this
    .drawBehind {
        val active = selection.pressedIndex == index
        if (!suppressed && active) {
            val progress = selection.pressProgress.coerceIn(0f, 1f)
            if (progress > 0.01f) {
                // 左右留出与菜单内容一致的内边距，避免按压底直接贴住菜单边缘
                val inset = 10.dp.toPx()
                val radius = size.height / 2f
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.15f * progress),
                    topLeft = Offset(inset, 0f),
                    size = Size((size.width - inset * 2f).coerceAtLeast(1f), size.height),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
        }
    }

/**
 * 菜单级按住拖动手势。在 Initial 阶段消费指针事件，让行自带的 clickable 不再单独响应，
 * 由本手势统一决定"按下哪行、拖到哪行、松手落在哪行"。
 *
 * [indexAt] 把内容区坐标换算成选项下标；[onActivate] 在松手时对最后停留的行触发。
 */
@Composable
fun Modifier.menuDragSelect(
    selection: MenuDragSelection,
    enabled: Boolean = true,
    indexAt: (Offset) -> Int?,
    onActivate: (Int) -> Unit,
    /** 手指位置（本手势坐标系）变化时回调：供二级面板接力按住态 */
    onMove: (Offset) -> Unit = {},
): Modifier {
    val latestIndexAt by rememberUpdatedState(indexAt)
    val latestActivate by rememberUpdatedState(onActivate)
    val latestMove by rememberUpdatedState(onMove)
    return pointerInput(selection, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startIndex = latestIndexAt(down.position) ?: return@awaitEachGesture
            down.consume()
            var anchor = down.position
            var current = startIndex
            selection.start(startIndex)
            latestMove(down.position)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        selection.stop()
                        latestActivate(current)
                        break
                    }
                    change.consume()
                    val index = latestIndexAt(change.position) ?: current
                    if (index != current) {
                        current = index
                        anchor = change.position
                    }
                    selection.moveTo(current, change.position - anchor)
                    latestMove(change.position)
                }
            } finally {
                if (selection.pressing) selection.cancel()
            }
        }
    }
}
