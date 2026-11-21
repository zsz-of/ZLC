package com.zsz.zlivephoto.ui.liquid

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.AppUpdater
import com.zsz.zlivephoto.ui.AppSettings
import com.zsz.zlivephoto.ui.LegacyApp
import com.zsz.zlivephoto.ui.LegacyUninstallFlowController
import com.zsz.zlivephoto.ui.MarkdownBody
import com.zsz.zlivephoto.ui.ReminderKey
import com.zsz.zlivephoto.ui.UpdateFlowController
import com.zsz.zlivephoto.ui.liquid.common.LiquidIndeterminateProgressBar
import com.zsz.zlivephoto.ui.liquid.common.LiquidProgressBar
import com.zsz.zlivephoto.ui.rememberHapticFeedback
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonLayout
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioAlertTextField
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioSwitch

/** 从更新说明中隐藏「[蓝奏云-xx]: url」这类发布元数据行，只给用户看可读内容 */
private fun notesForDisplay(notes: String?, emptyText: String): String {
    if (notes.isNullOrBlank()) return emptyText
    val kept = notes.lineSequence()
        .filterNot { it.trimStart().startsWith("[蓝奏云-") }
        .joinToString("\n")
        .trim()
    return kept.ifEmpty { emptyText }
}

/**
 * 记住 [value] 的最后一个值：[keep] 为 false（弹窗退场中）时返回退出前的值。
 * 退场期间源状态往往已被清空（dismissAll / closeMessage 等），
 * 但内容仍需渲染完淡出动画。
 */
@Composable
private fun <T> rememberLast(value: T, keep: Boolean): T {
    val last = remember { mutableStateOf(value) }
    // 只在「保持」期间记录：退场瞬间 keep=false 时源状态已被清空，
    // 若此时仍写 last 会把文案清掉，导致淡出过程闪空。
    LaunchedEffect(value, keep) { if (keep) last.value = value }
    return if (keep) value else last.value
}

/**
 * 液态玻璃版更新弹窗宿主：状态机与按钮行为与 [com.zsz.zlivephoto.ui.UpdateFlowHosts] 完全一致，
 * 仅 UI 层改用 Glasmio 玻璃弹窗。
 *
 * 弹窗互斥优先级：
 * requireNormalOpen > forcedBlocked > busy > permissionApk > message > notes > info。
 */
