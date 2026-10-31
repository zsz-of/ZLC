package com.zsz.zlivephoto.ui.picker

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.annotation.StringRes
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.zsz.zlivephoto.BuildConfig
import com.zsz.zlivephoto.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Check
import com.zsz.zlivephoto.ui.formatOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.zsz.zlivephoto.ui.AlphaEasing
import com.zsz.zlivephoto.ui.AppSettings
import com.zsz.zlivephoto.ui.FancyEasing
import com.zsz.zlivephoto.ui.LocalDarkTheme
import com.zsz.zlivephoto.ui.glass.rememberAdaptiveContentColor
import com.zsz.zlivephoto.ui.PressFeedback
import com.zsz.zlivephoto.ui.PressHapticEffect
import com.zsz.zlivephoto.ui.ReminderInfoDialog
import com.zsz.zlivephoto.ui.ReminderKey
import com.zsz.zlivephoto.ui.glass.GlassBackButton
import com.zsz.zlivephoto.ui.glass.GlassIconButton
import com.zsz.zlivephoto.ui.glass.GlassMenuEntry
import com.zsz.zlivephoto.ui.glass.GlassMenuOverlay
import com.zsz.zlivephoto.ui.glass.GlassMenuRow
import com.zsz.zlivephoto.ui.glass.GlassPanel
import com.zsz.zlivephoto.ui.glass.LocalGlassBackdrop
import com.zsz.zlivephoto.ui.glass.LocalGlassEnabled
import com.zsz.zlivephoto.ui.glass.PublishPageBackdrop
import com.zsz.zlivephoto.ui.glass.TopBarFrost
import com.zsz.zlivephoto.ui.glass.animatedThemeColor
import com.zsz.zlivephoto.ui.glass.liquidDraggable
import com.zsz.zlivephoto.ui.glass.liquidGlass
import com.zsz.zlivephoto.ui.glass.liquidMorphClip
import com.zsz.zlivephoto.ui.glass.rememberGlassButtonPhysics
import com.zsz.zlivephoto.ui.glass.rememberLiquidDragState
import com.zsz.zlivephoto.ui.glass.rememberLiquidMorphProgress
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.shadow.Shadow
import com.zsz.zlivephoto.ui.rememberPressFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.util.Calendar

/**
 * 缩略图内存 LRU 缓存（项 9 按需加载）：
 * - 每次仅组合屏幕可见区域附近的网格项，滚动回来命中缓存直接显示
 * - 容量 96 条（约 5 屏）：未超出屏幕过远的保持加载，过远的被 LRU 淘汰（卸载）
 */
private val thumbCache = LruCache<String, Bitmap>(96)

// 缩略图加载有界并发：限制同时进行的 ContentResolver.loadThumbnail 数量，
// 避免快速滚动/千张列表时并发 I/O 过多拖垮主线程与磁盘，造成卡顿
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val thumbDispatcher = Dispatchers.IO.limitedParallelism(
    if (BuildConfig.FLAVOR == "go") 1 else 4
)

/** 相册内排序字段 */
enum class AlbumSortField { DATE, SIZE, NAME }

/** 按字段排序（正/倒序）。名称比较忽略大小写，日期取 sortTime（拍摄时间，缺失回退修改时间）。 */
private fun sortMediaItems(
    items: List<MediaItem>,
    field: AlbumSortField,
    ascending: Boolean
): List<MediaItem> {
    val comparator = when (field) {
        AlbumSortField.DATE -> compareBy<MediaItem> { it.sortTime }
        AlbumSortField.SIZE -> compareBy { it.size }
        AlbumSortField.NAME -> compareBy { it.name.lowercase() }
    }
    return if (ascending) items.sortedWith(comparator)
           else items.sortedWith(comparator.reversed())
}

/** 按名称搜索过滤（忽略大小写） */
private fun filterMediaItems(items: List<MediaItem>, query: String): List<MediaItem> {
    if (query.isBlank()) return items
    return items.filter { it.name.contains(query, ignoreCase = true) }
}

/** 媒体类型筛选：全部 / 仅动态照片 / 仅视频 / 仅图片（先筛后排，排序对筛选结果生效） */
enum class MediaFilter { ALL, LIVE, VIDEO, IMAGE }

/** 按媒体类型筛选（动态照片 = 识别到厂商格式的图片） */
private fun applyMediaFilter(items: List<MediaItem>, filter: MediaFilter): List<MediaItem> =
    when (filter) {
        MediaFilter.ALL -> items
        MediaFilter.LIVE -> items.filter { it.kind == MediaKind.LIVE }
        MediaFilter.VIDEO -> items.filter { it.kind == MediaKind.VIDEO }
        MediaFilter.IMAGE -> items.filter { it.kind == MediaKind.IMAGE }
    }

/** 媒体类型筛选项文案 */
private fun mediaFilterLabelRes(f: MediaFilter): Int = when (f) {
    MediaFilter.ALL -> R.string.screen_filter_all
    MediaFilter.LIVE -> R.string.screen_filter_live
    MediaFilter.VIDEO -> R.string.screen_filter_video
    MediaFilter.IMAGE -> R.string.screen_filter_image
}

/**
 * 选择器返回按钮：
 * - 玻璃启用：圆形液态玻璃按钮 + [AppleChevron]（苹果风格「<」，比 Material 更开）
 * - 否则：原生 IconButton + ArrowBack（原模式保留）
 */
@Composable
private fun BackButton(onBack: () -> Unit) {
    GlassBackButton(onBack = onBack, contentDescription = stringResource(R.string.screen_back))
}

/**
 * 确认导入按钮：
 * - 文案 = 「导入」+ 动态照片/图片/视频三类张数（数字变化时逐位上下滚动切换）
 * - 宽度随文案变化用弹簧动画过渡
 * - 玻璃启用时为液态玻璃按钮（按下形态沿用 [PressFeedback] 的圆角/缩放反馈）
 * - 图片与视频数量不匹配（或未选）时禁用
 */
@Composable
private fun ConfirmImportButton(
    canConfirm: Boolean,
    liveCount: Int,
    imageCount: Int,
    videoCount: Int,
    feedback: PressFeedback,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassEnabled.current
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(feedback.corner)
    // 内容自适应着色（「根据背景自动切黑/白」的判断器）：
    // 导入按钮永远浮在照片之上，玻璃折射到的背景亮度不定，
    // 文字/计数按背景亮暗自动切换黑/白，保证任何照片都可读。
    val adaptive = rememberAdaptiveContentColor(
        initialColor = if (dark) Color.White else Color.Black,
        enabled = glass && canConfirm
    )
    // 演示版（LiquidButton）同款物理：触点径向高光 + tanh 阻尼拖拽 + 方向形变。
    // layerBlock 的 graphicsLayer 包裹整个节点，玻璃与文字作为整体变形。
    // maxOffset 收到 2dp：按钮贴在屏幕右下角，形变/放大幅度越小越不会
    // 在拖拽时poke出屏幕边缘被裁切（用户反馈「拖拽或点击时内容被裁切」）。
    val physics = rememberGlassButtonPhysics(maxOffset = 2.dp)
    val textColor = when {
        !canConfirm -> MaterialTheme.colorScheme.onSurfaceVariant
        glass -> adaptive.color
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Box(
        modifier = modifier
            .then(feedback.scaleModifier)
            .animateContentSize(
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
            )
            .then(
                if (glass) Modifier.liquidGlass(
                    shape,
                    tintAlpha = if (canConfirm) 0.34f else 0.05f,
                    lensHeight = 12.dp,
                    lensAmount = 22.dp,
                    // 关掉默认边缘高光：1.4dp 白描边在照片底上读起来就是一圈
                    // 生硬边框（用户反馈「移除导入按钮四周生硬边框」）；
                    // 层次改由 tint + 透镜折射 + 轻投影表达
                    highlight = null,
                    layerBlock = physics.transform,
                    onDrawBackdrop = if (canConfirm) adaptive.recordBackdrop else null
                )
                else Modifier.background(
                    if (canConfirm) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape
                )
            )
            .then(if (glass && canConfirm) physics.highlight.modifier else Modifier)
            .clip(shape)
            .clickable(
                enabled = canConfirm,
                interactionSource = feedback.interactionSource,
                indication = null
            ) { onClick() }
            .then(if (glass && canConfirm) physics.highlight.gestureModifier else Modifier)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.screen_import),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1
            )
            if (liveCount + imageCount + videoCount > 0) {
                Spacer(Modifier.size(8.dp))
                CountLabel(R.string.screen_count_live, liveCount, textColor)
                CountSeparator(textColor)
                CountLabel(R.string.screen_count_image, imageCount, textColor)
                CountSeparator(textColor)
                CountLabel(R.string.screen_count_video, videoCount, textColor)
            }
        }
    }
}

/** 确认按钮内的单项计数：短标签（动/图/视）+ 滚动数字 */
@Composable
private fun CountLabel(@StringRes labelRes: Int, count: Int, color: Color) {
    val style = MaterialTheme.typography.labelLarge
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(labelRes),
            style = style,
            color = color.copy(alpha = 0.8f),
            maxLines = 1
        )
        RollingNumber(
            value = count,
            style = style.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun CountSeparator(color: Color) {
    Text(
        "·",
        style = MaterialTheme.typography.labelLarge,
        color = color.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 5.dp)
    )
}

/**
 * MD3 风格「滚动数字」：逐位用 [AnimatedContent] 竖向滑入滑出，
 * 数值增大时新数字自下而上滚入，减小时自上而下滚入。
 */
