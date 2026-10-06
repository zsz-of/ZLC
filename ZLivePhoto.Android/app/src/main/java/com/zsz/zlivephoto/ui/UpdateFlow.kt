package com.zsz.zlivephoto.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.BuildConfig
import com.zsz.zlivephoto.R
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
    /** 是否为「切换到正常版」模式：Go 版在 Android 10+ 上引导下载 normal 版 */
    var switchToNormalMode by mutableStateOf(false)
        private set
    /** Go 强制切换但未能获取新版本信息（无网络等）：仍不可关闭，只提供「重试」 */
    var forcedBlocked by mutableStateOf(false)
        private set
    /** Go 运行在 Android 10+ 且本机已安装正常版：不可关闭，必须打开正常版才能继续使用 */
    var requireNormalOpen by mutableStateOf(false)
        private set

    /**
     * 对应的 GitHub 链接：优先 Release 页面（浏览器里可自行挑选对应架构/渠道的包），
     * 其次才是 APK 直链。
     */
    private fun githubBrowserUrl(): String? =
        info?.releaseUrl?.takeIf { it.isNotBlank() } ?: githubApkUrl()

    /**
     * 兜底渠道：用外置浏览器打开对应的 GitHub 链接。
     * 与应用内下载互不依赖——应用内解析失败、国内网络访问 GitHub API/资产异常，
     * 或系统限制应用内安装时，用户仍可从浏览器页面自行下载安装包。
     */
    fun openGithubInBrowser() {
        val url = githubBrowserUrl()
        if (url.isNullOrEmpty()) {
            message = context.getString(R.string.update_github_url_missing)
            return
        }
        openInBrowser(context, url)
    }

    private var downloadJob: Job? = null

    /**
     * 是否有会话型弹窗需要占用屏幕。供宿主（MainActivity）的全局弹窗闸门排队：
     * 同一时刻只允许一个弹窗占用，处理（转换/导入）进行中一律延后。
     */
    val wantsDialog: Boolean
        get() = requireNormalOpen || forcedBlocked || busy != null || permissionApk != null ||
            message != null || info != null

    /**
     * 是否为强制切换：Go 轻量版运行在 Android 10 及以上时，旧版本已不再兼容，
     * 主弹窗不可通过外部点击/返回键关闭，也不提供「暂不切换」，必须更新后才能继续使用。
     */
    val forcedSwitch: Boolean
        get() = BuildConfig.FLAVOR == "go" &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** 当前版本对应的发布渠道标签（normal=标准版，go=Go版；切正常版时强制标准版） */
    val flavorLabel: String
        get() = if (switchToNormalMode || BuildConfig.FLAVOR != "go") context.getString(R.string.update_flavor_standard) else context.getString(R.string.update_flavor_go)

    /** 展示「发现新版本」弹窗 */
    fun present(newInfo: UpdateInfo, reinstall: Boolean = false) {
        info = newInfo
        reinstallMode = reinstall
        switchToNormalMode = false
        forcedBlocked = false
        requireNormalOpen = false
        showNotes = false
        busy = null
        permissionApk = null
        message = null
        downloadJob = null
    }

    /** 「切换到正常版」：Go 版在 Android 10+ 上引导下载 normal 版（文案与发现新版本区分） */
    fun presentSwitchToNormal(newInfo: UpdateInfo) {
        present(newInfo, reinstall = false)
        switchToNormalMode = true
    }

    /** Go 强制切换拉取失败：显示不可关闭的阻塞弹窗（仅「重试」） */
    fun showForcedBlocked() {
        forcedBlocked = true
    }

    /** 关闭阻塞弹窗（重试前先收起，由宿主重新发起拉取） */
    fun clearForcedBlocked() {
        forcedBlocked = false
    }

    /**
     * Go 运行在 Android 10+ 且本机已安装正常版：显示不可关闭的弹窗，只能「打开正常版」。
     * 比「去下载」优先——重复下载安装已是多余，直接打开即可继续使用。
     */
    fun presentRequireNormalOpen() {
        if (requireNormalOpen) return
        dismissAll()
        requireNormalOpen = true
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
        switchToNormalMode = false
        forcedBlocked = false
        requireNormalOpen = false
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
            message = context.getString(R.string.update_lanzou_url_missing)
            return
        }
        // 蓝奏现已对非会员上传的分享强制加提取码，提取码与链接同写在 Release 正文里
        val passwd = info?.lanzouPasswd
        runDownload(context.getString(R.string.update_fail_lanzou)) {
            busy = BusyState(context.getString(R.string.update_lanzou_resolving), null)
            val directUrl = AppUpdater.resolveLanzouDirectLink(lz, passwd)
            downloadAndInstall(directUrl, lz, context.getString(R.string.update_lanzou_downloading))
        }
    }

    /** 次选渠道：点「从 GitHub 下载」→ 内置下载安装（按 normal/go 匹配 APK 资产） */
    fun downloadFromGithub() {
        val g = githubApkUrl()
        if (g == null) {
            message = context.getString(R.string.update_apk_direct_missing)
            return
        }
        runDownload(context.getString(R.string.update_fail_github)) {
            // GitHub 资产走多级重定向直链，国内网络偶发首连失败；短暂等待后自动重试一次
            var attempts = 0
            while (true) {
                attempts++
                try {
                    downloadAndInstall(g, null, context.getString(R.string.update_github_downloading))
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
            message = context.getString(R.string.update_install_permission_missing)
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
                message = "$failPrefix：${e.message ?: context.getString(R.string.update_error_unknown)}"
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
        busy = BusyState(context.getString(R.string.update_unzipping), null)
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
private fun notesForDisplay(notes: String?, emptyText: String): String {
    if (notes.isNullOrBlank()) return emptyText
    val kept = notes.lineSequence()
        .filterNot { it.trimStart().startsWith("[蓝奏云-") }
        .joinToString("\n")
        .trim()
    return kept.ifEmpty { emptyText }
}

/** 在宿主界面渲染更新相关的全部弹窗（调用一次即可，状态由 [flow] 驱动） */
@Composable
internal fun UpdateFlowHosts(
    flow: UpdateFlowController,
    vibrate: () -> Unit = {},
    enabled: Boolean = true,
    onForcedRetry: () -> Unit = {}
) {
    val context = LocalContext.current

    // 「安装未知应用」授权页返回后回调（已授权则自动续装）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        flow.onPermissionResult()
    }

    // 全局弹窗闸门：同一时刻只允许一个会话型弹窗（处理进行中时由闸门整体抑制）
    if (!enabled) return

    // ── Go 运行在 Android 10+ 且本机已安装正常版：不可关闭，只能打开正常版 ──
    // 优先级最高：已装正常版就没必要再走「下载安装」，直接打开即可继续使用。
    if (flow.requireNormalOpen) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_use_normal_title), fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    stringResource(R.string.update_use_normal_body)
                )
            },
            confirmButton = {
                Button(onClick = {
                    vibrate()
                    LegacyApp.launchNormalIntent(context)?.let { intent ->
                        runCatching { context.startActivity(intent) }
                    }
                }) { Text(stringResource(R.string.update_open_normal)) }
            }
        )
        return
    }

    // ── Go 强制切换但拉取失败：不可关闭，只能重试（未更新前无法继续使用）──
    if (flow.forcedBlocked) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_must_update_title), fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    stringResource(R.string.update_forced_body)
                )
            },
            confirmButton = {
                Button(onClick = {
                    vibrate()
                    onForcedRetry()
                }) { Text(stringResource(R.string.update_retry)) }
            }
        )
        return
    }

    // ── 下载 / 解压进度（带「取消」按钮，立即停止并清理缓存）──
    val busy = flow.busy
    if (busy != null) {
        AlertDialog(
            onDismissRequest = {}, // 下载中不可通过外部点击关闭，需点「取消」
            title = { Text(stringResource(R.string.update_downloading_title)) },
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
                }) { Text(stringResource(R.string.update_cancel)) }
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
            title = { Text(stringResource(R.string.update_need_permission_title)) },
            text = {
                Text(
                    stringResource(R.string.update_need_permission_body)
                )
            },
            confirmButton = {
                Button(onClick = {
                    vibrate()
                    permissionLauncher.launch(AppUpdater.installPermissionIntent(context))
                }) { Text(stringResource(R.string.update_grant)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vibrate()
                    flow.cancelPermission()
                }) { Text(stringResource(R.string.update_cancel)) }
            }
        )
        return
    }

    // ── 结果 / 错误提示 ──
    val msg = flow.message
    if (msg != null) {
        AlertDialog(
            onDismissRequest = { flow.closeMessage() },
            title = { Text(stringResource(R.string.update_notice_title)) },
            text = { Text(msg) },
            confirmButton = {
                Button(onClick = {
                    vibrate()
                    flow.closeMessage()
                }) { Text(stringResource(R.string.update_got_it)) }
            }
        )
        return
    }

    // ── 更新说明子弹窗（markdown 排版；关闭后回到主弹窗）──
    val info = flow.info
    if (info != null && flow.showNotes) {
        AlertDialog(
            onDismissRequest = { flow.closeNotes() },
            title = { Text(stringResource(R.string.update_notes_title, info.version)) },
            text = {
                MarkdownBody(
                    markdown = notesForDisplay(
                        info.notes,
                        stringResource(R.string.update_notes_empty)
                    ),
                    modifier = Modifier.heightIn(max = 400.dp).padding(top = 4.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    vibrate()
                    flow.closeNotes()
                }) { Text(stringResource(R.string.update_back)) }
            }
        )
        return
    }

    // ── 「发现新版本」/「重新安装本版本」主弹窗 ──
    if (info != null) {
        val lanzou = info.lanzouUrl
        // Go 版在 Android 10+ 上强制切换：外部点击 / 返回键都关不掉，必须下载新版本
        val forced = flow.forcedSwitch
        AlertDialog(
            onDismissRequest = {
                // 点外部/返回视同「暂不更新」；强制切换模式下不响应
                if (!forced) {
                    vibrate()
                    flow.onLater()
                }
            },
            title = {
                Text(
                    when {
                        flow.switchToNormalMode -> stringResource(R.string.update_title_switch_normal)
                        flow.reinstallMode -> stringResource(R.string.update_title_reinstall)
                        else -> stringResource(R.string.update_title_new_version)
                    },
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.update_version_flavor, info.version, flow.flavorLabel),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (flow.switchToNormalMode) {
                        // 「切换到正常版」模式：写明旧兼容版（Go 版）在本机已不可继续使用
                        Text(
                            stringResource(R.string.update_switch_normal_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (flow.reinstallMode) {
                        // 「重新安装」模式：明确告知这是覆盖安装当前版本，用于验证下载→安装链路
                        Text(
                            stringResource(R.string.update_reinstall_body),
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
                            stringResource(R.string.update_view_notes),
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
                    ) { Text(if (lanzou.isNullOrEmpty()) stringResource(R.string.update_lanzou_unavailable) else stringResource(R.string.update_download_lanzou)) }
                    // 次选渠道：GitHub（内置下载，已按 normal/go 匹配对应 APK 资产）
                    OutlinedButton(
                        onClick = {
                            vibrate()
                            flow.downloadFromGithub()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) { Text(stringResource(R.string.update_download_github)) }
                    // 兜底渠道：用外置浏览器打开对应的 GitHub 链接自行下载
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            vibrate()
                            flow.openGithubInBrowser()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) { Text(stringResource(R.string.update_download_browser)) }
                    Spacer(Modifier.height(2.dp))
                    when {
                        // 强制切换：不提供任何关闭/跳过入口，必须更新后才能继续使用
                        forced -> {}
                        !flow.reinstallMode && !flow.switchToNormalMode -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        vibrate()
                                        flow.onLater()
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) { Text(stringResource(R.string.update_later)) }
                                OutlinedButton(
                                    onClick = {
                                        vibrate()
                                        flow.onSkipThisVersion()
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) { Text(stringResource(R.string.update_skip_version)) }
                            }
                        }
                        else -> {
                            // 「重新安装」/「切换到正常版」模式无需「跳过此版本」，只留一个关闭动作
                            FilledTonalButton(
                                onClick = {
                                    vibrate()
                                    flow.onLater()
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) { Text(if (flow.switchToNormalMode) stringResource(R.string.update_later_switch) else stringResource(R.string.update_later)) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}

/**
 * 用外置浏览器打开 URL。
 * 找不到可处理的浏览器/系统限制时静默忽略（更新弹窗本身已提供应用内下载渠道）。
 */
private fun openInBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
