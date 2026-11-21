package com.zsz.zlivephoto.ui.liquid

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.BuildConfig
import com.zsz.zlivephoto.CrashHandler
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.UpdateCheckResult
import com.zsz.zlivephoto.core.UpdateChecker
import com.zsz.zlivephoto.flavor.FlavorCapabilities
import com.zsz.zlivephoto.ui.ErrorReporter
import com.zsz.zlivephoto.ui.PendingErrorReportWatcher
import com.zsz.zlivephoto.ui.AllFilesPurpose
import com.zsz.zlivephoto.ui.AppHost
import com.zsz.zlivephoto.ui.AppScreen
import kotlinx.coroutines.launch
import android.widget.Toast
import com.zsz.zlivephoto.ui.AppSettings
import com.zsz.zlivephoto.ui.ConflictAction
import com.zsz.zlivephoto.ui.ConflictRequest
import com.zsz.zlivephoto.ui.FancyEasing
import com.zsz.zlivephoto.ui.LegacyApp
import com.zsz.zlivephoto.ui.OutputMode
import com.zsz.zlivephoto.ui.ReminderKey
import com.zsz.zlivephoto.ui.RiskAckDialog
import com.zsz.zlivephoto.ui.SystemBarsAppearance
import com.zsz.zlivephoto.ui.ZLivePhotoTheme
import com.zsz.zlivephoto.ui.glass.GlassBackgroundFill
import com.zsz.zlivephoto.ui.glass.GlassRoot
import com.zsz.zlivephoto.ui.glass.LocalGlassBackdrop
import com.zsz.zlivephoto.ui.glass.LocalPageBackdropPublisher
import com.zsz.zlivephoto.ui.glass.PageBackdropPublisher
import com.zsz.zlivephoto.sponsor.SponsorState
import com.zsz.zlivephoto.ui.liquid.sponsor.LiquidSponsorReminderHost
import com.zsz.zlivephoto.ui.liquid.common.LiquidSwipeBackState
import com.zsz.zlivephoto.ui.liquid.onboarding.LiquidAgreementGate
import com.zsz.zlivephoto.ui.liquid.common.liquidSwipeBackDrag
import com.zsz.zlivephoto.ui.LegacyUninstallFlowHosts
import com.zsz.zlivephoto.ui.picker.ImportMode
import com.zsz.zlivephoto.ui.rememberHapticFeedback
import com.zsz.zlivephoto.ui.rememberLegacyUninstallFlow
import com.zsz.zlivephoto.ui.rememberUpdateFlow
import com.zsz.zlivephoto.ui.UpdateFlowHosts
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.GlasmioSwipeBackExclusionRegistry
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.LocalGlasmioSwipeBackExclusions
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.icons.GlasmioIconProvider
import io.github.neoruaa.glasmio.icons.GlasmioIconResolver
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonLayout
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import io.github.neoruaa.glasmio.ui.GlasmioSwitch
import io.github.neoruaa.glasmio.ui.GlasmioTextSelectionHost
import kotlinx.coroutines.CancellationException

/**
 * SfIcon 的 Material 兜底解析器。
 *
 * 宿主未提供 SF 符号字体，[SfIcon] 会走解析器取 Material 向量；
 * 库内置 [io.github.neoruaa.glasmio.icons.MaterialGlasmioIconResolver] 未收录
 * `bolt` / `stop.fill`（会落到 Widgets 方块），这里补上对应图标，
 * 其余符号名返回 null 继续交给库默认解析。
 */
private val AppGlasmioIconResolver = GlasmioIconResolver { systemName ->
    when (systemName) {
        "bolt" -> Icons.Default.Bolt
        "stop.fill" -> Icons.Default.Stop
        // 库的兜底用 startsWith("photo") → Image，会丢掉「+」，
        // 这里补成带加号的相片图标（对应 SF 的 photo.badge.plus）
        "photo.badge.plus" -> Icons.Default.AddPhotoAlternate
        // 竖排三点（更多菜单）与排序 / 筛选图标：库未收录时用 Material 语义图标
        "ellipsis.vertical" -> Icons.Default.MoreVert
        "arrow.up.arrow.down" -> Icons.Default.SwapVert
        "arrow.up" -> Icons.Default.ArrowUpward
        "arrow.down" -> Icons.Default.ArrowDownward
        "square.grid.2x2" -> Icons.Default.GridView
        // 文本选择工具栏：剪切 / 拷贝 / 粘贴 / 全选
        "scissors" -> Icons.Default.ContentCut
        "document.on.document" -> Icons.Default.ContentCopy
        "document.on.clipboard" -> Icons.Default.ContentPaste
        "square.dashed" -> Icons.Default.SelectAll
        "character" -> Icons.Default.TextFields
        // 输出格式 / 全选状态
        "doc.text" -> Icons.Default.Description
        "checkmark.circle", "checkmark.circle.fill" -> Icons.Default.CheckCircle
        else -> null
    }
}

