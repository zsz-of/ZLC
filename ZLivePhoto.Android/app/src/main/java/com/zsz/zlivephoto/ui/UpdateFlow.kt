package com.zsz.zlivephoto.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.BuildConfig
import com.zsz.zlivephoto.core.AppUpdater
import com.zsz.zlivephoto.core.UpdateInfo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 更新弹窗的状态控制器：管理「发现新版本」主弹窗 + 更新说明子弹窗 +
 * 下载进度 + 结果提示 的全部状态与流程。
 *
 * 下载策略：主按钮「从 GitHub 下载」走内置下载（按 normal/go 自动匹配对应 APK
 * 资产）；「从蓝奏云下载」仅跳外部浏览器打开分享页（不内置下载）。
 */
internal class BusyState(val label: String, val fraction: Float?)

internal class UpdateFlowController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    var info by mutableStateOf<UpdateInfo?>(null)
        private set
    /** 正在查看更新说明（说明弹窗之上仍保留主弹窗状态，关闭说明后回到主弹窗） */
    var showNotes by mutableStateOf(false)
        private set
    /** 下载/解压进行中 */
    var busy by mutableStateOf<BusyState?>(null)
        private set
    /** 结果/错误提示 */
    var message by mutableStateOf<String?>(null)
        private set

    /** 当前版本对应的发布渠道标签（normal=标准版，go=Go版） */
    val flavorLabel: String
        get() = if (BuildConfig.FLAVOR == "go") "Go 版" else "标准版"

    /** 展示「发现新版本」弹窗 */
    fun present(newInfo: UpdateInfo) {
        info = newInfo
        showNotes = false
        busy = null
        message = null
    }

    /** 跳过此版本：记住版本号，除非发布更新的版本否则不再提示 */
    fun onSkipThisVersion() {
        info?.let { AppSettings.rememberSkippedVersion(it.version) }
        dismissAll()
    }

    /** 暂不更新：关掉弹窗，下次启动/手动检查时再提示 */
    fun onLater() = dismissAll()

    fun openNotes() {
        showNotes = true
    }

    fun closeNotes() {
        showNotes = false
    }

    private fun dismissAll() {
        info = null
        showNotes = false
        busy = null
        message = null
    }

    /** GitHub 当前版本是否有可直接下载的 APK 资产（UpdateChecker 已按 flavor 匹配） */
    private fun githubApkUrl(): String? =
        info?.downloadUrl?.takeIf { it.endsWith(".apk", ignoreCase = true) }

    /** 点「从 GitHub 下载」：内置下载（下载/解压/调系统安装器） */
    fun downloadFromGithub() {
        val g = githubApkUrl()
        if (g != null) {
            busy = BusyState("正在从 GitHub 下载…", null)
            scope.launch {
                try {
                    downloadAndInstall(g, "正在从 GitHub 下载…")
                } catch (e: Exception) {
                    busy = null
                    message = "GitHub 下载失败：${e.message}\n\n可改用下方「从蓝奏云下载」在浏览器中下载安装包。"
                }
            }
        } else {
            message = "该版本没有提供与本版本匹配的安装包直链，请到 GitHub 发布页选择对应 APK。"
        }
    }

    /** 点「从蓝奏云下载」：跳外部浏览器打开分享页 */
    fun downloadFromLanzou() {
        val lz = info?.lanzouUrl
        if (lz.isNullOrEmpty()) {
            message = "该版本未提供蓝奏云下载链接。"
        } else {
            openBrowser(context, lz)
        }
    }

    fun closeMessage() {
        message = null
    }

    // ---------- 下载流程 ----------

    private suspend fun downloadAndInstall(url: String, label: String) {
        busy = BusyState(label, null)
        try {
            val file = AppUpdater.downloadToCache(context, url, null) { done, total ->
                busy = BusyState(
                    label,
                    if (total > 0L) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
                )
            }
            busy = BusyState("正在解压安装包…", null)
            val apk = AppUpdater.resolveApk(context, file)
            busy = null
            val err = AppUpdater.installApk(context, apk)
            if (err == null) {
                // 成功拉起系统安装器
                runCatching { File(context.cacheDir, "zlc_update").deleteRecursively() }
                dismissAll()
            } else {
                message = err
            }
        } catch (e: Exception) {
            busy = null
            message = e.message ?: "下载失败，请稍后重试"
        }
    }
}

