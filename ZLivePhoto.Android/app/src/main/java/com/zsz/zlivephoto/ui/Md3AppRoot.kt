package com.zsz.zlivephoto.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.BuildConfig
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.UpdateChecker
import com.zsz.zlivephoto.core.UpdateCheckResult
import com.zsz.zlivephoto.flavor.FlavorCapabilities
import com.zsz.zlivephoto.ui.glass.GlassCheckbox
import com.zsz.zlivephoto.ui.glass.GlassRoot
import com.zsz.zlivephoto.ui.glass.LiquidDialog
import com.zsz.zlivephoto.ui.glass.LocalGlassBackdrop
import com.zsz.zlivephoto.ui.glass.LocalPageBackdropPublisher
import com.zsz.zlivephoto.ui.glass.PageBackdropPublisher
import com.zsz.zlivephoto.ui.picker.ImportMode
import com.zsz.zlivephoto.ui.picker.PhotoPickerScreen
import kotlinx.coroutines.CancellationException

/**
 * 会话型弹窗占用者（全局弹窗闸门）：同一时刻只允许一个弹窗占用屏幕。
 * 处理（转换 / 导入）进行中一律不占用，处理队列结束后再按此优先级依次弹出。
 */
private enum class DialogOwner { NONE, PERMISSION, UPDATE, ALL_FILES, LEGACY }

/**
 * Material 3 版应用外壳（go 轻量版，以及 normal 在设备不支持液态玻璃时的回退）。
 *
 * 所有业务状态与动作都经由 [AppHost]，本文件只负责页面路由、过渡动画与
 * 应用级弹窗编排；玻璃组件在 `AppSettings.glassActive == false` 时自动退化为 MD3。
 */
