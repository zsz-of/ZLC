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
import androidx.compose.ui.unit.dp

/**
 * 所有带「不再提示」的弹窗统一定义。
 * - 设置页「默认选项」板块据此枚举展示（分类 + 开关 + 查看弹窗）
 * - 各处弹窗复用同一 title/message，保证「查看对应弹窗」与真实弹窗文案一致
 * - prefsKey 为 SharedPreferences 中的持久化键（true = 不再提示）
 */
enum class ReminderKey(
    val prefsKey: String,
    val category: String,
    val title: String,
    val message: String
) {
    COMPOSE_VIDEO_OVER_3S(
        prefsKey = "compose_video_over3s_no_warn",
        category = "合成",
        title = "视频超过 3 秒",
        message = "所选视频时长超过 3 秒，合成后的动态照片可能存在兼容性问题：\n\n" +
            "• 可能无法正常播放\n" +
            "• 可能无法被系统相册识别\n" +
            "• 部分机型可能无法识别该动态照片"
    ),
    LEGACY_GO_INSTALLED(
        prefsKey = "legacy_go_installed_no_warn",
        category = "更新",
        title = "检测到旧版本",
        message = "本设备仍安装了旧版本（Go 版）应用，该旧版本在本设备上已不可用，可卸载以释放空间。"
    )
}

/**
 * 通用的「不再提示」信息弹窗（提示类，无业务副作用）。
 * - showDontRemind=true：显示「不再提示」复选框，确认时若勾选则写入抑制状态
 * - showDontRemind=false：仅展示标题+文案+「知道了」，点确认即关闭（用于设置页「查看对应弹窗」）
 * - onDismiss 统一负责关闭弹窗；点确认前会先落盘抑制状态（如启用）
 */
@Composable
fun ReminderInfoDialog(
    key: ReminderKey,
    showDontRemind: Boolean = true,
    onDismiss: () -> Unit
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
                        Text("不再提示")
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                if (showDontRemind && dontRemind) AppSettings.setReminderSuppressed(key, true)
                onDismiss()
            }) { Text("知道了") }
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

    /**
     * 是否应切换到正常版：Go 版仅面向旧安卓（minSdk 23），
     * 系统版本达到正常版 minSdk（Android 10 / API 29）及以上时不再适用。
     */
    fun shouldSwitchToNormal(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** 旧版本（Go 版）是否已安装（按包名精确检测，不依赖「访问应用列表」权限） */
    fun isGoInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(GO_PACKAGE, 0)
    }.getOrNull() != null

    /** 发起卸载旧版本的系统卸载请求 */
    fun uninstallIntent(context: Context): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$GO_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
fun LegacyUninstallFlowHosts(flow: LegacyUninstallFlowController, vibrate: () -> Unit = {}) {
    if (!flow.visible) return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { flow.dismiss() },
        title = { Text(ReminderKey.LEGACY_GO_INSTALLED.title) },
        text = { Text(ReminderKey.LEGACY_GO_INSTALLED.message) },
        confirmButton = {
            Button(onClick = { vibrate(); flow.onUninstall(context) }) { Text("卸载") }
        },
        dismissButton = {
            TextButton(onClick = { vibrate(); flow.onDontRemind() }) { Text("不再提示") }
        }
    )
}

