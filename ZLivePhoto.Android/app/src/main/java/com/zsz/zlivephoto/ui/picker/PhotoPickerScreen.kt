package com.zsz.zlivephoto.ui.picker

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import com.zsz.zlivephoto.BuildConfig
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.zsz.zlivephoto.ui.AlphaEasing
import com.zsz.zlivephoto.ui.AppSettings
import com.zsz.zlivephoto.ui.FancyEasing
import com.zsz.zlivephoto.ui.PressHapticEffect
import com.zsz.zlivephoto.ui.ReminderInfoDialog
import com.zsz.zlivephoto.ui.ReminderKey
import com.zsz.zlivephoto.ui.rememberPressFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoPickerScreen(
    albums: List<AlbumInfo>,
    scanner: AlbumScanner,
    scannerScope: CoroutineScope,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onConfirm: (List<MediaItem>) -> Unit,
    onLaunchSystemPicker: () -> Unit,
    /** 合成模式：选择普通照片+视频配对合成动态照片 */
    composeMode: Boolean = false,
    /** 合成模式确认回调：(照片列表, 视频列表)，按序号一一配对 */
    onConfirmCompose: ((List<MediaItem>, List<MediaItem>) -> Unit)? = null
) {
    val haptic = com.zsz.zlivephoto.ui.rememberHapticFeedback()
    var currentBucketId by remember { mutableStateOf(albums.firstOrNull()?.bucketId) }
    val bucketId = currentBucketId
    val selected = remember { mutableStateListOf<MediaItem>() }
    // 合成模式：照片与视频分开计数（序号各自独立，第 1 张照片与第 1 个视频都是序号 1）
    val selectedPhotos = remember { mutableStateListOf<MediaItem>() }
    val selectedVideos = remember { mutableStateListOf<MediaItem>() }
    var albumsExpanded by remember { mutableStateOf(false) }
    // 相册切换滚动方向（项 4）：1=切到右侧相册（新内容自右侧进入、画面向左滚），-1=反向
    var slideDir by remember { mutableStateOf(1) }

    // 进入/离开相册：进入 diff+续扫，离开取消（保留进度）
    DisposableEffect(bucketId) {
        if (bucketId != null) scanner.enter(bucketId, scannerScope)
        onDispose { if (bucketId != null) scanner.leave(bucketId) }
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
    // 序号与视觉顺序一致；固定按日期降序；size 读取驱动扫描追加时重算
    val n = results?.size ?: 0
    val displayOrder = remember(results, n) {
        results.orEmpty().sortedByDescending { it.sortTime }
    }

    // 合成模式视频时长告警阈值（超过 3 秒提示兼容性风险，但仍允许选择）
    val videoWarnMs = 3000L

    // 合成模式提示弹窗状态
    var showOver3sWarning by remember { mutableStateOf(false) }
    var showSysPickerBlocked by remember { mutableStateOf(false) }
    // 合成模式：COMPOSE_SYSTEM_PICKER 弹窗勾选「不再提示」后，该「系统选择器」按钮
    // 无任何实际作用（合成模式只能走内置选择器），直接隐藏；普通导入模式不受影响
    var sysPickerNoRemind by remember {
        mutableStateOf(
            AppSettings.isReminderSuppressed(ReminderKey.COMPOSE_SYSTEM_PICKER)
        )
    }
    val hideSysPickerButton = composeMode && sysPickerNoRemind

    /** 项是否已选中（合成模式按类型查对应列表） */
    fun isSelected(item: MediaItem): Boolean {
        if (!composeMode) return selected.any { it.id == item.id }
        val list = if (item.isVideo) selectedVideos else selectedPhotos
        return list.any { it.id == item.id }
    }

    // 确认导入（合成模式：照片/视频都至少 1 个才可确认，回调按序号一一配对）
    val selText = if (composeMode) {
        if (selectedPhotos.isEmpty() && selectedVideos.isEmpty()) ""
        else "照片 ${selectedPhotos.size} · 视频 ${selectedVideos.size}"
    } else if (selected.isEmpty()) "" else "已选 ${selected.size} 张"
    val canConfirm = if (composeMode) selectedPhotos.isNotEmpty() && selectedVideos.isNotEmpty()
                     else selected.isNotEmpty()
    val doConfirm: () -> Unit = {
        if (composeMode) onConfirmCompose?.invoke(selectedPhotos.toList(), selectedVideos.toList())
        else onConfirm(selected.toList())
    }

    // 单项选择切换（追加到末尾，序号递增）；
    // 合成模式按类型分流到照片/视频列表，选择超过 3 秒的视频时提示兼容性风险
    val toggleItem: (MediaItem) -> Unit = { item ->
        haptic.click()
        val list = if (composeMode) (if (item.isVideo) selectedVideos else selectedPhotos) else selected
        val idx = list.indexOfFirst { it.id == item.id && it.isVideo == item.isVideo }
        if (idx >= 0) {
            list.removeAt(idx)
        } else {
            list.add(item)
            if (composeMode && item.isVideo && item.durationMs > videoWarnMs &&
                !AppSettings.isReminderSuppressed(ReminderKey.COMPOSE_VIDEO_OVER_3S)) {
                showOver3sWarning = true
            }
        }
    }
    // 成组选择切换（日期栏/全选）：组内可选项全选 → 全部取消；否则按传入顺序补齐未选
    val toggleGroup: (List<MediaItem>) -> Unit = { group ->
        val choosable = group
        val allIn = choosable.isNotEmpty() && choosable.all { isSelected(it) }
        haptic.click()
        val ids = choosable.map { it.id }.toSet()
        if (allIn) {
            selected.removeAll { it.id in ids }
            selectedPhotos.removeAll { it.id in ids }
            selectedVideos.removeAll { it.id in ids }
        } else {
            for (g in choosable) {
                if (composeMode) {
                    if (g.isVideo) {
                        if (selectedVideos.none { it.id == g.id }) selectedVideos.add(g)
                    } else if (selectedPhotos.none { it.id == g.id }) selectedPhotos.add(g)
                } else if (selected.none { it.id == g.id }) selected.add(g)
            }
        }
    }

    /** 项的选中序号（合成模式照片/视频各自独立编号） */
    fun selIndexOf(item: MediaItem): Int {
        if (!composeMode) return selected.indexOfFirst { it.id == item.id }
        val list = if (item.isVideo) selectedVideos else selectedPhotos
        return list.indexOfFirst { it.id == item.id }
    }

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

    // 底部确认栏按钮固定序列点击动画（项 1）
    val importFb = rememberPressFeedback(baseCorner = 20.dp, pressedCorner = 8.dp)
    // 系统选择器按钮
    val sysPickerFb = rememberPressFeedback(baseCorner = 20.dp, pressedCorner = 8.dp)

    // 深浅色切换时以 isDarkTheme 为 key 驱动全树重组：
    // uiMode configChanges 下部分控件（排序按钮/日期头/张数文本）曾不随主题切换
    key(isDarkTheme) {
    // 横屏判断：顶栏分左右两栏（相册/系统选择器居左，全选/进度居右）
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── 顶栏（surfaceContainer 容器）──
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .statusBarsPadding()
        ) {
            if (isLandscape) {
                // ── 横屏：左栏（标题 → 系统选择器 → 垂直相册列表）+ 右栏（全选/进度/导入）──
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(0.5f)) {
                        // 标题行
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { haptic.click(); onBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                                    tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(if (composeMode) "合成动态照片" else "选择动态照片",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        // 系统选择器按钮（紧跟标题下方；合成模式置灰，点击说明不支持原因；
                        // 合成模式勾选「不再提示」后按钮整体隐藏，普通导入模式始终显示）
                        if (!hideSysPickerButton) {
                            FilledTonalButton(
                                onClick = {
                                    if (composeMode) showSysPickerBlocked = true
                                    else onLaunchSystemPicker()
                                },
                                interactionSource = sysPickerFb.interactionSource,
                                shape = RoundedCornerShape(sysPickerFb.corner),
                                colors = if (composeMode) ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ) else ButtonDefaults.filledTonalButtonColors(),
                                modifier = Modifier
                                    .padding(start = 12.dp, bottom = 4.dp)
                                    .then(sysPickerFb.scaleModifier),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) { Text("系统选择器", style = MaterialTheme.typography.labelMedium) }
                        }
                        // 相册列表（垂直排列）+ 右端展开按钮（封面横向展开）
                        if (albums.size > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LazyColumn(
                                    Modifier
                                        .weight(1f)
                                        .heightIn(max = 132.dp)
                                        .padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                IconButton(
                                    onClick = { haptic.click(); albumsExpanded = !albumsExpanded },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    val rotation by animateFloatAsState(
                                        targetValue = if (albumsExpanded) 180f else 0f,
                                        animationSpec = tween(250), label = "chevron"
                                    )
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = if (albumsExpanded) "收起相册封面" else "展开相册封面",
                                        modifier = Modifier.rotate(rotation)
                                    )
                                }
                            }
                        }
                    }
                    // 右栏：全选 / 扫描进度 / 已选数 + 导入按钮（不占底部整条空间）
                    Column(
                        Modifier
                            .weight(0.5f)
                            .padding(end = 12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        val allSel = n > 0 && displayOrder.all { isSelected(it) }
        val someSel = displayOrder.any { isSelected(it) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("全选", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.size(4.dp))
                            CircleTriCheckbox(
                                state = when {
                                    allSel -> ToggleableState.On
                                    someSel -> ToggleableState.Indeterminate
                                    else -> ToggleableState.Off
                                },
                                onClick = { if (n > 0) toggleGroup(displayOrder) }
                            )
                        }
                        AnimatedVisibility(visible = running) {
                            LinearProgressIndicator(
                                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)
                            )
                        }
                        // 已选数 + 导入按钮（原底部确认栏在横屏下的替代位置）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        ) {
                            Text(
                                selText,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.size(12.dp))
                            Button(
                                onClick = doConfirm,
                                enabled = canConfirm,
                                interactionSource = importFb.interactionSource,
                                shape = RoundedCornerShape(importFb.corner),
                                modifier = Modifier.then(importFb.scaleModifier),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                            ) { Text("导入") }
                        }
                    }
                }
            } else {
                // ── 竖屏：标题行 + 相册栏 + 进度条 ──
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { haptic.click(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(if (composeMode) "合成动态照片" else "选择动态照片",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f))
                    // 系统选择器按钮（固定序列点击动画，项 1）；
                    // 合成模式置灰，点击说明不支持原因（需同时配对照片+视频）；
                    // 合成模式勾选「不再提示」后按钮整体隐藏，普通导入模式始终显示
                    if (!hideSysPickerButton) {
                        FilledTonalButton(
                            onClick = {
                                if (composeMode) showSysPickerBlocked = true
                                else onLaunchSystemPicker()
                            },
                            interactionSource = sysPickerFb.interactionSource,
                            shape = RoundedCornerShape(sysPickerFb.corner),
                            colors = if (composeMode) ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ) else ButtonDefaults.filledTonalButtonColors(),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .then(sysPickerFb.scaleModifier),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) { Text("系统选择器", style = MaterialTheme.typography.labelMedium) }
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
                                    expanded = albumsExpanded,
                                    onClick = { switchAlbum(album.bucketId) }
                                )
                            }
                        }
                        IconButton(
                            onClick = { haptic.click(); albumsExpanded = !albumsExpanded },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            val rotation by animateFloatAsState(
                                targetValue = if (albumsExpanded) 180f else 0f,
                                animationSpec = tween(250), label = "chevron"
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (albumsExpanded) "收起相册封面" else "展开相册封面",
                                modifier = Modifier.rotate(rotation)
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
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        // ── 工具行：张数（左）；横屏全选已在顶栏右栏，竖屏保留全选（右） ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 相册项目数量
            Text(
                if (running) "扫描中 ${progress.first}/$total" else "${results?.size ?: 0} 张",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isLandscape) {
                Spacer(Modifier.weight(1f))
                // 全选（移至右侧，圆形现代样式，项 2/3）：按网格顺序有序标记
                val allSel = n > 0 && displayOrder.all { g -> selected.any { it.id == g.id } }
                val someSel = displayOrder.any { g -> selected.any { it.id == g.id } }
                Text("全选", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(4.dp))
                CircleTriCheckbox(
                    state = when {
                        allSel -> ToggleableState.On
                        someSel -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                    onClick = { if (n > 0) toggleGroup(displayOrder) }
                )
            }
        }

        // ── 缩略图网格（3 列；相册切换横向滚动动画，方向反转项 4） ──
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            AnimatedContent(
                targetState = bucketId,
                transitionSpec = {
                    if (slideDir >= 0) {
                        // 切到右侧相册：新内容自右侧进入、旧内容向左滑出（画面向左滚动）
                        (slideInHorizontally(tween(320, easing = FancyEasing)) { it } +
                                fadeIn(tween(240, easing = AlphaEasing)))
                            .togetherWith(
                                slideOutHorizontally(tween(320, easing = FancyEasing)) { -it } +
                                        fadeOut(tween(240, easing = AlphaEasing)))
                    } else {
                        (slideInHorizontally(tween(320, easing = FancyEasing)) { -it } +
                                fadeIn(tween(240, easing = AlphaEasing)))
                            .togetherWith(
                                slideOutHorizontally(tween(320, easing = FancyEasing)) { it } +
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
                    onToggle = toggleItem,
                    onToggleGroup = toggleGroup,
                    composeMode = composeMode
                )
            }
        }

        // ── 底部确认栏（仅竖屏；横屏的导入按钮已移至顶栏右栏，不占底部空间）──
        if (!isLandscape) {
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selText,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = doConfirm,
                    enabled = canConfirm,
                    interactionSource = importFb.interactionSource,
                    shape = RoundedCornerShape(importFb.corner),
                    modifier = Modifier.then(importFb.scaleModifier)
                ) { Text("导入") }
            }
        }
        }

        // 合成模式提示弹窗（统一「不再提示」机制）
        if (showOver3sWarning) {
            ReminderInfoDialog(
                key = ReminderKey.COMPOSE_VIDEO_OVER_3S,
                onDismiss = { showOver3sWarning = false }
            )
        }
        if (showSysPickerBlocked) {
            ReminderInfoDialog(
                key = ReminderKey.COMPOSE_SYSTEM_PICKER,
                onDismiss = {
                    showSysPickerBlocked = false
                    // 弹窗内勾选「不再提示」后立即刷新本地状态，按钮随之隐藏
                    sysPickerNoRemind =
                        AppSettings.isReminderSuppressed(ReminderKey.COMPOSE_SYSTEM_PICKER)
                }
            )
        }
    }
    } // key(isDarkTheme)
}

