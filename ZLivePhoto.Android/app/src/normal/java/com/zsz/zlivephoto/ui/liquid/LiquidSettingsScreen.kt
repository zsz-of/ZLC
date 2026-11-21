package com.zsz.zlivephoto.ui.liquid

import androidx.compose.runtime.Composable
import com.zsz.zlivephoto.ui.liquid.settings.LiquidSettingsHost

/**
 * Glasmio（液态玻璃 / iOS 风格）版设置页。
 *
 * 具体分类导航与各分类内容位于 [com.zsz.zlivephoto.ui.liquid.settings] 包：
 * - 窄屏：分类首页 → 分类内容 → 提醒子页；
 * - 横屏（≥ 720dp）：左侧分类栏 + 右侧内容双栏。
 *
 * @param onBack 返回主页
 * @param onSubpageChanged 二级页开合状态回传，宿主据此决定系统返回键是否交给本页处理
 * @param onRequestUpdate 请求宿主在应用根层级展示更新弹窗（弹窗不渲染在设置页内部）
 */
@Composable
fun LiquidSettingsScreen(
    onBack: () -> Unit,
    onSubpageChanged: (Boolean) -> Unit = {},
    onRequestUpdate: () -> Unit = {}
) {
    LiquidSettingsHost(
        onBack = onBack,
        onSubpageChanged = onSubpageChanged,
        onRequestUpdate = onRequestUpdate
    )
}
