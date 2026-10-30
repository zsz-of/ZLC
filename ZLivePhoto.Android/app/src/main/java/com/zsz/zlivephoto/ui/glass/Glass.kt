package com.zsz.zlivephoto.ui.glass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.zsz.zlivephoto.ui.AppSettings
import com.zsz.zlivephoto.ui.LocalDarkTheme
import com.zsz.zlivephoto.ui.glass.catalog.components.LiquidBottomTab
import com.zsz.zlivephoto.ui.glass.catalog.components.LiquidBottomTabs
import com.zsz.zlivephoto.ui.glass.catalog.components.LiquidToggle
import com.zsz.zlivephoto.ui.glass.catalog.utils.InteractiveHighlight
import com.zsz.zlivephoto.ui.rememberHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

/*
 * iOS 风格「液态玻璃」UI 基础设施（io.github.kyant0:backdrop 1.0.2）。
 *
 * 三层结构（关键：玻璃元素必须是「被记录节点」的兄弟，否则会采样到自身）：
 *   Box
 *   ├─ Box(layerBackdrop) ← 采样源：主题色柔和渐变（页面内容在其上方，透明容器）
 *   └─ Box                ← 页面内容 + 玻璃元素（同一 Window、同一 Composition 树）
 *
 * 弹窗/菜单**必须**在同一 Compose 树内做覆盖层（库在独立 Window 内采样坐标错误）。
 *
 * 能力门槛：SDK < 31 时库的效果整体静默失效 → [GlassRoot] 直接走原生 MD3 分支；
 * AGSL（边缘折射 lens / 色差）需 SDK ≥ 33，低版本只做高斯模糊。
 */

/** 玻璃是否生效（[AppSettings.glassActive]）。false 时全部组件走原生 MD3 实现 */
val LocalGlassEnabled = staticCompositionLocalOf { false }

/** 采样源记录器（根节点提供） */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 根节点铺在内容之下的静态采样层（主题渐变 / 深色底）。
 * 位于「页面内容采样层」内部的玻璃控件必须用它，否则会采样到自己造成频闪。
 */
val LocalGlassRootBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 方向传感器驱动的「高光照射角」（度）。玻璃元素**在绘制阶段**读取该 lambda，
 * 因此传感器更新只会触发重绘，不会引起整页重组。
 */
val LocalGlassHighlightAngle = staticCompositionLocalOf<() -> Float> { { 45f } }

/**
 * 根采样层的**底色**（[GlassRoot] 铺在内容之下的那条渐变的基础色）。
 *
 * 用途：玻璃件的内容自适应着色（[rememberAdaptiveContentColor]）需要知道
 * 「玻璃透明处透出的是什么颜色」——页面内容层在玻璃模式下是透明的，
 * 直接对采样层求亮度会恒得 0（恒判白字）。采样前先把本底色垫进去合成，
 * 设置按钮 / 导入按钮这类浮在透明页面上的控件才能正确切黑/白。
 * 跟随主题 500ms 渐变（见 [animatedThemeColor]）。
 */
val LocalGlassRootTint = staticCompositionLocalOf { Color.White }

/**
 * 当前页面的玻璃采样层发布者。
 *
 * 应用级弹窗（权限引导 / 更新提示 / 文件名冲突 / 旧版本卸载提示）渲染在
 * 各个页面**之外**（MainActivity 根作用域），拿不到页面自己的
 * `pageBackdrop`；页面在此发布自己的采样层，应用级弹窗据此采样
 * 「根渐变 + 当前页面内容」——与页面同窗口，坐标天然对正、模糊实时
 * （用户反馈 #5/#6），且**复用页面已有的录制，零额外开销**。
 */
class PageBackdropPublisher {
    var backdrop by mutableStateOf<LayerBackdrop?>(null)
}

val LocalPageBackdropPublisher = staticCompositionLocalOf<PageBackdropPublisher?> { null }

/**
 * 页面发布自己的采样层（见 [PageBackdropPublisher]）。
 * 在页面创建 pageBackdrop 之后调用一次；玻璃未启用时传 null。
 */
@Composable
fun PublishPageBackdrop(backdrop: LayerBackdrop?) {
    val publisher = LocalPageBackdropPublisher.current ?: return
    // 同一实例重复赋值为 no-op，不会引起额外重组
    SideEffect { publisher.backdrop = backdrop }
}

/**
 * 跟随深浅色切换的颜色，带 **500ms 渐变过渡**（与主题色 [animateColorScheme] 同步）。
 *
 * 玻璃组件里直接 `if (dark) A else B` 取色的地方（轨道色 / 容器色 / 未选中文字色等）
 * 一律改用本函数：用户切换深色模式时这些颜色也平滑过渡，而不是瞬变
 * （用户反馈：「换色的时候应该是渐变 0.5s 过渡」）。
 */
@Composable
fun animatedThemeColor(darkColor: Color, lightColor: Color): Color {
    val target = if (LocalDarkTheme.current) darkColor else lightColor
    return animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 500),
        label = "themeColor"
    ).value
}

/**
 * 设备倾角 → 高光角度。使用旋转矢量传感器取 roll（绕屏幕法线旋转），
 * 手机在手中转动 / 倾斜时，玻璃边缘的白色高光会跟着移动，形成真实的光照感。
 */@Composable
private fun rememberHighlightAngleProvider(): () -> Float {
    val context = LocalContext.current
    val angle = remember { mutableFloatStateOf(45f) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        var smoothed = 45f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                // roll：绕屏幕法线旋转，是最贴近「手机倾斜导致高光移动」的分量
                val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
                // 低通滤波，避免手抖导致高光跳动
                smoothed += (roll - smoothed) * 0.12f
                // 3° 一档量化：SENSOR_DELAY_GAME 下手部微抖每秒可触发数十次
                // 状态写入 → 每次都是全玻璃件高光重绘；量化后静止手机写入归零
                angle.floatValue = ((45f + smoothed) / 3f).roundToInt() * 3f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { manager?.unregisterListener(listener) }
    }
    return remember { { angle.floatValue } }
}

/**
 * 玻璃容器：铺一条主题色渐变作为采样源，页面内容画在其上（玻璃元素可模糊/折射该渐变）。
 * 未启用玻璃时退化为普通 Box（原生 MD3 分支）。
 * 深色模式下采样源同步换成深底，保证模糊层叠出来的是深色而不是白色。
 */
@Composable
fun GlassRoot(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val active = AppSettings.glassActive
    if (!active) {
        CompositionLocalProvider(LocalGlassEnabled provides false) {
            Box(modifier) { content() }
        }
        return
    }
    val backdrop = rememberLayerBackdrop()
    val cs = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    val highlightAngle = rememberHighlightAngleProvider()
    // 采样源底色跟随主题 500ms 渐变（而非瞬时翻转）：
    // 用户反馈「切换深浅模式时顶部栏异常变灰，重启才正常」——根因是采样源
    // 底色读的是裸 boolean（瞬间翻转），而玻璃件的 tint / 页面文字都在做
    // 500ms 过渡，两者不同步导致顶栏在过渡窗里呈现出「灰蒙蒙」的中间态。
    // 这里让整条采样源与主题色一起渐变，整屏形成一次连贯的交叉淡化。
    val rootBaseColor = animatedThemeColor(Color(0xFF0B0B0D), Color.White)
    // 色斑浓度跟随主题 500ms 渐变（与底色同步）：瞬时翻转会让切换窗口里
    // 色斑「跳一下」再渐变，与底色渐变不同步（顶栏一带观感不连贯）。
    // 深色模式的色斑刻意压淡（0.7×）：用户反馈「深色模式的背景颜色不统一」——
    // 深色底上高浓度主题色斑会把背景染成一块块不同颜色，整页观感不均匀；
    // 压低后深色背景呈均匀深灰，仅保留极淡的层次纹理。
    val blobBoost by animateFloatAsState(
        targetValue = if (dark) 0.7f else 1f,
        animationSpec = tween(durationMillis = 500),
        label = "rootBlobBoost"
    )
    CompositionLocalProvider(
        LocalGlassEnabled provides true,
        LocalGlassBackdrop provides backdrop,
        LocalGlassRootBackdrop provides backdrop,
        LocalGlassRootTint provides rootBaseColor,
        LocalGlassHighlightAngle provides highlightAngle
    ) {
        Box(modifier) {
        Box {
            // ── 采样源（被记录；不参与内容绘制）──
            // 用户要求背景底色回到纯白：采样源改为白底 + 极淡色斑，
            // 玻璃仍能折射出色斑细节，但整页观感是干净的白色。
            // 深色模式：深灰底 + 更淡的色斑，模糊层才会是深色而不是发白。
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(rootBaseColor)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    fun blob(cx: Float, cy: Float, r: Float, color: Color, a: Float) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(color.copy(alpha = a), Color.Transparent),
                                center = Offset(cx, cy),
                                radius = r
                            ),
                            radius = r,
                            center = Offset(cx, cy)
                        )
                    }
                    blob(size.width * 0.12f, size.height * 0.10f, size.width * 0.66f, cs.primary, 0.12f * blobBoost)
                    blob(size.width * 0.90f, size.height * 0.82f, size.width * 0.72f, cs.tertiary, 0.10f * blobBoost)
                    blob(size.width * 0.74f, size.height * 0.16f, size.width * 0.42f, cs.secondary, 0.08f * blobBoost)
                    // 细颗粒（确定性伪随机，保证每帧一致）
                    var seed = 20240613
                    repeat(140) {
                        seed = seed * 1103515245 + 12345
                        val x = ((seed ushr 8) % 1000) / 1000f * size.width
                        seed = seed * 1103515245 + 12345
                        val y = ((seed ushr 8) % 1000) / 1000f * size.height
                        drawCircle(
                            color = if (dark) Color.White.copy(alpha = 0.02f)
                            else Color.Black.copy(alpha = 0.02f),
                            radius = 1.6.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }
            // ── 页面内容（不参与记录；容器背景需透明才能看到采样源）──
            Box(Modifier.fillMaxSize()) { content() }
        }
        }
    }
}

/** 通透度 → 表面色调层透明度（通透度越低越接近磨砂白板） */
private fun glassTintAlpha(opacity: Float): Float = (1f - opacity) * 0.28f + 0.02f

/**
 * 液态玻璃表面（模糊 → 边缘折射 → 表面色调 → 外阴影）。
 *
 * 未启用（API<31 / 用户关闭）时返回原 Modifier：调用方需自行提供 MD3 背景。
 * 形状必须是 [androidx.compose.foundation.shape.RoundedCornerShape]（lens 仅支持 CornerBasedShape）。
 *
 * 参数全部有默认值；[tintColor]/[tintAlpha] 控制「玻璃里掺的那层颜色」，
 * [lensHeight]/[lensAmount] 控制边缘折射的高度与偏移量（越大越像水滴）。
 */
/**
 * 默认的液态玻璃方向性边缘高光（左上 45° 照射，非均匀描边）。
 * 酷安 / iOS 26 的液态玻璃高光极克制：窄、柔、低透明度，只在受光侧留一条细亮边，
 * 而不是一圈生硬描边。这里刻意压到「若隐若现」。
 */
private val DefaultGlassHighlight = Highlight(
    width = 0.9.dp,
    blurRadius = 2.dp,
    alpha = 0.36f,
    style = HighlightStyle.Default
)

/** 拖拽时按钮跟随手指的比例（越小越「迟钝」、越像液体的粘滞感） */
private const val DragFollowFactor = 0.45f

/**
 * 解析玻璃的默认采样源：把「根渐变层」与「当前页面内容层」合成为一份
 * **下层总显示内容**——文字、形状、布局边缘、照片全部在内，
 * 而不是只取其中一层（用户反馈：「液态玻璃的模糊还是没有包含他下面的所有层」）。
 *
 * - 页面内容作用域：[LocalGlassBackdrop] 就是根层（GlassRoot 提供），两者相同，
 *   直接返回根层——页面内的玻璃件（开关/滑条/chip）不采样自身所在层，
 *   避免「把自己画进去」的反馈式频闪（见 [GlassSwitch] 的注释）；
 * - 覆盖层作用域（顶栏 / 底部面板 / 菜单等浮在页面层之上的 chrome）：
 *   [LocalGlassBackdrop] 是页面层，与根层合成后返回，
 *   玻璃模糊因此包含下层的总显示内容。
 *
 * 需要显式指定采样源的调用方仍可传 `liquidGlass(backdropOverride = ...)`。
 */
@Composable
fun rememberGlassBackdrop(): Backdrop? {
    val root = LocalGlassRootBackdrop.current
    val page = LocalGlassBackdrop.current
    if (root == null) return page
    if (page == null || page === root) return root
    return rememberCombinedBackdrop(root, page)
}

/** iOS 开关开启态轨道绿（与系统开关一致的 #34C759） */
private val IosGreen = Color(0xFF34C759)

