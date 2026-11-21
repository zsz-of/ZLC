package com.zsz.zlivephoto.ui.liquid.sponsor

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.sponsor.OfficialBuild
import com.zsz.zlivephoto.sponsor.QrCodes
import com.zsz.zlivephoto.sponsor.SponsorLinks
import com.zsz.zlivephoto.sponsor.SponsorState
import com.zsz.zlivephoto.ui.rememberHapticFeedback
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonLayout
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import io.github.neoruaa.glasmio.ui.GlasmioSectionTitle
import kotlinx.coroutines.launch

/** 赞助方式。 */
private enum class SponsorMethod(val link: String) {
    WECHAT(SponsorLinks.WECHAT),
    ALIPAY(SponsorLinks.ALIPAY)
}

/**
 * 启动提醒：每 5 次冷启动弹一次（第 1 次不弹，用户选择「不再提示」后永不弹）。
 * 由应用根调用；与其它会话型弹窗共用闸门（优先级最低）。
 */
@Composable
internal fun LiquidSponsorReminderHost(visible: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showDisclaimer by rememberSaveable { mutableStateOf(false) }
    var showMethod by rememberSaveable { mutableStateOf(false) }
    var showUnofficial by rememberSaveable { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()

    if (visible) {
        GlasmioAlertDialog(
            visible = visible,
            onDismissRequest = onDismiss,
            title = stringResource(R.string.sponsor_reminder_title),
            message = stringResource(R.string.sponsor_reminder_body),
            // 三个按钮改为纵向分层：现在赞助独占最下方一层，避免与两个取消按钮挤在一行
            buttonLayout = GlasmioAlertButtonLayout.Stacked,
            buttons = listOf(
                GlasmioAlertButtonSpec(
                    label = stringResource(R.string.sponsor_later),
                    role = GlasmioAlertButtonRole.Cancel,
                    onClick = {
                        haptic.click()
                        onDismiss()
                    }
                ),
                GlasmioAlertButtonSpec(
                    label = stringResource(R.string.sponsor_never),
                    role = GlasmioAlertButtonRole.Cancel,
                    onClick = {
                        haptic.click()
                        SponsorState.suppress(context)
                        onDismiss()
                    }
                ),
                GlasmioAlertButtonSpec(
                    label = stringResource(R.string.sponsor_now),
                    onClick = {
                        haptic.click()
                        onDismiss()
                        if (OfficialBuild.isOfficial(context)) showDisclaimer = true else showUnofficial = true
                    }
                )
            )
        )
    }

    SponsorDisclaimerHost(
        visible = showDisclaimer,
        onDismiss = { showDisclaimer = false },
        onAgree = {
            showDisclaimer = false
            showMethod = true
        }
    )
    LiquidSponsorMethodDialog(visible = showMethod, onDismiss = { showMethod = false })
    LiquidUnofficialDialog(visible = showUnofficial, onDismiss = { showUnofficial = false })
}

/**
 * 设置页「赞助」入口（一级页置顶）。
 * 非官方签名时只提示「非官方版本」，不展示收款码。
 */
@Composable
internal fun SponsorEntrySection() {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    var showDisclaimer by remember { mutableStateOf(false) }
    var showMethod by remember { mutableStateOf(false) }
    var showUnofficial by remember { mutableStateOf(false) }

    GlasmioSectionTitle(stringResource(R.string.sponsor_section_title))
    GlasmioGroupedList {
        GlasmioListRow(
            title = stringResource(R.string.sponsor_row),
            onClick = {
                haptic.click()
                if (OfficialBuild.isOfficial(context)) showDisclaimer = true else showUnofficial = true
            },
            trailing = {
                SfIcon(
                    systemName = "heart.fill",
                    contentDescription = null,
                    size = 18.dp,
                    tint = LocalGlassColors.current.destructive
                )
            }
        )
    }
    Spacer(Modifier.height(18.dp))

    // 设置页入口直接进入付款页：先过免责声明，再展示收款二维码
    SponsorDisclaimerHost(
        visible = showDisclaimer,
        onDismiss = { showDisclaimer = false },
        onAgree = {
            showDisclaimer = false
            showMethod = true
        }
    )
    LiquidSponsorMethodDialog(visible = showMethod, onDismiss = { showMethod = false })
    LiquidUnofficialDialog(visible = showUnofficial, onDismiss = { showUnofficial = false })
}

/**
 * 展示收款二维码前的免责声明：不可退款、不得以软件问题为由要求退款、法律免责。
 * 同意后才进入扫码付款页。
 */
@Composable
internal fun SponsorDisclaimerHost(visible: Boolean, onDismiss: () -> Unit, onAgree: () -> Unit) {
    val haptic = rememberHapticFeedback()
    GlasmioAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.sponsor_disclaimer_title),
        message = stringResource(R.string.sponsor_disclaimer_body),
        buttonLayout = GlasmioAlertButtonLayout.Stacked,
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.sponsor_disclaimer_cancel),
                role = GlasmioAlertButtonRole.Cancel,
                onClick = {
                    haptic.click()
                    onDismiss()
                }
            ),
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.sponsor_disclaimer_agree),
                onClick = {
                    haptic.click()
                    onAgree()
                }
            )
        )
    )
}

