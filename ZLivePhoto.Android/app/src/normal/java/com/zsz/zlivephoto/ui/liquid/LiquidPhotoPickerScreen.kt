package com.zsz.zlivephoto.ui.liquid

import com.zsz.zlivephoto.flavor.FlavorCapabilities
import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.widthIn
import com.zsz.zlivephoto.ui.formatOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
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
import com.zsz.zlivephoto.ui.ReminderKey
import com.zsz.zlivephoto.ui.glass.GlassMenuRow
import com.zsz.zlivephoto.ui.glass.LocalGlassEnabled
import com.zsz.zlivephoto.ui.glass.LocalGlassRootTint
import com.zsz.zlivephoto.ui.glass.PublishPageBackdrop
import com.zsz.zlivephoto.ui.glass.TopBarFrost
import com.zsz.zlivephoto.ui.glass.animatedThemeColor
import com.zsz.zlivephoto.ui.glass.liquidDraggable
import com.zsz.zlivephoto.ui.glass.liquidMorphClip
import com.zsz.zlivephoto.ui.glass.rememberGlassButtonPhysics
import com.zsz.zlivephoto.ui.glass.rememberLiquidDragState
import com.zsz.zlivephoto.ui.glass.rememberLiquidMorphProgress
import io.github.neoruaa.glasmio.icons.SfIcon
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousRoundedRectangle
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.swipeBackExcluded
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioAlertFieldGroup
import io.github.neoruaa.glasmio.ui.GlasmioAlertTextField
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioButtonSize
import io.github.neoruaa.glasmio.ui.GlasmioCascadingMenuItem
import io.github.neoruaa.glasmio.ui.GlasmioEditableText
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import com.zsz.zlivephoto.ui.picker.AlbumInfo
import com.zsz.zlivephoto.ui.picker.AlbumScanner
import com.zsz.zlivephoto.ui.picker.ImportMode
import com.zsz.zlivephoto.ui.picker.MediaFilter
import com.zsz.zlivephoto.ui.picker.MediaItem
import com.zsz.zlivephoto.ui.picker.MediaKind
import com.zsz.zlivephoto.ui.picker.naturalCompare
import com.zsz.zlivephoto.ui.liquid.common.LiquidGlassIconCircle
import com.zsz.zlivephoto.ui.liquid.common.LiquidIndeterminateProgressBar
import com.zsz.zlivephoto.ui.liquid.common.LiquidSlideOverPage
import com.zsz.zlivephoto.ui.liquid.picker.PickerCascadingMenu
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
    FlavorCapabilities.maxParallelism
)

/** 相册内排序字段 */
enum class AlbumSortField { DATE, SIZE, NAME }

/** 按字段排序（正/倒序）。名称按自然顺序（数字段按数值比较），日期取 sortTime（拍摄时间，缺失回退修改时间）。 */
private fun sortMediaItems(
    items: List<MediaItem>,
    field: AlbumSortField,
    ascending: Boolean
): List<MediaItem> {
    val comparator = when (field) {
        AlbumSortField.DATE -> compareBy<MediaItem> { it.sortTime }
        AlbumSortField.SIZE -> compareBy { it.size }
        AlbumSortField.NAME -> Comparator<MediaItem> { a, b -> naturalCompare(a.name, b.name) }
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
 * 选择器返回按钮：Glasmio 玻璃圆钮 + 苹果风格「<」。
 */
@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = com.zsz.zlivephoto.ui.rememberHapticFeedback()
    LiquidGlassIconCircle(
        onClick = { haptic.click(); onBack() },
        modifier = modifier
    ) {
        SfIcon(
            systemName = "chevron.backward",
            contentDescription = stringResource(R.string.screen_back),
            size = 22.dp
        )
    }
}

/**
 * 选择器顶栏/浮层的通用玻璃圆钮。
 * GlasmioIconButton 默认由 action group 合并绘制表面，首帧组宽为 0 时只剩底色
 * → 看起来是「纯色实心圆」。所有浮在照片上的按钮都必须显式给这三项。
 */
@Composable
private fun ChromeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    LiquidGlassIconCircle(
        onClick = onClick,
        modifier = modifier,
        content = content
    )
}

/**
 * 确认导入按钮（Glasmio 玻璃按钮）：
 * - 文案 = 「导入」+ 动态照片/图片/视频三类张数（数字变化时逐位上下滚动切换）
 * - 图片与视频数量不匹配（或未选）时禁用
 */
