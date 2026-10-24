package com.zsz.zlivephoto.ui.glass.catalog.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.zsz.zlivephoto.ui.glass.GlassButtonPhysics
import com.zsz.zlivephoto.ui.glass.animatedThemeColor
import com.zsz.zlivephoto.ui.glass.quantizeAnim
import com.zsz.zlivephoto.ui.glass.rememberGlassButtonPhysics
import com.zsz.zlivephoto.ui.glass.rememberGlassEffectCache
import com.zsz.zlivephoto.ui.glass.catalog.shapes.Capsule
import com.zsz.zlivephoto.ui.glass.catalog.utils.DampedDragAnimation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 液态玻璃滑条：玻璃轨道 + 高亮已选段 + 玻璃圆滑块。
 * 滑块可拖拽形变（阻尼 + 弹簧回弹），按下时滑块径向缩放呈现「按进轨道」的物理感。
 *
 * 两种模式：
 * - 默认（阻尼模式）：演示版同款 [DampedDragAnimation]——拖拽跟手、松手回弹，
 *   适合连续量（如通透度）；
 * - [direct] = true（直接模式）：滑块的**位置 1:1 跟随数值**，不做弹簧追赶，
 *   点击/拖拽立即映射到目标值。适合整数量参数（如 CRF 10..30）——避免
 *   「动画追着四舍五入后的整数值跑」造成的拖动卡顿/抖动。
 *
 * 两种模式下点击轨道**或滑块本身**都会跳转到对应位置（此前只有 6dp 轨道可点，
 * 点滑块毫无反应）。
 *
 * [enabled] = false 时保持玻璃外观、仅置灰并禁用手势（不退回原生 MD3 滑条）——
 * 转码方式选 off/remux 时 CRF 不可调，但样式不能变（用户反馈：「转码器如果关闭，
 * 那下面的 UI 会退回 MD3 的样式，应该保持液态玻璃的样式」）。
 */
@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    direct: Boolean = false,
    enabled: Boolean = true
) {
    if (direct) {
        LiquidSliderDirect(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            backdrop = backdrop,
            modifier = modifier,
            enabled = enabled
        )
    } else {
        LiquidSliderDamped(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            visibilityThreshold = visibilityThreshold,
            backdrop = backdrop,
            modifier = modifier,
            enabled = enabled
        )
    }
}

/** 阻尼模式：演示版同款弹簧物理（连续量用） */
@Composable
private fun LiquidSliderDamped(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // 深浅色跟随应用内主题（LocalDarkTheme），并带 500ms 渐变过渡（与主题色同步）
    val accentColor = animatedThemeColor(Color(0xFF0091FF), Color(0xFF0088FF))
    val trackColor = animatedThemeColor(
        Color(0xFF787880).copy(0.36f),
        Color(0xFF787878).copy(0.2f)
    )

    val trackBackdrop = rememberLayerBackdrop()
    // 效果链缓存：按压/拖拽期间每帧不再新建原生 RenderEffect
    val effectCache = rememberGlassEffectCache()

    // 禁用态整体置灰（玻璃外观保留，不退回 MD3 滑条）
    BoxWithConstraints(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDrag) {
                        onValueChange(targetValue)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    onValueChange(
                        if (isLtr) (targetValue + delta).coerceIn(valueRange)
                        else (targetValue - delta).coerceIn(valueRange)
                    )
                }
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() }
                .collectLatest { value ->
                    if (dampedDragAnimation.targetValue != value) {
                        dampedDragAnimation.updateValue(value)
                    }
                }
        }

        // 轨道点击 → 跳转（含滑块正下方：滑块兄弟节点也会收到事件，双保险）
        fun seekTo(trackX: Float) {
            val delta = (valueRange.endInclusive - valueRange.start) * (trackX / trackWidth)
            val targetValue =
                (if (isLtr) valueRange.start + delta
                else valueRange.endInclusive - delta)
                    .coerceIn(valueRange)
            dampedDragAnimation.animateToValue(targetValue)
            onValueChange(targetValue)
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(Capsule())
                    .background(trackColor)
                    .pointerInput(animationScope) {
                        detectTapGestures { position -> seekTo(position.x) }
                    }
                    .height(6f.dp)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .clip(Capsule())
                    .background(accentColor)
                    .height(6f.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }
                // 禁用态不给拖拽/回弹手势（玻璃外观保留）
                .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                // 点击滑块本身也要跳转：把触点换算回轨道坐标
                // （滑块左缘 = trackWidth*progress - 半宽，触点在滑块内 0..40dp）
                .pointerInput(trackWidth, isLtr, enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { position ->
                        val centerX = trackWidth * dampedDragAnimation.progress
                        val trackX = centerX + position.x - 20f.dp.toPx()
                        seekTo(if (isLtr) trackX else trackWidth - trackX)
                    }
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        // 按压量量化到 4 档：短促按压弹簧期间效果链缓存仍能命中
                        val press = quantizeAnim(dampedDragAnimation.pressProgress)
                        effectCache.apply(
                            scope = this,
                            blurRadiusPx = 8f.dp.toPx() * (1f - press),
                            lensHeightPx = 10f.dp.toPx() * press,
                            lensAmountPx = 14f.dp.toPx() * press,
                            lensEnabled = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40f.dp, 24f.dp)
        )
    }
}