/**
 * 会话型弹窗占用者（全局弹窗闸门）：同一时刻只允许一个弹窗占用屏幕。
 * 处理（转换 / 导入）进行中一律不占用，处理队列结束后再按此优先级依次弹出。
 */
private enum class DialogOwner {
    NONE, PERMISSION, UPDATE, ALL_FILES, LEGACY, RISK, IMPORT_MODE, GO_REMINDER, SPONSOR
}

/**
 * 液态玻璃版应用外壳（normal flavor，Android 13+ 且支持 AGSL 着色器时启用）。
 *
 * 结构：
 * - [GlasmioIconProvider] + [GlasmioTheme]：Glasmio 的图标与玻璃色板；
 * - [ZLivePhotoTheme]：应用自身配色（页面沿用原有设计，保证与 go 版功能一致）；
 * - [GlasmioBackdropHost]：把「背景 + 页面」录制为采样源，应用级弹窗据此做实时模糊/折射；
 * - 页面路由、过渡动画与全局弹窗闸门沿用应用原有实现，弹窗统一使用 Glasmio 组件。
 *
 * 标准版只有这一套外壳（已移除 Material 3 回退）。
 */
@Composable
fun LiquidAppRoot(host: AppHost) {
    // 标记「当前运行在玻璃外壳中」：崩溃日志据此区分外壳阶段
    CrashHandler.liquidShellActive = true
    GlasmioIconProvider(resolver = AppGlasmioIconResolver) {
        // 全局模糊度（设置页唯一滑条）统一驱动 Glasmio 玻璃外观：
        // 高模糊度 → 关闭折射、改用磨砂不透明外观；低模糊度 → 全通透折射。
        // 注意：Glasmio 内部模糊半径不可配，只能通过外观档位近似实现全局一致。
        GlasmioTheme(
            isDark = host.isDarkTheme,
            appearance = GlassAppearance(
                reduceTransparency = AppSettings.glassSolid
            )
        ) {
            ZLivePhotoTheme(darkTheme = host.isDarkTheme) {
                // 系统栏图标随主题切换（浅色模式必须是深色图标）
                SystemBarsAppearance(isDark = host.isDarkTheme)
                LiquidAppContent(host)
            }
        }
    }
}