@Composable
private fun RollingNumber(value: Int, style: TextStyle, color: Color) {
    val digits = value.toString()
    Row {
        digits.forEachIndexed { i, c ->
            key(i) {
                AnimatedContent(
                    targetState = c,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInVertically(tween(220, easing = FancyEasing)) { it } +
                                    fadeIn(tween(160)))
                                .togetherWith(
                                    slideOutVertically(tween(220, easing = FancyEasing)) { -it } +
                                            fadeOut(tween(120)))
                        } else {
                            (slideInVertically(tween(220, easing = FancyEasing)) { -it } +
                                    fadeIn(tween(160)))
                                .togetherWith(
                                    slideOutVertically(tween(220, easing = FancyEasing)) { it } +
                                            fadeOut(tween(120)))
                        }
                    },
                    label = "digit"
                ) { d ->
                    Text(
                        d.toString(),
                        style = style,
                        color = color,
                        maxLines = 1,
                        modifier = Modifier.clipToBounds()
                    )
                }
            }
        }
    }
}

/**
 * 内置动态照片选择器。
 *
 * UI 参考 ImageToolbox（github.com/T8RIN/ImageToolbox，Apache License 2.0）的 media-picker：
 * - 顶栏 surfaceContainer 容器：相册 chip 横滑条，右端 chevron 旋转按钮切换封面展开态
 * - 网格单元选中：以几何中心为变换中心缩放 + 圆角过渡；勾选徽章在右上角
 * - 照片序号：全选时按网格顺序（从上到下、从左到右）依次标记
 * - 排序动画：时间↔名称整体模糊缩放过渡；升降序位置平滑移动；日期头数字竖向滚动
 * - 日期头/全选使用圆形三态复选框；张数在左侧、全选在右侧
 * - 缩略图按需加载（LRU 缓存 + 呼吸占位动画）
 *
 * 扫描/识别逻辑为本项目 AlbumScanner（会话化多线程扫描），与 ImageToolbox 无关。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoPickerScreen(
    albums: List<AlbumInfo>,
    scanner: AlbumScanner,
    scannerScope: CoroutineScope,
    isDarkTheme: Boolean,
    /** 右上角输出格式（key）：决定本次导入的输出格式（含 "extract"=拆解） */
    outputFormat: String,
    onOutputFormatChange: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: (List<MediaItem>) -> Unit
) {
    val haptic = com.zsz.zlivephoto.ui.rememberHapticFeedback()
    var currentBucketId by remember { mutableStateOf(albums.firstOrNull()?.bucketId) }
    val bucketId = currentBucketId
    val selected = remember { mutableStateListOf<MediaItem>() }
    var albumsExpanded by remember { mutableStateOf(false) }
    // 相册切换滚动方向（项 4）：1=切到右侧相册（新内容自右侧进入、画面向左滚），-1=反向
    var slideDir by remember { mutableStateOf(1) }
    // 排序（大小/日期/名称 + 正倒序）与按名称搜索。
    // 排序字段 / 顺序 / 媒体筛选从 AppSettings 读出初值（用户要求：展开菜单里的
    // 设置要沿用到下一次，不能每次进入选择器都被重置），变更后再写回持久化。
    var sortField by remember {
        mutableStateOf(
            runCatching { AlbumSortField.valueOf(AppSettings.pickerSortField) }
                .getOrDefault(AlbumSortField.DATE)
        )
    }
    var sortAscending by remember { mutableStateOf(AppSettings.pickerSortAscending) }
    var searchQuery by remember { mutableStateOf("") }
    // 媒体类型筛选（全部 / 仅动态照片 / 仅视频 / 仅图片），与排序独立、先筛后排
    var mediaFilter by remember {
        mutableStateOf(
            runCatching { MediaFilter.valueOf(AppSettings.pickerMediaFilter) }
                .getOrDefault(MediaFilter.ALL)
        )
    }
    // 写回持久化（首帧与已存值相同，重复写入无副作用）
    LaunchedEffect(sortField, sortAscending) {
        AppSettings.setPickerSort(sortField.name, sortAscending)
    }
    LaunchedEffect(mediaFilter) {
        AppSettings.rememberPickerMediaFilter(mediaFilter.name)
    }
    // 右上角「更多」菜单：moreMenuOpen=菜单显隐；morePage=0 全部收起（4 个父项
    // 平铺），1..4=对应分组原地展开（其后的父项让位，子面板下方直接收边）
    var moreMenuOpen by remember { mutableStateOf(false) }
    var morePage by remember { mutableStateOf(0) }
    // 搜索：点按钮后原地横向展开为玻璃搜索框
    var searchOpen by remember { mutableStateOf(false) }
    // 搜索展开形变：一条欠阻尼弹簧驱动「搜索钮触发圆 → 整行搜索框」的裁剪形变、
    // 玻璃模糊与内容入场（复刻 liquid_glass_widgets 菜单的 morph）
    val searchMorph = rememberLiquidMorphProgress(searchOpen)
    // 触发圆锚点：搜索钮的根坐标矩形（竖屏/横屏各自测量，同一时刻只有一个）
    var searchAnchor by remember { mutableStateOf(Rect.Zero) }

    // 进入/离开相册：进入 diff+续扫，离开取消（保留进度）
    DisposableEffect(bucketId) {
        if (bucketId != null) scanner.enter(bucketId, scannerScope)
        onDispose { if (bucketId != null) scanner.leave(bucketId) }
    }

    // 切到「拆解」时动态照片不可选：把已选的动态照片自动移除，
    // 避免出现「已选中但点不动、也无法取消」的幽灵选择
    LaunchedEffect(outputFormat) {
        if (outputFormat == "extract") selected.removeAll { it.kind == MediaKind.LIVE }
    }

    val state = remember(bucketId) { if (bucketId != null) scanner.stateOf(bucketId) else null }
    val results = state?.results
    val total = state?.total ?: 0
    // 进度轮询（done/running 为普通并发变量，需轮询驱动重组）
    val progress by produceState(0 to false, state) {
        while (true) {
            value = (state?.doneCount?.get() ?: 0) to (state?.running ?: false)
            delay(200)
        }
    }
    val running = progress.second

    // 网格显示顺序（项 3 有序标记）：全选时按此顺序（从上到下、从左到右）添加，
    // 序号与视觉顺序一致；应用排序（大小/日期/名称 + 正倒序）与名称搜索过滤；
    // size 读取驱动扫描追加时重算
    val n = results?.size ?: 0
    val displayOrder = remember(results, n, sortField, sortAscending, searchQuery, mediaFilter) {
        sortMediaItems(
            applyMediaFilter(filterMediaItems(results.orEmpty(), searchQuery), mediaFilter),
            sortField, sortAscending
        )
    }

    // 合成模式视频时长告警阈值（超过 3 秒提示兼容性风险，但仍允许选择）
    val videoWarnMs = 3000L

    // 合成模式提示弹窗状态
    var showOver3sWarning by remember { mutableStateOf(false) }

    /** 项是否已选中 */
    fun isSelected(item: MediaItem): Boolean =
        selected.any { it.id == item.id && it.isVideo == item.isVideo }

    /**
     * 项是否可选：拆解模式下**动态照片不可选**（用户需求）。
     * 拆解是「把已有动态照片拆成图片+视频」，与「选动态照片来转换」互斥；
     * 同时该模式下图片/视频用于合成，动态照片没有意义。
     */
    val isSelectable: (MediaItem) -> Boolean = { item ->
        !(outputFormat == "extract" && item.kind == MediaKind.LIVE)
    }

    // 选择统计（确认按钮内显示：动态照片 / 图片 / 视频 各自张数）
    val liveCount = selected.count { it.kind == MediaKind.LIVE }
    val imageCount = selected.count { it.kind == MediaKind.IMAGE }
    val videoCount = selected.count { it.kind == MediaKind.VIDEO }
    // 确认导入：至少 1 项，且图片与视频数量必须一致
    // （合成按选择顺序两两配对，数量不等就无法全部配对，禁止确认）
    val canConfirm = selected.isNotEmpty() && imageCount == videoCount
    val doConfirm: () -> Unit = { onConfirm(selected.toList()) }

    // 单项选择切换（追加到末尾，序号递增）；选择超过 3 秒的视频时提示兼容性风险
    val toggleItem: (MediaItem) -> Unit = { item ->
        if (!isSelectable(item)) {
            // 不可选（拆解模式下的动态照片）：仅给出触感反馈，不改变选择
            haptic.click()
        } else {
            haptic.click()
            val idx = selected.indexOfFirst { it.id == item.id && it.isVideo == item.isVideo }
            if (idx >= 0) {
                selected.removeAt(idx)
            } else {
                selected.add(item)
                if (item.isVideo && item.durationMs > videoWarnMs &&
                    !AppSettings.isReminderSuppressed(ReminderKey.COMPOSE_VIDEO_OVER_3S)) {
                    showOver3sWarning = true
                }
            }
        }
    }
    // 成组选择切换（日期栏/全选）：组内可选项全选 → 全部取消；否则按传入顺序补齐未选
    val toggleGroup: (List<MediaItem>) -> Unit = { group ->
        val choosable = group.filter { isSelectable(it) }
        val allIn = choosable.isNotEmpty() && choosable.all { isSelected(it) }
        haptic.click()
        if (allIn) {
            selected.removeAll { s -> choosable.any { it.id == s.id && it.isVideo == s.isVideo } }
        } else {
            for (g in choosable) {
                if (selected.none { it.id == g.id && it.isVideo == g.isVideo }) selected.add(g)
            }
        }
    }

    /** 项的选中序号 */
    fun selIndexOf(item: MediaItem): Int =
        selected.indexOfFirst { it.id == item.id && it.isVideo == item.isVideo }

    // 相册切换（项 4）：无论间隔多少个相册，均一次性平滑过渡到目标（直接跳转，
    // 不经过中间相册，避免多级动画叠加的臃肿感）
    val switchAlbum: (Long) -> Unit = { target ->
        if (target != bucketId) {
            val oldIdx = albums.indexOfFirst { it.bucketId == bucketId }
            val newIdx = albums.indexOfFirst { it.bucketId == target }
            slideDir = if (newIdx >= oldIdx) 1 else -1
            currentBucketId = target
        }
    }

    // 导入按钮点击动画（项 1）
    val importFb = rememberPressFeedback(baseCorner = 20.dp, pressedCorner = 8.dp)

    // 根容器实测尺寸（横竖屏判断的数据源，由下方根 Box 的 onSizeChanged 回填）。
    // 必须声明在 key(isDarkTheme) **之外**：key 重组会重置 remember 值，而尺寸没变时
    // onSizeChanged 不会重发，横屏状态会被清零后误判回竖屏。
    var rootSizePx by remember { mutableStateOf(IntSize.Zero) }
    // 深浅色切换时以 isDarkTheme 为 key 驱动全树重组：
    // uiMode configChanges 下部分控件曾不随主题切换
    key(isDarkTheme) {
    // 横屏判断：以根容器**实测尺寸**为准，LocalConfiguration 仅作首帧兜底。
    // 手动 configChanges 模式下若系统没把新方向分发给界面（个别 ROM），横屏会被
    // 误判成竖屏——相册栏留在顶部、横屏左右分栏不出现（用户反馈「相册栏还在顶部」）。
    val isLandscape = if (rootSizePx == IntSize.Zero)
        LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    else rootSizePx.width > rootSizePx.height
    // 横屏左侧相册栏宽度：收起为窄栏（仅相册名），展开时整栏由左向右变宽（配合封面右向展开）
    val railWidth by animateDpAsState(
        targetValue = if (albumsExpanded) 188.dp else 148.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "albumRailWidth"
    )
    // 顶栏覆盖层：网格是滚动内容、也是玻璃采样层；顶栏浮在其上叠纯色渐变
    val glassNow = LocalGlassEnabled.current
    val pickerBackdrop = rememberLayerBackdrop()
    // 发布给应用级弹窗（渲染在本页之外）采样「根 + 本页」：同窗口坐标对正、
    // 模糊实时（用户反馈 #5/#6），复用本录制零开销
    PublishPageBackdrop(if (glassNow) pickerBackdrop else null)
    // 顶栏覆盖层实测高度（px）：onSizeChanged 布局后回填，首帧为 0（网格首屏内容暂不顶到顶栏下方，扫描完成前通常为空态，闪烁可忽略）
    var chromeHeightPx by remember { mutableIntStateOf(0) }
    // 收起态顶栏高度：模糊带只按收起态铺，相册展开时封面长高不把模糊区一起拉长（v4 回归修复）
    var collapsedChromePx by remember { mutableIntStateOf(0) }
    val chromeHeightDp = with(LocalDensity.current) { chromeHeightPx.toDp() }
    val collapsedChromeDp = with(LocalDensity.current) { collapsedChromePx.toDp() }
    // 顶栏玻璃带高度 = 顶栏内容实测高度（含状态栏），**不再向下伸渐隐尾巴**：
    // 日期分组 stickyHeader 钉在内容下方，尾巴伸进该位置会把日期栏盖在玻璃块里
    // ——看不见也点不到（用户反馈「日期栏被相册玻璃块遮挡无法点击」）。
    // 玻璃带下沿与内容底边齐平后日期栏完整落在玻璃带下方。
    val veilHeight = if (chromeHeightPx > collapsedChromePx) chromeHeightDp else collapsedChromeDp
    // 输出格式菜单与返回按钮都是玻璃 chrome：整屏 Box 承载菜单浮层
    // （库在独立 Window 内采样坐标错误，弹窗/菜单必须同窗口 Compose overlay）
    Box(
        Modifier
            .fillMaxSize()
            // 实测窗口尺寸回填给 rootSizePx：旋转后本修饰符随根布局重新测量，
            // 不依赖系统把 Orientation 配置分发到界面（横竖屏判断的真正数据源）
            .onSizeChanged { rootSizePx = it }
    ) {
    Row(
        Modifier
            .fillMaxSize()
            .then(
                // 玻璃启用时容器背景透明，才能看到 GlassRoot 铺的采样源渐变
                if (LocalGlassEnabled.current) Modifier
                else Modifier.background(MaterialTheme.colorScheme.background)
            )
    ) {
        // ── 横屏：最左侧纵向铺开的相册栏（展开方向由左到右）──
        if (isLandscape) {
            Column(
                Modifier
                    .width(railWidth)
                    .fillMaxHeight()
                    .then(
                        if (LocalGlassEnabled.current) Modifier
                        else Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                    )
                    .statusBarsPadding()
                    // 横屏：列表最后一项不能被导航栏遮住
                    .navigationBarsPadding()
            ) {
                // 行 1：返回。标题移到独立一行——收起态栏宽仅 148dp，
                // 标题与按钮同行时被 weight(1f) 挤到只剩几十 dp，
                // 「选择动态照片」被截断成「选择动态…」（用户反馈「标题有时会被截断」）。
                // 「搜索 / 更多」不放本栏：横屏统一挪到**屏幕右上角**（内容区右上覆盖层），
                // 搜索在左、更多在右（用户需求）。
                // 左右内边距统一 12dp：与竖屏顶栏、网格的页面内缩一致
                // （用户反馈「横屏部件左右太靠边缘，横竖屏距边缘的宽度也不一样」）
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackButton(onBack)
                }
                // 行 2：标题独立成行（独占整栏宽度，不再与按钮争抢）
                Text(
                    stringResource(R.string.screen_picker_title_select),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                )
                // 相册数 + 展开/收起封面（箭头未展开朝右、展开后朝左）
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.screen_album_count, albums.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    // 「搜索」不放本栏：与「更多」一起挪到屏幕右上角（内容区右上覆盖层）
                    // 展开/收起封面（玻璃圆钮，与顶栏按钮同款）
                    GlassIconButton(
                        onClick = { haptic.click(); albumsExpanded = !albumsExpanded },
                        contentDescription = if (albumsExpanded) stringResource(R.string.screen_album_collapse_cover) else stringResource(R.string.screen_album_expand_cover)
                    ) {
                        val rotation by animateFloatAsState(
                            targetValue = if (albumsExpanded) 180f else 0f,
                            // 与封面展开同一套弹簧：箭头翻转与栏宽、封面生长同步收尾
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "railChevron"
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.rotate(rotation).size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 扫描进度
                AnimatedVisibility(visible = running) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                // 相册列表：贴近屏幕最左侧纵向铺开，展开时向右生长（左缘不动）
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // 与上面几行同为 12dp 页面内缩（不再贴屏幕左右边缘）
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(albums, key = { it.bucketId }) { album ->
                        AlbumChip(
                            album = album,
                            selected = album.bucketId == bucketId,
                            expanded = albumsExpanded,
                            onClick = { switchAlbum(album.bucketId) },
                            horizontal = true
                        )
                    }
                }
            }
        }

        // ── 内容区（竖屏为整屏）──
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(if (isLandscape) Modifier.statusBarsPadding() else Modifier)
        ) {
            // ── 缩略图网格（3 列；相册切换横向滚动动画，方向反转项 4）──
            // 网格占满内容区（从屏幕顶开始），向上滚动时从顶栏下方穿过；它同时是
            // 玻璃采样层：顶栏的玻璃件与右下角导入按钮都采样真实缩略图。
            // 必须用**专用** backdrop，与根节点的渐变采样层分开，否则两处同时向
            // 同一 GraphicsLayer 录制会互相覆盖。
            // clipToBounds 只兜网格与横屏搜索浮层（相册切换的横向滑动画需要裁剪）；
            // 导入按钮是形变玻璃件，已移出本层（见下方）。
            Box(Modifier.fillMaxSize().clipToBounds()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(if (glassNow) Modifier.layerBackdrop(pickerBackdrop) else Modifier)
                ) {
                    // RTL：相册切换的横向滚动方向必须跟随布局方向（阿语/希伯来语下整体反号）
                    val rtlDir = if (
                        androidx.compose.ui.platform.LocalLayoutDirection.current ==
                        androidx.compose.ui.unit.LayoutDirection.Rtl
                    ) -1 else 1
                    AnimatedContent(
                        targetState = bucketId,
                        transitionSpec = {
                            if (isLandscape) {
                                // 横屏：相册切换改为上下切换（新内容自下/上进入）
                                if (slideDir >= 0) {
                                    (slideInVertically(tween(320, easing = FancyEasing)) { it } +
                                            fadeIn(tween(240, easing = AlphaEasing)))
                                        .togetherWith(
                                            slideOutVertically(tween(320, easing = FancyEasing)) { -it } +
                                                    fadeOut(tween(240, easing = AlphaEasing)))
                                } else {
                                    (slideInVertically(tween(320, easing = FancyEasing)) { -it } +
                                            fadeIn(tween(240, easing = AlphaEasing)))
                                        .togetherWith(
                                            slideOutVertically(tween(320, easing = FancyEasing)) { it } +
                                                    fadeOut(tween(240, easing = AlphaEasing)))
                                }
                            } else if (slideDir >= 0) {
                                // 切到右侧相册：新内容自右侧进入、旧内容向左滑出（画面向左滚动）；RTL 下反号
                                (slideInHorizontally(tween(320, easing = FancyEasing)) { it * rtlDir } +
                                        fadeIn(tween(240, easing = AlphaEasing)))
                                    .togetherWith(
                                        slideOutHorizontally(tween(320, easing = FancyEasing)) { -it * rtlDir } +
                                                fadeOut(tween(240, easing = AlphaEasing)))
                            } else {
                                (slideInHorizontally(tween(320, easing = FancyEasing)) { -it * rtlDir } +
                                        fadeIn(tween(240, easing = AlphaEasing)))
                                    .togetherWith(
                                        slideOutHorizontally(tween(320, easing = FancyEasing)) { it * rtlDir } +
                                                fadeOut(tween(240, easing = AlphaEasing)))
                            }
                        },
                        label = "albumSwitch"
                    ) { targetBucket ->
                        AlbumGridPage(
                            bucketId = targetBucket,
                            scanner = scanner,
                            isSelected = { isSelected(it) },
                            selIndexOf = { selIndexOf(it) },
                            selectable = isSelectable,
                            onToggle = toggleItem,
                            onToggleGroup = toggleGroup,
                            mediaFilter = mediaFilter,
                            sortField = sortField,
                            sortAscending = sortAscending,
                            searchQuery = searchQuery,
                            // 横屏没有顶栏覆盖层，chromeHeightPx 是竖屏组合留下的**陈旧值**
                            // （onSizeChanged 挂在竖屏内容列上，横屏该节点不组合、不更新），
                            // 拿它做顶部内缩会在网格顶压出一大块空白（用户反馈「可滚动区域
                            // 上面有块白色区域」）。横屏只需给状态栏让位（内容区已
                            // statusBarsPadding），顶部内缩归到最小 4dp。
                            contentTopPadding = if (isLandscape) 4.dp else chromeHeightDp + 4.dp,
                            landscape = isLandscape
                        )
                    }

                    // ── 底部填充背景：与设置页一致的「浅色渐白 / 深色渐黑」渐变 ──
                    // 这里原来是 0.72/0.85 alpha 的黑/白渐罩：底部照片始终半透、发灰，
                    // 和设置页那种干净的纯色底不一致（用户反馈「可滚动区域底部填充的
                    // 背景要和设置一样的白色/黑色渐变，按深浅色模式」）。现在渐隐到与
                    // GlassRoot 采样源同源的目标色（深色 #0B0B0D、浅色纯白），
                    // 最底部为完全不透明底色；MD3 模式下改用主题背景色。
                    // **必须在采样层之内**：导入按钮的自适应色采样的是「玻璃折射到的背景」，
                    // 渐罩画在采样层之外时按钮采样不到提亮后的底色，字体色会误判
                    // （用户反馈「导入按钮自动改变字体颜色不正常」）；同时渐罩本身也是
                    // 「下层内容」的一部分，玻璃模糊理应包含它。
                    val bottomFill = if (glassNow)
                        animatedThemeColor(Color(0xFF0B0B0D), Color.White)
                    else MaterialTheme.colorScheme.background
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        bottomFill.copy(alpha = 0f),
                                        bottomFill.copy(alpha = 0.82f),
                                        bottomFill
                                    )
                                )
                            )
                    )
                }

                // ── 横屏搜索：横屏没有顶栏，搜索展开后浮在网格顶部（竖屏由顶栏承载）──
                if (isLandscape) {
                    CompositionLocalProvider(
                        LocalGlassBackdrop provides (if (glassNow) pickerBackdrop else LocalGlassBackdrop.current)
                    ) {
                        LandscapeSearchOverlay(
                            visible = searchOpen,
                            query = searchQuery,
                            onQuery = { searchQuery = it },
                            onClose = { searchOpen = false; searchQuery = "" },
                            anchor = { searchAnchor },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }

            // ── 横屏：搜索 / 更多按钮组固定在**屏幕右上角**，搜索在左、更多在右 ──
            // 横屏没有顶栏，这组按钮此前散在左侧相册栏（更多在行 1 右端、搜索在行 3），
            // 用户要求统一放屏幕右上角。搜索展开动画不变：LandscapeSearchOverlay 直接
            // 覆盖整个顶部，此时本按钮组淡出让位（否则会浮在搜索框上）。
            if (isLandscape) {
                // 搜索打开时淡出让位。这里不能用 AnimatedVisibility：本处在
                // 屏幕 Row(RowScope) 与内容 Box(BoxScope) 的双重作用域下，
                // 重载解析会选中 RowScope 版本并因隐式接收者报编译错误，
                // 改用显式 alpha 动画实现同样的淡入淡出。
                val topButtonsAlpha by animateFloatAsState(
                    targetValue = if (searchOpen) 0f else 1f,
                    animationSpec = tween(if (searchOpen) 100 else 140),
                    label = "landscapeTopButtonsFade"
                )
                if (topButtonsAlpha > 0.01f) CompositionLocalProvider(
                    LocalGlassBackdrop provides (if (glassNow) pickerBackdrop else LocalGlassBackdrop.current)
                ) {
                    // 内容区已 statusBarsPadding（横屏），这里只需对齐右上角 12dp/2dp
                    // ——与竖屏顶栏按钮组同一套边距，更多菜单锚点也与此对齐。
                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .graphicsLayer { alpha = topButtonsAlpha }
                            .padding(top = 2.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                            // 搜索（左）：点击后整组淡出，搜索框从本钮的触发圆长成整行
                            GlassIconButton(
                                onClick = { haptic.click(); searchOpen = true },
                                modifier = Modifier.onGloballyPositioned {
                                    searchAnchor = it.boundsInRoot()
                                },
                                contentDescription = stringResource(R.string.screen_search),
                                // 浮在照片上：图标按背景亮度自动切黑白（与竖屏一致）
                                adaptiveContent = true
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            // 更多（右）：与竖屏同一套菜单
                            GlassIconButton(
                                onClick = { haptic.click(); morePage = 0; moreMenuOpen = true },
                                contentDescription = stringResource(R.string.screen_more),
                                adaptiveContent = true
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                    }
                }
            }

            // ── 导入按钮：永远固定在右下角 ──
            // **必须在裁剪层之外**：按钮带拖拽形变 + 按压缩放（physics.transform），
            // 此前的 clipToBounds 会把形变后的玻璃/文字切掉一截
            // （用户反馈「拖拽或点击时内容被裁切」）；采样仍走 pickerBackdrop
            // （上面的真实照片），移出裁剪层不影响采样。
            CompositionLocalProvider(
                LocalGlassBackdrop provides (if (glassNow) pickerBackdrop else LocalGlassBackdrop.current)
            ) {
                ConfirmImportButton(
                    canConfirm = canConfirm,
                    liveCount = liveCount,
                    imageCount = imageCount,
                    videoCount = videoCount,
                    feedback = importFb,
                    onClick = doConfirm,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(16.dp)
                )
            }

            // ── 顶栏覆盖层：标题 / 相册条 / 搜索 / 工具行浮在滚动内容之上 ──
            // 背后是自屏幕顶端向中间减淡的纯色渐变（深色模式黑 / 浅色模式白），
            // 用于区分常驻顶栏与向下滚动的照片列表（替代此前的渐进高斯模糊）。
            // 顶栏覆盖层内的玻璃件（模糊 / 圆钮 / 搜索框）统一采样网格层
            CompositionLocalProvider(
                LocalGlassBackdrop provides (if (glassNow) pickerBackdrop else LocalGlassBackdrop.current)
            ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (glassNow) Modifier
                        else Modifier
                            // MD3 顶栏背景：同样半透明（50%）+ 底部透明度渐隐过渡（与玻璃带一致）
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val fadeH = 32.dp.toPx()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0f to Color.Black, 1f to Color.Transparent,
                                        startY = size.height - fadeH, endY = size.height
                                    ),
                                    topLeft = Offset(0f, size.height - fadeH),
                                    // 本文件已 import android.util.Size（缩略图尺寸），
                                    // 这里必须写全限定名避免同名冲突
                                    size = androidx.compose.ui.geometry.Size(size.width, fadeH),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.50f))
                    )
            ) {
            if (!isLandscape) {
                // ── 竖屏顶栏模糊区：标题行 + 相册栏 + 进度条（模糊/薄纱仅作用于此，搜索与工具行不模糊）──
                // 模糊覆盖到状态栏（不 pad 状态栏），内容自身再向下让出状态栏高度
                // 玻璃带高度 = 收起态顶栏内容实测高（见 veilHeight）：相册展开只会让
                // chip 浮层向下长，玻璃带不跟着变长；下沿与内容底边齐平，
                // 日期分组 stickyHeader 钉在其下方 4dp 处，不被玻璃遮挡
                Box(Modifier.fillMaxWidth()) {
                    val dark = LocalDarkTheme.current
                    // 顶栏玻璃的内容自适应色：采样玻璃折射到的照片亮度，标题/控件自动切黑白
                    val topAdaptive = rememberAdaptiveContentColor(
                        initialColor = if (dark) Color.White else Color.Black,
                        enabled = glassNow
                    )
                    if (glassNow) {
                        // 顶栏改用与设置页同款的 [TopBarFrost]：固定轻磨砂 + 70% 黑/白
                        // 有色层，并在下缘平滑渐隐。此前的「50% 同色 tint 玻璃条」
                        // 叠在深色页面底上时等于什么都没叠，观感是一整块纯黑硬边
                        // （用户反馈「固定顶栏还是全黑的，参考设置页面制作」）。
                        val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                        TopBarFrost(
                            modifier = Modifier.fillMaxWidth(),
                            barHeight = (veilHeight - statusInset).coerceAtLeast(0.dp)
                        ) {}
                    }
                    CompositionLocalProvider(
                        LocalContentColor provides
                            if (glassNow) topAdaptive.color
                            else MaterialTheme.colorScheme.onSurface
                    ) {
                    // 3.7.1 版式：顶部固定玻璃栏（标题行 + 相册横向列表 + 进度条），
                    // 相册在这里原地展开，不再有额外的下拉面板
                    Column(
                        Modifier
                            .fillMaxWidth()
                            // onSizeChanged 必须放在 statusBarsPadding **之前**（外层）：
                            // 顶栏玻璃带与网格顶部让位都从屏幕顶(y=0)算起，测得的顶栏
                            // 总高必须**含状态栏**。放在内层时测的是纯内容高度、漏掉
                            // 状态栏那一截，网格顶部内缩偏小，照片顶端会被相册栏压住
                            // 一截（用户反馈「可滚动区域的顶端应该再往下移一点，
                            // 保证不要被相册组件遮挡」）。
                            // 仍只挂内容列、不挂玻璃所在父节点：玻璃带高度由本测得值
                            // 反推，挂父节点会互相抬高导致布局无限增长。
                            .onSizeChanged {
                                chromeHeightPx = it.height
                                if (!albumsExpanded) collapsedChromePx = it.height
                            }
                            .statusBarsPadding()
                    ) {
                // 标题行：收起态 = 返回 + 标题 + 搜索 + 更多；展开态由**同一条形变进度**
                // 驱动的搜索框接管——复刻 liquid_glass_widgets 菜单的 morph：
                // 从搜索钮的触发圆（40dp）长成整行、圆角 easeInExpo、玻璃模糊 0→基准、
                // 内容在形变 25%..70% 淡入并 0.7→1 放大；收起沿同一路径弹回触发圆。
                Box(Modifier.fillMaxWidth()) {
                    // 收起态标题行：随形变进度快速淡出（形变过半即不可见）
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp)
                            .graphicsLayer {
                                alpha = (1f - searchMorph.value * 2f).coerceIn(0f, 1f)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BackButton(onBack)
                        Text(stringResource(R.string.screen_picker_title_select),
                            style = MaterialTheme.typography.titleMedium,
                            // 玻璃模式下跟随顶栏玻璃的自适应色（按照片亮度切黑白），
                            // MD3 模式用主题 onSurface
                            color = LocalContentColor.current,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 8.dp))
                        // 搜索（玻璃圆钮）：点击后从本钮长成整行搜索框；
                        // 同时回填根坐标作为形变的触发圆锚点
                        GlassIconButton(
                            onClick = { haptic.click(); searchOpen = true },
                            modifier = Modifier.onGloballyPositioned {
                                searchAnchor = it.boundsInRoot()
                            },
                            contentDescription = stringResource(R.string.screen_search),
                            // 内容自适应着色：浮在照片上，图标按背景亮暗自动切黑/白
                            adaptiveContent = true
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        // 右上角「更多」：输出格式 / 排序字段 / 排序顺序 / 显示类别（二级菜单）
                        GlassIconButton(
                            onClick = { haptic.click(); morePage = 0; moreMenuOpen = true },
                            contentDescription = stringResource(R.string.screen_more),
                            adaptiveContent = true
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // 搜索展开期间截断标题行的按钮点击（搜索框本身在上层，不受影响）
                    if (searchOpen) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { }
                        )
                    }
                    // 展开态：绘制期按形变进度裁剪（不触发重组）；关闭动画结束即退出组合
                    if (searchOpen || searchMorph.isRunning) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .liquidMorphClip(
                                    progress = { searchMorph.value },
                                    anchor = { searchAnchor },
                                    cornerRadius = 20.dp
                                )
                                .graphicsLayer {
                                    val p = searchMorph.value
                                    alpha = ((p - 0.25f) / 0.45f).coerceIn(0f, 1f)
                                    val t = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
                                    val s = 0.7f + 0.3f * t
                                    scaleX = s
                                    scaleY = s
                                    transformOrigin = TransformOrigin(1f, 0.5f)
                                }
                        ) {
                            SearchInputRow(
                                query = searchQuery,
                                onQuery = { searchQuery = it },
                                onClose = { searchOpen = false; searchQuery = "" },
                                morph = { searchMorph.value }
                            )
                        }
                    }
                }

                // 相册 chip 横滑条 + 右端展开按钮
                if (albums.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LazyRow(
                            Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp),
                            contentPadding = PaddingValues(start = 12.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(albums, key = { it.bucketId }) { album ->
                                AlbumChip(
                                    album = album,
                                    selected = album.bucketId == bucketId,
                                    // 3.7.1 版式：相册条就在顶部固定栏内，展开时 chip 原地长高
                                    expanded = albumsExpanded,
                                    onClick = { switchAlbum(album.bucketId) },
                                    // 顶栏玻璃的自适应黑白（玻璃模式下；MD3 下为 onSurface，不会被用）
                                    contentColor = LocalContentColor.current
                                )
                            }
                        }
                        // 展开/收起封面（玻璃圆钮）；右缘与标题行「更多」按钮对齐（12dp）
                        GlassIconButton(
                            onClick = { haptic.click(); albumsExpanded = !albumsExpanded },
                            modifier = Modifier.padding(end = 12.dp),
                            contentDescription = if (albumsExpanded) stringResource(R.string.screen_album_collapse_cover) else stringResource(R.string.screen_album_expand_cover),
                            adaptiveContent = true
                        ) {
                            val rotation by animateFloatAsState(
                                targetValue = if (albumsExpanded) 180f else 0f,
                                animationSpec = tween(250), label = "chevron"
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.rotate(rotation).size(20.dp)
                            )
                        }
                    }
                }

                // 扫描进度条
                AnimatedVisibility(visible = running) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                    } // 顶栏模糊区 Column（固定栏）
                } // 顶栏模糊区 Box
                // 玻璃模式下顶栏由模糊衰减自然收边，不再画硬分隔线
                if (!glassNow) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
                } // 顶栏 Column
                    } // CompositionLocalProvider（顶栏内容自适应黑白）
            } // 顶栏覆盖层 Box
            } // CompositionLocalProvider
        }
    }

    // 合成模式提示弹窗（统一「不再提示」机制）：渲染在**整屏根 Box**内、
    // 自带网格采样作用域——弹窗与页面同窗口，采样坐标天然对正、模糊实时
    // （用户反馈 #5/#6）；横竖屏都覆盖全屏；visible 模式带退场动画
    CompositionLocalProvider(
        LocalGlassBackdrop provides (if (glassNow) pickerBackdrop else LocalGlassBackdrop.current)
    ) {
        ReminderInfoDialog(
            key = ReminderKey.COMPOSE_VIDEO_OVER_3S,
            onDismiss = { showOver3sWarning = false },
            visible = showOver3sWarning
        )
    }

    // 右上角「更多」菜单（同一 Composition 树内的玻璃浮层）：
    // 一级 = 输出格式 / 排序字段 / 排序顺序 / 显示类别，各入口进入二级子菜单；
    // 二级顶部有「‹ 标题」行返回一级。选择即应用并收起整个菜单。
    val sortFieldLabel = stringResource(
        when (sortField) {
            AlbumSortField.DATE -> R.string.screen_sort_date
            AlbumSortField.SIZE -> R.string.screen_sort_size
            AlbumSortField.NAME -> R.string.screen_sort_name
        }
    )
    val sortOrderLabel = stringResource(
        if (sortAscending) R.string.screen_sort_asc else R.string.screen_sort_desc
    )
    val filterLabel = stringResource(mediaFilterLabelRes(mediaFilter))
    val currentFormatLabel = formatOptions
        .firstOrNull { it.key == outputFormat }
        ?.let { stringResource(it.nameRes) }
        ?: outputFormat
    // 菜单是玻璃件：采样网格层（真实照片）而不是根渐变，展开形变时折射的才是背后内容。
    // 二级子菜单形变起点：各父项的根坐标矩形（onAnchor 持续回填，点击时取最新值）
    var formatAnchor by remember { mutableStateOf(Rect.Zero) }
    var sortFieldAnchor by remember { mutableStateOf(Rect.Zero) }
    var sortOrderAnchor by remember { mutableStateOf(Rect.Zero) }
    var filterAnchor by remember { mutableStateOf(Rect.Zero) }
    CompositionLocalProvider(
        LocalGlassBackdrop provides (if (glassNow) pickerBackdrop else LocalGlassBackdrop.current)
    ) {
    GlassMenuOverlay(
        visible = moreMenuOpen,
        onDismiss = { moreMenuOpen = false; morePage = 0 },
        // 横竖屏统一右上锚定：横屏「更多」按钮已挪到屏幕右上角（内容区右上覆盖层，
        // 内容区 statusBarsPadding 后按钮顶 = 状态栏 + 2dp、右 = 12dp），与竖屏同一套坐标。
        alignment = Alignment.TopEnd,
        modifier = Modifier
            .statusBarsPadding()
            .padding(
                // 菜单右上角直接对齐「更多」按钮（按钮右上角 = 距右 12dp、顶 2dp），
                // 入场形变从该角的 40dp 圆（按钮轮廓）溶解长为菜单外轮廓。
                top = 2.dp,
                start = 0.dp,
                end = 12.dp,
                bottom = 8.dp
            )
            .widthIn(min = 196.dp, max = 252.dp),
        // 二级子菜单（用户参考图：日区相册「更多」菜单两态）：
        // 一级整体缩小 + 压暗，二级以玻璃面板**叠加**在一级之上，从所点击
        // 父项的按钮框平滑展开到目标尺寸后再渲染内容；选择选项后只置
        // morePage = 0——仅收起二级、保留一级菜单（用户明确要求）。
        // morePage：0 = 全部收起；1..4 = 对应分组展开。
        submenuVisible = morePage != 0,
        submenuAnchor = when (morePage) {
            1 -> formatAnchor
            2 -> sortFieldAnchor
            3 -> sortOrderAnchor
            4 -> filterAnchor
            else -> null
        },
        onSubmenuDismiss = { morePage = 0 },
        // 子卡顶部重画所点父行（FlClash `_buildContent` 的 covered header）：
        // 标题 + 当前值，与一级卡片里被盖住的那一行逐像素一致。
        submenuTitle = when (morePage) {
            1 -> stringResource(R.string.screen_menu_output_format)
            2 -> stringResource(R.string.screen_menu_sort_field)
            3 -> stringResource(R.string.screen_menu_sort_order)
            4 -> stringResource(R.string.screen_menu_media_filter)
            else -> null
        },
        submenuValue = when (morePage) {
            1 -> currentFormatLabel
            2 -> sortFieldLabel
            3 -> sortOrderLabel
            4 -> filterLabel
            else -> null
        },
        submenuContent = {
            when (morePage) {
                // ① 输出格式
                1 -> formatOptions.forEach { opt ->
                    GlassMenuRow(
                        label = stringResource(opt.nameRes),
                        selected = opt.key == outputFormat,
                        onClick = {
                            onOutputFormatChange(opt.key)
                            morePage = 0
                        }
                    )
                }
                // ② 排序字段
                2 -> AlbumSortField.values().forEach { field ->
                    GlassMenuRow(
                        label = stringResource(
                            when (field) {
                                AlbumSortField.DATE -> R.string.screen_sort_date
                                AlbumSortField.SIZE -> R.string.screen_sort_size
                                AlbumSortField.NAME -> R.string.screen_sort_name
                            }
                        ),
                        selected = field == sortField,
                        onClick = { sortField = field; morePage = 0 }
                    )
                }
                // ③ 排序顺序
                3 -> listOf(true to R.string.screen_sort_asc, false to R.string.screen_sort_desc)
                    .forEach { (asc, res) ->
                        GlassMenuRow(
                            label = stringResource(res),
                            selected = asc == sortAscending,
                            onClick = { sortAscending = asc; morePage = 0 }
                        )
                    }
                // ④ 显示类别
                4 -> MediaFilter.values().forEach { filter ->
                    GlassMenuRow(
                        label = stringResource(mediaFilterLabelRes(filter)),
                        selected = filter == mediaFilter,
                        onClick = { mediaFilter = filter; morePage = 0 }
                    )
                }
                else -> {}
            }
        }
    ) {
        // 一级菜单：4 个父项——标签 + 当前值副标题 + 右侧「>」箭头。
        // 三路状态机（用户反馈）：①点已展开的父项（展开位置）= 保持现有选项
        // 不变、播放逆动画退回一级；②点非展开父项 = 整体收起菜单；
        // ③二级展开期间父项保持可点（不再置灰），锚点矩形持续回填供形变使用。
        GlassMenuEntry(
            label = stringResource(R.string.screen_menu_output_format),
            value = currentFormatLabel,
            onClick = {
                when {
                    morePage == 0 -> morePage = 1
                    morePage == 1 -> morePage = 0
                    else -> { moreMenuOpen = false; morePage = 0 }
                }
            },
            expanded = morePage == 1,
            onAnchor = { formatAnchor = it }
        )
        GlassMenuEntry(
            label = stringResource(R.string.screen_menu_sort_field),
            value = sortFieldLabel,
            onClick = {
                when {
                    morePage == 0 -> morePage = 2
                    morePage == 2 -> morePage = 0
                    else -> { moreMenuOpen = false; morePage = 0 }
                }
            },
            expanded = morePage == 2,
            onAnchor = { sortFieldAnchor = it }
        )
        GlassMenuEntry(
            label = stringResource(R.string.screen_menu_sort_order),
            value = sortOrderLabel,
            onClick = {
                when {
                    morePage == 0 -> morePage = 3
                    morePage == 3 -> morePage = 0
                    else -> { moreMenuOpen = false; morePage = 0 }
                }
            },
            expanded = morePage == 3,
            onAnchor = { sortOrderAnchor = it }
        )
        GlassMenuEntry(
            label = stringResource(R.string.screen_menu_media_filter),
            value = filterLabel,
            onClick = {
                when {
                    morePage == 0 -> morePage = 4
                    morePage == 4 -> morePage = 0
                    else -> { moreMenuOpen = false; morePage = 0 }
                }
            },
            expanded = morePage == 4,
            onAnchor = { filterAnchor = it }
        )
    }
    } // CompositionLocalProvider（菜单采样网格层）
    } // Box（菜单浮层宿主）
    } // key(isDarkTheme)
}

