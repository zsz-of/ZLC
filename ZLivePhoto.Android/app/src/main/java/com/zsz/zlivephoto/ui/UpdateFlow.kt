package com.zsz.zlivephoto.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
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
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 更新弹窗的状态控制器：管理「发现新版本」主弹窗 + 更新说明子弹窗 +
 * 下载进度 + 安装权限引导 + 结果提示 的全部状态与流程。
 *
 * 下载策略（v3.1.10+ 起）：
 * - 首选渠道「从蓝奏云下载」：原生 HTTP 解析蓝奏云分享页得到最终 CDN 直链，
 *   直接下载 zip → 解压 APK → 安装（不再打开内置浏览器，无需用户手动点击下载）。
 * - 次选渠道「从 GitHub 下载」：内置下载对应 flavor 的 APK 资产。
 * - 下载弹窗可取消（立即停止并清理缓存）；下载完成前不会产生 .apk 半成品。
 * - 若安装需要「安装未知应用」权限：先引导去系统设置授权，授权返回后直接续装，
 *   不会要求重新下载。
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
    /** 等待用户去系统设置授予「安装未知应用」权限（APK 已下载完，授权后直接续装） */
    var permissionApk by mutableStateOf<File?>(null)
        private set
    /** 结果/错误提示 */
    var message by mutableStateOf<String?>(null)
        private set
    /** 是否为「重新安装本版本」模式：弹窗标题/文案与「发现新版本」区分，隐藏「跳过此版本」 */
    var reinstallMode by mutableStateOf(false)
        private set

    private var downloadJob: Job? = null

    /** 当前版本对应的发布渠道标签（normal=标准版，go=Go版） */
    val flavorLabel: String
        get() = if (BuildConfig.FLAVOR == "go") "Go 版" else "标准版"

    /** 展示「发现新版本」弹窗 */
    fun present(newInfo: UpdateInfo, reinstall: Boolean = false) {
        info = newInfo
        reinstallMode = reinstall
        showNotes = false
        busy = null
        permissionApk = null
        message = null
        downloadJob = null
    }

    /**
     * 「重新安装本版本」：调用方已确认“当前即是最新版本”并持有最新版信息 [latest]，
     * 直接把它当作更新目标展示，复用「下载→安装」链路，便于随时回归验证更新机制。
     * 是否最新的判断由设置页在渲染该入口时完成，此处不再发起网络请求。
     */
    fun startReinstall(latest: UpdateInfo) {
        if (info != null || busy != null) return
        present(latest, reinstall = true)
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
        downloadJob?.cancel()
        downloadJob = null
        info = null
        reinstallMode = false
        showNotes = false
        busy = null
        permissionApk = null
        message = null
    }

    /** GitHub 当前版本是否有可直接下载的 APK 资产（UpdateChecker 已按 flavor 匹配） */
    private fun githubApkUrl(): String? =
        info?.downloadUrl?.takeIf { it.endsWith(".apk", ignoreCase = true) }

    /** 首选渠道：点「从蓝奏云下载」→ 原生 HTTP 解析出最终直链后直接下载安装（无需打开浏览器） */
    fun downloadFromLanzou() {
        val lz = info?.lanzouUrl
        if (lz.isNullOrEmpty()) {
            message = "该版本未提供蓝奏云下载链接，可改用 GitHub 下载。"
            return
        }
        runDownload("蓝奏云下载失败") {
            busy = BusyState("正在解析蓝奏云下载链接…", null)
            val directUrl = AppUpdater.resolveLanzouDirectLink(lz)
            downloadAndInstall(directUrl, lz, "正在从蓝奏云下载…")
        }
    }

    /** 次选渠道：点「从 GitHub 下载」→ 内置下载安装（按 normal/go 匹配 APK 资产） */
    fun downloadFromGithub() {
        val g = githubApkUrl()
        if (g == null) {
            message = "该版本没有提供与本版本匹配的安装包直链，请到 GitHub 发布页选择对应 APK。"
            return
        }
        runDownload("GitHub 下载失败") {
            // GitHub 资产走多级重定向直链，国内网络偶发首连失败；短暂等待后自动重试一次
            var attempts = 0
            while (true) {
                attempts++
                try {
                    downloadAndInstall(g, null, "正在从 GitHub 下载…")
                    return@runDownload
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (attempts >= 2) throw e
                    busy = null
                    delay(1500)
                }
            }
        }
    }

    /** 取消当前下载：先取消协程、再断开阻塞中的网络连接，立即清理缓存目录 */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        AppUpdater.abortActiveDownload()
        busy = null
        cleanupCache()
    }

    /** 取消「去授权」：不安装，清掉已下载的安装包 */
    fun cancelPermission() {
        permissionApk = null
        cleanupCache()
    }

    /** 系统「安装未知应用」授权页返回后的回调：已授权则直接续装（不重新下载） */
    fun onPermissionResult() {
        val apk = permissionApk ?: return
        permissionApk = null
        if (AppUpdater.needsInstallPermission(context)) {
            cleanupCache()
            message = "未获得「安装未知应用」权限，无法自动安装。请到系统设置中允许后重试。"
            return
        }
        finishInstall(apk)
    }

    fun closeMessage() {
        message = null
    }

    // ---------- 下载流程 ----------

    /** 启动一个可取消的下载任务；异常统一转为 message（前缀区分渠道） */
    private fun runDownload(failPrefix: String, block: suspend () -> Unit) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                busy = null
                cleanupCache()
                message = "$failPrefix：${e.message ?: "未知错误"}"
            }
        }
    }

    private suspend fun downloadAndInstall(url: String, referer: String?, label: String) {
        busy = BusyState(label, null)
        val file = AppUpdater.downloadToCache(context, url, referer) { done, total ->
            busy = BusyState(
                label,
                if (total > 0L) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
            )
        }
        busy = BusyState("正在解压安装包…", null)
        val apk = AppUpdater.resolveApk(context, file)
        busy = null
        if (AppUpdater.needsInstallPermission(context)) {
            // APK 已就绪：等用户授权后直接续装，避免重复下载
            permissionApk = apk
            return
        }
        finishInstall(apk)
    }

    private fun finishInstall(apk: File) {
        val err = AppUpdater.installApk(context, apk)
        if (err == null) {
            // 成功拉起系统安装器。
            // 注意：绝不能在此删除缓存 APK——系统安装器是「异步」通过 FileProvider
            // 读取文件的，startActivity 一返回就删文件会让安装器读到不存在的包，
            // 表现为一律「解析包出现问题」（曾长期存在的根因）。
            // 缓存目录会在下一次更新下载开始时自动清空，残留文件无害。
            dismissAll()
        } else {
            cleanupCache()
            message = err
        }
    }

    private fun cleanupCache() {
        runCatching { File(context.cacheDir, "zlc_update").deleteRecursively() }
    }
}