@Composable
internal fun LiquidUpdateFlowHosts(
    flow: UpdateFlowController,
    vibrate: () -> Unit = {},
    enabled: Boolean = true,
    onForcedRetry: () -> Unit = {}
) {
    val context = LocalContext.current
    // 弹窗正文最大高度：取屏幕高度的一半。AlertSurface 自身没有高度上限，
    // 正文必须自己夹紧，否则横屏（可用高约 390dp）时标题与按钮会被推出屏幕。
    val maxContentHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f

    // 「安装未知应用」授权页返回后回调（已授权则自动续装）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        flow.onPermissionResult()
    }

    // ── 互斥可见性（全局弹窗闸门：处理进行中由 enabled 整体抑制）──
    val showRequireNormalOpen = enabled && flow.requireNormalOpen
    val showForcedBlocked = enabled && !showRequireNormalOpen && flow.forcedBlocked
    val showBusy = enabled && !showRequireNormalOpen && !showForcedBlocked && flow.busy != null
    val showPermission = enabled && !showRequireNormalOpen && !showForcedBlocked && !showBusy &&
        flow.permissionApk != null
    val showMessage = enabled && !showRequireNormalOpen && !showForcedBlocked && !showBusy &&
        !showPermission && flow.message != null
    val showNotes = enabled && !showRequireNormalOpen && !showForcedBlocked && !showBusy &&
        !showPermission && !showMessage && flow.info != null && flow.showNotes
    val showInfo = enabled && !showRequireNormalOpen && !showForcedBlocked && !showBusy &&
        !showPermission && !showMessage && !showNotes && flow.info != null

    // 退场动画期间对应状态可能已被 dismissAll 清空：记住最后一个值继续渲染
    val busy = rememberLast(flow.busy, showBusy)
    val msg = rememberLast(flow.message, showMessage)
    val info = rememberLast(flow.info, showInfo || showNotes)
    val switchToNormal = rememberLast(flow.switchToNormalMode, showInfo)
    val reinstall = rememberLast(flow.reinstallMode, showInfo)

    // ── Go 运行在 Android 10+ 且本机已安装正常版：不可关闭，只能打开正常版 ──
    GlasmioAlertDialog(
        visible = showRequireNormalOpen,
        onDismissRequest = {},
        title = stringResource(R.string.update_use_normal_title),
        message = stringResource(R.string.update_use_normal_body),
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.update_open_normal),
                onClick = {
                    vibrate()
                    LegacyApp.launchNormalIntent(context)?.let { intent ->
                        runCatching { context.startActivity(intent) }
                    }
                }
            )
        )
    )

    // ── Go 强制切换但拉取失败：不可关闭，只能重试 ──
    GlasmioAlertDialog(
        visible = showForcedBlocked,
        onDismissRequest = {},
        title = stringResource(R.string.update_must_update_title),
        message = stringResource(R.string.update_forced_body),
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.update_retry),
                onClick = {
                    vibrate()
                    onForcedRetry()
                }
            )
        )
    )

    // ── 下载 / 解压进度（带「取消」按钮，立即停止并清理缓存）──
    GlasmioAlertDialog(
        visible = showBusy,
        onDismissRequest = {}, // 下载中不可通过外部点击关闭，需点「取消」
        title = stringResource(R.string.update_downloading_title),
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.update_cancel),
                role = GlasmioAlertButtonRole.Cancel,
                onClick = {
                    vibrate()
                    flow.cancelDownload()
                }
            )
        )
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = maxContentHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(busy?.label ?: "", style = MaterialTheme.typography.bodyMedium)
            val fraction = busy?.fraction
            if (fraction != null) {
                LiquidProgressBar(progress = fraction, modifier = Modifier.fillMaxWidth())
            } else {
                LiquidIndeterminateProgressBar(Modifier.fillMaxWidth())
            }
            // 与底部「取消」按钮拉开距离
            Spacer(Modifier.height(14.dp))
        }
    }

    // ── 安装权限引导（APK 已下载完，去授权后返回自动续装）──
    GlasmioAlertDialog(
        visible = showPermission,
        onDismissRequest = {}, // 必须明确选择，防止误关导致不知道安装包去向
        title = stringResource(R.string.update_need_permission_title),
        message = stringResource(R.string.update_need_permission_body),
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.update_cancel),
                role = GlasmioAlertButtonRole.Cancel,
                onClick = {
                    vibrate()
                    flow.cancelPermission()
                }
            ),
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.update_grant),
                onClick = {
                    vibrate()
                    permissionLauncher.launch(AppUpdater.installPermissionIntent(context))
                }
            )
        )
    )

    // ── 结果 / 错误提示 ──
    GlasmioAlertDialog(
        visible = showMessage,
        onDismissRequest = { flow.closeMessage() },
        title = stringResource(R.string.update_notice_title),
        message = msg ?: "",
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.update_got_it),
                onClick = {
                    vibrate()
                    flow.closeMessage()
                }
            )
        )
    )

    // ── 更新说明子弹窗（markdown 排版；关闭后回到主弹窗）──
    GlasmioAlertDialog(
        visible = showNotes,
        onDismissRequest = { flow.closeNotes() },
        title = stringResource(R.string.update_notes_title, info?.version ?: ""),
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.update_back),
                onClick = {
                    vibrate()
                    flow.closeNotes()
                }
            )
        )
    ) {
        MarkdownBody(
            markdown = notesForDisplay(
                info?.notes.orEmpty(),
                stringResource(R.string.update_notes_empty)
            ),
            // 不再加上下渐隐：渐隐会盖住首尾文字（用户反馈顶部/底部文字被挡掉一部分），
            // 改为纯滚动裁切。
            modifier = Modifier
                .heightIn(max = maxContentHeight)
                .padding(top = 4.dp)
        )
    }

    // ── 「发现新版本」/「重新安装本版本」/「切换到正常版」主弹窗 ──
    val lanzou = info?.lanzouUrl
    // Go 版在 Android 10+ 上强制切换：外部点击 / 返回键都关不掉，必须下载新版本
    val forced = flow.forcedSwitch
    // 「暂不更新 / 跳过此版本」与下载渠道同属正文，用同样的液态玻璃按钮呈现；
    // 系统 Alert 的 Cancel 按钮是纯色胶囊，放进玻璃弹窗里显得突兀。
    val showLater = !forced
    val showSkipVersion = !forced && !reinstall && !switchToNormal
    GlasmioAlertDialog(
        buttonLayout = GlasmioAlertButtonLayout.Stacked,
        visible = showInfo,
        onDismissRequest = {
            // 点外部/返回视同「暂不更新」；强制切换模式下不响应
            if (!forced) {
                vibrate()
                flow.onLater()
            }
        },
        title = when {
            switchToNormal -> stringResource(R.string.update_title_switch_normal)
            reinstall -> stringResource(R.string.update_title_reinstall)
            else -> stringResource(R.string.update_title_new_version)
        },
        // 所有关闭动作都渲染在正文尾部（玻璃按钮），弹窗不再使用纯色 Cancel 胶囊
        buttons = emptyList()
    ) {
        val glassColors = LocalGlassColors.current
        Column(
            modifier = Modifier
                .heightIn(max = maxContentHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.update_version_flavor, info?.version ?: "", flow.flavorLabel),
                style = MaterialTheme.typography.titleMedium,
                color = glassColors.accent
            )
            if (switchToNormal) {
                // 「切换到正常版」模式：写明旧兼容版（Go 版）在本机已不可继续使用
                Text(
                    stringResource(R.string.update_switch_normal_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = glassColors.secondaryContent
                )
            }
            if (reinstall) {
                // 「重新安装」模式：明确告知这是覆盖安装当前版本
                Text(
                    stringResource(R.string.update_reinstall_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = glassColors.secondaryContent
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
                    color = glassColors.accent
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    "›",
                    style = MaterialTheme.typography.labelLarge,
                    color = glassColors.accent
                )
            }
            // 首选渠道：蓝奏云（应用内解析直链后直接下载安装）
            GlasmioButton(
                onClick = {
                    vibrate()
                    flow.downloadFromLanzou()
                },
                enabled = !lanzou.isNullOrEmpty(),
                emphasis = GlassEmphasis.Prominent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (lanzou.isNullOrEmpty()) stringResource(R.string.update_lanzou_unavailable)
                    else stringResource(R.string.update_download_lanzou),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            // 次选渠道：GitHub（内置下载，已按 normal/go 匹配对应 APK 资产）
            GlasmioButton(
                onClick = {
                    vibrate()
                    flow.downloadFromGithub()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.update_download_github),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            // 兜底渠道：用外置浏览器打开对应的 GitHub 链接自行下载
            GlasmioButton(
                onClick = {
                    vibrate()
                    flow.openGithubInBrowser()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.update_download_browser),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (showLater) {
                // 与下载渠道拉开距离，避免两个胶囊紧贴
                Spacer(Modifier.height(12.dp))
                GlasmioButton(
                    onClick = {
                        vibrate()
                        flow.onLater()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (switchToNormal) stringResource(R.string.update_later_switch)
                        else stringResource(R.string.update_later),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                if (showSkipVersion) {
                    GlasmioButton(
                        onClick = {
                            vibrate()
                            flow.onSkipThisVersion()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.update_skip_version),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * 液态玻璃版旧版本（Go 版）卸载提示：支持「不再提醒」「卸载」两个动作。
 * 状态机复用 [LegacyUninstallFlowController]，仅 UI 改为 Glasmio 玻璃弹窗。
 */
@Composable
fun LiquidLegacyUninstallFlowHosts(
    flow: LegacyUninstallFlowController,
    vibrate: () -> Unit = {},
    enabled: Boolean = true
) {
    val visible = enabled && flow.visible
    val context = LocalContext.current
    GlasmioAlertDialog(
        visible = visible,
        onDismissRequest = { flow.dismiss() },
        title = ReminderKey.LEGACY_GO_INSTALLED.title,
        message = ReminderKey.LEGACY_GO_INSTALLED.message,
        // 两个按钮都是长标签（不再提醒 / 卸载）：横排会挤压换行，改为纵向堆叠
        buttonLayout = GlasmioAlertButtonLayout.Stacked,
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.conv_reminder_dont_remind),
                role = GlasmioAlertButtonRole.Cancel,
                onClick = {
                    vibrate()
                    flow.onDontRemind()
                }
            ),
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.conv_reminder_uninstall),
                onClick = {
                    vibrate()
                    flow.onUninstall(context)
                }
            )
        )
    )
}

/**
 * 液态玻璃版「覆盖原图 / 删除原图」风险提示。
 *
 * 开始处理前每次都提示，但不再要求输入文字：文案按当前输出方式简短说明
 * （继续处理将替换原图 / 继续处理将删除原图）。
 */
@Composable
fun LiquidRiskAckDialog(
    visible: Boolean,
    replaceOriginal: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    GlasmioAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.conv_risk_ack_title),
        message = stringResource(
            if (replaceOriginal) R.string.conv_risk_replace_message
            else R.string.conv_risk_delete_message
        ),
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.conv_risk_ack_cancel),
                role = GlasmioAlertButtonRole.Cancel,
                onClick = onDismiss
            ),
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.conv_risk_ack_confirm),
                onClick = onConfirm
            )
        )
    )
}

