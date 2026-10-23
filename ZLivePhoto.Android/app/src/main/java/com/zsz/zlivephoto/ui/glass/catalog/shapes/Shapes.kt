package com.zsz.zlivephoto.ui.glass.catalog.shapes

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * 胶囊形状：50% 圆角（矩形时为胶囊，正方形时为圆）。
 * 等价于 Kyant0 `com.kyant.shapes.Capsule`（本项目未引入该独立 shapes 库，故本地实现）。
 */
fun Capsule(): Shape = RoundedCornerShape(percent = 50)

/** 圆角矩形（等价于 Kyant0 `com.kyant.shapes.RoundedRectangle`）。 */
fun RoundedRectangle(cornerRadius: Dp): Shape = RoundedCornerShape(cornerRadius)
