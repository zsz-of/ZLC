package com.zsz.zlivephoto.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    /** true=已安装且检测到新编码器版本（可「跳过此版本」）；false=首次安装/重新安装 */
    var promptUpdateMode by mutableStateOf(false)
        private set
    /** 正在查看转码器更新说明（说明弹窗之上保留主弹窗状态，关闭说明后回到主弹窗） */
    var showNotes by mutableStateOf(false)
        private set
    /** 结果 / 错误提示 */
    var message by mutableStateOf<String?>(null)
        private set
    /** 正在「检查更新」（拉取当前版本的附加项元数据） */
    var checking by mutableStateOf(false)
        private set

    /**
     * 是否有会话型弹窗需要占用屏幕。供宿主（MainActivity）的全局弹窗闸门排队：
     * 同一时刻只允许一个弹窗占用，处理（转换/导入）进行中一律延后。
     */
    val wantsDialog: Boolean
        get() = FfmpegAddon.busy || message != null || promptMeta != null

    /** 展示「选择下载通道」弹窗 */
    fun presentPrompt(meta: FfmpegAddon.AddonMeta, title: String, updateMode: Boolean = false) {
        promptMeta = meta
        promptTitle = title
        promptUpdateMode = updateMode
        showNotes = false
        message = null
    }

    fun openNotes() {
        showNotes = true
    }

    fun closeNotes() {
        showNotes = false
    }

    fun dismissPrompt() {
        promptMeta = null
        promptTitle = "安装转码器"
        promptUpdateMode = false
        showNotes = false
    }

    /**
     * 「跳过此版本」：记住当前编码器版本，除非发布更新的编码器版本，否则自动检查时不再提示
     * （与软件更新的「跳过此版本」逻辑一致；手动「检查更新」仍会给出结果）。
     */
    fun skipThisVersion() {
        promptMeta?.let { AppSettings.rememberSkippedAddonVersion(it.version) }
        dismissPrompt()
    }

    fun closeMessage() {
        message = null
    }

    /**
     * 「检查更新」：拉取当前软件版本对应的转码器元数据，与已安装版本比对。
     * 未安装或版本不匹配 → 弹窗提示下载；匹配 → 提示已是最新。
     * @param quiet true=静默检查（启动时用）：已是最新/无附加项信息/已跳过该版本时不弹提示，仅在不匹配时弹窗
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
                    // 用户已「跳过此版本」时，自动检查不再打扰（手动检查仍给出提示）
                    if (quiet && AppSettings.isAddonVersionSkipped(meta.version)) return false
                    presentPrompt(meta, "发现转码器更新", updateMode = true)
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

/** 渲染 ffmpeg 转码器相关的全部弹窗（下载/解压进度 / 结果提示 / 更新说明 / 选择下载通道） */
@Composable
internal fun FfmpegFlowHosts(
    flow: FfmpegFlowController,
    vibrate: () -> Unit = {},
    enabled: Boolean = true
) {
    // 全局弹窗闸门：同一时刻只允许一个会话型弹窗（处理进行中时由闸门整体抑制）
    if (!enabled) return

    // ── 下载 / 校验 / 解压进度（状态由 FfmpegAddon 维护，与设置页共用）──
    if (FfmpegAddon.busy) {
        val progress = FfmpegAddon.downloadProgress
        val extract = FfmpegAddon.extractProgress
        val extracting = extract >= 0f
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (extracting) "解压转码器" else "下载转码器") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val label = when {
                        extracting && extract >= 0f -> "解压中 ${(extract * 100).toInt()}%…"
                        extracting -> "解压中…"
                        progress >= 0f -> "下载中 ${(progress * 100).toInt()}%…"
                        else -> "下载中…"
                    }
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    val fraction = if (extracting) extract else progress
                    if (fraction >= 0f) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
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

    // ── 编码器更新说明子弹窗（与软件更新的「更新说明」同构，关闭后回到主弹窗）──
    val meta = flow.promptMeta
    if (meta != null && flow.showNotes) {
        AlertDialog(
            onDismissRequest = { flow.closeNotes() },
            title = { Text("编码器 ${meta.version} · 说明") },
            text = {
                Text(
                    addonNotes(meta),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(onClick = { vibrate(); flow.closeNotes() }) { Text("返回") }
            }
        )
        return
    }

    // ── 选择下载通道（发现更新 / 首次安装 / 重新安装）──
    if (meta != null) {
        val installed = FfmpegAddon.installedVersion
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
                        if (flow.promptUpdateMode && installed.isNotEmpty())
                            "当前已安装 $installed，可更新到 ${meta.version}。"
                        else
                            "下载完成后将自动校验 SHA-1 并解压安装，随后删除压缩包。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Spacer(Modifier.height(2.dp))
                    if (flow.promptUpdateMode) {
                        // 更新模式：与软件更新一致 —— 「暂不下载」+「跳过此版本」
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    vibrate()
                                    flow.dismissPrompt()
                                },
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) { Text("暂不下载") }
                            OutlinedButton(
                                onClick = {
                                    vibrate()
                                    flow.skipThisVersion()
                                },
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) { Text("跳过此版本") }
                        }
                    } else {
                        // 首次安装 / 重新安装：无可跳过的版本，只留整行「暂不下载」
                        OutlinedButton(
                            onClick = {
                                vibrate()
                                flow.dismissPrompt()
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) { Text("暂不下载") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}

/** 转码器「更新说明」正文：由附加项元数据生成（与发布页正文中的转码器说明对应） */
private fun addonNotes(meta: FfmpegAddon.AddonMeta): String = buildString {
    appendLine("编码器版本：${meta.version}")
    appendLine("适用软件版本：v${BuildConfig.VERSION_NAME}")
    appendLine()
    appendLine("包含 libx264 / libx265 编码器，可把非标准 MP4 视频（如 WebM / MKV / AV1）转码为标准 MP4（H.264 / H.265）后再合成动态照片。")
    appendLine()
    appendLine("下载完成后自动校验 SHA-1 并解压安装，随后删除压缩包；转码器不内置在 APK 中，可随时在设置页删除。")
    appendLine()
    append("SHA-1：${meta.sha1}")
}