/**
 * 玻璃效果链缓存。
 *
 * backdrop 的 blur()/lens() 每次调用都新建原生 RenderEffect（blur 一个、
 * lens 的 RuntimeShader 包装 + 链式 effect 各一个），而 DrawBackdropElement.update()
 * 末尾固定 invalidDrawCache()——动画/重组每一帧都会重跑 effects，
 * 等于每帧新建 2~4 个原生对象并重建效果链（中端机上表现为 1% Low 帧差）。
 *
 * 这里按量化参数缓存整条链，量化段内零原生分配复用；
 * 思路与 [ProgressiveBlur] 的 remember RenderEffect 一致，推广到 liquidGlass 主线。
 * 每个玻璃件在组合时各持一份实例（draw 只在渲染线程），无跨线程共享。
 */
internal class GlassEffectCache {
    private data class Key(
        val blur: Float,
        val lensH: Float,
        val lensA: Float,
        val w: Float,
        val h: Float,
        val vibrancy: Boolean,
        val chromatic: Boolean
    )

    private var chain: android.graphics.RenderEffect? = null
    private var lastKey: Key? = null
    private var lastPadding = 0f

    fun apply(
        scope: com.kyant.backdrop.BackdropEffectScope,
        blurRadiusPx: Float,
        lensHeightPx: Float,
        lensAmountPx: Float,
        lensEnabled: Boolean,
        vibrancyEnabled: Boolean = false,
        chromatic: Boolean = false
    ) {
        // 0.5px / 0.25px 量化：半径连续变化时把「每帧都变」收敛成少数几档
        val qb = (blurRadiusPx * 2f).roundToInt() / 2f
        val qh = if (lensEnabled) (lensHeightPx * 4f).roundToInt() / 4f else 0f
        val qa = if (lensEnabled) (lensAmountPx * 4f).roundToInt() / 4f else 0f
        val sz = scope.size
        // size 入键：lens 的 uniform 与尺寸相关，防复用过期链
        val key = Key(qb, qh, qa, sz.width, sz.height, vibrancyEnabled, chromatic)
        if (chain != null && key == lastKey) {
            scope.padding = lastPadding
            scope.renderEffect = chain
            return
        }
        // vibrancy() 内部也是 createColorFilterEffect（同样每次新建原生对象），一并纳入缓存
        if (vibrancyEnabled) scope.vibrancy()
        if (qb > 0.5f) scope.blur(qb, TileMode.Clamp)
        // chromatic=true 走折射色散着色器（液态玻璃边缘的彩虹色散）
        if (lensEnabled) scope.lens(qh, qa, chromatic, false)
        chain = scope.renderEffect
        lastKey = key
        lastPadding = scope.padding
    }
}

/** 每个玻璃件组合时各持一份效果链缓存（draw 只在渲染线程，无跨线程共享） */
@Composable
internal fun rememberGlassEffectCache(): GlassEffectCache = remember { GlassEffectCache() }

/**
 * 按压/动画驱动的连续量量化到 [steps] 档。
 * 短促按压弹簧（~120ms）期间半径连续变化会击穿效果链缓存（每帧重建原生链），
 * 量化到 4 档后整段按压只重建 4 次，肉眼仍是连续的反馈。
 */
internal fun quantizeAnim(v: Float, steps: Int = 4): Float =
    ((v * steps).roundToInt().coerceIn(0, steps)) / steps.toFloat()

/**
 * 液态玻璃「超椭圆」轮廓（对应 liquid_glass_widgets 的 `LiquidShape` /
 * `LiquidRoundedRectangle`）。
 *
 * 该库的玻璃轮廓不是普通圆角矩形，而是 Lamé 超椭圆（指数 4~5）：转角比圆弧更「方」，
 * 小尺寸下更像 iOS 26 的玻璃片。此前 ZLC 的卡片/菜单全部是 RoundedCornerShape，
 * 这里补上同款轮廓，供**非折射件**（设置卡片、列表底板等）使用——
 * 折射 lens 的实现只接受 CornerBasedShape，玻璃件仍需圆角矩形。
 */
class LiquidSuperellipseShape(
    private val cornerRadius: Dp,
    private val exponent: Float = 4.5f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }
            .coerceIn(0f, minOf(size.width, size.height) / 2f)
        if (r <= 0f) return Outline.Rectangle(Rect(Offset.Zero, size))
        return Outline.Generic(superellipsePath(size.width, size.height, r, exponent))
    }
}

/** [LiquidSuperellipseShape] 的工厂（对应库中的 `LiquidRoundedRectangle(radius:)`） */
fun LiquidRoundedRectangle(radius: Dp, exponent: Float = 4.5f): Shape =
    LiquidSuperellipseShape(radius, exponent)

/** Lamé 超椭圆圆角矩形路径：四角按 |cos|^(2/n) / |sin|^(2/n) 采样（指数越大越方） */
private fun superellipsePath(w: Float, h: Float, r: Float, n: Float): Path {
    val path = Path()
    val e = (2f / n).coerceIn(0.05f, 1f)
    fun corner(cx: Float, cy: Float, sx: Float, sy: Float, fromDeg: Float, toDeg: Float) {
        val steps = 10
        for (i in 0..steps) {
            val deg = fromDeg + (toDeg - fromDeg) * i / steps
            val t = Math.toRadians(deg.toDouble())
            val x = cx + sx * r * Math.pow(abs(cos(t)), e.toDouble()).toFloat()
            val y = cy + sy * r * Math.pow(abs(sin(t)), e.toDouble()).toFloat()
            path.lineTo(x, y)
        }
    }
    path.moveTo(r, 0f)
    path.lineTo(w - r, 0f)
    corner(w - r, r, 1f, -1f, 90f, 0f)
    path.lineTo(w, h - r)
    corner(w - r, h - r, 1f, 1f, 0f, 90f)
    path.lineTo(r, h)
    corner(r, h - r, -1f, 1f, 90f, 0f)
    path.lineTo(0f, r)
    corner(r, r, -1f, -1f, 0f, 90f)
    path.close()
    return path
}

fun Modifier.liquidGlass(
    shape: Shape,
    tintAlpha: Float? = null,
    tintColor: Color = Color.Unspecified,
    lensHeight: Dp = 16.dp,
    lensAmount: Dp = 34.dp,
    highlight: Highlight? = DefaultGlassHighlight,
    shadow: Shadow? = null,
    innerShadow: InnerShadow? = null,
    lensEnabled: Boolean = true,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    blurOverride: (() -> Float?)? = null,
    // 模糊下限（px 计算前）：弹窗等可读性敏感组件传 12.dp 之类，
    // 保证「通透度=1」时磨砂不至于消失（用户反馈：弹窗高斯模糊不能为零）
    minBlur: Dp = 0.dp,
    // vibrancy 颜色滤镜（SDK<33 无 lens 时的玻璃感兜底）
    vibrancy: Boolean = false,
    // 边缘折射色散（LiquidGlassWidgets 的色散/彩虹边）：中等以上尺寸的面板开启，
    // 小按钮不开启（色散着色器比普通折射贵，小面积看不出差别）
    chromatic: Boolean = false,
    // 采样源（Backdrop 基类：既可是单层 LayerBackdrop，也可是多层合成的 CombinedBackdrop）
    backdropOverride: Backdrop? = null,
    // 背景绘制钩子：每帧先画背景再回调（用于把背景另录一份做亮度采样等内容自适应）
    onDrawBackdrop: (DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit)? = null
): Modifier = composed {
    val enabled = LocalGlassEnabled.current
    // 默认采样源 = 下层总显示内容（根渐变 + 页面内容，见 [rememberGlassBackdrop]）
    val backdrop = backdropOverride ?: rememberGlassBackdrop()
    if (!enabled || backdrop == null) return@composed this
    // 通透度在这里读取（而非 CompositionLocal）：读取作用域限定在调用方，
    // 设置页拖动通透度滑条时只有使用玻璃的 chrome 元素重组，不会整树重组。
    val opacity = AppSettings.glassOpacity
    val density = LocalDensity.current
    val angleProvider = LocalGlassHighlightAngle.current
    // 模糊：通透度 0 → 42px（强磨砂），通透度 1 → 2px（清透）。
    // 范围刻意拉大，保证「通透度」滑条拖动时磨砂变化肉眼可辨（此前 8~28px 变化过小）。
    val blurPx = with(density) { (2f + (1f - opacity) * 40f).dp.toPx() }
    val minBlurPx = with(density) { minBlur.toPx() }
    val lensH = with(density) { lensHeight.toPx() }
    val lensA = with(density) { lensAmount.toPx() }
    // 宽按钮（全宽/半宽）的边缘高光占比过高，转动设备时高光移动反而不明显——
    // 按实测宽度分档压低高光 alpha（onSizeChanged 写同档值时 no-op，不触发重组）。
    var measuredWidthPx by remember { mutableStateOf(0) }
    val hiScale = when (with(density) { measuredWidthPx.toDp() }) {
        in 480.dp..Float.MAX_VALUE.dp -> 0.55f   // 全宽按钮
        in 240.dp..480.dp -> 0.75f               // 半宽按钮
        else -> 1f
    }
    val sizedHighlight = highlight?.let { it.copy(alpha = it.alpha * hiScale) }
    // 表面色调层：深色模式必须掺黑，否则玻璃会把背后的亮照片「提亮」，
    // 叠出来仍是一块发白的玻璃（此前深色模式下玻璃件一律偏亮）。
    val dark = LocalDarkTheme.current
    val surfaceTint = if (tintColor == Color.Unspecified) {
        if (dark) Color.Black else Color.White
    } else {
        tintColor
    }
    // 深色模式的加深乘子同样跟随主题 500ms 渐变（而非瞬时翻转）：
    // 瞬时翻转会让切换窗口里「已渐变的 tint 色」被「未渐变的乘子」二次提亮/压暗，
    // 顶栏/面板在过渡窗里发灰（用户反馈「切换深浅模式顶部变灰」）。
    val darkBoost by animateFloatAsState(
        targetValue = if (dark) 1.35f else 1f,
        animationSpec = tween(durationMillis = 500),
        label = "glassDarkBoost"
    )
    // 全局通透度对「显式写死浓度」的组件（菜单 / 弹窗 / 按钮）同样生效：
    // 以 0.5 为基准（默认观感不变），越低越磨砂、越高越清透。
    // 否则设置页的通透度滑条只会影响默认组件，菜单和弹窗纹丝不动。
    val liquidScale = 1f + (0.5f - opacity) * 0.9f
    val blurScale = 1f + (0.5f - opacity) * 1.4f
    val tint = ((tintAlpha?.let { it * liquidScale } ?: glassTintAlpha(opacity)) * darkBoost)
        .coerceAtMost(0.72f)
    // 镜面高光（LiquidGlassWidgets `GlassGlow`）：左上到右下一条极淡的白色斜向光泽，
    // 通透度越高越明显（磨砂很重时高光会被散射吃掉）。
    val glowAlpha = (0.03f + 0.09f * opacity) * if (dark) 0.72f else 1f
    // 效果链缓存：消除动画/重组期间每帧新建原生 RenderEffect
    val effectCache = remember { GlassEffectCache() }
    // 外投影：极轻的「浮起」。液态玻璃主要靠折射/散射表达层次，阴影只给一点点，
    // 否则会叠出一圈灰边（用户反馈的「淡灰叠加层」来源之一）。
    val sh = shadow ?: Shadow(
        radius = 8.dp,
        offset = DpOffset(0.dp, 1.dp),
        color = Color.Black,
        alpha = 0.08f
    )
    this
        .onSizeChanged { measuredWidthPx = it.width }
        .drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            // blurOverride（动画/菜单形变）与 minBlur（可读性下限）取大者；
            // 显式模糊同样跟随全局通透度（blurScale），clamp 后半径稳定，效果链缓存不会被击穿
            val ov = blurOverride?.invoke()
            val b = maxOf(if (ov != null) ov * blurScale else blurPx, minBlurPx)
            effectCache.apply(
                scope = this,
                blurRadiusPx = b,
                lensHeightPx = lensH,
                lensAmountPx = lensA,
                lensEnabled = lensEnabled && Build.VERSION.SDK_INT >= 33,
                vibrancyEnabled = vibrancy,
                chromatic = chromatic
            )
        },
        // 高光固定为白色（HighlightStyle.Default 的 color = White α0.5），
        // 照射角随设备倾角旋转（陀螺仪 / 旋转矢量传感器）；
        // alpha 已按组件宽度分档压低（宽按钮转动时高光位移更明显）
        highlight = sizedHighlight?.let { base ->
            {
                val style = base.style
                if (style is HighlightStyle.Default) {
                    base.copy(style = HighlightStyle.Default(angle = angleProvider()))
                } else {
                    base
                }
            }
        },
        shadow = { sh },
        innerShadow = innerShadow?.let { { it } },
        layerBlock = layerBlock,
        onDrawBackdrop = onDrawBackdrop ?: { drawBackdrop -> drawBackdrop() },
        onDrawSurface = {
            if (tint > 0f) drawRect(surfaceTint.copy(alpha = tint))
            // 镜面光泽：斜向线性渐变，只在左上一带最亮，向右下迅速消失
            if (glowAlpha > 0.005f) {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = glowAlpha),
                            Color.White.copy(alpha = glowAlpha * 0.35f),
                            Color.Transparent
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width * 0.9f, size.height)
                    )
                )
            }
        }
    )
}

