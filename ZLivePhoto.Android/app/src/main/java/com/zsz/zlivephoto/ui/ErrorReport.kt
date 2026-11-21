package com.zsz.zlivephoto.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zsz.zlivephoto.CrashHandler
import com.zsz.zlivephoto.core.AppLog

/**
 * 可提交的错误报告请求。
 *
 * @param reason 一句话原因（弹窗正文）
 * @param detail 附加堆栈/说明（写进报告文件，不直接展示）
 * @param crash 是否来自上次启动的崩溃
 */
data class ErrorReportRequest(
    val reason: String,
    val detail: String? = null,
    val crash: Boolean = false
)

/**
 * 全局错误上报入口：只负责记录日志 + 请求弹窗，具体弹窗由各 flavor 的根壳渲染
 * （normal 用 Glasmio 玻璃弹窗，go 用 Material 3 弹窗）。
 */
object ErrorReporter {
    var request by mutableStateOf<ErrorReportRequest?>(null)
        private set

    fun notify(reason: String, throwable: Throwable? = null) {
        AppLog.error("App", reason, throwable)
        request = ErrorReportRequest(reason, throwable?.stackTraceToString())
    }

    fun notifyCrash(reason: String) {
        request = ErrorReportRequest(reason, null, crash = true)
    }

    fun dismiss() {
        request = null
    }

    /** 生成报告文本（设备信息 + 最近日志）。 */
    fun buildReport(context: Context, req: ErrorReportRequest): String =
        AppLog.buildReport(context, req.reason, req.detail)

    /** 拉起系统分享面板「提交错误报告」。 */
    fun share(context: Context, req: ErrorReportRequest) {
        val text = buildReport(context, req)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ZLC 错误报告")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, "提交错误报告")
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }
}

/**
 * 启动时检查上次崩溃标记（每个进程只检查一次），有则在根壳弹出「提交错误报告」。
 * 必须在 Activity 的 Compose 树内调用。
 */
@Composable
fun PendingErrorReportWatcher(context: Context) {
    LaunchedEffect(Unit) {
        CrashHandler.consumePendingCrash(context)?.let { ErrorReporter.notifyCrash(it) }
    }
}
