package com.zsz.zlivephoto.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.ui.glass.LocalGlassEnabled
import com.zsz.zlivephoto.ui.glass.LocalGlassRootBackdrop
import com.zsz.zlivephoto.ui.glass.liquidGlass
import com.zsz.zlivephoto.ui.glass.rememberGlassButtonPhysics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 文件卡片：支持双向侧滑删除与「转换完成」移除动画。
 *
 * 侧滑删除：
 * - 列表项同时是按钮：按下轻微内缩；横向拖动与列表纵向滚动共存（纵向优先时让位滚动）
 * - 划出圆形玻璃垃圾桶（与底部「清空」同源的红色），位置随滑动在屏幕边缘 → 露出区域中点移动，
 *   盖子朝滑动方向打开、桶身反向倾斜，越过 1/3 后角度明显加大
 * - 越过 / 回到 1/3：清脆振动
 * - 过 1/3 松手：以松手速度**匀速**滑出屏幕（非减速），垃圾桶同时滑向屏幕正中并淡出 → 移除列表项
 * - 未过 1/3 松手：停在刚好露出垃圾桶的位置，点垃圾桶同样匀速滑出删除
 * - 停在露出状态时点卡片：收起侧滑
 *
 * 转换完成移除：
 * - 由 Activity 直接无动画移除（附快速两下振动提示）
 */
