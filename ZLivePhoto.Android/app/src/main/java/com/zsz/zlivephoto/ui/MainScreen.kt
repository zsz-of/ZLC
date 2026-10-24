package com.zsz.zlivephoto.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.ui.glass.GlassActionButton
import com.zsz.zlivephoto.ui.glass.GlassBottomSheet
import com.zsz.zlivephoto.ui.glass.GlassFusionScope
import com.zsz.zlivephoto.ui.glass.GlassIconButton
import com.zsz.zlivephoto.ui.glass.GlassTopBar
import com.zsz.zlivephoto.ui.glass.LiquidDialog
import com.zsz.zlivephoto.ui.glass.LiquidSegmentedTabs
import com.zsz.zlivephoto.ui.glass.LocalGlassBackdrop
import com.zsz.zlivephoto.ui.glass.LocalGlassEnabled
import com.zsz.zlivephoto.ui.glass.PublishPageBackdrop
import com.zsz.zlivephoto.ui.glass.TopBarFrost
import com.zsz.zlivephoto.ui.glass.liquidGlass
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

data class FileItem(
    val path: String,
    val name: String,
    val info: String,
    val isUnrecognized: Boolean = false,
    val sourceUri: String? = null,
    val sourcePath: String? = null,
    val sourceTime: Long = 0L,
    val sourceTaken: Long = 0L,
    /** 识别出的格式（plugin.name），用于「目标=输入」蓝色提示 */
    val formatKey: String? = null,
    /**
     * 本项的输出格式（plugin.name / "extract"）；null = 未单独设置，回退到全局 [selectedFormat]。
     * 由「长按列表项 → 选择输出格式」写入，实现「格式全部分别设置」。
     */
    val targetFormat: String? = null,
    /** 合成任务：配对视频路径（formatKey=compose 时非空） */
    val composeVideoPath: String? = null,
    /** 正在用 ffmpeg 转码配对视频：列表项内展开显示转码进度 */
    val transcoding: Boolean = false,
    /** 转码已处理帧数 */
    val transcodeFrame: Long = 0L,
    /** 转码总帧数（<=0 表示未知，此时不显示百分比） */
    val transcodeTotal: Long = 0L,
    /** 转码预计剩余秒数（<0 表示未知） */
    val transcodeEtaSec: Long = -1L
)

/**
 * 输出格式选项：[nameRes] 为展示名资源 id —— 用户可见文案统一放 res/values/strings_*.xml，
 * 便于 119 语种翻译（本文件内不再出现硬编码文案）。
 */
data class FormatOption(
    val key: String,
    @StringRes val nameRes: Int
)

/**
 * 处理方式（第一版面三选一，互斥）：
 * - [REPLACE] 替换原图：转换结果直接写回源文件本体（破坏性，需「所有文件访问」）
 * - [DELETE]  删除原图：处理完成后自动删除原图（需「所有文件访问」）
 * - [KEEP]    保留原图：不删除、不替换（默认）
 */
enum class OutputMode { REPLACE, DELETE, KEEP }