@Composable
fun Md3AppRoot(host: AppHost) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ZLivePhotoTheme(darkTheme = host.isDarkTheme) {
        // 系统栏图标随主题切换（浅色模式必须是深色图标）
        SystemBarsAppearance(isDark = host.isDarkTheme)
        // 上次崩溃 / 运行期错误：统一弹「提交错误报告」
        PendingErrorReportWatcher(context)
        Md3ErrorReportDialog()
        // 全局触觉反馈：弹窗按钮等无独立交互源的控件统一使用
        val haptic = rememberHapticFeedback()
        host.attachHaptics(haptic)
        // 主列表滚动状态（LazyColumn 复位/保持由 MainScreen 使用）
        val listState = rememberLazyListState()
        // 深色模式三选变化：同步 isDarkTheme 驱动主题与选择器即时切换
        LaunchedEffect(AppSettings.themeMode) {
            host.refreshDarkTheme()
        }
        // 处理过程中吞掉系统返回键（预测式返回下同样生效）
        BackHandler(enabled = host.isConverting) { /* 处理中不响应返回 */ }

        LaunchedEffect(Unit) {
            // 已有媒体读取权限但缺「所有文件访问」（R+）时，弹一次引导（升级用户路径）
            host.maybePromptAllFilesAccess()
        }

        // 更新弹窗状态（发现新版本 / 说明子弹窗 / 下载进度 / 安装权限引导）
        val updateFlow = rememberUpdateFlow()

        // 旧版本（Go 版）卸载提示状态（普通版启动时检测）
        val legacyFlow = rememberLegacyUninstallFlow()

        // 页面玻璃采样层发布者：各页面发布自己的 pageBackdrop，
        // 应用级弹窗（权限引导 / 更新提示 / 文件名冲突 / 旧版本卸载提示）
        // 据此采样「根渐变 + 当前页面」——弹窗与页面同窗口，坐标天然
        // 对正、模糊实时（用户反馈 #5/#6），且复用页面已有录制零开销
        val pageBackdropPublisher = remember { PageBackdropPublisher() }

        // 启动时自动检查更新。
        // 优先级：Go 版提醒（可继续使用）→ 软件更新 → 旧版本卸载提示。
        // startupCheckKey 用于「必须更新」弹窗上的「重试」重新发起拉取。
        var startupCheckKey by remember { mutableStateOf(0) }
        // Go 版在 Android 12+ 只做一次启动提醒，不再拦截使用
        var showGoReminder by remember {
            mutableStateOf(FlavorCapabilities.isGo && LegacyApp.suggestsNormalEdition())
        }

        LaunchedEffect(startupCheckKey) {
            var updatePrompted = false
            if (AppSettings.checkUpdateOnStartup) {
                val r = UpdateChecker.check(BuildConfig.VERSION_NAME)
                if (r is UpdateCheckResult.Update &&
                    !AppSettings.isVersionSkipped(r.info.version)
                ) {
                    updateFlow.present(r.info)
                    updatePrompted = true
                }
            }
            // 旧版本卸载提示（仅普通版；无更新提示时）
            if (!updatePrompted && !FlavorCapabilities.isGo &&
                !AppSettings.isReminderSuppressed(ReminderKey.LEGACY_GO_INSTALLED) &&
                LegacyApp.isGoInstalled(context)
            ) {
                legacyFlow.show()
            }
        }

        // ── 全局会话型弹窗闸门 ──
        // 权限请求 / 应用更新 / 「所有文件访问」推荐 / 旧版本提示
        // 同一时刻只允许弹出一个：处理（转换 / 导入）进行中整体抑制，处理队列
        // 完成后才弹出；当前占用者关闭后自动轮到下一个（按下列优先级）。
        val sessionDialogOwner = when {
            host.isConverting || host.isImporting -> DialogOwner.NONE
            host.showPermissionDialog || host.showSettingsDialog -> DialogOwner.PERMISSION
            updateFlow.wantsDialog -> DialogOwner.UPDATE
            host.showAllFilesDialog -> DialogOwner.ALL_FILES
            legacyFlow.visible -> DialogOwner.LEGACY
            else -> DialogOwner.NONE
        }

        // 预览式返回：选择器 / 设置页手势进度驱动内容缩小右移（顶层稳定注册，
        // 不受 AnimatedContent 过渡重组影响；提交后回到主页）
        val backAnim = remember { Animatable(0f) }
        // 设置页是否停在二级页（由 SettingsScreen 回传）：二级页期间关闭本层的
        // 预测式返回动画，把返回完全交给设置页自己的层级（二级 → 设置根 → 主页）
        var settingsInSubpage by remember { mutableStateOf(false) }
        // 离开设置页时清掉标记（设置页被销毁后不会再有回传）
        LaunchedEffect(host.screen) {
            if (host.screen != AppScreen.Settings) settingsInSubpage = false
        }
        // 预览式返回：go 轻量版不启用（进一步简化）
        PredictiveBackHandler(enabled = !FlavorCapabilities.isGo &&
            (host.screen == AppScreen.Picker ||
                (host.screen == AppScreen.Settings && !settingsInSubpage))) { flow ->
            try {
                flow.collect { backAnim.snapTo(it.progress) }
                host.screen = AppScreen.Main
                backAnim.snapTo(0f)
            } catch (_: CancellationException) {
                backAnim.animateTo(0f, tween(200))
            }
        }
        // 系统返回键：选择器 / 设置页返回上一级（主页），不退出应用；
        // 处理中由上方 BackHandler(enabled = isConverting) 吞掉，二者不冲突。
        BackHandler(enabled = host.screen == AppScreen.Picker ||
            (host.screen == AppScreen.Settings && !settingsInSubpage)) {
            host.screen = AppScreen.Main
        }

        // 进入/退出内置选择器的过渡动画：选择器从右侧整屏滑入 + 淡入，
        // 主页向左小幅滑出让位；返回时反向。设置页用淡入淡出 + 轻微缩放过渡。
        // RTL：推拉方向必须跟随布局方向，否则 RTL 下「前进」会从反方向滑入。
        val rtlDir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1

        // 液态玻璃根容器：铺一条主题色渐变作为采样源，并向子树提供玻璃 CompositionLocal。
        GlassRoot {
            CompositionLocalProvider(LocalPageBackdropPublisher provides pageBackdropPublisher) {
                AnimatedContent(
                    targetState = host.screen,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (AppSettings.glassActive) Modifier
                            else Modifier.background(MaterialTheme.colorScheme.background)
                        )
                        .graphicsLayer {
                            val p = backAnim.value
                            if (p > 0f) {
                                scaleX = 1f - p * 0.06f
                                scaleY = 1f - p * 0.06f
                                translationX = p * size.width * 0.18f * rtlDir
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            }
                        },
                    transitionSpec = {
                        val slideIn = initialState == AppScreen.Main &&
                            (targetState == AppScreen.Picker || targetState == AppScreen.Settings)
                        val slideOut = (initialState == AppScreen.Picker || initialState == AppScreen.Settings) &&
                            targetState == AppScreen.Main
                        when {
                            slideIn ->
                                (slideInHorizontally(tween(450, easing = FancyEasing)) { it * rtlDir } +
                                        fadeIn(tween(300, 100))) togetherWith
                                        (slideOutHorizontally(tween(450, easing = FancyEasing)) { -it * rtlDir } +
                                                fadeOut(tween(300)))
                            slideOut ->
                                (slideInHorizontally(tween(450, easing = FancyEasing)) { -it * rtlDir } +
                                        fadeIn(tween(300, 100))) togetherWith
                                        (slideOutHorizontally(tween(450, easing = FancyEasing)) { it * rtlDir } +
                                                fadeOut(tween(300)))
                            else ->
                                (fadeIn(tween(250)) +
                                        scaleIn(initialScale = 0.97f, animationSpec = tween(300, easing = FancyEasing))) togetherWith
                                        (fadeOut(tween(180)))
                        }
                    },
                    label = "screenTransition"
                ) { page ->
                    when (page) {
                        AppScreen.Picker -> PhotoPickerScreen(
                            albums = host.albums,
                            scanner = host.scanner,
                            scannerScope = scope,
                            isDarkTheme = host.isDarkTheme,
                            importMode = host.importMode,
                            outputFormat = host.selectedFormat,
                            onOutputFormatChange = { host.setOutputFormat(it) },
                            onBack = { host.screen = AppScreen.Main },
                            onConfirm = { items, coverSec ->
                                host.screen = AppScreen.Main
                                host.importPickedItems(items, coverSec)
                            }
                        )
                        AppScreen.Settings -> SettingsScreen(
                            onBack = { host.screen = AppScreen.Main },
                            // 二级页开合回传：二级页期间最外层返回键 / 预测式返回让位
                            onSubpageChanged = { settingsInSubpage = it }
                        )
                        AppScreen.Main -> MainScreen(
                            files = host.files,
                            statusText = host.statusText,
                            progress = host.progress,
                            progressDetail = host.progressDetail,
                            isImporting = host.isImporting,
                            importProgress = host.importProgress,
                            isConverting = host.isConverting,
                            isPickerOpening = host.isPickerOpening,
                            clearBusy = host.clearBusy,
                            selectedFormat = host.selectedFormat,
                            listState = listState,
                            onAddFiles = { host.onAddFiles() },
                            onBatchImport = { host.onBatchImport() },
                            onOpenSettings = { host.screen = AppScreen.Settings },
                            onClearFiles = { host.clearFiles() },
                            onConvert = { host.startConvert() },
                            onStopConvert = { host.stopConvert() },
                            outputMode = host.outputMode,
                            outputModeUsable = host.outputModeUsable,
                            onSelectOutputMode = { mode -> host.selectOutputMode(mode) },
                            onSelectItemFormat = { path, key -> host.setItemFormat(path, key) },
                            onRemoveFile = { path -> host.removeFile(path) }
                        )
                    }
                } // AnimatedContent
            } // 页面采样层发布作用域

            // ── 应用级弹窗（与页面同窗口的覆盖层，见 LiquidDialog）──
            CompositionLocalProvider(
                LocalGlassBackdrop provides (
                        pageBackdropPublisher.backdrop ?: LocalGlassBackdrop.current
                        )
            ) {
                UpdateFlowHosts(
                    updateFlow,
                    vibrate = { haptic.click() },
                    enabled = sessionDialogOwner == DialogOwner.UPDATE,
                    onForcedRetry = {
                        updateFlow.clearForcedBlocked()
                        startupCheckKey++
                    }
                )

                LegacyUninstallFlowHosts(
                    legacyFlow,
                    vibrate = { haptic.click() },
                    enabled = sessionDialogOwner == DialogOwner.LEGACY
                )

                // 覆盖 / 删除原图：开始输出前的简短风险提示（每次开始都弹）
                RiskAckDialog(
                    visible = host.riskDialogVisible,
                    replaceOriginal = host.outputMode == OutputMode.REPLACE,
                    onDismiss = { host.riskDialogVisible = false },
                    onConfirm = { host.confirmRisk(false) }
                )

                // 选择文件之前：先确定导入方式（合并 / 转换视频 / 转换动态照片 / 拆分动态照片）
                ImportModeDialog(
                    visible = host.importModeDialogVisible,
                    onDismiss = { host.importModeDialogVisible = false },
                    onPick = { mode -> host.pickImportMode(mode) }
                )

                // Go 轻量版在现代系统上的启动提醒：可继续使用，也可打开已安装的完整版
                LiquidDialog(
                    visible = showGoReminder,
                    onDismissRequest = { showGoReminder = false },
                    title = { Text(stringResource(R.string.update_go_remind_title)) },
                    text = { Text(stringResource(R.string.update_go_remind_body)) },
                    confirmButton = {
                        FilledTonalButton(onClick = {
                            haptic.click()
                            showGoReminder = false
                        }) { Text(stringResource(R.string.update_go_continue)) }
                    },
                    dismissButton = if (LegacyApp.isNormalInstalled(context)) {
                        {
                            FilledTonalButton(onClick = {
                                haptic.click()
                                showGoReminder = false
                                LegacyApp.launchNormalIntent(context)?.let { intent ->
                                    runCatching { context.startActivity(intent) }
                                }
                            }) { Text(stringResource(R.string.update_open_normal)) }
                        }
                    } else {
                        {}
                    }
                )

                // 权限请求对话框（首次或仍可请求时；由上方全局闸门决定是否轮到自己弹出）
                LiquidDialog(
                    visible = host.showPermissionDialog && sessionDialogOwner == DialogOwner.PERMISSION,
                    onDismissRequest = {
                        // 拒绝不退出应用，仅关闭对话框
                        host.showPermissionDialog = false
                    },
                    title = { Text(stringResource(R.string.main_dialog_permission_title)) },
                    text = {
                        Text(stringResource(R.string.main_dialog_permission_message))
                    },
                    confirmButton = {
                        FilledTonalButton(onClick = {
                            haptic.click()
                            host.showPermissionDialog = false
                            host.onPermissionConfirm()
                        }) { Text(stringResource(R.string.main_btn_grant)) }
                    },
                    dismissButton = {
                        FilledTonalButton(onClick = {
                            haptic.click()
                            host.showPermissionDialog = false
                        }) { Text(stringResource(R.string.main_btn_deny)) }
                    }
                )

                // 跳转设置对话框（用户选了「不再询问」；与权限请求同一闸门优先级）
                LiquidDialog(
                    visible = host.showSettingsDialog && sessionDialogOwner == DialogOwner.PERMISSION,
                    onDismissRequest = { host.showSettingsDialog = false },
                    title = { Text(stringResource(R.string.main_dialog_settings_title)) },
                    text = { Text(stringResource(R.string.main_dialog_settings_message)) },
                    confirmButton = {
                        FilledTonalButton(onClick = {
                            haptic.click()
                            host.showSettingsDialog = false
                            host.openAppDetailSettings()
                        }) { Text(stringResource(R.string.main_btn_go_settings)) }
                    },
                    dismissButton = {
                        FilledTonalButton(onClick = {
                            haptic.click()
                            host.showSettingsDialog = false
                        }) { Text(stringResource(R.string.main_btn_cancel)) }
                    }
                )

                // 「所有文件访问」引导：三种用途（①直写磁盘保留位置信息 ②开启自动删除原图 ③开启替换模式）
                val purpose = host.allFilesDialogPurpose
                // Android 11+ 才有「所有文件访问」；更低版本（含 Android 10）无法直接改写公共目录文件
                val grantable = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                LiquidDialog(
                    visible = host.showAllFilesDialog && sessionDialogOwner == DialogOwner.ALL_FILES,
                    onDismissRequest = {
                        host.showAllFilesDialog = false
                        host.allFilesDialogPurpose = AllFilesPurpose.LOCATION
                    },
                    title = {
                        Text(
                            when {
                                purpose == AllFilesPurpose.DELETE && grantable -> stringResource(R.string.main_dialog_allfiles_title_delete)
                                purpose == AllFilesPurpose.DELETE -> stringResource(R.string.main_dialog_allfiles_title_delete_unsupported)
                                purpose == AllFilesPurpose.REPLACE && grantable -> stringResource(R.string.main_dialog_allfiles_title_replace)
                                purpose == AllFilesPurpose.REPLACE -> stringResource(R.string.main_dialog_allfiles_title_replace_unsupported)
                                else -> stringResource(R.string.main_dialog_allfiles_title_location)
                            }
                        )
                    },
                    text = {
                        Text(
                            when (purpose) {
                                AllFilesPurpose.DELETE ->
                                    if (grantable) {
                                        stringResource(R.string.main_dialog_allfiles_delete_message)
                                    } else {
                                        stringResource(R.string.main_dialog_allfiles_delete_unsupported_message)
                                    }
                                AllFilesPurpose.REPLACE ->
                                    if (grantable) {
                                        stringResource(R.string.main_dialog_allfiles_replace_message)
                                    } else {
                                        stringResource(R.string.main_dialog_allfiles_replace_unsupported_message)
                                    }
                                AllFilesPurpose.LOCATION ->
                                    stringResource(R.string.main_dialog_allfiles_location_message)
                            }
                        )
                    },
                    confirmButton = {
                        if (grantable) {
                            FilledTonalButton(onClick = {
                                haptic.click()
                                host.showAllFilesDialog = false
                                host.allFilesDialogPurpose = AllFilesPurpose.LOCATION
                                host.requestAllFilesAccess()
                            }) { Text(stringResource(R.string.main_btn_grant_access)) }
                        } else {
                            FilledTonalButton(onClick = {
                                haptic.click()
                                host.showAllFilesDialog = false
                                host.allFilesDialogPurpose = AllFilesPurpose.LOCATION
                            }) { Text(stringResource(R.string.main_btn_got_it)) }
                        }
                    },
                    dismissButton = if (grantable) {
                        {
                            FilledTonalButton(onClick = {
                                haptic.click()
                                host.showAllFilesDialog = false
                                host.allFilesDialogPurpose = AllFilesPurpose.LOCATION
                            }) { Text(stringResource(R.string.main_btn_not_now)) }
                        }
                    } else null
                )

                // 文件名冲突弹窗（跳过 / 覆盖 / 自动重命名）
                // 退出动画期间 conflictRequest 已被置空：记住最后一次非空请求，
                // 让内容渲染完淡出（否则退场时内容会突然消失）
                val lastConflictRequest = remember { mutableStateOf<ConflictRequest?>(null) }
                LaunchedEffect(host.conflictRequest) {
                    if (host.conflictRequest != null) lastConflictRequest.value = host.conflictRequest
                }
                val conflictReq = lastConflictRequest.value
                LiquidDialog(
                    visible = host.conflictRequest != null,
                    onDismissRequest = {
                        // 点外部关闭视同跳过，避免协程悬挂
                        host.chooseConflict(ConflictAction.SKIP)
                    },
                    title = { Text(stringResource(R.string.main_dialog_conflict_title)) },
                    text = {
                        conflictReq?.let { req ->
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    stringResource(R.string.main_dialog_conflict_message, req.displayNames)
                                )
                                // 第一行：跳过 / 覆盖
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FilledTonalButton(
                                        onClick = { haptic.click(); host.chooseConflict(ConflictAction.SKIP) },
                                        modifier = Modifier.weight(1f).height(42.dp)
                                    ) { Text(stringResource(R.string.main_btn_skip)) }
                                    FilledTonalButton(
                                        onClick = { haptic.click(); host.chooseConflict(ConflictAction.OVERWRITE) },
                                        modifier = Modifier.weight(1f).height(42.dp)
                                    ) { Text(stringResource(R.string.main_btn_overwrite)) }
                                }
                                // 第二行：自动重命名（独占一行）
                                FilledTonalButton(
                                    onClick = { haptic.click(); host.chooseConflict(ConflictAction.RENAME) },
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) { Text(stringResource(R.string.main_btn_auto_rename)) }
                                // 整行可点击切换（MD3 习惯：文字也是点击目标）
                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptic.click()
                                            host.conflictAlways = !host.conflictAlways
                                        }
                                        .padding(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    GlassCheckbox(checked = host.conflictAlways)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        stringResource(R.string.main_label_conflict_always),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {}
                )

                // 首次启动：使用协议（未同意前遮挡全部交互）
                Md3AgreementGate()
            } // 应用级弹窗作用域
        } // GlassRoot
    }
}