/**
 * 单个相册的网格页：应用排序（大小/日期/名称 + 正倒序）与名称搜索过滤。
 * 仅日期排序时按日期分组 + 粘性标题；大小/名称排序为扁平列表（日期分组无意义）。
 * 必须先排序取快照再 groupBy 归组逐组发射（扫描期间流式追加未排序，
 * 直接遍历发射会导致 stickyHeader 重复 key 崩溃）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGridPage(
    bucketId: Long?,
    scanner: AlbumScanner,
    isSelected: (MediaItem) -> Boolean,
    selIndexOf: (MediaItem) -> Int,
    selectable: (MediaItem) -> Boolean,
    onToggle: (MediaItem) -> Unit,
    onToggleGroup: (List<MediaItem>) -> Unit,
    mediaFilter: MediaFilter = MediaFilter.ALL,
    sortField: AlbumSortField = AlbumSortField.DATE,
    sortAscending: Boolean = false,
    searchQuery: String = "",
    // 顶栏覆盖层的高度：网格从屏幕顶开始，首屏内容要落在顶栏下方（滚动后穿过顶栏）
    contentTopPadding: Dp = 0.dp,
    // 横竖屏（决定列数）：由调用方按根容器实测尺寸判定，本函数不再读 LocalConfiguration
    landscape: Boolean = false
) {
    val state = remember(bucketId) { if (bucketId != null) scanner.stateOf(bucketId) else null }
    val results = state?.results
    val pageProgress by produceState(0 to false, state) {
        while (true) {
            value = (state?.doneCount?.get() ?: 0) to (state?.running ?: false)
            delay(200)
        }
    }
    val running = pageProgress.second

    val gridState = rememberLazyGridState()
    // 底部导航栏留白：横屏/竖屏最后一行照片都不能被导航栏压住
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 切换排序字段/正倒序时回到网格顶部，避免停留在中部导致内容跳变（与日期排序一致）
    LaunchedEffect(sortField, sortAscending) {
        gridState.scrollToItem(0)
    }

    when {
        results == null -> Box(
            Modifier.fillMaxSize().padding(top = contentTopPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.screen_no_albums), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        results.isEmpty() && !running -> Column(
            Modifier.fillMaxSize().padding(top = contentTopPadding, bottom = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.ImageNotSupported,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.screen_no_media_compose),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> LazyVerticalGrid(
            state = gridState,
            // 竖屏 3 列；横屏固定 5 列（用户要求每行五张照片）
            columns = GridCells.Fixed(if (landscape) 5 else 3),
            modifier = Modifier
                .fillMaxSize()
                // 顶部让位改用 **contentPadding**（只把首屏内容推到相册栏下沿，
                // 滚动时照片会从半透明顶栏后面滑过）：顶栏不透明度 50%、底缘 32dp
                // 渐隐过渡，需要照片真的透到栏后面才能看到「半透明 + 渐隐」，
                // 布局内缩（padding）会把网格视口压在栏下、栏杆背后永远是空底色，
                // 看上去就是一整块纯黑且与照片硬切（用户反馈「还是纯黑加明显边缘」）。
                // 左右内缩 12dp：与顶栏/横屏相册栏的页面内缩统一
                .padding(horizontal = 12.dp),
            // 底部留出渐变填充带的高度：最底下一排照片滚到渐变带上方就停住，
            // 不会被底部的白/黑渐变盖住（用户要求底部是「填充背景」而不是遮挡照片）。
            contentPadding = PaddingValues(
                top = contentTopPadding,
                bottom = navBarBottom + 8.dp + 112.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val sorted = sortMediaItems(
                applyMediaFilter(filterMediaItems(results.orEmpty(), searchQuery), mediaFilter),
                sortField, sortAscending
            )
            if (sorted.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.screen_filter_empty),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (sortField == AlbumSortField.DATE) {
                val groups = sorted.groupBy { dateKey(it.sortTime) }
                for ((dk, groupItems) in groups) {
                    stickyHeader(key = "hdr_$dk") {
                        // 日期栏的位置与移动完全交给 stickyHeader 自身（与 3.7.1 一致）：
                        // 此前在 offset 里读 gridState.layoutInfo 再补偿一次顶栏高度，
                        // 会让钉住的日期栏被推下两倍内缩、跟随滚动抖动——日期栏的
                        // 显示位置与黏附逻辑不该被背景板改动牵连（用户反馈）。
                        Box(Modifier.fillMaxWidth()) {
                            // 组内统计与整组切换只看可选项（拆解模式下动态照片不可选）
                            val choosable = groupItems.filter { selectable(it) }
                            val allSel = choosable.isNotEmpty() && choosable.all { isSelected(it) }
                            val someSel = choosable.any { isSelected(it) }
                            DateHeader(
                                label = dateLabel(groupItems.first().sortTime),
                                checkState = when {
                                    allSel -> ToggleableState.On
                                    someSel -> ToggleableState.Indeterminate
                                    else -> ToggleableState.Off
                                },
                                onToggleAll = { onToggleGroup(choosable) },
                                enabled = choosable.isNotEmpty()
                            )
                        }
                    }
                    items(groupItems, key = { it.key }) { item ->
                        // 日期排序不启用 animateItem 重排动画：切换正/倒序时日期分组整体翻转，
                        // 若叠加逐项位移动画会与 stickyHeader 重排竞争，导致图片位置乱飘
                        GridCell(
                            item = item,
                            selected = isSelected(item),
                            selectionIndex = selIndexOf(item),
                            enabled = selectable(item),
                            onToggle = { onToggle(item) },
                            durationText = if (item.isVideo)
                                formatDurationLabel(item.durationMs) else null
                        )
                    }
                }
            } else {
                items(sorted, key = { it.key }) { item ->
                    Box(Modifier.animateItem()) {
                        GridCell(
                            item = item,
                            selected = isSelected(item),
                            selectionIndex = selIndexOf(item),
                            enabled = selectable(item),
                            onToggle = { onToggle(item) },
                            durationText = if (item.isVideo)
                                formatDurationLabel(item.durationMs) else null,
                            sizeText = if (sortField == AlbumSortField.SIZE)
                                formatSizeLabel(item.size) else null
                        )
                    }
                }
            }
            // 扫描中：网格底部加载圈
            if (running) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
            }
        }
    }
}

/**
 * 横屏顶部搜索浮层：横屏没有常驻顶栏，搜索按钮展开后覆盖在网格顶部，
 * 背后叠同款纯色渐变（自顶端向中间减淡，深浅色分别用黑/白）。
 *
 * 展开/收起与竖屏搜索框共用同一套 morph（复刻 liquid_glass_widgets 菜单）：
 * 从搜索钮的触发圆长成整行、玻璃模糊 0→基准、内容随形变淡入；收起沿原路径弹回。
 */