/**
 * iOS 风格玻璃开关：轨道是液态玻璃胶囊，滑块是液态玻璃球，位置用弹簧动画。
 * 未启用玻璃时退回原生 M3 [Switch]（保留原样式与无障碍语义）。
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // 优先根采样层：设置页整页内容本身是被记录的采样层，
    // 开关若采样页面层会把自己画进去，产生反馈式频闪
    val backdrop = LocalGlassRootBackdrop.current ?: LocalGlassBackdrop.current
    if (!LocalGlassEnabled.current || backdrop == null) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
        return
    }
    // 全对齐 Kyant0 演示：改用 catalog 的液态玻璃开关（阻尼拖拽 + 弹簧回弹 + 折射）。
    // 禁用态（如「按钮回弹」在液态玻璃界面下不生效）也保持玻璃外观——
    // 只是手势整体失效（不切换、不拖动、不按压缩放），避免整行出现一颗
    // 原生 MD3 开关破坏玻璃一体感，也避免禁用态被拖动后状态与显示不一致。
    LiquidToggle(
        selected = { checked },
        onSelect = { if (enabled) onCheckedChange(it) },
        backdrop = backdrop,
        modifier = modifier,
        enabled = enabled
    )
}

/**
 * iOS 风格玻璃滑条：白色半透明轨道 + 高亮已选段 + 玻璃圆球滑块。
 * [steps] 与 M3 Slider 语义一致（分段数）；每跨一档触发一次 [HapticController.soft]。
 * 未启用玻璃时退回原生 M3 [Slider]。
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true
) {
    if (!LocalGlassEnabled.current) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled
        )
        return
    }
    val haptic = rememberHapticFeedback()
    val curValue by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    val start = valueRange.start
    val span = (valueRange.endInclusive - start).coerceAtLeast(1e-6f)
    val intervals = (steps + 1).coerceAtLeast(1)
    var widthPx by remember { mutableStateOf(1f) }
    val thumbR = 13.dp
    val trackH = 4.dp
    val hPad = 14.dp
    val density = LocalDensity.current
    val padPx = with(density) { hPad.toPx() }

    fun emitAt(x: Float) {
        val usable = (widthPx - padPx * 2).coerceAtLeast(1f)
        val raw = ((x - padPx) / usable).coerceIn(0f, 1f)
        // 有档位则吸附到最近档
        val snapped = if (steps > 0) {
            (raw * intervals).roundToInt().coerceIn(0, intervals) / intervals.toFloat()
        } else raw
        val prevIdx = ((curValue - start) / span * intervals).roundToInt()
        val nextIdx = (snapped * intervals).roundToInt()
        if (steps > 0 && nextIdx != prevIdx) haptic.soft()
        onChange((start + snapped * span).coerceIn(valueRange.start, valueRange.endInclusive))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(enabled, steps, start, span) {
                if (!enabled) return@pointerInput
                detectTapGestures { emitAt(it.x) }
            }
            .pointerInput(enabled, steps, start, span) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { emitAt(it.x) },
                    onDrag = { change, _ ->
                        change.consume()
                        emitAt(change.position.x)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val left = padPx
            val right = size.width - padPx
            val frac = ((value - start) / span).coerceIn(0f, 1f)
            val th = trackH.toPx()
            val cr = CornerRadius(th / 2f)
            val trackWidth = right - left
            // 轨道（未填充段：浅灰，iOS 风格细轨）
            drawRoundRect(
                color = Color(0xFFC7C7CC).copy(alpha = 0.60f),
                topLeft = Offset(left, cy - th / 2f),
                size = Size(trackWidth, th),
                cornerRadius = cr
            )
            // 已填充段：iOS 系统蓝
            val filled = trackWidth * frac
            if (filled > 0.5f) {
                drawRoundRect(
                    color = IosBlue,
                    topLeft = Offset(left, cy - th / 2f),
                    size = Size(filled, th),
                    cornerRadius = cr
                )
            }
            // 滑块：白色圆 + 柔和投影 + 极淡细描边（干净，无奇怪渐变）
            val cx = left + trackWidth * frac
            val r = thumbR.toPx()
            drawCircle(Color.Black.copy(alpha = 0.14f), radius = r, center = Offset(cx, cy + 1.dp.toPx()))
            drawCircle(Color.White, radius = r, center = Offset(cx, cy))
            drawCircle(
                color = Color.Black.copy(alpha = 0.06f),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(0.5.dp.toPx())
            )
        }
    }
}

/** 玻璃面板：启用玻璃时为液态玻璃表面；否则为 MD3 容器色圆角面板 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tintAlpha: Float? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalGlassEnabled.current
    Box(
        modifier = modifier.then(
            if (glass) Modifier.liquidGlass(shape, tintAlpha = tintAlpha)
            else Modifier.background(fallbackColor, shape)
        ),
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * 液态玻璃复选框（弹窗内使用，替代 MD3 [com.zsz.zlivephoto.ui.Md3Checkbox]）：
 * - 未勾选：22dp 玻璃小方框（轻磨砂，透出弹窗玻璃下方的内容）+ 细轮廓线；
 * - 勾选：主题色实心 + 白色勾号（180ms 路径描边动画），切换瞬间轻微回弹缩放。
 * 采样走默认解析（弹窗作用域 = 「根 + 页面」合成层），与弹窗玻璃同一来源。
 * 未启用玻璃时回落 MD3 复选框（此时弹窗本身也是原生 AlertDialog）。
 */
@Composable
fun GlassCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassEnabled.current
    if (!glass) {
        com.zsz.zlivephoto.ui.Md3Checkbox(checked = checked, modifier = modifier)
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    val checkColor = MaterialTheme.colorScheme.onPrimary
    val checkProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "glassCheckStroke"
    )
    // 勾选切换时的回弹缩放（0→1 过程中峰值 +16%）
    val pop by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 520f),
        label = "glassCheckPop"
    )
    val shape = RoundedCornerShape(6.dp)
    val outlineColor = animatedThemeColor(Color.White.copy(0.62f), Color.Black.copy(0.45f))
    Box(
        modifier = modifier
            .size(22.dp)
            .graphicsLayer {
                val s = 1f + 0.64f * pop * (1f - pop)
                scaleX = s
                scaleY = s
            }
            .then(
                if (checked) {
                    Modifier.background(accent, shape)
                } else {
                    Modifier
                        .liquidGlass(
                            shape = shape,
                            tintAlpha = 0.10f,
                            lensHeight = 8.dp,
                            lensAmount = 12.dp
                        )
                        .border(1.5.dp, outlineColor, shape)
                }
            ),
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

/**
 * 演示版（Kyant0 catalog `LiquidButton`）同款按钮物理效果：
 * - **按下**：触点附近径向浮起一圈白色高光（[InteractiveHighlight]，SDK≥33 用
 *   RuntimeShader 径向衰减，低版本整面均匀提亮），整体轻微放大；
 * - **拖拽**：位移经 `tanh` 阻尼（越小越「粘滞」），轮廓沿拖动方向液态拉伸
 *   （cos/sin 分解到 X/Y），像一颗被拽动的水滴；
 * - **松手**：弹簧回弹到原位。
 *
 * 变换只需写在 `liquidGlass(layerBlock = ...)` 一处：backdrop 库的
 * `drawBackdrop` 内部是 `graphicsLayer(layerBlock).then(DrawBackdropElement)`，
 * 该 graphicsLayer **包裹整个节点**（玻璃表面 + 内容），因此按钮与图标
 * 作为整体变形，绝不会出现「按钮跑了、图标没跟上」。
 */
class GlassButtonPhysics internal constructor(
    val highlight: InteractiveHighlight,
    private val maxOffset: Dp
) {
    val transform: GraphicsLayerScope.() -> Unit = {
        val progress = highlight.pressProgress
        val h = size.height.coerceAtLeast(1f)
        val w = size.width.coerceAtLeast(1f)
        val maxDim = size.maxDimension.coerceAtLeast(1f)
        // 按下：整体放大一档（最多 +maxOffset/height）
        val scale = lerp(1f, 1f + maxOffset.toPx() / h, progress)
        // 拖拽位移：tanh 阻尼——位移越大，同样拖拽距离造成的位移越小（粘滞感）
        val maxOffsetPx = size.minDimension.coerceAtLeast(1f)
        val initialDerivative = 0.05f
        val offset = highlight.offset
        translationX = maxOffsetPx * tanh(initialDerivative * offset.x / maxOffsetPx)
        translationY = maxOffsetPx * tanh(initialDerivative * offset.y / maxOffsetPx)
        // 拖拽形变：沿拖动方向把轮廓「拽长」（横向拖 → 拉宽，纵向拖 → 拉高）
        val maxDragScale = maxOffset.toPx() / h
        val offsetAngle = atan2(offset.y, offset.x)
        scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / maxDim) *
            (w / h).coerceAtMost(1f)
        scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / maxDim) *
            (h / w).coerceAtMost(1f)
    }
}

/** 记忆一份 [GlassButtonPhysics]（每个按钮一份，互不干扰） */
@Composable
fun rememberGlassButtonPhysics(maxOffset: Dp = 4.dp): GlassButtonPhysics {
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(animationScope = scope) }
    return remember(highlight, maxOffset) { GlassButtonPhysics(highlight, maxOffset) }
}

/**
 * 圆形玻璃图标按钮（返回箭头等）。
 * 图标槽建议放 [AppleChevron]（苹果风格「<」）。
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
    // 内容自适应着色：开启后把玻璃折射到的背景录一份做亮度采样，
    // 图标/文字按背景亮暗自动切换黑/白（参考 Kyant0 演示的 AdaptiveLuminance），
    // 适合浮在照片等多变背景上的按钮。内容请用 LocalContentColor.current 取色。
    adaptiveContent: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val haptic = rememberHapticFeedback()
    val glass = LocalGlassEnabled.current
    val dark = LocalDarkTheme.current
    // 背景亮度采样：仅在需要内容自适应时启动（每颗按钮一个采样循环，不用不开）
    val adaptive = rememberAdaptiveContentColor(
        initialColor = if (dark) Color.White else Color.Black,
        enabled = adaptiveContent && glass
    )
    // 演示版同款物理：径向高光 + tanh 阻尼拖拽 + 方向形变（见 [GlassButtonPhysics]）
    val physics = rememberGlassButtonPhysics()
    // 交互中提升层级：按下的按钮浮到同层内容之上（仍低于顶部固定栏等覆盖层，
    // 那些是后绘制的兄弟分支），避免被相邻控件/列表项压住一角
    // （用户反馈：「正在被交互的按钮要提高到这一层的最顶层」）。
    val iconInteraction = remember { MutableInteractionSource() }
    val iconPressed by iconInteraction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(size)
            .zIndex(if (iconPressed) 1f else 0f)
            .then(
                if (glass) Modifier.liquidGlass(
                    CircleShape,
                    tintAlpha = 0.06f,
                    // layerBlock 的 graphicsLayer 包裹整个节点：玻璃与图标一起变形
                    layerBlock = physics.transform,
                    onDrawBackdrop = if (adaptiveContent) adaptive.recordBackdrop else null
                )
                // MD3 模式：不要永久圆形背景板（用户反馈「永远存在的边框/背景板没必要」），
                // 保持透明，只保留按压涟漪反馈
                else Modifier
            )
            // 触点径向高光：画在玻璃之上、内容之下（链内顺序保证）
            .then(if (glass) physics.highlight.modifier else Modifier)
            .clickable(
                interactionSource = iconInteraction,
                indication = if (glass) null else LocalIndication.current
            ) { haptic.click(); onClick() }
            .then(if (glass) physics.highlight.gestureModifier else Modifier)
            .then(
                if (contentDescription == null) Modifier
                else Modifier.semantics { this.contentDescription = contentDescription }
            ),
        contentAlignment = Alignment.Center
    ) {
        // 内容与玻璃表面整体变形（layerBlock 已包裹全节点，这里只提供自适应色
        // 与居中容器；不再叠第二套 graphicsLayer，否则变换会被应用两次）。
        if (adaptiveContent && glass) {
            CompositionLocalProvider(LocalContentColor provides adaptive.color) {
                content()
            }
        } else {
            content()
        }
    }
}

/**
 * 液态玻璃拖拽状态。位移/形变交给 [Modifier.liquidGlass] 的 `layerBlock` 应用，
 * 而不是独立包一层 `graphicsLayer` —— 否则玻璃的 backdrop 采样坐标不会跟随位移，
 * 拖拽时玻璃会「脱离」它折射的背景（这是此前拖拽看起来怪异的根因）。
 */
class LiquidDragState internal constructor(internal val maxDragPx: Float) {
    internal val offsetX = Animatable(0f)
    internal val offsetY = Animatable(0f)
}

@Composable
fun rememberLiquidDragState(maxDrag: Dp = 8.dp): LiquidDragState {
    val maxDragPx = with(LocalDensity.current) { maxDrag.toPx() }
    return remember(maxDragPx) { LiquidDragState(maxDragPx) }
}

