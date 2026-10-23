package com.zsz.zlivephoto.ui.glass.catalog.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.zsz.zlivephoto.ui.LocalDarkTheme
import com.zsz.zlivephoto.ui.glass.animatedThemeColor
import com.zsz.zlivephoto.ui.glass.catalog.shapes.Capsule
import com.zsz.zlivephoto.ui.glass.catalog.utils.DampedDragAnimation
import com.zsz.zlivephoto.ui.rememberHapticFeedback
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 可横向滚动的液态玻璃 tab（[LiquidBottomTabs] 的滚动版）：
 * 选项较多、放不下等宽 tabs 时使用（如转码预设 9 选 veryslow…ultrafast）。
 *
 * 与 [LiquidBottomTabs] 同一套视觉语言与手势：
 * - 容器：vibrancy + 高斯模糊 + 边缘折射的玻璃胶囊；
 * - 指示器：玻璃胶囊随选中项**弹簧滑动**，**可直接拖拽**（阻尼跟手 + 松手
 *   按平均项宽换算成下标增量、弹簧落位选中），按下时按压缩放 + 径向高光 + 透镜；
 * - **点击选项有液体按压反馈**（缩放 + 白色提亮，用户反馈：直接点击没有液体效果）；
 * - 选中项自动滚入视野中央；选项宽度按内容自适应，整行可横向滚动。
 */