@Composable
private fun LiquidAppContent(host: AppHost) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 上次崩溃 / 运行期错误：统一弹「提交错误报告」（日志只记 WARN/ERROR）
    PendingErrorReportWatcher(context)

    // 玻璃外壳成功绘制出首帧：清除上次的崩溃回退标记
    LaunchedEffect(Unit) {
        withFrameNanos { }
        CrashHandler.markGlassHealthy(context)
    }

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
    // 据此采样「根渐变 + 当前页面」——弹窗与页面同窗口，坐标天然对正。
    val pageBackdropPublisher = remember { PageBackdropPublisher() }

    // 启动时自动检查更新。
    // 优先级：Go 版提醒（可继续使用）→ 软件更新 → 旧版本卸载提示。
    var startupCheckKey by remember { mutableStateOf(0) }
    // Go 版在 Android 12+ 只做一次启动提醒，不再拦截使用（用户可选择继续使用 Go 版）
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
        if (!updatePrompted && !FlavorCapabilities.isGo &&
            !AppSettings.isReminderSuppressed(ReminderKey.LEGACY_GO_INSTALLED) &&
            LegacyApp.isGoInstalled(context)
        ) {
            legacyFlow.show()
        }
    }

    // ── 全局会话型弹窗闸门 ──
    // 赞助提醒：启动计数在 MainActivity 里累加，这里只取一次「本次是否该弹」，
    // 取完即清标记，旋转屏幕不会重复弹。
    var sponsorReminder by remember { mutableStateOf(SponsorState.consumePending()) }
    val sessionDialogOwner = when {
        host.isConverting || host.isImporting -> DialogOwner.NONE
        host.showPermissionDialog || host.showSettingsDialog -> DialogOwner.PERMISSION
        updateFlow.wantsDialog -> DialogOwner.UPDATE
        host.showAllFilesDialog -> DialogOwner.ALL_FILES
        legacyFlow.visible -> DialogOwner.LEGACY
        host.riskDialogVisible -> DialogOwner.RISK
        host.importModeDialogVisible -> DialogOwner.IMPORT_MODE
        showGoReminder -> DialogOwner.GO_REMINDER
        sponsorReminder -> DialogOwner.SPONSOR
        else -> DialogOwner.NONE
    }

    // ── 页面栈（Flash 舞台模型）──
    // 主界面常驻底层；选择器 / 设置作为上层页面整幅推入。
    // 手势返回直接驱动「关闭进度」：上层页面跟手右移，主界面以 30% 视差归位。
    val pageSwipe = remember { LiquidSwipeBackState() }
    var pageLayer by remember { mutableStateOf<AppScreen?>(null) }
    var pickerInSubpage by remember { mutableStateOf(false) }
    var settingsInSubpage by remember { mutableStateOf(false) }
    LaunchedEffect(host.screen) {
        val target = host.screen
        if (target == AppScreen.Main) {
            if (pageLayer != null) {
                pageSwipe.animateTo(1f, tween(450, easing = FancyEasing))
                pageLayer = null
                pageSwipe.progress = 0f
            }
        } else if (pageLayer == null) {
            pageSwipe.progress = 1f
            pageLayer = target
            pageSwipe.animateTo(0f, tween(450, easing = FancyEasing))
        } else if (pageLayer != target) {
            pageLayer = target
        }
    }
    // 离开页面后清空二级页标记，恢复顶层手势返回
    LaunchedEffect(host.screen) {
        if (host.screen != AppScreen.Picker) pickerInSubpage = false
        if (host.screen != AppScreen.Settings) settingsInSubpage = false
    }
    BackHandler(
        enabled = !pickerInSubpage && !settingsInSubpage &&
            (host.screen == AppScreen.Picker || host.screen == AppScreen.Settings)
    ) {
        host.screen = AppScreen.Main
    }

    // 手势返回排除区注册表：Glasmio 滑块等通过 swipeBackExcluded() 自动登记
    val swipeExclusions = remember { GlasmioSwipeBackExclusionRegistry() }
    // RTL：推拉方向必须跟随布局方向
    val rtlDir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1

    // 主界面常驻底层：进入选择器 / 设置时不销毁，返回时可直接复用
    val mainScreen: @Composable () -> Unit = {
        LiquidMainScreen(
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

    GlasmioBackdropHost(
        modifier = Modifier.fillMaxSize(),
        // 采样源只画「根渐变背景」。Glasmio 要求玻璃控件绘制在采样层之外：
        // 控件若落在被采样的层里，会把自己的绘制结果当成采样源（反馈循环），
        // 表现为 RenderNode 无限嵌套 → RenderThread 原生栈溢出（启动即闪退）
        sampledContent = { _ ->
            GlassRoot(Modifier.fillMaxSize()) { }
        },
        overlayContent = { _ ->
            CompositionLocalProvider(
                LocalPageBackdropPublisher provides pageBackdropPublisher,
                LocalGlasmioSwipeBackExclusions provides swipeExclusions
            ) {
            // 文本工具条与选择端点统一使用 Glasmio 的窗口级自绘 chrome
            GlasmioTextSelectionHost {
            GlassRoot(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (AppSettings.glassActive) Modifier
                            else Modifier.background(MaterialTheme.colorScheme.background)
                        )
                ) {
                    // 主界面固定在同一个组合槽里：进出页面只改位移，不换槽位。
                    // 换槽位会让整棵主界面重新组合（列表项、滚动位置、按钮状态重建），
                    // 表现就是切页时闪一下、上一页部分内容位置跳动。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // 底层主界面：随上层页面推入 / 拖出做 30% 视差
                                translationX = if (pageLayer != null) {
                                    -0.3f * size.width * (1f - pageSwipe.progress) * rtlDir
                                } else 0f
                            }
                    ) {
                        mainScreen()
                    }
                    pageLayer?.let { page ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = pageSwipe.progress * size.width * rtlDir
                                }
                                .liquidSwipeBackDrag(
                                    state = pageSwipe,
                                    enabled = !pickerInSubpage && !settingsInSubpage,
                                    rtlDir = rtlDir,
                                    onCommit = { host.screen = AppScreen.Main }
                                )
                        ) {
                            // 页面自带不透明底色：转场中下层主界面不会从这里透出来
                            // （此前页面内容背景透明，手势拖动时整页看起来是半透明的）
                            GlassBackgroundFill(Modifier.fillMaxSize())
                            when (page) {
                            AppScreen.Picker -> LiquidPhotoPickerScreen(
                                albums = host.albums,
                                scanner = host.scanner,
                                scannerScope = scope,
                                isDarkTheme = host.isDarkTheme,
                                importMode = host.importMode,
                                outputFormat = host.selectedFormat,
                                onOutputFormatChange = { host.setOutputFormat(it) },
                                onBack = { host.screen = AppScreen.Main },
                                onSubpageChanged = { pickerInSubpage = it },
                                onConfirm = { items, coverSec ->
                                    host.screen = AppScreen.Main
                                    host.importPickedItems(items, coverSec)
                                }
                            )
                            AppScreen.Settings -> LiquidSettingsScreen(
                                onBack = { host.screen = AppScreen.Main },
                                onSubpageChanged = { settingsInSubpage = it },
                                onRequestUpdate = {
                                    // 「检查更新 / 重新安装本版本」统一由应用根层级弹窗承载：
                                    // 若在设置页内部渲染，会被页面推拉动画裁切、玻璃采样错位，
                                    // 表现为弹窗"跑进设置层级里"。
                                    scope.launch {
                                        val latest = UpdateChecker.fetchLatest()
                                        when {
                                            latest == null -> Toast.makeText(
                                                context,
                                                R.string.update_err_no_connection,
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            UpdateChecker.isNewer(
                                                latest.version,
                                                BuildConfig.VERSION_NAME
                                            ) -> updateFlow.present(latest)

                                            else -> updateFlow.startReinstall(latest)
                                        }
                                    }
                                }
                            )
                            AppScreen.Main -> Unit
                                }
                            }
                        }
                    }
                }
            // ── 以下为应用级弹窗：与页面同层，同样位于采样层之外 ──
            // 自研玻璃弹窗（更新 / 旧版本提示 / 风险确认）仍需页面采样层
            androidx.compose.runtime.CompositionLocalProvider(
                LocalGlassBackdrop provides (
                        pageBackdropPublisher.backdrop ?: LocalGlassBackdrop.current
                        )
            ) {
                LiquidUpdateFlowHosts(
                    updateFlow,
                    vibrate = { haptic.click() },
                    enabled = sessionDialogOwner == DialogOwner.UPDATE,
                    onForcedRetry = {
                        updateFlow.clearForcedBlocked()
                        startupCheckKey++
                    }
                )

                LiquidLegacyUninstallFlowHosts(
                    legacyFlow,
                    vibrate = { haptic.click() },
                    enabled = sessionDialogOwner == DialogOwner.LEGACY
                )

                // 覆盖 / 删除原图：开始输出前的简短风险提示（每次开始都弹）
                LiquidRiskAckDialog(
                    visible = host.riskDialogVisible && sessionDialogOwner == DialogOwner.RISK,
                    replaceOriginal = host.outputMode == OutputMode.REPLACE,
                    onDismiss = { host.riskDialogVisible = false },
                    onConfirm = { host.confirmRisk(false) }
                )

                // 选择文件之前：先确定导入方式
                LiquidImportModeDialog(
                    visible = host.importModeDialogVisible &&
                        sessionDialogOwner == DialogOwner.IMPORT_MODE,
                    onDismiss = { host.importModeDialogVisible = false },
                    onPick = { mode -> host.pickImportMode(mode) }
                )

                // Go 轻量版在现代系统上的启动提醒：可继续使用，也可打开已安装的完整版
                GoEditionReminderDialog(
                    visible = showGoReminder && sessionDialogOwner == DialogOwner.GO_REMINDER,
                    normalInstalled = LegacyApp.isNormalInstalled(context),
                    onDismiss = { showGoReminder = false },
                    onOpenNormal = {
                        showGoReminder = false
                        LegacyApp.launchNormalIntent(context)?.let { intent ->
                            runCatching { context.startActivity(intent) }
                        }
                    }
                )

                // 赞助提醒：每 5 次冷启动弹一次，优先级最低（压在所有会话弹窗之后）
                LiquidSponsorReminderHost(
                    visible = sponsorReminder && sessionDialogOwner == DialogOwner.SPONSOR,
                    onDismiss = { sponsorReminder = false }
                )

                // 权限请求对话框
                GlasmioAlertDialog(
                    visible = host.showPermissionDialog && sessionDialogOwner == DialogOwner.PERMISSION,
                    onDismissRequest = { host.showPermissionDialog = false },
                    title = stringResource(R.string.main_dialog_permission_title),
                    message = stringResource(R.string.main_dialog_permission_message),
                    buttons = listOf(
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_deny),
                            role = GlasmioAlertButtonRole.Cancel,
                            onClick = {
                                haptic.click()
                                host.showPermissionDialog = false
                            }
                        ),
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_grant),
                            onClick = {
                                haptic.click()
                                host.showPermissionDialog = false
                                host.onPermissionConfirm()
                            }
                        )
                    )
                )

                // 跳转设置对话框（用户选了「不再询问」）
                GlasmioAlertDialog(
                    visible = host.showSettingsDialog && sessionDialogOwner == DialogOwner.PERMISSION,
                    onDismissRequest = { host.showSettingsDialog = false },
                    title = stringResource(R.string.main_dialog_settings_title),
                    message = stringResource(R.string.main_dialog_settings_message),
                    buttons = listOf(
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_cancel),
                            role = GlasmioAlertButtonRole.Cancel,
                            onClick = {
                                haptic.click()
                                host.showSettingsDialog = false
                            }
                        ),
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_go_settings),
                            onClick = {
                                haptic.click()
                                host.showSettingsDialog = false
                                host.openAppDetailSettings()
                            }
                        )
                    )
                )

                // 「所有文件访问」引导：三种用途
                val purpose = host.allFilesDialogPurpose
                val grantable = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                val allFilesTitle = when {
                    purpose == AllFilesPurpose.DELETE && grantable ->
                        stringResource(R.string.main_dialog_allfiles_title_delete)
                    purpose == AllFilesPurpose.DELETE ->
                        stringResource(R.string.main_dialog_allfiles_title_delete_unsupported)
                    purpose == AllFilesPurpose.REPLACE && grantable ->
                        stringResource(R.string.main_dialog_allfiles_title_replace)
                    purpose == AllFilesPurpose.REPLACE ->
                        stringResource(R.string.main_dialog_allfiles_title_replace_unsupported)
                    else -> stringResource(R.string.main_dialog_allfiles_title_location)
                }
                val allFilesMessage = when (purpose) {
                    AllFilesPurpose.DELETE ->
                        if (grantable) stringResource(R.string.main_dialog_allfiles_delete_message)
                        else stringResource(R.string.main_dialog_allfiles_delete_unsupported_message)
                    AllFilesPurpose.REPLACE ->
                        if (grantable) stringResource(R.string.main_dialog_allfiles_replace_message)
                        else stringResource(R.string.main_dialog_allfiles_replace_unsupported_message)
                    AllFilesPurpose.LOCATION ->
                        stringResource(R.string.main_dialog_allfiles_location_message)
                }
                GlasmioAlertDialog(
                    buttonLayout = GlasmioAlertButtonLayout.Stacked,
                    visible = host.showAllFilesDialog && sessionDialogOwner == DialogOwner.ALL_FILES,
                    onDismissRequest = {
                        host.showAllFilesDialog = false
                        host.allFilesDialogPurpose = AllFilesPurpose.LOCATION
                    },
                    title = allFilesTitle,
                    message = allFilesMessage,
                    buttons = buildList {
                        if (grantable) {
                            add(
                                GlasmioAlertButtonSpec(
                                    label = stringResource(R.string.main_btn_not_now),
                                    role = GlasmioAlertButtonRole.Cancel,
                                    onClick = {
                                        haptic.click()
                                        host.showAllFilesDialog = false
                                        host.allFilesDialogPurpose = AllFilesPurpose.LOCATION
                                    }
                                )
                            )
                            add(
                                GlasmioAlertButtonSpec(
                                    label = stringResource(R.string.main_btn_grant_access),
                                    onClick = {
                                        haptic.click()
                                        host.showAllFilesDialog = false
                                        host.allFilesDialogPurpose = AllFilesPurpose.LOCATION
                                        host.requestAllFilesAccess()
                                    }
                                )
                            )
                        } else {
                            add(
                                GlasmioAlertButtonSpec(
                                    label = stringResource(R.string.main_btn_got_it),
                                    onClick = {
                                        haptic.click()
                                        host.showAllFilesDialog = false
                                        host.allFilesDialogPurpose = AllFilesPurpose.LOCATION
                                    }
                                )
                            )
                        }
                    }
                )

                // 文件名冲突弹窗（跳过 / 覆盖 / 自动重命名）
                // 退出动画期间 conflictRequest 已被置空：记住最后一次非空请求。
                // 组合期直接写入（而非 LaunchedEffect）：后者晚于首帧组合，
                // 首次弹出时 message 仍为 null，会闪一下空白文案。
                var lastConflictRequest by remember { mutableStateOf<ConflictRequest?>(null) }
                host.conflictRequest?.let { lastConflictRequest = it }
                val conflictReq = host.conflictRequest ?: lastConflictRequest
                GlasmioAlertDialog(
                    visible = host.conflictRequest != null,
                    onDismissRequest = { host.chooseConflict(ConflictAction.SKIP) },
                    title = stringResource(R.string.main_dialog_conflict_title),
                    message = conflictReq?.let {
                        stringResource(R.string.main_dialog_conflict_message, it.displayNames)
                    },
                    buttonLayout = GlasmioAlertButtonLayout.Stacked,
                    buttons = listOf(
                        // 顺序：自动重命名（默认蓝色）/ 覆盖（红色破坏性）/ 跳过（取消）
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_auto_rename),
                            onClick = { haptic.click(); host.chooseConflict(ConflictAction.RENAME) }
                        ),
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_overwrite),
                            role = GlasmioAlertButtonRole.Destructive,
                            onClick = { haptic.click(); host.chooseConflict(ConflictAction.OVERWRITE) }
                        ),
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_skip),
                            role = GlasmioAlertButtonRole.Cancel,
                            onClick = { haptic.click(); host.chooseConflict(ConflictAction.SKIP) }
                        )
                    ),
                ) {
                    // 「本轮操作不再询问」开关：弹窗内容不继承玻璃色板的内容色，
                    // 必须显式取 LocalGlassColors，否则暗色下默认黑色文字压在深色玻璃上。
                    // 底部再留 14dp，避免开关与下方按钮贴在一起。
                    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.click()
                                    host.conflictAlways = !host.conflictAlways
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            GlasmioSwitch(
                                checked = host.conflictAlways,
                                onCheckedChange = {
                                    haptic.click()
                                    host.conflictAlways = it
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.main_label_conflict_always),
                                style = io.github.neoruaa.glasmio.core.IosTypography.subheadline,
                                color = LocalGlassColors.current.content
                            )
                        }
                    }
                }

                // 错误报告弹窗（位于玻璃宿主内部，才能采样到页面并显示玻璃材质）
                LiquidErrorReportDialog()
                // 首次启动：使用协议（未同意前遮挡全部交互）
                LiquidAgreementGate()
            }
            }
            }
        }
    )
}

