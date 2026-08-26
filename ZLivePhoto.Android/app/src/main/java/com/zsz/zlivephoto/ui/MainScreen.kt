package com.zsz.zlivephoto.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FileItem(
    val path: String,
    val name: String,
    val info: String,
    val isUnrecognized: Boolean = false,
    val sourceUri: String? = null,
    val sourceTime: Long = 0L,
    val sourceTaken: Long = 0L
)

data class FormatOption(
    val key: String,
    val name: String,
    val desc: String
)

val formatOptions = listOf(
    FormatOption("google", "Google", "JPEG+MP4 单文件，兼容 Android 原生"),
    FormatOption("apple", "Apple", "JPG+MOV 双文件，Apple Live Photo"),
    FormatOption("oppo", "OPPO", "单文件，OPPO 私有 XMP 扩展"),
    FormatOption("vivo", "vivo", "单/双文件可选（点开设置），vivo 私有 XMP + footer"),
    FormatOption("xiaomi", "小米", "单文件，双 XMP 标签 + EXIF 标识"),
    FormatOption("honor", "荣耀", "单文件，Google Container + EIS matrix footer"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    files: SnapshotStateList<FileItem>,
    statusText: String,
    progress: Float,
    isConverting: Boolean,
    selectedFormat: String,
    onAddFiles: () -> Unit,
    onClearFiles: () -> Unit,
    onConvert: () -> Unit,
    onSelectFormat: (String) -> Unit,
    vivoMode: String,
    onSelectVivoMode: (String) -> Unit,
    onRemoveFile: (String) -> Unit
) {
    val haptic = rememberHapticFeedback()
    var showFormatSheet by remember { mutableStateOf(false) }
    var showStatusDetail by remember { mutableStateOf(false) }

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
                            Canvas(modifier = Modifier.size(22.dp)) {
                                val c = this.center
                                drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 2.5.dp.toPx(), center = c)
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color.White,
                                    radius = 5.5.dp.toPx(),
                                    center = c,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx())
                                )
                                drawCircle(
                                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
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
                                text = "动态照片格式互转 · 字节级无损",
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
            // 文件列表区域
            if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                                // 由外向内渐浓的涟漪
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "支持 Google / Apple / vivo / OPPO / 小米 / 荣耀\n动态照片格式互转，字节级无损",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(files, key = { it.path }) { item ->
                        SwipeToDeleteFileCard(
                            item = item,
                            onDelete = {
                                haptic.longPress()
                                // 按路径删除：连续滑动删除时列表项位移不会导致误删/卡死
                                onRemoveFile(item.path)
                            }
                        )
                    }
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
                    // 进度条
                    AnimatedVisibility(
                        visible = isConverting,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // 状态文字（带彩色状态点的胶囊，点击查看完整详情）
                    val statusColor = when {
                        statusText.contains("失败") || statusText.contains("错误") || statusText.contains("无法") ->
                            MaterialTheme.colorScheme.error
                        statusText.contains("完成") || statusText.contains("已导入") || statusText.contains("已获得") || statusText.contains("已移除") || statusText.contains("已清空") ->
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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
                    }

                    // 添加文件（系统照片选择器）
                    FilledTonalButton(
                        onClick = { haptic.click(); onAddFiles() },
                        modifier = Modifier.fillMaxWidth().height(52.dp).scaleOnPress(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("添加文件", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }

                    // 格式选择按钮
                    FilledTonalButton(
                        onClick = {
                            haptic.click()
                            showFormatSheet = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .scaleOnPress(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.FormatPaint, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        val fmtName = formatOptions.firstOrNull { it.key == selectedFormat }?.name ?: "Google"
                        val vivoSuffix = if (selectedFormat == "vivo") {
                            if (vivoMode == "single") "（单文件）" else "（双文件）"
                        } else ""
                        Text("输出格式：$fmtName$vivoSuffix", style = MaterialTheme.typography.titleSmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptic.click()
                                onClearFiles()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .scaleOnPress(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("清空")
                        }

                        // 渐变主按钮（品牌色 Indigo → Violet）
                        Button(
                            onClick = {
                                haptic.longPress()
                                onConvert()
                            },
                            modifier = Modifier
                                .weight(1.6f)
                                .height(52.dp)
                                .scaleOnPress()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    ),
                                    RoundedCornerShape(16.dp)
                                ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color.Transparent
                            ),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isConverting
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("开始转换", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 格式选择 BottomSheet
    if (showFormatSheet) {
        val sheetState = rememberModalBottomSheetState()
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
            // 列表限高 + 可滚动：vivo 单/双文件设置展开后，下方荣耀等选项
            // 仍可通过滚动完整查看；滚动位置由 ScrollState 保持，不会重置
            val maxListHeight = LocalConfiguration.current.screenHeightDp.dp * 0.62f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight)
                    .verticalScroll(rememberScrollState())
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
                                // vivo 点开后保留 sheet 以切换单/双文件模式
                                if (opt.key != "vivo") showFormatSheet = false
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = opt.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = opt.desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

                    // vivo 模式切换（选中 vivo 时展开）
                    AnimatedVisibility(visible = selected && opt.key == "vivo") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "输出模式",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            VivoModeRow(
                                label = "单文件",
                                desc = "JPG+MP4 合并为一个文件，vivo 相册可直接识别",
                                checked = vivoMode == "single",
                                onClick = { haptic.click(); onSelectVivoMode("single") }
                            )
                            VivoModeRow(
                                label = "双文件",
                                desc = "JPG + MP4 两个文件，通过 footer 关联",
                                checked = vivoMode == "double",
                                onClick = { haptic.click(); onSelectVivoMode("double") }
                            )

                            // 单文件模式警告：暗红色字体
                            if (vivoMode == "single") {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "⚠ 过老的机型可能无法识别此格式",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color(0xFF8B0000)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // 状态详情对话框（完整内容，长按可选中复制）
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
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(statusText))
                }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = { showStatusDetail = false }) { Text("关闭") }
            }
        )
    }
}

// ---------- vivo 单/双文件模式行 ----------

@Composable
private fun VivoModeRow(label: String, desc: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (checked) MaterialTheme.colorScheme.primaryContainer
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = checked, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------- 右滑删除文件卡片 ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteFileCard(item: FileItem, onDelete: () -> Unit) {
    // rememberUpdatedState：confirmValueChange 在首次组合时被 SwipeToDismissBoxState
    // 捕获，若直接引用 onDelete 会形成过期闭包（连续滑动删除时删错项/卡在删除态），
    // 必须经由 State 读取最新回调
    val currentOnDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnDelete()
                true
            } else false
        },
        positionalThreshold = { distance -> distance * 0.5f }
    )

    // 缩略图：IO 线程降采样解码 + EXIF 方向校正
    val thumb by produceState<Bitmap?>(null, item.path) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(item.path) }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        enableDismissFromStartToEnd = false,
        modifier = Modifier.fillMaxWidth()
    ) {
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
                    val nameColor = if (item.isUnrecognized) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    val nameDecoration = if (item.isUnrecognized) TextDecoration.LineThrough
                        else TextDecoration.None
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = nameColor,
                        textDecoration = nameDecoration,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.info,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isUnrecognized) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = nameDecoration
                    )
                }
            }
        }
    }
}

/** 解码列表缩略图：按 2 的幂降采样至约 128px，并按 EXIF 方向旋转；失败返回 null。 */
private fun decodeThumbnail(path: String): Bitmap? {
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