/**
 * 液态玻璃的「手抓」手势：按住后向任意方向拖出一小段（跟随手指），松手弹簧回弹。
 * 只更新 [LiquidDragState]，不自行做视觉变换（变换在 [Modifier.liquidGlass] 的 layerBlock 里）。
 */
fun Modifier.liquidDraggable(
    state: LiquidDragState,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this
    val scope = rememberCoroutineScope()
    val rebound = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
    this.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        var curX = 0f
        var curY = 0f
        // 非线性阻尼：位移越大，同样拖拽距离能改变的形变越小。
        // 起点（中心）跟手最明显，越靠近边缘越「拽不动」，不再有硬性的边缘急停。
        fun gain(offset: Float, max: Float): Float {
            val t = (offset / max).coerceIn(-1f, 1f)
            return DragFollowFactor * (1f - kotlin.math.abs(t))
        }
        detectDragGestures(
            onDragStart = {
                curX = state.offsetX.value
                curY = state.offsetY.value
            },
            onDrag = { change, drag ->
                change.consume()
                curX = (curX + drag.x * gain(curX, state.maxDragPx))
                    .coerceIn(-state.maxDragPx, state.maxDragPx)
                curY = (curY + drag.y * gain(curY, state.maxDragPx))
                    .coerceIn(-state.maxDragPx, state.maxDragPx)
                scope.launch {
                    state.offsetX.snapTo(curX)
                    state.offsetY.snapTo(curY)
                }
            },
            onDragEnd = {
                scope.launch { state.offsetX.animateTo(0f, rebound) }
                scope.launch { state.offsetY.animateTo(0f, rebound) }
            },
            onDragCancel = {
                scope.launch { state.offsetX.animateTo(0f, rebound) }
                scope.launch { state.offsetY.animateTo(0f, rebound) }
            }
        )
    }
}

/**
 * 返回按钮：玻璃生效时为圆形液态玻璃按钮 + 苹果风格箭头（[AppleChevron]）；
 * 未启用时退回原生 IconButton。所有返回入口（选择器 / 设置页及子页）共用，保证风格一致。
 * 玻璃模式下箭头颜色自适应（背后是照片时切黑/白，背后是主题渐变时跟随主题）。
 */
@Composable
fun GlassBackButton(
    onBack: () -> Unit,
    contentDescription: String? = null
) {
    val haptic = rememberHapticFeedback()
    // 与屏幕边缘留出呼吸感：TopAppBar 的 navigationIcon 槽位默认只有 4dp，
    // 玻璃圆钮贴边会显得局促（圆钮直径 40dp，8dp 内缩后观感与 iOS 一致）。
    val inset = Modifier.padding(start = 8.dp)
    if (LocalGlassEnabled.current) {
        GlassIconButton(
            onClick = onBack,
            modifier = inset,
            contentDescription = contentDescription,
            adaptiveContent = true
        ) {
            AppleChevron(tint = LocalContentColor.current)
        }
    } else {
        IconButton(onClick = { haptic.click(); onBack() }, modifier = inset) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = contentDescription)
        }
    }
}

/**
 * 苹果风格返回箭头（比 Material 的「<」更「开」：两臂夹角 [angleDeg]，圆头描边）。
 * RTL 下整体镜像（指向右侧）。
 */
@Composable
fun AppleChevron(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    strokeWidth: Dp = 2.2.dp,
    angleDeg: Float = 90f
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier
            .size(18.dp)
            .graphicsLayer { if (rtl) scaleX = -1f }
    ) {
        val apexX = size.width * 0.34f
        val cy = size.height * 0.5f
        val r = size.minDimension * 0.38f
        val half = Math.toRadians((angleDeg / 2f).toDouble())
        val dx = (r * cos(half)).toFloat()
        val dy = (r * sin(half)).toFloat()
        val path = Path().apply {
            moveTo(apexX + dx, cy - dy)
            lineTo(apexX, cy)
            lineTo(apexX + dx, cy + dy)
        }
        drawPath(
            path,
            color = tint,
            style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** 菜单卡内缩（FlClash `_cardInset`）：行像浮在卡面上的一圈留白 */
private val MenuCardInset = 8.dp
/** 菜单行圆角（FlClash `_itemRadius = AppCorner.md`） */
private val MenuItemCorner = 16.dp
/** 菜单卡圆角（FlClash `_cardRadius = _itemRadius + _cardInset`） */
private val MenuCardCorner = 24.dp

/**
 * 玻璃下拉菜单覆盖层（同一 Composition 树内的浮层，弹窗/菜单也必须这样写）。
 * 用法：把屏幕根节点包成 Box，然后在其内部调用本函数（先画的在下面）。
 *
 * **版式与动效照搬 FlClash `lib/ui/popup.dart` 的 `CommonPopupMenu`**：
 * - 整张菜单是**一层**玻璃卡（卡内缩 8dp、圆角 24dp），行是卡面上的普通条目
 *   （圆角 16dp、内边距 12dp，按压只给一层 highlight 底 + 轻微收缩），
 *   行本身不再是独立玻璃按钮；
 * - 玻璃取 `LiquidGlassStyle.menu`（12dp 模糊 + 深色 63% / 浅色 55% 亮面）；
 * - 入场：一条 stiffness 400 / damping 0.754 的弹簧（FlClash `_open`）；
 * - 二级子菜单：一张**独立卡片盖在一级之上**，从所点击的那一行的矩形长到
 *   整卡（圆角 16dp→24dp），卡片**比一级宽 12%** 并向左长出；卡顶把开启它的
 *   那一行**重画一遍**当 header，于是看起来是从这行里长出来的；行内容在
 *   展开 5%..40% 之间淡入（FlClash `_items`）。展开是 420ms/bounce 0.15 的
 *   弹簧，折回是 340ms 无回弹（FlClash `_foldSpring`）；同时一级卡片缩小到
 *   0.95（原点右上）并压暗 20%。
 */
@Composable
fun BoxScope.GlassMenuOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    alignment: Alignment = Alignment.TopEnd,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = MenuCardCorner,
    // ── 二级子菜单（一张卡片盖在一级之上）──
    // 展开态；收起时折回所点击的那一行
    submenuVisible: Boolean = false,
    // 所点击父行的根坐标矩形（由 GlassMenuEntry.onAnchor 回填）；
    // null = 兜底从一级卡片顶部展开
    submenuAnchor: Rect? = null,
    // 子卡顶部重画的父行（标签 + 当前值）；null = 不加 header
    submenuTitle: String? = null,
    submenuValue: String? = null,
    // 仅收起二级（保留一级菜单）
    onSubmenuDismiss: () -> Unit = {},
    submenuContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    // 开合进度（FlClash `_open`：stiffness 400 / damping 0.754，mass 1，约 450ms 落定）
    val openProgress = remember { Animatable(0f) }
    // 关闭进度（FlClash 无 sourceImage 分支 `_dismissScale` 0.8 + 淡出，200ms）。
    // 与 openProgress 分开：打开时裁剪形变负责「从小长到大」、scale 恒 1；
    // 关闭时整体缩到 0.8 + 淡出。两者共用一条 openProgress 会让打开时 scale
    // 也从 0.8→1，与裁剪形变双重缩放，观感卡顿。
    val dismissProgress = remember { Animatable(0f) }
    val haptic = rememberHapticFeedback()
    // 退场动画播完才退出组合：组合期读 progress.value 会让动画每一帧
    // 重组整棵菜单子树（并顺带重建子树内每个玻璃件的效果链）
    var contentActive by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            contentActive = true
            dismissProgress.snapTo(0f)
            openProgress.snapTo(0f)
            openProgress.animateTo(
                1f,
                spring(dampingRatio = 0.754f, stiffness = 400f)
            )
        } else {
            // FlClash 关闭（无 sourceImage 分支）：easeOut 缩到 0.8 + 淡出
            // （`_shrinkDuration` 200ms / `_dismissScale` 0.8，见 popup.dart `_RenderPopupMorph.paint`）
            dismissProgress.animateTo(
                1f,
                tween(durationMillis = 200, easing = FastOutSlowInEasing)
            )
            contentActive = false
        }
    }
    // 子菜单开合（FlClash `_unfoldSpring` 420ms/bounce 0.15、`_foldSpring` 340ms 无回弹）。
    // Flutter `withDurationAndBounce(420ms, 0.15)` 解出的欠阻尼弹簧约 damping 0.55；
    // 折回无 bounce → 临界阻尼（damping 1.0）。均用刚度 400 保持与一级一致的手感。
    val subProgress = remember { Animatable(0f) }
    var submenuActive by remember { mutableStateOf(submenuVisible) }
    LaunchedEffect(submenuVisible) {
        if (submenuVisible) {
            submenuActive = true
            // FlClash `HapticFeedback.selectionClick()`：二级展开瞬间清脆反馈
            haptic.click()
            subProgress.animateTo(
                1f,
                spring(dampingRatio = 0.55f, stiffness = 400f)
            )
        } else {
            // 仅当之前确实展开过才给折回反馈（首次组合 submenuVisible=false 不触发）
            if (submenuActive) haptic.click()
            subProgress.animateTo(
                0f,
                spring(dampingRatio = 1f, stiffness = 400f)
            )
            submenuActive = false
        }
    }
    if (!contentActive) return
    val glass = LocalGlassEnabled.current
    val density = LocalDensity.current
    val alignEnd = alignment != Alignment.TopStart && alignment != Alignment.BottomStart
    val alignBottom = alignment == Alignment.BottomEnd || alignment == Alignment.BottomCenter
    val menuShape = RoundedCornerShape(cornerRadius)
    val menuDark = LocalDarkTheme.current
    // 菜单玻璃（FlClash `LiquidGlassStyle.menu`）
    val menuTintColor = if (menuDark) MaterialTheme.colorScheme.surfaceContainerHighest
    else MaterialTheme.colorScheme.surfaceBright
    val menuTintAlpha = if (menuDark) 0.63f else 0.55f
    val menuBlurPx = with(density) { 12.dp.toPx() }
    // FlClash `menuShadows`：一道 29dp 模糊的软影，深色加强一档
    val menuShadow = Shadow(radius = 29.dp, alpha = if (menuDark) 0.18f else 0.12f)
    val dimColor = MaterialTheme.colorScheme.surface
    // 菜单是玻璃件：文字按折射到的背景亮度自动切黑/白
    val menuAdaptive = rememberAdaptiveContentColor(
        initialColor = if (menuDark) Color.White else Color.Black,
        enabled = glass
    )
    // 容器与一级卡片的根坐标矩形：二级卡片按它们定位
    var containerOrigin by remember { mutableStateOf(Rect.Zero) }
    var cardOrigin by remember { mutableStateOf(Rect.Zero) }
    var cardWidthPx by remember { mutableStateOf(0f) }

    // 透明点击层：菜单外的手势关闭菜单（FlClash 的弹出菜单没有压暗层）
    Box(
        Modifier
            .matchParentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    )
    // 整屏容器：二级卡片比一级宽 12%（FlClash）并向左溢出，
    // 容器必须够大，溢出的那一截才能接收触摸（否则左缘一条点不动）
    Box(
        Modifier
            .matchParentSize()
            .onGloballyPositioned { containerOrigin = it.boundsInRoot() }
    ) {
        // 二级展开时多一层：卡外区域点击 = 只折回一级（FlClash `_pop`）。
        // 必须画在一级卡片**之下**，否则父项行（三路状态机）收不到点击。
        if (submenuActive) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSubmenuDismiss() }
            )
        }
        // ── 一级卡片：圆形按钮轮廓 → 菜单外轮廓的溶解形变 ──
        Column(
            modifier
                .align(alignment)
                .onGloballyPositioned {
                    cardOrigin = it.boundsInRoot()
                    cardWidthPx = it.size.width.toFloat()
                }
                // 形变裁剪：起点 40dp 圆锚在触发按钮所在角，终点菜单外轮廓；
                // 绘制期每帧重建 Path，绕开 clip 节点的 outline 缓存
                .drawWithContent {
                    val p = openProgress.value
                    val startSize = 40.dp.toPx()
                    val w = lerp(startSize, size.width, p)
                    val h = lerp(startSize, size.height, p)
                    val r = lerp(startSize / 2f, cornerRadius.toPx(), p)
                    val left = if (alignEnd) size.width - w else 0f
                    val top = if (alignBottom) size.height - h else 0f
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left,
                                top = top,
                                right = left + w,
                                bottom = top + h,
                                cornerRadius = CornerRadius(r, r)
                            )
                        )
                    }
                    clipPath(path) { this@drawWithContent.drawContent() }
                }
                .graphicsLayer {
                    // 打开/落定时不透明；关闭时淡出（FlClash `_dismissScale` 淡出）
                    alpha = 1f - dismissProgress.value
                    // FlClash 关闭：菜单整体缩到 0.8（`_dismissScale`）并向锚点角收缩；
                    // 打开时由 clip 形变负责「从小长到大」，scale 保持 1（dismissProgress=0）。
                    val closeShrink = 1f - 0.2f * dismissProgress.value
                    // FlClash：子菜单开启时一级卡片再缩小 5%，原点在**右上角**
                    // （子卡右缘与父卡右缘对齐，父卡因此向左上收）
                    val dim = subProgress.value
                    val subShrink = 1f - 0.05f * dim
                    val s = closeShrink * subShrink
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .then(
                    if (glass) Modifier.liquidGlass(
                        shape = menuShape,
                        // FlClash `LiquidGlassStyle.menu`：深色 63% 深灰 / 浅色 55% 亮面
                        tintAlpha = menuTintAlpha,
                        tintColor = menuTintColor,
                        lensHeight = 16.dp,
                        lensAmount = 20.dp,
                        // FlClash `menuShadows`：一道 29dp 模糊、下移 8dp 的软影
                        shadow = menuShadow,
                        vibrancy = Build.VERSION.SDK_INT < 33,
                        // 菜单面积大，折射色散（彩虹边）能看出来
                        chromatic = Build.VERSION.SDK_INT >= 33,
                        blurOverride = { menuBlurPx },
                        onDrawBackdrop = menuAdaptive.recordBackdrop
                    )
                    else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, menuShape)
                )
                .clip(menuShape)
                // 子菜单开启时压暗一级卡片（FlClash：surface 20% × 层级）
                .drawWithContent {
                    drawContent()
                    val dim = subProgress.value
                    if (dim > 0f) drawRect(dimColor.copy(alpha = 0.20f * dim))
                }
                // 子菜单展开期间，一级卡片上的点击 = 折回一级
                .then(
                    if (submenuVisible) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSubmenuDismiss() }
                    else Modifier
                )
                // 卡内缩 8dp（FlClash `_cardInset`）：菜单行像浮在卡面上的圆角块
                .padding(MenuCardInset)
        ) {
            val contentBlurPx = with(density) { 4.dp.toPx() }
            CompositionLocalProvider(LocalContentColor provides menuAdaptive.color) {
                Column(
                    Modifier.graphicsLayer {
                        // 行比轮廓再晚一拍出现（FlClash 的内容渐显）
                        alpha = ((openProgress.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
                        // FlClash `_openContentBlur`：打开时内容 4px 模糊 → 落定清晰；
                        // 关闭走 dismissProgress 整体淡出（不再反向重模糊）。
                        // 绘制期读 value + 小半径每帧 createBlurEffect：不触发重组，
                        // 4px 半径开销可忽略（区别于 16dp 大半径的掉帧问题）。
                        // 0.5px 量化：动画期只有少数几档半径变化，避免每帧新建
                        // 原生 RenderEffect（每帧分配原生对象是形变期掉帧来源之一）
                        val blurRadius = (contentBlurPx * (1f - openProgress.value).coerceIn(0f, 1f) * 2f)
                            .roundToInt() / 2f
                        renderEffect = if (blurRadius > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            android.graphics.RenderEffect.createBlurEffect(
                                blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        } else null
                    },
                    content = content
                )
            }
        }

        // ── 二级卡片：叠在一级之上，从所点击的那一行长出来 ──
        GlassSubmenuOverlay(
            contentActive = submenuActive,
            progress = subProgress,
            anchor = submenuAnchor,
            containerOrigin = containerOrigin,
            cardOrigin = cardOrigin,
            cardWidthPx = cardWidthPx,
            title = submenuTitle,
            value = submenuValue,
            onDismiss = onSubmenuDismiss,
            content = submenuContent
        )
    }
}

/**
 * 二级子菜单卡片（照搬 FlClash `CommonPopupMenu` 的层级卡片）：
 * 一张独立玻璃卡**盖在一级之上**，从所点击那一行的矩形长到整卡——
 * 卡顶 = 该行顶边 - 卡内缩（8dp），卡宽 = 一级卡宽 × 1.12（右缘对齐、向左长出），
 * 形变矩形与圆角（16dp→24dp）在绘制期按弹簧进度插值。
 * 卡片顶部把开启它的那一行**重画一遍**当 header（FlClash 的做法），
 * 点 header 或卡片外区域即折回一级（反向弹簧）。
 */
@Composable
private fun BoxScope.GlassSubmenuOverlay(
    contentActive: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    anchor: Rect?,
    containerOrigin: Rect,
    cardOrigin: Rect,
    cardWidthPx: Float,
    title: String?,
    value: String?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    // 折回动画播完（contentActive=false）才退出组合，
    // 避免组合期读 progress.value 导致每帧重组
    if (!contentActive) return
    val glass = LocalGlassEnabled.current
    val density = LocalDensity.current
    val insetPx = with(density) { MenuCardInset.toPx() }
    val subShape = RoundedCornerShape(MenuCardCorner)
    val panelDark = LocalDarkTheme.current
    val panelAdaptive = rememberAdaptiveContentColor(
        initialColor = if (panelDark) Color.White else Color.Black,
        enabled = glass
    )
    val panelTint = if (panelDark) MaterialTheme.colorScheme.surfaceContainerHighest
    else MaterialTheme.colorScheme.surfaceBright
    val panelTintAlpha = if (panelDark) 0.63f else 0.55f
    val blurPx = with(density) { 12.dp.toPx() }
    val panelShadow = Shadow(radius = 29.dp, alpha = if (panelDark) 0.18f else 0.12f)
    // FlClash `_levelWidthScale`：子卡比父卡宽 12%（右缘对齐，向左长出）
    val subWidthPx = (cardWidthPx * 1.12f).coerceAtLeast(cardWidthPx)
    val hasFrame = anchor != null && cardOrigin != Rect.Zero && cardWidthPx > 0f
    // 卡片顶部（相对整屏容器）：父行顶边 - 卡内缩 → 卡内 header 行正好压住父行
    val topPx = if (hasFrame) {
        (anchor!!.top - containerOrigin.top - insetPx).coerceAtLeast(0f)
    } else 0f
    // 右缘对齐父卡：容器右缘 → 父卡右缘 的差
    val rightPx = if (hasFrame && containerOrigin != Rect.Zero) {
        (cardOrigin.right - containerOrigin.right).toFloat()
    } else 0f
    // 形变起点（子卡局部坐标）= 父行矩形。子卡右缘与父卡右缘对齐、
    // 比父卡宽 12%，且父卡有 8dp 内缩，所以父行左缘在子卡局部坐标里
    // 要减去“父卡右缘 → 子卡右缘”的差再减内缩：
    //   x_local = x_root - (cardOrigin.right - subWidthPx)
    val startTop = insetPx
    val startBottom = insetPx + (anchor?.height ?: 0f)
    val startLeft = (subWidthPx - cardWidthPx + insetPx).coerceAtLeast(0f)
    val startRight = (subWidthPx - insetPx).coerceAtLeast(startLeft + 1f)
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .offset { IntOffset(rightPx.roundToInt(), topPx.roundToInt()) }
            .width(with(density) { subWidthPx.toDp() })
            .heightIn(max = 400.dp)
            // 形变：父行矩形 → 整卡（圆角 16dp → 24dp），绘制期插值
            .drawWithContent {
                val t = progress.value.coerceIn(0f, 1.08f)
                val target = Rect(0f, 0f, size.width, size.height)
                val start = Rect(startLeft, startTop, startRight, startBottom)
                val l = lerp(start.left, target.left, t)
                val top = lerp(start.top, target.top, t)
                val r = lerp(start.right, target.right, t)
                val b = lerp(start.bottom, target.bottom, t)
                val rad = lerp(
                    with(density) { MenuItemCorner.toPx() },
                    with(density) { MenuCardCorner.toPx() },
                    t.coerceIn(0f, 1f)
                )
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = l,
                            top = top,
                            right = r,
                            bottom = b,
                            cornerRadius = CornerRadius(rad, rad)
                        )
                    )
                }
                clipPath(path) { this@drawWithContent.drawContent() }
            }
            // 卡片自身消费点击：卡内留白区域的点击不穿透到一级
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }
            .then(
                if (glass) Modifier.liquidGlass(
                    shape = subShape,
                    tintAlpha = panelTintAlpha,
                    tintColor = panelTint,
                    lensHeight = 16.dp,
                    lensAmount = 20.dp,
                    shadow = panelShadow,
                    vibrancy = Build.VERSION.SDK_INT < 33,
                    blurOverride = { blurPx }
                )
                else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, subShape)
            )
            .clip(subShape)
            .padding(MenuCardInset)
    ) {
        CompositionLocalProvider(LocalContentColor provides panelAdaptive.color) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (title != null) {
                    GlassMenuHeaderRow(label = title, value = value, onClick = onDismiss)
                    GlassMenuDivider()
                }
                // 行内容在展开 5%..40% 之间淡入（FlClash `_items`）
                Column(
                    Modifier.graphicsLayer {
                        alpha = ((progress.value - 0.05f) / 0.35f).coerceIn(0f, 1f)
                    },
                    content = content
                )
            }
        }
    }
}

