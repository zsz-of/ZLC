package com.zsz.zlivephoto.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    SYSTEM_PICKER(
        prefsKey = "sys_picker_no_warn",
        category = "导入",
        title = "使用系统选择器？",
        message = "通过系统选择器选择的照片可能丢失元数据（位置、镜头参数等），" +
            "且无法识别部分双文件动态照片。\n\n建议优先使用内置选择器。"
    ),
    COMPOSE_VIDEO_OVER_3S(
        prefsKey = "compose_video_over3s_no_warn",
        category = "合成",
        title = "视频超过 3 秒",
        message = "所选视频时长超过 3 秒，合成后的动态照片可能存在兼容性问题：\n\n" +
            "• 可能无法正常播放\n" +
            "• 可能无法被系统相册识别\n" +
            "• 部分机型可能无法识别该动态照片"
    ),
    COMPOSE_SYSTEM_PICKER(
        prefsKey = "compose_sys_picker_blocked_no_warn",
        category = "合成",
        title = "合成模式不支持系统选择器",
        message = "合成动态照片需要同时选择照片和视频，并按序号一一配对，" +
            "存在顺序要求，因此不能使用系统选择器。\n\n" +
            "请使用内置选择器分别选择照片与视频。"
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