val formatOptions = listOf(
    FormatOption("google", R.string.screen_format_google),
    FormatOption("apple", R.string.screen_format_apple),
    FormatOption("oppo", R.string.screen_format_oppo),
    FormatOption("vivo_single", R.string.screen_format_vivo_single),
    FormatOption("vivo", R.string.screen_format_vivo),
    FormatOption("xiaomi", R.string.screen_format_xiaomi),
    FormatOption("honor", R.string.screen_format_honor),
    FormatOption("meizu", R.string.screen_format_meizu),
    FormatOption("samsung", R.string.screen_format_samsung),
    FormatOption("extract", R.string.screen_format_extract),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
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
    // 替换原图首次开启：先弹风险说明，确认后才真正开启
    var pendingReplaceEnable by remember { mutableStateOf(false) }
    // 处理方式选择「被拒」回弹信号：风险弹窗未确认（外部选中值未变，但玻璃块
    // 已被拖/点过去）时 +1，让 LiquidSegmentedTabs 指示器弹回原选项
    // （用户反馈：拖到替换选项后不确认也不会弹回原来的选项，不行）
    var modeRevertTick by remember { mutableIntStateOf(0) }
    // 长按某项：该项输出格式选择弹窗（null = 未打开）
    var formatSheetItem by remember { mutableStateOf<FileItem?>(null) }
    // 状态详情弹窗（由 BottomControls 的状态胶囊点击打开；弹窗渲染在覆盖层作用域，
    // 故状态提升到本函数——竖屏面板与横屏右栏共用同一个 BottomControls）
    var showStatusDetail by remember { mutableStateOf(false) }

    // 忙碌态（转换中或导入中）：隐藏三按钮组、进度条显示、转换按钮变形为停止
    val busy = isConverting || isImporting

    // 实际转换目标：vivo 已拆分为两个顶级选项（vivo_single / vivo），直接使用
    val effectiveTarget = selectedFormat

    // 队列含合成任务（formatKey=compose）时禁止选择「拆解」：拆解与合成语义互斥，
    // 合成任务会把照片+视频合成动态照片，而拆解是反向操作，混用会产出不可识别产物
    val hasComposeTask = files.any { it.formatKey == "compose" }

    /** 处理方式单选点击：替换原图首次开启先弹风险说明；无权限时交给 Activity 引导授权 */
    val selectMode: (OutputMode) -> Unit = { mode ->
        if (mode == OutputMode.REPLACE && !AppSettings.isReminderSuppressed(ReminderKey.REPLACE_MODE_RISK)) {
            pendingReplaceEnable = true
        } else {
            onSelectOutputMode(mode)
        }
    }

    // 横屏判断（提升到函数级，标题限宽与两栏布局共用）
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    // 液态玻璃生效时容器一律透明：采样源渐变由根节点 GlassRoot 铺在内容之下，
    // 页面容器不透明会把它整块遮住，玻璃元素就无从采样（返回原生 MD3 时保持原配色）
    val glass = LocalGlassEnabled.current
    // 采样层：把整页内容（列表缩略图、控制面板）记录一份，底部玻璃面板/长按菜单折射的是
    // 真实内容而不是装饰渐变。玻璃元素本身必须在被记录节点之外（下面的覆盖层即是）。
    val pageBackdrop = if (glass) rememberLayerBackdrop() else null
    // 发布给应用级弹窗（权限/更新/冲突，渲染在本页之外）采样「根 + 本页」：
    // 弹窗与页面同窗口，坐标天然对正、模糊实时（用户反馈 #5/#6），且复用本录制零开销
    PublishPageBackdrop(pageBackdrop)

    // 底部控制面板（竖屏浮动覆盖层）的实测高度：回填给列表 contentPadding，
    // 列表因此能铺到屏幕最底、从面板下方穿过，最后一项也不会被面板挡住
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val panelHeightDp = with(LocalDensity.current) { panelHeightPx.toDp() }

    // 顶栏（覆盖层渲染）：透明容器，模糊由 TopBarFrost 铺在它背后。
    // 玻璃形态用 GlassTopBar（普通 Row，不裁剪），避免玻璃圆钮的投影在顶栏下沿被切掉。
    val mainTopBarTitle: @Composable () -> Unit = {
                    Row(
                        // 横屏标题只占左半边，不延伸到右侧控制区域上方
                        modifier = Modifier.fillMaxWidth(if (isLandscape) 0.58f else 1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 品牌 Logo 徽标（与图标同款渐变）
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
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
                            // 标志色跟随主题 onPrimary：深色模式下渐变底变浅，onPrimary 深色仍清晰
                            val markColor = MaterialTheme.colorScheme.onPrimary
                            Canvas(modifier = Modifier.size(22.dp)) {
                                val c = this.center
                                drawCircle(color = markColor, radius = 2.5.dp.toPx(), center = c)
                                drawCircle(
                                    color = markColor,
                                    radius = 5.5.dp.toPx(),
                                    center = c,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx())
                                )
                                drawCircle(
                                    color = markColor.copy(alpha = 0.6f),
                                    radius = 9.dp.toPx(),
                                    center = c,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4.dp.toPx())
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.screen_app_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
    }
    val mainTopBarActions: @Composable RowScope.() -> Unit = {
                    if (files.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.screen_file_count, files.size, files.size
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    // 设置入口（玻璃圆钮：与返回按钮同款，含拖拽回弹）
                    GlassIconButton(
                        onClick = onOpenSettings,
                        contentDescription = stringResource(R.string.screen_settings),
                        adaptiveContent = true
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
    }
    val mainTopBar: @Composable () -> Unit = {
        if (glass) {
            GlassTopBar(
                title = mainTopBarTitle,
                titleStartPadding = 8.dp,
                actions = mainTopBarActions
            )
        } else {
            TopAppBar(
                title = mainTopBarTitle,
                actions = mainTopBarActions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .fillMaxSize()
            .then(if (pageBackdrop != null) Modifier.layerBackdrop(pageBackdrop) else Modifier)
    ) {

    // 顶栏总高度（状态栏 + 64dp TopAppBar）：列表用 contentPadding 让位，
    // 保证内容从屏幕顶部绘制、滚动时真正穿过顶栏下方
    val topBarTotal = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp

    Scaffold(
        containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.background,
        // 顶栏改由覆盖层 TopBarFrost 渲染：内容从屏幕顶部开始，滚动时从顶栏下方穿过，
        // 顶栏只叠加「上强下弱」的渐进高斯模糊，与滚动列表区分开。
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        // 横屏两栏布局：左=文件列表，右=控制面板（可滚动、避免挤压错乱）；
        // 竖屏保持原有上下布局
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                FileListArea(
                    files = files,
                    listState = listState,
                    isConverting = isConverting,
                    effectiveTarget = effectiveTarget,
                    onLongPress = { formatSheetItem = it },
                    onRemoveFile = onRemoveFile,
                    topContentPadding = topBarTotal + 8.dp,
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                )
                // 右侧控制面板：玻璃生效时做成液态玻璃浮动面板（否则原 MD3 容器色）
                val panelShape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                GlassFusionScope {
                Surface(
                    color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = if (glass) 0.dp else 2.dp,
                    shape = panelShape,
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        // 顶栏是覆盖层：右侧面板从顶栏下方开始，避免标题压在面板上
                        .padding(top = topBarTotal)
                        .navigationBarsPadding()
                        .then(if (glass) Modifier.liquidGlass(panelShape) else Modifier)
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
                        onSelectOutputMode = selectMode,
                        modeRevertTick = modeRevertTick,
                        onAddFiles = onAddFiles,
                        onBatchImport = onBatchImport,
                        onClearFiles = onClearFiles,
                        onConvert = onConvert,
                        onStopConvert = onStopConvert,
                        onOpenStatusDetail = { showStatusDetail = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                }
            }
        } else {
            // 竖屏：文件列表铺满全屏。底部控制面板改为**浮动覆盖层**（见下方
            // 「底部控制面板」浮层），列表从面板下方穿过——面板玻璃采样
            // 「根渐变 + 页面内容」的合成层，折射的是真实列表而不是装饰渐变
            // （用户反馈：「液态玻璃的模糊还是要包含他下面的所有层」）。
            // 列表底部留白 = 面板实测高度，最后一项不会被面板挡住。
            FileListArea(
                files = files,
                listState = listState,
                isConverting = isConverting,
                effectiveTarget = effectiveTarget,
                onLongPress = { formatSheetItem = it },
                onRemoveFile = onRemoveFile,
                topContentPadding = topBarTotal + 8.dp,
                bottomContentPadding = panelHeightDp + 8.dp,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    } // 页面内容（采样层）

    // 顶栏覆盖层（在采样层之外）：滚动内容从顶栏下方穿过，顶栏叠加渐进高斯模糊
    CompositionLocalProvider(
        LocalGlassBackdrop provides (pageBackdrop ?: LocalGlassBackdrop.current)
    ) {
        TopBarFrost(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            mainTopBar()
        }
    }

    // 覆盖层：底部面板 / 菜单压在被记录的页面之上，玻璃折射的是真实内容
    CompositionLocalProvider(
        LocalGlassBackdrop provides (pageBackdrop ?: LocalGlassBackdrop.current)
    ) {

    // ── 底部控制面板（竖屏浮动覆盖层；横屏为右侧栏，见上方布局分支）──
    // 在采样层之外：面板玻璃通过 [rememberGlassBackdrop] 采样「根渐变 + 页面内容」
    // 的合成层，列表从面板下方穿过，折射的是真实列表内容。
    // 实测高度回填给列表 contentPadding（panelHeightPx），两者形成闭环。
    if (!isLandscape) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { panelHeightPx = it.height }
        ) {
            val panelShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            GlassFusionScope {
            Surface(
                color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = if (glass) 0.dp else 2.dp,
                shape = panelShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .then(if (glass) Modifier.liquidGlass(panelShape) else Modifier)
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
                    onSelectOutputMode = selectMode,
                    modeRevertTick = modeRevertTick,
                    onAddFiles = onAddFiles,
                    onBatchImport = onBatchImport,
                    onClearFiles = onClearFiles,
                    onConvert = onConvert,
                    onStopConvert = onStopConvert,
                    onOpenStatusDetail = { showStatusDetail = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            }
        }
    }

    // 长按列表项 → 单项输出格式选择（含「拆解」；合成任务不提供拆解）
    GlassBottomSheet(
        visible = formatSheetItem != null,
        onDismiss = { formatSheetItem = null }
    ) {        val item = formatSheetItem ?: return@GlassBottomSheet
        val formatScrollState = rememberScrollState()
        val itemKey = item.formatKey
            Text(
                text = stringResource(R.string.screen_item_format_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
            )
            HorizontalDivider()
            val maxListHeight = LocalConfiguration.current.screenHeightDp.dp * 0.62f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight)
                    .verticalScroll(formatScrollState)
            ) {
                formatOptions.forEach { opt ->
                    val current = item.targetFormat ?: selectedFormat
                    val selected = opt.key == current
                    // 拆解与合成互斥：本条为合成任务（formatKey=compose）时不可选拆解
                    val disabled = opt.key == "extract" && itemKey == "compose"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when {
                                    selected -> MaterialTheme.colorScheme.secondaryContainer
                                    disabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                }
                            )
                            .clickable(enabled = !disabled) {
                                haptic.click()
                                onSelectItemFormat(item.path, opt.key)
                                formatSheetItem = null
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(opt.nameRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                selected -> MaterialTheme.colorScheme.onSecondaryContainer
                                disabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.screen_selected),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 16.dp).size(24.dp)
                            )
                        }
                    }
                    if (opt.key == "vivo_single") {
                        Text(
                            text = stringResource(R.string.screen_vivo_single_warning),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 36.dp, bottom = 6.dp)
                        )
                    }
                    if (opt.key == "extract" && disabled) {
                        Text(
                            text = stringResource(R.string.screen_extract_blocked),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 36.dp, bottom = 6.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

    // 状态详情对话框（完整内容，长按可选中复制；按钮带 tonal 底色）
    // 同窗口覆盖层（不再用独立 Dialog 窗口）：采样坐标与主窗口一致、
    // 页面持续重绘所以模糊实时（用户反馈 #5/#6）；visible=false 时播完
    // 退场动画才退出组合，故不能用 if 包裹。
    // LocalClipboardManager 的替代品 LocalClipboard 是 suspend API，
    // 此处为同步复制场景，保留旧 API 并抑制弃用警告
    @Suppress("DEPRECATION")
    val statusClipboard = LocalClipboardManager.current
    LiquidDialog(
        visible = showStatusDetail,
        onDismissRequest = { showStatusDetail = false },
        title = { Text(stringResource(R.string.screen_status_detail)) },
        text = {
            Column {
                SelectionContainer {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.screen_long_press_copy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                haptic.click()
                statusClipboard.setText(AnnotatedString(statusText))
            }) { Text(stringResource(R.string.screen_copy)) }
        },
        dismissButton = {
            FilledTonalButton(onClick = { haptic.click(); showStatusDetail = false }) { Text(stringResource(R.string.screen_close)) }
        }
    )

    // 替换原图首次开启前的风险说明（沿用统一「不再提示」弹窗）
    // visible 模式：退场动画期间内容继续留存（不能用 if 包裹）
    ReminderInfoDialog(
        key = ReminderKey.REPLACE_MODE_RISK,
        confirmLabel = stringResource(R.string.screen_agree_and_enable),
        onDismiss = {
            pendingReplaceEnable = false
            // 未确认：玻璃块弹回原选项
            modeRevertTick++
        },
        onConfirm = {
            pendingReplaceEnable = false
            onSelectOutputMode(OutputMode.REPLACE)
        },
        visible = pendingReplaceEnable
    )
    } // 覆盖层
    } // 采样根 Box
}

/**
 * 文件列表区域（含空状态装饰）：竖屏占上下布局的上部，横屏占左右布局的左栏。
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
    /** 顶部让位：内容从屏幕顶部开始、滚动时穿过顶栏下方，用 contentPadding 预留顶栏高度 */
    topContentPadding: Dp = 8.dp,
    /** 底部让位：竖屏底部控制面板是浮动覆盖层，列表从面板下方穿过，
     *  用 contentPadding 预留面板高度，最后一项不会被面板挡住 */
    bottomContentPadding: Dp = 8.dp
) {
    Box(modifier = modifier) {
        if (files.isEmpty()) {
            // 空状态背景同心圆的主题色（DrawScope 内无法读 MaterialTheme，先取出）
            val primaryColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomContentPadding),
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
                            // 背景同心圆跟随主题色（原硬编码 0xFF4F46E5）；
                            // DrawScope 内不能读 MaterialTheme，从组合作用域传入
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
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
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
            // 文件列表液态玻璃面板（用户反馈：文件列表也没有液态玻璃效果，
            // 用 demo 的 Lazy scroll container 方案）：玻璃面板是父容器，
            // layerBackdrop 挂在子 LazyColumn 上只录制列表内容——面板折射的
            // 是真实列表内容且无自采样回路；同时整块面板被 pageBackdrop 录制，
            // 顶栏 frost 与底部面板还能折射到它
            val glass = LocalGlassEnabled.current
            // 绘制期（DrawScope lambda）不能读 CompositionLocal，深浅色在这里提前取出
            val listDark = LocalDarkTheme.current
            val listBackdrop = rememberLayerBackdrop()
            val panelShape = RoundedCornerShape(28.dp)
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .then(
                        if (glass) Modifier.drawBackdrop(
                            backdrop = listBackdrop,
                            shape = { panelShape },
                            effects = {
                                // 静态参数：重跑仅发生在尺寸变化时
                                vibrancy()
                                blur(8f.dp.toPx())
                                lens(16f.dp.toPx(), 32f.dp.toPx())
                            },
                            onDrawSurface = {
                                // 与整体通透度一致的轻色纸，保证列表文字可读
                                drawRect(if (listDark) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.18f))
                            }
                        ) else Modifier
                    )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (glass) Modifier.layerBackdrop(listBackdrop) else Modifier)
                        .then(if (glass) Modifier.clip(panelShape) else Modifier)
                        .padding(horizontal = 0.dp),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = topContentPadding,
                        bottom = bottomContentPadding
                    )
                ) {
                items(files, key = { it.path }) { item ->
                    // 项间距放在项内部：移除动画塌陷时连同间距一起收起；
                    // 剩余项补位为弹簧平移（animateItem placement），可被新的侧滑打断
                    Box(
                        Modifier.animateItem(
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
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
                                onDelete = { path -> onRemoveFile(path) }
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
 * 竖屏为底部浮起面板，横屏为右侧栏（外层负责滚动）。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    /** 处理方式选择「被拒」回弹信号（由外层 MainScreen 持有）：风险弹窗未确认时 +1 */
    modeRevertTick: Int,
    onAddFiles: () -> Unit,
    onBatchImport: () -> Unit,
    onClearFiles: () -> Unit,
    onConvert: () -> Unit,
    onStopConvert: () -> Unit,
    /** 打开状态详情弹窗（弹窗渲染在 MainScreen 覆盖层作用域，故由外层持有状态） */
    onOpenStatusDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 状态胶囊（带彩色状态点，点击查看完整详情）：
        // 忙碌时内部展开进度条 + a/b 明细（如「已处理 18/240」，批量模式
        // 另有第二行动态照片张数）——进度与明细常驻胶囊小背景板内
        // 圆点颜色：处理中=蓝、就绪=绿、错误=红、完成类=主题色
        // i18n：**不能按中文字面量匹配**（切到别的语言后判定会全部失效），
        // 这里改为从「本语言的资源文案」里取关键字：带占位符的模板取 `%` 之前的前缀做包含匹配，
        // 无占位符的整串做相等匹配。statusText 由 MainActivity 生成，两侧共用同一批 main_status_* 资源。
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
                busy -> androidx.compose.ui.graphics.Color(0xFF1E88E5)
                isError -> MaterialTheme.colorScheme.error
                isReady -> androidx.compose.ui.graphics.Color(0xFF34A853)
                isDone -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
        // 消息栏液态玻璃（用户反馈：上方的消息栏还没有液态玻璃效果）：
        // 玻璃分支用 liquidGlass 胶囊采样「根渐变+页面」合成层（默认解析），
        // 列表内容透过消息栏折射；MD3 分支保留原实色 Surface
        val msgShape = RoundedCornerShape(12.dp)
        val msgGlass = LocalGlassEnabled.current
        val msgInteraction = remember { MutableInteractionSource() }
        val msgPressed by msgInteraction.collectIsPressedAsState()
        Surface(
            shape = msgShape,
            color = if (msgGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (msgGlass) Modifier.liquidGlass(
                        msgShape,
                        tintAlpha = 0.10f,
                        lensHeight = 8.dp,
                        lensAmount = 14.dp,
                        fusionActive = { if (msgPressed) 1f else 0f }
                    ) else Modifier
                )
                .clickable(
                    interactionSource = msgInteraction,
                    indication = null
                ) { haptic.click(); onOpenStatusDetail() }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.screen_view_detail),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
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
                        LinearProgressIndicator(
                            progress = { if (isImporting) importProgress else progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (progressDetail.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = progressDetail,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 三按钮组（添加文件 / 批量处理 / 输出格式）：
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
                // 添加文件 + 批量导入（平分空间；处理中禁用）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassActionButton(
                        onClick = { onAddFiles() },
                        // 选择器打开流程（读相册）完成前禁用，防止重入引发扫描竞态崩溃
                        enabled = !busy && !isPickerOpening,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(26.dp),
                        tint = MaterialTheme.colorScheme.primaryContainer,
                        tintAlpha = 0.55f,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.screen_add_files), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                    GlassActionButton(
                        onClick = { onBatchImport() },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(26.dp),
                        tint = MaterialTheme.colorScheme.surface,
                        tintAlpha = 0.42f,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.screen_batch_import), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                }

                // 处理方式（第一版面）：液态玻璃 tabs 三选一（替换原图 / 删除原图 / 保留原图）。
                // 玻璃可用时为演示版 LiquidBottomTabs（可拖拽玻璃块 + 按压力高光 + 弹簧动画），
                // 点击选项玻璃块直接移动到该选项；不可用/禁用时回落原生分段。
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
                    LiquidSegmentedTabs(
                        labels = listOf(
                            stringResource(R.string.screen_mode_replace),
                            stringResource(R.string.screen_mode_delete),
                            stringResource(R.string.screen_mode_keep)
                        ),
                        selectedIndex = outputModes.indexOf(outputMode).coerceAtLeast(0),
                        onSelect = { onSelectOutputMode(outputModes[it]) },
                        enabled = !busy,
                        revertSignal = modeRevertTick
                    )
                    // 选中项的说明 + 权限不足时的解锁提示
                    Text(
                        text = stringResource(
                            when (outputMode) {
                                OutputMode.REPLACE -> R.string.screen_mode_replace_desc
                                OutputMode.DELETE -> R.string.screen_mode_delete_desc
                                OutputMode.KEEP -> R.string.screen_mode_keep_desc
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    if (!outputModeUsable) {
                        Text(
                            text = stringResource(R.string.screen_mode_need_access),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }

        // 清空 + 开始转换/停止处理 morph 行：
        // 点击开始转换后，转换按钮以贝塞尔曲线（0.5s）平滑拉伸占据清空按钮位置；
        // 内容以 0.3s 淡入淡出整体切换为停止图标（实心方块）+「停止处理」；
        // 处理完成/停止后按相同动画反向还原。
        // 抽成独立 composable：morph 动画 500ms 内的每帧重组被限制在本行，
        // 不再波及整个 BottomControls（分段栏 / 状态胶囊 / 提示文案）
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
 * 点击开始转换后，转换按钮以贝塞尔曲线（0.5s）平滑拉伸占据清空按钮位置；
 * 内容以 0.3s 淡入淡出整体切换为停止图标 +「停止处理」。
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
    // 清空按钮的显隐也走同一条 500ms 贝塞尔时间线：清空列表时平滑收起+淡出，
    // 转换按钮同步平滑扩展（用户反馈：要有推拉的平滑动画，此前是瞬间跳变）
    val clearProgress by animateFloatAsState(
        targetValue = if (hasFiles) 1f else 0f,
        animationSpec = tween(500, easing = FancyEasing),
        label = "clearProgress"
    )
    // extend = 清空按钮展开量（0=收起）。busy 收拢与 hasFiles 显隐两条
    // 时间线相乘互不干扰，原三组状态数值全部保持不变：
    // 有文件+空闲 → extend=1（clear 1.001 / convert 1.6）；busy → extend=0（convert 2.6）
    val extend = clearProgress * (1f - morph)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy((8f * extend).dp)
    ) {
        // 清空（玻璃按钮；随 morph/清空进度淡出并让位）。
        // 风格改为液态玻璃：淡 error 染色 + error 内容色——此前的 0.66 实色块
        // 是一颗纯红胶囊，没有玻璃感（用户反馈「清空按钮还没有改变风格」）。
        Box(modifier = Modifier.weight(extend + 0.001f)) {
            // 完全收起后不再渲染玻璃：alpha=0 的玻璃按钮仍会逐帧算模糊/折射，
            // 白烧 GPU（用户要求：不需要显示的时候不渲染）。权重槽位保留，
            // 推拉动画期间（extend > 0.02）照常渲染。
            if (extend > 0.02f) {
            GlassActionButton(
                onClick = onClearFiles,
                enabled = !busy && !clearBusy && hasFiles,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(extend),
                shape = RoundedCornerShape(26.dp),
                tint = MaterialTheme.colorScheme.error,
                tintAlpha = 0.16f,
                contentColor = MaterialTheme.colorScheme.error
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.screen_clear))
            }
            }
        }

        // 液态玻璃主按钮（品牌色着色）；与清空按钮互补推拉，动画过程中仍可点击
        GlassActionButton(
            onClick = {
                if (busy) onStopConvert() else onConvert()
            },
            modifier = Modifier.weight(2.6f - extend),
            shape = RoundedCornerShape(26.dp),
            tint = MaterialTheme.colorScheme.primary,
            tintAlpha = 0.82f,
            contentColor = MaterialTheme.colorScheme.onPrimary
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
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.screen_stop), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.screen_start_convert), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
