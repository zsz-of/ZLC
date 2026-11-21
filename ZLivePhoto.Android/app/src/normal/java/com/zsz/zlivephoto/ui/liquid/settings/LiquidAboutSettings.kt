package com.zsz.zlivephoto.ui.liquid.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.zsz.zlivephoto.BuildConfig
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.UpdateCheckResult
import com.zsz.zlivephoto.core.UpdateChecker
import com.zsz.zlivephoto.core.UpdateInfo
import com.zsz.zlivephoto.ui.rememberHapticFeedback
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import kotlinx.coroutines.launch

private const val GITHUB_REPO_URL = "https://github.com/zsz-of/ZLC"

/**
 * 关于二级页：顶栏 + 关于分组（应用信息 / 更新 / 鸣谢 / 开源）。
 * 赞助已提到设置一级页最顶端，这里不再重复。
 */
@Composable
internal fun AboutSettingsPage(
    onRequestUpdate: () -> Unit,
    onBack: () -> Unit,
    /** 非空：顶栏由设置宿主统一绘制（二级页切换时返回钮不动） */
    hostContentPadding: PaddingValues? = null
) {
    SettingsPage(
        title = stringResource(R.string.settings_section_about),
        onBack = onBack,
        hostContentPadding = hostContentPadding
    ) {
        AboutSettingsSection(onRequestUpdate = onRequestUpdate)
    }
}

/**
 * 关于：检查更新 / 重新安装 / 版本 / 鸣谢 / 开源与授权全部合并为一个分组，
 * 组内条目之间由 GlasmioListRow 自带的分隔线区分，不再拆成多个带标题的分组。
 *
 * @param onRequestUpdate 请求宿主在应用根层级展示「检查更新 / 重新安装本版本」弹窗
 */
@Composable
internal fun AboutSettingsSection(onRequestUpdate: () -> Unit = {}) {
    val haptic = rememberHapticFeedback()
    val context = LocalContext.current

    // 手动检查更新：每次点击触发一次网络检查（正在检查时忽略重复点击）
    var checking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    // 最近一次拉取到的「最新 release」
    var latestInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showDialogTest by remember { mutableStateOf(false) }
    val checkScope = rememberCoroutineScope()

    /** 拉取最新版并刷新状态；[presentIfNewer] 为 true（用户主动点「检查更新」）时发现新版直接弹窗 */
    suspend fun performCheck(presentIfNewer: Boolean) {
        val info = UpdateChecker.fetchLatest()
        if (info == null) {
            updateResult = UpdateCheckResult.NetworkError
        } else {
            latestInfo = info
            val newer = UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)
            updateResult = if (newer) UpdateCheckResult.Update(info) else UpdateCheckResult.UpToDate
            // 弹窗由宿主在应用根层级渲染，设置页只负责发起请求
            if (newer && presentIfNewer) onRequestUpdate()
        }
    }

    // 进入关于区先静默拉一次最新版本信息（不自动弹窗）
    LaunchedEffect(Unit) {
        checking = true
        performCheck(presentIfNewer = false)
        checking = false
    }

    SettingsSection {
        val statusText = when (val r = updateResult) {
            null -> if (checking) stringResource(R.string.settings_checking) else ""
            is UpdateCheckResult.Update -> stringResource(R.string.settings_update_available, r.info.version)
            UpdateCheckResult.UpToDate -> stringResource(R.string.settings_up_to_date)
            UpdateCheckResult.NetworkError -> stringResource(R.string.settings_network_error)
        }
        GlasmioListRow(
            title = stringResource(R.string.settings_check_update_title),
            detail = statusText.ifEmpty { null },
            onClick = {
                if (!checking) {
                    haptic.click()
                    checking = true
                    checkScope.launch {
                        performCheck(presentIfNewer = true)
                        checking = false
                    }
                }
            }
        )

        // 重新安装本版本：仅在「当前已是最新版」时可用；弹窗交由宿主展示
        val info = latestInfo
        val upToDate = info != null &&
            !UpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)
        GlasmioListRow(
            title = stringResource(R.string.settings_reinstall_title),
            onClick = {
                if (upToDate && !checking) {
                    haptic.click()
                    onRequestUpdate()
                }
            },
            enabled = upToDate
        )

        GlasmioListRow(
            title = stringResource(R.string.settings_version_title),
            detail = stringResource(R.string.settings_version_value, BuildConfig.VERSION_NAME)
        )

        GlasmioListRow(
            title = "@毛血旺o",
            onClick = {
                haptic.click()
                openInBrowser(context, "https://www.coolapk.com/u/2910471")
            }
        )

        // 纯专有名词，保持硬编码
        GlasmioListRow(
            title = "Trae · GML · DeepSeek · Kimi"
        )

        GlasmioListRow(
            title = stringResource(R.string.settings_github_title),
            onClick = {
                haptic.click()
                openInBrowser(context, GITHUB_REPO_URL)
            }
        )

        GlasmioListRow(
            title = stringResource(R.string.settings_license_title),
            detail = "GPL-3.0"
        )

        // 弹窗测试页：只做外观与交互验证，无真实副作用
        GlasmioListRow(
            title = stringResource(R.string.settings_dialog_test),
            onClick = {
                haptic.click()
                showDialogTest = true
            }
        )
    }

    LiquidDialogTestPage(
        visible = showDialogTest,
        onDismiss = { showDialogTest = false }
    )
}

/** 跳系统浏览器打开链接。 */
private fun openInBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {}
}