/**
 * 展开式下拉菜单（liquid_glass_widgets 的 `GlassMenu` 形态）。
 *
 * 用于设置页「深浅色 / 编码器 / 压缩预设」：点击一行后，在该行的正下方长出一张
 * 玻璃卡，宽度与行一致，选项用 [GlassMenuRow]（当前值打勾）。
 *
 * 与 [GlassMenuOverlay] 的区别：按**行的根坐标矩形**定位（[anchor]），而不是固定
 * 贴屏幕某个角；[containerOrigin] 是覆盖层根 Box 的根坐标（覆盖层铺满整屏时传
 * `Rect.Zero`）。卡片高度受屏幕剩余空间限制，超出时内部滚动。
 */
@Composable
fun BoxScope.GlassDropdownMenu(
    visible: Boolean,
    anchor: Rect?,
    onDismiss: () -> Unit,
    containerOrigin: Rect = Rect.Zero,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!visible || anchor == null) return
    val glass = LocalGlassEnabled.current
    val dark = LocalDarkTheme.current
    val density = LocalDensity.current
    // 入场：一条和菜单同款的弹簧；退场直接移除（下拉菜单不需要长退场）
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f))
    }
    BackHandler(enabled = true) { onDismiss() }
    val shape = RoundedCornerShape(20.dp)
    val blurPx = with(density) { 12.dp.toPx() }
    val tintColor = if (dark) MaterialTheme.colorScheme.surfaceContainerHighest
    else MaterialTheme.colorScheme.surfaceBright
    val tintAlpha = if (dark) 0.63f else 0.55f
    val fg = if (dark) Color.White else Color.Black

    // 菜单外点击关闭
    Box(
        Modifier
            .matchParentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    )
    BoxWithConstraints(Modifier.matchParentSize()) {
        val topDp = with(density) { (anchor.bottom - containerOrigin.top).toDp() } + 6.dp
        val leftDp = with(density) { (anchor.left - containerOrigin.left).toDp() }
        val widthDp = with(density) { anchor.width.toDp() }
        // 屏幕剩余高度不够时收缩，菜单内部滚动
        val maxCardHeight = (maxHeight - topDp - 12.dp).coerceAtLeast(120.dp)
        Column(
            Modifier
                .offset(x = leftDp, y = topDp)
                .width(widthDp)
                .heightIn(max = maxCardHeight)
                .graphicsLayer {
                    val p = progress.value
                    alpha = p
                    scaleX = 0.94f + 0.06f * p
                    scaleY = 0.94f + 0.06f * p
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .then(
                    if (glass) Modifier.liquidGlass(
                        shape = shape,
                        tintAlpha = tintAlpha,
                        tintColor = tintColor,
                        lensHeight = 14.dp,
                        lensAmount = 18.dp,
                        shadow = Shadow(
                            radius = 26.dp,
                            offset = DpOffset(0.dp, 6.dp),
                            alpha = if (dark) 0.18f else 0.12f
                        ),
                        vibrancy = Build.VERSION.SDK_INT < 33,
                        chromatic = Build.VERSION.SDK_INT >= 33,
                        blurOverride = { blurPx }
                    )
                    else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
                )
                .clip(shape)
                .verticalScroll(rememberScrollState())
                .padding(MenuCardInset)
        ) {
            CompositionLocalProvider(LocalContentColor provides fg) {
                Column(content = content)
            }
        }
    }
}

/**
 * 液态玻璃菜单按钮（照搬 FlClash 的 `LiquidGlass.buttonStyle` + `ElasticPress`）：
 * 每颗按钮自带一片玻璃曲面（磨砂 + 边缘折射），按下整体缩到 0.955 并带一点
 * 过冲地弹性回弹，松手后归位。未启用玻璃时退回 MD3 填色。
 *
 * 供 [GlassMenuRow] / [GlassMenuEntry] 以及主页长按「输出格式」按钮组复用。
 */
