package com.zsz.zlivephoto

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
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
import com.zsz.zlivephoto.ui.MainScreen
import com.zsz.zlivephoto.ui.ZLivePhotoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private val files = mutableStateListOf<FileItem>()
    private var statusText by mutableStateOf("就绪")
    private var progress by mutableFloatStateOf(0f)
    private var isConverting by mutableStateOf(false)
    private var selectedFormat by mutableStateOf("google")
    // vivo 输出模式：single=单文件（默认） / double=双文件
    private var vivoMode by mutableStateOf("single")
    private var lastExportError: String? = null
    private var showPermissionDialog by mutableStateOf(false)
    private var showSettingsDialog by mutableStateOf(false)

    private lateinit var incomingDir: String
    private lateinit var outputDir: String

    // 动态照片 = 图片 + 伴生视频，必须同时申请图片与视频读取权限
    // （vivo/OPPO 等双文件格式需要直接读取同目录 .mp4，缺视频权限会报
    //   "xxx.mp4: open failed: EACCES"）
    private fun requiredReadPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasReadPermission(): Boolean =
        requiredReadPermissions().any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun hasRequestedReadPermission(): Boolean =
        getSharedPreferences("zlivephoto", MODE_PRIVATE).getBoolean("req_read_media", false)

    private fun markRequestedReadPermission() {
        getSharedPreferences("zlivephoto", MODE_PRIVATE)
            .edit().putBoolean("req_read_media", true).apply()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // 图片/视频任一获得授权即可进入选择器（部分照片访问也算）；
        // 但视频权限缺失时，双文件格式导入会失败，给出提示
        val granted = result.values.any { it }
        if (granted) {
            val videoGranted = result.entries
                .filter { it.key == android.Manifest.permission.READ_MEDIA_VIDEO }
                .all { it.value } ||
                result.entries
                    .filter { it.key == android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED }
                    .all { it.value }
            statusText = if (videoGranted) "已获得读取照片和视频权限"
                         else "已授权，但视频权限缺失：vivo/OPPO 双文件动态照片可能无法导入"
            launchSystemPicker()
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
                    val srcTime = queryMediaModified(uri)
                    val srcTaken = queryMediaTaken(uri)
                    contentResolver.openInputStream(uri)?.use { input ->
                        File(dst).outputStream().use { output -> input.copyTo(output) }
                    }
                    importFromPath(dst, sourceUri = uri.toString(), sourceTime = srcTime, sourceTaken = srcTaken)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { statusText = "导入失败：${e.message}" }
                }
            }
        }
    }

    private fun queryMediaModified(uri: Uri): Long {
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) * 1000L else 0L
            } ?: 0L
        } catch (_: Exception) { 0L }
    }

    /** 查询媒体项的创建时间（拍摄时间 DATE_TAKEN，毫秒）；photo picker 临时 URI 先归一化为真实媒体 URI。 */
    private fun queryMediaTaken(uri: Uri): Long {
        val real = if (Build.VERSION.SDK_INT >= 33 && uri.authority?.contains("photopicker") == true) {
            try { MediaStore.getMediaUri(this, uri) ?: uri } catch (_: Exception) { uri }
        } else uri
        return try {
            contentResolver.query(real, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
            } ?: 0L
        } catch (_: Exception) { 0L }
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
        vivoMode = prefs.getString("vivo_mode", "single") ?: "single"

        // 处理启动时通过分享 Intent 进入的情况
        handleShareIntent(intent)

        setContent {
            ZLivePhotoTheme {
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
                            vivoMode = vivoMode,
                            onSelectVivoMode = { mode ->
                                vivoMode = mode
                                getSharedPreferences("zlivephoto", MODE_PRIVATE)
                                    .edit().putString("vivo_mode", mode).apply()
                            },
                            onRemoveFile = { path -> removeFile(path) }
                )

                // 权限请求对话框（首次或仍可请求时）
                if (showPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            // 拒绝不退出应用，仅关闭对话框
                            showPermissionDialog = false
                        },
                        title = { Text("需要读取照片和视频权限") },
                        text = {
                            Text(
                                "本程序需要读取您设备上的照片和视频：\n\n" +
                                "• 照片权限：选择动态照片文件\n" +
                                "• 视频权限：读取 vivo/OPPO 等双文件格式的伴生视频（缺失会导致导入失败）\n\n" +
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
                        title = { Text("需要手动授予照片和视频权限") },
                        text = {
                            Text(
                                "您之前选择了「不再询问」，系统不再弹出权限对话框。\n\n" +
                                "请前往应用详情页 → 权限 → 照片和视频，手动授予访问权限后返回本应用。\n\n" +
                                "注意：必须同时授予「照片」和「视频」权限，否则 vivo/OPPO 双文件动态照片会导入失败。"
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
                    val srcTime = queryMediaModified(uri)
                    val srcTaken = queryMediaTaken(uri)
                    contentResolver.openInputStream(uri)?.use { input ->
                        File(dst).outputStream().use { output -> input.copyTo(output) }
                    }
                    importFromPath(dst, sourceUri = uri.toString(), sourceTime = srcTime, sourceTaken = srcTaken)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { statusText = "导入失败：${e.message}" }
                }
            }
        }
    }

    // ---------- 权限 ----------

    private fun onAddFiles() {
        if (hasReadPermission()) {
            launchSystemPicker()
        } else {
            // 始终弹出自定义对话框；「拒绝」不退出，下次点击再请求
            showPermissionDialog = true
        }
    }

    private fun launchSystemPicker() {
        systemPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /** vivo 选中且模式为单文件时，实际转换目标是 vivo_single */
    private fun effectiveTargetFormat(): String =
        if (selectedFormat == "vivo" && vivoMode == "single") "vivo_single" else selectedFormat

    private fun onPermissionConfirm() {
        val perms = requiredReadPermissions()
        if (!hasRequestedReadPermission()) {
            // 第一次请求：标记并直接发起系统权限请求
            markRequestedReadPermission()
            requestReadPermission()
        } else if (perms.any { shouldShowRequestPermissionRationale(it) }) {
            // 用户之前拒绝过但未勾选「不再询问」：可正常请求
            requestReadPermission()
        } else {
            // 已请求过且 shouldShow=false：用户选了「不再询问」→ 跳转应用详情
            showSettingsDialog = true
        }
    }

    private fun requestReadPermission() {
        requestPermissionLauncher.launch(requiredReadPermissions())
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
    private fun importFromPath(dst: String, sourceUri: String? = null, sourceTime: Long = 0L, sourceTaken: Long = 0L) {
        val name = File(dst).name
        if (files.any { it.name == name }) return
        val item = FileItem(path = dst, name = name, info = "检测中…",
            sourceUri = sourceUri, sourceTime = sourceTime, sourceTaken = sourceTaken)
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

    /** 按路径移除列表项并清理本地缓存文件（侧滑删除回调）。 */
    private fun removeFile(path: String) {
        val idx = files.indexOfFirst { it.path == path }
        if (idx < 0) return
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
        lastExportError = null

        lifecycleScope.launch(Dispatchers.IO) {
            // 4 路并发转换（Semaphore 限流），单文件异常隔离不中断
            val sem = Semaphore(4)
            val total = targets.size
            val done = java.util.concurrent.atomic.AtomicInteger(0)
            val exported = java.util.concurrent.atomic.AtomicInteger(0)
            val successPaths = java.util.concurrent.ConcurrentLinkedQueue<String>()

            val jobs = targets.map { item ->
                launch {
                    sem.withPermit {
                        withContext(Dispatchers.Main) {
                            val i0 = files.indexOfFirst { it.path == item.path }
                            if (i0 >= 0) files[i0] = files[i0].copy(info = "转换中…")
                        }
                        var n = 0
                        try {
                            val outputs = Converter.convertFile(
                                path = item.path,
                                target = effectiveTargetFormat(),
                                outDir = outputDir,
                                log = { level, msg, tag ->
                                    if (level == "error" || level == "warn") {
                                        lifecycleScope.launch(Dispatchers.Main) { statusText = "[$tag] $msg" }
                                    }
                                }
                            )
                            for (outPath in outputs) {
                                // 始终保留原图修改时间（原文件名时间信息不丢失）
                                val srcTime = if (item.sourceTime > 0L) item.sourceTime else System.currentTimeMillis()
                                File(outPath).setLastModified(srcTime)
                                if (exportToMediaStore(outPath, srcTime) != null) n++
                            }
                            exported.addAndGet(n)
                            successPaths.add(item.path)
                            withContext(Dispatchers.Main) {
                                val i = files.indexOfFirst { it.path == item.path }
                                if (i >= 0) {
                                    files[i] = files[i].copy(info = "完成（导出 $n 个）")
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
                        val d = done.incrementAndGet()
                        withContext(Dispatchers.Main) {
                            progress = d.toFloat() / total
                        }
                    }
                }
            }
            jobs.joinAll()
            withContext(Dispatchers.Main) {
                val exportErr = lastExportError
                statusText = if (exportErr != null)
                    "完成：$total 个文件处理完毕，导出 ${exported.get()} 个到相册（原因：$exportErr）"
                else
                    "完成：$total 个文件处理完毕，导出 ${exported.get()} 个到相册"
                isConverting = false
                val successSet = successPaths.toSet()
                val toRemove = files.filter { it.path in successSet }
                for (item in toRemove) {
                    files.remove(item)
                    try { File(item.path).delete() } catch (_: Exception) {}
                }
                File(outputDir).listFiles()?.forEach { it.delete() }
                progress = 0f
            }
        }
    }
    /**
     * 导出到系统相册（Pictures/Z-LivePhoto-Converter）。
     * 1) MediaStore 标准写入（IS_PENDING，写完才出现在相册）；
     * 2) 失败回退：直接写公共 Pictures 目录 + MediaScannerConnection 触发媒体扫描；
     * 失败原因记录到 lastExportError，随转换结果一起展示。
     */
    private fun exportToMediaStore(srcPath: String, timestamp: Long): Uri? {
        val src = File(srcPath)
        if (!src.exists() || src.length() == 0L) {
            setExportError("转换产物缺失或为空：${src.name}")
            return null
        }
        val ext = src.extension.lowercase()
        val isVideo = ext == "mp4" || ext == "mov"
        val mime = if (isVideo) (if (ext == "mov") "video/quicktime" else "video/mp4") else "image/jpeg"
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        // 1) MediaStore 标准写入（IS_PENDING 流程）
        var inserted: Uri? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Z-LivePhoto-Converter")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                put(MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
            }
            inserted = contentResolver.insert(collection, values)
            if (inserted == null) {
                // 个别 OEM 设备对 VOLUME_EXTERNAL_PRIMARY 支持不佳，退回默认外部卷集合
                val fallback = if (isVideo) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                }
                inserted = contentResolver.insert(fallback, values)
            }
            if (inserted == null) {
                setExportError("MediaStore insert 返回 null")
                throw IllegalStateException("insert null")
            }
            val written = contentResolver.openOutputStream(inserted, "w")?.use { output ->
                src.inputStream().use { input -> input.copyTo(output) }
            } != null
            // 部分设备会在写入完成后用真实写入时间覆盖 DATE_MODIFIED，先固化一次
            if (written) {
                try {
                    val ts = ContentValues().apply {
                        put(MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000)
                        put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
                    }
                    contentResolver.update(inserted, ts, null, null)
                } catch (_: Exception) {}
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    contentResolver.update(inserted, done, null, null)
                } catch (_: Exception) {}
            }
            // IS_PENDING 置 0 发布时个别设备会再刷新一次文件时间，再固化一次
            if (written) {
                try {
                    val ts = ContentValues().apply {
                        put(MediaStore.MediaColumns.DATE_MODIFIED, timestamp / 1000)
                        put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
                    }
                    contentResolver.update(inserted, ts, null, null)
                } catch (_: Exception) {}
            }
            if (written) return inserted
            setExportError("MediaStore 输出流不可用")
        } catch (e: Exception) {
            setExportError("MediaStore 写入失败：${e.message}")
            if (inserted != null) {
                try { contentResolver.delete(inserted, null, null) } catch (_: Exception) {}
            }
        }

        // 2) 回退：直接写公共 Pictures 目录 + 触发媒体扫描（需「所有文件访问权限」/旧版写权限）
        return exportDirectWithScan(src, mime, timestamp)
    }

    /** 回退方案：直接写 Pictures/Z-LivePhoto-Converter 并触发媒体扫描。 */
    private fun exportDirectWithScan(src: File, mime: String, timestamp: Long): Uri? {
        return try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Z-LivePhoto-Converter"
            )
            if (!dir.exists() && !dir.mkdirs()) {
                setExportError("无法创建相册目录 $dir")
                return null
            }
            val dst = File(dir, uniqueName(dir, src.name))
            src.copyTo(dst, overwrite = true)
            dst.setLastModified(timestamp)
            MediaScannerConnection.scanFile(this, arrayOf(dst.absolutePath), arrayOf(mime)) { _, _ -> }
            Uri.fromFile(dst)
        } catch (e: Exception) {
            setExportError("写相册目录失败：${e.message}")
            null
        }
    }

    /** 同名文件存在时追加序号，避免覆盖相册已有文件。 */
    private fun uniqueName(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$stem($i)" else "$stem($i).$ext"
            if (!File(dir, candidate).exists()) return candidate
            i++
        }
    }

    private fun setExportError(msg: String) {
        lastExportError = msg
    }
}
