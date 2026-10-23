package com.zsz.zlivephoto.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.zsz.zlivephoto.ui.glass.catalog.utils.scale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 玻璃内容自适应着色状态（「根据背景自动切黑/白」的判断器）。
 *
 * 参考 Kyant0 演示 `AdaptiveLuminanceGlass` 的采样法：玻璃件在
 * [recordBackdrop] 里把折射到的背景**再录一份**进 GraphicsLayer，
 * 协程以固定频率降采样到 5×5 求 Rec.709 平均亮度，过 0.5 阈值即在
 * 「黑内容 / 白内容」间切换。这样图标/文字无论压在照片、亮渐变还是
 * 深色底上，都保持可读的对比度。
 *
 * 关键修正（用户反馈「设置按钮/导入按钮无法根据背景自动变色」）：
 * 页面内容层在玻璃模式下是**透明**的（背景由根采样层的渐变提供），
 * 直接对采样层求亮度会恒得 0 → 内容色恒判白。因此采样前先把
 * [rootTint]（根采样层底色）垫在采样层下面再合成，等效于
 * 「玻璃实际透出的背景」，透明区域也能得到正确的黑/白判断。
 *
 * 用法（玻璃件）：
 * ```
 * val content = rememberAdaptiveContentColor(initial = Color.Black)
 * ...
 * .drawBackdrop(
 *     ...,
 *     onDrawBackdrop = content.recordBackdrop   // 把采样接线进去
 * )
 * // 内容着色用 content.color
 * ```
 */
class AdaptiveContentColorState internal constructor(
    initialColor: Color
) {
    /** 采样层：与玻璃件同尺寸，由 [recordBackdrop] 每帧录制；协程从中取图分析 */
    var layer: androidx.compose.ui.graphics.layer.GraphicsLayer? = null
        internal set

    /** 当前应使用的内容色（黑/白），由采样循环驱动 */
    var color: Color by mutableStateOf(initialColor)
        internal set

    /** 最近一次采样到的平均亮度（0..1），调试/其它判断可读 */
    var luminance: Float by mutableStateOf(0f)
        internal set

    /**
     * 根采样层底色（玻璃透明处透出它）：采样时先与它预乘合成。
     * 由 [rememberAdaptiveContentColor] 在组合期写入，跟随主题 500ms 渐变。
     */
    var rootTint: Color = Color.White
        internal set

    /**
     * 接到 `drawBackdrop(onDrawBackdrop = ...)` 上：
     * 先正常画背景，再把同一份背景录进采样层供亮度分析。
     *
     * 注意：本 lambda 带 DrawScope 接收者，`record` 解析到的是
     * DrawScope 的成员扩展（catalog 同款用法），离开此上下文勿直接调用。
     *
     * 录制全程 try/catch：采样是「锦上添花」的可视增强，任何绘制期异常
     * （零尺寸层 / 底层录制冲突等）都不应该让 App 崩溃，退化为固定色即可。
     */
    val recordBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = { drawBackdrop ->
        drawBackdrop()
        val l = layer
        if (l != null && size.width > 0f && size.height > 0f) {
            try {
                l.record { drawBackdrop() }
            } catch (_: Exception) {
                // 忽略：本帧不更新采样层（零尺寸层 / 底层录制冲突等）
            }
        }
    }
}

/**
 * 记忆一个 [AdaptiveContentColorState] 并启动亮度采样循环。
 *
 * @param initialColor 首帧内容色（深色主题传白、浅色主题传黑，避免首帧闪错色）
 * @param sampleIntervalMs 采样间隔；亮度变化不频繁，250ms 足够且省电
 */
@Composable
fun rememberAdaptiveContentColor(
    initialColor: Color,
    sampleIntervalMs: Long = 250L,
    enabled: Boolean = true
): AdaptiveContentColorState {
    val state = remember(initialColor) { AdaptiveContentColorState(initialColor) }
    val layer = rememberGraphicsLayer()
    state.layer = if (enabled) layer else null
    // 根采样层底色：组合期读取（动画期间随重组刷新），供采样预乘合成
    state.rootTint = LocalGlassRootTint.current
    val buffer = remember { IntArray(25) }

    LaunchedEffect(layer, sampleIntervalMs, enabled) {
        if (!enabled) return@LaunchedEffect
        while (isActive) {
            // 采样整段 try/catch：取图/缩放/读像素在任何一环失败都只跳过本次，
            // 绝不让采样协程的异常逃逸成未捕获崩溃（闪退）。
            try {
                val bitmap: ImageBitmap = layer.toImageBitmap()
                if (bitmap.width > 0 && bitmap.height > 0) {
                    val thumbnail = bitmap.scale(5, 5)
                    thumbnail.readPixels(buffer)
                    // 预乘合成：采样像素叠在根采样层底色之上（玻璃透明处透出它），
                    // 再求 Rec.709 亮度——避免「页面内容透明 → 恒判白」。
                    val rt = state.rootTint.red
                    val gt = state.rootTint.green
                    val bt = state.rootTint.blue
                    val average = buffer.sumOf { argb ->
                        val a = (argb shr 24 and 0xFF) / 255f
                        val r = a * ((argb shr 16 and 0xFF) / 255f) + (1f - a) * rt
                        val g = a * ((argb shr 8 and 0xFF) / 255f) + (1f - a) * gt
                        val b = a * ((argb and 0xFF) / 255f) + (1f - a) * bt
                        0.2126 * r + 0.7152 * g + 0.0722 * b
                    } / buffer.size
                    state.luminance = average.toFloat()
                    state.color = if (average > 0.5) Color.Black else Color.White
                }
            } catch (_: Exception) {
                // 首帧前层尚无内容等情况：跳过本次采样
            }
            delay(sampleIntervalMs)
        }
    }
    return state
}