/**
 * 单个相册的网格页：固定按日期降序分组显示（排序功能已移除）。
 * 日期分组 + 粘性标题；必须先排序取快照再 groupBy 归组逐组发射
 * （扫描期间流式追加未排序，直接遍历发射会导致 stickyHeader 重复 key 崩溃）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGridPage(
    bucketId: Long?,
    scanner: AlbumScanner,
    isSelected: (MediaItem) -> Boolean,
    selIndexOf: (MediaItem) -> Int,
    onToggle: (MediaItem) -> Unit,
    onToggleGroup: (List<MediaItem>) -> Unit,
    composeMode: Boolean = false
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

    when {
        results == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("没有可用相册", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        results.isEmpty() && !running -> Column(
            Modifier.fillMaxSize().padding(bottom = 48.dp),
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
                if (composeMode) "没有找到照片或视频" else "没有找到动态照片",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> LazyVerticalGrid(
            state = rememberLazyGridState(),
            // 竖屏 3 列；横屏固定 5 列（用户要求每行五张照片）
            columns = GridCells.Fixed(
                if (LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp) 5 else 3
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val sorted = results.sortedByDescending { it.sortTime }
            val groups = sorted.groupBy { dateKey(it.sortTime) }
            for ((dk, groupItems) in groups) {
                stickyHeader(key = "hdr_$dk") {
                    Box(Modifier.animateItem().fillMaxWidth()) {
                        val allSel = groupItems.all { isSelected(it) }
                        val someSel = groupItems.any { isSelected(it) }
                        DateHeader(
                            label = dateLabel(groupItems.first().sortTime),
                            checkState = when {
                                allSel -> ToggleableState.On
                                someSel -> ToggleableState.Indeterminate
                                else -> ToggleableState.Off
                            },
                            onToggleAll = { onToggleGroup(groupItems) }
                        )
                    }
                }
                items(groupItems, key = { it.key }) { item ->
                    Box(Modifier.animateItem()) {
                        GridCell(
                            item = item,
                            selected = isSelected(item),
                            selectionIndex = selIndexOf(item),
                            onToggle = { onToggle(item) },
                            durationText = if (composeMode && item.isVideo)
                                formatDurationLabel(item.durationMs) else null
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
    horizontal: Boolean = false
) {
    val resolver = LocalContext.current.contentResolver
    val density = LocalDensity.current
    var textWidth by remember { mutableStateOf(100.dp) }
    val interactionSource = remember { MutableInteractionSource() }
    PressHapticEffect(interactionSource)
    val corner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (expanded) 16.dp else 12.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chipCorner"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(corner),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        if (horizontal) {
            // 横向布局（选择器横屏用）：文字在左，展开时封面在右侧横向展开
            Row(
                Modifier
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
                    enter = fadeIn(tween(200)) + expandHorizontally(tween(250)),
                    exit = fadeOut(tween(150)) + shrinkHorizontally(tween(200))
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
 * - 背景：与界面背景一致的不透明色（滑动时内容不穿透）
 * - 文本（项 6）：月日不变时无动画；变化时数字竖向滚动（Material 滚动数字风格，
 *   160ms 一步到位）
 * - 右端圆形三态复选框（项 2）：点击选择/取消该日期下所有照片
 */