@Composable
internal fun rememberUpdateFlow(): UpdateFlowController {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember { UpdateFlowController(context, scope) }
}

private fun openBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {}
}

/** 从更新说明中隐藏「[蓝奏云-xx]: url」这类发布元数据行，只给用户看可读内容 */
private fun notesForDisplay(notes: String?): String {
    if (notes.isNullOrBlank()) return "该版本暂无更新说明。"
    val kept = notes.lineSequence()
        .filterNot { it.trimStart().startsWith("[蓝奏云-") }
        .joinToString("\n")
        .trim()
    return kept.ifEmpty { "该版本暂无更新说明。" }
}

/** 在宿主界面渲染更新相关的全部弹窗（调用一次即可，状态由 [flow] 驱动） */
@Composable
internal fun UpdateFlowHosts(flow: UpdateFlowController, vibrate: () -> Unit = {}) {
    // ── 下载 / 解压进度（必须排在主弹窗之前渲染，否则进度被主弹窗分支吞掉）──
    val busy = flow.busy
    if (busy != null) {
        AlertDialog(
            onDismissRequest = {}, // 下载中不可关闭
            title = { Text("下载更新") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(busy.label, style = MaterialTheme.typography.bodyMedium)
                    if (busy.fraction != null) {
                        LinearProgressIndicator(
                            progress = { busy.fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
        return
    }

    // ── 结果 / 错误提示 ──
    val msg = flow.message
    if (msg != null) {
        AlertDialog(
            onDismissRequest = { flow.closeMessage() },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = {
                Button(onClick = {
                    vibrate()
                    flow.closeMessage()
                }) { Text("知道了") }
            }
        )
        return
    }

    // ── 更新说明子弹窗（markdown 排版；关闭后回到主弹窗）──
    val info = flow.info
    if (info != null && flow.showNotes) {
        AlertDialog(
            onDismissRequest = { flow.closeNotes() },
            title = { Text("v${info.version} · 更新说明") },
            text = {
                MarkdownBody(
                    markdown = notesForDisplay(info.notes),
                    modifier = Modifier.heightIn(max = 400.dp).padding(top = 4.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    vibrate()
                    flow.closeNotes()
                }) { Text("返回") }
            }
        )
        return
    }

    // ── 「发现新版本」主弹窗 ──
    if (info != null) {
        val lanzou = info.lanzouUrl
        AlertDialog(
            onDismissRequest = {
                // 点外部/返回视同「暂不更新」
                vibrate()
                flow.onLater()
            },
            title = { Text("发现新版本", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "v${info.version} · ${flow.flavorLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                vibrate()
                                flow.openNotes()
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            "查看更新说明",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "›",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    // 默认渠道：GitHub（内置下载，已按 normal/go 匹配对应 APK 资产）
                    Button(
                        onClick = {
                            vibrate()
                            flow.downloadFromGithub()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("从 GitHub 下载") }
                    // 备用渠道：蓝奏云（仅跳外部浏览器，不用内置下载器）
                    if (!lanzou.isNullOrEmpty()) {
                        OutlinedButton(
                            onClick = {
                                vibrate()
                                flow.downloadFromLanzou()
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) { Text("从蓝奏云下载") }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                vibrate()
                                flow.onLater()
                            },
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) { Text("暂不更新") }
                        OutlinedButton(
                            onClick = {
                                vibrate()
                                flow.onSkipThisVersion()
                            },
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) { Text("跳过此版本") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}
