package com.zsz.zlivephoto.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.BuildConfig
import com.zsz.zlivephoto.core.UpdateChecker
import com.zsz.zlivephoto.core.UpdateCheckResult

private const val GITHUB_REPO_URL = "https://github.com/zsz-of/ZLC"

/**
 * 设置页入口：负责各子页（0=主设置，1=不再提示弹窗，2=关于）的
 * 进入/退出过渡与返回键层级。主内容委托给 SettingsMainContent。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var subpage by remember { mutableStateOf(0) }
    // 设置主内容滚动位置提升到此处，子页返回后保持进入前的滚动位置
    val settingsScrollState = rememberScrollState()
    // 任一子页打开时，系统返回键先回到设置页（而非直接回主页）
    BackHandler(enabled = subpage != 0) { subpage = 0 }
    AnimatedContent(
        targetState = subpage,
        transitionSpec = {
            if (targetState == 0) {
                (slideInHorizontally(tween(300, easing = FancyEasing)) { -it } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(300, easing = FancyEasing)) { it } + fadeOut(tween(200)))
            } else {
                (slideInHorizontally(tween(300, easing = FancyEasing)) { it } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(300, easing = FancyEasing)) { -it } + fadeOut(tween(200)))
            }
        },
        label = "settingsSubNav"
    ) { page ->
        when (page) {
            0 -> SettingsMainContent(
                scrollState = settingsScrollState,
                onBack = onBack,
                onOpenReminder = { subpage = 1 },
                onOpenAbout = { subpage = 2 }
            )
            1 -> ReminderSettingsScreen(onBack = { subpage = 0 })
            else -> AboutScreen(onBack = { subpage = 0 })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainContent(
    scrollState: ScrollState,
    onBack: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    val context = LocalContext.current

    var showCustomPalette by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { haptic.click(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── 通用 ──
            SectionTitle("通用")
            val isGo = BuildConfig.FLAVOR == "go"
            SettingsRowsGroup(
                buildList {
                    add { s ->
                        SettingsSwitchRow(
                            shape = s,
                            title = "启动时检查更新",
                            subtitle = "启动时自动从 GitHub 获取新版本，有更新时提示下载",
                            checked = AppSettings.checkUpdateOnStartup,
                            leading = {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onToggle = { AppSettings.setStartupUpdateCheck(it) }
                        )
                    }
                    // go 轻量版：无马达反馈与按钮弹性动画，隐藏对应开关
                    if (!isGo) {
                        add { s ->
                            SettingsSwitchRow(
                                shape = s,
                                title = "震动反馈",
                                subtitle = "按钮点击与状态变化时的触觉反馈",
                                checked = AppSettings.hapticsEnabled,
                                onToggle = { AppSettings.setHaptics(it) }
                            )
                        }
                        add { s ->
                            SettingsSwitchRow(
                                shape = s,
                                title = "按钮弹性动画",
                                subtitle = "按钮按下时的缩放回弹动画",
                                checked = AppSettings.bounceEnabled,
                                onToggle = { AppSettings.setBounce(it) }
                            )
                        }
                    }
                }
            )

            // ── 外观 ──
            SectionTitle("外观")
            // 深色模式三选 + Material You 动态取色（go 无动态取色，隐藏对应行）
            SettingsRowsGroup(
                buildList {
                    add { s ->
                        ThemeModeRow(
                            shape = s,
                            title = "跟随系统",
                            selected = AppSettings.themeMode == "system",
                            onClick = { AppSettings.setThemeModeValue("system") }
                        )
                    }
                    add { s ->
                        ThemeModeRow(
                            shape = s,
                            title = "深色",
                            selected = AppSettings.themeMode == "dark",
                            onClick = { AppSettings.setThemeModeValue("dark") }
                        )
                    }
                    add { s ->
                        ThemeModeRow(
                            shape = s,
                            title = "浅色",
                            selected = AppSettings.themeMode == "light",
                            onClick = { AppSettings.setThemeModeValue("light") }
                        )
                    }
                    if (!isGo) {
                        add { s ->
                            SettingsSwitchRow(
                                shape = s,
                                title = "Material You 动态取色",
                                subtitle = "跟随系统壁纸颜色（Android 12+）；关闭后使用下方预制主题色",
                                checked = AppSettings.dynamicTheme,
                                onToggle = { AppSettings.setUseDynamicTheme(it) }
                            )
                        }
                    }
                }
            )
            Spacer(Modifier.height(4.dp))
            // 预制主题色（动态取色开启时禁用；go 版无动态取色，始终可选）
            val presetEnabled = isGo || !AppSettings.dynamicTheme
            val customActive = AppSettings.customHue >= 0f
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                    .alpha(if (presetEnabled) 1f else 0.38f)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text("预制主题色", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (presetEnabled) "当前：${if (customActive) "自定义" else presetNames[AppSettings.presetColor]}"
                    else "关闭「Material You 动态取色」后可选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                // 4 列两行布局：7 个预制色 + 1 个自定义调色板
                for (row in 0 until 2) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0 until 4) {
                            val idx = row * 4 + col
                            if (idx == 7) {
                                PaletteLauncherDot(
                                    selected = customActive,
                                    enabled = presetEnabled,
                                    onClick = {
                                        haptic.click()
                                        showCustomPalette = true
                                    }
                                )
                            } else {
                                PresetColorDot(
                                    index = idx,
                                    selected = !customActive && AppSettings.presetColor == idx,
                                    enabled = presetEnabled,
                                    onClick = {
                                        haptic.click()
                                        AppSettings.setPresetColorIndex(idx)
                                    }
                                )
                            }
                        }
                    }
                    if (row == 0) Spacer(Modifier.height(12.dp))
                }
            }

            // ── 默认选项 ──
            SectionTitle("默认选项")
            SettingsRowsGroup(
                listOf(
                    { s ->
                        SettingsActionRow(
                            shape = s,
                            title = "不再提示弹窗",
                            subtitle = "管理所有「不再提示」弹窗的开关",
                            icon = { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                haptic.click()
                                onOpenReminder()
                            }
                        )
                    }
                )
            )

            // ── 关于 ──
            SectionTitle("关于")
            SettingsRowsGroup(
                listOf(
                    { s ->
                        SettingsActionRow(
                            shape = s,
                            title = "关于",
                            subtitle = "版本 · 检查更新 · 鸣谢与赞助",
                            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                haptic.click()
                                onOpenAbout()
                            }
                        )
                    }
                )
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    // 自定义调色板：任意色相选择（主题色只按色相派生，饱和度/明度沿用 MD3 基线）
    if (showCustomPalette) {
        var hue by remember {
            mutableStateOf(if (AppSettings.customHue >= 0f) AppSettings.customHue else presetHues[AppSettings.presetColor])
        }
        AlertDialog(
            onDismissRequest = { showCustomPalette = false },
            title = { Text("自定义主题色") },
            text = {
                Column {
                    // 实时预览
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(swatchForHue(hue))
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "色相 ${hue.toInt()}°",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    haptic.click()
                    AppSettings.setCustomHueValue(hue)
                    showCustomPalette = false
                }) { Text("应用") }
            },
            dismissButton = {
                FilledTonalButton(onClick = {
                    haptic.click()
                    showCustomPalette = false
                }) { Text("取消") }
            }
        )
    }
}

/**
 * 「默认选项」子页：按分类列出所有带「不再提示」的弹窗。
 * 每条右侧开关：开 = 不再提示；关 = 下次触发仍弹窗（无查看/预览弹窗）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSettingsScreen(onBack: () -> Unit) {
    val haptic = rememberHapticFeedback()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("不再提示弹窗", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { haptic.click(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            for ((category, keys) in ReminderKey.entries.groupBy { it.category }) {
                SectionTitle(category)
                keys.forEachIndexed { index, key ->
                    if (index > 0) Spacer(Modifier.height(4.dp))
                    ReminderSettingRow(key = key, shape = groupShape(index, keys.size))
                }
            }
            Text(
                "开关打开表示「不再提示」；关闭后，下次触发对应场景时会重新弹出提示。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 24.dp)
            )
        }
    }
}

/**
 * 「关于」子页：不再平铺在设置里，而是独立子界面。
 * 分组顺序（鸣谢板块位于开源许可证之前）：
 * 应用（检查更新点一次查一次 / 版本）→ 鸣谢与赞助（支付宝赞助、@毛血旺o 酷安主页、
 * AI 开发伙伴）→ 开源与授权（GitHub 开源地址 / 开源许可证书本图标）。
 * 赞助点击后先弹感谢/谢绝学生的确认窗，吊起支付宝失败则单独提示并致谢。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    val haptic = rememberHapticFeedback()
    val context = LocalContext.current

    // 手动检查更新：每次点击触发一次网络检查（正在检查时忽略重复点击）
    var checking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    // 更新弹窗状态：发现新版本（4 按钮）/ 说明子弹窗 / 下载进度 / 蓝奏失败回退
    val updateFlow = rememberUpdateFlow()
    LaunchedEffect(checking) {
        if (checking) {
            val r = UpdateChecker.check(BuildConfig.VERSION_NAME)
            updateResult = r
            // 手动检查不套用「跳过此版本」：用户主动点查就给出弹窗
            if (r is UpdateCheckResult.Update) updateFlow.present(r.info)
            checking = false
        }
    }
    // 支付宝赞助相关弹窗
    var showSponsorDialog by remember { mutableStateOf(false) }
    var alipayMissing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { haptic.click(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── 应用 ──
            SectionTitle("应用")
            SettingsRowsGroup(
                listOf(
                    { s ->
                        SettingsActionRow(
                            shape = s,
                            title = "检查更新",
                            subtitle = "从 GitHub Releases 获取最新版本",
                            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailing = {
                                Text(
                                    when (val r = updateResult) {
                                        null -> if (checking) "检查中…" else ""
                                        is UpdateCheckResult.Update -> "v${r.info.version} 可用"
                                        UpdateCheckResult.UpToDate -> "已是最新版本"
                                        UpdateCheckResult.NetworkError -> "网络错误，请稍后重试"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                if (!checking) {
                                    haptic.click()
                                    checking = true
                                }
                            }
                        )
                    },
                    { s ->
                        SettingsInfoRow(shape = s, title = "版本", value = "v${BuildConfig.VERSION_NAME}")
                    }
                )
            )

            // ── 鸣谢与赞助 ──
            // 用户要求「鸣谢板块」放在「开源许可证」前面
            SectionTitle("鸣谢与赞助")
            SettingsRowsGroup(
                listOf(
                    { s ->
                        // 支付宝赞助：先弹致谢确认窗，再决定是否拉起支付宝
                        SettingsActionRow(
                            shape = s,
                            title = "支付宝赞助",
                            subtitle = "支持开发",
                            icon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                haptic.click()
                                showSponsorDialog = true
                            }
                        )
                    },
                    { s ->
                        // 酷安用户 @毛血旺o：点击跳浏览器打开其主页
                        SettingsActionRow(
                            shape = s,
                            title = "@毛血旺o",
                            subtitle = "酷安用户 · 感谢他提供的支持",
                            icon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                haptic.click()
                                openInBrowser(context, "https://www.coolapk.com/u/2910471")
                            }
                        )
                    },
                    { s ->
                        // AI IDE 与 AI 模型的鸣谢
                        SettingsCreditRow(
                            shape = s,
                            title = "Trae · GML · DeepSeek · Kimi",
                            subtitle = "感谢 AI IDE 与 AI 模型在开发中提供的协助"
                        )
                    }
                )
            )

            // ── 开源与授权 ──
            SectionTitle("开源与授权")
            SettingsRowsGroup(
                listOf(
                    { s ->
                        SettingsActionRow(
                            shape = s,
                            title = "GitHub 开源地址",
                            subtitle = GITHUB_REPO_URL.removePrefix("https://"),
                            icon = { Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                haptic.click()
                                openInBrowser(context, GITHUB_REPO_URL)
                            }
                        )
                    },
                    { s ->
                        // 开源许可证：书本图标
                        SettingsInfoRow(
                            shape = s,
                            title = "开源许可证",
                            value = "GPL-3.0",
                            leading = {
                                Icon(
                                    Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                )
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    // 手动检查发现新版本：4 按钮更新弹窗（说明子弹窗/下载进度/蓝奏失败回退统一在此渲染）
    UpdateFlowHosts(updateFlow, vibrate = { haptic.click() })

    // 支付宝赞助致谢弹窗（无「不再提示」）：感谢 + 礼貌谢绝学生
    if (showSponsorDialog) {
        AlertDialog(
            onDismissRequest = { showSponsorDialog = false },
            title = { Text("感谢你的支持") },
            text = {
                Text(
                    "真的非常感谢你愿意支持这个应用！\n\n" +
                    "如果你还是学生、或经济还没有独立，请千万不要破费——\n" +
                    "把这份心意留在心里，继续使用下去，就是对我最好的支持。\n\n" +
                    "愿意赞助的话，一块也是爱，我会把它投入到更好的维护与功能上：）"
                )
            },
            confirmButton = {
                Button(onClick = {
                    haptic.click()
                    showSponsorDialog = false
                    // 成功拉起支付宝收款码则交给支付宝；失败（含未安装）单独提示
                    if (!launchAlipay(context)) alipayMissing = true
                }) { Text("赞助") }
            },
            dismissButton = {
                FilledTonalButton(onClick = {
                    haptic.soft()
                    showSponsorDialog = false
                }) { Text("关闭") }
            }
        )
    }

    // 支付宝未能吊起：说明未安装，并再次感谢用户好意
    if (alipayMissing) {
        AlertDialog(
            onDismissRequest = { alipayMissing = false },
            title = { Text("还没有安装支付宝") },
            text = {
                Text(
                    "没有检测到支付宝，暂时无法完成赞助。\n\n" +
                    "没关系——无论怎样，都非常感谢你的好意！"
                )
            },
            confirmButton = {
                Button(onClick = {
                    haptic.click()
                    alipayMissing = false
                }) { Text("好") }
            }
        )
    }
}

/** 静态鸣谢行（非点击）：标题 + 摘要 */
@Composable
private fun SettingsCreditRow(shape: RoundedCornerShape, title: String, subtitle: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 单条「不再提示」设置行：标题/摘要 + 开关；整行可点 + 弹性动画，开关状态即时刷新 */
@Composable
private fun ReminderSettingRow(key: ReminderKey, shape: RoundedCornerShape) {
    val fb = rememberPressFeedback(hapticOnPress = false)
    val haptic = rememberHapticFeedback()
    // 本地 state 驱动 Switch 即时重绘（AppSettings 持久化值不触发重组，此前开关不刷新）
    var checked by remember(key) { mutableStateOf(AppSettings.isReminderSuppressed(key)) }
    val toggle: (Boolean) -> Unit = { v ->
        if (v) haptic.click() else haptic.soft()
        checked = v
        AppSettings.setReminderSuppressed(key, v)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .clickable(
                interactionSource = fb.interactionSource,
                indication = null
            ) { toggle(!checked) }
            .then(fb.scaleModifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(key.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                key.message.replace('\n', ' '),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        // 恢复系统默认 M3 开关样式（滑块自带涟漪与系统动效）
        Switch(
            checked = checked,
            onCheckedChange = toggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

/** 分组标题 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
    )
}

/** 分组行圆角（Kazumi SplitListGroup 风格）：
 *  组首行顶部大圆角、组尾行底部大圆角、组内行小圆角（4dp）。 */
private fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 16.dp
    val inner = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner
    )
}

/**
 * Kazumi ContentSection.group 风格的连续圆角分组：
 * 各行为同一 surfaceContainerLow 底色，行间距 4dp（露出背景把行切开），
 * 行间间隙处露出背景色，视觉上是一个被切开的圆角矩形组。
 */
@Composable
private fun SettingsRowsGroup(rows: List<@Composable (RoundedCornerShape) -> Unit>) {
    Column {
        rows.forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(4.dp))
            row(groupShape(index, rows.size))
        }
    }
}

/** 深色模式三选行：整行可点 + 弹性动画，选中项行尾显示对勾 */
@Composable
private fun ThemeModeRow(
    shape: RoundedCornerShape,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val fb = rememberPressFeedback(hapticOnPress = false)
    val haptic = rememberHapticFeedback()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .clickable(
                interactionSource = fb.interactionSource,
                indication = null
            ) {
                haptic.click()
                onClick()
            }
            .then(fb.scaleModifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 开关行：整行可点 + 行级弹性动画；Switch 使用自身默认 interactionSource，
 * 其滑块按压动画与行级弹性动画解耦，避免共享 interactionSource 时
 * 滑块在动画中断/开关自身被切换时卡在放大态无法复原。
 * 开启=清脆震动 / 关闭=柔和震动（与删除原图行一致的差异化反馈）。
 */
@Composable
private fun SettingsSwitchRow(
    shape: RoundedCornerShape,
    title: String,
    subtitle: String?,
    checked: Boolean,
    leading: (@Composable () -> Unit)? = null,
    onToggle: (Boolean) -> Unit
) {
    val fb = rememberPressFeedback(hapticOnPress = false)
    val haptic = rememberHapticFeedback()
    val toggle: (Boolean) -> Unit = { v ->
        if (v) haptic.click() else haptic.soft()
        onToggle(v)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .clickable(
                interactionSource = fb.interactionSource,
                indication = null
            ) { toggle(!checked) }
            .then(fb.scaleModifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // 恢复系统默认 M3 开关样式（与 ReminderSettingRow 一致）
        Switch(
            checked = checked,
            onCheckedChange = toggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

/** 信息展示行（版本号 / 许可证），可带前置小图标 */
@Composable
private fun SettingsInfoRow(
    shape: RoundedCornerShape,
    title: String,
    value: String,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 可点击操作行（检查更新 / GitHub 链接），带弹性动画 */
@Composable
private fun SettingsActionRow(
    shape: RoundedCornerShape,
    title: String,
    subtitle: String?,
    icon: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val fb = rememberPressFeedback(hapticOnPress = false)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .clickable(
                interactionSource = fb.interactionSource,
                indication = null
            ) { onClick() }
            .then(fb.scaleModifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** 预制主题色圆点：选中态 primary 外圈 + 对勾。
 *  外圈/色点均固定尺寸（40dp / 30dp），选中变化只动画颜色与色点缩放，
 *  不改变布局高度，避免选中态外圈展开把下半列表顶出小幅抽动。 */
@Composable
private fun PresetColorDot(
    index: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val swatch = presetSwatch(index)
    val haptic = rememberHapticFeedback()
    val ringColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(),
        label = "presetRingColor"
    )
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "presetDotScale"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 固定 40dp 外框，选中指示圈固定 40dp，仅颜色变化（不改变布局）
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .border(2.dp, ringColor, CircleShape)
            )
            // 色点：选中时在固定边界内做图形层缩放，不影响布局
            Box(
                Modifier
                    .size(30.dp)
                    .graphicsLayer {
                        scaleX = dotScale
                        scaleY = dotScale
                    }
                    .clip(CircleShape)
                    .background(swatch)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable(enabled = enabled) {
                        haptic.click()
                        onClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = presetNames[index],
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            presetNames[index],
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 自定义调色板入口（预制色第 8 位）：调色板图标，选中态与预制色点一致 */
@Composable
private fun PaletteLauncherDot(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val haptic = rememberHapticFeedback()
    val ringColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(),
        label = "paletteRingColor"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .border(2.dp, ringColor, CircleShape)
            )
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable(enabled = enabled) {
                        haptic.click()
                        onClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = "自定义调色板",
                    tint = if (selected) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "自定义",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 跳系统浏览器打开链接 */
private fun openInBrowser(context: android.content.Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {}
}

/**
 * 拉起支付宝收款码页。
 * @return true=已成功交给支付宝处理；false=未安装支付宝或拉起失败（不自动跳官网，
 *         由调用方弹窗提示并致谢）。
 * Android 11+ 需在 Manifest 的 <queries> 声明 com.eg.android.AlipayGphone，
 * 否则 resolveActivity 恒为 null。
 */
private fun launchAlipay(context: android.content.Context): Boolean {
    return try {
        val rawUrl = "https://qr.alipay.com/fkx11041w0pjbbnivcyli91"
        val encoded = java.net.URLEncoder.encode(rawUrl, "UTF-8")
        val scheme = "alipays://platformapi/startapp?appId=20000067&url=$encoded"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheme))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            false // 未安装支付宝
        } else {
            context.startActivity(intent)
            true
        }
    } catch (_: Exception) {
        false // 拉起失败
    }
}
