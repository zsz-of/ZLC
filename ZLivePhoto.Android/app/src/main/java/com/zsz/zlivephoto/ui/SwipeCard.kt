package com.zsz.zlivephoto.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 文件卡片：支持双向侧滑删除与「转换完成」移除动画。
 *
 * 侧滑删除（自研手势，参考 ImageToolbox 交互习惯）：
 * - 左右两个方向均可滑动删除
 * - 划出红色圆形垃圾桶徽章（带盖子开合动画），以划出空间的中点水平居中（垂直不动）
 * - 滑过 1/3：清脆振动 + 盖子打开；划回 1/3 以内：再次清脆振动 + 盖子关上
 * - 过阈值后松手：沿滑动方向滑出屏幕 → 图标继续居中渐隐（划出屏后 +0.1s 完全透明）→ 移除列表项
 * - 未过阈值松手：弹簧动画回位（不移除）
 *
 * 转换完成移除动画：
 * - 一次左滑效果直接滑出屏幕（不显示垃圾桶徽章）
 * - 滑出后高度塌陷（后续项弹簧补位）
 */
@Composable
fun FileCard(
    item: FileItem,
    isConverting: Boolean,
    sameFormat: Boolean,
    onDelete: (String) -> Unit,
    onDoneRemove: (String) -> Unit
) {
    val haptic = rememberHapticFeedback()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // 状态用 State 对象承载：pointerInput(Unit) 闭包跨重组读取最新值
    var boxWidthPx by remember { mutableIntStateOf(0) }
    var boxHeightPx by remember { mutableIntStateOf(0) }
    val offsetX = remember { Animatable(0f) }        // 侧滑位移（px，正=向右）
    val iconAlpha = remember { Animatable(1f) }       // 垃圾桶徽章透明度
    val heightAnim = remember { Animatable(-1f) }     // 高度塌陷（px，-1=未开始）
    var lidOpen by remember { mutableStateOf(false) }
    var pastThreshold by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    // 转换完成触发的左滑退出（不显示垃圾桶徽章）
    var doneExiting by remember { mutableStateOf(false) }
    // 垃圾桶条件显示：仅在拖动项目时显示（项 9）
    var dragging by remember { mutableStateOf(false) }
    // 垃圾桶盖方向：从右向左拉（off<0）→ 盖开向左边；从左向右拉（off>0）→ 盖开向右边
    var lidToLeft by remember { mutableStateOf(false) }

    // 垃圾桶徽章显隐：仅手动拖动/手动删除退出时可见；转换完成的左滑退出不显示垃圾桶
    val badgeAlpha by animateFloatAsState(
        targetValue = if ((dragging || exiting) && !doneExiting) 1f else 0f,
        animationSpec = tween(120),
        label = "badgeAlpha"
    )

    // 转换完成：一次左滑直接滑出屏幕（无垃圾桶），滑出后高度塌陷并移除
    LaunchedEffect(item.isDone) {
        if (!item.isDone) return@LaunchedEffect
        exiting = true
        doneExiting = true
        if (heightAnim.value < 0f && boxHeightPx > 0) heightAnim.snapTo(boxHeightPx.toFloat())
        val w = boxWidthPx.toFloat()
        offsetX.animateTo(-w, tween(260, easing = FastOutSlowInEasing))
        heightAnim.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        onDoneRemove(item.path)
    }

    // 处理中 / 已完成 / 退出动画中：禁用侧滑手势
    val gestureModifier = if (!isConverting && !item.isDone && !exiting) {
        Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { dragging = true },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    val w = boxWidthPx.toFloat()
                    if (w <= 0f) return@detectHorizontalDragGestures
                    val target = (offsetX.value + dragAmount).coerceIn(-w, w)
                    scope.launch { offsetX.snapTo(target) }
                    // 盖子方向跟随滑动方向：从右向左拉（off<0）→ 盖开向左边
                    if (target != 0f) lidToLeft = target < 0f
                    val over = abs(target) > w / 3f
                    if (over != pastThreshold) {
                        pastThreshold = over
                        lidOpen = over
                        haptic.click() // 清脆振动
                    }
                },
                onDragEnd = {
                    dragging = false
                    val w = boxWidthPx.toFloat()
                    if (w <= 0f) return@detectHorizontalDragGestures
                    if (pastThreshold && abs(offsetX.value) > w / 3f) {
                        // 删除：沿滑动方向滑出屏幕，图标渐隐（划出屏后 +0.1s 完全透明）
                        exiting = true
                        val dir = if (offsetX.value < 0f) -1f else 1f
                        scope.launch {
                            offsetX.animateTo(dir * w, tween(220, easing = FastOutSlowInEasing))
                            iconAlpha.animateTo(0f, tween(100))
                            onDelete(item.path)
                        }
                    } else {
                        // 弹簧回位（不移除）
                        scope.launch {
                            offsetX.animateTo(
                                0f,
                                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                            )
                        }
                    }
                },
                onDragCancel = {
                    dragging = false
                    scope.launch {
                        offsetX.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                        )
                    }
                }
            )
        }
    } else Modifier

    val useFixedHeight = heightAnim.value >= 0f
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (useFixedHeight) Modifier.height(with(density) { heightAnim.value.toDp() }) else Modifier)
            .onSizeChanged { if (heightAnim.value < 0f) { boxWidthPx = it.width; boxHeightPx = it.height } }
            .clipToBounds()
            .then(gestureModifier)
    ) {
        // 背景层：垃圾桶圆形徽章，以划出空间的中点水平居中（垂直不动）
        // 条件显示：仅拖动/删除退出时可见（badgeAlpha），默认位置被前景卡片完全遮挡
        if (boxWidthPx > 0) {
            val off = offsetX.value
            val iconSizePx = with(density) { 40.dp.roundToPx() }
            val centerX = if (off >= 0f) off / 2f else boxWidthPx + off / 2f
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((centerX - iconSizePx / 2f).roundToInt(), 0) }
                    .size(40.dp)
                    .graphicsLayer { alpha = badgeAlpha * iconAlpha.value }
            ) {
                TrashBadge(open = lidOpen, lidToLeft = lidToLeft)
            }
        }

        // 前景卡片：侧滑平移（手动删除与转换完成左滑共用 translationX）
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = offsetX.value
                }
        ) {
            FileCardContent(item = item, sameFormat = sameFormat)
        }
    }
}

