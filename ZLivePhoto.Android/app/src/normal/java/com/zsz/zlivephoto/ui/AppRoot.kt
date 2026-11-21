package com.zsz.zlivephoto.ui

import androidx.compose.runtime.Composable
import com.zsz.zlivephoto.ui.liquid.LiquidAppRoot

/**
 * normal 版入口：始终使用 Glasmio 液态玻璃外壳（不再提供 Material 3 回退）。
 *
 * 标准版已移除 MD3 支持：旧版的「关闭液态玻璃界面」「崩溃后回退 MD3」入口一并删除，
 * 界面只有一套玻璃实现，避免两套外观与主题逻辑并存。
 */
@Composable
fun AppRoot(host: AppHost) {
    // 应用语言宿主：切换语言不重建 Activity，原地渐变切换（见 LanguageHost）
    LanguageHost {
        LiquidAppRoot(host)
    }
}
