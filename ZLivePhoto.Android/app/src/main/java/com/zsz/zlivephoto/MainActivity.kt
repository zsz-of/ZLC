package com.zsz.zlivephoto

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.zsz.zlivephoto.core.Converter
import com.zsz.zlivephoto.core.FfmpegAddon
import com.zsz.zlivephoto.core.IconManager
import com.zsz.zlivephoto.core.QuickClassify
import com.zsz.zlivephoto.core.UpdateChecker
import com.zsz.zlivephoto.core.UpdateCheckResult
import com.zsz.zlivephoto.core.formats.FormatRegistry
import com.zsz.zlivephoto.ui.AppSettings
import com.zsz.zlivephoto.ui.FancyEasing
import com.zsz.zlivephoto.ui.FileItem
import com.zsz.zlivephoto.ui.MainScreen
import com.zsz.zlivephoto.ui.Md3Checkbox
import com.zsz.zlivephoto.ui.SettingsScreen
import com.zsz.zlivephoto.ui.ZLivePhotoTheme
import com.zsz.zlivephoto.ui.UpdateFlowHosts
import com.zsz.zlivephoto.ui.FfmpegFlowHosts
import com.zsz.zlivephoto.ui.rememberFfmpegFlow
import com.zsz.zlivephoto.ui.rememberHapticFeedback
import com.zsz.zlivephoto.ui.rememberUpdateFlow
import com.zsz.zlivephoto.ui.wallpaperHue
import com.zsz.zlivephoto.ui.picker.AlbumInfo
import com.zsz.zlivephoto.ui.picker.AlbumScanner
import com.zsz.zlivephoto.ui.picker.MediaItem
import com.zsz.zlivephoto.ui.picker.MediaRepo
import com.zsz.zlivephoto.ui.picker.PhotoPickerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

/** 文件名冲突处理动作 */
internal enum class ConflictAction { SKIP, OVERWRITE, RENAME }

/** 冲突询问请求（挂起协程 ↔ 弹窗之间的桥） */
private class ConflictRequest(
    val displayNames: String,
    val onChoose: (ConflictAction) -> Unit
)

class MainActivity : ComponentActivity() {

    private val files = mutableStateListOf<FileItem>()
    private var statusText by mutableStateOf("就绪")
    private var progress by mutableFloatStateOf(0f)
    private var isConverting by mutableStateOf(false)
    private var selectedFormat by mutableStateOf("google")
    private var lastExportError: String? = null
    private var showPermissionDialog by mutableStateOf(false)
    private var showSettingsDialog by mutableStateOf(false)
    private var showAllFilesDialog by mutableStateOf(false)
    // 清空/处理收尾过程中（清空按钮须禁用，防止动画期间重复触发或状态错乱）
    private var clearBusy by mutableStateOf(false)
    // 防抖落盘任务：识别完成等高频稳定态回调合并为一次 JSON 写入
    private var stablePersistJob: kotlinx.coroutines.Job? = null
    // Compose 内创建的触觉反馈控制器引用（清空/完成批量清理的快速两下振动用）
    private var hapticController: com.zsz.zlivephoto.ui.HapticController? = null
    // 识别检测用有界调度器：限制并发文件检测数，避免批量导入 >500 张时
    // 并发协程过多导致资源耗尽崩溃
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val detectionDispatcher = Dispatchers.IO.limitedParallelism(
        if (BuildConfig.FLAVOR == "go") 1 else 4
    )
    // 自上次落盘以来列表变更累计次数（每 50 条写一次本地 JSON）
    private var dirtyCount = 0

    // 进度第二行明细：列表转换「已处理 X/Y」；批量导入「已导入 X/Y」
    private var progressDetail by mutableStateOf("")
    // 内置选择器导入进度（已添加/总需添加个数）
    private var isImporting by mutableStateOf(false)
    private var importProgress by mutableFloatStateOf(0f)
    // 终止转换请求：处理完当前文件后立即停止
    @Volatile private var stopRequested = false
    // 终止导入请求：导入循环下一项前检查，立即停止不再继续导入
    @Volatile private var stopImportRequested = false
    // 内置选择器打开流程进行中（读相册列表）：完成前禁用「添加文件」按钮
    private var isPickerOpening by mutableStateOf(false)
    // 显式跟踪系统深浅色（uiMode configChanges 下 LocalConfiguration 传播不可靠，
    // 部分 picker 控件（排序按钮/日期头/张数文本）曾不随主题切换）
    private var isDarkTheme by mutableStateOf(false)

    // 动态取色时响应系统壁纸颜色变化：注册监听后，应用在后台时桌面图标可实时
    // 跟随壁纸取色。前台禁用正在使用的入口 alias 会被系统中止 Activity（闪退），
    // 因此仅当 iconApplySafe（已 onStop）时才真正切换；前台期间发生的壁纸变化
    // 由用户退出到桌面时 onStop 里的同步兜底。
    private var iconApplySafe = true
    private var wallpaperColorsListener: WallpaperManager.OnColorsChangedListener? = null

    // 文件名冲突弹窗（批量与单个转换共用）
    private var conflictRequest by mutableStateOf<ConflictRequest?>(null)
    private val conflictMutex = Mutex()
    /** 本次转换已占用（或计划占用）的输出名，避免并发转换重复决策 */
    private val claimedNames = mutableSetOf<Pair<String, String>>() // (relSubDir, fileName)
    /** 冲突弹窗「本批次总是」复选框状态（每次弹窗前重置） */
    private var conflictAlways by mutableStateOf(false)
    /** 本批次内记住的冲突处理动作（null=每次询问；批次开始时清空，仅当前批次有效） */
    @Volatile private var batchConflictAction: ConflictAction? = null

    // ---------- 项10：处理完成后删除原图 ----------
    /** 开关（持久化；处理中控制区隐藏不可切换，保证批次语义确定） */
    private var deleteOriginal by mutableStateOf(false)
    /** 本批次成功处理后待删除的原图 URI（按源路径分组；IO 线程并发合并需加锁） */
    private val pendingDeleteBySource = LinkedHashMap<String, MutableList<Uri>>()
    private val pendingDeleteLock = Any()
    /** 覆盖冲突保护：被覆盖的输出就是原文件本身（源位于输出目录内）时禁止删除，
     *  否则新导出的产物会被移入回收站造成永久丢失 */
    private val protectedOriginalPaths = ConcurrentHashMap.newKeySet<String>()
    /** 删除结果回调时展示的项数与批次完成状态 */
    private var pendingDeleteCount = 0
    private var deleteBaseStatus = ""

    // 内置选择器（默认入口；系统选择器作为备选保留）
    private lateinit var mediaRepo: MediaRepo
    private lateinit var scanner: AlbumScanner
    // 合成模式选择器：扫描普通照片+视频（供「合成动态照片」按序号配对）
    private lateinit var composeScanner: AlbumScanner
    // 当前打开的选择器是否为合成模式
    private var pickerComposeMode by mutableStateOf(false)
    /** 顶层页面路由：Main 主页 / Picker 内置选择器 / Settings 设置页 */
    private enum class AppScreen { Main, Picker, Settings }
    private var screen by mutableStateOf(AppScreen.Main)
    private var pickerAlbums by mutableStateOf<List<AlbumInfo>>(emptyList())

    private lateinit var incomingDir: String
    private lateinit var outputDir: String

