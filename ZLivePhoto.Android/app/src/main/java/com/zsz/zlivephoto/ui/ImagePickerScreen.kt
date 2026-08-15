package com.zsz.zlivephoto.ui

import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.core.formats.FormatRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImageEntry(
    val id: Long,
    val name: String,
    val uri: Uri,
    val path: String,
    val bucket: String
)

data class Album(
    val name: String,
    val count: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePickerScreen(
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    onUseSystemPicker: () -> Unit
) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val selectedUris = remember { mutableStateListOf<Uri>() }
    val displayImages = remember { mutableStateListOf<ImageEntry>() }

    // 拦截返回键，回到主界面
    BackHandler { onDismiss() }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var showAlbumDropdown by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var scanTotal by remember { mutableIntStateOf(0) }
    var scanDone by remember { mutableIntStateOf(0) }
    var scanJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // 加载相册列表
    LaunchedEffect(Unit) {
        val albumMap = mutableMapOf<String, Int>()
        withContext(Dispatchers.IO) {
            val projection = arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val selection = "${MediaStore.Images.Media.MIME_TYPE} = ?"
            val selectionArgs = arrayOf("image/jpeg")
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { cursor ->
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val bucket = cursor.getString(bucketCol) ?: "未知相册"
                    albumMap[bucket] = (albumMap[bucket] ?: 0) + 1
                }
            }
        }
        val sorted = albumMap.entries.sortedWith(
            compareBy<MutableMap.MutableEntry<String, Int>> { if (it.key.equals("Camera", true)) 0 else 1 }
                .thenByDescending { it.value }
        ).map { Album(it.key, it.value) }
        albums = sorted
        selectedAlbum = sorted.firstOrNull { it.name.equals("Camera", true) }?.name
            ?: sorted.firstOrNull()?.name
    }

    // 扫描指定相册的动态照片（流式加载）
    LaunchedEffect(selectedAlbum) {
        if (selectedAlbum == null) return@LaunchedEffect

        scanJob?.cancel()
        displayImages.clear()
        isScanning = true
        scanProgress = 0f
        scanDone = 0

        scanJob = launch(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            val selection = "${MediaStore.Images.Media.MIME_TYPE} = ? AND ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf("image/jpeg", selectedAlbum!!)
            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

            val candidates = mutableListOf<ImageEntry>()
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    val path = cursor.getString(dataCol)
                    val bucket = cursor.getString(bucketCol) ?: "未知相册"
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    candidates.add(ImageEntry(id, name, uri, path, bucket))
                }
            }

            scanTotal = candidates.size
            for (entry in candidates) {
                try {
                    val (plugin, score) = FormatRegistry.detectBest(entry.path)
                    if (plugin != null && score >= 50) {
                        launch(Dispatchers.Main) { displayImages.add(entry) }
                    }
                } catch (_: Exception) {}
                scanDone++
                launch(Dispatchers.Main) { scanProgress = scanDone.toFloat() / scanTotal }
            }

            launch(Dispatchers.Main) {
                isScanning = false
                scanProgress = 1f
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("选择动态照片", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (selectedUris.isNotEmpty()) {
                            Text("已选 ${selectedUris.size} 张", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { haptic.click(); onDismiss() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    FilledTonalButton(onClick = { haptic.click(); onUseSystemPicker() }, modifier = Modifier.scaleOnPress()) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("系统选择器")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (selectedUris.isNotEmpty()) {
                        FilledTonalButton(onClick = { haptic.longPress(); onConfirm(selectedUris.mapNotNull { uri -> displayImages.firstOrNull { it.uri == uri }?.path }) }, modifier = Modifier.scaleOnPress()) {
                            Text("完成 (${selectedUris.size})")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 相册选择器
            Row(
                modifier = Modifier.fillMaxWidth().clickable { haptic.click(); showAlbumDropdown = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Photo, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(selectedAlbum ?: "选择相册", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.weight(1f))
                Text("${displayImages.size} 张${if (isScanning) "（$scanDone/$scanTotal）" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 扫描进度条
            if (isScanning && scanTotal > 0) {
                LinearProgressIndicator(progress = { scanProgress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }

            DropdownMenu(expanded = showAlbumDropdown, onDismissRequest = { showAlbumDropdown = false }) {
                albums.forEach { album ->
                    DropdownMenuItem(text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(album.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (album.name == selectedAlbum) FontWeight.Bold else FontWeight.Normal, color = if (album.name == selectedAlbum) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${album.count}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }, onClick = { haptic.click(); selectedAlbum = album.name; showAlbumDropdown = false })
                }
            }

            // 图片网格（强制一行四张）
            if (displayImages.isEmpty() && !isScanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("此相册中没有动态照片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
                ) {
                    items(displayImages, key = { it.id }) { entry ->
                        val isSelected = selectedUris.contains(entry.uri)
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { haptic.click(); if (isSelected) selectedUris.remove(entry.uri) else selectedUris.add(entry.uri) }
                        ) {
                            ThumbnailImage(uri = entry.uri)
                            if (isSelected) {
                                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
                                Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, contentDescription = "已选择", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    if (isScanning) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbnailImage(uri: Uri) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, android.util.Size(200, 200), null)
                } else {
                    MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, ContentUris.parseId(uri), MediaStore.Images.Thumbnails.MINI_KIND, null)
                }
            } catch (_: Exception) { null }
        }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}