@Composable
private fun LandscapeSearchOverlay(
    visible: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    anchor: () -> Rect,
    modifier: Modifier = Modifier
) {
    val morph = rememberLiquidMorphProgress(visible)
    // 收起动画播完才退出绘制（复用 remember 状态，下次展开从触发圆重新生长）
    if (!visible && !morph.isRunning) return
    Box(modifier.fillMaxWidth()) {
        // 横屏搜索栏背后的渐罩：颜色/浓度跟随主题 500ms 渐变（与竖屏底部渐罩一致），
        // 透明度随形变进度建立/退场
        val scrim = animatedThemeColor(Color.Black, Color.White)
        val scrimAlpha by animateFloatAsState(
            targetValue = if (LocalDarkTheme.current) 0.72f else 0.85f,
            animationSpec = tween(durationMillis = 500),
            label = "landscapeSearchScrimAlpha"
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = morph.value.coerceIn(0f, 1f) }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            scrim.copy(alpha = scrimAlpha),
                            scrim.copy(alpha = 0f)
                        )
                    )
                )
        )
        Box(
            Modifier
                .liquidMorphClip(
                    progress = { morph.value },
                    anchor = anchor,
                    cornerRadius = 20.dp
                )
                .graphicsLayer {
                    val p = morph.value
                    alpha = ((p - 0.25f) / 0.45f).coerceIn(0f, 1f)
                    val t = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    val s = 0.7f + 0.3f * t
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(1f, 0.5f)
                }
        ) {
            SearchInputRow(
                query = query,
                onQuery = onQuery,
                onClose = onClose,
                morph = { morph.value }
            )
        }
    }
}

