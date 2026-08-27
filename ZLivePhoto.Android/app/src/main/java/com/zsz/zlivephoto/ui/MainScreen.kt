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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
    /** 转换成功：触发「完成」移除动画 */
    val isDone: Boolean = false
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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    files: SnapshotStateList<FileItem>,
    statusText: String,
    progress: Float,
    progressDetail: String,
    progressDetail2: String,
    isImporting: Boolean,
    importProgress: Float,
    isConverting: Boolean,
    isBatch: Boolean,
    isPickerOpening: Boolean,
    selectedFormat: String,
    onAddFiles: () -> Unit,
    onBatchProcess: () -> Unit,
    onClearFiles: () -> Unit,
    onConvert: () -> Unit,
    onStopConvert: () -> Unit,
    onSelectFormat: (String) -> Unit,
    // 项10：处理完成后删除原图开关（开启=清脆震动 / 关闭=柔和震动）
    deleteOriginal: Boolean,
    onToggleDeleteOriginal: (Boolean) -> Unit,
    onRemoveFile: (String, Boolean) -> Unit
) {
    val haptic = rememberHapticFeedback()
    var showFormatSheet by remember { mutableStateOf(false) }
    var showStatusDetail by remember { mutableStateOf(false) }

    // 按钮按压反馈（项 5）：按下瞬间立即震动 + 圆角弹簧减小 + 轻微缩放
    val addFb = rememberPressFeedback()
    val batchFb = rememberPressFeedback()
    val formatFb = rememberPressFeedback()
    val clearFb = rememberPressFeedback(baseCorner = 16.dp, pressedCorner = 6.dp)
    val convertFb = rememberPressFeedback()
    // 项10 开关行：保留圆角曲率+缩放的固定序列动画，但按下不震动
    // （开启=清脆 / 关闭=柔和的差异化反馈由切换回调触发）
    val deleteFb = rememberPressFeedback(hapticOnPress = false)

    // 忙碌态（转换中或导入中）：隐藏三按钮组、进度条显示、转换按钮变形为停止
    val busy = isConverting || isImporting

    // 实际转换目标：vivo 已拆分为两个顶级选项（vivo_single / vivo），直接使用
    val effectiveTarget = selectedFormat

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                text = "Z-LivePhoto-Converter",
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
        // 文件列表区域（批量处理时被半透明叠加层覆盖，叠层低于底部控制区）
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // 涟漪同心圆装饰（呼应应用图标）
                        Box(
                            modifier = Modifier.size(168.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val c = this.center
                                val maxR = this.size.minDimension / 2f
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color(0xFF4F46E5).copy(alpha = 0.08f),
                                    radius = maxR
                                )
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color(0xFF4F46E5).copy(alpha = 0.14f),
                                    radius = maxR * 0.76f
                                )
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color(0xFF4F46E5).copy(alpha = 0.22f),
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
                                    onDelete = { path -> onRemoveFile(path, false) },
                                    onDoneRemove = { path -> onRemoveFile(path, true) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            // 批量处理叠加层：与背景底色相同、完全不透明，仅遮挡列表区
            // （空列表同样覆盖），位于底部控制区图层之下，为批量专属动画预留空间
            if (isBatch) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }

            // 底部控制区（浮起面板）
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                    if (progressDetail2.isNotEmpty()) {
                                        Text(
                                            text = progressDetail2,
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
                            // 添加文件 + 批量处理（平分左右空间；处理中禁用）
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
                                    onClick = { onBatchProcess() },
                                    enabled = !busy,
                                    interactionSource = batchFb.interactionSource,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .then(batchFb.scaleModifier),
                                    shape = RoundedCornerShape(batchFb.corner)
                                ) {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("批量处理", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                                }
                            }

                            // 格式选择按钮（处理中禁用）
                            FilledTonalButton(
                                onClick = { showFormatSheet = true },
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
                                    .clip(RoundedCornerShape(deleteFb.corner))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable(
                                        interactionSource = deleteFb.interactionSource,
                                        indication = null
                                    ) { onToggle(!deleteOriginal) }
                                    .then(deleteFb.scaleModifier)
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
                                Switch(
                                    checked = deleteOriginal,
                                    onCheckedChange = onToggle
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
                                enabled = !busy && files.isNotEmpty(),
                                interactionSource = clearFb.interactionSource,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .clickable {
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
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
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
                            color = androidx.compose.ui.graphics.Color(0xFF8B0000),
                            modifier = Modifier.padding(start = 36.dp, bottom = 6.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
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
