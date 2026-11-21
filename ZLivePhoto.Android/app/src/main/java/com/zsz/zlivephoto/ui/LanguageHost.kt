package com.zsz.zlivephoto.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.zsz.zlivephoto.core.AppLanguage
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 应用语言宿主：不重建 Activity，原地切换界面语言。
 *
 * 切换流程（用户要求）：
 * 1. 选择语言后等待菜单收起动画（约 220ms）；
 * 2. 整页文字淡出 → 切换语言 → 淡入（切换 LTR/RTL 时也不做镜像动画）；
 * 3. 页面滚动位置 / 选中状态 / 玻璃背景全部保持，不重新加载页面。
 *
 * 实现方式：用目标语言的 [Configuration] 创建 Context，注入
 * [LocalContext] / [LocalResources] / [LocalConfiguration] / [LocalLayoutDirection]，
 * `stringResource` 等资源读取随之切换。
 */
@Composable
fun LanguageHost(content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val initialTag = remember { AppLanguage.currentTag(baseContext) }
    val targetTag = AppLanguage.runtimeTag ?: initialTag

    // 当前“已生效”的语言：动画中点才真正切换，保证文字在完全淡出时换掉
    var appliedTag by remember { mutableStateOf(initialTag) }

    val contentAlpha = remember { Animatable(1f) }

    LaunchedEffect(targetTag) {
        if (targetTag == appliedTag) return@LaunchedEffect
        // 等下拉菜单收回后再开始渐变（用户要求）
        delay(220)
        // 只做淡出 → 换语言 → 淡入；LTR/RTL 切换也不再镜像
        contentAlpha.animateTo(0f, tween(durationMillis = 100))
        appliedTag = targetTag
        contentAlpha.animateTo(1f, tween(durationMillis = 180))
    }

    val locale = remember(appliedTag, baseConfig) { resolveLocale(appliedTag, baseConfig) }
    val localizedConfig = remember(appliedTag, baseConfig) {
        Configuration(baseConfig).apply {
            setLocale(locale)
            setLayoutDirection(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(locale))
            }
        }
    }
    val localizedContext = remember(appliedTag, baseContext) {
        // 用 ContextWrapper 包回原 Activity：只替换 Resources（语言），
        // 其余（startActivity / 系统服务 / 主题 / findActivity）仍委托给 Activity，
        // 避免「从非 Activity Context 启动 Activity 必须 NEW_TASK」之类的回归。
        val localizedBase = baseContext.createConfigurationContext(localizedConfig)
        LocalizedContextWrapper(baseContext, localizedBase.resources)
    }

    SideEffect { Locale.setDefault(locale) }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalResources provides localizedContext.resources,
        LocalConfiguration provides localizedConfig,
        LocalLayoutDirection provides if (localizedConfig.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha.value }
        ) {
            content()
        }
    }
}

/** 空串 = 跟随系统：用设备（而非应用包装后）的 Configuration，避免切回「跟随系统」时取到旧语言。 */
private fun resolveLocale(tag: String, baseConfig: Configuration): Locale {
    if (tag.isEmpty()) {
        val system = android.content.res.Resources.getSystem().configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            system.locales[0] ?: Locale.getDefault()
        } else {
            @Suppress("DEPRECATION")
            system.locale ?: Locale.getDefault()
        }
    }
    return Locale.forLanguageTag(tag)
}

/** 只替换 Resources 的 Context 包装（保留 Activity 的全部上下文能力）。 */
private class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: android.content.res.Resources
) : android.content.ContextWrapper(base) {
    override fun getResources(): android.content.res.Resources = localizedResources
    override fun getAssets(): android.content.res.AssetManager = localizedResources.assets
}
