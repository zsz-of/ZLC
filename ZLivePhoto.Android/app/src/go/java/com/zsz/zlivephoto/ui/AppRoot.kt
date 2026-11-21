package com.zsz.zlivephoto.ui

import androidx.compose.runtime.Composable

/** go 轻量版入口：恒为 Material 3 外壳。 */
@Composable
fun AppRoot(host: AppHost) {
    // 应用语言宿主：切换语言不重建 Activity，原地渐变 / 镜像切换（见 LanguageHost）
    LanguageHost {
        Md3AppRoot(host)
    }
}