/**
 * 标题行内的整宽搜索输入框（展开态）：
 * 左端返回箭头收起、中间玻璃输入区（关键字高亮）、右端清空按钮。
 * 用于替换旧的「搜索胶囊」，搜索只保留顶部这一处。
 */
@Composable
private fun SearchInputRow(
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    // 形变进度（0..1）：玻璃模糊随同一进度从 0 建立到基准值（复刻库菜单的 morph）；
    // 不做形变的调用方保持默认 1（模糊恒定）
    morph: () -> Float = { 1f }
) {
    val haptic = com.zsz.zlivephoto.ui.rememberHapticFeedback()
    val glass = LocalGlassEnabled.current
    val pill = RoundedCornerShape(20.dp)
    val tint by animateFloatAsState(
        targetValue = if (query.isNotEmpty()) 0.24f else 0.05f,
        animationSpec = tween(200),
        label = "searchTint"
    )
    Row(
        // 右端留 12dp：搜索框此前几乎贴住屏幕右缘（用户反馈）
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 与「选择动态照片」标题行的返回按钮完全同一组件（含 8dp 起始内缩），
        // 展开/收起时两者位置严格重合
        GlassBackButton(
            onBack = { haptic.click(); onClose() },
            contentDescription = stringResource(R.string.screen_search_collapse)
        )
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .weight(1f)
                .height(40.dp)
                .then(
                    if (glass) Modifier.liquidGlass(
                        shape = pill,
                        tintAlpha = tint,
                        tintColor = MaterialTheme.colorScheme.primary,
                        lensHeight = 8.dp,
                        lensAmount = 16.dp,
                        // 模糊动画：形变期间玻璃模糊 0 → 基准值
                        blurScale = morph
                    ) else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, pill)
                )
                .clip(pill),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(14.dp))
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                maxLines = 1,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.screen_search_hint),
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.click()
                            onQuery("")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.screen_search_clear),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}

