package com.zsz.zlivephoto.ui.glass.catalog.utils

import androidx.compose.runtime.withFrameNanos

/** 等待下一帧（Android 实现，等价于 KMP 的 awaitFrame）。 */
suspend fun awaitFrame() {
    withFrameNanos { }
}