/** 错误报告弹窗（崩溃 / 转换失败等）：提交 = 系统分享面板。 */
@Composable
private fun LiquidErrorReportDialog() {
    val context = LocalContext.current
    val request = ErrorReporter.request ?: return
    val message = if (request.crash) {
        stringResource(R.string.error_report_crash_message, request.reason)
    } else {
        stringResource(R.string.error_report_message, request.reason)
    }
    GlasmioAlertDialog(
        visible = true,
        onDismissRequest = { ErrorReporter.dismiss() },
        title = stringResource(R.string.error_report_title),
        message = message,
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.error_report_later),
                role = GlasmioAlertButtonRole.Cancel,
                onClick = { ErrorReporter.dismiss() }
            ),
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.error_report_submit),
                onClick = {
                    ErrorReporter.share(context, request)
                    ErrorReporter.dismiss()
                }
            )
        )
    )
}

/** Go 轻量版在现代系统上的启动提醒（不阻塞使用）。 */
@Composable
private fun GoEditionReminderDialog(
    visible: Boolean,
    normalInstalled: Boolean,
    onDismiss: () -> Unit,
    onOpenNormal: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    GlasmioAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.update_go_remind_title),
        message = stringResource(R.string.update_go_remind_body),
        buttons = buildList {
            add(
                GlasmioAlertButtonSpec(
                    label = stringResource(R.string.update_go_continue),
                    role = GlasmioAlertButtonRole.Cancel,
                    onClick = {
                        haptic.click()
                        onDismiss()
                    }
                )
            )
            if (normalInstalled) {
                add(
                    GlasmioAlertButtonSpec(
                        label = stringResource(R.string.update_open_normal),
                        onClick = {
                            haptic.click()
                            onOpenNormal()
                        }
                    )
                )
            }
        }
    )
}

