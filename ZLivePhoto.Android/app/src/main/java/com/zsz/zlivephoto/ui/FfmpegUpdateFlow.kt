package com.zsz.zlivephoto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.core.DownloadChannel
import com.zsz.zlivephoto.core.FfmpegAddon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * ffmpeg 转码器附加项的「安装 / 重新安装 / 检查更新 / 删除」流程控制器 + 弹窗。
 *
 * 与软件更新弹窗 [UpdateFlowController] 同构：
 * - 发现「未安装 / 版本不匹配」时弹窗提示，给用户选择下载通道（蓝奏云优先 / GitHub 次选）；
 * - 下载进度由 [FfmpegAddon.busy] / [FfmpegAddon.downloadProgress] 驱动（与设置页共用同一状态）；
 * - 安装/重新安装永远只安装与当前软件版本匹配的转码器版本；删除仅清本地文件。
 */
internal class FfmpegFlowController(private val scope: CoroutineScope) {
    /** 待下载/安装的转码器元数据（非 null = 展示「选择下载通道」弹窗） */
    var promptMeta by mutableStateOf<FfmpegAddon.AddonMeta?>(null)
        private set
    /** 弹窗标题：区分「发现转码器更新 / 首次安装」与「重新安装转码器」 */
    var promptTitle by mutableStateOf("安装转码器")
        private set
    /** 结果 / 错误提示 */
    var message by mutableStateOf<String?>(null)
        private set
    /** 正在「检查更新」（拉取当前版本的附加项元数据） */
    var checking by mutableStateOf(false)
        private set

    /** 展示「选择下载通道」弹窗 */
    fun presentPrompt(meta: FfmpegAddon.AddonMeta, title: String) {
        promptMeta = meta
        promptTitle = title
        message = null
    }

    fun dismissPrompt() {
        promptMeta = null
    }

    fun closeMessage() {
        message = null
    }

    /**
     * 「检查更新」：拉取当前软件版本对应的转码器元数据，与已安装版本比对。
     * 未安装或版本不匹配 → 弹窗提示下载；匹配 → 提示已是最新。
     * @param quiet true=静默检查（启动时用）：已是最新/无附加项信息时不弹提示，仅在不匹配时弹窗
     */
    fun checkUpdate(quiet: Boolean = false) {
        if (checking || FfmpegAddon.busy) return
        scope.launch { checkUpdateSuspend(quiet) }
    }

    /**
     * 「检查更新」的挂起版本：返回本次是否真的弹出了「选择下载通道」提示，
     * 供启动流程判断是否已占用弹窗（避免与旧版本卸载提示同框）。
     */
    suspend fun checkUpdateSuspend(quiet: Boolean = false): Boolean {
        if (checking || FfmpegAddon.busy) return false
        checking = true
        try {
            val meta = FfmpegAddon.fetchMetaForCurrentVersion()
            if (meta == null) {
                if (!quiet) message = "当前版本暂无转码器附加项信息，请稍后重试"
                return false
            }
            val installed = FfmpegAddon.installedVersion
            return when {
                installed.isEmpty() -> {
                    presentPrompt(meta, "安装转码器")
                    true
                }
                installed != meta.version -> {
                    presentPrompt(meta, "发现转码器更新（$installed → ${meta.version}）")
                    true
                }
                else -> {
                    if (!quiet) message = "转码器已是最新版本（$installed）"
                    false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!quiet) message = e.message ?: "检查失败"
            return false
        } finally {
            checking = false
        }
    }

    /**
     * 「安装 / 重新安装转码器」：只安装与当前软件版本匹配的转码器版本。
     * 拉取当前版本元数据后直接弹出「选择下载通道」弹窗（已安装时为覆盖安装）。
     * @param reinstall true=已安装过（弹窗标题为「重新安装转码器」）
     */
    fun install(reinstall: Boolean = false) {
        if (checking || FfmpegAddon.busy) return
        scope.launch {
            checking = true
            try {
                val meta = FfmpegAddon.fetchMetaForCurrentVersion()
                if (meta == null) {
                    message = "当前版本暂无转码器附加项信息，请稍后重试"
                    return@launch
                }
                presentPrompt(meta, if (reinstall) "重新安装转码器" else "安装转码器")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "操作失败"
            } finally {
                checking = false
            }
        }
    }

    /** 「删除转码器」：删除本地已安装的转码器（不发网络请求），转码功能随即停用 */
    fun deleteAddon() {
        if (FfmpegAddon.busy) return
        FfmpegAddon.uninstall()
        message = "已删除转码器，转码功能已停用（可随时重新安装）"
    }

    private fun download(meta: FfmpegAddon.AddonMeta, channel: DownloadChannel) {
        promptMeta = null
        scope.launch {
            try {
                FfmpegAddon.downloadAndInstall(meta, channel)
                message = "转码器安装完成（${FfmpegAddon.installedVersion}）"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "下载失败"
            }
        }
    }

    fun downloadFromLanzou() {
        val meta = promptMeta ?: return
        download(meta, DownloadChannel.LANZOU)
    }

    fun downloadFromGithub() {
        val meta = promptMeta ?: return
        download(meta, DownloadChannel.GITHUB)
    }
}

@Composable
internal fun rememberFfmpegFlow(): FfmpegFlowController {
    val scope = rememberCoroutineScope()
    return remember { FfmpegFlowController(scope) }
}

/** 渲染 ffmpeg 转码器相关的全部弹窗（下载进度 / 结果提示 / 选择下载通道） */
@Composable
internal fun FfmpegFlowHosts(flow: FfmpegFlowController, vibrate: () -> Unit = {}) {
    // ── 下载 / 校验 / 解压进度（状态由 FfmpegAddon 维护，与设置页共用）──
    if (FfmpegAddon.busy) {
        val progress = FfmpegAddon.downloadProgress
        AlertDialog(
            onDismissRequest = {},
            title = { Text("下载转码器") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val label = if (progress >= 0f) "下载中 ${(progress * 100).toInt()}%…" else "下载中…"
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    if (progress >= 0f) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
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
                Button(onClick = { vibrate(); flow.closeMessage() }) { Text("知道了") }
            }
        )
        return
    }

    // ── 选择下载通道（发现更新 / 首次安装 / 重新安装）──
    val meta = flow.promptMeta
    if (meta != null) {
        AlertDialog(
            onDismissRequest = {
                vibrate()
                flow.dismissPrompt()
            },
            title = {
                Text(flow.promptTitle, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "编码器版本 ${meta.version}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "下载完成后将自动校验 SHA-1 并解压安装，随后删除压缩包。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    // 首选渠道：蓝奏云
                    Button(
                        onClick = {
                            vibrate()
                            flow.downloadFromLanzou()
                        },
                        enabled = !meta.mirrorUrl.isNullOrBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(if (meta.mirrorUrl.isNullOrBlank()) "蓝奏云暂不可用" else "从蓝奏云下载")
                    }
                    // 次选渠道：GitHub
                    OutlinedButton(
                        onClick = {
                            vibrate()
                            flow.downloadFromGithub()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) { Text("从 GitHub 下载") }
                    // 暂不下载：与上面两个按钮同款尺寸/风格（整行宽度）
                    OutlinedButton(
                        onClick = {
                            vibrate()
                            flow.dismissPrompt()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) { Text("暂不下载") }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}