/**
 * 导入方式选择弹窗：在打开文件选择器**之前**弹出，决定后续显示哪些媒体、
 * 以及导入后走哪条处理路径（合并 / 转换视频 / 转换动态照片 / 拆分动态照片）。
 */
@Composable
private fun ImportModeDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPick: (ImportMode) -> Unit
) {
    LiquidDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.screen_import_mode_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // 只保留四个方式本身：去掉一行说明小字，行高收紧（与液态玻璃侧一致）
                ImportMode.entries.forEach { mode ->
                    Text(
                        stringResource(mode.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(mode) }
                            .padding(horizontal = 10.dp, vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = { },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.screen_import_mode_cancel))
            }
        }
    )
}

/** 错误报告弹窗（崩溃 / 转换失败等）：提交 = 系统分享面板。 */
@Composable
private fun Md3ErrorReportDialog() {
    val context = LocalContext.current
    val request = ErrorReporter.request ?: return
    val message = if (request.crash) {
        stringResource(R.string.error_report_crash_message, request.reason)
    } else {
        stringResource(R.string.error_report_message, request.reason)
    }
    LiquidDialog(
        visible = true,
        onDismissRequest = { ErrorReporter.dismiss() },
        title = { Text(stringResource(R.string.error_report_title)) },
        text = {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    ErrorReporter.share(context, request)
                    ErrorReporter.dismiss()
                }
            ) {
                Text(stringResource(R.string.error_report_submit))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { ErrorReporter.dismiss() }) {
                Text(stringResource(R.string.error_report_later))
            }
        }
    )
}