/**
 * 相册 chip（ImageToolbox 式）：展开态在文字下方垂直显示封面缩略图（按需加载）。
 * - 选中：secondaryContainer；未选中：surfaceContainerHigh
 * - 圆角曲率动画（收起 12dp ↔ 展开 16dp）直接作用于 Surface 的 shape，
 *   阴影/涟漪叠加层与按钮共用同一 shape，圆角动画全程实时匹配
 */
@Composable
private fun AlbumChip(
    album: AlbumInfo,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    horizontal: Boolean = false,
    // 外部尺寸（下拉面板里撑满整行）
    modifier: Modifier = Modifier,
    // 玻璃模式下由调用方传入内容色（竖屏顶栏传顶栏玻璃的自适应黑白；
    // 横屏栏不传，回落主题色）。null = 使用主题默认配色。
    contentColor: Color? = null
) {
    val resolver = LocalContext.current.contentResolver
    val density = LocalDensity.current
    var textWidth by remember { mutableStateOf(100.dp) }
    val interactionSource = remember { MutableInteractionSource() }
    PressHapticEffect(interactionSource)
    val corner by androidx.compose.animation.core.animateDpAsState(
        // 圆角与整体玻璃风格对齐（此前 12/16dp 显得过小、像硬角小方块）
        targetValue = if (expanded) 22.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chipCorner"
    )

    val glass = LocalGlassEnabled.current
    // 演示版同款按钮物理：触点径向高光 + tanh 阻尼拖拽 + 方向形变（松手弹簧回弹）。
    // 相册 chip 此前只有一层静态玻璃——没有按压力高光、也没有滑动时的方向
    // 变形拖拽物理（用户反馈「每个相册的按钮还不完全是液态玻璃」）。
    // 手势用 inspectDragGestures（不消费事件）：chip 身在 LazyRow / LazyColumn 内，
    // 列表滚动与 chip 形变互不打断——左右/上下滑动列表时，指下的 chip
    // 沿滑动方向被「拽长」，松手弹簧回弹。
    val physics = rememberGlassButtonPhysics()
    val fallbackColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
    // 点击改挂 modifier 并显式 indication = null：Surface(onClick=) 自带 MD3
    // 涟漪，与液态玻璃语言不符（用户反馈「去除 MD3 点击涟漪反馈」）；
    // interactionSource 沿用原实例，PressHapticEffect 的按压触感不受影响
    Surface(
        shape = RoundedCornerShape(corner),
        color = if (glass) Color.Transparent else fallbackColor,
        // 玻璃模式下相册 chip 的文字色：竖屏顶栏传自适应黑白（按照片亮度），
        // 其余回落主题 onSurface；MD3 模式沿用容器配色
        contentColor = when {
            glass && contentColor != null -> contentColor
            glass -> MaterialTheme.colorScheme.onSurface
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier.then(if (glass) Modifier
            .liquidGlass(
                RoundedCornerShape(corner),
                tintAlpha = if (selected) 0.20f else 0.08f,
                // 玻璃表面与文字作为整体变形（layerBlock 的 graphicsLayer 包裹全节点）
                layerBlock = physics.transform
            )
            // 触点径向高光画在玻璃之上、内容之下（链内顺序保证）
            .then(physics.highlight.modifier)
            // 拖拽/按压力采集（不消费事件，不影响外层列表滚动）
            .then(physics.highlight.gestureModifier)
        else Modifier
        ).clickable(
            interactionSource = interactionSource,
            indication = null
        ) { onClick() }
    ) {
        if (horizontal) {
            // 横向布局（选择器横屏用）：文字在左，展开时封面在右侧横向展开
            Row(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy
                        ),
                        alignment = Alignment.CenterStart
                    )
                    .padding(
                        horizontal = if (expanded) 8.dp else 12.dp,
                        vertical = if (expanded) 6.dp else 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    album.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedVisibility(
                    visible = expanded,
                    // 展开方向固定「从左往右」：封面贴住文字右缘、以自身左缘为基准向右揭开；
                    // 收起则原路向左收回。弹簧（低阻尼）替代 tween，和整体液态玻璃的
                    // 回弹手感统一，宽度变化与圆角/栏宽弹簧同步收尾。
                    enter = fadeIn(tween(180)) + expandHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        expandFrom = Alignment.Start
                    ),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        shrinkTowards = Alignment.Start
                    )
                ) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .size(width = 72.dp, height = 44.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        val coverUri = Uri.parse(
                            (if (album.coverIsVideo)
                                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            else
                                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            ).toString() + "/${album.coverId}"
                        )
                        MediaThumbnail(coverUri, resolver, Modifier.fillMaxSize())
                    }
                }
            }
        } else {
        Column(
            Modifier
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy
                    ),
                    alignment = Alignment.TopCenter
                )
                .padding(
                    horizontal = if (expanded) 8.dp else 12.dp,
                    vertical = if (expanded) 8.dp else 6.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                album.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.onSizeChanged {
                    textWidth = with(density) {
                        it.width.toDp().coerceAtLeast(100.dp)
                    }
                }
            )
            // 展开态：封面图（高度 100dp，宽度与文字对齐；按需加载走 LRU 缓存）
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
            ) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .height(100.dp)
                        .width(textWidth)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    val coverUri = Uri.parse(
                        (if (album.coverIsVideo)
                            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        ).toString() + "/${album.coverId}"
                    )
                    MediaThumbnail(coverUri, resolver, Modifier.fillMaxSize())
                }
            }
        }
        }
    }
}