    // 动态照片 = 图片 + 伴生视频，必须同时申请图片与视频读取权限
    // （vivo/OPPO 等双文件格式需要直接读取同目录 .mp4，缺视频权限会报
    //   "xxx.mp4: open failed: EACCES"）
    private fun requiredReadPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            android.Manifest.permission.ACCESS_MEDIA_LOCATION
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.ACCESS_MEDIA_LOCATION
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.ACCESS_MEDIA_LOCATION
        )
        // Android 9-：READ 读取媒体；WRITE 用于导出到公共 Pictures / 删除原图
        // （WRITE_EXTERNAL_STORAGE 仅 Android 9- 生效，manifest 已限 maxSdk 29）
        else -> arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    // 媒体访问能力（不含 ACCESS_MEDIA_LOCATION）：任一媒体读取权限授予即可进入选择器
    private fun hasReadPermission(): Boolean =
        requiredReadPermissions()
            .filter { it != android.Manifest.permission.ACCESS_MEDIA_LOCATION }
            .any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    // Android 10+：缺 ACCESS_MEDIA_LOCATION 时系统（MediaStore/FUSE）会脱敏 GPS EXIF，
    // 导致转换后照片丢失位置信息。必须单独确保该权限授予。
    private fun hasMediaLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return checkSelfPermission(android.Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    // 位置权限授予后要执行的后续动作（打开内置选择器 / 启动批量选择器）
    private var pendingActionAfterLocation: (() -> Unit)? = null

    private fun ensureLocationThen(action: () -> Unit) {
        if (hasMediaLocationPermission()) {
            action()
        } else {
            pendingActionAfterLocation = action
            requestLocationPermissionLauncher.launch(
                android.Manifest.permission.ACCESS_MEDIA_LOCATION
            )
        }
    }

    // 单独请求 ACCESS_MEDIA_LOCATION（媒体权限已授予但缺位置权限时触发）
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingActionAfterLocation
        pendingActionAfterLocation = null
        statusText = if (granted) "已获得位置权限，转换后将保留 GPS 元数据"
                     else "未授予位置权限：转换后的照片将丢失 GPS 位置信息"
        action?.invoke()
    }

    // Android 11+ 是否已授予「所有文件访问」权限
    private fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    // 「所有文件访问」授权页返回后：刷新状态提示
    private val allFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        statusText = if (hasAllFilesAccess())
            "已获得「所有文件访问」权限，转换将直写相册目录完整保留位置信息"
        else
            "未授予「所有文件访问」权限：个别机型转换后可能丢失位置信息"
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.fromParts("package", packageName, null)
            allFilesAccessLauncher.launch(intent)
        } catch (_: Exception) {}
    }

    /** 仅在「有媒体读取权限但缺所有文件访问（R+）」时弹一次引导（持久化标记避免反复打扰） */
    private fun maybePromptAllFilesAccess() {
        if (!hasReadPermission()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (hasAllFilesAccess()) return
        val prefs = getSharedPreferences("zlivephoto", MODE_PRIVATE)
        if (prefs.getBoolean("all_files_prompted", false)) return
        prefs.edit().putBoolean("all_files_prompted", true).apply()
        showAllFilesDialog = true
    }

    // 媒体权限授予后要执行的动作（默认进入内置选择器；批量导入则继续打开文件夹选择器）
    private var pendingPermissionAction: (() -> Unit)? = null

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
                         else "已授权，但视频权限缺失：无法查找双文件动态照片附带的伴生视频"
            // 授权后若缺「所有文件访问」（R+）弹一次引导，便于后续直写磁盘保留位置
            maybePromptAllFilesAccess()
            // 执行授权前挂起的动作（批量导入→文件夹选择器；默认→内置选择器）
            val action = pendingPermissionAction
            pendingPermissionAction = null
            (action ?: { openBuiltInPicker() })()
        } else {
            statusText = "未授予权限，可再次点击「添加文件」"
        }
    }

    // 批量导入文件夹选择器
    private val batchFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            statusText = "未选择文件夹"
            return@registerForActivityResult
        }
        val rootPath = treeUriToFilePath(uri)
        if (rootPath == null) {
            statusText = "无法访问所选文件夹（仅支持本地存储目录）"
            return@registerForActivityResult
        }
        importBatchFolder(rootPath)
    }

    // 项10：系统删除工具（回收站）结果回调——整批仅一次请求
    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val base = deleteBaseStatus
        statusText = if (result.resultCode == RESULT_OK)
            "$base；原图已移入回收站（${pendingDeleteCount} 项）"
        else
            "$base；已取消删除原图"
        pendingDeleteCount = 0
    }

    /** 把 OpenDocumentTree 的树 URI 解析为本地存储绝对路径。 */
    private fun treeUriToFilePath(treeUri: Uri): String? {
        return try {
            if (treeUri.authority != "com.android.externalstorage.documents") return null
            externalStorageDocToPath(DocumentsContract.getTreeDocumentId(treeUri))
        } catch (_: Exception) { null }
    }

    /** 把外部存储 Documents docId（形如 "primary:DCIM/xxx.jpg"）映射为绝对路径。 */
    private fun externalStorageDocToPath(docId: String): String? {
        val idx = docId.indexOf(':')
        if (idx < 0) return null
        val volume = docId.substring(0, idx)
        val path = docId.substring(idx + 1)
        val volumeRoot = when (volume) {
            "primary" -> Environment.getExternalStorageDirectory().absolutePath
            else -> "/storage/$volume"
        }
        return if (path.isEmpty()) volumeRoot else File(volumeRoot, path).absolutePath
    }

    /** 系统选择器在 Android 9- 上退化为 ACTION_OPEN_DOCUMENT（ExternalStorageProvider 文档 URI），
     *  尝试把文档 URI 映射回绝对路径。 */
    private fun resolveDocumentPath(uri: Uri): String? {
        return try {
            if (uri.authority != "com.android.externalstorage.documents") return null
            externalStorageDocToPath(DocumentsContract.getDocumentId(uri))
        } catch (_: Exception) { null }
    }

    /** 查询 openable URI 的显示文件名（不可用时返回 null）。 */
    private fun queryOpenableName(uri: Uri): String? {
        return try {
            contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    /**
     * 导入单个已选中的媒体 URI（须在 IO 协程内调用）。
     * 引用模式：解析出原始文件绝对路径后直接引用，不复制到应用缓存（零空间占用，
     * 且原文件 EXIF/位置/镜头数据天然完整保留）。
     * 仅当路径不可直读（文档 URI / 个别受限环境）时才回退为 content 流复制到 incoming。
     */
    private suspend fun importPickedUri(uri: Uri) {
        // ① 解析原始文件绝对路径：媒体库 DATA → 外部存储文档 URI（Android 9- 系统选择器退化场景）
        var srcPath = resolveMediaPath(uri)
        if (srcPath == null) srcPath = resolveDocumentPath(uri)
        val srcFile = srcPath?.let { File(it) }
        if (srcFile != null && srcFile.exists() && srcFile.canRead()) {
            // ② 引用模式：直接使用原文件路径（时间戳查询失败时回退文件 mtime）
            val mod = queryMediaModified(uri).takeIf { it > 0L } ?: srcFile.lastModified()
            val taken = queryMediaTaken(uri).takeIf { it > 0L } ?: mod
            importFromPath(
                path = srcPath, sourceUri = uri.toString(), sourcePath = srcPath,
                sourceTime = mod, sourceTaken = taken
            )
        } else {
            // ③ 受限环境/文档 URI 回退：content 流复制到 incoming 再处理（保字节，EXIF 完整）
            val name = queryOpenableName(uri) ?: "import_${System.currentTimeMillis()}"
            val dst = File(incomingDir, name).path
            contentResolver.openInputStream(uri)?.use { input ->
                File(dst).outputStream().use { input.copyTo(it) }
            } ?: throw IOException("无法读取所选文件")
            val dstFile = File(dst)
            importFromPath(
                path = dst, sourceUri = uri.toString(),
                sourceTime = dstFile.lastModified().takeIf { dstFile.exists() } ?: 0L,
                sourceTaken = 0L
            )
        }
    }

    /** 解析媒体 URI 的原始绝对路径（DATA 列）；Photo Picker 临时 URI 先归一化为媒体 URI。 */
    private fun resolveMediaPath(uri: Uri): String? {
        val real = if (Build.VERSION.SDK_INT >= 33 && uri.authority?.contains("photopicker") == true) {
            try { MediaStore.getMediaUri(this, uri) ?: uri } catch (_: Exception) { uri }
        } else uri
        return try {
            contentResolver.query(real, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    /** 校验文件头为合法 MP4/MOV（偏移 4 处的 ftyp box）。 */
    private fun hasValidMp4Head(f: File): Boolean {
        return try {
            java.io.RandomAccessFile(f, "r").use { raf ->
                val head = ByteArray(12)
                raf.readFully(head)
                com.zsz.zlivephoto.core.Mp4Util.hasFtyp(head)
            }
        } catch (_: Exception) { false }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.install()

        // 全局设置（主题/触感/弹性动画/更新检查）：首帧组合前初始化
        AppSettings.init(this)
        // 转码器附加项（下载/校验/解压/调用 ffmpeg）：需在首帧组合前初始化，
        // 否则设置页读取 appCtx / prefs 时抛 lateinit property appCtx has not been initialized
        FfmpegAddon.init(this)

        incomingDir = File(filesDir, "incoming").absolutePath
        outputDir = File(filesDir, "output").absolutePath
        File(incomingDir).mkdirs()
        File(outputDir).mkdirs()
        // 启动时自动清理缓存（上次残留的暂存文件），避免占用空间无限膨胀
        cleanupAllCaches()

        // 恢复上次会话的列表（本地 JSON）；异常退出则清空并提示
        when (val s = readListState()) {
            is ListState.Loaded -> {
                files.addAll(s.files)
                statusText = "已恢复上次会话（${s.files.size} 个文件）"
                // 上次退出时仍在检测中的项，重新触发识别
                s.files.forEach { f ->
                    if (f.info == "检测中…") launchDetection(f.path, f.sourcePath ?: f.path)
                }
            }
            is ListState.AbnormalExit ->
                statusText = "检测到上次异常退出，已清空未完成的列表"
            ListState.Corrupt, ListState.Empty -> {}
        }

        mediaRepo = MediaRepo(contentResolver)
        scanner = AlbumScanner(mediaRepo)
        composeScanner = AlbumScanner(mediaRepo, composeMode = true)

        val prefs = getSharedPreferences("zlivephoto", MODE_PRIVATE)
        // 迁移：旧版 vivo + vivo_mode=single → 新版顶级选项 vivo_single
        selectedFormat = prefs.getString("target", "google") ?: "google"
        if (selectedFormat == "vivo" && prefs.getString("vivo_mode", "single") == "single") {
            selectedFormat = "vivo_single"
        }
        deleteOriginal = prefs.getBoolean("delete_original", false)

        // 初始深浅色（后续由 onConfigurationChanged / 设置项变化实时跟踪）
        isDarkTheme = computeDarkTheme()

        // 处理启动时通过分享 Intent 进入的情况
        handleShareIntent(intent)

        // 动态取色：系统壁纸/取色变化时，应用在后台则同步桌面图标颜色
        registerWallpaperColorListener()

        setContent {
            ZLivePhotoTheme(darkTheme = isDarkTheme) {
                // 全局触觉反馈：弹窗按钮等无独立交互源的控件统一使用
                val haptic = rememberHapticFeedback()
                hapticController = haptic
                // 主列表滚动状态（LazyColumn 复位/保持由 MainScreen 使用）
                val listState = rememberLazyListState()
                // 深色模式三选变化：同步 isDarkTheme 驱动主题与选择器即时切换
                LaunchedEffect(AppSettings.themeMode) {
                    isDarkTheme = computeDarkTheme()
                }
                // 动态取色开关变化：已在 AppSettings.setUseDynamicTheme 内同步写好
                // liveDynamicHue（与自定义取色同一模式），此处无需再做异步刷新；
                // 壁纸颜色变化则由 registerWallpaperColorListener 驱动同一内存态。
                // 处理过程中吞掉系统返回键（预测式返回下同样生效）
                BackHandler(enabled = isConverting) { /* 处理中不响应返回 */ }

                // 桌面图标跟随主题色：仅冷启动应用一次（前台运行时禁用正在使用的入口
                // alias 会导致系统停掉本 Activity → 闪退；主题色变化后的同步在 onStop
                // 完成，动态取色下系统壁纸颜色变化由 registerWallpaperColorListener 驱动）
                LaunchedEffect(Unit) {
                    IconManager.apply(this@MainActivity)
                    // 已有媒体读取权限但缺「所有文件访问」（R+）时，弹一次引导（升级用户路径）
                    maybePromptAllFilesAccess()
                }

                // 更新弹窗状态（发现新版本 4 按钮 / 说明子弹窗 / 下载进度 / 蓝奏失败回退）
                val updateFlow = rememberUpdateFlow()

                // 转码器（ffmpeg 附加项）流程状态：检查更新 / 重新安装 / 下载通道选择
                val ffmpegFlow = rememberFfmpegFlow()

                // 启动时自动检查更新（设置开启时）：后台比对 GitHub 最新 release；
                // 用户点过「跳过此版本」的版本不再自动弹窗；无软件更新时再静默检查转码器版本
                LaunchedEffect(Unit) {
                    if (AppSettings.checkUpdateOnStartup) {
                        val r = UpdateChecker.check(BuildConfig.VERSION_NAME)
                        if (r is UpdateCheckResult.Update &&
                            !AppSettings.isVersionSkipped(r.info.version)
                        ) {
                            updateFlow.present(r.info)
                        } else if (BuildConfig.FLAVOR != "go") {
                            // 无软件更新：检查转码器（已是最新则不弹提示，版本不匹配/未安装则弹窗）
                            ffmpegFlow.checkUpdate(quiet = true)
                        }
                    }
                }

                // 发现新版本 / 更新说明 / 下载进度 / 蓝奏失败回退等弹窗统一在这里渲染
                UpdateFlowHosts(updateFlow, vibrate = { haptic.click() })

                // 转码器相关的下载进度 / 结果提示 / 下载通道选择弹窗
                FfmpegFlowHosts(ffmpegFlow, vibrate = { haptic.click() })

                // 预览式返回：选择器 / 设置页手势进度驱动内容缩小右移（顶层稳定注册，
                // 不受 AnimatedContent 过渡重组影响；提交后回到主页）
                val backAnim = remember { Animatable(0f) }
                // 预览式返回：go 轻量版不启用（进一步简化）
                PredictiveBackHandler(enabled = BuildConfig.FLAVOR != "go" &&
                    (screen == AppScreen.Picker || screen == AppScreen.Settings)) { flow ->
                    try {
                        flow.collect { backAnim.snapTo(it.progress) }
                        screen = AppScreen.Main
                        backAnim.snapTo(0f)
                    } catch (_: CancellationException) {
                        backAnim.animateTo(0f, tween(200))
                    }
                }
                // 系统返回键：选择器 / 设置页返回上一级（主页），不退出应用；
                // 处理中由上方 BackHandler(enabled = isConverting) 吞掉，二者不冲突
                BackHandler(enabled = screen == AppScreen.Picker || screen == AppScreen.Settings) {
                    screen = AppScreen.Main
                }

                // 进入/退出内置选择器的过渡动画（ImageToolbox fancySlideTransition 式）：
                // 选择器从右侧整屏滑入 + 淡入，主页向左小幅滑出让位；返回时反向。
                // 设置页用淡入淡出 + 轻微缩放过渡。
                AnimatedContent(
                    targetState = screen,
                    modifier = Modifier
                        .fillMaxSize()
                        // 过渡动画期间两屏交错露出的底层必须使用主题背景，
                        // 否则深色模式下会露出 Activity 窗口默认的白色背景
                        .background(MaterialTheme.colorScheme.background)
                        .graphicsLayer {
                            val p = backAnim.value
                            if (p > 0f) {
                                scaleX = 1f - p * 0.06f
                                scaleY = 1f - p * 0.06f
                                translationX = p * size.width * 0.18f
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            }
                        },
                    transitionSpec = {
                        // 用 initialState / targetState 判断方向（screen 是可变的当前目标，
                        // 动画开始时已指向目标页，用它判断「从主页进入」必然失败 → 会退化到兜底淡入）
                        val slideIn = initialState == AppScreen.Main && (targetState == AppScreen.Picker || targetState == AppScreen.Settings)
                        val slideOut = (initialState == AppScreen.Picker || initialState == AppScreen.Settings) && targetState == AppScreen.Main
                        when {
                            // 主页 → 选择器 / 设置页：目标页从右整屏推入，主页整屏被推向左，对称推拉
                            slideIn ->
                                (slideInHorizontally(tween(450, easing = FancyEasing)) { it } +
                                        fadeIn(tween(300, 100))) togetherWith
                                        (slideOutHorizontally(tween(450, easing = FancyEasing)) { -it } +
                                                fadeOut(tween(300)))
                            // 选择器 / 设置页 → 主页：反向对称推拉（主页整屏从左侧拉入，当前页整屏推出）
                            slideOut ->
                                (slideInHorizontally(tween(450, easing = FancyEasing)) { -it } +
                                        fadeIn(tween(300, 100))) togetherWith
                                        (slideOutHorizontally(tween(450, easing = FancyEasing)) { it } +
                                                fadeOut(tween(300)))
                            // 其余过渡（兜底）：淡入淡出 + 轻微缩放
                            else ->
                                (fadeIn(tween(250)) +
                                        scaleIn(initialScale = 0.97f, animationSpec = tween(300, easing = FancyEasing))) togetherWith
                                        (fadeOut(tween(180)))
                        }
                    },
                    label = "screenTransition"
                ) { page ->
                    if (page == AppScreen.Picker) {
                        // 内置选择器（默认）：MediaStore 直查，路径第一手，EXIF 完整保留
                        PhotoPickerScreen(
                            albums = pickerAlbums,
                            scanner = if (pickerComposeMode) composeScanner else scanner,
                            scannerScope = lifecycleScope,
                            isDarkTheme = isDarkTheme,
                            onBack = { screen = AppScreen.Main },
                            onConfirm = { items ->
                                screen = AppScreen.Main
                                importPickedItems(items)
                            },
                            composeMode = pickerComposeMode,
                            onConfirmCompose = { photos, videos ->
                                screen = AppScreen.Main
                                importComposeItems(photos, videos)
                            }
                        )
                    } else if (page == AppScreen.Settings) {
                        // 设置页：更新检查 / 触感与动画开关 / 主题定制 / 关于
                        SettingsScreen(onBack = { screen = AppScreen.Main })
                    } else {
                        MainScreen(
                            files = files,
                            statusText = statusText,
                            progress = progress,
                            progressDetail = progressDetail,
                            isImporting = isImporting,
                            importProgress = importProgress,
                            isConverting = isConverting,
                            isPickerOpening = isPickerOpening,
                            clearBusy = clearBusy,
                            selectedFormat = selectedFormat,
                            listState = listState,
                            onAddFiles = { onAddFiles() },
                            onBatchImport = { onBatchImport() },
                            onCompose = { onCompose() },
                            onOpenSettings = { screen = AppScreen.Settings },
                            onClearFiles = { clearFiles() },
                            onConvert = { startConvert() },
                            onStopConvert = { stopConvert() },
                            onSelectFormat = { fmt ->
                                // 拆解与合成互斥：队列已有合成任务时禁止切到拆解模式
                                if (fmt == "extract" && files.any { it.formatKey == "compose" }) {
                                    statusText = "队列含合成任务，不能切换为拆解模式"
                                } else {
                                    selectedFormat = fmt
                                    getSharedPreferences("zlivephoto", MODE_PRIVATE)
                                        .edit().putString("target", fmt).apply()
                                }
                            },
                            deleteOriginal = deleteOriginal,
                            onToggleDeleteOriginal = { on ->
                                deleteOriginal = on
                                getSharedPreferences("zlivephoto", MODE_PRIVATE)
                                    .edit().putBoolean("delete_original", on).apply()
                            },
                            onRemoveFile = { path -> removeFile(path) }
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
                        title = { Text("需要读取照片和视频权限") },
                        text = {
                            Text(
                                "本程序需要读取您设备上的照片和视频：\n\n" +
                                "• 照片权限：扫描设备相册，识别其中的动态照片\n" +
                                "• 视频权限：查找双文件动态照片附带的伴生视频\n" +
                                "• 位置权限：保留照片中的 GPS 位置元数据（缺失则转换后丢失位置信息）\n\n" +
                                "拒绝后无法使用内置选择器，可稍后再次点击「添加文件」重新授权。"
                            )
                        },
                        confirmButton = {
                            FilledTonalButton(onClick = {
                                haptic.click()
                                showPermissionDialog = false
                                onPermissionConfirm()
                            }) { Text("授权") }
                        },
                        dismissButton = {
                            FilledTonalButton(onClick = {
                                haptic.click()
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
                                "注意：必须同时授予「照片」和「视频」权限，否则双文件动态照片将无法找到附带的伴生视频。"
                            )
                        },
                        confirmButton = {
                            FilledTonalButton(onClick = {
                                haptic.click()
                                showSettingsDialog = false
                                openAppDetailSettings()
                            }) { Text("去设置") }
                        },
                        dismissButton = {
                            FilledTonalButton(onClick = {
                                haptic.click()
                                showSettingsDialog = false
                            }) { Text("取消") }
                        }
                    )
                }

                // 「所有文件访问」引导（Android 11+：直写磁盘保留位置，个别 OEM 的 MediaStore 会脱敏 GPS）
                if (showAllFilesDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showAllFilesDialog = false
                        },
                        title = { Text("建议授予「所有文件访问」权限") },
                        text = {
                            Text(
                                "部分机型（如魅族）通过系统相册接口写入会脱敏 GPS 位置信息，导致转换后照片丢失位置。\n\n" +
                                "授予「所有文件访问」后，本程序将直接写入相册目录，完整保留位置元数据。\n\n" +
                                "不授予也能正常转换，个别机型可能丢失位置信息。"
                            )
                        },
                        confirmButton = {
                            FilledTonalButton(onClick = {
                                haptic.click()
                                showAllFilesDialog = false
                                requestAllFilesAccess()
                            }) { Text("去授权") }
                        },
                        dismissButton = {
                            FilledTonalButton(onClick = {
                                haptic.click()
                                showAllFilesDialog = false
                            }) { Text("暂不") }
                        }
                    )
                }

                // 文件名冲突弹窗（跳过 / 覆盖 / 自动重命名）
                conflictRequest?.let { req ->
                    AlertDialog(
                        onDismissRequest = {
                            // 点外部关闭视同跳过，避免协程悬挂
                            val cb = req.onChoose
                            conflictRequest = null
                            cb(ConflictAction.SKIP)
                        },
                        title = { Text("输出文件名冲突") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    "输出目录中已存在同名文件：\n${req.displayNames}\n\n" +
                                    "请选择处理方式。"
                                )
                                // 第一行：跳过 / 覆盖
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FilledTonalButton(
                                        onClick = { haptic.click(); req.onChoose(ConflictAction.SKIP) },
                                        modifier = Modifier.weight(1f).height(42.dp)
                                    ) { Text("跳过") }
                                    FilledTonalButton(
                                        onClick = { haptic.click(); req.onChoose(ConflictAction.OVERWRITE) },
                                        modifier = Modifier.weight(1f).height(42.dp)
                                    ) { Text("覆盖") }
                                }
                                // 第二行：自动重命名（独占一行）
                                FilledTonalButton(
                                    onClick = { haptic.click(); req.onChoose(ConflictAction.RENAME) },
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) { Text("自动重命名") }
                                // 整行可点击切换（MD3 习惯：文字也是点击目标）
                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptic.click()
                                            conflictAlways = !conflictAlways
                                        }
                                        .padding(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Md3Checkbox(checked = conflictAlways)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "为后续冲突使用此处理方法",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {}
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

    /** uiMode configChanges：Activity 不重建，显式跟踪深浅色变化驱动主题切换 */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        isDarkTheme = computeDarkTheme()
    }

    /** 系统当前是否处于深色模式（uiMode 夜间位） */
    private fun systemIsDark(): Boolean =
        (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    /** 依据用户「深色模式」三选（跟随系统/深色/浅色）计算当前是否深色 */
    private fun computeDarkTheme(): Boolean = when (AppSettings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemIsDark()
    }

    /** 进入前台：此后禁止 activity-alias 切换。前台禁用正在运行的入口组件会被
     *  系统判定为当前界面失效而中止 Activity（表现为主界面闪退）。 */
    override fun onStart() {
        super.onStart()
        iconApplySafe = false
    }

    /** 退到后台时把桌面图标同步为当前主题色。
     *  activity-alias 切换必须等 Activity 停止后再做（见 onStart 注释）。 */
    override fun onStop() {
        super.onStop()
        iconApplySafe = true
        try {
            IconManager.apply(this)
        } catch (_: Exception) {
            // 极端场景（如系统组件状态异常）下忽略，冷启动仍会重试同步
        }
    }

    /** 注册系统壁纸颜色变化监听（Android 12+ 动态取色用）。
     *  壁纸一变即刷新动态取色跟随的色相缓存（AppSettings.liveDynamicHue，
     *  UI 主题与桌面图标共用，值变更会驱动 UI 下次重组为壁纸色）；
     *  图标 alias 切换仅在「已退到后台 + 动态取色开启」时执行，避免前台闪退；
     *  前台期间发生的壁纸颜色变化由退出到桌面时 onStop 的同步兜底。 */
    private fun registerWallpaperColorListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (BuildConfig.FLAVOR == "go") return
        try {
            val wm = WallpaperManager.getInstance(this)
            val listener = WallpaperManager.OnColorsChangedListener { _, _ ->
                // 统一色相来源：让 UI 主题（next recomposition）与桌面图标同步到壁纸色
                AppSettings.updateLiveDynamicHue(wallpaperHue(this@MainActivity) ?: -1f)
                if (iconApplySafe && AppSettings.dynamicTheme) {
                    try {
                        IconManager.apply(this@MainActivity)
                    } catch (_: Exception) {
                        // 系统组件状态异常时忽略，退出到后台的 onStop 仍会兜底同步
                    }
                }
            }
            wallpaperColorsListener = listener
            wm.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
            // 个别 ROM 可能不提供该能力，忽略即可（图标仍随启动/onStop 更新）
        }
    }

    /** 正常退出兜底：防抖的稳定态写入若尚未落盘，退出前同步补写尾部标记，
     *  防止下次启动被误判为「异常退出」。 */
    override fun onDestroy() {
        stablePersistJob?.cancel()
        if (files.isNotEmpty()) persistList(complete = true)
        // 移除壁纸颜色监听，防止泄漏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wallpaperColorsListener?.let { l ->
                try {
                    WallpaperManager.getInstance(this).removeOnColorsChangedListener(l)
                } catch (_: Exception) {
                    // 实例即将销毁，无需补救
                }
            }
            wallpaperColorsListener = null
        }
        super.onDestroy()
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
            var imported = 0
            var failed = 0
            for (uri in uris) {
                try {
                    importPickedUri(uri)
                    imported++
                } catch (e: Exception) {
                    failed++
                    withContext(Dispatchers.Main) { statusText = "导入失败：${e.message}" }
                }
            }
            withContext(Dispatchers.Main) {
                statusText = if (failed > 0) "导入完成：成功 $imported 个，失败 $failed 个"
                             else "导入完成：共导入 $imported 个文件"
                onListStable()
            }
        }
    }

    // ---------- 权限 ----------

    private fun onAddFiles() {
        if (!hasReadPermission()) {
            // 始终弹出自定义对话框；「拒绝」不退出，下次点击再请求
            // 授权后进入内置选择器
            pendingPermissionAction = { openBuiltInPicker() }
            showPermissionDialog = true
        } else {
            // 媒体权限已有但缺位置权限时，先补请求 ACCESS_MEDIA_LOCATION，再进入选择器
            ensureLocationThen { openBuiltInPicker() }
        }
    }

    /** 打开内置选择器（默认入口）：会话化扫描 + 查询相册列表后展示；
     *  读相册期间 isPickerOpening=true，完成前「添加文件」按钮保持禁用（防重入） */
    private fun openBuiltInPicker() {
        if (isPickerOpening || isConverting) return
        isPickerOpening = true
        pickerComposeMode = false
        scanner.newSession() // 每次进入选择器开启新会话（相册扫过即不再扫）
        statusText = "正在读取相册…"
        lifecycleScope.launch(Dispatchers.IO) {
            val albums = mediaRepo.queryAlbums()
            withContext(Dispatchers.Main) {
                pickerAlbums = albums
                screen = AppScreen.Picker
                isPickerOpening = false
                statusText = if (albums.isEmpty()) "未找到相册" else "就绪"
            }
        }
    }

    /** 合成动态照片入口：打开合成模式选择器（普通照片+视频，按序号配对） */
    private fun onCompose() {
        if (!hasReadPermission()) {
            pendingPermissionAction = { openComposePicker() }
            showPermissionDialog = true
        } else {
            ensureLocationThen { openComposePicker() }
        }
    }

    /** 打开合成模式选择器：扫描普通照片（非动态照片）+ 视频（含时长） */
    private fun openComposePicker() {
        if (isPickerOpening || isConverting) return
        isPickerOpening = true
        pickerComposeMode = true
        composeScanner.newSession()
        statusText = "正在读取相册…"
        lifecycleScope.launch(Dispatchers.IO) {
            val albums = mediaRepo.queryAlbums(includeVideos = true)
            withContext(Dispatchers.Main) {
                pickerAlbums = albums
                screen = AppScreen.Picker
                isPickerOpening = false
                statusText = if (albums.isEmpty()) "未找到相册" else "就绪"
            }
        }
    }

    /**
     * 合成模式确认导入：照片[i] + 视频[i] 按序号一一配对生成合成任务。
     * 数量不一致时按较少一方配对并提示；任务项 formatKey=compose、
     * composeVideoPath=配对视频，输出时间戳取照片的修改时间/拍摄时间。
     */
    private fun importComposeItems(photos: List<MediaItem>, videos: List<MediaItem>) {
        val pairs = minOf(photos.size, videos.size)
        if (pairs == 0) {
            statusText = if (photos.isEmpty()) "未选择照片，无法合成"
                         else "未选择视频，无法合成"
            return
        }
        var added = 0
        for (i in 0 until pairs) {
            val p = photos[i]
            val v = videos[i]
            // 去重按绝对路径判断（跨相册同名照片可同时合成，不因 name 相同误跳）
            if (files.any { it.path == p.path }) continue
            files.add(FileItem(
                path = p.path,
                name = p.name,
                info = "合成任务（点击开始转换执行合成）",
                sourcePath = p.path,
                sourceTime = p.dateModified * 1000L, // 输出修改时间=照片修改时间
                sourceTaken = p.dateTaken,            // 输出创建时间=照片拍摄时间
                formatKey = "compose",
                composeVideoPath = v.path
            ))
            added++
        }
        statusText = when {
            added == 0 -> "所选照片均已在列表中"
            photos.size != videos.size ->
                "照片 ${photos.size} 张、视频 ${videos.size} 个，按较少方已添加 $added 对合成任务"
            else -> "已添加 $added 个合成任务，点击「开始转换」执行合成"
        }
        // 拆解与合成互斥：合成任务入队时若当前还是拆解模式（无意义的合成目标），
        // 自动切到默认的 Google 输出，避免“拆解模式下合成”产出不可识别产物
        if (added > 0 && selectedFormat == "extract") {
            selectedFormat = "google"
            getSharedPreferences("zlivephoto", MODE_PRIVATE)
                .edit().putString("target", "google").apply()
            statusText += "（已自动切换输出格式为 Google）"
        }
        onListMutated()
        onListStable(immediate = true)
    }

    /** 内置选择器确认导入：引用模式直接记录原文件路径（不复制，零空间占用，
     *  EXIF 天然完整保留）；导入期间仅显示状态栏 + 进度条，完成后按钮再从底部动画上移 */
    private fun importPickedItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val total = items.size
        isImporting = true
        importProgress = 0f
        stopImportRequested = false
        progressDetail = "已添加 0/$total"
        statusText = "正在导入 $total 个文件…"
        lifecycleScope.launch(Dispatchers.IO) {
            var imported = 0
            var failed = 0
            for (item in items) {
                if (stopImportRequested) break // 停止导入：使用当前进度，不再继续
                try {
                    val src = File(item.path)
                    if (!src.exists() || !src.canRead()) throw IOException("源文件不存在或不可读")
                    importFromPath(
                        path = item.path, sourceUri = item.uri.toString(),
                        sourceTime = item.dateModified * 1000L, sourceTaken = item.dateTaken
                    )
                    imported++
                } catch (e: Exception) {
                    failed++
                }
                withContext(Dispatchers.Main) {
                    val done = imported + failed
                    importProgress = done.toFloat() / total
                    progressDetail = "已添加 $done/$total"
                    statusText = "正在导入 $done/$total…"
                }
            }
            withContext(Dispatchers.Main) {
                isImporting = false
                importProgress = 0f
                progressDetail = ""
                statusText = when {
                    stopImportRequested -> "已停止导入：成功 $imported 个，失败 $failed 个"
                    failed > 0 -> "导入完成：成功 $imported 个，失败 $failed 个"
                    else -> "导入完成：共导入 $imported 个文件"
                }
                stopImportRequested = false
                onListStable()
            }
        }
    }

    /** 终止转换/导入：转换处理完当前文件后停止；导入循环下一项前立即停止 */
    private fun stopConvert() {
        if (isConverting && !stopRequested) {
            stopRequested = true
            statusText = "正在停止…（完成当前文件后停止）"
        } else if (isImporting && !stopImportRequested) {
            stopImportRequested = true
            statusText = "正在停止导入…"
        }
    }

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

    /**
     * 添加文件到列表并异步识别格式（引用模式：直接使用源文件路径，不复制）。
     * 识别完成后，对未匹配为单文件内嵌格式（Google/OPPO/vivo 单文件/小米/荣耀）的项目，
     * 在源文件同目录按同名校验伴生视频（仅拼扩展名 mp4/mov + ftyp 头校验）：
     * 存在则重新识别（vivo/Apple 双文件配对）；不存在则视为不匹配直接跳过。
     * @param path 转换用路径：引用模式=原文件路径；受限回退模式=incoming 副本
     * @param sourcePath 原始文件绝对路径（伴生视频查找 / 删除原图用），默认与 path 相同
     */
    private fun importFromPath(path: String, sourceUri: String? = null,
                               sourcePath: String? = null,
                               sourceTime: Long = 0L, sourceTaken: Long = 0L) {
        // 批量导入在 IO 协程内调用：SnapshotStateList 变更必须在 Main 线程，切回 Main 保证线程安全
        if (Looper.myLooper() != Looper.getMainLooper()) {
            lifecycleScope.launch(Dispatchers.Main) {
                importFromPath(path, sourceUri, sourcePath, sourceTime, sourceTaken)
            }
            return
        }
        val srcPath = sourcePath ?: path
        val name = File(path).name
        // 去重仅按绝对路径判断：不同相册可能存在同名文件（如均含 IMG_xxx.jpg），
        // 按 name 去重会误删跨相册同名文件，导致只导入其中一个相册的文件
        if (files.any { it.path == path }) return
        val item = FileItem(path = path, name = name, info = "检测中…",
            sourceUri = sourceUri, sourcePath = srcPath,
            sourceTime = sourceTime, sourceTaken = sourceTaken)
        files.add(item)
        onListMutated()
        launchDetection(path, srcPath)
    }

    /**
     * 异步识别格式（引用模式：直接使用源文件路径，不复制）。
     * 识别完成后，对未匹配为单文件内嵌格式（Google/OPPO/vivo 单文件/小米/荣耀）的项目，
     * 在源文件同目录按同名校验伴生视频（仅拼扩展名 mp4/mov + ftyp 头校验）：
     * 存在则重新识别（vivo/Apple 双文件配对）；不存在则视为不匹配直接跳过。
     * @param path 转换用路径：引用模式=原文件路径；受限回退模式=incoming 副本
     * @param srcPath 原始文件绝对路径（伴生视频查找用）
     */
    private fun launchDetection(path: String, srcPath: String) {
        lifecycleScope.launch(detectionDispatcher) {
            // 视频内嵌的单文件格式，转换不依赖伴生视频
            val embeddedFormats = setOf("google", "oppo", "vivo_single", "xiaomi", "honor")
            var (plugin, score) = FormatRegistry.detectBest(path)

            // 未识别 / vivo·Apple 双文件格式：在原始目录查找同名伴生视频
            if (plugin?.name !in embeddedFormats) {
                val stem = srcPath.substringBeforeLast('.')
                val videoPath = listOf("$stem.mp4", "$stem.mov", "$stem.MP4", "$stem.MOV")
                    .firstOrNull { File(it).exists() && File(it).length() > 8L }
                if (videoPath != null && hasValidMp4Head(File(videoPath))) {
                    // 受限回退模式：JPG 已复制到 incoming，伴生视频也需复制过去配对
                    if (path != srcPath) {
                        try {
                            File(videoPath).copyTo(File(File(path).parent, File(videoPath).name), overwrite = true)
                        } catch (_: Exception) {}
                    }
                    // 伴生视频存在且头部合法，重新识别（如 vivo 双文件 40 分 → 90 分）
                    val (p2, s2) = FormatRegistry.detectBest(path)
                    plugin = p2; score = s2
                }
                // 找不到可用的同名视频 → 不匹配，跳过（保持当前识别状态）
            }

            val recognized = plugin != null && score >= 50
            withContext(Dispatchers.Main) {
                val idx = files.indexOfFirst { it.path == path }
                if (idx >= 0) {
                    files[idx] = files[idx].copy(
                        info = if (recognized) plugin!!.display else "未识别的动态照片格式",
                        isUnrecognized = !recognized,
                        formatKey = if (recognized) plugin!!.name else null
                    )
                    // 识别结果同步到本地 JSON（防抖合并，避免批量导入时高频写盘）
                    onListStable()
                }
            }
        }
    }

    /**
     * 按路径移除列表项并清理本地缓存文件（含已复制的伴生视频）。
     */
    private fun removeFile(path: String) {
        val idx = files.indexOfFirst { it.path == path }
        if (idx < 0) return
        val item = files[idx]
        cleanupIncomingItem(item)
        files.removeAt(idx)
        statusText = "已移除 ${item.name}"
        onListStable()
    }

    /** 清理列表项在 incoming 的缓存（图片 + 伴生视频）。
     *  引用模式（路径不在 incoming 内）不做任何删除，保护用户原图。 */
    private fun cleanupIncomingItem(item: FileItem) {
        try {
            val f = File(item.path)
            if (f.parent != incomingDir) return // 引用模式：不删原图
            f.delete()
            val stem = item.path.substringBeforeLast('.')
            listOf("$stem.mp4", "$stem.mov").forEach { File(it).delete() }
        } catch (_: Exception) {}
    }

    /** 清理全部应用缓存（incoming 暂存 + output 暂存 + 系统缓存目录）。
     *  启动时与每次处理完成后调用，避免占用空间无限膨胀。删除在 IO 线程执行。 */
    private fun cleanupAllCaches() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                File(incomingDir).listFiles()?.forEach { it.delete() }
                File(outputDir).walkBottomUp().forEach { it.delete() }
                cacheDir.walkBottomUp().forEach { it.delete() }
            } catch (_: Exception) {}
        }
    }

    // ---------- 列表持久化（本地 JSON + 异常退出检测） ----------

    private sealed class ListState {
        object Empty : ListState()
        data class Loaded(val files: List<FileItem>) : ListState()
        object Corrupt : ListState()
        data class AbnormalExit(val count: Int) : ListState()
    }

    private fun listStateFile() = File(filesDir, "list_state.json")

    /** 把当前列表写入本地 JSON；complete=true 表示稳定态（尾部标记），
     *  false 表示正在导入/处理中（标记被移除，用于异常退出检测）。 */
    private fun persistList(complete: Boolean) {
        try {
            val arr = JSONArray()
            for (f in files) {
                val o = JSONObject()
                o.put("path", f.path)
                o.put("name", f.name)
                o.put("info", f.info)
                o.put("unrecognized", f.isUnrecognized)
                o.put("sourceUri", f.sourceUri ?: "")
                o.put("sourcePath", f.sourcePath ?: "")
                o.put("sourceTime", f.sourceTime)
                o.put("sourceTaken", f.sourceTaken)
                o.put("formatKey", f.formatKey ?: "")
                o.put("composeVideo", f.composeVideoPath ?: "")
                arr.put(o)
            }
            val root = JSONObject()
            root.put("files", arr)
            root.put("complete", complete)
            val tmp = File(filesDir, "list_state.json.tmp")
            tmp.writeText(root.toString())
            val target = listStateFile()
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } catch (_: Exception) {}
    }

    /** 读取本地 JSON：损坏即删并返回 Corrupt；无标记且非空返回 AbnormalExit。 */
    private fun readListState(): ListState {
        val f = listStateFile()
        if (!f.exists()) return ListState.Empty
        return try {
            val root = JSONObject(f.readText())
            val arr = root.optJSONArray("files") ?: run { f.delete(); return ListState.Corrupt }
            val items = ArrayList<FileItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: run { f.delete(); return ListState.Corrupt }
                val path = o.optString("path")
                if (path.isEmpty()) { f.delete(); return ListState.Corrupt }
                items.add(FileItem(
                    path = path,
                    name = o.optString("name", File(path).name),
                    info = o.optString("info", "检测中…"),
                    isUnrecognized = o.optBoolean("unrecognized", false),
                    sourceUri = o.optString("sourceUri").ifEmpty { null },
                    sourcePath = o.optString("sourcePath").ifEmpty { null },
                    sourceTime = o.optLong("sourceTime", 0L),
                    sourceTaken = o.optLong("sourceTaken", 0L),
                    formatKey = o.optString("formatKey").ifEmpty { null },
                    composeVideoPath = o.optString("composeVideo").ifEmpty { null }
                ))
            }
            when {
                items.isEmpty() -> { f.delete(); ListState.Empty }
                !root.optBoolean("complete", false) -> { f.delete(); ListState.AbnormalExit(items.size) }
                else -> ListState.Loaded(items)
            }
        } catch (_: Exception) {
            f.delete()
            ListState.Corrupt
        }
    }

    /** 标记列表进入不稳定态（导入/处理开始前移除尾部标记）。 */
    private fun markIncomplete() {
        dirtyCount = 0
        stablePersistJob?.cancel()
        persistList(complete = false)
    }

    /** 列表被追加/修改：首次变更写一次无标记 JSON，之后每 50 条写一次快照。 */
    private fun onListMutated() {
        dirtyCount++
        stablePersistJob?.cancel() // 挂起的稳定态写入已过期，取消防止覆盖无标记状态
        if (dirtyCount == 1 || dirtyCount >= 50) {
            persistList(complete = false)
            if (dirtyCount >= 50) dirtyCount = 0
        }
    }

    /** 列表进入稳定态（导入/处理完成或清空后加回尾部标记）。
     *  高频调用（如批量识别完成）时防抖 250ms 合并为一次写盘；紧急场景用 immediate。 */
    private fun onListStable(immediate: Boolean = false) {
        dirtyCount = 0
        stablePersistJob?.cancel()
        if (immediate) {
            persistList(complete = true)
        } else {
            stablePersistJob = lifecycleScope.launch(Dispatchers.IO) {
                delay(250)
                persistList(complete = true) // 快照读取线程安全
            }
        }
    }

    private fun clearFiles() {
        if (files.isEmpty() || clearBusy) return
        clearBusy = true // 清理过程中禁用清空按钮
        hapticController?.double() // 快速两下振动
        progress = 0f
        statusText = "已清空"
        File(incomingDir).listFiles()?.forEach { it.delete() }
        File(outputDir).listFiles()?.forEach { it.delete() }
        // 快照：分 50 条一批移除，避免大量缓存删除 + 列表项移除
        // 一次性执行造成主线程卡顿（>500 项时尤为明显）
        val instant = files.toList()
        lifecycleScope.launch(Dispatchers.Main) {
            var i = 0
            while (i < instant.size) {
                val batch = instant.subList(i, minOf(i + 50, instant.size))
                batch.forEach { cleanupIncomingItem(it) }
                files.removeAll(batch.toSet())
                i += 50
                if (i < instant.size) delay(16) // 让出一帧，避免单帧重组/IO 卡顿
            }
            onListStable(immediate = true)
            clearBusy = false
        }
    }

    // ---------- 转换（列表模式） ----------

    private fun startConvert() {
        if (isConverting) return
        // 排除未识别 / Apple 不可转换 / 失败项（Apple 已标记 isUnrecognized=true）
        val targets = files.filter { !it.isUnrecognized && !it.info.contains("失败") }.toList()
        if (targets.isEmpty()) {
            statusText = "没有可转换的文件"
            return
        }

        isConverting = true
        stopRequested = false
        progress = 0f
        progressDetail = "已处理 0/${targets.size}"
        lastExportError = null
        claimedNames.clear()
        batchConflictAction = null // 「本批次总是」的选择仅当前批次有效
        resetPendingDeletes()
        markIncomplete() // 处理开始：移除列表 JSON 尾部标记（异常退出可检测）

        lifecycleScope.launch(Dispatchers.IO) {
            // 转换并发：normal 4 路（Semaphore 限流），go 单线程（老机型性能低），单文件异常隔离不中断
            val sem = Semaphore(if (BuildConfig.FLAVOR == "go") 1 else 4)
            val total = targets.size
            val done = AtomicInteger(0)
            val exported = AtomicInteger(0)
            // 成功处理的路径集合：处理期间不移除，全部完成后统一左滑清除
            val successPaths = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

            val jobs = targets.map { item ->
                launch {
                    sem.withPermit {
                        // 停止请求：未开始的任务直接跳过（已开始的会做完当前文件）
                        if (stopRequested) {
                            val d = done.incrementAndGet()
                            progress = d.toFloat() / total
                            progressDetail = "已处理 ${d - 1}/$total"
                            return@withPermit
                        }
                        withContext(Dispatchers.Main) {
                            val i0 = files.indexOfFirst { it.path == item.path }
                            if (i0 >= 0) files[i0] = files[i0].copy(info = "转换中…")
                        }
                        var n = 0
                        var staged: List<String>? = null
                        try {
                            if (item.formatKey == "compose" && item.composeVideoPath != null) {
                                // 合成任务：照片 + 配对视频 → 动态照片。
                                // 非标准 MP4（MOV/非 H.264/H.265 编码）时自动用 ffmpeg 附加项转码
                                // 为标准 MP4；附加项未安装则抛 VideoContainerException 提示用户先下载。
                                staged = Converter.compose(
                                    photoPath = item.path,
                                    videoPath = item.composeVideoPath!!,
                                    target = selectedFormat,
                                    outDir = outputDir,
                                    log = { level, msg, tag ->
                                        if (level == "error" || level == "warn") {
                                            statusText = "[$tag] $msg"
                                        }
                                    }
                                )
                            } else {
                                // 普通项走转换管线
                                staged = Converter.convertFile(
                                    path = item.path,
                                    target = selectedFormat,
                                    outDir = outputDir,
                                    log = { level, msg, tag ->
                                        if (level == "error" || level == "warn") {
                                            statusText = "[$tag] $msg"
                                        }
                                    }
                                )
                            }
                            // 项10：必须在冲突/导出发生前解析原图 URI——覆盖会删除旧媒体
                            // 条目，之后按路径查询会误中刚导出的新产物
                            // 合成任务删除封面照片 + 配对视频；普通任务删除原图（含双文件伴生视频）
                            val originalUris = if (deleteOriginal) {
                                if (item.formatKey == "compose") {
                                    resolveComposeOriginalUris(item.sourcePath, item.sourceUri, item.composeVideoPath)
                                } else {
                                    resolveOriginalUris(item.sourcePath, item.sourceUri, item.formatKey)
                                }
                            } else emptyList()
                            // 输出文件名冲突处理（跳过 / 覆盖 / 自动后缀）
                            val finalOutputs = resolveConflicts(staged.orEmpty(), "", item.sourcePath)
                            if (finalOutputs != null) {
                                for (outPath in finalOutputs) {
                                    // 输出时间戳：修改时间=源文件修改时间；创建时间=源文件拍摄时间
                                    // （合成任务取照片的时间，二者在照片上天然同源）
                                    val srcTime = if (item.sourceTime > 0L) item.sourceTime else System.currentTimeMillis()
                                    val srcTaken = if (item.sourceTaken > 0L) item.sourceTaken else srcTime
                                    File(outPath).setLastModified(srcTime)
                                    if (exportToMediaStore(outPath, srcTime, srcTaken) != null) n++
                                }
                            }
                            exported.addAndGet(n)
                            // 项10：成功导出的原图入待删集合（失败/跳过/覆盖保护项不删）
                            mergePendingDeletes(item.sourcePath, originalUris, n > 0)
                            if (finalOutputs != null) successPaths.add(item.path)
                            withContext(Dispatchers.Main) {
                                val i = files.indexOfFirst { it.path == item.path }
                                if (i >= 0) {
                                    // 处理期间不移除项目：仅更新状态，待全部完成后统一左滑清除
                                    files[i] = files[i].copy(
                                        info = if (finalOutputs == null) "完成（跳过：同名冲突）"
                                               else "完成（导出 $n 个）"
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                val i = files.indexOfFirst { it.path == item.path }
                                if (i >= 0) {
                                    files[i] = files[i].copy(info = "失败：${e.message}")
                                }
                            }
                        } finally {
                            // 清理本地暂存产物（已导出 / 跳过 / 失败均清理）
                            staged?.forEach { p -> try { File(p).delete() } catch (_: Exception) {} }
                        }
                        val d = done.incrementAndGet()
                        progress = d.toFloat() / total
                        progressDetail = "已处理 $d/$total"
                    }
                }
            }
            jobs.joinAll()
            withContext(Dispatchers.Main) {
                clearBusy = true // 收尾清理过程中禁用清空按钮
                // 处理完成/终止：成功项直接无动画移除（快速两下振动提示），
                // 失败项保留，由 animateItem placement 自动上移补位
                hapticController?.double()
                val successes = files.filter { it.path in successPaths }.toList()
                successes.forEach { cleanupIncomingItem(it) }
                files.removeAll(successes.toSet())
                cleanupAllCaches()
                val exportErr = lastExportError
                statusText = when {
                    stopRequested -> "已停止：$total 个文件中处理了 ${done.get()} 个，导出 ${exported.get()} 个到相册"
                    exportErr != null ->
                        "完成：$total 个文件处理完毕，导出 ${exported.get()} 个到相册（原因：$exportErr）"
                    else ->
                        "完成：$total 个文件处理完毕，导出 ${exported.get()} 个到相册"
                }
                isConverting = false
                progress = 0f
                progressDetail = ""
                // 项10：批次完成，一次性请求把成功处理的原图移入回收站（系统删除工具）
                if (deleteOriginal) requestDeleteOriginals(statusText)
                onListStable(immediate = true) // 处理结束/终止：加回列表 JSON 尾部标记
                clearBusy = false
            }
        }
    }

    // ---------- 批量导入（文件夹模式） ----------

    private fun onBatchImport() {
        if (isConverting || isImporting) return
        if (!hasReadPermission()) {
            // 批量导入同样需要媒体读取权限（直接 File 遍历 + 直读源文件）
            // 授权后继续打开文件夹选择器，而不是进入内置选择器
            pendingPermissionAction = { ensureLocationThen { batchFolderLauncher.launch(null) } }
            showPermissionDialog = true
            return
        }
        ensureLocationThen { batchFolderLauncher.launch(null) }
    }

    /**
     * 批量导入：选择文件夹后整树枚举图片文件，粗筛（QuickClassify）出动态照片，
     * 逐个添加到处理列表（引用模式，不复制、不转换）。格式识别在 importFromPath 内
     * 异步进行；转换由用户随后点「开始转换」统一触发。
     */
    private fun importBatchFolder(rootPath: String) {
        val root = File(rootPath)
        if (!root.isDirectory || !root.canRead()) {
            statusText = "无法读取所选文件夹"
            return
        }

        isImporting = true
        importProgress = 0f
        stopImportRequested = false
        progressDetail = "已导入 0/0"
        statusText = "正在扫描文件夹…"

        lifecycleScope.launch(Dispatchers.IO) {
            // 1. 整树枚举图片文件（动态照片的主文件是 JPG/HEIC）
            val imageFiles = mutableListOf<File>()
            fun walk(dir: File) {
                val children = dir.listFiles() ?: return
                for (c in children) {
                    if (c.isDirectory) walk(c)
                    else if (c.isFile) {
                        val ext = c.extension.lowercase()
                        if (ext == "jpg" || ext == "jpeg" || ext == "heic") imageFiles.add(c)
                    }
                }
            }
            walk(root)
            val total = imageFiles.size
            if (total == 0) {
                withContext(Dispatchers.Main) {
                    isImporting = false
                    importProgress = 0f
                    progressDetail = ""
                    statusText = "所选文件夹内未找到图片"
                }
                return@launch
            }

            // 2. 粗筛动态照片并加入处理列表（引用模式，识别异步进行）
            var added = 0
            var skipped = 0
            for (f in imageFiles) {
                if (stopImportRequested) break // 停止导入：使用当前进度，不再继续
                val path = f.absolutePath
                if (QuickClassify.sniff(path)) {
                    importFromPath(
                        path = path, sourceUri = null, sourcePath = path,
                        sourceTime = f.lastModified(), sourceTaken = 0L
                    )
                    added++
                } else {
                    skipped++
                }
                val done = added + skipped
                withContext(Dispatchers.Main) {
                    importProgress = done.toFloat() / total
                    progressDetail = "已导入 $added/$total"
                    statusText = "正在批量导入 $done/$total…"
                }
            }
            withContext(Dispatchers.Main) {
                isImporting = false
                importProgress = 0f
                progressDetail = ""
                statusText = when {
                    stopImportRequested ->
                        "已停止批量导入：共导入 $added 个动态照片" +
                            (if (skipped > 0) "，跳过 $skipped 个非动态文件" else "")
                    else ->
                        "批量导入完成：共导入 $added 个动态照片" +
                            (if (skipped > 0) "，跳过 $skipped 个非动态文件" else "")
                }
                stopImportRequested = false
                onListStable()
            }
        }
    }

    // ---------- 冲突处理 ----------

    /** 输出名是否已被占用：本批次已声明 / MediaStore 索引 / 实际文件系统三重检查。
     *  File 系统级检查不可省：MediaStore 索引可能滞后，漏检时 insert 同名会被系统
     *  静默改名（如 a (1).jpg，逐文件独立），导致「没询问就自动重命名」且
     *  双文件的图片与视频 stem 不一致。 */
    private fun nameOccupied(relSubDir: String, fileName: String): Boolean {
        if ((relSubDir to fileName) in claimedNames) return true
        if (mediaStoreExists(fileName, relSubDir)) return true
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            albumFolder() + (if (relSubDir.isNotEmpty()) "/$relSubDir" else "")
        )
        return File(dir, fileName).exists()
    }

    /**
     * 处理输出文件名冲突：
     * - 无冲突：直接占用并返回原列表
     * - 有冲突：弹窗询问（跳过=返回 null / 覆盖=删除已存在项 / 自动后缀=整体改名）
     * 双文件输出保证图片与视频除扩展名外同名。
     * @param sourcePath 被处理的原文件路径（项10 覆盖保护判定用；可为 null）
     */
    private suspend fun resolveConflicts(
        staged: List<String>, relSubDir: String, sourcePath: String? = null
    ): List<String>? {
        conflictMutex.withLock {
            val conflicts = staged.filter { nameOccupied(relSubDir, File(it).name) }
            if (conflicts.isEmpty()) {
                staged.forEach { claimedNames.add(relSubDir to File(it).name) }
                return staged
            }
            val display = conflicts.map { File(it).name }.distinct().joinToString("、")
            when (askConflictAction(display)) {
                ConflictAction.SKIP -> return null
                ConflictAction.OVERWRITE -> {
                    conflicts.forEach {
                        val name = File(it).name
                        deleteFromMediaStore(name, relSubDir)
                        // 项10 覆盖保护：被覆盖的输出就是正在处理的原文件本身
                        // （源位于输出目录内、同名）时，新产物即将写回原位置——
                        // 禁止删除该原图，否则新导出的产物会被移入回收站造成永久丢失
                        if (sourcePath != null && File(sourcePath).name == name &&
                            File(outputAlbumDir(relSubDir), name).absolutePath ==
                                File(sourcePath).absolutePath
                        ) {
                            protectedOriginalPaths.add(sourcePath)
                            synchronized(pendingDeleteLock) { pendingDeleteBySource.remove(sourcePath) }
                        }
                    }
                    staged.forEach { claimedNames.add(relSubDir to File(it).name) }
                    return staged
                }
                ConflictAction.RENAME -> {
                    val stem = File(staged.first()).nameWithoutExtension
                    var i = 1
                    while (true) {
                        val candidate = "$stem$i"
                        val free = staged.all { s ->
                            val f = File(s)
                            val n = if (f.extension.isEmpty()) candidate else "$candidate.${f.extension}"
                            !nameOccupied(relSubDir, n)
                        }
                        if (free) {
                            // 整组重命名（图片与视频保持同一 stem）
                            val renamed = staged.map { p ->
                                val f = File(p)
                                val ext = f.extension
                                val target = if (ext.isEmpty()) File(f.parentFile, candidate)
                                             else File(f.parentFile, "$candidate.$ext")
                                claimedNames.add(relSubDir to target.name)
                                if (f.renameTo(target)) target.absolutePath else p
                            }
                            return renamed
                        }
                        i++
                    }
                }
            }
        }
    }

    /** 挂起等待用户在冲突弹窗中选择动作（互斥：同一时刻最多一个询问）。
     *  本批次内已勾选「总是」的动作直接套用，不再弹窗。 */
    private suspend fun askConflictAction(displayNames: String): ConflictAction {
        batchConflictAction?.let { return it }
        return suspendCancellableCoroutine { cont ->
            conflictAlways = false // 每次弹窗前重置复选框
            conflictRequest = ConflictRequest(displayNames) { action ->
                conflictRequest = null
                if (conflictAlways) batchConflictAction = action // 本批次内总是
                if (cont.isActive) cont.resume(action)
            }
            cont.invokeOnCancellation {
                // 协程被取消（如 Activity 销毁）时清掉弹窗
                conflictRequest = null
            }
        }
    }

    /** 相册输出根目录名：normal 与 go 均统一输出到 Pictures/Z-LivePhoto-Converter（Go 仅体现在应用名） */
    private fun albumFolder(): String = "Z-LivePhoto-Converter"

    /** 相册输出目录（Pictures/Z-LivePhoto-Converter[/<子目录>]）中是否已存在同名文件。
     *  Android 9-：输出落盘为物理文件，由 nameOccupied 的文件系统检查兜底，这里直接返回 false。 */
    private fun mediaStoreExists(displayName: String, relSubDir: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val rel = albumRelPath(relSubDir)
        for (collection in listOf(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        )) {
            try {
                contentResolver.query(
                    collection, arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                    arrayOf(displayName, rel), null
                )?.use { c -> if (c.moveToFirst()) return true }
            } catch (_: Exception) {}
        }
        return false
    }

    /** 删除相册输出目录中的同名文件（覆盖前清理）。
     *  Android 10+：按 RELATIVE_PATH 查 MediaStore 行删除；
     *  Android 9-：直接删除物理文件 + 按 DATA 查 MediaStore 行删除（无 RELATIVE_PATH 列）。 */
    private fun deleteFromMediaStore(displayName: String, relSubDir: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rel = albumRelPath(relSubDir)
            for (collection in listOf(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            )) {
                try {
                    contentResolver.query(
                        collection, arrayOf(MediaStore.MediaColumns._ID),
                        "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                        arrayOf(displayName, rel), null
                    )?.use { c ->
                        while (c.moveToNext()) {
                            val id = c.getLong(0)
                            try {
                                contentResolver.delete(
                                    android.content.ContentUris.withAppendedId(collection, id), null, null)
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
            }
            return
        }
        // Android 9-：物理文件 + 媒体行（按 DATA）一并删除
        val f = File(outputAlbumDir(relSubDir), displayName)
        try { if (f.exists()) f.delete() } catch (_: Exception) {}
        val abs = f.absolutePath
        for (collection in listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )) {
            try {
                contentResolver.delete(collection, "${MediaStore.MediaColumns.DATA}=?", arrayOf(abs))
            } catch (_: Exception) {}
        }
    }

    /** 相册输出相对路径：Pictures/{albumFolder}[/<子目录>] */
    private fun albumRelPath(relSubDir: String): String =
        Environment.DIRECTORY_PICTURES + "/" + albumFolder() +
            (if (relSubDir.isNotEmpty()) "/$relSubDir" else "")

    // ---------- 项10：处理完成后删除原图（辅助） ----------

    /** 相册输出目录的文件系统路径（覆盖冲突保护判定用） */
    private fun outputAlbumDir(relSubDir: String): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        albumFolder() + (if (relSubDir.isNotEmpty()) "/$relSubDir" else "")
    )

    /**
     * 解析原图（含双文件伴生视频）的 MediaStore URI。
     * - 主图：优先用内置选择器直查所得的媒体 URI（content://media/external/…）；
     *   Photo Picker 临时 URI 或缺失时按 DATA 路径回查
     * - 双文件格式（vivo/Apple）：伴生视频一并纳入（否则会残留孤儿视频）
     */
    private fun resolveOriginalUris(
        sourcePath: String?, sourceUri: String?, formatKey: String?
    ): List<Uri> {
        if (sourcePath == null) return emptyList()
        val uris = mutableListOf<Uri>()
        if (sourceUri != null && sourceUri.startsWith("content://media/external/")) {
            try { uris.add(Uri.parse(sourceUri)) } catch (_: Exception) {}
        } else {
            resolveUriByPath(sourcePath)?.let { uris.add(it) }
        }
        if (formatKey == "vivo" || formatKey == "apple") {
            val stem = sourcePath.substringBeforeLast('.')
            for (ext in listOf(".mp4", ".mov", ".MP4", ".MOV")) {
                val videoPath = stem + ext
                if (File(videoPath).exists() && File(videoPath).length() > 8L) {
                    resolveUriByPath(videoPath)?.let { uris.add(it) }
                    break
                }
            }
        }
        return uris
    }

    /**
     * 合成任务的待删原文件：封面照片 + 配对视频。
     * 合成任务无 sourceUri（引用普通照片/视频路径），统一按 DATA 路径回查 MediaStore URI。
     */
    private fun resolveComposeOriginalUris(
        photoPath: String?, photoUri: String?, videoPath: String?
    ): List<Uri> {
        if (photoPath == null) return emptyList()
        val uris = mutableListOf<Uri>()
        if (photoUri != null && photoUri.startsWith("content://media/external/")) {
            try { uris.add(Uri.parse(photoUri)) } catch (_: Exception) {}
        } else {
            resolveUriByPath(photoPath)?.let { uris.add(it) }
        }
        if (videoPath != null && File(videoPath).exists() && File(videoPath).length() > 8L) {
            resolveUriByPath(videoPath)?.let { uris.add(it) }
        }
        return uris
    }

    /** 按 DATA 绝对路径在媒体库查 URI（图片/视频集合各查一次；EXTERNAL_CONTENT_URI 全版本可用） */
    private fun resolveUriByPath(path: String): Uri? {
        for (collection in listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )) {
            try {
                val id = contentResolver.query(
                    collection, arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DATA}=?", arrayOf(path), null
                )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
                if (id != null) {
                    return android.content.ContentUris.withAppendedId(collection, id)
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /** 批次开始时清空待删集合与覆盖保护记录 */
    private fun resetPendingDeletes() {
        synchronized(pendingDeleteLock) { pendingDeleteBySource.clear() }
        protectedOriginalPaths.clear()
        pendingDeleteCount = 0
    }

    /**
     * 成功导出后把原图 URI 并入待删集合（IO 线程并发调用，加锁）。
     * @param exported 导出是否成功（false=失败/跳过，保留原图）
     */
    private fun mergePendingDeletes(sourcePath: String?, uris: List<Uri>, exported: Boolean) {
        if (sourcePath == null || !exported || uris.isEmpty()) return
        if (sourcePath in protectedOriginalPaths) return // 覆盖保护项不删
        synchronized(pendingDeleteLock) {
            pendingDeleteBySource.getOrPut(sourcePath) { mutableListOf() }.addAll(uris)
        }
    }

    /**
     * 批次结束后一次性请求把原图移入回收站（系统回收站工具：
     * 「Z-LivePhoto-Converter 想将 N 个项目移入回收站」）。
     * - 先过滤已失效条目（覆盖冲突中被替换的旧 URI 等），避免请求抛异常
     * - Android 11+：createTrashRequest 整批一次请求移入回收站
     * - Android 10：无该 API，仅能直接删除本应用拥有的媒体（无回收站）
     */
    private fun requestDeleteOriginals(baseStatus: String) {
        val all = synchronized(pendingDeleteLock) {
            val flat = pendingDeleteBySource.values.flatten().distinct()
            pendingDeleteBySource.clear()
            flat
        }
        protectedOriginalPaths.clear()
        if (all.isEmpty()) return
        val valid = all.filter { uri ->
            try {
                contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                    ?.use { c -> c.moveToFirst() } == true
            } catch (_: Exception) { false }
        }
        if (valid.isEmpty()) return
        deleteBaseStatus = baseStatus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val sender = MediaStore.createTrashRequest(contentResolver, valid, true).intentSender
                pendingDeleteCount = valid.size
                deleteRequestLauncher.launch(IntentSenderRequest.Builder(sender).build())
            } catch (e: Exception) {
                pendingDeleteCount = 0
                statusText = "$baseStatus；原图移入回收站请求失败（${e.message}）"
            }
        } else {
            // Android 10：无 createTrashRequest 与回收站；非本应用拥有的媒体无法删除
            // Android 9-：WRITE_EXTERNAL_STORAGE 允许直接删媒体行与物理文件（无回收站）
            var deleted = 0
            for (uri in valid) {
                try { contentResolver.delete(uri, null, null); deleted++ } catch (_: Exception) {}
            }
            val noTrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                "Android 10 无回收站，直接删除" else "旧系统无回收站，已直接删除"
            statusText = if (deleted > 0)
                "$baseStatus；已删除 $deleted 个原文件（$noTrash）"
            else
                "$baseStatus；原文件删除失败，已保留"
        }
    }

    /**
     * 导出到系统相册（Pictures/Z-LivePhoto-Converter[/<子目录>]）。
     * 1) MediaStore 标准写入（IS_PENDING，写完才出现在相册）；
     * 2) 失败回退：直接写公共 Pictures 目录 + MediaScannerConnection 触发媒体扫描；
     * 失败原因记录到 lastExportError，随转换结果一起展示。
     *
     * 时间戳语义（修复：修改时间此前被写入时间重写）：
     * - modifiedMs → 修改时间：MediaStore DATE_MODIFIED + FUSE 文件 mtime（setLastModified）
     * - takenMs → 创建时间：DATE_TAKEN（拍摄时间，缺省回退 modifiedMs）
     */
    private fun exportToMediaStore(srcPath: String, modifiedMs: Long, takenMs: Long, relSubDir: String = ""): Uri? {
        val src = File(srcPath)
        if (!src.exists() || src.length() == 0L) {
            setExportError("转换产物缺失或为空：${src.name}")
            return null
        }
        val taken = if (takenMs > 0L) takenMs else modifiedMs
        val ext = src.extension.lowercase()
        val isVideo = ext == "mp4" || ext == "mov"
        val mime = if (isVideo) (if (ext == "mov") "video/quicktime" else "video/mp4") else "image/jpeg"

        // Android 11+：已授予「所有文件访问」时优先直写公共 Pictures 目录（字节级透传，
        // 规避个别 OEM 经 MediaStore 写入脱敏 EXIF GPS 导致的位置丢失）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasAllFilesAccess()) {
            val direct = exportDirectWithScan(src, mime, modifiedMs, relSubDir)
            if (direct != null) return direct
            // 直写失败（如目录异常）继续回退 MediaStore
        }

        // Android 9-：无 MediaStore 相对路径写入（RELATIVE_PATH/IS_PENDING/VOLUME 均为 Q+ 概念），
        // 直接写公共 Pictures 目录 + 触发媒体扫描（需 WRITE_EXTERNAL_STORAGE，已在权限流程申请）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return exportDirectWithScan(src, mime, modifiedMs, relSubDir)
        }

        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        // 图片与视频的 DATE_TAKEN 分属不同集合列（虽然底层同为 datetaken），
        // 按目标集合取对应常量，保证视频的创建时间同样被正确写入
        val dateTakenColumn = if (isVideo) MediaStore.Video.Media.DATE_TAKEN else MediaStore.Images.Media.DATE_TAKEN
        val relativePath = albumRelPath(relSubDir)

        // 1) MediaStore 标准写入（IS_PENDING 流程）
        var inserted: Uri? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                put(MediaStore.MediaColumns.DATE_MODIFIED, modifiedMs / 1000)
                put(dateTakenColumn, taken)
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
                        put(MediaStore.MediaColumns.DATE_MODIFIED, modifiedMs / 1000)
                        put(dateTakenColumn, taken)
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
                        put(MediaStore.MediaColumns.DATE_MODIFIED, modifiedMs / 1000)
                        put(dateTakenColumn, taken)
                    }
                    contentResolver.update(inserted, ts, null, null)
                    // FUSE 层文件 mtime 也会被写入时间重写（后续媒体扫描会用文件系统
                    // mtime 覆盖回数据库，导致之前的固化失效）：查 DATA 实际路径后
                    // 直接 setLastModified 修复文件系统层修改时间
                    contentResolver.query(
                        inserted, arrayOf(MediaStore.MediaColumns.DATA), null, null, null
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            val dataPath = c.getString(0)
                            if (!dataPath.isNullOrEmpty()) File(dataPath).setLastModified(modifiedMs)
                        }
                    }
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
        return exportDirectWithScan(src, mime, modifiedMs, relSubDir)
    }

    /** 回退方案：直接写 Pictures/Z-LivePhoto-Converter 并触发媒体扫描。
     *  文件 mtime=修改时间；扫描后 MediaStore 的 DATE_TAKEN 取文件时间，尽力保留。 */
    private fun exportDirectWithScan(
        src: File, mime: String, modifiedMs: Long, relSubDir: String = ""
    ): Uri? {
        return try {
            val dir = File(
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    albumFolder()
                ),
                relSubDir
            )
            if (!dir.exists() && !dir.mkdirs()) {
                setExportError("无法创建相册目录 $dir")
                return null
            }
            val dst = File(dir, uniqueName(dir, src.name))
            src.copyTo(dst, overwrite = true)
            dst.setLastModified(modifiedMs)
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
