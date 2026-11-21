package com.zsz.zlivephoto.core

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 全局日志（仅记录 WARN / ERROR）。
 *
 * - 落盘位置：应用私有目录 `files/logs/zlog.txt`，用户无权限要求、卸载即清除；
 * - 单文件上限 [MAX_BYTES]，超出后滚动为 `zlog.1.txt`（只保留一份历史），
 *   避免长期使用后无限膨胀；
 * - 写入走单线程队列，转换线程/主线程调用都不会被磁盘 IO 阻塞；
 * - [buildReport] 生成可直接提交的错误报告（设备信息 + 最近日志 + 堆栈）。
 *
 * 设计约束：本类位于 main 源集，normal / go 两个 flavor 共用，不依赖任何 UI 库。
 */
object AppLog {
    private const val MAX_BYTES = 512L * 1024L
    private const val MAX_TAIL_LINES = 400

    private var appContext: Context? = null
    private val writeLock = Any()
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "zlc-log").apply { isDaemon = true }
    }
    private val lineFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val reportFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /** 在 Application.onCreate 调用一次。 */
    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun warn(tag: String, message: String, throwable: Throwable? = null) {
        write("W", tag, message, throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        write("E", tag, message, throwable)
    }

    /**
     * 同步写 ERROR：仅用于进程即将结束的崩溃路径（异步队列会随进程一起消失）。
     */
    fun errorSync(tag: String, message: String, throwable: Throwable? = null) {
        val ctx = appContext ?: return
        val line = formatLine("E", tag, message, throwable)
        synchronized(writeLock) { append(ctx, line) }
    }

    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        val ctx = appContext ?: return
        val line = formatLine(level, tag, message, throwable)
        writer.execute {
            synchronized(writeLock) { append(ctx, line) }
        }
    }

    private fun formatLine(level: String, tag: String, message: String, throwable: Throwable?): String =
        buildString {
            append(lineFormat.format(Date()))
            append(' ').append(level).append('/').append(tag).append(": ").append(message)
            if (throwable != null) {
                append('\n').append(throwable.stackTraceToString().trimEnd())
            }
            append('\n')
        }

    private fun append(ctx: Context, line: String) {
        runCatching {
            val file = logFile(ctx)
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() + line.length > MAX_BYTES) {
                val backup = File(file.parentFile, "zlog.1.txt")
                runCatching { backup.delete() }
                runCatching { file.renameTo(backup) }
            }
            file.appendText(line)
        }
    }

    fun logFile(context: Context): File = File(File(context.filesDir, "logs"), "zlog.txt")

    /** 读取最近 [maxLines] 行（含滚动后的历史文件）。 */
    fun readTail(context: Context, maxLines: Int = MAX_TAIL_LINES): String {
        val file = logFile(context)
        val backup = File(file.parentFile, "zlog.1.txt")
        val lines = ArrayList<String>(maxLines * 2)
        for (f in listOf(backup, file)) {
            if (!f.exists()) continue
            runCatching { f.readLines().forEach { lines.add(it) } }
        }
        if (lines.isEmpty()) return ""
        return lines.takeLast(maxLines).joinToString("\n")
    }

    /**
     * 生成提交用的错误报告：时间 / 原因 / 设备信息 / 最近日志。
     * [detail] 可附带上层捕获的堆栈或附加说明。
     */
    fun buildReport(context: Context, reason: String, detail: String? = null): String {
        val sb = StringBuilder()
        sb.append("=== Z-LivePhoto 错误报告 ===\n")
        sb.append("时间: ").append(reportFormat.format(Date())).append('\n')
        sb.append("版本: ").append(runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?").append('\n')
        sb.append("包名: ").append(context.packageName).append('\n')
        sb.append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        sb.append("系统: Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("原因: ").append(reason).append('\n')
        if (!detail.isNullOrBlank()) {
            sb.append("详情:\n").append(detail.trimEnd()).append('\n')
        }
        val tail = readTail(context)
        sb.append("\n=== 最近日志（仅警告与错误）===\n")
        sb.append(tail.ifBlank { "（暂无日志）" }).append('\n')
        return sb.toString()
    }
}
