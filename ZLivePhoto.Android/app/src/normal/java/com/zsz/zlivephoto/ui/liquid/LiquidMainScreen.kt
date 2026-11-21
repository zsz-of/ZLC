package com.zsz.zlivephoto.ui.liquid

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop as glassLayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop as rememberGlassLayerBackdrop
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.ui.AlphaEasing
import com.zsz.zlivephoto.ui.FancyEasing
import com.zsz.zlivephoto.ui.FileCard
import com.zsz.zlivephoto.ui.FileItem
import com.zsz.zlivephoto.ui.liquid.common.LiquidProgressBar
import com.zsz.zlivephoto.ui.OutputMode
import com.zsz.zlivephoto.ui.formatOptions
import com.zsz.zlivephoto.ui.glass.LocalGlassEnabled
import com.zsz.zlivephoto.ui.glass.LocalGlassRootTint
import com.zsz.zlivephoto.ui.glass.PublishPageBackdrop
import com.zsz.zlivephoto.ui.rememberHapticFeedback
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.LocalGlassDimensions
import com.kyant.capsule.ContinuousRoundedRectangle
import io.github.neoruaa.glasmio.core.systemGray4
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.scaffold.GlasmioScaffold
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioButtonSize
import io.github.neoruaa.glasmio.ui.GlasmioCard
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import io.github.neoruaa.glasmio.ui.GlasmioSegmentedControl
import io.github.neoruaa.glasmio.glass.GlasmioToolbarActionStyle
import io.github.neoruaa.glasmio.ui.GlasmioTopBar

/**
 * Glasmio（液态玻璃 / iOS 风格）版主页。
 *
 * 与 `com.zsz.zlivephoto.ui.MainScreen` **参数列表完全一致**（参数名、类型、顺序、默认值），
 * 仅把视觉外壳换成 Glasmio 组件：
 * - 顶栏：[GlasmioTopBar]（标题 + 副标题 + 文件数胶囊 + 设置圆钮）；
 * - 底部控制面板 / 横屏右栏：[GlasmioCard] + [GlasmioTopBar] 同源的玻璃表面；
 * - 按钮：[GlasmioButton] / [GlasmioIconButton]；
 * - 处理方式三选一：[GlasmioSegmentedControl]；
 * - 长按格式菜单、状态详情：[GlasmioAlertDialog] + [GlasmioGroupedList]。
 *
 * 业务回调、状态、列表（侧滑删除 / 长按 / 缩略图进度环）、横竖屏两栏布局、
 * morph 按钮推拉动画、进度与状态文案全部沿用原页面实现。
 * 外层已提供 `GlasmioTheme` / `GlasmioBackdropHost`，此处不再包主题。
 */
