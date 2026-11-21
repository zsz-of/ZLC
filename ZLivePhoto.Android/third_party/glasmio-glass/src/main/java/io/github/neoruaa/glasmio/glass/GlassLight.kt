package io.github.neoruaa.glasmio.glass

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 全局光照方向（角度，单位：度）。
 *
 * 材质的高光位置由它决定：宿主在应用根注入一个跟随机身姿态的 [State]
 * （旋转矢量传感器取 roll），所有玻璃表面（按钮 / 开关 / 导航栏 / 弹窗）
 * 的高光会随手机一起转动。默认 0°（顶部打光）。
 *
 * 只读 State 的 `value`；宿主应按 3° 左右量化后再写入，避免高频重组。
 */
val LocalGlassLightAngle: ProvidableCompositionLocal<State<Float>> =
    staticCompositionLocalOf { mutableStateOf(0f) }
