package com.zsz.zlivephoto.flavor

/**
 * go flavor（Android 6+）能力表：
 * - 不含 ffmpeg 转码器，批量并发 1
 * - 恒用 Material 3 UI（不依赖 Glasmio）
 */
object FlavorCapabilities {
    const val isGo: Boolean = true
    const val maxParallelism: Int = 1
    const val hasEmbeddedFfmpeg: Boolean = false
    const val liquidGlassSupported: Boolean = false
}
