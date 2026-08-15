package com.zsz.zlivephoto

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.zsz.zlivephoto.core.Converter
import com.zsz.zlivephoto.core.formats.FormatRegistry
import com.zsz.zlivephoto.ui.FileItem
import com.zsz.zlivephoto.ui.ImagePickerScreen
import com.zsz.zlivephoto.ui.MainScreen
import com.zsz.zlivephoto.ui.ZLivePhotoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private val files = mutableStateListOf<FileItem>()
    private var statusText by mutableStateOf("就绪")
    private var progress by mutableFloatStateOf(0f)
    private var isConverting by mutableStateOf(false)
    private var selectedFormat by mutableStateOf("google")
    private var showImagePicker by mutableStateOf(false)
    private var showPermissionDialog by mutableStateOf(false)
    private var showSettingsDialog by mutableStateOf(false)

    private lateinit var incomingDir: String
    private lateinit var outputDir: String

    private fun requiredReadPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun hasReadPermission(): Boolean =
        checkSelfPermission(requiredReadPermission()) == PackageManager.PERMISSION_GRANTED

    private fun hasRequestedReadPermission(): Boolean =
        getSharedPreferences("zlivephoto", MODE_PRIVATE).getBoolean("req_read_media", false)

    private fun markRequestedReadPermission() {
        getSharedPreferences("zlivephoto", MODE_PRIVATE)
            .edit().putBoolean("req_read_media", true).apply()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        if (granted) {
            statusText = "已获得读取照片权限"
            showImagePicker = true
        } else {
            statusText = "未授予权限，可再次点击「添加文件」"
        }
    }

    // 系统照片选择器
    private val systemPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNullOrEmpty()) {
            statusText = "未选择任何文件"
            return@registerForActivityResult
        }
        statusText = "正在导入 ${uris.size} 个文件…"
        lifecycleScope.launch(Dispatchers.IO) {
            for (uri in uris) {
                try {
                    val name = queryDisplayName(uri) ?: "photo_${System.currentTimeMillis()}.jpg"
                    val dst = File(incomingDir, name).path
                    contentResolver.openInputStream(uri)?.use { input ->
                        File(dst).outputStream().use { output -> input.copyTo(output) }
                    }
                    importFromPath(dst)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { statusText = "导入失败：${e.message}" }
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        try {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        } catch (_: Exception) {}
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install()

        incomingDir = File(filesDir, "incoming").absolutePath
        outputDir = File(filesDir, "output").absolutePath
        File(incomingDir).mkdirs()
        File(outputDir).mkdirs()

        val prefs = getSharedPreferences("zlivephoto", MODE_PRIVATE)
        selectedFormat = prefs.getString("target", "google") ?: "google"

        // 处理启动时通过分享 Intent 进入的情况
        handleShareIntent(intent)

        setContent {
            ZLivePhotoTheme {
                Crossfade(
                    targetState = showImagePicker,
                    animationSpec = tween(300),
                    label = "page_switch"
                ) { isPicker ->
                    if (isPicker) {
                        ImagePickerScreen(
                            onConfirm = { paths ->
                                showImagePicker = false
                                paths.forEach { importWithVideo(File(it)) }
                            },
                            onDismiss = { showImagePicker = false },
                            onUseSystemPicker = {
                                showImagePicker = false
                                systemPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    } else {
                        MainScreen(
                            files = files,
                            statusText = statusText,
                            progress = progress,
                            isConverting = isConverting,
                            selectedFormat = selectedFormat,
                            onAddFiles = { onAddFiles() },
                            onClearFiles = { clearFiles() },
                            onConvert = { startConvert() },
                            onSelectFormat = { fmt ->
                                selectedFormat = fmt
                                getSharedPreferences("zlivephoto", MODE_PRIVATE)
                                    .edit().putString("target", fmt).apply()
                            },
                            onRemoveFile = { idx -> removeFile(idx) }
                        )
                    }
                }

                // 权限请求对话框（首次或仍可请求时）
                if (showPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            // 拒绝不退出应用，仅关闭对话框
                            showPermissionDialog = false
                        },
                        title = { Text("需要读取照片权限") },
                        text = {
                            Text(
                                "本程序需要读取您设备上的照片，用于选择动态照片文件并自动查找同目录的视频文件。\n\n" +
                                "拒绝后无法选择文件，可稍后再次点击「添加文件」重新授权。"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showPermissionDialog = false
                                onPermissionConfirm()
                            }) { Text("授权") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showPermissionDialog = false
                            }) { Text("拒绝") }
                        }
                    )
                }

                // 跳转设置对话框（用户选了「不再询问」）
                if (showSettingsDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showSettingsDialog = false
                        },
                        title = { Text("需要手动授予读取照片权限") },
                        text = {
                            Text(
                                "您之前选择了「不再询问」，系统不再弹出权限对话框。\n\n" +
                                "请前往应用详情页 → 权限 → 照片和视频，手动授予访问权限后返回本应用。"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showSettingsDialog = false
                                openAppDetailSettings()
                            }) { Text("去设置") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showSettingsDialog = false
                            }) { Text("取消") }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 应用已启动，将分享图片追加到现有列表
        handleShareIntent(intent)
    }

    // ---------- 分享 Intent 处理 ----------

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null || intent.action == null) return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                if (uri != null) listOf(uri) else emptyList()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
                }
            }
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        statusText = "正在导入分享的 ${uris.size} 张图片…"
        lifecycleScope.launch(Dispatchers.IO) {
            for (uri in uris) {
                try {
                    val name = queryDisplayName(uri) ?: "shared_${System.currentTimeMillis()}.jpg"
                    val dst = File(incomingDir, name).path
                    contentResolver.openInputStream(uri)?.use { input ->
                        File(dst).outputStream().use { output -> input.copyTo(output) }
                    }
                    importFromPath(dst)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { statusText = "导入失败：${e.message}" }
                }
            }
        }
    }

    // ---------- 权限 ----------

    private fun onAddFiles() {
        if (hasReadPermission()) {
            showImagePicker = true
        } else {
            // 始终弹出自定义对话框；「拒绝」不退出，下次点击再请求
            showPermissionDialog = true
        }
    }

    private fun onPermissionConfirm() {
        val perm = requiredReadPermission()
        if (!hasRequestedReadPermission()) {
            // 第一次请求：标记并直接发起系统权限请求
            markRequestedReadPermission()
            requestReadPermission()
        } else if (shouldShowRequestPermissionRationale(perm)) {
            // 用户之前拒绝过但未勾选「不再询问」：可正常请求
            requestReadPermission()
        } else {
            // 已请求过且 shouldShow=false：用户选了「不再询问」→ 跳转应用详情
            showSettingsDialog = true
        }
    }

    private fun requestReadPermission() {
        requestPermissionLauncher.launch(arrayOf(requiredReadPermission()))
    }

    private fun openAppDetailSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {}
    }

    // ---------- 文件导入 ----------

    // 系统选择器/分享导入（content URI 已复制为本地文件，无需查找同目录视频）
    private fun importFromPath(dst: String) {
        val name = File(dst).name
        if (files.any { it.name == name }) return
        val item = FileItem(path = dst, name = name, info = "检测中…")
        files.add(item)

        lifecycleScope.launch(Dispatchers.IO) {
            val (plugin, score) = FormatRegistry.detectBest(dst)
            withContext(Dispatchers.Main) {
                val idx = files.indexOfFirst { it.path == dst }
                if (idx >= 0) {
                    val recognized = plugin != null && score >= 50
                    files[idx] = files[idx].copy(
                        info = if (recognized) plugin!!.display else "未识别的动态照片格式",
                        isUnrecognized = !recognized
                    )
                }
            }
        }
    }

    private fun importWithVideo(jpg: File) {
        try {
            val name = jpg.name
            val stem = jpg.nameWithoutExtension
            val dst = File(incomingDir, name).path
            jpg.copyTo(File(dst), overwrite = true)

            val parent = jpg.parentFile
            var videoFile: File? = null
            if (parent != null) {
                for (ext in listOf("mp4", "MP4", "mov", "MOV")) {
                    val v = File(parent, "$stem.$ext")
                    if (v.exists() && v.isFile) {
                        videoFile = v
                        break
                    }
                }
            }

            if (videoFile != null) {
                val videoDst = File(incomingDir, videoFile.name).path
                videoFile.copyTo(File(videoDst), overwrite = true)
            }

            if (files.any { it.name == name }) {
                statusText = "$name 已在列表中"
                return
            }
            val item = FileItem(path = dst, name = name, info = "检测中…")
            files.add(item)

            lifecycleScope.launch(Dispatchers.IO) {
                val (plugin, score) = FormatRegistry.detectBest(dst)
                withContext(Dispatchers.Main) {
                    val idx = files.indexOfFirst { it.path == dst }
                    if (idx >= 0) {
                        val recognized = plugin != null && score >= 50
                        files[idx] = files[idx].copy(
                            info = if (recognized) plugin!!.display else "未识别的动态照片格式",
                            isUnrecognized = !recognized
                        )
                    }
                }
            }

            statusText = if (videoFile != null) {
                "已导入 $name + ${videoFile.name}"
            } else {
                "已导入 $name"
            }
        } catch (e: Exception) {
            statusText = "导入失败：${e.message}"
        }
    }

    private fun removeFile(idx: Int) {
        if (idx < 0 || idx >= files.size) return
        val item = files[idx]
        try { File(item.path).delete() } catch (_: Exception) {}
        files.removeAt(idx)
        statusText = "已移除 ${item.name}"
    }

    private fun clearFiles() {
        files.clear()
        File(incomingDir).listFiles()?.forEach { it.delete() }
        File(outputDir).listFiles()?.forEach { it.delete() }
        progress = 0f
        statusText = "已清空"
    }

    // ---------- 转换 ----------

    private fun startConvert() {
        if (isConverting) return
        val targets = files.filter { !it.info.contains("未识别") && !it.info.contains("失败") && !it.info.contains("完成") }.toList()
        if (targets.isEmpty()) {
            statusText = "没有可转换的文件"
            return
        }

        isConverting = true
        progress = 0f

        lifecycleScope.launch(Dispatchers.IO) {
            var done = 0
            var exported = 0
            val successPaths = mutableListOf<String>()
            for (item in targets) {
                val idx = files.indexOfFirst { it.path == item.path }
                if (idx >= 0) {
                    withContext(Dispatchers.Main) {
                        files[idx] = files[idx].copy(info = "转换中…")
                    }
                }
                try {
                    val outputs = Converter.convertFile(
                        path = item.path,
                        target = selectedFormat,
                        outDir = outputDir,
                        log = { level, msg, tag ->
                            if (level == "error" || level == "warn") {
                                lifecycleScope.launch(Dispatchers.Main) { statusText = "[$tag] $msg" }
                            }
                        }
                    )
                    var n = 0
                    for (outPath in outputs) {
                        val srcTime = File(item.path).lastModified()
                        File(outPath).setLastModified(srcTime)
                        if (exportToMediaStore(outPath, srcTime) != null) n++
                    }
                    exported += n
                    successPaths.add(item.path)
                    withContext(Dispatchers.Main) {
                        val i = files.indexOfFirst { it.path == item.path }
                        if (i >= 0) {
                            files[i] = files[i].copy(info = "完成（导出 $n 个到相册）")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val i = files.indexOfFirst { it.path == item.path }
                        if (i >= 0) {
                            files[i] = files[i].copy(info = "失败：${e.message}")
                        }
                    }
                }
                done++
                withContext(Dispatchers.Main) {
                    progress = done.toFloat() / targets.size
                }
            }
            withContext(Dispatchers.Main) {
                statusText = "完成：$done 个文件处理完毕，导出 $exported 个到相册（Pictures/Z-LivePhoto-Converter）"
                isConverting = false
                // 只移除成功的文件，失败/跳过的保留在列表中便于重试
                val successSet = successPaths.toSet()
                val toRemove = files.filter { it.path in successSet }
                for (item in toRemove) {
                    files.remove(item)
                    try { File(item.path).delete() } catch (_: Exception) {}
                }
                // 清理 output 临时目录（incoming 中失败文件保留）
                File(outputDir).listFiles()?.forEach { it.delete() }
                progress = 0f
            }
        }
    }

    private fun exportToMediaStore(srcPath: String, timestamp: Long): Uri? {
        try {
            val ext = File(srcPath).extension.lowercase()
            val isVideo = ext == "mp4" || ext == "mov"
            // 所有文件统一输出到 Pictures/Z-LivePhoto-Converter，保证双文件格式的图片和视频在一起
            val values = ContentValues().apply {
                put("_display_name", File(srcPath).name)
                put("mime_type", if (isVideo) (if (ext == "mov") "video/quicktime" else "video/mp4") else "image/jpeg")
                put("relative_path", Environment.DIRECTORY_PICTURES + "/Z-LivePhoto-Converter")
                put("date_modified", timestamp / 1000)
            }
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val uri = contentResolver.insert(collection, values) ?: return null
            contentResolver.openOutputStream(uri, "w")?.use { output ->
                File(srcPath).inputStream().use { input -> input.copyTo(output) }
            }
            return uri
        } catch (e: Exception) {
            return null
        }
    }
}
