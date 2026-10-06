package com.zsz.zlivephoto.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.CoreText

/**
 * 所有带「不再提示」的弹窗统一定义。
 * - 设置页「默认选项」板块据此枚举展示（分类 + 开关 + 查看弹窗）
 * - 各处弹窗复用同一 title/message，保证「查看对应弹窗」与真实弹窗文案一致
 * - prefsKey 为 SharedPreferences 中的持久化键（true = 不再提示）
 */
enum class ReminderKey(
    val prefsKey: String,
    private val categoryRes: Int,
    private val titleRes: Int,
    private val messageRes: Int
) {
    COMPOSE_VIDEO_OVER_3S(
        prefsKey = "compose_video_over3s_no_warn",
        categoryRes = R.string.conv_reminder_cat_compose,
        titleRes = R.string.conv_reminder_video_over3s_title,
        messageRes = R.string.conv_reminder_video_over3s_message
    ),
    LEGACY_GO_INSTALLED(
        prefsKey = "legacy_go_installed_no_warn",
        categoryRes = R.string.conv_reminder_cat_update,
        titleRes = R.string.conv_reminder_legacy_go_title,
        messageRes = R.string.conv_reminder_legacy_go_message
    ),
    REPLACE_MODE_RISK(
        prefsKey = "replace_mode_risk_no_warn",
        categoryRes = R.string.conv_reminder_cat_convert,
        titleRes = R.string.conv_reminder_replace_risk_title,
        messageRes = R.string.conv_reminder_replace_risk_message
    );

    /**
     * 分类 / 标题 / 正文按**当前语言即时解析**（不缓存），应用内切换语言后立刻生效。
     * 枚举常量本身拿不到 Context，故走 [CoreText]（内部用 FfmpegAddon.init 缓存的
     * applicationContext）；设置页等非 Compose 调用方仍直接读这三个属性。
     */
    val category: String get() = CoreText.of(categoryRes)
    val title: String get() = CoreText.of(titleRes)
    val message: String get() = CoreText.of(messageRes)
}

/**
 * 通用的「不再提示」信息弹窗（提示类，无业务副作用）。
 * - showDontRemind=true：显示「不再提示」复选框，确认时若勾选则写入抑制状态
 * - showDontRemind=false：仅展示标题+文案+[confirmLabel]，点确认即关闭（用于设置页「查看对应弹窗」）
 * - onDismiss 负责关闭弹窗；点确认前会先落盘抑制状态（如启用）
 * - onConfirm 非空时由它接管「确认」动作（调用方自行关闭弹窗），
 *   用于「确认后才执行某个有副作用的操作」的场景；取消/点外部仍走 onDismiss（即不执行）
 */
@Composable
fun ReminderInfoDialog(
    key: ReminderKey,
    showDontRemind: Boolean = true,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null
) {
    var dontRemind by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(key.title) },
        text = {
            Column {
                Text(key.message)
                if (showDontRemind) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { dontRemind = !dontRemind }
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Md3Checkbox(checked = dontRemind)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.conv_reminder_dont_remind))
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                if (showDontRemind && dontRemind) AppSettings.setReminderSuppressed(key, true)
                if (onConfirm != null) onConfirm() else onDismiss()
            }) { Text(confirmLabel ?: stringResource(R.string.conv_reminder_confirm_ok)) }
        }
    )
}

/**
 * 旧版本（Go 版）检测与卸载引导。
 *
 * - Go 版包名 [GO_PACKAGE] 已在 AndroidManifest 的 `<queries>` 中声明，
 *   因此 Android 11+ 的软件包可见性过滤不会挡住 `getPackageInfo`，
 *   无需申请 `QUERY_ALL_PACKAGES` 权限。
 * - 发起卸载请求需要 `REQUEST_DELETE_PACKAGES` 权限（普通权限，仅清单声明），
 *   与「访问应用列表」权限无关。
 */
object LegacyApp {
    /** 旧版本（Go 版）包名 */
    const val GO_PACKAGE = "com.zsz.zlivephoto.go"

    /** 正常版（完整版）包名 */
    const val NORMAL_PACKAGE = "com.zsz.zlivephoto"

    /**
     * 是否应切换到正常版：Go 版仅面向旧安卓（minSdk 23），
     * 系统版本达到正常版 minSdk（Android 10 / API 29）及以上时不再适用。
     */
    fun shouldSwitchToNormal(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** 旧版本（Go 版）是否已安装（按包名精确检测，不依赖「访问应用列表」权限） */
    fun isGoInstalled(context: Context): Boolean = isInstalled(context, GO_PACKAGE)

    /** 正常版（完整版）是否已安装（Go 版据此决定「直接打开」还是「去下载」） */
    fun isNormalInstalled(context: Context): Boolean = isInstalled(context, NORMAL_PACKAGE)

    private fun isInstalled(context: Context, pkg: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(pkg, 0)
    }.getOrNull() != null

    /** 发起卸载旧版本的系统卸载请求 */
    fun uninstallIntent(context: Context): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$GO_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 打开正常版的启动意图（未安装或无可启动入口时返回 null） */
    fun launchNormalIntent(context: Context): Intent? = runCatching {
        context.packageManager.getLaunchIntentForPackage(NORMAL_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }.getOrNull()
}

/** 旧版本卸载提示的状态控制器（普通版启动时检测到旧 Go 版已安装时使用） */
class LegacyUninstallFlowController {
    var visible by mutableStateOf(false)
        private set

    fun show() {
        visible = true
    }

    fun dismiss() {
        visible = false
    }

    fun onDontRemind() {
        AppSettings.setReminderSuppressed(ReminderKey.LEGACY_GO_INSTALLED, true)
        visible = false
    }

    fun onUninstall(context: Context) {
        visible = false
        runCatching { context.startActivity(LegacyApp.uninstallIntent(context)) }
    }
}

@Composable
fun rememberLegacyUninstallFlow(): LegacyUninstallFlowController =
    remember { LegacyUninstallFlowController() }

/** 渲染旧版本卸载提示弹窗（支持「不再提示」「卸载」两个动作） */
@Composable
fun LegacyUninstallFlowHosts(
    flow: LegacyUninstallFlowController,
    vibrate: () -> Unit = {},
    enabled: Boolean = true
) {
    // 全局弹窗闸门：同一时刻只允许一个会话型弹窗（处理进行中时由闸门整体抑制）
    if (!enabled || !flow.visible) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { flow.dismiss() },
        title = { Text(ReminderKey.LEGACY_GO_INSTALLED.title) },
        text = { Text(ReminderKey.LEGACY_GO_INSTALLED.message) },
        confirmButton = {
            Button(onClick = { vibrate(); flow.onUninstall(context) }) { Text(stringResource(R.string.conv_reminder_uninstall)) }
        },
        dismissButton = {
            TextButton(onClick = { vibrate(); flow.onDontRemind() }) { Text(stringResource(R.string.conv_reminder_dont_remind)) }
        }
    )
}

