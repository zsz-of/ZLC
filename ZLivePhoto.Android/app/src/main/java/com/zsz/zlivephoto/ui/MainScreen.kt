package com.zsz.zlivephoto.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.R

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
    /** 合成任务：配对视频路径（formatKey=compose 时非空） */
    val composeVideoPath: String? = null
)

data class FormatOption(
    val key: String,
    val name: String
)

val formatOptions = listOf(
    FormatOption("google", "Google"),
    FormatOption("apple", "Apple"),
    FormatOption("oppo", "OPPO"),
    FormatOption("vivo_single", "vivo（单文件）"),
    FormatOption("vivo", "vivo（双文件）"),
    FormatOption("xiaomi", "小米"),
    FormatOption("honor", "荣耀"),
    FormatOption("meizu", "魅族"),
    FormatOption("extract", "拆解"),
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
    onCompose: () -> Unit,
    /** 打开设置页 */
    onOpenSettings: () -> Unit,
    onClearFiles: () -> Unit,
    onConvert: () -> Unit,
    onStopConvert: () -> Unit,
    onSelectFormat: (String) -> Unit,
    // 项10：处理完成后删除原图开关（开启=清脆震动 / 关闭=柔和震动）
    deleteOriginal: Boolean,
    onToggleDeleteOriginal: (Boolean) -> Unit,
    onRemoveFile: (String) -> Unit
) {
    val haptic = rememberHapticFeedback()
    var showFormatSheet by remember { mutableStateOf(false) }
    var showToolboxSheet by remember { mutableStateOf(false) }

    // 忙碌态（转换中或导入中）：隐藏三按钮组、进度条显示、转换按钮变形为停止
    val busy = isConverting || isImporting

    // 实际转换目标：vivo 已拆分为两个顶级选项（vivo_single / vivo），直接使用
    val effectiveTarget = selectedFormat

    // 队列含合成任务（formatKey=compose）时禁止选择「拆解」：拆解与合成语义互斥，
    // 合成任务会把照片+视频合成动态照片，而拆解是反向操作，混用会产出不可识别产物
    val hasComposeTask = files.any { it.formatKey == "compose" }

    // 横屏判断（提升到函数级，标题限宽与两栏布局共用）
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
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
                                text = "动态照片格式互转",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (files.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "${files.size} 个文件",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    // 设置入口（固定图标位，带点击弹性）
                    val settingsFb = rememberPressFeedback()
                    IconButton(
                        onClick = { haptic.click(); onOpenSettings() },
                        interactionSource = settingsFb.interactionSource,
                        modifier = Modifier.then(settingsFb.scaleModifier)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
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
                    onRemoveFile = onRemoveFile,
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                ) {
                    BottomControls(
                        statusText = statusText,
                        progress = progress,
                        progressDetail = progressDetail,
                        isImporting = isImporting,
                        importProgress = importProgress,
                        isConverting = isConverting,
                        isPickerOpening = isPickerOpening,
                        selectedFormat = selectedFormat,
                        hasFiles = files.isNotEmpty(),
                        busy = busy,
                        clearBusy = clearBusy,
                        deleteOriginal = deleteOriginal,
                        onToggleDeleteOriginal = onToggleDeleteOriginal,
                        onAddFiles = onAddFiles,
                        onOpenToolbox = { showToolboxSheet = true },
                        onClearFiles = onClearFiles,
                        onConvert = onConvert,
                        onStopConvert = onStopConvert,
                        onOpenFormatSheet = { showFormatSheet = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 文件列表区域（批量处理时被半透明叠加层覆盖，叠层低于底部控制区）
                FileListArea(
                    files = files,
                    listState = listState,
                    isConverting = isConverting,
                    effectiveTarget = effectiveTarget,
                    onRemoveFile = onRemoveFile,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                // 底部控制区（浮起面板）
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BottomControls(
                        statusText = statusText,
                        progress = progress,
                        progressDetail = progressDetail,
                        isImporting = isImporting,
                        importProgress = importProgress,
                        isConverting = isConverting,
                        isPickerOpening = isPickerOpening,
                        selectedFormat = selectedFormat,
                        hasFiles = files.isNotEmpty(),
                        busy = busy,
                        clearBusy = clearBusy,
                        deleteOriginal = deleteOriginal,
                        onToggleDeleteOriginal = onToggleDeleteOriginal,
                        onAddFiles = onAddFiles,
                        onOpenToolbox = { showToolboxSheet = true },
                        onClearFiles = onClearFiles,
                        onConvert = onConvert,
                        onStopConvert = onStopConvert,
                        onOpenFormatSheet = { showFormatSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }

    // 格式选择 BottomSheet（选择后不自动关闭，由用户自行关闭）
    if (showFormatSheet) {
        val sheetState = rememberModalBottomSheetState()
        // 滚动状态提升到 sheet 顶层 remember：切换输出格式触发重组时复用同一实例，
        // 选项列表的滚动位置不会丢失/重置（内联 rememberScrollState 在内容高度变化时易被重建）
        val formatScrollState = rememberScrollState()
        ModalBottomSheet(
            onDismissRequest = { showFormatSheet = false },
            sheetState = sheetState
        ) {
            Text(
                text = "选择输出格式",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            // 格式列表滚动区：vivo 已拆分为「单文件 / 双文件」两个顶级选项
            val maxListHeight = LocalConfiguration.current.screenHeightDp.dp * 0.62f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight)
                    .verticalScroll(formatScrollState)
            ) {
                formatOptions.forEach { opt ->
                    val selected = opt.key == selectedFormat
                    // 拆解与合成互斥：队列已有合成任务时不可切换到拆解模式
                    val disabled = opt.key == "extract" && hasComposeTask
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
                                onSelectFormat(opt.key)
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = opt.name,
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
                                contentDescription = "已选择",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 16.dp).size(24.dp)
                            )
                        }
                    }

                    // vivo 单文件选项下方的兼容性警告（常驻显示）
                    if (opt.key == "vivo_single") {
                        Text(
                            text = "⚠ 过老的机型可能无法识别此格式",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 36.dp, bottom = 6.dp)
                        )
                    }
                    // 拆解被禁用时的原因提示
                    if (opt.key == "extract" && disabled) {
                        Text(
                            text = "⚠ 队列含合成任务，不能选择拆解",
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
    }

    // 工具箱 BottomSheet（与输出格式选择器同款 UI：图标 + 一行一个功能）
    if (showToolboxSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showToolboxSheet = false },
            sheetState = sheetState
        ) {
            Text(
                text = "工具箱",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                ToolboxRow(
                    icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "批量导入",
                    subtitle = "扫描整个相册并批量识别动态照片",
                    onClick = {
                        haptic.click()
                        showToolboxSheet = false
                        onBatchImport()
                    }
                )
                ToolboxRow(
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "合成动态照片",
                    subtitle = "选择一张照片和一段视频配对合成",
                    onClick = {
                        haptic.click()
                        showToolboxSheet = false
                        onCompose()
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
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
    onRemoveFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (files.isEmpty()) {
            // 空状态背景同心圆的主题色（DrawScope 内无法读 MaterialTheme，先取出）
            val primaryColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .fillMaxSize(),
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
                        text = "还没有添加文件",
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
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
                                sameFormat = item.formatKey != null && item.formatKey == effectiveTarget,
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

/**
 * 工具箱条目：图标 + 标题 + 副标题，整行可点（与输出格式选择器同款视觉）。
 */
@Composable
private fun ToolboxRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val fb = rememberPressFeedback()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = fb.interactionSource,
                indication = null
            ) { onClick() }
            .then(fb.scaleModifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 底部控制面板内容：状态胶囊 + 三按钮组 + 删除原图开关 + 清空/转换 morph 行。
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
    selectedFormat: String,
    hasFiles: Boolean,
    busy: Boolean,
    clearBusy: Boolean,
    deleteOriginal: Boolean,
    onToggleDeleteOriginal: (Boolean) -> Unit,
    onAddFiles: () -> Unit,
    onOpenToolbox: () -> Unit,
    onClearFiles: () -> Unit,
    onConvert: () -> Unit,
    onStopConvert: () -> Unit,
    onOpenFormatSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    var showStatusDetail by remember { mutableStateOf(false) }

    // 按钮按压反馈（项 5）：按下瞬间立即震动 + 圆角弹簧减小 + 轻微缩放
    val addFb = rememberPressFeedback()
    val toolboxFb = rememberPressFeedback()
    val formatFb = rememberPressFeedback()
    val clearFb = rememberPressFeedback(baseCorner = 16.dp, pressedCorner = 6.dp)
    val convertFb = rememberPressFeedback()
    // 项10 开关行：保留圆角曲率+缩放的固定序列动画，但按下不震动
    // （开启=清脆 / 关闭=柔和的差异化反馈由切换回调触发）
    val deleteFb = rememberPressFeedback(hapticOnPress = false)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 状态胶囊（带彩色状态点，点击查看完整详情）：
        // 忙碌时内部展开进度条 + a/b 明细（如「已处理 18/240」，批量模式
        // 另有第二行动态照片张数）——进度与明细常驻胶囊小背景板内
        // 圆点颜色：处理中=蓝、就绪=绿、错误=红、完成类=主题色
        val statusColor = when {
            busy -> androidx.compose.ui.graphics.Color(0xFF1E88E5)
            statusText.contains("失败") || statusText.contains("错误") || statusText.contains("无法") ->
                MaterialTheme.colorScheme.error
            statusText == "就绪" -> androidx.compose.ui.graphics.Color(0xFF34A853)
            statusText.contains("完成") || statusText.contains("已导入") || statusText.contains("已获得") || statusText.contains("已移除") || statusText.contains("已清空") || statusText.contains("批量") || statusText.contains("已停止") ->
                MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { haptic.click(); showStatusDetail = true }
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
                            contentDescription = "查看详情",
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
                // 添加文件 + 工具箱（平分空间；处理中禁用）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onAddFiles() },
                        // 选择器打开流程（读相册）完成前禁用，防止重入引发扫描竞态崩溃
                        enabled = !busy && !isPickerOpening,
                        interactionSource = addFb.interactionSource,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .then(addFb.scaleModifier),
                        shape = RoundedCornerShape(addFb.corner)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("添加文件", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                    FilledTonalButton(
                        onClick = { onOpenToolbox() },
                        enabled = !busy,
                        interactionSource = toolboxFb.interactionSource,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .then(toolboxFb.scaleModifier),
                        shape = RoundedCornerShape(toolboxFb.corner)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("工具箱", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }
                }

                // 格式选择按钮（处理中禁用）
                FilledTonalButton(
                    onClick = { onOpenFormatSheet() },
                    enabled = !busy,
                    interactionSource = formatFb.interactionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .then(formatFb.scaleModifier),
                    shape = RoundedCornerShape(formatFb.corner)
                ) {
                    Icon(Icons.Default.FormatPaint, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    val fmtName = formatOptions.firstOrNull { it.key == selectedFormat }?.name ?: "Google"
                    Text("输出格式：$fmtName", style = MaterialTheme.typography.titleSmall)
                }

                // 项10：处理完成后删除原图开关行（随按钮组在处理中隐藏禁用）；
                // 批次完成后由系统删除工具一次性移入回收站；
                // 开启=清脆震动 / 关闭=柔和震动
                val onToggle: (Boolean) -> Unit = { newValue ->
                    if (newValue) haptic.click() else haptic.soft()
                    onToggleDeleteOriginal(newValue)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .then(deleteFb.scaleModifier)
                        .clip(RoundedCornerShape(deleteFb.corner))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable(
                            interactionSource = deleteFb.interactionSource,
                            indication = null
                        ) { onToggle(!deleteOriginal) }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoDelete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "处理完成后删除原图",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 不复用整行的 deleteFb 按压源：行与开关共用同一 interactionSource
                    // 会导致 M3 滑块(小圆)在行级动画/快速切换时卡在放大态无法复原
                    Switch(
                        checked = deleteOriginal,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }

        // 清空 + 开始转换/停止处理 morph 行：
        // 点击开始转换后，转换按钮以贝塞尔曲线（0.5s）平滑拉伸占据清空按钮位置；
        // 内容以 0.3s 淡入淡出整体切换为停止图标（实心方块）+「停止处理」；
        // 处理完成/停止后按相同动画反向还原
        val morph by animateFloatAsState(
            targetValue = if (busy) 1f else 0f,
            animationSpec = tween(500, easing = FancyEasing),
            label = "morph"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((8 * (1f - morph)).dp)
        ) {
            // 清空（tonal 容器带底色；随 morph 淡出并让位）
            Box(modifier = Modifier.weight(1f - morph + 0.001f)) {
                FilledTonalButton(
                    onClick = { onClearFiles() },
                    enabled = !busy && !clearBusy && hasFiles,
                    interactionSource = clearFb.interactionSource,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .alpha(1f - morph)
                        .then(clearFb.scaleModifier),
                    shape = RoundedCornerShape(clearFb.corner)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清空")
                }
            }

            // 渐变主按钮（品牌色 Indigo → Violet）；动画播放过程中仍可点击
            Button(
                onClick = {
                    if (busy) onStopConvert() else onConvert()
                },
                interactionSource = convertFb.interactionSource,
                modifier = Modifier
                    .weight(1.6f + morph * 0.999f)
                    .height(52.dp)
                    .then(convertFb.scaleModifier)
                    .clip(RoundedCornerShape(convertFb.corner))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        RoundedCornerShape(convertFb.corner)
                    ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                shape = RoundedCornerShape(convertFb.corner)
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
                            Text("停止处理", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("开始转换", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 状态详情对话框（完整内容，长按可选中复制；按钮带 tonal 底色）
    if (showStatusDetail) {
        // LocalClipboardManager 的替代品 LocalClipboard 是 suspend API，
        // 此处为同步复制场景，保留旧 API 并抑制弃用警告
        @Suppress("DEPRECATION")
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showStatusDetail = false },
            title = { Text("状态详情") },
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
                        text = "长按文本可选中复制",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(onClick = {
                    haptic.click()
                    clipboard.setText(AnnotatedString(statusText))
                }) { Text("复制") }
            },
            dismissButton = {
                FilledTonalButton(onClick = { haptic.click(); showStatusDetail = false }) { Text("关闭") }
            }
        )
    }
}