@Composable
private fun DateHeader(label: String, checkState: ToggleableState, onToggleAll: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
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
        CircleTriCheckbox(state = checkState, onClick = onToggleAll)
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
            .size(26.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick?.invoke() }
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(26.dp)) {
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

/** 日期标签：今天 / 昨天 / x月x日（今年） / yyyy年x月x日（往年） */
private fun dateLabel(time: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = time }
    val now = Calendar.getInstance()

    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        sameDay(cal, now) -> "今天"
        sameDay(cal, yesterday) -> "昨天"
        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
            "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
        else ->
            "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
    }
}

/** 视频时长徽标文本：≥10s 取整显示，否则保留 1 位小数（如 2.5s） */
private fun formatDurationLabel(ms: Long): String {
    val s = ms / 1000.0
    return if (s >= 10.0) "${s.toInt()}s" else "${"%.1f".format(s)}s"
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
    durationText: String? = null
) {
    val resolver = LocalContext.current.contentResolver
    val transition = updateTransition(selected, label = "cell")

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
            .clickable(onClick = onToggle)
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

        // 合成模式视频项：左下角时长徽标（如 2.5s）
        if (durationText != null) {
            Text(
                text = durationText,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
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
        // 加载占位（项 9 ImageToolbox 式）：呼吸脉冲
        if (bitmap == null) {
            val pulse by rememberInfiniteTransition(label = "thumbPulse").animateFloat(
                initialValue = 0.45f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
                label = "pulseAlpha"
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = pulse))
            )
        }
    }
}