@Composable
private fun ConfirmImportButton(
    canConfirm: Boolean,
    liveCount: Int,
    imageCount: Int,
    videoCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlasmioButton(
        onClick = onClick,
        // 未选中 → 已选中直接跳变：不再做宽度/计数形变（用户要求回到上一版观感）
        modifier = modifier,
        enabled = canConfirm,
        emphasis = if (canConfirm) GlassEmphasis.Prominent else GlassEmphasis.Regular,
        size = GlasmioButtonSize.Large
    ) {
        val textColor = LocalContentColor.current
        Text(
            stringResource(R.string.screen_import),
            // 用库的按钮字号（17sp）而不是 labelLarge(14sp)，小屏/长译文也不被裁
            style = IosTypography.headline,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // 已选汇总：只显示数量 > 0 的类别，文案用「动态照片 / 图片 / 视频」全称，
        // 分隔符只在两项以上时出现；数字恢复上下滚动切换（用户要求）
        val counts = buildList {
            if (liveCount > 0) add(R.string.screen_count_live to liveCount)
            if (imageCount > 0) add(R.string.screen_count_image to imageCount)
            if (videoCount > 0) add(R.string.screen_count_video to videoCount)
        }
        counts.forEachIndexed { index, (labelRes, count) ->
            if (index > 0) {
                Text(
                    text = "· ",
                    style = IosTypography.headline,
                    color = textColor.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
            Text(
                text = stringResource(labelRes),
                style = IosTypography.headline,
                color = textColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            RollingCount(
                count = count,
                style = IosTypography.headline,
                color = textColor.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * 计数滚动切换：数字增加时新数字自下往上顶入，减少时自上往下落回。
 * 只对数字本体做位移动画，标签文字保持静止，宽度随内容自然变化。
 */
@Composable
private fun RollingCount(count: Int, style: TextStyle, color: Color) {
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(tween(220, easing = FancyEasing)) { h -> if (up) h else -h } +
                    fadeIn(tween(140))) togetherWith
                    (slideOutVertically(tween(220, easing = FancyEasing)) { h -> if (up) -h else h } +
                            fadeOut(tween(140)))
        },
        contentAlignment = Alignment.Center,
        label = "rollingCount"
    ) { value ->
        Text(
            text = value.toString(),
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false
        )
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
fun LiquidPhotoPickerScreen(
    albums: List<AlbumInfo>,
    scanner: AlbumScanner,
    scannerScope: CoroutineScope,
    isDarkTheme: Boolean,
    /** 本次导入方式（选文件之前已确定）：决定显示/可选媒体与输出方式是否锁定 */
    importMode: ImportMode,
    /** 右上角输出格式（key）：决定本次导入的输出格式（含 "extract"=拆解） */
    outputFormat: String,
    onOutputFormatChange: (String) -> Unit,
    onBack: () -> Unit,
    /** 相册选择页（二级页）开合状态回传，宿主据此避让顶层手势返回 */
    onSubpageChanged: (Boolean) -> Unit = {},
    /** [coverSec] 仅「转换视频」模式有效（封面帧秒数，-1 表示不适用） */
    onConfirm: (List<MediaItem>, Double) -> Unit
) {
    val haptic = com.zsz.zlivephoto.ui.rememberHapticFeedback()
    var currentBucketId by remember { mutableStateOf(albums.firstOrNull()?.bucketId) }
    val bucketId = currentBucketId
    val selected = remember { mutableStateListOf<MediaItem>() }
    var albumsExpanded by remember { mutableStateOf(false) }
    // 相册全屏选择页（相册展开按钮旁的「选择」按钮进入）
    var albumPageOpen by remember { mutableStateOf(false) }
    // 相册页的搜索状态提升到此处：共享返回按钮需要先收起搜索框，再关闭相册页
    var albumSearchOpen by remember { mutableStateOf(false) }
    var albumSearchQuery by remember { mutableStateOf("") }
    LaunchedEffect(albumPageOpen) { onSubpageChanged(albumPageOpen) }
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
    // 当前导入方式允许的输出格式 / 显示类别：逻辑上不可能的组合直接不出现
    val allowedFormats = remember(importMode) {
        formatOptions.filter { importMode.allowsFormat(it.key) }
    }
    // 显示类别始终提供四档（全部 / 图片 / 动态照片 / 视频）：
    // 之前按导入方式裁剪只剩两项，用户无法在「转换动态照片」时切到图片/视频视图核对素材。
    val allowedFilters = MediaFilter.values().toList()
    // 从其它模式带着「拆解」/无效格式进来时，自动落到本模式的合法值
    LaunchedEffect(importMode) {
        if (allowedFormats.isNotEmpty() && !importMode.allowsFormat(outputFormat)) {
            onOutputFormatChange(allowedFormats.first().key)
        }
    }
    // 右上角「更多」菜单：moreMenuOpen=菜单显隐；
    // moreAnchor = 三点按钮**窗口坐标**矩形（横/竖屏各自测量），交给库的
    // GlasmioCascadingMenu 贴锚点形变展开（一级行点开后在原地长出二级选项页）。
    var moreMenuOpen by remember { mutableStateOf(false) }
    var moreAnchor by remember { mutableStateOf(Rect.Zero) }
    // 横/竖屏各存一份三点按钮矩形：两条工具栏可能同时参与组合（横竖切换动画期间），
    // 共享一个 moreAnchor 会被对方的 onGloballyPositioned 覆盖，导致菜单跑到屏幕顶部。
    // 因此点击时把**本按钮**最新测得的位置写入 moreAnchor。
    var portraitMoreRect by remember { mutableStateOf(Rect.Zero) }
    var landscapeMoreRect by remember { mutableStateOf(Rect.Zero) }
    // 三点钮实时透明度：库在形变时回调，按钮随菜单长出同步淡出并缩小，
    // 与形变合为一体（只在 draw 阶段读取，避免每帧重组）
    val moreTriggerAlpha = remember { mutableFloatStateOf(1f) }
    // 搜索：点按钮后原地横向展开为玻璃搜索框
    var searchOpen by remember { mutableStateOf(false) }
    // 搜索展开形变：一条欠阻尼弹簧驱动「搜索钮触发圆 → 整行搜索框」的裁剪形变、
    // 玻璃模糊与内容入场（复刻 liquid_glass_widgets 菜单的 morph）
    val searchMorph = rememberLiquidMorphProgress(searchOpen)
    // 触发圆锚点：搜索钮的根坐标矩形（竖屏/横屏各自测量，同一时刻只有一个）
    var searchAnchor by remember { mutableStateOf(Rect.Zero) }

    // 页面采样层：应用级弹窗（权限引导 / 更新 / 文件名冲突）在选择器页也会弹出，
    // 它们统一从 PageBackdropPublisher 取「当前页面」做实时模糊。
    // 此前选择器只 import 未发布，弹窗会沿用主页已离开的采样层（采样到空白 → 玻璃发灰/发死）。
    val glassEnabledForPage = LocalGlassEnabled.current
    val pageBackdrop = if (glassEnabledForPage) rememberLayerBackdrop() else null
    PublishPageBackdrop(pageBackdrop)
    // 页内玻璃件统一采样 GlasmioBackdropHost 的静态采样层。
    // 不再单独录制缩略图网格：录制滚动内容时采样纹理与绘制帧不同步，
    // 表现就是玻璃「花屏 / 滑动乱闪 / 破面」（用户反馈）；改用宿主采样层后画面稳定。

    // 搜索展开时，系统返回键只收起搜索框，不直接退出选择器。
    // 放在主屏（相册选择页有自己的 BackHandler，后注册者优先，互不冲突）。
    BackHandler(enabled = searchOpen) {
        searchOpen = false
        searchQuery = ""
    }

    // 进入/离开相册：进入 diff+续扫，离开取消（保留进度）
    DisposableEffect(bucketId) {
        if (bucketId != null) scanner.enter(bucketId, scannerScope)
        onDispose { if (bucketId != null) scanner.leave(bucketId) }
    }

    // 切换导入方式 / 输出格式时，把当前方式下不可选的已选项移除，
    // 避免出现「已选中但点不动、也无法取消」的幽灵选择
    LaunchedEffect(importMode, outputFormat) {
        selected.removeAll { !importMode.allows(it) }
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
    // 序号与视觉顺序一致；先按导入方式过滤（合并=图片+视频 / 转换视频=仅视频 /
    // 转换与拆分动态照片=仅动态照片），再按名称搜索、媒体类别筛选与排序；
    // size 读取驱动扫描追加时重算
    val n = results?.size ?: 0
    val displayOrder = remember(results, n, sortField, sortAscending, searchQuery, mediaFilter, importMode) {
        sortMediaItems(
            applyMediaFilter(
                filterMediaItems(results.orEmpty().filter { importMode.allows(it) }, searchQuery),
                mediaFilter
            ),
            sortField, sortAscending
        )
    }

    // 合成模式视频时长告警阈值（超过 3 秒提示兼容性风险，但仍允许选择）
    val videoWarnMs = 3000L
    // 转换视频模式：>5 秒会截取前 5 秒
    val videoTrimMs = 5000L

    // 合成模式提示弹窗状态 / 转换视频模式的 5 秒截取提示
    var showOver3sWarning by remember { mutableStateOf(false) }
    var showTrimNotice by remember { mutableStateOf(false) }

    /** 项是否已选中 */
    fun isSelected(item: MediaItem): Boolean =
        selected.any { it.id == item.id && it.isVideo == item.isVideo }

    /**
     * 项是否可选：完全由导入方式决定（[ImportMode.allows]）。
     * - 合并：图片 + 视频可选（动态照片不参与配对）
     * - 转换视频：仅视频
     * - 转换 / 拆分动态照片：仅动态照片
     */
    val isSelectable: (MediaItem) -> Boolean = { item -> importMode.allows(item) }

    // 选择统计（确认按钮内显示：动态照片 / 图片 / 视频 各自张数）
    val liveCount = selected.count { it.kind == MediaKind.LIVE }
    val imageCount = selected.count { it.kind == MediaKind.IMAGE }
    val videoCount = selected.count { it.kind == MediaKind.VIDEO }
    // 确认门槛：
    // - 合并：图片与视频数量必须一致（按选择顺序两两配对）
    // - 转换视频：至少 1 个视频
    // - 转换 / 拆分动态照片：至少 1 张动态照片
    val canConfirm = when (importMode) {
        ImportMode.COMPOSE -> selected.isNotEmpty() && imageCount == videoCount && videoCount > 0
        ImportMode.VIDEO -> videoCount > 0
        ImportMode.LIVE, ImportMode.EXTRACT -> liveCount > 0
    }

    // 「转换视频」：确认前先问封面帧秒数（点击开始后才真正处理）
    var coverDialogVisible by remember { mutableStateOf(false) }
    var coverInput by remember { mutableStateOf("1") }
    val doConfirm: () -> Unit = {
        if (importMode == ImportMode.VIDEO) {
            coverInput = "1"
            coverDialogVisible = true
        } else {
            onConfirm(selected.toList(), -1.0)
        }
    }

    // 单项选择切换（追加到末尾，序号递增）；按导入方式给出相应提示
    val toggleItem: (MediaItem) -> Unit = { item ->
        if (!isSelectable(item)) {
            // 当前导入方式下不可选：仅给出触感反馈，不改变选择
            haptic.click()
        } else {
            haptic.click()
            val idx = selected.indexOfFirst { it.id == item.id && it.isVideo == item.isVideo }
            if (idx >= 0) {
                selected.removeAt(idx)
            } else {
                selected.add(item)
                if (item.isVideo && importMode == ImportMode.VIDEO) {
                    if (item.durationMs > videoTrimMs &&
                        !AppSettings.isReminderSuppressed(ReminderKey.VIDEO_OVER_5S_TRIM)) {
                        showTrimNotice = true
                    }
                } else if (item.isVideo && item.durationMs > videoWarnMs &&
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
    // 旋转屏幕时收起「更多」菜单：锚点位置已变，继续显示会停在旧坐标上（遮挡/错位）
    LaunchedEffect(isLandscape) {
        moreMenuOpen = false
        // 旋转后搜索框锚点已失效：收起搜索并清空关键字
        searchOpen = false
        searchQuery = ""
    }
    // 横屏左侧相册栏宽度：收起为窄栏（仅相册名），展开时整栏由左向右变宽（配合封面右向展开）
    // 横屏相册栏宽度：按可用宽度收缩（约 22%，夹在 140..200dp），展开态再 +40dp。
    // 不再固定 148/188dp——平板/横屏宽屏下左侧栏不会过宽挤占网格。
    val rootWidthDp = if (rootSizePx == IntSize.Zero)
        LocalConfiguration.current.screenWidthDp.dp
    else with(LocalDensity.current) { rootSizePx.width.toDp() }
    val railCollapsedDp = (rootWidthDp * 0.22f).coerceIn(140.dp, 200.dp)
    val railWidth by animateDpAsState(
        targetValue = if (albumsExpanded) (railCollapsedDp + 40.dp).coerceAtMost(200.dp) else railCollapsedDp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "albumRailWidth"
    )
    // 顶栏覆盖层：网格是滚动内容；顶栏浮在其上叠纯色渐变（Glasmio 版不再自建采样层，
    // 玻璃折射交由外层 GlasmioBackdropHost 承担）
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
    // 玻璃带高度只用**收起态**高度：相册展开时 chip/封面向下长，玻璃带保持不变，
    // 展开区不铺整列底色，网格顶部也让位到同一收起态高度（不随展开瞬增而跳动）。
    // 首帧 onSizeChanged 尚未回调（veilHeight=0）：若直接用它，第一帧网格顶到屏幕
    // 最上方、第二帧整体下移近 90dp，肉眼可见跳动。这里用可计算的高度兜底：
    // 状态栏 + 标题行（圆钮 40dp + 上下各 2dp）+ 相册行（chip 28dp + 上下各 8dp）。
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val estimatedChromeDp = statusBarTopDp + 44.dp + if (albums.size > 1) 44.dp else 0.dp
    val veilHeight = when {
        collapsedChromePx > 0 -> collapsedChromeDp
        chromeHeightPx > 0 -> chromeHeightDp
        else -> estimatedChromeDp
    }
    // 状态栏 inset 晚到（冷启动首帧为 0）、相册扫描完成导致相册行出现/消失时，
    // 收起态高度需要重新测量一次（否则会一直沿用过小的冷启动值）。
    LaunchedEffect(statusBarTopDp, albums.size) {
        collapsedChromePx = 0
    }
    // 玻璃带**绘制**高度：相册展开时延伸到展开区下沿，让顶栏与相册展开区共用
    // 同一块阴影/薄纱（此前只在相册条那一截有阴影，下面是干净背景，像被硬切）。
    // 注意它只影响绘制，不影响 contentTopPadding —— 网格让位仍按收起态高度，
    // 否则展开/收起时列表会被重新排版（这也是此前「列表被拉一下」的来源之一）。
    val bandHeight = if (albumsExpanded) maxOf(chromeHeightDp, veilHeight) else veilHeight
    // 输出格式菜单与返回按钮都是玻璃 chrome：整屏 Box 承载菜单浮层
    // （库在独立 Window 内采样坐标错误，弹窗/菜单必须同窗口 Compose overlay）
    Box(
        Modifier
            .fillMaxSize()
            // 实测窗口尺寸回填给 rootSizePx：旋转后本修饰符随根布局重新测量，
            // 不依赖系统把 Orientation 配置分发到界面（横竖屏判断的真正数据源）
            .onSizeChanged { rootSizePx = it }
    ) {
    // 玻璃件直接采样外层 GlasmioBackdropHost 的采样层（见其 overlay 说明），
    // 页面内不再创建/录制任何 backdrop，避免反馈循环与采样撕裂。
    // 采样层只录「相册栏 + 网格 + 顶栏」这一层（下面的菜单浮层、本地弹窗都在录制范围之外），
    // 否则玻璃菜单/弹窗会把自己也录进采样源，出现重影或首帧采样为空。
    Box(
        Modifier
            .fillMaxSize()
            .then(if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier)
    ) {
    Row(
        Modifier
            .fillMaxSize()
    ) {
        // ── 横屏：最左侧纵向铺开的相册栏（展开方向由左到右）──
        if (isLandscape) {
            Column(
                Modifier
                    .width(railWidth)
                    .fillMaxHeight()
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
                    // 返回按钮由页面根部的共享按钮承担（横屏同位置，不随页面切换重绘）
                    Spacer(Modifier.size(40.dp))
                    Spacer(Modifier.weight(1f))
                    // 全屏相册选择页入口已移入「更多」菜单（横屏同样只留展开/收起钮）
                }
                // 行 2：标题独立成行（独占整栏宽度，不再与按钮争抢）
                Text(
                    stringResource(R.string.screen_picker_title_select),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    // 横屏左栏只有 140~200dp，西语/阿拉伯语标题单行必然省略；
                    // 允许两行，保证「选择动态照片」类标题完整可读。
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                )
                // 相册数（展开/收起改为长按相册 chip，不再单独放箭头按钮）
                Text(
                    stringResource(R.string.screen_album_count, albums.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
                // 扫描进度
                AnimatedVisibility(visible = running) {
                    LiquidIndeterminateProgressBar(
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
                            onClick = { haptic.click(); switchAlbum(album.bucketId) },
                            // 长按相册：展开 / 收起缩略图
                            onLongClick = { haptic.click(); albumsExpanded = !albumsExpanded },
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
                BoxWithConstraints(
                    Modifier.fillMaxSize()
                ) {
                    // 底部渐变高度按可用高度自适应（横屏屏矮时不吞掉半屏网格）：
                    // 约 18% 可用高度，夹在 96..160dp
                    val bottomFadeHeight = (maxHeight * 0.18f).coerceIn(96.dp, 160.dp)
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
                            contentTopPadding = if (isLandscape) 4.dp else veilHeight + 4.dp,
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
                    // 玻璃模式下必须与 GlassRoot 的根渐变同色，否则底部渐变到主题背景色
                    // （#FFFBFE / #141218）与根色（纯白 / #0B0B0D）之间出现一条可见色差带
                    // （用户反馈「阴影栏深浅黑白不匹配，有断层」）。
                    val bottomFill =
                        if (glassEnabledForPage) LocalGlassRootTint.current
                        else MaterialTheme.colorScheme.background
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(bottomFadeHeight)
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
                if (topButtonsAlpha > 0.01f) {
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
                            Box(Modifier.onGloballyPositioned { searchAnchor = it.boundsInWindow() }) {
                                ChromeIconButton(
                                    onClick = { haptic.click(); searchOpen = true },
                                    modifier = Modifier
                                ) {
                                    SfIcon(
                                        systemName = "magnifyingglass",
                                        contentDescription = stringResource(R.string.screen_search),
                                        size = 20.dp
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            // 更多（右）：与竖屏同一套菜单；菜单从本钮矩形形变长出，
                            // 不再提前淡出锚点（避免「先消失再长出」的断层）
                            Box(
                                Modifier
                                    .graphicsLayer {
                                        val a = moreTriggerAlpha.floatValue.coerceIn(0f, 1f)
                                        alpha = a
                                        val s = 0.55f + 0.45f * a
                                        scaleX = s
                                        scaleY = s
                                    }
                                    .onGloballyPositioned { landscapeMoreRect = it.boundsInWindow() }
                            ) {
                                ChromeIconButton(
                                    onClick = {
                                        haptic.click()
                                        moreAnchor = landscapeMoreRect
                                        moreMenuOpen = true
                                    },
                                    modifier = Modifier
                                ) {
                                    SfIcon(
                                        systemName = "ellipsis.vertical",
                                        contentDescription = stringResource(R.string.screen_more),
                                        size = 20.dp
                                    )
                                }
                            }
                    }
                }
            }

            // ── 导入按钮：永远固定在右下角 ──
            // **必须在裁剪层之外**：按钮带拖拽形变 + 按压缩放（physics.transform），
            // 此前的 clipToBounds 会把形变后的玻璃/文字切掉一截
            // （用户反馈「拖拽或点击时内容被裁切」）；采样仍走 pickerBackdrop
            // （上面的真实照片），移出裁剪层不影响采样。
            ConfirmImportButton(
                canConfirm = canConfirm,
                liveCount = liveCount,
                imageCount = imageCount,
                videoCount = videoCount,
                onClick = { haptic.click(); doConfirm() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp)
            )

            // ── 顶栏覆盖层：标题 / 相册条 / 搜索 / 工具行浮在滚动内容之上 ──
            // 背后是自屏幕顶端向中间减淡的纯色渐变（深色模式黑 / 浅色模式白），
            // 用于区分常驻顶栏与向下滚动的照片列表（替代此前的渐进高斯模糊）。
            // 顶栏覆盖层内的 Glasmio 玻璃件由外层 GlasmioBackdropHost 提供采样
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                if (!isLandscape) {
                // Glasmio 顶栏玻璃带：半透明（50%）+ 底部 32dp 透明度渐隐。
                // 绘制高度取 bandHeight：相册展开时向下延伸盖住展开区（阴影横向贯通、
                // 与顶栏连成一整块），但内容的让位高度仍是收起态（不触发网格重排）。
                // 横屏没有常驻顶栏：不画玻璃带（否则会用竖屏留下的陈旧高度盖住网格顶部）。
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(bandHeight)
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
                        // 玻璃模式下与根色同源（surfaceContainer 与 GlassRoot 根色不同，
                        // 半透明叠加后会形成一条比页面底色更深的横带）；MD3 模式沿用主题容器色
                        .background(
                            if (glassEnabledForPage) LocalGlassRootTint.current.copy(alpha = 0.50f)
                            else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.50f)
                        )
                )
                }
                Column(
                Modifier
                    .fillMaxWidth()
            ) {
            if (!isLandscape) {
                // ── 竖屏顶栏模糊区：标题行 + 相册栏 + 进度条（模糊/薄纱仅作用于此，搜索与工具行不模糊）──
                // 模糊覆盖到状态栏（不 pad 状态栏），内容自身再向下让出状态栏高度
                // 玻璃带高度 = 收起态顶栏内容实测高（见 veilHeight）：相册展开只会让
                // chip 浮层向下长，玻璃带不跟着变长；下沿与内容底边齐平，
                // 日期分组 stickyHeader 钉在其下方 4dp 处，不被玻璃遮挡
                Box(Modifier.fillMaxWidth()) {
                    // 顶栏内容色：Glasmio 主题下统一跟随主题 onSurface
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurface
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
                                if (!albumsExpanded) {
                                    // 只接受「更小」的测量值：关闭封面时 chip 高度是动画回落，
                                    // 中途仍大于最终高度。若把中间值写进收起态高度，玻璃带与
                                    // 网格让位会跟着一起变，表现为「关闭缩略图时列表被拉一下」。
                                    collapsedChromePx = if (collapsedChromePx == 0) {
                                        it.height
                                    } else {
                                        minOf(collapsedChromePx, it.height)
                                    }
                                }
                            }
                            .statusBarsPadding()
                    ) {
                // 标题行：收起态 = 返回 + 标题 + 搜索 + 更多；展开态由**同一条形变进度**
                // 驱动的搜索框接管——复刻 liquid_glass_widgets 菜单的 morph：
                // 从搜索钮的触发圆（40dp）长成整行、圆角 easeInExpo、玻璃模糊 0→基准、
                // 内容在形变 25%..70% 淡入并 0.7→1 放大；收起沿同一路径弹回触发圆。
                Box(Modifier.fillMaxWidth()) {
                    // 返回按钮由页面根部的共享按钮承担：始终固定在原位，
                    // 不参与搜索展开/收起，也不随相册选择页切换而移动。
                    // 右侧区域：标题 + 搜索钮 + 更多钮；同时是搜索栏形变的裁剪范围
                    // （搜索栏只从搜索钮长到右端，不会盖住返回按钮）。
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 60.dp, end = 12.dp)
                            // 44dp = GlassIconButton 默认直径。此前 40dp - 上下各 2dp 内缩
                            // 只给按钮留了 36dp 高，按钮被压成 44×36 的扁圆（用户反馈）
                            .height(44.dp)
                    ) {
                        // 搜索态退出动画：更多钮向右滑出屏幕，标题与搜索钮淡出
                        val moreExit by animateFloatAsState(
                            targetValue = if (searchOpen) 1f else 0f,
                            animationSpec = tween(if (searchOpen) 220 else 260, easing = FancyEasing),
                            label = "moreExit"
                        )
                        val textAlpha = (1f - searchMorph.value * 2f).coerceIn(0f, 1f)
                        Row(
                            Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.screen_picker_title_select),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                // 玻璃模式下跟随顶栏玻璃的自适应色（按照片亮度切黑白），
                                // MD3 模式用主题 onSurface
                                color = LocalContentColor.current,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                                    .graphicsLayer { alpha = textAlpha }
                            )
                            // 搜索（玻璃圆钮）：点击后从本钮长成右侧整条搜索框；
                            // 锚点取窗口坐标（boundsInRoot 会被状态栏偏移，导致形变原点错位）
                            Box(
                                Modifier
                                    .graphicsLayer { alpha = textAlpha }
                                    .onGloballyPositioned { searchAnchor = it.boundsInWindow() }
                            ) {
                                ChromeIconButton(
                                    onClick = { haptic.click(); searchOpen = true },
                                    modifier = Modifier
                                ) {
                                    SfIcon(
                                        systemName = "magnifyingglass",
                                        contentDescription = stringResource(R.string.screen_search),
                                        size = 20.dp
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            // 右上角「更多」：搜索展开时整钮向右滑出屏幕（不做覆盖式淡出）
                            Box(
                                Modifier
                                    .graphicsLayer {
                                        val a = moreTriggerAlpha.floatValue.coerceIn(0f, 1f)
                                        translationX = moreExit * 72.dp.toPx()
                                        alpha = (1f - moreExit) * a
                                        val s = 0.55f + 0.45f * a
                                        scaleX = s
                                        scaleY = s
                                    }
                                    .onGloballyPositioned { portraitMoreRect = it.boundsInWindow() }
                            ) {
                                ChromeIconButton(
                                    onClick = {
                                        haptic.click()
                                        moreAnchor = portraitMoreRect
                                        moreMenuOpen = true
                                    },
                                    modifier = Modifier
                                ) {
                                    SfIcon(
                                        systemName = "ellipsis.vertical",
                                        contentDescription = stringResource(R.string.screen_more),
                                        size = 20.dp
                                    )
                                }
                            }
                        }
                        // 搜索展开期间截断右侧区域的按钮点击（搜索框本身在上层，不受影响）
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
                                        // 只做透明度淡入，尺寸/位置完全由 liquidMorphClip 形变，
                                        // 与菜单展开同一套流体动效（不再额外缩放）。
                                        val p = searchMorph.value
                                        alpha = ((p - 0.25f) / 0.45f).coerceIn(0f, 1f)
                                    }
                            ) {
                                SearchInputRow(
                                    query = searchQuery,
                                    onQuery = { searchQuery = it },
                                    onClose = { searchOpen = false; searchQuery = "" },
                                    morph = { searchMorph.value },
                                    // 竖屏返回按钮常驻在左侧，这里不再重复画一个
                                    showBackButton = false
                                )
                            }
                        }
                    }
                }

                // 相册 chip 横滑条（展开/收起改为长按 chip 或上下滑动手势）
                if (albums.size > 1) {
                    val swipeThreshold = with(LocalDensity.current) { 24.dp.toPx() }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // 上滑收起缩略图、下滑展开缩略图（横向滑动仍交给 LazyRow）
                            .pointerInput(albumsExpanded, swipeThreshold) {
                                var total = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { total = 0f },
                                    onDragEnd = {
                                        if (total > swipeThreshold) albumsExpanded = true
                                        else if (total < -swipeThreshold) albumsExpanded = false
                                    }
                                ) { _, dragAmount -> total += dragAmount }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                                // 横向可拖组件优先于页面手势返回
                                .swipeBackExcluded(),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(albums, key = { it.bucketId }) { album ->
                                AlbumChip(
                                    album = album,
                                    selected = album.bucketId == bucketId,
                                    // 3.7.1 版式：相册条就在顶部固定栏内，展开时 chip 原地长高
                                    expanded = albumsExpanded,
                                    onClick = { haptic.click(); switchAlbum(album.bucketId) },
                                    // 长按相册：展开 / 收起缩略图
                                    onLongClick = { haptic.click(); albumsExpanded = !albumsExpanded },
                                    // 顶栏玻璃的自适应黑白（玻璃模式下；MD3 下为 onSurface，不会被用）
                                    contentColor = LocalContentColor.current
                                )
                            }
                        }
                    }
                }

                // 扫描进度条
                AnimatedVisibility(visible = running) {
                    LiquidIndeterminateProgressBar(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                    } // 顶栏模糊区 Column（固定栏）
                } // 顶栏模糊区 Box
                // 玻璃模式下顶栏由模糊衰减自然收边，不再画硬分隔线（断面）
            }
                } // 顶栏 Column
                    } // CompositionLocalProvider（顶栏内容自适应黑白）
            } // 顶栏覆盖层 Box
        }
    }
    } // 页面采样层 Box（layerBackdrop）

    // 合成模式提示弹窗（统一「不再提示」机制）：渲染在**整屏根 Box**内；
    // 横竖屏都覆盖全屏；visible 模式带退场动画
    ReminderInfoDialog(
        key = ReminderKey.COMPOSE_VIDEO_OVER_3S,
        onDismiss = { showOver3sWarning = false },
        visible = showOver3sWarning
    )
    // 「转换视频」：>5 秒只截取前 5 秒的提示
    ReminderInfoDialog(
        key = ReminderKey.VIDEO_OVER_5S_TRIM,
        onDismiss = { showTrimNotice = false },
        visible = showTrimNotice
    )

    // 右上角「更多」菜单：改用 Glasmio 原生级联菜单（GlasmioCascadingMenu）。
    // 一级 = 输出格式 / 排序字段 / 排序顺序 / 显示类别（副标题显示当前值）；
    // 点击一级行后由库在原地把该行 morph 成二级选项页（弹簧动画 + 内部滚动），
    // 选中子项后库会自行收起整张菜单，这里只负责更新业务数据、不再手动关菜单。
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
    val currentFormatLabel = (allowedFormats + formatOptions)
        .firstOrNull { it.key == outputFormat }
        ?.let { stringResource(it.nameRes) }
        ?: outputFormat
    if (moreMenuOpen) {
        // ① 输出格式：只列出本导入方式合法的格式（拆分模式固定「拆解」，
        //    其余模式不提供「拆解」），选中项带对勾
        val outputFormatChildren = allowedFormats.map { opt ->
            GlasmioCascadingMenuItem(
                title = stringResource(opt.nameRes),
                systemName = if (opt.key == outputFormat) "checkmark" else null,
                onClick = {
                    haptic.click()
                    onOutputFormatChange(opt.key)
                }
            )
        }
        // ② 排序字段
        val sortFieldChildren = AlbumSortField.values().map { field ->
            GlasmioCascadingMenuItem(
                title = stringResource(
                    when (field) {
                        AlbumSortField.DATE -> R.string.screen_sort_date
                        AlbumSortField.SIZE -> R.string.screen_sort_size
                        AlbumSortField.NAME -> R.string.screen_sort_name
                    }
                ),
                systemName = if (field == sortField) "checkmark" else null,
                onClick = {
                    haptic.click()
                    sortField = field
                }
            )
        }
        // ③ 排序顺序（正序 / 倒序）
        val sortOrderChildren = listOf(true to R.string.screen_sort_asc, false to R.string.screen_sort_desc)
            .map { (asc, res) ->
                GlasmioCascadingMenuItem(
                    title = stringResource(res),
                    systemName = if (asc == sortAscending) "checkmark" else null,
                    onClick = {
                        haptic.click()
                        sortAscending = asc
                    }
                )
            }
        // ④ 显示类别：只列出与本模式可选媒体有交集的类别
        val filterChildren = allowedFilters.map { filter ->
            GlasmioCascadingMenuItem(
                title = stringResource(mediaFilterLabelRes(filter)),
                systemName = if (filter == mediaFilter) "checkmark" else null,
                onClick = {
                    haptic.click()
                    mediaFilter = filter
                }
            )
        }
        val menuItems = mutableListOf<GlasmioCascadingMenuItem>()
        // 拆分动态照片模式：输出方式固定为「拆分」，不提供输出格式选项
        if (importMode != ImportMode.EXTRACT) {
            menuItems += GlasmioCascadingMenuItem(
                title = stringResource(R.string.screen_menu_output_format),
                subtitle = currentFormatLabel,
                systemName = "doc.text",
                children = outputFormatChildren
            )
        }
        menuItems += GlasmioCascadingMenuItem(
            title = stringResource(R.string.screen_menu_sort_field),
            subtitle = sortFieldLabel,
            systemName = "arrow.up.arrow.down",
            children = sortFieldChildren
        )
        menuItems += GlasmioCascadingMenuItem(
            title = stringResource(R.string.screen_menu_sort_order),
            subtitle = sortOrderLabel,
            systemName = if (sortAscending) "arrow.up" else "arrow.down",
            children = sortOrderChildren
        )
        menuItems += GlasmioCascadingMenuItem(
            title = stringResource(R.string.screen_menu_media_filter),
            subtitle = filterLabel,
            systemName = "square.grid.2x2",
            children = filterChildren
        )
        // ⑤ 全选 / 取消全选：一次选中当前相册所有可选项（按网格顺序编号）
        val allChosen = displayOrder.isNotEmpty() && displayOrder.all { isSelected(it) }
        menuItems += GlasmioCascadingMenuItem(
            title = stringResource(
                if (allChosen) R.string.screen_select_none else R.string.screen_select_all
            ),
            systemName = if (allChosen) "checkmark.circle.fill" else "checkmark.circle",
            onClick = {
                haptic.click()
                toggleGroup(displayOrder)
            }
        )
        // 全屏相册选择页入口放在最底部
        menuItems += GlasmioCascadingMenuItem(
            title = stringResource(R.string.screen_album_pick),
            systemName = "line.3.horizontal",
            onClick = {
                haptic.click()
                albumPageOpen = true
            }
        )
        // 库对空 children 的一级行只会“点一下就关”，用户看不到任何内容；
        // 但「选择相册」这类叶子动作行必须保留，所以只按 children 过滤掉纯空分组。
        val visibleMenuItems = menuItems.filter { it.children.isNotEmpty() || it.systemName != null }
        if (visibleMenuItems.isNotEmpty() && moreAnchor != Rect.Zero) {
            // 库的 GlasmioCascadingMenu 首次组合时自播展开动画，关闭动画播完才回调
            // onDismiss；它由外层 moreMenuOpen 控制是否组合，不能常驻。
            PickerCascadingMenu(
                anchorRect = moreAnchor,
                items = visibleMenuItems,
                onDismiss = {
                    moreMenuOpen = false
                    moreTriggerAlpha.floatValue = 1f
                },
                title = stringResource(R.string.screen_more),
                onTriggerAlphaChanged = { moreTriggerAlpha.floatValue = it }
            )
        }
    }
    } // Box（菜单浮层宿主）


    // 全屏相册选择页（相册「选择」进入）：从右侧整幅推入（非线性动画），
    // 返回按钮由页面根部同一颗共享按钮承担，位置不随页面切换变化。
    LiquidSlideOverPage(
        visible = albumPageOpen,
        onDismiss = {
            albumPageOpen = false
            albumSearchOpen = false
            albumSearchQuery = ""
        }
    ) {
        AlbumPickPage(
            albums = albums,
            currentBucketId = bucketId,
            onPick = { id ->
                slideDir = if (id > (bucketId ?: 0L)) 1 else -1
                switchAlbum(id)
                albumPageOpen = false
            },
            onClose = {
                albumPageOpen = false
                albumSearchOpen = false
                albumSearchQuery = ""
            },
            searchOpen = albumSearchOpen,
            onSearchOpenChange = { albumSearchOpen = it },
            query = albumSearchQuery,
            onQueryChange = { albumSearchQuery = it }
        )
    }

    // 全应用共享的返回按钮：始终停留在顶栏原位，覆盖主列表与相册选择页
    Box(Modifier.fillMaxSize()) {
    BackButton(
        onBack = {
            when {
                // 相册页内：搜索框展开时先收起搜索，再关页面
                albumPageOpen -> {
                    haptic.click()
                    if (albumSearchOpen) {
                        albumSearchOpen = false
                        albumSearchQuery = ""
                    } else {
                        albumPageOpen = false
                    }
                }
                searchOpen -> {
                    haptic.click()
                    searchOpen = false
                    searchQuery = ""
                }
                else -> onBack()
            }
        },
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(start = 12.dp, top = if (isLandscape) 4.dp else 0.dp)
    )
    }

    // 「转换视频」：确认时输入封面帧秒数（允许小数），点「开始」后才真正处理
    CoverFrameDialog(
        visible = coverDialogVisible,
        value = coverInput,
        onValueChange = { coverInput = it },
        onDismiss = { coverDialogVisible = false },
        onConfirm = { sec ->
            coverDialogVisible = false
            onConfirm(selected.toList(), sec)
        }
    )
    } // key(isDarkTheme)
}

/**
 * 全屏相册选择页：每行 = 封面缩略图（相册第一张）+ 相册名 + 数量。
 * 顶部搜索框按相册名过滤；排序按钮在「按名称升序 / 降序」间切换，
 * 名称用 [naturalCompare] 比较，保证 IMG_2 在 IMG_10 之前。
 */
@Composable
private fun AlbumPickPage(
    albums: List<AlbumInfo>,
    currentBucketId: Long?,
    onPick: (Long) -> Unit,
    onClose: () -> Unit,
    searchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit
) {
    var ascending by remember { mutableStateOf(true) }
    // 名称搜索：点击顶部搜索钮后从该钮形变展开（与主选择页同一套液体形变）
    // 展开状态由外层持有，共享返回按钮可优先收起搜索框
    val searchMorph = rememberLiquidMorphProgress(searchOpen)
    var albumSearchAnchor by remember { mutableStateOf(Rect.Zero) }
    // 竖排三点菜单：只放「正序 / 倒序」
    var albumMenuOpen by remember { mutableStateOf(false) }
    var albumMoreAnchor by remember { mutableStateOf(Rect.Zero) }
    var albumMoreRect by remember { mutableStateOf(Rect.Zero) }
    val moreTriggerAlpha = remember { mutableFloatStateOf(1f) }
    val haptic = com.zsz.zlivephoto.ui.rememberHapticFeedback()
    // 返回键优先关闭搜索 / 菜单，再关闭相册页
    BackHandler {
        when {
            searchOpen -> {
                onSearchOpenChange(false)
                onQueryChange("")
            }
            albumMenuOpen -> albumMenuOpen = false
            else -> onClose()
        }
    }
    val listState = rememberLazyListState()
    // 排序方向 / 搜索词变化后回到列表最顶端：否则会停在旧滚动位置，
    // 排序后的第一条被滚出屏幕，看起来像「排序没生效」
    LaunchedEffect(query, ascending) {
        listState.scrollToItem(0)
    }
    val shown = remember(albums, query, ascending) {
        val filtered = albums.filter {
            query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)
        }
        val cmp = Comparator<AlbumInfo> { a, b -> naturalCompare(a.name, b.name) }
        if (ascending) filtered.sortedWith(cmp) else filtered.sortedWith(cmp.reversed())
    }
    val resolver = LocalContext.current.contentResolver

    // 与「选择动态照片」页共用同一块页面底色（外层 LiquidSlideOverPage 的 GlassBackgroundFill）：
    // 此前这里额外铺了一层全屏 GlassSurface，颜色与主选择页不一致（用户反馈），
    // 现在不再叠加任何底色，两页背景完全统一。
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // 标题行：与主选择页同一套搜索形变（从搜索钮的触发圆长成整行），
            // 右侧放「搜索 + 竖排三点」；正序 / 倒序收进三点菜单。
            // 不再加任何纵向 padding：共享返回钮从状态栏下沿开始，这里也必须同为 44dp 顶对齐。
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 60.dp, end = 12.dp)
                        .height(44.dp)
                ) {
                    val textAlpha = (1f - searchMorph.value * 2f).coerceIn(0f, 1f)
                    Row(
                        Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.screen_album_pick),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                                .graphicsLayer { alpha = textAlpha }
                        )
                        // 搜索：点击后从本钮长成右侧整条搜索框（与主选择页形变一致）
                        Box(
                            Modifier
                                .graphicsLayer { alpha = textAlpha }
                                .onGloballyPositioned { albumSearchAnchor = it.boundsInWindow() }
                        ) {
                            ChromeIconButton(onClick = { haptic.click(); onSearchOpenChange(true) }) {
                                SfIcon(
                                    systemName = "magnifyingglass",
                                    contentDescription = stringResource(R.string.screen_search),
                                    size = 20.dp
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        // 更多（竖排三点）：正序 / 倒序
                        Box(
                            Modifier
                                .graphicsLayer {
                                    val a = moreTriggerAlpha.floatValue.coerceIn(0f, 1f)
                                    alpha = textAlpha * a
                                    val s = 0.55f + 0.45f * a
                                    scaleX = s
                                    scaleY = s
                                }
                                .onGloballyPositioned { albumMoreAnchor = it.boundsInWindow() }
                        ) {
                            ChromeIconButton(
                                onClick = {
                                    haptic.click()
                                    albumMoreRect = albumMoreAnchor
                                    albumMenuOpen = true
                                }
                            ) {
                                SfIcon(
                                    systemName = "ellipsis.vertical",
                                    contentDescription = stringResource(R.string.screen_more),
                                    size = 20.dp
                                )
                            }
                        }
                    }
                    // 搜索展开期间截断右侧按钮点击（搜索框在上层，不受影响）
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
                    if (searchOpen || searchMorph.isRunning) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .liquidMorphClip(
                                    progress = { searchMorph.value },
                                    anchor = { albumSearchAnchor },
                                    cornerRadius = 20.dp
                                )
                                .graphicsLayer {
                                    val p = searchMorph.value
                                    alpha = ((p - 0.25f) / 0.45f).coerceIn(0f, 1f)
                                }
                        ) {
                            SearchInputRow(
                                query = query,
                                onQuery = onQueryChange,
                                onClose = { onSearchOpenChange(false); onQueryChange("") },
                                morph = { searchMorph.value },
                                showBackButton = false
                            )
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.screen_album_pick_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(shown, key = { it.bucketId }) { album ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(album.bucketId) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                MediaThumbnail(
                                    uri = ((if (album.coverIsVideo)
                                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                    else
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                                        .buildUpon().appendPath(album.coverId.toString()).build()),
                                    resolver = resolver,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    album.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    stringResource(R.string.screen_album_media_count, album.count),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (album.bucketId == currentBucketId) {
                                SfIcon(
                                    systemName = "checkmark",
                                    contentDescription = null,
                                    size = 20.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 竖排三点菜单：正序 / 倒序（选中项带对勾，库会在选中后自行收起）
    if (albumMenuOpen && albumMoreRect != Rect.Zero) {
        PickerCascadingMenu(
            anchorRect = albumMoreRect,
            title = stringResource(R.string.screen_menu_sort_order),
            items = listOf(
                GlasmioCascadingMenuItem(
                    title = stringResource(R.string.screen_sort_asc),
                    systemName = if (ascending) "checkmark" else null,
                    onClick = {
                        haptic.click()
                        ascending = true
                    }
                ),
                GlasmioCascadingMenuItem(
                    title = stringResource(R.string.screen_sort_desc),
                    systemName = if (!ascending) "checkmark" else null,
                    onClick = {
                        haptic.click()
                        ascending = false
                    }
                )
            ),
            onDismiss = {
                albumMenuOpen = false
                moreTriggerAlpha.floatValue = 1f
            },
            onTriggerAlphaChanged = { moreTriggerAlpha.floatValue = it }
        )
    }
}

/** 「转换视频」封面帧秒数输入弹窗（允许小数；0~5 有效） */
@Composable
private fun CoverFrameDialog(
    visible: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val sec = value.trim().toDoubleOrNull()
    val valid = sec != null && sec >= 0.0 && sec <= 5.0
    GlasmioAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.screen_video_cover_title),
        message = stringResource(R.string.screen_video_cover_message),
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.screen_video_cover_cancel),
                role = GlasmioAlertButtonRole.Cancel,
                onClick = onDismiss
            ),
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.screen_video_cover_confirm),
                enabled = valid,
                onClick = { sec?.let(onConfirm) }
            )
        )
    ) {
        Column {
            // 弹窗内输入框改用库自带的 GlasmioAlertTextField（与 iOS 弹窗同款
            // 26dp 圆角字段组、玻璃底、光标色取玻璃强调色）；
            // 之前用 Material BasicTextField + surfaceContainerHigh，暗色下是一块与
            // 玻璃弹窗完全不同的不透明灰块。
            GlasmioAlertFieldGroup {
                GlasmioAlertTextField(
                    // 每次文本变化把光标固定到末尾（数值输入只会追加/退格）
                    value = TextFieldValue(value, selection = TextRange(value.length)),
                    onValueChange = { onValueChange(it.text) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            if (!valid) {
                Text(
                    stringResource(R.string.screen_video_cover_invalid),
                    style = IosTypography.footnote,
                    color = LocalGlassColors.current.destructive,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
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
            SfIcon(
                systemName = "photo",
                contentDescription = null,
                size = 64.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.screen_no_media_compose),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
            // 底部渐变带高度：与内容区底部渐罩用同一公式（可用高度 18%，夹 96..160dp）。
            // 网格底部留白引用该值，避免固定 112dp 与渐罩实际高度不一致（留白过多/末行被遮）。
            val bottomFadeHeight = (maxHeight * 0.18f).coerceIn(96.dp, 160.dp)
            LazyVerticalGrid(
            state = gridState,
            // 自适应列宽：每格最小 88dp，横屏/平板自动多排几列，
            // 不再固定 5 列把单格拉成巨块（竖屏窄屏自然落到 3~4 列）
            columns = GridCells.Adaptive(minSize = 88.dp),
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
                bottom = navBarBottom + 8.dp + bottomFadeHeight
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
                        // 日期栏的位置与移动交给 stickyHeader 自身（与 3.7.1 一致）。
                        // 但顶栏是**覆盖层**：stickyHeader 钉住时贴在视口 y=0，会被
                        // 半透明玻璃带压住 —— 日期文字既发灰、又与标题行重叠。
                        // 这里只做**绘制位移**（graphicsLayer 不参与布局测量，因此不会
                        // 出现此前 offset 方案「推下两倍内缩 + 滚动抖动」的问题）：
                        // 日期栏贴到玻璃带下沿，未钉住时位移为 0、行为不变。
                        val stickyDensity = LocalDensity.current
                        val veilPx = with(stickyDensity) {
                            (contentTopPadding - 4.dp).coerceAtLeast(0.dp).toPx()
                        }
                        var stuckShiftPx by remember { mutableStateOf(0f) }
                        // 关键：测量层与位移层必须是两个节点。
                        // positionInWindow() 会包含自身 graphicsLayer 的 translationY，
                        // 在同一节点上「测量→设位移→再测量」会每帧自我抵消，日期栏就会一直上下跳。
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coords ->
                                    val target = if (veilPx <= 0f) 0f else {
                                        val top = coords.positionInWindow().y
                                        (veilPx - top).coerceIn(0f, veilPx)
                                    }
                                    if (kotlin.math.abs(target - stuckShiftPx) > 0.5f) {
                                        stuckShiftPx = target
                                    }
                                }
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { translationY = stuckShiftPx }
                            ) {
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
    morph: () -> Float = { 1f },
    // 竖屏：返回按钮常驻在搜索框左侧（不参与形变），这里不再画第二个
    showBackButton: Boolean = true
) {
    val haptic = com.zsz.zlivephoto.ui.rememberHapticFeedback()
    // 44dp = 与顶栏玻璃圆钮同高；之前 40dp 比返回按钮矮一截（用户反馈）
    val pill = RoundedCornerShape(22.dp)
    // Glasmio 自绘光标 / 选择手柄需要 TextFieldValue（保留选区）
    var fieldValue by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    LaunchedEffect(query) {
        if (fieldValue.text != query) {
            fieldValue = TextFieldValue(query, TextRange(query.length))
        }
    }
    // 搜索展开后自动聚焦并拉起输入法：此前展开只显示输入框，用户还得再点一次才能输入。
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Row(
        // 右端留 12dp：搜索框此前几乎贴住屏幕右缘（用户反馈）。
        // 固定 44dp 高并与返回圆钮（44dp）垂直居中：两者高度与中线严格一致。
        Modifier.fillMaxWidth().height(44.dp).padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 与「选择动态照片」标题行的返回按钮完全同一组件（含 8dp 起始内缩），
        // 展开/收起时两者位置严格重合
        if (showBackButton) {
            ChromeIconButton(onClick = { haptic.click(); onClose() }) {
                SfIcon(
                    systemName = "chevron.backward",
                    contentDescription = stringResource(R.string.screen_search_collapse),
                    size = 22.dp
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        // 玻璃输入区：直接用玻璃表面（不是 GlasmioSheetSurface —— sheet 版本自带
        // 一层黑色投影，搜索框会看起来带「阴影层」，用户反馈要去掉）
        GlassSurface(
            modifier = Modifier.weight(1f).height(44.dp),
            shape = pill,
            emphasis = GlassEmphasis.Regular,
            groupSurfaceSeparation = { 1f }
        ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(14.dp))
            // 逐元素形变：放大镜比输入框更早淡入，并向左轻微滑入；
            // 收起时反向错开，避免整块内容“一起闪现”。
            SfIcon(
                systemName = "magnifyingglass",
                contentDescription = null,
                size = 18.dp,
                modifier = Modifier.graphicsLayer {
                    val t = ((morph() - 0.30f) / 0.45f).coerceIn(0f, 1f)
                    alpha = t
                    val s = 0.8f + 0.2f * t
                    scaleX = s
                    scaleY = s
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            GlasmioEditableText(
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                    onQuery(it.text)
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .graphicsLayer {
                        val t = ((morph() - 0.42f) / 0.45f).coerceIn(0f, 1f)
                        alpha = t
                        translationX = -10f * (1f - t)
                    },
                singleLine = true,
                maxLines = 1,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.screen_search_hint),
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
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
                    SfIcon(
                        systemName = "xmark",
                        contentDescription = stringResource(R.string.screen_search_clear),
                        size = 18.dp
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
        }
        }
    }
}

/**
 * 相册 chip（ImageToolbox 式）：展开态在文字下方垂直显示封面缩略图（按需加载）。
 * - 选中：secondaryContainer；未选中：surfaceContainerHigh
 * - 圆角曲率动画（收起 12dp ↔ 展开 16dp）直接作用于 Surface 的 shape，
 *   阴影/涟漪叠加层与按钮共用同一 shape，圆角动画全程实时匹配
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AlbumChip(
    album: AlbumInfo,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
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

    val chipContentColor = contentColor
        ?: if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    // 点击改挂 modifier 并显式 indication = null：避免 MD3 涟漪，与玻璃语言不符。
    // 用玻璃表面而非 sheet 表面：sheet 自带的黑色投影叠在相册条上会形成
    // 「半透明黑色遮挡」的观感，与顶部/底部的白色玻璃带颜色不统一（用户反馈）
    GlassSurface(
        modifier = modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onLongClick = onLongClick,
            onClick = { onClick() }
        ),
        shape = RoundedCornerShape(corner),
        emphasis = GlassEmphasis.Regular,
        groupSurfaceSeparation = { 1f }
    ) {
        // 选中态：玻璃之上叠一层主色 tint（不遮挡折射）
        if (selected) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            )
        }
        CompositionLocalProvider(LocalContentColor provides chipContentColor) {
        if (horizontal) {
            // 横向布局（选择器横屏用）：文字在左，展开时封面在右侧横向展开
            Row(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        // 与封面横向展开用同一套弹簧：宽度和内容的收放同步收尾
                        animationSpec = spring(
                            dampingRatio = 0.78f,
                            stiffness = Spring.StiffnessMediumLow
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
                    // 展开时右侧多出封面：文字占剩余宽度（长译文最多 2 行），
                    // 不再被 72dp 封面挤到不可见
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
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
                    // 与封面纵向展开同一套弹簧（0.78 / 520）：
                    // 之前这里用默认刚度的弹簧，收起时宽度要等纵向动画结束才补完
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 520f
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
            // 展开态：封面图（高度 100dp，宽度与文字对齐；按需加载走 LRU 缓存）。
            // 展开/收起与箭头统一用同一套弹簧（原来是 tween，竖屏手感与横屏不一致）
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(200)) + expandVertically(
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f)
                ),
                exit = fadeOut(tween(150)) + shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f)
                )
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
        } // CompositionLocalProvider（chip 内容色）
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

    // 徽章背景色：未选中是「玻璃黑」（半透明深色 + 白色描边），选中是主色玻璃面
    val badgeBg by animateColorAsState(
        // 未选中态不能是纯透明：空心白圈画在浅色照片上会完全消失（选择入口看不见）。
        // 铺一层半透明黑底，任何明暗的照片上都有对比。
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
        else Color.Black.copy(alpha = 0.24f),
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
                // 选中描边跟随液态玻璃风格（白色高光细边），不再用 MD3 的 primaryContainer 粗边
                .border(
                    width = if (selected) 1.5.dp else 0.dp,
                    color = if (selected) Color.White.copy(alpha = 0.85f) else Color.Transparent,
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

        // 动态照片角标：纯白图标、无灰底，且随图片选择缩放一起缩放（像印在图上）。
        // 用一个铺满单元格的透明层承担缩放，缩放原点即照片中心：
        // 之前直接对右下角图标做 scale，视觉上像是「向下缩」，与照片的相对位置会漂移。
        if (item.kind == MediaKind.LIVE) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_live_photo),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 5.dp, bottom = 5.dp)
                        .size(20.dp)
                )
            }
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
                .background(if (selected) Color.Transparent else badgeBg, CircleShape)
                // 白色高光细边：让徽章在任何底图上都保留「玻璃片」的边缘感
                .border(if (selected) 0.dp else 1.dp, Color.White.copy(alpha = 0.72f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // 选中态的**外圈**用液态玻璃（折射 + 环形高光边），里面再压一层主色圆底，
            // 白色序号在任何底图上都能读清；未选中仍是半透明黑底 + 白描边。
            if (selected && LocalGlassEnabled.current) {
                GlassSurface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    emphasis = GlassEmphasis.Regular,
                    groupSurfaceSeparation = { 1f }
                ) {}
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(1.5.dp)
                        .background(badgeBg, CircleShape)
                )
            }
            AnimatedContent(
                targetState = if (selected) selectionIndex else -1,
                // 内容必须居中：默认 TopStart 会让数字贴在外圈左上角，
                // 看起来像「从左下角往中间挪」（用户反馈）
                contentAlignment = Alignment.Center,
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
                        // 用库的 iOS 字号 + 去掉字体上下留白、行高对齐到中心：
                        // 继承 MD3 文本行高时，数字会落在圆圈的下半部（用户反馈）
                        style = IosTypography.caption2.copy(
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both
                            )
                        )
                    )
                }
                // 未选中态不再额外画一圈描边：外层徽章本身已有
                // 半透明黑底 + 1dp 白描边，内层再画一个 16dp 圆会叠成两层（用户反馈）
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