/**
 * 直接模式（CRF 等整数量参数）：**不限行程、不分档位**——按「已滑动比例 ×
 * 总长度 + 起点」得出连续值，只在写回时四舍五入成整数（用户规格）。
 *
 * 手势结构（与阻尼版同款可靠手感）：
 * - 滑块（拇指）上挂**消费式**拖拽检测器：按哪儿拖哪儿，滑块位置 1:1 跟手
 *   （拖拽期间用本地 float 值驱动，不被四舍五入的整数值一格一格拽住）；
 * - 轨道其余区域点击直接跳转。
 * - 每次位移写回 round(值)——显示数值取整，滑块移动保持连续。
 *
 * 玻璃滑块保留演示版的按压物理（径向高光 + 按压缩放，见 [GlassButtonPhysics]）。
 */
@Composable
private fun LiquidSliderDirect(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // 深浅色跟随应用内主题（LocalDarkTheme），并带 500ms 渐变过渡（与主题色同步）
    val accentColor = animatedThemeColor(Color(0xFF0091FF), Color(0xFF0088FF))
    val trackColor = animatedThemeColor(
        Color(0xFF787880).copy(0.36f),
        Color(0xFF787878).copy(0.2f)
    )

    val trackBackdrop = rememberLayerBackdrop()
    val physics: GlassButtonPhysics = rememberGlassButtonPhysics(maxOffset = 3.dp)
    // 效果链缓存：按压/拖拽期间每帧不再新建原生 RenderEffect
    val effectCache = rememberGlassEffectCache()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // 禁用态整体置灰（玻璃外观保留，不退回 MD3 滑条）
    BoxWithConstraints(
        modifier.fillMaxWidth().height(42f.dp).alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
        val thumbHalfPx = with(density) { 20.dp.toPx() }

        // 拖拽中的连续值（null = 未拖拽）：滑块 1:1 跟手（float），
        // 只在写回时四舍五入——整数值不再反向把滑块拽成逐格跳变。
        // progress 提为 lambda：内部读快照状态，调用点（layout/layer 期）各自订阅，
        // 避免拖拽/点击动画每一帧重组整个滑条子树
        var dragValue by remember { mutableStateOf<Float?>(null) }
        val baseProgress = ((value() - valueRange.start) / span).fastCoerceIn(0f, 1f)
        // 点击跳转的**液态玻璃转移动画**：点轨道任意位置 → 滑块以弹簧平滑滑到
        // 目标比例（而非瞬移），数值同步写回（用户反馈：点击后要有转移动画）
        val tapAnim = remember { Animatable(baseProgress) }
        var tapping by remember { mutableStateOf(false) }
        val progress: () -> Float = {
            when {
                dragValue != null -> ((dragValue!! - valueRange.start) / span).fastCoerceIn(0f, 1f)
                tapping -> tapAnim.value
                else -> baseProgress
            }
        }

        // 手势进度每帧变化，pointerInput 协程里是捕获值——用 rememberUpdatedState 取最新
        val currentProgress = rememberUpdatedState(progress)

        // 轨道坐标（0..trackWidth）→ 数值：按滑动比例 × 总长度（LTR/RTL 感知）
        fun valueAt(trackX: Float): Float {
            val frac = (trackX / trackWidth).fastCoerceIn(0f, 1f)
            return if (isLtr) valueRange.start + span * frac
            else valueRange.endInclusive - span * frac
        }
        // 轨道坐标 → 比例（0..1）
        fun fracAt(trackX: Float): Float = (trackX / trackWidth).fastCoerceIn(0f, 1f)

        // 采样层撑满整个 42dp 手势区，轨道/已选段在层内**垂直居中**
        // （滑块与滑条同一条中线，见用户反馈「滑块和滑条垂直位置有偏差」）
        Box(Modifier.layerBackdrop(trackBackdrop).fillMaxSize()) {
            // 轨道
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .clip(Capsule())
                    .background(trackColor)
                    .height(6f.dp)
                    .fillMaxWidth()
            )
            // 已选段（跟手，含拖拽中的连续比例）
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .clip(Capsule())
                    .background(accentColor)
                    .height(6f.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * progress()).fastRoundToInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
            // 点击轨道任意位置 → 弹簧平滑滑到目标比例（液态玻璃转移动画），数值写回
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(enabled, trackWidth, isLtr) {
                        if (!enabled) return@pointerInput
                        detectTapGestures { position ->
                            val targetFrac = fracAt(position.x)
                            val targetValue = valueAt(position.x)
                            onValueChange(targetValue.roundToInt().toFloat())
                            scope.launch {
                                tapping = true
                                tapAnim.animateTo(
                                    targetFrac,
                                    spring(dampingRatio = 0.72f, stiffness = 420f)
                                )
                                tapping = false
                            }
                        }
                    }
            )
        }

        // 玻璃滑块：位置 1:1 对应比例；拇指上的消费式拖拽保证滑块本身可拖
        Box(
            Modifier
                .graphicsLayer {
                    val thumbW = size.width
                    translationX =
                        if (isLtr) trackWidth * progress() - thumbW / 2f
                        else trackWidth * (1f - progress()) - thumbW / 2f
                }
                // 按压径向高光（非消费，事件继续传给下面的拖拽检测器）
                .then(if (enabled) physics.highlight.gestureModifier else Modifier)
                // 拇指拖拽：消费事件（与阻尼滑条同款手势可靠性）；
                // 触点换算回轨道坐标 = 拇指左缘 + 触点在拇指内的 x
                .pointerInput(enabled, trackWidth, isLtr) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { startPos ->
                            val thumbLeft =
                                trackWidth * (currentProgress.value)() - thumbHalfPx
                            val v = valueAt(thumbLeft + startPos.x)
                            dragValue = v
                            onValueChange(v.roundToInt().toFloat())
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val thumbLeft =
                                trackWidth * (currentProgress.value)() - thumbHalfPx
                            val v = valueAt(thumbLeft + change.position.x)
                            dragValue = v
                            onValueChange(v.roundToInt().toFloat())
                        },
                        onDragEnd = {
                            dragValue?.let { onValueChange(it.roundToInt().toFloat()) }
                            dragValue = null
                        },
                        onDragCancel = { dragValue = null }
                    )
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val press = physics.highlight.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, press)
                            val scaleY = lerp(0f, 1f, press)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        // 按压量量化到 4 档：短促按压弹簧期间效果链缓存仍能命中
                        val press = quantizeAnim(physics.highlight.pressProgress)
                        effectCache.apply(
                            scope = this,
                            blurRadiusPx = 8f.dp.toPx() * (1f - press),
                            lensHeightPx = 10f.dp.toPx() * press,
                            lensAmountPx = 14f.dp.toPx() * press,
                            lensEnabled = true
                        )
                    },
                    highlight = {
                        val press = physics.highlight.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            // 静止时也保留一圈环境高光：滑块读作「玻璃球」而非实心块
                            // （用户反馈：恢复滑块液态玻璃效果）
                            alpha = 0.45f + 0.55f * press
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val press = physics.highlight.pressProgress
                        InnerShadow(
                            radius = 4f.dp * press,
                            alpha = press
                        )
                    },
                    layerBlock = physics.transform,
                    onDrawSurface = {
                        val press = physics.highlight.pressProgress
                        // 表面只铺一层轻纱白（不再是 1f 全实心）：静止时滑块透出
                        // 下层轨道/页面的磨砂影像——液态玻璃滑块（用户反馈恢复项）
                        drawRect(Color.White.copy(alpha = 0.30f * (1f - press)))
                    }
                )
                .then(physics.highlight.modifier)
                .size(40f.dp, 24f.dp)
        )
    }
}