@Composable
fun GlassMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    shape: Shape = RoundedCornerShape(14.dp),
    onPositioned: ((Rect) -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val haptic = rememberHapticFeedback()
    val glass = LocalGlassEnabled.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // FlClash ElasticPress 的按压曲线：按下缩一档，松手欠阻尼弹簧回弹（带轻微过冲）
    val pressScale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        pressScale.animateTo(
            targetValue = if (pressed) 0.955f else 1f,
            animationSpec = spring(dampingRatio = 0.52f, stiffness = 900f)
        )
    }
    val tintAlpha = when {
        !enabled -> 0.04f
        selected -> 0.20f
        else -> 0.09f
    }
    // 选中项掺主题色，未选中保持中性玻璃
    val tintColor = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified
    Row(
        modifier = modifier
            .onGloballyPositioned { onPositioned?.invoke(it.boundsInRoot()) }
            .then(
                if (glass) Modifier.liquidGlass(
                    shape = shape,
                    tintAlpha = tintAlpha,
                    tintColor = tintColor,
                    lensHeight = 10.dp,
                    lensAmount = 18.dp,
                    // 按钮贴在菜单面板上，不再投外阴影（否则按钮之间出现一圈灰边）
                    shadow = Shadow(radius = 0.dp, alpha = 0f),
                    vibrancy = Build.VERSION.SDK_INT < 33,
                    // layerBlock 的 graphicsLayer 包裹整个按钮：玻璃与文字一起缩放
                    layerBlock = {
                        val s = pressScale.value
                        scaleX = s
                        scaleY = s
                    }
                )
                else Modifier
                    .graphicsLayer {
                        val s = pressScale.value
                        scaleX = s
                        scaleY = s
                    }
                    .background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else Color.Transparent,
                        shape
                    )
            )
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = if (glass) null else LocalIndication.current,
                enabled = enabled
            ) { haptic.click(); onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * 菜单行外壳（照搬 FlClash `_buildRow` / `_BuildRow` 的版式）：
 * **行本身不是玻璃件**——整张菜单只有一层玻璃（卡片），行是卡片上的普通条目；
 * 按压只给一层圆角 highlight 底 + 轻微收缩（FlClash 的 `ElasticPress` 行手感），
 * 圆角 16dp、内边距 12dp，与 FlClash `_itemRadius` / `_itemPadding` 一致。
 */
@Composable
private fun MenuRowShell(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    // 格尾有图标（箭头/对勾）时右侧内边距由 12dp 收到 8dp（FlClash `_itemArrowPadding`）
    trailingIcon: Boolean = false,
    // FlClash `_buildContent`：被二级子菜单 header 盖住的那一行要藏起来
    // （玻璃半透明，两行叠着会互相透出来重影）
    hidden: Boolean = false,
    // 根坐标矩形回填（二级子菜单的形变起点）
    onPositioned: ((Rect) -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // FlClash ElasticPress 的手感：按下收缩一档，松手欠阻尼弹簧带轻微过冲回弹
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 900f),
        label = "menuRowPress"
    )
    val highlight = LocalContentColor.current.copy(alpha = 0.10f)
    Row(
        modifier = modifier
            .onGloballyPositioned { onPositioned?.invoke(it.boundsInRoot()) }
            .graphicsLayer {
                alpha = if (hidden) 0f else 1f
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressed) highlight else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(
                start = 12.dp,
                top = 12.dp,
                bottom = 12.dp,
                end = if (trailingIcon) 8.dp else 12.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * 菜单子项（二级子菜单里的选项）：普通行 + 右侧 ✓（FlClash 的 checked 行）。
 * 选中时文字加粗并显示对勾，图标 16dp、距文字 8dp。
 */
@Composable
fun GlassMenuRow(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val fg = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f)
    val haptic = rememberHapticFeedback()
    MenuRowShell(
        onClick = {
            // FlClash `HapticFeedback.selectionClick()`：选中子项清脆反馈
            haptic.click()
            onClick()
        },
        enabled = enabled,
        trailingIcon = selected
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = fg
            )
        }
    }
}

/**
 * 菜单父项的内容（FlClash `_buildRow`）：左侧两行——标签 + 当前值副标题
 * （footnote 级、更暗），右侧「>」箭头；[expanded] 时箭头旋转 90° 指向上方
 * （FlClash 子菜单开启后箭头转过 1/4 圈）。
 */
@Composable
private fun RowScope.MenuEntryContent(
    label: String,
    value: String?,
    expanded: Boolean,
    enabled: Boolean
) {
    val fg = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "menuEntryChevron"
    )
    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!value.isNullOrEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = fg.copy(alpha = if (enabled) 0.72f else 0.32f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Spacer(Modifier.width(8.dp))
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        modifier = Modifier.size(16.dp).rotate(rotation),
        tint = fg.copy(alpha = if (enabled) 0.6f else 0.3f)
    )
}

/**
 * 玻璃菜单的父项：标签 + 当前值副标题 + 右侧「>」箭头，点击由调用方切换展开态。
 * 一级菜单用它承载「输出格式 / 排序字段 / 排序顺序 / 显示类别」四个入口。
 *
 * @param onAnchor 回填本项在**根坐标系**的矩形（onGloballyPositioned 采集），
 *   调用方把它存下来传给 GlassMenuOverlay 的 submenuAnchor——二级卡片的
 *   形变起点就是这一行的矩形（FlClash `_MenuStep.itemTop/itemHeight`）。
 * @param enabled 二级展开期间由一级卡片统一接管点击，行可保持可点。
 */
@Composable
fun GlassMenuEntry(
    label: String,
    value: String?,
    onClick: () -> Unit,
    expanded: Boolean = false,
    enabled: Boolean = true,
    onAnchor: ((Rect) -> Unit)? = null
) {
    MenuRowShell(
        onClick = onClick,
        enabled = enabled,
        trailingIcon = true,
        onPositioned = onAnchor,
        // 该行开着二级子菜单 → 由子卡 header 重画，本行藏起来（FlClash covered）
        hidden = expanded
    ) {
        MenuEntryContent(label = label, value = value, expanded = expanded, enabled = enabled)
    }
}

/**
 * 二级卡片顶部的父项行（FlClash 的做法）：子卡片把开启它的那一行**重画一遍**
 * 作为 header，于是子菜单看起来是从这一行里长出来的；点它折回一级。
 */
@Composable
private fun GlassMenuHeaderRow(label: String, value: String?, onClick: () -> Unit) {
    MenuRowShell(onClick = onClick, enabled = true, trailingIcon = true) {
        MenuEntryContent(label = label, value = value, expanded = true, enabled = true)
    }
}

/** 菜单分组分隔线（FlClash `_dividerHeight` = 卡内缩 ×2 + 1） */
@Composable
private fun GlassMenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(17.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .background(LocalContentColor.current.copy(alpha = 0.10f))
    )
}

/** iOS 系统蓝：弹窗的肯定按钮用色 */
private val IosBlue = Color(0xFF0A84FF)

/**
 * 液态玻璃弹窗（与 MD3 [AlertDialog] 同签名的替代品）。
 *
 * 为什么不用 [AlertDialog]：它自带的 Surface 是不透明容器，玻璃无从透出。
 * 为什么不用独立 [Dialog] 窗口：backdrop 库要求玻璃元素与被采样内容在
 * **同一 Compose 树**内——独立窗口采样坐标错误（窗口缩放时不跟随，
 * 点击映射错位，用户反馈 #5），且弹窗窗口拿到焦点后主窗口停绘、
 * 采样层不再刷新，模糊因此陈旧不实时（用户反馈 #6）。
 * 所以这里是**同窗口覆盖层**：由调用方放在页面根 Box 内（采样层之外），
 * 玻璃折射的即是页面真实内容，坐标与主窗口严格一致、每帧刷新。
 *
 * 进入/退出动画：面板整块高斯模糊（16dp → 0）+ 淡入淡出 + 轻微缩放，
 * 遮罩同步淡入淡出；退出采用 220ms 缓动，退场期间内容继续留存，
 * **退场动画结束后才退出组合**。
 * 因此调用方不能再写 `if (flag) { LiquidDialog(...) }`（那会立刻移除内容，
 * 看不到淡出），必须传 `visible = flag`。
 *
 * 未启用玻璃（API<31 或用户关闭）时按 [visible] 展示原生 [AlertDialog]。
 *
 * 肯定按钮统一用 iOS 系统蓝（通过局部覆写 colorScheme.primary/secondaryContainer 实现，
 * 所以调用方沿用原来的 TextButton / FilledTonalButton 写法即可）。
 */
@Composable
fun LiquidDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    visible: Boolean = true
) {
    if (!LocalGlassEnabled.current) {
        if (visible) {
            // material3 的 AlertDialog 内部没有任何滚动（text 槽只是 weight(1f,
            // fill=false) 的 Box），内容超高直接被裁剪——与玻璃分支（下方
            // weight + verticalScroll）对齐，MD3 回落也自行包滚动容器
            // （用户反馈：MD3 模式下的弹窗无法滚动）
            val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.92f
            AlertDialog(
                onDismissRequest = onDismissRequest,
                confirmButton = confirmButton,
                modifier = modifier.heightIn(max = maxDialogHeight),
                dismissButton = dismissButton,
                icon = icon,
                title = title,
                text = {
                    Box(
                        Modifier
                            .heightIn(max = maxDialogHeight * 0.66f)
                            .verticalScroll(rememberScrollState())
                    ) { text?.invoke() }
                }
            )
        }
        return
    }
    // 覆盖层不占独立窗口，系统返回键需自行拦截（可见时才接管）
    BackHandler(enabled = visible) { onDismissRequest() }
    // FlClash `_alertSpring`：withDurationAndBounce(275ms, bounce 0) → 临界阻尼，
    // 质量 1、刚度 ≈ (2π/0.275)² ≈ 522，曲线截断到 404ms（iOS 27 alert 手感）。
    // 进入从 1.2× 缩到 1×（`_alertEntryScale`）；退出只淡出、不回缩（scale 保持 1）。
    val alertSpec = spring<Float>(dampingRatio = 1f, stiffness = 522f)
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    val scale = remember { Animatable(if (visible) 1f else 1.2f) }
    // 退场动画播完才退出组合（期间继续渲染内容，否则看不到淡出）；
    // 组合期不读 progress.value——否则每帧重组整个弹窗子树
    var contentActive by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        // 常驻组合的隐藏弹窗（如 UpdateFlowHosts 的互斥组）不做空转动画
        if (visible) {
            contentActive = true
            if (progress.value != 1f || scale.value != 1f) {
                val pJob = launch { progress.animateTo(1f, alertSpec) }
                val sJob = launch { scale.animateTo(1f, alertSpec) }
                pJob.join()
                sJob.join()
            }
        } else {
            if (progress.value != 0f) {
                progress.animateTo(0f, alertSpec)
            }
            // FlClash 退出：scale 保持当前值（进入落定即 1），只淡出；
            // 淡出完成后内容已不可见，把 scale 重置回 1.2 供下次进入使用
            scale.snapTo(1.2f)
            contentActive = false
        }
    }
    if (!contentActive) return
    val dark = LocalDarkTheme.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(34.dp)
    // 面板最大高度：横屏可用高度很小时，文字区独立滚动、按钮行永远完整可见
    // （用户反馈：横屏弹窗文字太多，最后的选择按钮被遮掉）
    val maxDialogHeight = LocalConfiguration.current.screenHeightDp.dp * 0.92f
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 遮罩（点外部关闭；随进度淡入淡出）。
        // alpha 在绘制期读 progress：组合期读会让弹窗每帧重组
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    // FlClash `alertScrim`：iOS 27 弹窗压暗，深色 48% / 浅色 20%
                    drawRect(Color.Black.copy(alpha = (if (dark) 0.48f else 0.2f) * progress.value))
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismissRequest() }
        )
        // 弹窗内容层
        Box {
        Column(
            modifier = modifier
                .padding(horizontal = 28.dp)
                // FlClash `_alertWidth = 348`
                .widthIn(max = 348.dp)
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight)
                // 进入/退出：淡入淡出 + 轻微缩放。
                // 不再叠 Modifier.blur(16dp*(1-p))：面板自身已是 liquidGlass，
                // 且每帧变半径 = 每帧新建原生 RenderEffect（形变期掉帧源）
                .graphicsLayer {
                    alpha = progress.value
                    val s = scale.value
                    scaleX = s
                    scaleY = s
                }
                .liquidGlass(
                    shape = shape,
                    // FlClash `LiquidGlassStyle.alert` = menu.copyWith(blur: 16)：
                    // 深色 63% 深灰 / 浅色 55% 亮面，16dp 模糊
                    tintAlpha = if (dark) 0.63f else 0.55f,
                    tintColor = if (dark) MaterialTheme.colorScheme.surfaceContainerHighest
                        else MaterialTheme.colorScheme.surfaceBright,
                    lensHeight = 16.dp,
                    lensAmount = 20.dp,
                    // FlClash `menuShadows`：一道 29dp 模糊、下移 8dp 的软影，深色加强一档
                    shadow = Shadow(
                        radius = 29.dp,
                        offset = DpOffset(0.dp, 8.dp),
                        alpha = if (dark) 0.18f else 0.12f
                    ),
                    vibrancy = Build.VERSION.SDK_INT < 33,
                    chromatic = Build.VERSION.SDK_INT >= 33,
                    blurOverride = { with(density) { 16.dp.toPx() } }
                )
                .clip(shape)
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            icon?.let {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) { it() }
            }
            title?.let {
                // 标题色显式取 onSurface：弹窗可能从任意调用方打开
                //（如底部玻璃面板内的 Surface 会覆写 LocalContentColor），
                // 不显式指定时深色模式下标题会继承错色（用户反馈：
                // 「弹窗的标题在深色模式下没有变为白色」）。
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.titleLarge
                        .copy(fontWeight = FontWeight.SemiBold),
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) { it() }
                }
            }
            text?.let {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    // 文字区：weight(fill=false) 让短内容按原样包裹；
                    // 超出剩余高度（横屏）时独立滚动——按钮行始终完整可见
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) { it() }
                }
            }
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = IosBlue,
                    onPrimary = Color.White,
                    primaryContainer = IosBlue.copy(alpha = if (dark) 0.24f else 0.14f),
                    onPrimaryContainer = IosBlue,
                    secondaryContainer = IosBlue.copy(alpha = if (dark) 0.24f else 0.14f),
                    onSecondaryContainer = IosBlue
                ),
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    // FlClash：取消（dismiss）在左、默认动作（confirm）在右，均分整行
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                    }
                    confirmButton()
                }
            }
        }
        }
    }
}