@Composable
fun LiquidScrollTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (labels.isEmpty()) return
    val haptic = rememberHapticFeedback()

    // 深浅色跟随应用内主题（LocalDarkTheme），并带 500ms 渐变过渡（与主题色同步）
    val containerColor = animatedThemeColor(
        Color(0xFF121212).copy(0.4f),
        Color(0xFFFAFAFA).copy(0.4f)
    )
    // 绘制期（onDrawSurface 等 DrawScope lambda）不能读 CompositionLocal，
    // 深浅色在这里提前取出（切换时 500ms 渐变动画在组合期完成）
    val isDark = LocalDarkTheme.current

    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()

    // 每个选项在「滚动内容坐标系」中的起点/宽度（onGloballyPositioned 采集，
    // 经父坐标系换算，不受当前滚动位移影响；指示器用 -scrollState.value 补偿）。
    val itemX = remember { mutableStateMapOf<Int, Float>() }
    val itemW = remember { mutableStateMapOf<Int, Float>() }
    var viewportW by remember { mutableFloatStateOf(0f) }
    var laidOut by remember { mutableStateOf(false) }

    val safeIndex = selectedIndex.coerceIn(0, labels.lastIndex)
    val indicatorX = remember { Animatable(0f) }
    val indicatorW = remember { Animatable(0f) }
    val targetX = itemX[safeIndex]
    val targetW = itemW[safeIndex]
    LaunchedEffect(targetX, targetW, laidOut) {
        val x = targetX ?: return@LaunchedEffect
        val w = targetW ?: return@LaunchedEffect
        if (!laidOut) {
            // 首次布局直接落位，避免从 0 滑入的闪跳
            indicatorX.snapTo(x)
            indicatorW.snapTo(w)
            laidOut = true
        } else {
            indicatorX.animateTo(x, spring(dampingRatio = 0.72f, stiffness = 420f))
            indicatorW.animateTo(w, spring(dampingRatio = 0.72f, stiffness = 420f))
        }
    }
    // 选中项变化时把它滚到视野中央（首次布局同样生效）
    LaunchedEffect(safeIndex, viewportW, laidOut) {
        if (!laidOut || viewportW <= 0f) return@LaunchedEffect
        val x = itemX[safeIndex] ?: return@LaunchedEffect
        val w = itemW[safeIndex] ?: return@LaunchedEffect
        val target = (x - (viewportW - w) / 2f)
            .coerceIn(0f, scrollState.maxValue.toFloat())
        if (abs(target - scrollState.value) > 1f) {
            scrollState.animateScrollTo(target.roundToInt())
        }
    }

    // ── 指示器拖拽（与 LiquidBottomTabs 同一套 DampedDragAnimation 物理）──
    // 拖拽位移（px）：跟手累积，松手按「平均项宽」换算成下标增量
    var dragOffset by remember { mutableStateOf(0f) }
    val avgTabWidth = viewportW / labels.size.coerceAtLeast(1)
    // remember(animationScope) 的回调里不能捕获会过期的组合值——最新值走 rememberUpdatedState
    val latestIndex = rememberUpdatedState(safeIndex)
    val latestLastIndex = rememberUpdatedState(labels.lastIndex)
    val latestOnSelect = rememberUpdatedState(onSelect)
    val latestAvgTabWidth = rememberUpdatedState(avgTabWidth)
    val latestHaptic = rememberUpdatedState(haptic)
    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = 0f,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 58f / 42f,
            onDragStarted = {},
            onDragStopped = {
                val avgW = latestAvgTabWidth.value
                val delta = if (avgW > 1f) (dragOffset / avgW).roundToInt() else 0
                dragOffset = 0f
                val cur = latestIndex.value
                val target = (cur + delta).coerceIn(0, latestLastIndex.value)
                if (target != cur) {
                    latestHaptic.value.click()
                    latestOnSelect.value(target)
                }
                // target == cur：位移清零，指示器自然回到原位（translationX 少一项）
            },
            onDrag = { _, dragAmount ->
                dragOffset += dragAmount.x
            }
        )
    }

    // 可见行的标签内容；选中项在布局完成后改画在指示器玻璃之上（清晰、不被折射遮挡），
    // 行内不再重复绘制；首帧布局前（laidOut=false）仍画在行内，避免文字闪烁
    val labelContent: @Composable (Int) -> Unit = { index ->
        if (laidOut && index == safeIndex) {
            // 选中项文字见下方指示器内的 AnimatedContent
        } else {
            Text(
                text = labels[index],
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (index == safeIndex) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    index == safeIndex -> MaterialTheme.colorScheme.onSurface
                    else -> animatedThemeColor(Color(0xFF9A9AA0), Color(0xFF5F5F66))
                },
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(50f.dp)
            // 视口宽度：选中项滚入中央、拖拽换算下标增量共用
            .onSizeChanged { viewportW = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart
    ) {
        // ── 可见容器：玻璃胶囊（vibrancy + 模糊 + 边缘折射），内容横向滚动 ──
        Row(
            Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        // 指示器被按下时容器一起轻微放大（演示版同款物理）
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .height(50f.dp)
                .fillMaxWidth()
                .padding(4.dp)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, _ ->
                // 液体按压反馈：按下缩放 + 白色提亮（用户反馈：直接点击没有液体效果）
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val pressScale by animateFloatAsState(
                    targetValue = if (pressed) 0.88f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
                    label = "scrollTabPress"
                )
                Box(
                    Modifier
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                        .background(
                            if (pressed) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .onGloballyPositioned { coords ->
                            val parent = coords.parentLayoutCoordinates
                            if (parent != null) {
                                itemX[index] = parent.localPositionOf(coords, Offset.Zero).x
                                itemW[index] = coords.size.width.toFloat()
                            }
                        }
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled
                        ) {
                            if (index != safeIndex) {
                                haptic.click()
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    labelContent(index)
                }
            }
        }

        // 视口宽度回填在容器 onSizeChanged（见上方 BoxWithConstraints）

        // ── 玻璃指示器：随选中项平滑滑动（弹簧）+ 可拖拽 + 按压缩放 ──
        // 常驻液态玻璃（vibrancy + 模糊 + 环境高光 + 投影）：切换时是玻璃平滑滑过，
        // 而非 MD3 扁平色块；translationX 钳制在 [0, 视口宽 - 指示器宽]，
        // 玻璃块始终限制在容器边框内并与预设框对齐。
        if (laidOut && (targetW ?: 0f) > 0f) {
            val indicatorLeft = (indicatorX.value - scrollState.value + dragOffset)
                .fastCoerceIn(0f, (viewportW - indicatorW.value).coerceAtLeast(0f))
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer { translationX = indicatorLeft }
                    .width(with(density) { indicatorW.value.toDp() })
                    .height(42f.dp)
                    // 指示器拖拽（消费式，与 LiquidBottomTabs 同款）；
                    // 禁用态不给拖拽手势（玻璃外观与弹簧定位仍保留）
                    .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val press = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(3f.dp.toPx())
                            lens(
                                10f.dp.toPx() * press,
                                14f.dp.toPx() * press,
                                chromaticAberration = false
                            )
                        },
                        highlight = {
                            val press = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = 0.4f + 0.6f * press)
                        },
                        shadow = {
                            val press = dampedDragAnimation.pressProgress
                            Shadow(alpha = 0.25f + 0.75f * press)
                        },
                        innerShadow = {
                            val press = dampedDragAnimation.pressProgress
                            InnerShadow(radius = 8f.dp * press, alpha = press)
                        },
                        layerBlock = {
                            // 按压缩放（与 LiquidBottomTabs 的 58/42 同款比例）
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val press = dampedDragAnimation.pressProgress
                            drawRect(
                                if (!isDark) Color.Black.copy(0.1f)
                                else Color.White.copy(0.1f),
                                alpha = 1f - press
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * press))
                        }
                    )
                    // 指示器内的文字是行内文字的重复节点，清掉语义避免读屏双重播报
                    .clearAndSetSemantics {}
                    // 文字裁进胶囊：宽度弹簧过渡 + 交叉淡入期间，宽文字不溢出玻璃边缘
                    .clip(Capsule()),
                contentAlignment = Alignment.Center
            ) {
                // 选中项文字清晰绘制在玻璃之上（切换时交叉淡入），
                // 不被玻璃折射/模糊遮挡——修复文字右半被遮挡问题
                AnimatedContent(
                    targetState = labels[safeIndex],
                    transitionSpec = {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                    },
                    contentAlignment = Alignment.Center,
                    label = "scrollTabSelectedLabel"
                ) { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (!enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}