// ---------- 垃圾桶徽章 ----------

/**
 * 红色圆形底 + 白色垃圾桶（项 9）：
 * - 盖子开启角度 35-45°（取 40°），绕铰链弹簧旋转开合（非突变）
 * - 盖子方向动态：lidToLeft=true（从右向左拉）→ 铰链在右端、盖开向左边；
 *   lidToLeft=false（从左向右拉）→ 铰链在左端、盖开向右边
 * - 桶身水平宽度压缩（15→12dp），提升视觉协调性
 */
@Composable
private fun TrashBadge(open: Boolean, lidToLeft: Boolean) {
    val lidProgress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "lid"
    )
    val bg = MaterialTheme.colorScheme.error
    val fg = MaterialTheme.colorScheme.onError
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        drawCircle(bg, radius = size.minDimension / 2f, center = c)
        val stroke = 2.2.dp.toPx()
        // 桶身（水平宽度压缩）
        val bodyW = 12.dp.toPx()
        val bodyH = 12.dp.toPx()
        val bodyLeft = c.x - bodyW / 2f
        val bodyTop = c.y - bodyH / 2f + 3.dp.toPx()
        drawRoundRect(
            color = fg,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(stroke)
        )
        // 桶身竖线
        val x1 = bodyLeft + bodyW / 3f
        val x2 = bodyLeft + bodyW * 2f / 3f
        drawLine(fg, Offset(x1, bodyTop + bodyH * 0.25f), Offset(x1, bodyTop + bodyH * 0.8f), stroke)
        drawLine(fg, Offset(x2, bodyTop + bodyH * 0.25f), Offset(x2, bodyTop + bodyH * 0.8f), stroke)
        // 盖子 + 提手：开启角度 40°，方向随滑动方向
        // - 开向左边（lidToLeft）：铰链在右端，正向旋转
        // - 开向右边：铰链在左端，负向旋转
        val lidW = 15.dp.toPx()
        val lidH = 2.4.dp.toPx()
        val lidLeft = c.x - lidW / 2f
        val lidY = bodyTop - 4.dp.toPx()
        val angle = 40f * lidProgress
        if (lidToLeft) {
            rotate(degrees = angle, pivot = Offset(lidLeft + lidW, lidY + lidH / 2f)) {
                drawRoundRect(
                    color = fg,
                    topLeft = Offset(lidLeft, lidY),
                    size = Size(lidW, lidH),
                    cornerRadius = CornerRadius(lidH / 2f)
                )
                drawRoundRect(
                    color = fg,
                    topLeft = Offset(c.x - 3.dp.toPx(), lidY - 3.dp.toPx()),
                    size = Size(6.dp.toPx(), 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            }
        } else {
            rotate(degrees = -angle, pivot = Offset(lidLeft, lidY + lidH / 2f)) {
                drawRoundRect(
                    color = fg,
                    topLeft = Offset(lidLeft, lidY),
                    size = Size(lidW, lidH),
                    cornerRadius = CornerRadius(lidH / 2f)
                )
                drawRoundRect(
                    color = fg,
                    topLeft = Offset(c.x - 3.dp.toPx(), lidY - 3.dp.toPx()),
                    size = Size(6.dp.toPx(), 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
            }
        }
    }
}

// ---------- 卡片内容 ----------

/** 同格式（目标=输入）文件名与提示的蓝色（随深色模式取深浅蓝） */
private val SameFormatBlueLight = Color(0xFF1565C0)
private val SameFormatBlueDark = Color(0xFF7EAEFF)

@Composable
private fun FileCardContent(item: FileItem, sameFormat: Boolean) {
    // 缩略图：IO 线程降采样解码 + EXIF 方向校正
    val thumb by produceState<Bitmap?>(null, item.path) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(item.path) }
    }
    val sameFormatBlue = if (isSystemInDarkTheme()) SameFormatBlueDark else SameFormatBlueLight
    val nameColor = when {
        sameFormat -> sameFormatBlue
        item.isUnrecognized -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val infoColor = when {
        sameFormat -> sameFormatBlue
        item.isUnrecognized -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文件缩略图（解码失败回退图标）
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (item.isUnrecognized) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                val t = thumb
                if (t != null) {
                    Image(
                        bitmap = t.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (item.isUnrecognized) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.info,
                    style = MaterialTheme.typography.bodySmall,
                    color = infoColor
                )
            }
        }
    }
}

/** 解码列表缩略图：按 2 的幂降采样至约 128px，并按 EXIF 方向旋转；失败返回 null。 */
internal fun decodeThumbnail(path: String): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 128 && bounds.outHeight / (sample * 2) >= 128) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
        val rotation = when (ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        } else {
            bmp
        }
    } catch (_: Exception) {
        null
    }
}