// ---------- 日期分组 ----------

/**
 * 粘性日期标题：
 * - 背景：透明（用户需求：日期栏背景板改为透明，与底层背景融为一体）
 * - 文本（项 6）：月日不变时无动画；变化时数字竖向滚动（Material 滚动数字风格，
 *   160ms 一步到位）
 * - 右端圆形三态复选框（项 2）：点击选择/取消该日期下所有照片
 */
@Composable
private fun DateHeader(
    label: String,
    checkState: ToggleableState,
    onToggleAll: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                (slideInVertically(tween(160)) { it / 2 } + fadeIn(tween(160))) togetherWith
                        (slideOutVertically(tween(160)) { -it / 2 } + fadeOut(tween(160)))
            },
            label = "dateRoll",
            modifier = Modifier.weight(1f)
        ) { l ->
            Text(
                text = l,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 组内没有可选项时不显示三态复选框（避免点了没反应）
        if (enabled) CircleTriCheckbox(state = checkState, onClick = onToggleAll)
    }
}

/**
 * 圆形三态复选框（项 2 现代风格，替代方形 TriStateCheckbox）：
 * - Off：外圈描边
 * - Indeterminate：primary 横线
 * - On：primary 填充 + 白色对勾
 */
@Composable
private fun CircleTriCheckbox(
    state: ToggleableState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val borderColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fillColor = MaterialTheme.colorScheme.primary
    val checkColor = MaterialTheme.colorScheme.onPrimary
    val interactionSource = remember { MutableInteractionSource() }
    PressHapticEffect(interactionSource)
    Box(
        modifier
            .size(18.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick?.invoke() }
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(18.dp)) {
            val s = size.minDimension
            val stroke = 2.dp.toPx()
            when (state) {
                ToggleableState.On -> {
                    drawCircle(fillColor, radius = s / 2 - stroke / 2 - 1.dp.toPx())
                    drawLine(
                        color = checkColor,
                        start = Offset(s * 0.30f, s * 0.53f),
                        end = Offset(s * 0.45f, s * 0.68f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = checkColor,
                        start = Offset(s * 0.45f, s * 0.68f),
                        end = Offset(s * 0.72f, s * 0.34f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
                ToggleableState.Indeterminate -> {
                    drawLine(
                        color = fillColor,
                        start = Offset(s * 0.28f, s / 2),
                        end = Offset(s * 0.72f, s / 2),
                        strokeWidth = stroke * 1.2f,
                        cap = StrokeCap.Round
                    )
                }
                ToggleableState.Off -> {}
            }
        }
    }
}

/** 日期 key（年-月-日） */
private fun dateKey(time: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = time }
    return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
}

/**
 * 日期标签：今天 / 昨天 / x月x日（今年） / yyyy年x月x日（往年）。
 * 改为 @Composable：文案走 strings_screens.xml（其它语种可用 %1$d 占位符重排顺序）。
 */
@Composable
private fun dateLabel(time: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = time }
    val now = Calendar.getInstance()

    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(cal, now) -> stringResource(R.string.screen_date_today)
        sameDay(cal, yesterday) -> stringResource(R.string.screen_date_yesterday)
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            stringResource(
                R.string.screen_date_this_year,
                cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
        else ->
            stringResource(
                R.string.screen_date_other_year,
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
    }
}

/** 视频时长徽标文本：≥10s 取整显示，否则保留 1 位小数（如 2.5s） */
private fun formatDurationLabel(ms: Long): String {
    val s = ms / 1000.0
    return if (s >= 10.0) "${s.toInt()}s" else "${"%.1f".format(s)}s"
}

/** 文件大小徽标文本：<1024KB 显示 KB，否则显示 MB（保留 1 位小数） */
private fun formatSizeLabel(bytes: Long): String {
    if (bytes < 1024L * 1024L) {
        val kb = bytes / 1024.0
        val text = if (kb >= 100f) "%.0f".format(kb) else "%.1f".format(kb)
        return "${text}KB"
    }
    return "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
}

/**
 * 网格单元：
 * - 勾选徽章在右上角（项 2）
 * - 选中动画以几何中心为变换中心：图片中心缩放 0.86，徽章/数字以中心弹簧缩放（项 8）
 * - 圆角（项 7）：未选 6dp（较小），选中缩小并增大至 16dp
 * - 合成模式视频项：左下角时长徽标
 */
@Composable
private fun GridCell(
    item: MediaItem,
    selected: Boolean,
    selectionIndex: Int,
    onToggle: () -> Unit,
    durationText: String? = null,
    sizeText: String? = null,
    /** 是否可选：拆解模式下动态照片不可选（置灰 + 点击无效） */
    enabled: Boolean = true
) {
    val resolver = LocalContext.current.contentResolver
    val transition = updateTransition(selected, label = "cell")
    // 关涟漪后的点击源（仍保留按压语义/无障碍焦点行为）
    val cellInteraction = remember { MutableInteractionSource() }

    // 选中时以几何中心为定点缩小
    val scale by transition.animateFloat(label = "scale") { if (it) 0.86f else 1f }
    // 圆角：未选 6dp（较小）、选中 16dp（项 7）；以 0..1 分数动画映射 Dp
    val cornerFraction by transition.animateFloat(label = "cornerF") { if (it) 1f else 0f }
    val cornerRadius = lerp(6.dp, 16.dp, cornerFraction)

    // 徽章背景色：透明→primary（颜色平滑过渡）
    val badgeBg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200), label = "badgeBg"
    )
    // 徽章以几何中心为基准的弹簧缩放（项 8，替代挤压式）
    val badgeScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "badgeScale"
    )

    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .alpha(if (enabled) 1f else 0.38f)
            // 显式关掉 MD3 涟漪：选中态本身的缩放/圆角/徽章动画即点击反馈
            // （用户反馈「动态照片选择页去除 MD3 点击涟漪反馈」）
            .clickable(
                interactionSource = cellInteraction,
                indication = null,
                onClick = onToggle
            )
    ) {
        // 内层图片：中心缩放 + 圆角/边框动画
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .clip(RoundedCornerShape(cornerRadius))
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(cornerRadius)
                )
        ) {
            MediaThumbnail(item.uri, resolver, Modifier.fillMaxSize())
        }

        // 左下角徽标：合成模式视频时长 + 按大小排序时的文件大小（可叠加，时长在上、大小在下）
        if (durationText != null || sizeText != null) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (durationText != null) {
                    Text(
                        text = durationText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                if (sizeText != null) {
                    Text(
                        text = sizeText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // 动态照片角标：纯白图标、无灰底，且随图片选择缩放一起缩放（像印在图上）
        if (item.kind == MediaKind.LIVE) {
            Image(
                painter = painterResource(R.drawable.ic_live_photo),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 5.dp)
                    .size(20.dp)
                    .graphicsLayer {
                        val shrink = 1f - scale
                        // 与缩略图一起缩小时，同时向画面中心收一段距离：
                        // 只缩小不内收会让角标「脱离」图片边缘，看起来像漏贴上去的
                        translationX = -shrink * 18.dp.toPx()
                        translationY = shrink * 18.dp.toPx()
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            )
        }

        // 勾选徽章（右上角，项 2）：选中时 primary 圆底 + 白色序号，未选中空心圈；
        // 数字与圆圈均以自身几何中心缩放（项 8）
        // 徽章尺寸按序号位数自适应：三位数（100+）增大徽章避免末位数字被截断
        val digits = if (selected) (selectionIndex + 1).toString().length else 1
        val badgeSize = when {
            digits >= 3 -> 28.dp
            digits == 2 -> 24.dp
            else -> 22.dp
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 6.dp)
                .size(badgeSize)
                .graphicsLayer {
                    scaleX = badgeScale
                    scaleY = badgeScale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .background(badgeBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = if (selected) selectionIndex else -1,
                transitionSpec = {
                    // 数字切换：以中心为基准缩放淡入淡出（项 8）
                    (scaleIn(
                        initialScale = 0.5f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn(tween(200))) togetherWith
                            (scaleOut(
                                targetScale = 0.5f,
                                animationSpec = tween(150)
                            ) + fadeOut(tween(150)))
                },
                label = "badge"
            ) { idx ->
                if (idx >= 0) {
                    Text(
                        text = "${idx + 1}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Box(
                        Modifier
                            .size(16.dp)
                            .graphicsLayer { alpha = 0.8f }
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * 兼容缩略图加载：
 * - Android 10+：ContentResolver.loadThumbnail（系统缓存缩略图，不落盘）
 * - Android 9-：无 loadThumbnail API，退化用 MediaStore MINI_KIND 内置缩略图；
 *   仍取不到（新导入未生成缩略图）时按 DATA 路径采样解码兜底
 */
private fun loadThumbCompat(resolver: ContentResolver, uri: Uri): Bitmap? {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        return try {
            resolver.loadThumbnail(uri, Size(160, 160), null)
        } catch (_: Exception) { null }
    }
    @Suppress("DEPRECATION")
    val bmp = try {
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        if (uri.toString().contains("/video/")) {
            android.provider.MediaStore.Video.Thumbnails.getThumbnail(
                resolver, id, android.provider.MediaStore.Video.Thumbnails.MINI_KIND, null)
        } else {
            android.provider.MediaStore.Images.Thumbnails.getThumbnail(
                resolver, id, android.provider.MediaStore.Images.Thumbnails.MINI_KIND, null)
        }
    } catch (_: Exception) { null }
    return bmp ?: decodePathFallback(resolver, uri)
}

/** Android 9- 兜底：按 DATA 路径采样解码（边长上限 ~512），避免过度占用内存。 */
private fun decodePathFallback(resolver: ContentResolver, uri: Uri): Bitmap? {
    val path = try {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    } catch (_: Exception) { null }
    if (path.isNullOrEmpty()) return null
    return try {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        var maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / sample > 512) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        android.graphics.BitmapFactory.decodeFile(path, opts)
    } catch (_: Exception) { null }
}

@Composable
private fun MediaThumbnail(uri: Uri, resolver: ContentResolver, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, uri) {
        val key = uri.toString()
        // 命中缓存（滚动回来）：直接显示，不重复加载
        thumbCache.get(key)?.let { value = it; return@produceState }
        value = withContext(thumbDispatcher) {
            val b = loadThumbCompat(resolver, uri)
            if (b != null) thumbCache.put(key, b)
            b
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = bitmap != null,
            enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.92f),
            exit = fadeOut(tween(150))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        // 加载占位（项 9 ImageToolbox 式）：**静态**色块。
        // 此前的「呼吸脉冲」是无限动画——网格是顶栏玻璃的采样层，扫描首屏时
        // 几十个占位同时逐帧改 alpha 会让采样层每帧失效、顶栏/按钮的玻璃
        // 每帧重算模糊与折射（性能 P0）。占位改为常量 alpha，采样层只在
        // 真有内容变化（图片到位 / 滚动）时才失效。
        if (bitmap == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.66f))
            )
        }
    }
}