@Composable
fun FileCard(
    item: FileItem,
    isConverting: Boolean,
    sameFormat: Boolean,
    onLongPress: () -> Unit,
    onDelete: (String) -> Unit,
    /** 可选卡片容器（如 Glasmio 玻璃卡）：为 null 时用默认 MD3 [Card] 表面 */
    cardContainer: (@Composable (Modifier, @Composable () -> Unit) -> Unit)? = null,
    /** 可选进度条（玻璃外壳传 LiquidProgressBar）：为 null 时用 Material 进度条 */
    progressBar: (@Composable (Float) -> Unit)? = null
) {
    val haptic = rememberHapticFeedback()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // 状态用 State 对象承载：pointerInput(Unit) 闭包跨重组读取最新值
    var boxWidthPx by remember { mutableIntStateOf(0) }
    val offsetX = remember { Animatable(0f) }        // 侧滑位移（px，正=向右）
    val trashExit = remember { Animatable(0f) }      // 删除时垃圾桶滑向屏幕正中并淡出的进度
    var overThreshold by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    // 未过 1/3 松手后停在「刚好露出垃圾桶」的位置，此时垃圾桶可点按删除
    var settled by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }

    val trashSize = 44.dp
    val trashSizePx = with(density) { trashSize.toPx() }
    val trashMargin = 12.dp
    val revealPx = with(density) { (trashSize + trashMargin * 2).toPx() }

    // 列表项即按钮：按下轻微内缩（只做缩放，不做玻璃拖拽形变，
    // 否则横向拖动会被形变吃掉，列表无法上下滚动）
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !dragging && !exiting) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "pressScale"
    )

    // 删除：按松手速度**匀速**滑出屏幕（非减速），垃圾桶同时滑向屏幕正中并淡出
    fun startDelete(flingVelocity: Float) {
        val width = boxWidthPx.toFloat()
        if (exiting || width <= 0f) return
        exiting = true
        dragging = false
        settled = false
        val dir = if (offsetX.value <= 0f) -1f else 1f
        val speed = max(abs(flingVelocity), with(density) { 1400.dp.toPx() })
        val remaining = (width - abs(offsetX.value)).coerceAtLeast(1f)
        val duration = (remaining / speed * 1000f).toInt().coerceIn(120, 480)
        scope.launch {
            launch { trashExit.animateTo(1f, tween(duration, easing = LinearEasing)) }
            offsetX.animateTo(dir * width, tween(duration, easing = LinearEasing))
            onDelete(item.path)
        }
    }

    // 处理中 / 退出动画中：禁用侧滑与长按手势。
    // 横向拖拽在纵向位移占优时会让位给列表滚动（detectHorizontalDragGestures 的
    // slop 竞争），因此列表照常可以上下滚动，只有明确的横向滑动才进入侧滑删除。
    val gestureModifier = if (!isConverting && !exiting) {
        Modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        velocityTracker.resetTracking()
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val width = boxWidthPx.toFloat()
                        if (width <= 0f) return@detectHorizontalDragGestures
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val target = (offsetX.value + dragAmount).coerceIn(-width, width)
                        scope.launch { offsetX.snapTo(target) }
                        // 刚越过 / 回到 1/3：清脆振动（删除判定线反馈）
                        val over = abs(target) > width / 3f
                        if (over != overThreshold) {
                            overThreshold = over
                            haptic.click()
                        }
                    },
                    onDragEnd = {
                        dragging = false
                        val width = boxWidthPx.toFloat()
                        if (width <= 0f) return@detectHorizontalDragGestures
                        val velocity = velocityTracker.calculateVelocity().x
                        velocityTracker.resetTracking()
                        val offset = offsetX.value
                        when {
                            // 过 1/3：直接删除（以松手速度匀速滑出，不减速）
                            abs(offset) > width / 3f -> startDelete(velocity)
                            // 未过 1/3：停在刚好露出垃圾桶的位置，垃圾桶可点按删除
                            abs(offset) > revealPx * 0.4f -> {
                                settled = true
                                val dir = if (offset <= 0f) -1f else 1f
                                scope.launch {
                                    offsetX.animateTo(
                                        dir * revealPx,
                                        spring(dampingRatio = 0.86f, stiffness = 380f)
                                    )
                                }
                            }
                            // 只是轻碰：回位
                            else -> scope.launch {
                                offsetX.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 380f))
                                overThreshold = false
                                settled = false
                            }
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        val offset = offsetX.value
                        if (abs(offset) > revealPx * 0.4f) {
                            settled = true
                            val dir = if (offset <= 0f) -1f else 1f
                            scope.launch {
                                offsetX.animateTo(
                                    dir * revealPx,
                                    spring(dampingRatio = 0.86f, stiffness = 380f)
                                )
                            }
                        } else {
                            scope.launch {
                                offsetX.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 380f))
                                overThreshold = false
                                settled = false
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                // 长按列表项：单独设置本项输出格式；
                // 点按在「已露出垃圾桶」的状态下收起侧滑
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        if (settled && !exiting) {
                            settled = false
                            overThreshold = false
                            scope.launch {
                                offsetX.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 380f))
                            }
                        }
                    },
                    onLongPress = {
                        haptic.longPress()
                        onLongPress()
                    }
                )
            }
    } else Modifier

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { boxWidthPx = it.width }
            .clipToBounds()
            .then(gestureModifier)
    ) {
        // 背景层：圆形玻璃垃圾桶按钮。
        // 水平位置：未过 1/3 贴住屏幕左/右边缘（留边距）；越过 1/3 附近平滑滑到
        // 「屏幕边缘 ↔ 项目边缘」两坐标的中点。位置在 layout 阶段由 offset lambda 计算，
        // 拖动时只重排/重绘，不重组列表项。
        if (boxWidthPx > 0 && (dragging || exiting || settled)) {
            val glassBadge = LocalGlassEnabled.current && LocalGlassRootBackdrop.current != null
            val badgePhysics = if (glassBadge) rememberGlassButtonPhysics(maxOffset = 2.dp) else null
            val red = MaterialTheme.colorScheme.error
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        val width = boxWidthPx.toFloat()
                        val off = offsetX.value
                        val marginCenter = (trashMargin + trashSize / 2).toPx()
                        val pinned = if (off <= 0f) width - marginCenter else marginCenter
                        val gap = if (off <= 0f) width + off / 2f else off / 2f
                        val blend = ((abs(off) - width * 0.18f) / (width * 0.15f)).coerceIn(0f, 1f)
                        val eased = blend * blend * (3f - 2f * blend)
                        val center = pinned + (gap - pinned) * eased
                        // 删除时垃圾桶滑向整行（屏幕）正中
                        val exitCenter = center + (width / 2f - center) * trashExit.value
                        IntOffset((exitCenter - trashSizePx / 2f).roundToInt(), 0)
                    }
                    .size(trashSize)
                    .graphicsLayer { alpha = (1f - trashExit.value).coerceIn(0f, 1f) }
                    .then(
                        if (glassBadge && badgePhysics != null) Modifier.liquidGlass(
                            CircleShape,
                            // 圆形玻璃按钮：红色与底部「清空」按钮同源（colorScheme.error）
                            tintAlpha = 0.22f,
                            tintColor = red,
                            lensHeight = 8.dp,
                            lensAmount = 12.dp,
                            layerBlock = badgePhysics.transform,
                            backdropOverride = LocalGlassRootBackdrop.current
                        ) else Modifier.background(red, CircleShape)
                    )
                    .then(if (badgePhysics != null) badgePhysics.highlight.gestureModifier else Modifier)
                    .then(
                        // 停在露出垃圾桶的位置时，垃圾桶本身即删除按钮
                        if (settled && !exiting) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.click()
                            startDelete(0f)
                        } else Modifier
                    )
            ) {
                TrashCan(
                    progress = {
                        val width = boxWidthPx.toFloat()
                        if (width > 0f) abs(offsetX.value) / width else 0f
                    },
                    openLeft = { offsetX.value <= 0f },
                    tint = if (glassBadge) red else MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(11.dp)
                )
            }
        }

        // 前景卡片：侧滑平移 + 按压缩放（列表项作为可交互「按钮」）
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = offsetX.value
                    scaleX = pressScale
                    scaleY = pressScale
                }
        ) {
            FileCardContent(item = item, sameFormat = sameFormat, cardContainer = cardContainer, progressBar = progressBar)
        }
    }
}

