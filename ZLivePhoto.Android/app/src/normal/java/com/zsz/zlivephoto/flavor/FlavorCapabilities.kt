package com.zsz.zlivephoto.flavor

import android.os.Build

/**
 * normal flavor（Android 12+）能力表：
 * - 内置 ffmpeg 转码器（arm64），批量并发 4
 * - 设备支持 AGSL 运行时着色器（Android 13+）时启用 Glasmio 液态玻璃，
 *   Android 12 无 AGSL，自动回落 Material 3
 */
object FlavorCapabilities {
    const val isGo: Boolean = false
    const val maxParallelism: Int = 4
    const val hasEmbeddedFfmpeg: Boolean = true

    /** 设备是否支持液态玻璃着色器（AGSL 需 Android 13+） */
    val liquidGlassSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
