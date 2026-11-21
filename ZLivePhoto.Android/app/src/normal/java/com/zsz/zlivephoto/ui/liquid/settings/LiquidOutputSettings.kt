package com.zsz.zlivephoto.ui.liquid.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.FfmpegAddon
import com.zsz.zlivephoto.flavor.FlavorCapabilities
import com.zsz.zlivephoto.ui.AppSettings
import com.zsz.zlivephoto.ui.liquid.common.LiquidDropdownRow
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import io.github.neoruaa.glasmio.ui.GlasmioSegmentedControl
import io.github.neoruaa.glasmio.ui.GlasmioSlider

/** 输出：文件与文件夹层级。 */
@Composable
internal fun OutputSettingsSection() {
    var preserve by remember { mutableStateOf(AppSettings.preserveFolders) }
    SettingsSection(stringResource(R.string.settings_section_output)) {
        SwitchRow(
            title = stringResource(R.string.settings_preserve_folders_title),
            checked = preserve,
            onToggle = {
                preserve = it
                AppSettings.setPreserveFolderStructure(it)
            }
        )
    }
}

/**
 * 内置转码器（ffmpeg）：
 * - go 轻量版不含内置转码器，整个区块隐藏；
 * - 转码方式默认「仅重封装容器」；
 * - 编码器 / 压缩预设统一为下拉行，参数仅「重新编码」时生效。
 */
@Composable
internal fun TranscodeSettingsSection() {
    if (FlavorCapabilities.isGo) return

    var mode by remember { mutableStateOf(FfmpegAddon.mode) }
    var codec by remember { mutableStateOf(FfmpegAddon.codec) }
    var preset by remember { mutableStateOf(FfmpegAddon.preset) }
    var crf by remember { mutableIntStateOf(FfmpegAddon.crf) }
    // 按下 CRF 区域时锁住外层滚动：拖动滑块期间列表位置必须固定
    val scrollLock = LocalSettingsScrollLock.current

    val encoding = mode == FfmpegAddon.MODE_ENCODE

    SettingsSection(stringResource(R.string.settings_section_transcode)) {
        GlasmioListRow(
            title = stringResource(R.string.settings_transcoder_title),
            detail = stringResource(R.string.settings_transcoder_value, FfmpegAddon.BUNDLED_VERSION)
        )
        // 转码方式与其它设置项同处一个分组卡片，避免裸控件直接贴在页面背景上
        val modes = listOf(FfmpegAddon.MODE_ENCODE, FfmpegAddon.MODE_REMUX, FfmpegAddon.MODE_OFF)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            GlasmioSegmentedControl(
                items = listOf(
                    modes[0] to stringResource(R.string.settings_transcode_mode_encode),
                    modes[1] to stringResource(R.string.settings_transcode_mode_remux),
                    modes[2] to stringResource(R.string.settings_transcode_mode_off)
                ),
                selected = mode,
                onSelected = {
                    mode = it
                    FfmpegAddon.updateMode(it)
                }
            )
        }
        // 编码器 / 压缩预设并入「转码器」分组；禁用时由行自身降透明度，不再整块 alpha
        val codecLabels = mapOf(
            "h265" to stringResource(R.string.settings_encoder_h265),
            "h264" to stringResource(R.string.settings_encoder_h264)
        )
        LiquidDropdownRow(
            title = stringResource(R.string.settings_encoder_title),
            selected = codec,
            options = listOf("h265", "h264"),
            label = { codecLabels[it] ?: it },
            enabled = encoding,
            onSelected = {
                codec = it
                FfmpegAddon.updateCodec(it)
            }
        )
        LiquidDropdownRow(
            title = stringResource(R.string.settings_compress_preset_title),
            selected = preset,
            options = FfmpegAddon.PRESETS,
            label = { it },
            enabled = encoding,
            onSelected = {
                preset = it
                FfmpegAddon.updatePreset(it)
            }
        )
        // 质量 / CRF 作为转码分组内的最后一行：标题 + 当前值 + 滑块（不再单独成组）
        // 分隔线用与库内 GlasmioListRow 相同的 0.5dp 分隔色实现，
        // 不使用 Material 的 HorizontalDivider（会带自己的默认厚度/边距，行间距与组内不一致）。
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(0.5.dp)
                .background(LocalGlassColors.current.separator)
        )
        val colors = LocalGlassColors.current
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                // 只观察不消费：滑块仍拿到完整手势，但按下期间外层纵向滚动被禁用，
                // 斜向拖动不会带着整页滚（此前列表位置会突然跳走）。
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        scrollLock.locked = true
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.none { it.pressed }) break
                            }
                        } finally {
                            scrollLock.locked = false
                        }
                    }
                }
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.settings_crf_title),
                    style = IosTypography.body,
                    // 禁用态只在此处降一次透明度（滑块自身另有禁用态样式）
                    color = colors.content.copy(alpha = if (encoding) 1f else 0.4f)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.settings_crf_value, crf),
                    style = IosTypography.body,
                    color = colors.secondaryContent.copy(alpha = if (encoding) 1f else 0.4f)
                )
            }
            GlasmioSlider(
                value = crf.toFloat(),
                onValueChange = { value -> crf = value.toInt() },
                valueRange = 10f..30f,
                enabled = encoding,
                onValueChangeFinished = { FfmpegAddon.updateCrf(crf) }
            )
        }
    }
}