/**
 * 垃圾桶图形（自绘，跟随侧滑进度做「开盖 + 桶身反向倾斜」）。
 *
 * - 1/3 之前：盖子随滑动缓慢打开（0° → 12°），桶身不动
 * - 超过 1/3：盖子明显张开（12° → 68°），桶身向反方向倾斜（0° → 10°）
 * - 向左滑时盖子左端抬起（铰链在右），向右滑时盖子右端抬起（铰链在左）
 */
@Composable
private fun TrashCan(
    progress: () -> Float,
    openLeft: () -> Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val p = progress().coerceIn(0f, 1f)
        val left = openLeft()
        val oneThird = 1f / 3f
        val lidAngle =
            if (p <= oneThird) 12f * (p / oneThird)
            else 12f + 56f * ((p - oneThird) / (1f - oneThird))
        val bodyTilt = if (p <= oneThird) 0f else 10f * ((p - oneThird) / (1f - oneThird))
        val dir = if (left) 1f else -1f
        val sw = 2.dp.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val w = size.width
        val h = size.height
        val bodyTop = h * 0.36f
        val bodyBottom = h * 0.93f

        // 桶身：绕底部中心向盖子的反方向倾斜
        rotate(degrees = bodyTilt * dir, pivot = Offset(w / 2f, bodyBottom)) {
            val body = Path().apply {
                moveTo(w * 0.19f, bodyTop)
                lineTo(w * 0.26f, bodyBottom)
                lineTo(w * 0.74f, bodyBottom)
                lineTo(w * 0.81f, bodyTop)
            }
            drawPath(body, color = tint, style = stroke)
            drawLine(
                color = tint,
                start = Offset(w * 0.40f, bodyTop + h * 0.10f),
                end = Offset(w * 0.43f, bodyBottom - h * 0.09f),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.60f, bodyTop + h * 0.10f),
                end = Offset(w * 0.57f, bodyBottom - h * 0.09f),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }

        // 盖子：绕铰链旋转（左滑铰链在右端，右滑铰链在左端），把手随盖子一起转
        val lidY = h * 0.28f
        val hingeX = if (left) w * 0.88f else w * 0.12f
        rotate(degrees = lidAngle * dir, pivot = Offset(hingeX, lidY)) {
            drawLine(
                color = tint,
                start = Offset(w * 0.12f, lidY),
                end = Offset(w * 0.88f, lidY),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
            val handle = Path().apply {
                moveTo(w * 0.42f, lidY)
                lineTo(w * 0.45f, lidY - h * 0.11f)
                lineTo(w * 0.55f, lidY - h * 0.11f)
                lineTo(w * 0.58f, lidY)
            }
            drawPath(handle, color = tint, style = stroke)
        }
    }
}