/**
 * 液态玻璃底部面板（替代 [ModalBottomSheet] 的玻璃形态）。
 *
 * 必须放在屏幕根 [BoxScope] 内调用：它是**同一 Window 内的覆盖层**，
 * 这样才能折射页面真实内容（调用方应把页面内容包进 `layerBackdrop`）。
 * 未启用玻璃时退回原生 [ModalBottomSheet]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.GlassBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!LocalGlassEnabled.current) {
        if (visible) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
                content()
            }
        }
        return
    }
    val progress = remember { Animatable(0f) }
    var contentActive by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            contentActive = true
            progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 360f))
        } else {
            progress.animateTo(0f, tween(durationMillis = 200))
            contentActive = false
        }
    }
    // 退场动画播完才退出组合；组合期不读 progress.value
    if (!contentActive) return
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    // 遮罩（alpha 绘制期读，避免每帧重组）
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.34f * progress.value)) }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    )
    Box {
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            // 从屏幕下方滑入：位移与遮罩用同一条进度，关闭时逆向收回
            .graphicsLayer { translationY = (1f - progress.value) * size.height }
            .liquidGlass(
                shape = shape,
                tintAlpha = if (dark) 0.62f else 0.52f,
                tintColor = MaterialTheme.colorScheme.surface,
                lensHeight = 16.dp,
                lensAmount = 26.dp,
                // 模糊下限：通透度拉满时磨砂也不许消失（可读性）
                minBlur = 12.dp
            )
            .clip(shape)
            .navigationBarsPadding()
            .padding(top = 10.dp, bottom = 8.dp)
    ) {
        // 抓手
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.55f))
        )
        content()
    }
    }
}

/**
 * 顶栏渐进高斯模糊（iOS 风格 variable blur）。
 *
 * 官方推荐实现：**单个** [drawPlainBackdrop] 做整体模糊，再用一个 AGSL alpha-mask
 * shader 让模糊从顶部（[fadeStart] 高度比例）向下到 [fadeEnd] 平滑淡出到清晰，
 * 不再是「多条带各自模糊」，因此不会有条带之间露出的白线。
 *
 * 采样的是**当前页面真实内容**，调用方须让滚动内容真正穿过顶栏下方
 * （顶栏改为覆盖层，滚动容器用 contentPadding 预留顶部空间）。
 * SDK < 33 时 alpha-mask 不生效，仅有整体磨砂（顶部到底部一体）。
 */
@Composable
fun ProgressiveBlur(
    modifier: Modifier = Modifier,
    maxBlur: Dp = 24.dp,
    fadeStart: Float = 0.55f,
    fadeEnd: Float = 1f
) {
    val enabled = LocalGlassEnabled.current
    val backdrop = LocalGlassBackdrop.current
    if (!enabled || backdrop == null) return
    val maxBlurPx = with(LocalDensity.current) { maxBlur.toPx() }
    // AGSL 着色器与 RenderEffect 都只创建一次：此前每帧在 effects 里
    // `RenderEffect.createRuntimeShaderEffect` 会持续生成新的原生 RenderEffect 对象，
    // 滚动时原生内存快速膨胀，最终表现为照片选择器随机崩溃（OOM / 原生层异常）。
    // 这里 remember 复用，绘制时只更新 uniform；RenderEffect 持有 shader 引用，
    // setFloatUniform 原地更新后效果随之生效。
    val maskShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.graphics.RuntimeShader(
                """
                uniform shader content;
                uniform float2 size;
                uniform float fadeStart;
                uniform float fadeEnd;

                half4 main(float2 coord) {
                    float y = coord.y / max(size.y, 1.0);
                    float alpha = 1.0 - smoothstep(fadeStart, fadeEnd, y);
                    return content.eval(coord) * alpha;
                }
                """.trimIndent()
            )
        } else {
            null
        }
    }
    val maskEffect = remember {
        maskShader?.let {
            android.graphics.RenderEffect.createRuntimeShaderEffect(it, "content")
        }
    }
    // 整条效果链也静态化：blur 部分此前每次重跑都新建原生 RenderEffect。
    // chain = mask(blur(content))；uniform（size/fade）绘制期原地更新即可生效，
    // 链对象无需随 size 重建。
    val staticChain = remember(maxBlurPx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurFx = android.graphics.RenderEffect.createBlurEffect(
                maxBlurPx, maxBlurPx, android.graphics.Shader.TileMode.CLAMP
            )
            maskEffect?.let { fx ->
                android.graphics.RenderEffect.createChainEffect(fx, blurFx)
            } ?: blurFx
        } else null
    }
    Box(
        modifier.drawPlainBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = {
                maskShader?.run {
                    setFloatUniform("size", size.width, size.height)
                    setFloatUniform("fadeStart", fadeStart)
                    setFloatUniform("fadeEnd", fadeEnd)
                }
                staticChain?.let { renderEffect = it }
            }
        )
    )
}

/**
 * 顶栏毛玻璃外壳（按用户规格重做，主页 / 设置页顶端共用）：
 * 1) **模糊层**：固定模糊值——就是「液态玻璃通透度」滑条拉到**最右**时的
 *    模糊程度（liquidGlass 公式 (2+(1-opacity)*40)dp 在 opacity=1 处 = 2dp
 *    的轻磨砂），不随通透度设置变化；快到下缘时经 AGSL alpha-mask 把模糊层
 *    透明度逐渐减弱，收边不生硬（SDK<33 无 AGSL，整段均匀模糊）。
 * 2) **有色层**：模糊层之上、内容之下叠一层 **40% 黑（深色）/ 白（浅色）**，
 *    颜色本身做 500ms 动画——切换深浅模式时**实时**交叉淡化，不需要重进界面
 *   （用户反馈「顶部常驻栏的叠加色不能实时切换」）；且在模糊开始减弱的同一
 *    区段把透明度平滑降到 0，与模糊层同一条收边曲线。
 * 3) **内容层**：常驻按钮画在最上面，玻璃按钮经 [rememberGlassBackdrop]
 *    采样「根 + 页面」合成层——即按钮底下**所有**显示内容的直接取样，按钮
 *    玻璃运算的就是这些内容（调用方把本组件渲染在页面采样层之外并提供
 *    LocalGlassBackdrop，见 MainScreen / SettingsScreen）。
 *
 * 未启用玻璃时直接渲染 [content]（调用方原本的不透明顶栏行为不变）。
 */
@Composable
fun TopBarFrost(
    modifier: Modifier = Modifier,
    barHeight: Dp = 64.dp,
    content: @Composable () -> Unit
) {
    if (!LocalGlassEnabled.current) {
        content()
        return
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 采样「根 + 页面」总显示内容（本组件由调用方放在覆盖层作用域内）
    val backdrop = rememberGlassBackdrop()
    // 固定模糊 = 通透度滑条最右（opacity=1）时的 liquidGlass 模糊值
    val blurPx = with(LocalDensity.current) { 2.dp.toPx() }
    // 下缘渐隐区段（占整条高度的比例）：0..fadeStart 全浓，fadeStart..1 渐隐
    val fadeStart = 0.55f
    val fadeEnd = 1f
    // AGSL alpha-mask：与 ProgressiveBlur 同一手法——着色器/RenderEffect 只创建
    // 一次（remember 复用），绘制期只更新 uniform，避免滚动时原生对象膨胀。
    val maskShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.graphics.RuntimeShader(
                """
                uniform shader content;
                uniform float2 size;
                uniform float fadeStart;
                uniform float fadeEnd;

                half4 main(float2 coord) {
                    float y = coord.y / max(size.y, 1.0);
                    float alpha = 1.0 - smoothstep(fadeStart, fadeEnd, y);
                    return content.eval(coord) * alpha;
                }
                """.trimIndent()
            )
        } else {
            null
        }
    }
    val maskEffect = remember {
        maskShader?.let {
            android.graphics.RenderEffect.createRuntimeShaderEffect(it, "content")
        }
    }
    // 整条效果链静态化（与 ProgressiveBlur 同法）：blur 部分不再每次重跑新建
    val staticChain = remember(blurPx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurFx = android.graphics.RenderEffect.createBlurEffect(
                blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP
            )
            maskEffect?.let { fx ->
                android.graphics.RenderEffect.createChainEffect(fx, blurFx)
            } ?: blurFx
        } else null
    }
    // 有色层颜色（含 70% 不透明度，用户指定）随主题 500ms 渐变：切换深浅模式实时生效
    val tintColor = animatedThemeColor(Color.Black, Color.White)
    val barModifier = Modifier
        .fillMaxWidth()
        .height(topInset + barHeight)
    Box(modifier) {
        // 1) 模糊层
        if (backdrop != null) {
            Box(
                barModifier
                    .align(Alignment.TopCenter)
                    .drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        maskShader?.run {
                            setFloatUniform("size", size.width, size.height)
                            setFloatUniform("fadeStart", fadeStart)
                            setFloatUniform("fadeEnd", fadeEnd)
                        }
                        staticChain?.let { renderEffect = it }
                    }
                )
            )
        }
        // 2) 有色层（70% 黑/白，与模糊同区段渐隐收边）
        Box(
            barModifier
                .align(Alignment.TopCenter)
                .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to tintColor.copy(alpha = 0.70f),
                        fadeStart to tintColor.copy(alpha = 0.70f),
                        fadeEnd to tintColor.copy(alpha = 0f)
                    )
                )
            )
        )
        // 3) 顶栏内容（按钮/标题，玻璃件采样下方所有层）
        content()
    }
}

/**
 * 玻璃顶栏：替代 M3 [androidx.compose.material3.TopAppBar] 的玻璃版本。
 *
 * 为什么不用 TopAppBar：它内部是 Surface，会裁剪到自身矩形，
 * 导致玻璃圆钮的投影在顶栏下沿被切掉一截（用户反馈的「阴影被裁」）。
 * 这里改成普通 Row，不做裁剪，投影可以自然溢出到下方内容上。
 */
@Composable
fun GlassTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    titleStartPadding: Dp = 14.dp,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 4.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navigationIcon?.invoke()
            Box(
                Modifier
                    .weight(1f)
                    .padding(start = titleStartPadding)
            ) {
                title()
            }
            actions()
        }
    }
}

/**
 * MD3 实心底上的可读内容色（「液态玻璃可读性逻辑」在 MD3 UI 的落地）：
 *
 * 主题色相替换（buildScheme 的 shiftHue）只换 primary 等容器色、保留 onPrimary，
 * 玻璃不可用时的实心指示器一旦用 `primary` 打底 + `onSurface` 写字，
 * 就会随色相/深浅色组合翻车——深色主题的浅色 primary 上写白字（浅蓝底白字）、
 * 浅色主题的深色 primary 上写黑字（深色底黑字）（用户反馈）。
 *
 * 与玻璃件的 [rememberAdaptiveContentColor] 同一套判据（Rec.709 亮度 →
 * 动态黑/白），但底色是已知实色、无需采样：阈值取黑白对比度相等点
 * （L≈0.18）附近，保证任意色相下内容对比度 ≥ 4:1。
 */
fun readableContentColorOn(background: Color): Color =
    if (background.luminance() > 0.18f) Color.Black else Color.White

/**
 * 玻璃分段选择（等宽 tabs）：选中项是高光玻璃胶囊，未选中完全透明。
 * 指示器随选择做弹簧滑动，按下时有轻微缩放（物理反馈），选项用文字描述。
 */