@Composable
internal fun LiquidUnofficialDialog(visible: Boolean, onDismiss: () -> Unit) {
    val haptic = rememberHapticFeedback()
    GlasmioAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.sponsor_unofficial_title),
        message = stringResource(R.string.sponsor_unofficial_body),
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.sponsor_close),
                role = GlasmioAlertButtonRole.Cancel,
                onClick = {
                    haptic.click()
                    onDismiss()
                }
            )
        )
    )
}

/**
 * 赞助方式弹窗：微信 / 支付宝切换，二维码由链接即时生成。
 * - 微信：只显示收款二维码；
 * - 支付宝：二维码 + 「打开支付宝」直达按钮；
 * - 两种方式都可把二维码保存到相册。
 */
@Composable
internal fun LiquidSponsorMethodDialog(visible: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    var method by rememberSaveable { mutableStateOf(SponsorMethod.WECHAT) }
    var saved by remember { mutableStateOf(false) }
    var resetJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // 横屏时二维码缩小，保证弹窗整体高度不超出屏幕
    val landscape = LocalConfiguration.current.screenHeightDp < 480
    val qrSize = if (landscape) 132.dp else 190.dp
    val qr = remember(method) { QrCodes.encode(method.link, 720) }

    GlasmioAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.sponsor_method_title),
        buttons = listOf(
            GlasmioAlertButtonSpec(
                label = stringResource(R.string.sponsor_close),
                role = GlasmioAlertButtonRole.Cancel,
                onClick = {
                    haptic.click()
                    onDismiss()
                }
            )
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = if (landscape) 250.dp else 420.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 方式选择：选中项用强调色玻璃，未选中为普通玻璃。
            // 注意：玻璃按钮内部是独立图层，必须显式提升到最上层，否则会被下方二维码盖住。
            Row(
                Modifier
                    .fillMaxWidth()
                    .zIndex(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                SponsorMethod.entries.forEach { m ->
                    GlasmioButton(
                        onClick = {
                            haptic.click()
                            method = m
                            saved = false
                            resetJob?.cancel()
                        },
                        emphasis = if (method == m) GlassEmphasis.Prominent else GlassEmphasis.Regular
                    ) {
                        Text(
                            text = stringResource(
                                if (m == SponsorMethod.WECHAT) R.string.sponsor_wechat else R.string.sponsor_alipay
                            ),
                            style = IosTypography.body
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 二维码：白底方图（扫码识别需要纯白留白）
            Box(
                Modifier
                    .size(qrSize + 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.graphics.Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.sponsor_qr_cd),
                    modifier = Modifier.size(qrSize)
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    if (method == SponsorMethod.WECHAT) R.string.sponsor_qr_hint_wechat
                    else R.string.sponsor_qr_hint_alipay
                ),
                style = IosTypography.footnote,
                color = LocalGlassColors.current.secondaryContent,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                GlasmioButton(
                    // 保存成功时按钮内容由文字换成打勾，宽度会变：
                    // 用 Spring 尺寸动画过渡，避免直接突变。
                    modifier = Modifier.animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    onClick = {
                        haptic.click()
                        val ok = QrCodes.saveToGallery(
                            context, qr,
                            if (method == SponsorMethod.WECHAT) "zlivephoto_wechat" else "zlivephoto_alipay"
                        )
                        if (ok) {
                            // 保存成功：按钮文字变为打勾（描边动画），2.5s 后恢复
                            saved = true
                            resetJob?.cancel()
                            resetJob = scope.launch {
                                kotlinx.coroutines.delay(2500)
                                saved = false
                            }
                        } else {
                            Toast.makeText(context, R.string.sponsor_save_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                        if (saved) {
                            SaveCheckMark()
                        } else {
                            Text(stringResource(R.string.sponsor_save_qr), style = IosTypography.body)
                        }
                    }
                }
                if (method == SponsorMethod.ALIPAY) {
                    GlasmioButton(
                        onClick = {
                            haptic.click()
                            openAlipay(context)
                        }
                    ) {
                        Text(stringResource(R.string.sponsor_open_alipay), style = IosTypography.body)
                    }
                }
            }
            // 与底部「关闭」按钮拉开距离，避免两个按钮贴在一起
            Spacer(Modifier.height(14.dp))
        }
    }
}

/** 保存成功后的打勾动画：路径按进度逐步描出。 */
@Composable
private fun SaveCheckMark() {
    val progress = remember { Animatable(0f) }
    val color = LocalGlassColors.current.content
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
    }
    Canvas(Modifier.size(width = 22.dp, height = 24.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.12f, size.height * 0.54f)
            lineTo(size.width * 0.40f, size.height * 0.80f)
            lineTo(size.width * 0.88f, size.height * 0.22f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * progress.value, partial, true)
        drawPath(
            path = partial,
            color = color,
            style = Stroke(
                width = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/** 唤起支付宝：使用老版本可直接识别的 startapp+appId 链接，失败则退回浏览器打开收款链接。 */
private fun openAlipay(context: android.content.Context) {
    val encoded = runCatching {
        java.net.URLEncoder.encode(SponsorLinks.ALIPAY, "UTF-8")
    }.getOrDefault(SponsorLinks.ALIPAY)
    val direct = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("alipays://platformapi/startapp?appId=20000067&url=$encoded")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val launched = runCatching { context.startActivity(direct) }.isSuccess
    if (!launched) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(SponsorLinks.ALIPAY))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
