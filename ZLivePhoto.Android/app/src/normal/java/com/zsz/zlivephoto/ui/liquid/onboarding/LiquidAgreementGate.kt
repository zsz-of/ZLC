package com.zsz.zlivephoto.ui.liquid.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.Onboarding
import com.zsz.zlivephoto.core.findActivityOrNull
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.glass.GlassEmphasis
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioButton

/**
 * 首次启动的使用协议页（液态玻璃版）。
 *
 * 覆盖全屏并禁止返回键 / 点击外部关闭：只有「同意并继续」才能进入应用，
 * 「不同意并退出」会结束 Activity。
 */
@Composable
internal fun LiquidAgreementGate() {
    val context = LocalContext.current
    var agreed by remember { mutableStateOf(Onboarding.isAgreed(context)) }
    var showDecline by remember { mutableStateOf(false) }

    if (agreed) return

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        GlasmioBackdropHost(sampledColor = GlasmioTheme.colors.groupedBackground) {
            val colors = LocalGlassColors.current
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.agreement_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.content
                    )
                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = stringResource(R.string.agreement_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.content
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    GlasmioButton(
                        onClick = {
                            Onboarding.agree(context)
                            agreed = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        emphasis = GlassEmphasis.Prominent
                    ) {
                        Text(
                            text = stringResource(R.string.agreement_agree),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    GlasmioButton(
                        onClick = { showDecline = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.agreement_decline),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                GlasmioAlertDialog(
                    visible = showDecline,
                    onDismissRequest = { showDecline = false },
                    title = stringResource(R.string.agreement_decline_title),
                    message = stringResource(R.string.agreement_decline_body),
                    buttons = listOf(
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.agreement_agree),
                            onClick = {
                                showDecline = false
                                Onboarding.agree(context)
                                agreed = true
                            }
                        ),
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.agreement_decline),
                            role = GlasmioAlertButtonRole.Cancel,
                            onClick = {
                                showDecline = false
                                context.findActivityOrNull()?.finish()
                            }
                        )
                    ),
                    buttonLayout = io.github.neoruaa.glasmio.ui.GlasmioAlertButtonLayout.Stacked
                )
            }
        }
    }
}