@Composable
fun GlassSegments(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (options.isEmpty()) return
    val haptic = rememberHapticFeedback()
    val glass = LocalGlassEnabled.current
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 620f),
        label = "segmentPress"
    )
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "segmentIndex"
    )
    // 深浅色切换 500ms 渐变（与主题色同步）
    val unselectedColor = animatedThemeColor(Color(0xFF9A9AA0), Color(0xFF5F5F66))
    // 选中项文字色：玻璃态压在玻璃指示器上用 onSurface（主题自适应）；
    // 无玻璃回落态压在实心 primary 上，按底色亮度动态切黑/白，
    // 杜绝「浅蓝底白字 / 深色底黑字」（见 [readableContentColorOn]）
    val selectedColor = if (glass) MaterialTheme.colorScheme.onSurface
    else readableContentColorOn(MaterialTheme.colorScheme.primary)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(if (dark) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled
            ) { },
        contentAlignment = Alignment.CenterStart
    ) {
        val segmentWidth = maxWidth / options.size
        Box(
            Modifier
                .offset(x = segmentWidth * animatedIndex)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .then(
                    if (glass) Modifier.liquidGlass(
                        shape = shape,
                        tintAlpha = 0.24f,
                        lensHeight = 10.dp,
                        lensAmount = 18.dp,
                        backdropOverride = LocalGlassRootBackdrop.current
                    ) else Modifier.background(MaterialTheme.colorScheme.primary, shape)
                )
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            options.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (index != selectedIndex) {
                                haptic.click()
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                        color = when {
                            !enabled -> unselectedColor.copy(alpha = 0.45f)
                            index == selectedIndex -> selectedColor
                            else -> unselectedColor
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 玻璃选项 chip（用于数量较多、可横向滚动的选项）：
 * 选中 = 玻璃胶囊 + 白色高光；未选中 = 完全透明；按下带缩放/回弹，
 * 并支持液态玻璃拖拽形变（小位移大形变，内容与玻璃表面整体联动）。
 */
@Composable
fun GlassChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    val glass = LocalGlassEnabled.current
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 640f),
        label = "chipPress"
    )
    // 液态玻璃拖拽形变：用非消费手势（inspectDragGestures 不 consume），
    // 横向拖动不抢横向滚动（压缩预设列表），纵向/斜向拖动呈现拉伸形变。
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    val maxDragPx = with(LocalDensity.current) { 8.dp.toPx() }
    val transform: GraphicsLayerScope.() -> Unit = {
        val ox = highlight.offset.x
        val oy = highlight.offset.y
        val maxDrag = maxDragPx.coerceAtLeast(1f)
        // 位移收得很小、拉伸更明显：整体形变而非「按钮跑走」。
        translationX = ox * 0.10f
        translationY = oy * 0.10f
        transformOrigin = TransformOrigin.Center
        scaleX = pressScale * (1f + kotlin.math.abs(ox) / maxDrag * 0.20f)
        scaleY = pressScale * (1f + kotlin.math.abs(oy) / maxDrag * 0.20f)
    }
    Box(
        modifier = modifier
            .then(
                if (glass && selected) Modifier.liquidGlass(
                    shape = shape,
                    tintAlpha = 0.26f,
                    lensHeight = 10.dp,
                    lensAmount = 18.dp,
                    layerBlock = transform,
                    backdropOverride = LocalGlassRootBackdrop.current
                ) else Modifier
            )
            .clip(shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null
            ) {
                haptic.click()
                onClick()
            }
            .graphicsLayer(block = transform)
            .then(if (glass && enabled) highlight.modifier else Modifier)
            .then(if (glass && enabled) highlight.gestureModifier else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                selected -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

/**
 * 可横向滚动的液态玻璃分段选择器（选项较多时使用，如转码预设 9 选）：
 * 玻璃胶囊指示器随选中项平滑滑动（弹簧动画），选中项自动滚入视野；
 * 未启用玻璃时退回纯色 primary 指示器，滑动行为一致。
 * 选项宽度按内容自适应（非等分），因此支持长标签 + 横向滚动。
 */
@Composable
fun LiquidScrollSegments(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (options.isEmpty()) return
    val haptic = rememberHapticFeedback()
    val glass = LocalGlassEnabled.current
    // 采样源走默认解析：内容作用域内为根层；若被 chrome 覆盖层承载
    // （LocalGlassBackdrop = 页面层）则合成「根 + 页面」，模糊包含下层总显示内容
    val backdrop = rememberGlassBackdrop()
    val shape = RoundedCornerShape(50)
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // 每个选项在「滚动内容坐标系」中的起点/宽度（onGloballyPositioned 采集，
    // 经父坐标系换算，不受当前滚动位移影响）。
    val itemX = remember { mutableStateMapOf<Int, Float>() }
    val itemW = remember { mutableStateMapOf<Int, Float>() }
    var viewportW by remember { mutableFloatStateOf(0f) }
    var laidOut by remember { mutableStateOf(false) }

    val indicatorX = remember { Animatable(0f) }
    val indicatorW = remember { Animatable(0f) }
    val targetX = itemX[selectedIndex]
    val targetW = itemW[selectedIndex]
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
    LaunchedEffect(selectedIndex, viewportW, laidOut) {
        if (!laidOut || viewportW <= 0f) return@LaunchedEffect
        val x = itemX[selectedIndex] ?: return@LaunchedEffect
        val w = itemW[selectedIndex] ?: return@LaunchedEffect
        val target = (x - (viewportW - w) / 2f)
            .coerceIn(0f, scrollState.maxValue.toFloat())
        if (abs(target - scrollState.value) > 1f) {
            scrollState.animateScrollTo(target.roundToInt())
        }
    }

    // 深浅色切换 500ms 渐变（与主题色同步）
    val unselectedColor = animatedThemeColor(Color(0xFF9A9AA0), Color(0xFF5F5F66))
    // 选中项文字色：玻璃态压在玻璃指示器上用 onSurface（主题自适应）；
    // 无玻璃回落态压在实心 primary 上，按底色亮度动态切黑/白，
    // 杜绝「浅蓝底白字 / 深色底黑字」（见 [readableContentColorOn]）
    val selectedColor = if (glass) MaterialTheme.colorScheme.onSurface
    else readableContentColorOn(MaterialTheme.colorScheme.primary)
    val capsuleBg = animatedThemeColor(
        Color.Black.copy(alpha = 0.22f),
        Color.White.copy(alpha = 0.10f)
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            // 容器改成液态玻璃胶囊（与演示版 tab 同款）：此前是纯色半透明底，
            // 与里面的玻璃指示器风格不统一（用户反馈「压缩预设 UI 风格还没变成
            // 液态玻璃 tab 的风格」）。禁用态保持纯色，避免处处都在折射。
            .then(
                if (glass) Modifier
                    .liquidGlass(
                        shape = shape,
                        tintAlpha = 0.16f,
                        lensEnabled = false,
                        highlight = null,
                        shadow = Shadow(radius = 0.dp, alpha = 0f),
                        backdropOverride = backdrop
                    )
                    .clip(shape)
                else Modifier
                    .clip(shape)
                    .background(capsuleBg)
            )
            .onSizeChanged { viewportW = it.width.toFloat() }
    ) {
        // 滑动指示器：放在不滚动的外层，用 scrollState.value 补偿位移。
        // graphicsLayer 块内读取 scroll 状态只会触发该层重绘，不引起重组。
        if (laidOut && (targetW ?: 0f) > 0f) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer { translationX = indicatorX.value - scrollState.value }
                    .width(with(density) { indicatorW.value.toDp() })
                    .fillMaxHeight()
                    .padding(3.dp)
                    .then(
                        if (glass) Modifier.liquidGlass(
                            shape = shape,
                            tintAlpha = 0.24f,
                            lensHeight = 10.dp,
                            lensAmount = 18.dp,
                            backdropOverride = backdrop
                        ) else Modifier.background(MaterialTheme.colorScheme.primary, shape)
                    )
            )
        }
        Row(
            Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, label ->
                val optionInteraction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .onGloballyPositioned { coords ->
                            val parent = coords.parentLayoutCoordinates
                            if (parent != null) {
                                val off = parent.localPositionOf(coords, Offset.Zero)
                                itemX[index] = off.x
                                itemW[index] = coords.size.width.toFloat()
                            }
                        }
                        .clickable(
                            interactionSource = optionInteraction,
                            indication = null,
                            enabled = enabled
                        ) {
                            if (index != selectedIndex) {
                                haptic.click()
                                onSelect(index)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                        color = when {
                            !enabled -> unselectedColor.copy(alpha = 0.45f)
                            index == selectedIndex -> selectedColor
                            else -> unselectedColor
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 玻璃动作按钮（主页「添加文件 / 批量导入 / 清空 / 开始转换」、选择器确认导入）：
 * 与演示版 LiquidButton 同款物理——触点径向高光 + tanh 阻尼拖拽 + 方向形变，
 * 玻璃表面与内容作为整体变形（layerBlock 的 graphicsLayer 包裹全节点）。
 */
@Composable
fun GlassActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(26.dp),
    tint: Color = MaterialTheme.colorScheme.surface,
    tintAlpha: Float = 0.34f,
    brush: Brush? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    adaptiveContent: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val haptic = rememberHapticFeedback()
    val glass = LocalGlassEnabled.current
    val dark = LocalDarkTheme.current
    // 内容自适应着色：浮在照片上时文字/计数按背景亮暗自动切黑/白
    val adaptive = rememberAdaptiveContentColor(
        initialColor = if (dark) Color.White else Color.Black,
        enabled = adaptiveContent && glass
    )
    // 演示版同款物理（见 [GlassButtonPhysics]）
    val physics = rememberGlassButtonPhysics()
    // 交互中提升层级（同 [GlassIconButton]）：按下的动作按钮浮到同层内容之上
    val actionInteraction = remember { MutableInteractionSource() }
    val actionPressed by actionInteraction.collectIsPressedAsState()
    val effectiveContentColor =
        if (adaptiveContent && glass) adaptive.color else contentColor
    Row(
        modifier = modifier
            .height(52.dp)
            .zIndex(if (actionPressed) 1f else 0f)
            .alpha(if (enabled) 1f else 0.45f)
            .then(
                if (glass) Modifier
                    .liquidGlass(
                        shape = shape,
                        tintAlpha = tintAlpha,
                        tintColor = tint,
                        lensHeight = 12.dp,
                        lensAmount = 22.dp,
                        layerBlock = physics.transform,
                        // 采样源走默认解析（根层 + 页面层合成）：面板内的动作按钮
                        // 折射的是面板下方透出的真实内容，而不是只有装饰渐变
                        onDrawBackdrop = if (adaptiveContent) adaptive.recordBackdrop else null
                    )
                    .clip(shape)
                else Modifier
                    .then(
                        if (brush != null) Modifier.background(brush, shape)
                        else Modifier.background(tint, shape)
                    )
                    .clip(shape)
            )
            .then(if (glass && enabled) physics.highlight.modifier else Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = actionInteraction,
                indication = if (glass) null else LocalIndication.current
            ) {
                haptic.click()
                onClick()
            }
            .then(if (glass && enabled) physics.highlight.gestureModifier else Modifier)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = {
            CompositionLocalProvider(LocalContentColor provides effectiveContentColor) {
                content()
            }
        }
    )
}

/**
 * 液态玻璃 BOTTOM tabs（外观 / 转码方式 / 处理方式等三选）：
 *  玻璃可用时用演示版 [LiquidBottomTabs]（胶囊容器 + 可拖拽指示器 + 按压力高光 + 弹簧动画），
 *  否则退回 [GlassSegments] 保持原生分段样式。
 *
 * 指示器**时刻跟随外部状态**：点击任一选项，玻璃块直接滑到该选项
 * （不只是拖动玻璃块才能切换——用户反馈的核心诉求，见 [LiquidBottomTabs]）。
 */
@Composable
fun LiquidSegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // 调用方「拒绝」最近一次选择时的回弹信号（每次 +1 触发指示器弹回外部选中项，
    // 如处理方式拖到「替换」但风险弹窗未确认——用户反馈：不确认也应弹回原选项）。
    // 默认 0 = 不回弹。
    revertSignal: Int = 0
) {
    // 采样源走默认解析（根层 + 页面层合成，见 [rememberGlassBackdrop]）：
    // 顶栏/面板内的 tab 折射的是下层总显示内容。
    // 禁用态（enabled=false）在玻璃可用时**保持液态玻璃 tab 外观**（整体置灰 +
    // 禁用手势），不再回落原生分段——转码方式选 off/remux 时参数区整体不可用，
    // 但样式不能退回 MD3（用户反馈：「转码器如果关闭，那下面的 UI 会退回
    // MD3 的样式，应该保持液态玻璃的样式」）。仅玻璃不可用（API<31 / 用户关闭）
    // 时才回落 [GlassSegments]。
    val backdrop = rememberGlassBackdrop()
    if (backdrop == null) {
        GlassSegments(
            options = labels,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            enabled = enabled,
            modifier = modifier
        )
        return
    }
    LiquidBottomTabs(
        selectedTabIndex = { selectedIndex },
        onTabSelected = onSelect,
        backdrop = backdrop,
        tabsCount = labels.size,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        revertSignal = revertSignal
    ) {
        labels.forEachIndexed { index, label ->
            LiquidBottomTab(onClick = { onSelect(index) }, enabled = enabled) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (index == selectedIndex) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
