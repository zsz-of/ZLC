package com.zsz.zlivephoto.ui.glass.catalog.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** 缩放位图到指定尺寸（用于亮度采样等低分辨率缩略）。 */
fun ImageBitmap.scale(width: Int, height: Int): ImageBitmap =
    Bitmap.createScaledBitmap(asAndroidBitmap(), width, height, true).asImageBitmap()