@Composable
fun LiquidMainScreen(
    files: SnapshotStateList<FileItem>,
    statusText: String,
    progress: Float,
    progressDetail: String,
    isImporting: Boolean,
    importProgress: Float,
    isConverting: Boolean,
    isPickerOpening: Boolean,
    /** 清空/处理收尾动画播放中：清空按钮禁用 */
    clearBusy: Boolean,
    selectedFormat: String,
    listState: LazyListState,
    onAddFiles: () -> Unit,
    onBatchImport: () -> Unit,
    /** 打开设置页 */
    onOpenSettings: () -> Unit,
    onClearFiles: () -> Unit,
    onConvert: () -> Unit,
    onStopConvert: () -> Unit,
    /** 处理方式（第一版面三选一）：替换原图 / 删除原图 / 保留原图 */
    outputMode: OutputMode,
    /** 处理方式是否可切换：替换原图与删除原图都需「所有文件访问」权限 */
    outputModeUsable: Boolean,
    onSelectOutputMode: (OutputMode) -> Unit,
    /** 长按列表项：单独设置该项输出格式（key 含 "extract"=拆解）；合成任务不提供拆解 */
    onSelectItemFormat: (String, String) -> Unit,
    onRemoveFile: (String) -> Unit
) {
    val haptic = rememberHapticFeedback()
    // 输出格式面板：visible 与「作用对象」分开存（退场动画期间仍要渲染内容）。
    // - formatSheetPath = null → 全局默认格式
    // - formatSheetPath = 某文件路径 → 该项单独格式（长按列表项打开）
    var formatSheetVisible by remember { mutableStateOf(false) }
    var formatSheetPath by remember { mutableStateOf<String?>(null) }
    // 状态详情弹窗（由 BottomControls 的状态胶囊点击打开）
    var showStatusDetail by remember { mutableStateOf(false) }

    // 忙碌态（转换中或导入中）：隐藏三按钮组、进度条显示、转换按钮变形为停止
    val busy = isConverting || isImporting
    val effectiveTarget = selectedFormat

    // 横屏判断以根容器实测尺寸为准（LocalConfiguration 仅作首帧兜底）
    var rootSizePx by remember { mutableStateOf(IntSize.Zero) }
    val isLandscape = if (rootSizePx == IntSize.Zero)
        LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    else rootSizePx.width > rootSizePx.height

    // 页面采样层：应用级自研玻璃弹窗（权限/更新/冲突）据此采样「根 + 本页」
    val glass = LocalGlassEnabled.current
    val pageBackdrop = if (glass) rememberLayerBackdrop() else null
    // FLClash 式模糊：把「列表内容」单独录制一层，只供**绘制在录制层之外**的
    // 顶栏 / 底部玻璃面板采样。玻璃件本身不在该录制层内，因此不存在
    // 「自己采样自己」的反馈循环（rules 里禁止的是把玻璃件录进它自己的采样源）。
    val contentBackdrop = if (glass) rememberGlassLayerBackdrop() else null
    PublishPageBackdrop(pageBackdrop)
    // 页内玻璃件统一采样 GlasmioBackdropHost 的静态采样层。
    // 不再单独录制文件列表：录制滚动内容时采样纹理与绘制帧不同步，
    // 表现就是玻璃「花屏 / 滑动乱闪 / 破面」（用户反馈）；改用宿主采样层后画面稳定。

    // 底部控制面板实测高度：回填给列表 contentPadding，最后一项不会被面板挡住
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val panelHeightDp = with(LocalDensity.current) { panelHeightPx.toDp() }
    // 导航栏高度：玻璃面板自身不再挂 navigationBarsPadding，改由内容底部内边距让位，
    // 面板可一直贴到窗口底边，不会在导航栏上方截出硬边
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // ── 顶栏覆盖层尺寸 ──
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 顶栏内容实测高度（不含状态栏）：onSizeChanged 回填。
    // 首帧兜底必须与库内 GlasmioTopBar(Default) 的真实高度一致（54.dp），
    // 否则首帧用 64.dp 会让列表/面板让位多出 10dp，顶栏量到真实高度后整体跳动。
    val topBarHeightDp = if (topBarHeightPx > 0) with(density) { topBarHeightPx.toDp() } else 54.dp
    // 玻璃带高度 = 状态栏高 + 顶栏内容高：列表/面板让位与覆盖层高度共用同一值
    val chromeHeightDp = statusBarTop + topBarHeightDp
    // 列表滚动驱动的标题可见度：GlasmioTopBar 的 collapseProgress 语义是
    // 「标题可见度」（alpha = progress，progress=0 时标题隐藏并模糊），
    // 因此顶部为 1、向下滚动 120dp 内渐变到 0（必须按 dp 换算成像素：直接减像素值
    // 会让折叠距离随屏幕密度变化，高密度机型几乎瞬间消失、低密度机型拖很久）。
    val titleVisibility by remember(density) {
        val collapseDistancePx = with(density) { 120.dp.toPx() }
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 0f
            else (1f - listState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { rootSizePx = it }
    ) {
        // 页面内容层：应用级自研玻璃弹窗（权限/更新/冲突）据此采样本页；
        // 页内 Glasmio 玻璃件统一采样 GlasmioBackdropHost 的静态层，画面稳定无撕裂。
        Box(
            Modifier
                .fillMaxSize()
                .then(if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier)
        ) {
        GlasmioScaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            // 顶栏改为覆盖层（见下方根 Box 末尾）：不再占用内容空间，列表从玻璃带下穿过
            topBar = {}
        ) { paddingValues ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 横屏两栏布局：左=文件列表，右=控制面板（可滚动）；竖屏保持上下布局
                if (isLandscape) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        FileListArea(
                            files = files,
                            listState = listState,
                            isConverting = isConverting,
                            effectiveTarget = effectiveTarget,
                            onLongPress = { formatSheetPath = it.path; formatSheetVisible = true },
                            onRemoveFile = onRemoveFile,
                            topContentPadding = chromeHeightDp + 8.dp,
                            modifier = Modifier
                                .weight(0.58f)
                                .fillMaxHeight()
                                // 左列补底部安全区，避免最后一项被手势导航条压住
                                .navigationBarsPadding()
                        )
                        Box(
                            Modifier
                                .weight(0.42f)
                                .fillMaxHeight()
                                // 顶栏覆盖层浮在内容之上，右栏整体让出玻璃带高度
                                .padding(top = chromeHeightDp)
                        ) {
                            // 玻璃面板：原来的 GlasmioCard 只是一块不透明 elevatedBackground，
                            // 没有玻璃的法线描边/透镜边缘，控制栏看起来是「一块灰卡」（用户反馈）。
                            // 换成真正的玻璃表面后边缘有折射与高光，内容透出文件列表。
                            GlassSurface(
                                modifier = Modifier
                                    .fillMaxSize(),
                                shape = ContinuousRoundedRectangle(
                                    LocalGlassDimensions.current.regularCornerRadius
                                ),
                                emphasis = GlassEmphasis.Regular,
                                groupSurfaceSeparation = { 1f }
                            ) {
                                BottomControls(
                                    statusText = statusText,
                                    progress = progress,
                                    progressDetail = progressDetail,
                                    isImporting = isImporting,
                                    importProgress = importProgress,
                                    isConverting = isConverting,
                                    isPickerOpening = isPickerOpening,
                                    hasFiles = files.isNotEmpty(),
                                    busy = busy,
                                    clearBusy = clearBusy,
                                    outputMode = outputMode,
                                    outputModeUsable = outputModeUsable,
                                    onSelectOutputMode = onSelectOutputMode,
                                    onAddFiles = onAddFiles,
                                    onBatchImport = onBatchImport,
                                    onClearFiles = onClearFiles,
                                    onConvert = onConvert,
                                    onStopConvert = onStopConvert,
                                    onOpenStatusDetail = { showStatusDetail = true },
                                    // 横屏右栏高度有限（约 250dp 可用），状态区 + 三按钮组
                                    // 自然高度约 330dp：上半部分必须可滚动，否则会被
                                    // GlasmioCard 的圆角裁掉底部动作行。
                                    bodyScrollable = true,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(
                                            start = 16.dp,
                                            top = 12.dp,
                                            end = 16.dp,
                                            bottom = 16.dp + navBarBottom
                                        )
                                )
                            }
                        }
                    }
                } else {
                    // 竖屏：文件列表铺满全屏，底部控制面板为浮动覆盖层，列表从面板下方穿过。
                    // 列表内容录入 contentBackdrop，底部玻璃面板在其外层采样 → 面板下能看到
                    // 被折射/模糊的真实列表内容（FLClash 式模糊），而不是一块纯色磨砂。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (contentBackdrop != null) {
                                    Modifier.glassLayerBackdrop(contentBackdrop)
                                } else Modifier
                            )
                    ) {
                        FileListArea(
                            files = files,
                            listState = listState,
                            isConverting = isConverting,
                            effectiveTarget = effectiveTarget,
                            onLongPress = { formatSheetPath = it.path; formatSheetVisible = true },
                            onRemoveFile = onRemoveFile,
                            topContentPadding = chromeHeightDp + 8.dp,
                            bottomContentPadding = panelHeightDp + 8.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        }

        // 竖屏底部玻璃面板：绘制在 contentBackdrop 录制层之外，采样其下方滚动的列表内容。
        if (!isLandscape && contentBackdrop != null) {
            CompositionLocalProvider(LocalGlassBackdrop provides contentBackdrop) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { panelHeightPx = it.height }
                ) {
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = ContinuousRoundedRectangle(
                            LocalGlassDimensions.current.regularCornerRadius
                        ),
                        emphasis = GlassEmphasis.Regular,
                        groupSurfaceSeparation = { 1f }
                    ) {
                        BottomControls(
                            statusText = statusText,
                            progress = progress,
                            progressDetail = progressDetail,
                            isImporting = isImporting,
                            importProgress = importProgress,
                            isConverting = isConverting,
                            isPickerOpening = isPickerOpening,
                            hasFiles = files.isNotEmpty(),
                            busy = busy,
                            clearBusy = clearBusy,
                            outputMode = outputMode,
                            outputModeUsable = outputModeUsable,
                            onSelectOutputMode = onSelectOutputMode,
                            onAddFiles = onAddFiles,
                            onBatchImport = onBatchImport,
                            onClearFiles = onClearFiles,
                            onConvert = onConvert,
                            onStopConvert = onStopConvert,
                            onOpenStatusDetail = { showStatusDetail = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 16.dp,
                                    top = 12.dp,
                                    end = 16.dp,
                                    bottom = 16.dp + navBarBottom
                                )
                        )
                    }
                }
            }
        }

        // ── 顶栏覆盖层（根 Box 内绘制在列表/面板之上；位于采样录制层之外）──
        // 列表从玻璃带下方穿过：渐变收边（无硬分隔线）+ 状态栏内边距 + 顶栏内容。
        // 玻璃模式下底色取根渐变底色并留半透明让底图透出；非玻璃模式用主题背景色。
        val veilBase = if (glass) LocalGlassRootTint.current else MaterialTheme.colorScheme.background
        val veilTop = if (glass) veilBase.copy(alpha = 0.88f) else veilBase
        // 注意：这一层**不能固定 height(chromeHeightDp)**。
        // 冷启动首帧 statusBars inset 可能还没派发（为 0），chromeHeightDp 只有 54dp，
        // 而顶栏内容实际是「状态栏 + 54dp」：外层一裁，顶栏只剩上面十几 dp，
        // 表现就是标题只剩一条、设置按钮垂直被压扁（切页回来 inset 到位后恢复正常）。
        // 因此只让背景渐变按 chromeHeightDp 铺，顶栏内容用 wrapContentHeight 自适应。
        // 顶栏玻璃件改为采样「列表内容录制层」，标题悬浮在滚动内容上时能看到 FLClash 式模糊/折射。
        val chromeBackdrop = contentBackdrop ?: LocalGlassBackdrop.current
        CompositionLocalProvider(LocalGlassBackdrop provides chromeBackdrop) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(chromeHeightDp)
                    .background(
                        // 顶栏玻璃带：单调渐变到透明，中间不再插“半透明加深”停靠点，
                        // 否则会在 0.72 处出现一条可见的色带（与页面底色深浅不一致）
                        Brush.verticalGradient(
                            0f to veilTop,
                            1f to Color.Transparent
                        )
                    )
            )
            // onSizeChanged 放在 statusBarsPadding 之后：只量顶栏内容高（不含状态栏），
            // 玻璃带总高由「状态栏高 + 顶栏内容高」合成
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .onSizeChanged { topBarHeightPx = it.height }
            ) {
                GlasmioTopBar(
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.screen_app_subtitle),
                    collapseProgress = titleVisibility,
                    // Merged 会把组内控件合并成一个表面：本节只有一个设置按钮，
                    // 合并组首帧宽度为 0，按钮会先画成扁平方块、重组后才变成圆形。
                    // 用 Separated 让按钮从一开始就是独立圆形玻璃。
                    actionsStyle = GlasmioToolbarActionStyle.Separated,
                    // 库内部会用一个 spring 把 separationProgress 从 0 动画到 1，
                    // 首帧 0 = 合并胶囊（宽>高的椭圆）。显式给 1f 让首帧就是分离圆形。
                    actionsSeparationProgress = 1f,
                    actions = {
                        // 顶栏不再显示文件数胶囊：它会挤压居中标题（库的 fitsCentered
                        // 判断到标题放不下就把它挤到右侧），也会让标题看起来被截断。
                        // 文件数量在列表/底部状态区已有呈现。
                        // 顶栏图标显式指定玻璃档：Glasmio 只有 Regular（常规玻璃）/ Prominent（强调玻璃），
                        // Regular 即库默认玻璃档，不是扁平样式；backdrop 用当前玻璃采样层，
                        // 避免落到无采样层时只画一个纯色容器（看起来就是扁平按钮）
                        GlasmioIconButton(
                            onClick = {
                                haptic.click()
                                onOpenSettings()
                            },
                            backdrop = LocalGlassBackdrop.current,
                            emphasis = GlassEmphasis.Regular,
                            // 关键：按钮自身是否绘制玻璃面取决于 groupSurfaceSeparation，
                            // 而不是 TopBar 的 actionsSeparationProgress。首帧 CompositionLocal
                            // 还没被 action group 更新时它是 0 → 按钮不画自己、改由 group 的
                            // 合并表面绘制，而合并表面首帧测量宽度为 0，看起来就是「扁平的椭圆」，
                            // 第二帧才恢复圆形。显式给 1f，让按钮首帧就画自己的圆形玻璃。
                            groupSurfaceSeparation = { 1f }
                        ) {
                            SfIcon(
                                "gearshape",
                                contentDescription = stringResource(R.string.screen_settings),
                                size = 20.dp
                            )
                        }
                    }
                )
            }
        }
        }

        // ── 长按列表项：该项输出格式菜单（按路径从 files 实时读取，勾选与真实生效格式一致）──
        val formatSheetItem = formatSheetPath?.let { p -> files.firstOrNull { it.path == p } }
        GlasmioAlertDialog(
            visible = formatSheetVisible && formatSheetItem != null,
            onDismissRequest = { formatSheetVisible = false },
            title = stringResource(R.string.screen_item_format_title),
            message = formatSheetItem?.name,
            buttons = listOf(
                GlasmioAlertButtonSpec(
                    label = stringResource(R.string.screen_close),
                    role = GlasmioAlertButtonRole.Cancel,
                    onClick = { formatSheetVisible = false }
                )
            )
        ) {
            val item = formatSheetItem
            if (item != null) {
                val currentKey = item.targetFormat ?: selectedFormat
                // 逐项输出格式：按该项来源导入方式过滤，逻辑上不可能的格式直接不列出
                val itemFormats = when (item.originMode) {
                    "EXTRACT" -> formatOptions.filter { it.key == "extract" }
                    "COMPOSE", "VIDEO", "LIVE" -> formatOptions.filter { it.key != "extract" }
                    else -> when (item.formatKey) {
                        "compose", "video" -> formatOptions.filter { it.key != "extract" }
                        else -> formatOptions
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.62f)
                        .verticalScroll(rememberScrollState())
                ) {
                    GlasmioGroupedList {
                        itemFormats.forEach { opt ->
                            GlasmioListRow(
                                title = stringResource(opt.nameRes),
                                onClick = {
                                    onSelectItemFormat(item.path, opt.key)
                                    formatSheetVisible = false
                                },
                                trailing = {
                                    if (opt.key == currentKey) {
                                        SfIcon(
                                            "checkmark",
                                            contentDescription = null,
                                            size = 20.dp,
                                            tint = LocalGlassColors.current.accent
                                        )
                                    }
                                }
                            )
                        }
                    }
                    if (currentKey == "vivo_single") {
                        Text(
                            text = stringResource(R.string.screen_vivo_single_warning),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // ── 状态详情（完整内容，由「复制」按钮整段复制）──
        // LocalClipboardManager 的替代品 LocalClipboard 是 suspend API，
        // 此处为同步复制场景，保留旧 API 并抑制弃用警告
        @Suppress("DEPRECATION")
        val statusClipboard = LocalClipboardManager.current
        GlasmioAlertDialog(
            visible = showStatusDetail,
            onDismissRequest = { showStatusDetail = false },
            title = stringResource(R.string.screen_status_detail),
            buttons = listOf(
                GlasmioAlertButtonSpec(
                    label = stringResource(R.string.screen_copy),
                    role = GlasmioAlertButtonRole.Cancel,
                    onClick = {
                        haptic.click()
                        statusClipboard.setText(AnnotatedString(statusText))
                        // 复制后直接关闭弹窗（用户反馈：复制完还要再点一次关闭）
                        showStatusDetail = false
                    }
                ),
                GlasmioAlertButtonSpec(
                    label = stringResource(R.string.screen_close),
                    onClick = {
                        haptic.click()
                        showStatusDetail = false
                    }
                )
            )
        ) {
            // 正文字体/颜色/内边距与「更新说明」弹窗的 Markdown 正文保持一致（bodyMedium + onSurface + 顶部 4dp），
            // 两处文本左缘与基线对齐；Glass 文本选择手柄由外层 GlasmioTextSelectionHost 接管。
            // 正文限高并可滚动：太长时不会把「复制 / 关闭」按钮顶出屏幕。
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.5f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 文件列表区域（含空状态装饰）：竖屏占上下布局的上部，横屏占左右布局的左栏。
 * 列表项沿用原 [FileCard]（侧滑删除、长按、缩略图转码进度环为自研交互，无 Glasmio 等价物）。
 */
@Composable
private fun FileListArea(
    files: SnapshotStateList<FileItem>,
    listState: LazyListState,
    isConverting: Boolean,
    effectiveTarget: String,
    onLongPress: (FileItem) -> Unit,
    onRemoveFile: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 顶部让位：顶栏为覆盖层，首项从玻璃带下方开始 */
    topContentPadding: Dp = 8.dp,
    /** 底部让位：竖屏底部控制面板为浮动覆盖层，用 contentPadding 预留面板高度 */
    bottomContentPadding: Dp = 8.dp
) {
    Box(modifier = modifier) {
        // 文件由空变非空时回到列表顶部，避免复用上一次列表的滚动位置
        LaunchedEffect(files.isNotEmpty()) {
            if (files.isNotEmpty()) listState.scrollToItem(0)
        }
        // 空态与列表之间做淡入淡出，避免首次导入/清空时画面硬跳
        Crossfade(
            targetState = files.isEmpty(),
            animationSpec = tween(180),
            label = "fileListCrossfade"
        ) { empty ->
            if (empty) {
            val primaryColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 扣掉玻璃带与底部面板高度，空态在两者之间保持视觉居中
                    .padding(top = topContentPadding, bottom = bottomContentPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 涟漪同心圆装饰（呼应应用图标）
                    Box(
                        modifier = Modifier.size(168.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val c = this.center
                            val maxR = this.size.minDimension / 2f
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.08f),
                                radius = maxR
                            )
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.14f),
                                radius = maxR * 0.76f
                            )
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.22f),
                                radius = maxR * 0.52f
                            )
                        }
                        // 空态图标：玻璃开时用一块真正的玻璃（折射 + 顶边高光 + 外描边），
                        // 关闭玻璃才退回原来的主色渐变方块
                        if (LocalGlassEnabled.current) {
                            GlassSurface(
                                modifier = Modifier.size(88.dp),
                                shape = ContinuousRoundedRectangle(28.dp),
                                emphasis = GlassEmphasis.Regular,
                                groupSurfaceSeparation = { 1f }
                            ) {
                                SfIcon(
                                    "photo.on.rectangle",
                                    contentDescription = null,
                                    size = 44.dp,
                                    tint = primaryColor
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                SfIcon(
                                    "photo.on.rectangle",
                                    contentDescription = null,
                                    size = 44.dp,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.screen_empty_no_files),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                state = listState,
                contentPadding = PaddingValues(
                    top = topContentPadding,
                    bottom = bottomContentPadding
                )
            ) {
                items(files, key = { it.path }) { item ->
                    // 项间距放在项内部：移除动画塌陷时连同间距一起收起；
                    // 剩余项补位为弹簧平移（animateItem placement），可被新的侧滑打断。
                    // 阻尼接近临界、刚度放低：删除后上方项目柔和地补位，不再「弹一下」。
                    Box(
                        Modifier.animateItem(
                            placementSpec = spring(
                                dampingRatio = 0.92f,
                                stiffness = 300f
                            )
                        )
                    ) {
                        Column {
                            FileCard(
                                item = item,
                                isConverting = isConverting,
                                // 逐项格式：优先本项 targetFormat，未设置时回退全局 selectedFormat
                                sameFormat = item.formatKey != null &&
                                    item.formatKey == (item.targetFormat ?: effectiveTarget),
                                onLongPress = { onLongPress(item) },
                                onDelete = { path -> onRemoveFile(path) },
                                // normal flavor：卡片表面换成液态玻璃面（半透明 + 白色高光细边），
                                // 不再用 GlasmioCard 的不透明 elevatedBackground——那样和 MD3 卡片
                                // 观感几乎一样，列表看起来没有切到新 UI。
                                cardContainer = { mod, content ->
                                    val glassColors = LocalGlassColors.current
                                    Box(
                                        mod
                                            .clip(ContinuousRoundedRectangle(22.dp))
                                            .background(glassColors.elevatedBackground.copy(alpha = 0.72f))
                                            .border(
                                                width = 0.75.dp,
                                                color = glassColors.subtleStroke,
                                                shape = ContinuousRoundedRectangle(22.dp)
                                            )
                                    ) { content() }
                                },
                                // 进度条统一用玻璃样式，避免卡内出现 Material 进度条
                                progressBar = { fraction ->
                                    LiquidProgressBar(
                                        progress = fraction,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            }
        }
    }
}

/**
 * 底部控制面板内容：状态胶囊 + 处理方式三选一 + 三按钮组 + 清空/转换 morph 行。
 * 竖屏为底部浮动面板，横屏为右侧栏（外层负责滚动）。
 */
@Composable
private fun BottomControls(
    statusText: String,
    progress: Float,
    progressDetail: String,
    isImporting: Boolean,
    importProgress: Float,
    isConverting: Boolean,
    isPickerOpening: Boolean,
    hasFiles: Boolean,
    busy: Boolean,
    clearBusy: Boolean,
    outputMode: OutputMode,
    outputModeUsable: Boolean,
    onSelectOutputMode: (OutputMode) -> Unit,
    onAddFiles: () -> Unit,
    onBatchImport: () -> Unit,
    onClearFiles: () -> Unit,
    onConvert: () -> Unit,
    onStopConvert: () -> Unit,
    /** 打开状态详情弹窗（弹窗渲染在 LiquidMainScreen 作用域，故由外层持有状态） */
    onOpenStatusDetail: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 横屏右栏：状态区与三按钮组放进「weight(1f) + verticalScroll」的独立滚动区，
     * 底部动作行（清空 / 开始转换）固定贴底不随滚动。
     */
    bodyScrollable: Boolean = false
) {
    val haptic = rememberHapticFeedback()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 上半部分（状态区 + 三按钮组）抽成局部 lambda：竖屏直接铺开，
        // 横屏装进滚动容器（内部使用 weight，不能放在同一 Column 的滚动区里）。
        val body: @Composable ColumnScope.() -> Unit = {
        // 状态胶囊（带彩色状态点，点击查看完整详情）：
        // 忙碌时展开进度条 + 明细，进度与明细常驻胶囊内。
        // i18n：**不能按中文字面量匹配**——按本语言资源文案做前缀/整串匹配。
        val statusColor = run {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            fun matchesRaw(raw: String): Boolean {
                if (!raw.contains('%')) return statusText == raw
                val kw = raw.substringBefore('%').trim().trimEnd('，', ',', '；', ';', ':', '：', '。', '.', '！', '!')
                return kw.length >= 2 && statusText.contains(kw)
            }
            fun matches(id: Int) = matchesRaw(ctx.getString(id))
            // <plurals> 取 count=2 的形态（中文只有 other；其它语言取一个常见复数形即可用于前缀匹配）
            fun matchesPlural(id: Int) = matchesRaw(ctx.resources.getQuantityString(id, 2))
            val isError = listOf(
                R.string.main_status_import_failed, R.string.main_status_folder_unavailable,
                R.string.main_status_folder_unreadable, R.string.main_status_compose_no_photo,
                R.string.main_status_compose_no_video, R.string.main_status_nothing_to_convert,
                R.string.main_status_no_images, R.string.main_status_delete_needs_all_files,
                R.string.main_status_replace_needs_all_files
            ).any { matches(it) }
            val isReady = statusText == ctx.getString(R.string.main_status_ready)
            val isDone = listOf(
                R.string.main_status_import_done_partial,
                R.string.main_status_import_stopped, R.string.main_status_removed,
                R.string.main_status_cleared, R.string.main_status_replace_off,
                R.string.main_status_replace_off_mutex, R.string.main_status_delete_off_mutex,
                R.string.main_status_compose_all_added, R.string.main_status_all_files_granted,
                R.string.main_status_location_granted, R.string.main_status_read_granted,
                R.string.main_status_auto_google, R.string.main_status_done_export,
                R.string.main_status_done_replace, R.string.main_status_done_export_reason,
                R.string.main_status_done_replace_reason, R.string.main_status_stopped_export,
                R.string.main_status_stopped_replace
            ).any { matches(it) } || listOf(
                R.plurals.main_status_import_done, R.plurals.main_status_batch_done,
                R.plurals.main_status_batch_done_skipped, R.plurals.main_status_batch_stopped,
                R.plurals.main_status_batch_stopped_skipped
            ).any { matchesPlural(it) }
            when {
                busy -> Color(0xFF1E88E5)
                isError -> MaterialTheme.colorScheme.error
                isReady -> Color(0xFF34A853)
                isDone -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
        // 状态区直接画在底部面板这张玻璃卡里：不再嵌套第二张 GlasmioCard。
        // 玻璃套玻璃会让内层二次提亮/加深，并各自带一道阴影 → 深浅不一致、卡片边缘出现断层。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    haptic.click()
                    onOpenStatusDetail()
                }
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // 长文本显示不完全时，给出可展开的提示箭头
                if (statusText.length > 40) {
                    SfIcon(
                        "chevron.right",
                        contentDescription = stringResource(R.string.screen_view_detail),
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 进度条 + a/b 明细（转换/导入/批量均显示在胶囊内）
            AnimatedVisibility(
                visible = busy,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    LiquidProgressBar(
                        progress = if (isImporting) importProgress else progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (progressDetail.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = progressDetail,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 三按钮组（添加文件 / 批量处理 / 处理方式）：
        // 忙碌时下滑与停止按钮重合后隐藏（面板高度同步收缩，控件实时下移）；
        // 恢复时从底部贝塞尔曲线动画上移（0.5s，FancyEasing）
        AnimatedVisibility(
            visible = !busy,
            enter = slideInVertically(tween(500, easing = FancyEasing)) { it } +
                fadeIn(tween(300, 100)) + expandVertically(tween(500, easing = FancyEasing)),
            exit = slideOutVertically(tween(500, easing = FancyEasing)) { it } +
                fadeOut(tween(300)) + shrinkVertically(tween(500, easing = FancyEasing))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 浅色模式：主页背景是纯白，默认导航材质会把采样底压暗成灰色；
                // 这里显式传入玻璃容器色，让按钮保持干净的白色玻璃（深色模式不变）。
                val homeGlassColors = LocalGlassColors.current
                val lightSurfaceTint =
                    if (homeGlassColors.isDark) null else homeGlassColors.container
                // 添加文件 + 批量导入（平分空间；处理中禁用）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlasmioButton(
                        onClick = { onAddFiles() },
                        // 选择器打开流程（读相册）完成前禁用，防止重入引发扫描竞态崩溃
                        enabled = !busy && !isPickerOpening,
                        size = GlasmioButtonSize.Large,
                        navigationSurfaceColor = lightSurfaceTint,
                        modifier = Modifier.weight(1f)
                    ) {
                        SfIcon("photo.badge.plus", contentDescription = null, size = 18.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.screen_add_files),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    GlasmioButton(
                        onClick = { onBatchImport() },
                        enabled = !busy,
                        size = GlasmioButtonSize.Large,
                        navigationSurfaceColor = lightSurfaceTint,
                        modifier = Modifier.weight(1f)
                    ) {
                        SfIcon("arrow.triangle.2.circlepath", contentDescription = null, size = 18.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.screen_batch_import),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 处理方式（第一版面）：三选一（替换原图 / 删除原图 / 保留原图）。
                // 替换与删除均需「所有文件访问」权限：无权限时点击仍会触发授权引导，
                // 权限是否具备由下方提示行说明。
                val outputModes = listOf(OutputMode.REPLACE, OutputMode.DELETE, OutputMode.KEEP)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.screen_mode_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    GlasmioSegmentedControl(
                        items = listOf(
                            OutputMode.REPLACE to stringResource(R.string.screen_mode_replace),
                            OutputMode.DELETE to stringResource(R.string.screen_mode_delete),
                            OutputMode.KEEP to stringResource(R.string.screen_mode_keep)
                        ),
                        selected = outputMode,
                        onSelected = { onSelectOutputMode(it) },
                        enabled = !busy
                    )
                    // 选项本身已足够表意，不再附一行解释小字（底部空间紧张，且文案冗余）
                    if (!outputModeUsable) {
                        Text(
                            text = stringResource(R.string.screen_mode_need_access),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }

        } // body 结束
        if (bodyScrollable) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                body()
            }
        } else {
            body()
        }
        // 清空 + 开始转换/停止处理 morph 行：
        // 点击开始转换后，转换按钮以贝塞尔曲线（0.5s）平滑拉伸占据清空按钮位置；
        // 内容以 0.3s 淡入淡出整体切换为停止图标（实心方块）+「停止处理」；
        // 处理完成/停止后按相同动画反向还原。
        // 抽成独立 composable：morph 动画 500ms 内的每帧重组被限制在本行。
        MorphActionRow(
            busy = busy,
            hasFiles = hasFiles,
            clearBusy = clearBusy,
            onClearFiles = onClearFiles,
            onConvert = onConvert,
            onStopConvert = onStopConvert
        )
    }
}

/**
 * 清空 + 开始转换/停止处理 morph 行（独立 composable，隔离 morph 动画的重组范围）。
 */
@Composable
private fun MorphActionRow(
    busy: Boolean,
    hasFiles: Boolean,
    clearBusy: Boolean,
    onClearFiles: () -> Unit,
    onConvert: () -> Unit,
    onStopConvert: () -> Unit
) {
    val morph by animateFloatAsState(
        targetValue = if (busy) 1f else 0f,
        animationSpec = tween(500, easing = FancyEasing),
        label = "morph"
    )
    // 清空按钮的显隐也走同一条 500ms 贝塞尔时间线
    val clearProgress by animateFloatAsState(
        targetValue = if (hasFiles) 1f else 0f,
        animationSpec = tween(500, easing = FancyEasing),
        label = "clearProgress"
    )
    // extend = 清空按钮展开量（0=收起）。busy 收拢与 hasFiles 显隐两条
    // 时间线相乘互不干扰：有文件+空闲 → extend=1（clear 1.001 / convert 1.6）；
    // busy → extend=0（convert 2.6）
    val extend = clearProgress * (1f - morph)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy((8f * extend).dp)
    ) {
        // 清空（Glasmio 破坏性按钮；随 morph/清空进度淡出并让位）
        Box(modifier = Modifier.weight(extend + 0.001f)) {
            // 完全收起后不再渲染玻璃按钮（alpha=0 的玻璃按钮仍会逐帧算模糊/折射）
            if (extend > 0.02f) {
                GlasmioButton(
                    onClick = onClearFiles,
                    enabled = !busy && !clearBusy && hasFiles,
                    size = GlasmioButtonSize.Large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(extend),
                    destructive = true
                ) {
                    SfIcon("trash", contentDescription = null, size = 20.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.screen_clear))
                }
            }
        }

        // 主按钮（品牌色玻璃）；与清空按钮互补推拉，动画过程中仍可点击
        GlasmioButton(
            onClick = {
                if (busy) onStopConvert() else onConvert()
            },
            size = GlasmioButtonSize.Large,
            modifier = Modifier
                .weight(2.6f - extend)
        ) {
            AnimatedContent(
                targetState = busy,
                transitionSpec = {
                    (fadeIn(tween(300, 100, easing = AlphaEasing))) togetherWith
                        (fadeOut(tween(300, easing = AlphaEasing)))
                },
                label = "convertBtn"
            ) { converting ->
                // 文字和图标作为一个整体，按钮内居中显示
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (converting) {
                        SfIcon("stop.fill", contentDescription = null, size = 20.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.screen_stop), fontWeight = FontWeight.Bold)
                    } else {
                        SfIcon("bolt", contentDescription = null, size = 20.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.screen_start_convert), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

