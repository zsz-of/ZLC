package com.zsz.zlivephoto

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃处理器：
 * 1. 把崩溃堆栈写到应用专属外部目录与「下载」目录（应用专属目录无需权限；
 *    Android 10+ 通过 MediaStore 写入下载目录，用户可直接在文件管理器里找到并反馈）；
 * 2. 记录「本次启动在首帧前崩溃」标记，下次启动自动回退 Material 3 外壳，
 *    避免液态玻璃路径在个别设备上反复闪退导致应用完全不可用。
 */
object CrashHandler : Thread.UncaughtExceptionHandler {
    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    /** 首帧前崩溃标记的 SharedPreferences 键（与 AppSettings 同一文件） */
    const val KEY_GLASS_CRASH_PENDING = "glass_crash_pending"

    /** 上次启动发生崩溃的原因（下次启动读一次即清除，用于弹「提交错误报告」） */
    private const val KEY_CRASH_REASON_PENDING = "crash_reason_pending"

    /**
     * 当前是否正在「液态玻璃外壳」中运行。只有玻璃外壳内的崩溃才写回退标记，
     * 避免 Material 3 外壳自身的崩溃被误记为玻璃不可用。
     */
    @Volatile
    var liquidShellActive: Boolean = false

    fun install(context: Context) {
        appContext = context.applicationContext
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    /** 上次启动是否在玻璃外壳中崩溃过（决定本次是否强制回落 Material 3）。 */
    fun isGlassCrashPending(context: Context): Boolean =
        context.getSharedPreferences("zlivephoto", Context.MODE_PRIVATE)
            .getBoolean(KEY_GLASS_CRASH_PENDING, false)

    /** 玻璃外壳已成功画出首帧：清除回退标记，允许下次继续使用玻璃。 */
    fun markGlassHealthy(context: Context) {
        context.getSharedPreferences("zlivephoto", Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_GLASS_CRASH_PENDING)
            .apply()
    }

    /**
     * 读取并清除「上次崩溃原因」。返回非空表示上次进程异常退出，
     * 界面应弹「提交错误报告」。
     */
    fun consumePendingCrash(context: Context): String? {
        val prefs = context.getSharedPreferences("zlivephoto", Context.MODE_PRIVATE)
        val reason = prefs.getString(KEY_CRASH_REASON_PENDING, null) ?: return null
        prefs.edit().remove(KEY_CRASH_REASON_PENDING).apply()
        return reason
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val report = buildReport(t, e)
        // 先写入全局日志（仅 ERROR），保证「提交错误报告」里能看到崩溃堆栈
        runCatching {
            com.zsz.zlivephoto.core.AppLog.errorSync(
                "Crash",
                "uncaught exception on ${t.name}",
                e
            )
        }
        try {
            appContext?.let { ctx ->
                // 先同步落盘标记：进程即将结束，必须 commit。
                ctx.getSharedPreferences("zlivephoto", Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CRASH_REASON_PENDING, e.message ?: e.javaClass.simpleName)
                    .commit()
                // 仅玻璃外壳内崩溃才标记，下次启动回落 Material 3，避免反复闪退
                if (liquidShellActive) {
                    ctx.getSharedPreferences("zlivephoto", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_GLASS_CRASH_PENDING, true)
                        .commit()
                }

                ctx.getExternalFilesDir(null)?.let { dir ->
                    runCatching { File(dir, "Z-LivePhoto-Crash.log").appendText(report) }
                }
                runCatching { saveToDownloads(ctx, report) }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    runCatching {
                        File(Environment.getExternalStorageDirectory(), "Z-LivePhoto-Crash.log")
                            .appendText(report)
                    }
                }
            }
        } catch (_: Exception) {
        }

        previousHandler?.uncaughtException(t, e)
    }

    /** 写入系统「下载」目录，便于用户直接把文件发出来。 */
    private fun saveToDownloads(ctx: Context, report: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val name = "ZLC-crash-${fileNameFormat.format(Date())}.txt"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) }
    }

    private fun buildReport(t: Thread, e: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        pw.println("=== Z-LivePhoto-Converter Crash ===")
        pw.println("Time: ${dateFormat.format(Date())}")
        pw.println("Thread: ${t.name}")
        pw.println()
        e.printStackTrace(pw)
        pw.println()
        pw.println("=== Device Info ===")
        pw.println("SDK: ${Build.VERSION.SDK_INT}")
        pw.println("Release: ${Build.VERSION.RELEASE}")
        pw.println("Model: ${Build.MODEL}")
        pw.println("Manufacturer: ${Build.MANUFACTURER}")
        pw.println()
        return sw.toString()
    }
}