// ---------- 卡片内容 ----------

@Composable
private fun FileCardContent(
    item: FileItem,
    sameFormat: Boolean,
    cardContainer: (@Composable (Modifier, @Composable () -> Unit) -> Unit)? = null,
    progressBar: (@Composable (Float) -> Unit)? = null
) {
    // 缩略图：IO 线程降采样解码 + EXIF 方向校正
    val thumb by produceState<Bitmap?>(null, item.path) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(item.path) }
    }
    val nameColor = when {
        sameFormat -> MaterialTheme.colorScheme.primary
        item.isUnrecognized -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val infoColor = when {
        sameFormat -> MaterialTheme.colorScheme.primary
        item.isUnrecognized -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    // 卡片自带一层薄底（列表外面不再包玻璃面板）：文字直接压在渐变/照片上
    // 会透出一圈「光晕」，给卡片一个高不透明度的卡片底色即可干净收边
    val glass = LocalGlassEnabled.current
    // 内容只写一份：默认 MD3 Card 与外部传入容器（Glasmio 玻璃卡）共用同一份 Row
    val rowContent: @Composable () -> Unit = {
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
                        else if (glass) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
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
                        tint = when {
                            item.isUnrecognized -> MaterialTheme.colorScheme.onErrorContainer
                            glass -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
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
                // 视频转码进度：只在该任务自己的卡片内展开，起止用等高动画平滑过渡，
                // 避免进度出现/消失时列表项高度突变
                AnimatedVisibility(
                    visible = item.transcoding,
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(200)),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(140))
                ) {
                    TranscodeProgress(item, progressBar)
                }
            }
        }
    }
    if (cardContainer == null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            // 玻璃模式：更圆的大圆角 + 半透明底 + 细描边（此前的 16dp 不透明卡面
            // 在液态玻璃界面里仍是 MD3 观感——用户反馈「列表项边框还是 MD3 的」）
            shape = RoundedCornerShape(if (glass) 22.dp else 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (glass) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
                else MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            border = if (glass) BorderStroke(
                0.75.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            ) else null,
            elevation = CardDefaults.cardElevation(defaultElevation = if (glass) 0.dp else 1.dp)
        ) {
            rowContent()
        }
    } else {
        cardContainer(Modifier.fillMaxWidth()) { rowContent() }
    }
}

/**
 * 转码进度（单个任务）：进度条以「已处理帧 / 总帧数」为准，并给出预计剩余时间。
 * 总帧数未知时退化为「已处理 N 帧」（不显示百分比与剩余时间）。
 */
@Composable
private fun TranscodeProgress(item: FileItem, progressBar: (@Composable (Float) -> Unit)? = null) {
    val total = item.transcodeTotal
    val known = total > 0L
    val fraction = if (known) (item.transcodeFrame.toFloat() / total).coerceIn(0f, 1f) else 0f
    // ffmpeg 每 0.5s 上报一次；用同长度线性补间把台阶填成连续推进，避免进度条一顿一顿
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(500, easing = LinearEasing),
        label = "transcodeFraction"
    )
    val eta = item.transcodeEtaSec
    val label = buildString {
        if (known) {
            append(stringResource(R.string.screen_transcode_progress, (fraction * 100f).roundToInt()))
            append(" · " + stringResource(R.string.screen_transcode_frames, item.transcodeFrame, total))
            if (eta >= 0L) append(" · " + stringResource(R.string.screen_transcode_eta, formatEta(eta)))
        } else {
            append(stringResource(R.string.screen_transcode_in_progress, item.transcodeFrame))
        }
    }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        // 玻璃外壳传入 LiquidProgressBar（4dp 胶囊 + glass separator 轨道），
        // 避免同一屏出现 Material 进度条与玻璃进度条两种视觉。
        if (progressBar != null) {
            progressBar(animated)
        } else {
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 预计剩余秒数 → 「12 秒」/「1 分 05 秒」 */
@Composable
private fun formatEta(sec: Long): String =
    if (sec < 60L) {
        stringResource(R.string.screen_eta_seconds, sec)
    } else {
        stringResource(R.string.screen_eta_minutes, sec / 60, (sec % 60).toString().padStart(2, '0'))
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