/**
 * 液态玻璃版「不再提示」信息弹窗（normal flavor）。
 *
 * 与 MD3 版 [com.zsz.zlivephoto.ui.ReminderInfoDialog] 语义一致：
 * - [showDontRemind] 为 true 时显示「不再提示」开关，确认时勾选则写入抑制状态；
 * - [onConfirm] 非空时由它接管确认动作（调用方自行关闭弹窗）；
 * - 取消 / 点外部仍走 [onDismiss]；[visible] 为 false 时播放退场动画后才退出组合。
 *
 * 在 liquid 包内单独实现的原因：Glasmio 依赖仅 normal flavor 提供，
 * 共享 ui 包中的同名弹窗必须保留 MD3 组件供 go flavor 编译。
 */
@Composable
fun ReminderInfoDialog(
    key: ReminderKey,
    showDontRemind: Boolean = true,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    visible: Boolean = true
) {
    // 每次重新可见都重置勾选：避免下次弹窗仍带「不再提示」。
    // 不能用 remember(visible)：退场动画期间 visible=false 会让 key 变化立刻复位，
    // 结果是开关在淡出时突然弹回未选中（首帧闪一下）。
    var dontRemind by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) dontRemind = false }
    val haptic = rememberHapticFeedback()
    GlasmioAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = key.title,
        message = key.message,
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = confirmLabel ?: stringResource(R.string.conv_reminder_confirm_ok),
                onClick = {
                    haptic.click()
                    if (showDontRemind && dontRemind) {
                        AppSettings.setReminderSuppressed(key, true)
                    }
                    if (onConfirm != null) onConfirm() else onDismiss()
                }
            )
        )
    ) {
        if (showDontRemind) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { dontRemind = !dontRemind }
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                GlasmioSwitch(
                    checked = dontRemind,
                    onCheckedChange = {
                        haptic.click()
                        dontRemind = it
                    }
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.conv_reminder_dont_remind))
            }
        }
    }
}