@Composable
internal fun rememberUpdateFlow(): UpdateFlowController {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember { UpdateFlowController(context, scope) }
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
    val context = LocalContext.current

    // 「安装未知应用」授权页返回后回调（已授权则自动续装）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        flow.onPermissionResult()
    }

    // ── 下载 / 解压进度（带「取消」按钮，立即停止并清理缓存）──
    val busy = flow.busy
    if (busy != null) {
        AlertDialog(
            onDismissRequest = {}, // 下载中不可通过外部点击关闭，需点「取消」
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
            confirmButton = {
                TextButton(onClick = {
                    vibrate()
                    flow.cancelDownload()
                }) { Text("取消") }
            },
            dismissButton = {}
        )
        return
    }

    // ── 安装权限引导（APK 已下载完，去授权后返回自动续装）──
    val pApk = flow.permissionApk
    if (pApk != null) {
        AlertDialog(
            onDismissRequest = {}, // 必须明确选择，防止误关导致不知道安装包去向
            title = { Text("需要安装权限") },
            text = {
                Text(
                    "安装新版本需要系统「安装未知应用」权限。\n\n" +
                        "点击「去授权」，在设置中允许后返回应用，将自动继续安装（无需重新下载）。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    vibrate()
                    permissionLauncher.launch(AppUpdater.installPermissionIntent(context))
                }) { Text("去授权") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vibrate()
                    flow.cancelPermission()
                }) { Text("取消") }
            }
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

    // ── 「发现新版本」/「重新安装本版本」主弹窗 ──
    if (info != null) {
        val lanzou = info.lanzouUrl
        AlertDialog(
            onDismissRequest = {
                // 点外部/返回视同「暂不更新」
                vibrate()
                flow.onLater()
            },
            title = {
                Text(
                    if (flow.reinstallMode) "重新安装本版本" else "发现新版本",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "v${info.version} · ${flow.flavorLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (flow.reinstallMode) {
                        // 「重新安装」模式：明确告知这是覆盖安装当前版本，用于验证下载→安装链路
                        Text(
                            "将下载并重新安装该版本（覆盖当前应用）。\n此功能用于回归测试更新机制。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                    // 首选渠道：蓝奏云（内置浏览器打开分享页，用户点击下载后自动拦截直链下载安装）
                    Button(
                        onClick = {
                            vibrate()
                            flow.downloadFromLanzou()
                        },
                        enabled = !lanzou.isNullOrEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text(if (lanzou.isNullOrEmpty()) "蓝奏云暂不可用" else "从蓝奏云下载") }
                    // 次选渠道：GitHub（内置下载，已按 normal/go 匹配对应 APK 资产）
                    OutlinedButton(
                        onClick = {
                            vibrate()
                            flow.downloadFromGithub()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) { Text("从 GitHub 下载") }
                    Spacer(Modifier.height(2.dp))
                    if (!flow.reinstallMode) {
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
                    } else {
                        // 「重新安装」模式无需「跳过此版本」（目标版本＝当前/最新版），只留暂不更新
                        FilledTonalButton(
                            onClick = {
                                vibrate()
                                flow.onLater()
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) { Text("暂不更新") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}