/** 导入方式选择弹窗：决定后续显示哪些媒体、以及导入后走哪条处理路径。 */
@Composable
internal fun LiquidImportModeDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPick: (ImportMode) -> Unit
) {
    val haptic = rememberHapticFeedback()
    // 纯色模式下 Glasmio 会把弹窗底色从 container 换成 systemBackground（reduceTransparency 分支），
    // 导致同一个弹窗的选项底色/层次与通透模式不一致。这里只对弹窗关掉该分支，
    // 保证两种外观下弹窗内部 UI 完全一致。
    CompositionLocalProvider(
        LocalGlassAppearance provides GlasmioTheme.appearance.copy(reduceTransparency = false)
    ) {
    GlasmioAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.screen_import_mode_title),
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.screen_import_mode_cancel),
                role = GlasmioAlertButtonRole.Cancel,
                onClick = onDismiss
            )
        ),
        content = {
            val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
            if (landscape) {
                // 横屏：4 个导入方式排成 2×2。竖排 4 行会超过横屏可用高度，
                // 「取消」按钮被顶出屏幕；2×2 把弹窗高度压回可用范围内。
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ImportMode.entries.chunked(2).forEach { rowModes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowModes.forEach { mode ->
                                ImportModeCell(
                                    mode = mode,
                                    onClick = {
                                        haptic.click()
                                        onPick(mode)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowModes.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                // 4 行导入方式在横屏（可用高约 390dp）会超过弹窗可用高度，
                // 必须限高 + 内部滚动，否则「取消」按钮被推出屏幕。
                GlasmioGroupedList(
                    modifier = Modifier
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.5f)
                        .verticalScroll(rememberScrollState()),
                    // 弹窗自身就是玻璃面板，四行选项不再叠一层分组底板：
                    // 那层底板亮色下接近白色（看不出），深色下是明显的深色块，两种模式观感不统一。
                    framed = false
                ) {
                    ImportMode.entries.forEachIndexed { index, mode ->
                        // 精简为单行：图标 + 名称，不再附说明文字
                        GlasmioListRow(
                            title = stringResource(mode.labelRes),
                            // 无分组坐标时每行都会画顶部分隔线，首行必须关掉
                            showTopSeparator = index > 0,
                            leading = {
                                SfIcon(
                                    when (mode) {
                                        ImportMode.COMPOSE -> "photo.badge.plus"
                                        ImportMode.VIDEO -> "play.fill"
                                        ImportMode.LIVE -> "bolt"
                                        ImportMode.EXTRACT -> "arrow.down.circle"
                                    },
                                    contentDescription = null,
                                    size = 20.dp,
                                    tint = LocalGlassColors.current.accent
                                )
                            },
                            onClick = {
                                haptic.click()
                                onPick(mode)
                            }
                        )
                    }
                }
            }
            // AlertSurface 的 content 与按钮之间是 0 间距，补 12dp 让列表不贴着「取消」按钮
            Spacer(Modifier.height(12.dp))
        }
    )
    }
}

/**
 * 横屏导入方式单元格：图标在上、名称在下，四个排成 2×2。
 * 弹窗宽度由 GlasmioAlertDialog 固定（≤300dp），两列各约 130dp，
 * 用 footnote 字号可单行放下中文名称。
 */
@Composable
private fun ImportModeCell(
    mode: ImportMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalGlassColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            // 不叠 MD3 涟漪：按压反馈由整组的触觉 + 玻璃弹窗自身提供
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SfIcon(
            when (mode) {
                ImportMode.COMPOSE -> "photo.badge.plus"
                ImportMode.VIDEO -> "play.fill"
                ImportMode.LIVE -> "bolt"
                ImportMode.EXTRACT -> "arrow.down.circle"
            },
            contentDescription = null,
            size = 20.dp,
            tint = colors.accent
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(mode.labelRes),
            style = IosTypography.footnote,
            color = colors.content,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
