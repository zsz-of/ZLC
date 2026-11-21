package com.zsz.zlivephoto.ui.liquid.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.ui.liquid.LiquidImportModeDialog
import com.zsz.zlivephoto.ui.liquid.LiquidRiskAckDialog
import com.zsz.zlivephoto.ui.liquid.common.LiquidProgressBar
import com.zsz.zlivephoto.ui.liquid.sponsor.LiquidSponsorMethodDialog
import com.zsz.zlivephoto.ui.liquid.sponsor.LiquidUnofficialDialog
import com.zsz.zlivephoto.ui.liquid.sponsor.SponsorDisclaimerHost
import com.zsz.zlivephoto.ui.picker.ImportMode
import com.zsz.zlivephoto.ui.rememberHapticFeedback
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import io.github.neoruaa.glasmio.ui.GlasmioSwitch

/** 测试页里可手动弹出的弹窗。 */
private enum class DialogDemo {
    AGREEMENT,
    SPONSOR_REMINDER,
    SPONSOR_DISCLAIMER,
    SPONSOR_METHOD,
    SPONSOR_UNOFFICIAL,
    PERMISSION,
    SETTINGS,
    ALL_FILES,
    CONFLICT,
    IMPORT_MODE,
    RISK,
    UPDATE,
    DOWNLOAD,
    ERROR_REPORT,
    GO_REMINDER
}

/**
 * 弹窗测试页（关于 → 弹窗测试）。
 *
 * 只用于验证弹窗外观与交互：所有条目都在本地弹出一个无真实副作用的示例弹窗，
 * 不出现在正式使用流程里。赞助 / 导入方式 / 风险确认复用真实弹窗组件。
 */
@Composable
internal fun LiquidDialogTestPage(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    val haptic = rememberHapticFeedback()
    var demo by remember { mutableStateOf<DialogDemo?>(null) }
    var conflictAlways by remember { mutableStateOf(false) }
    var sponsorDisclaimer by remember { mutableStateOf(false) }
    var sponsorMethod by remember { mutableStateOf(false) }
    var sponsorUnofficial by remember { mutableStateOf(false) }
    var importMode by remember { mutableStateOf(false) }
    var risk by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlasmioBackdropHost(sampledColor = GlasmioTheme.colors.groupedBackground) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlasmioButton(onClick = {
                            haptic.click()
                            onDismiss()
                        }) {
                            Text(stringResource(R.string.settings_btn_close), style = IosTypography.body)
                        }
                        Spacer(Modifier.fillMaxWidth(0.04f))
                        Text(
                            text = stringResource(R.string.settings_dialog_test),
                            style = IosTypography.title3,
                            color = LocalGlassColors.current.content
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        SettingsSection {
                            val rows = listOf(
                                R.string.agreement_title to DialogDemo.AGREEMENT,
                                R.string.sponsor_reminder_title to DialogDemo.SPONSOR_REMINDER,
                                R.string.sponsor_disclaimer_title to DialogDemo.SPONSOR_DISCLAIMER,
                                R.string.sponsor_method_title to DialogDemo.SPONSOR_METHOD,
                                R.string.sponsor_unofficial_title to DialogDemo.SPONSOR_UNOFFICIAL,
                                R.string.main_dialog_permission_title to DialogDemo.PERMISSION,
                                R.string.main_dialog_settings_title to DialogDemo.SETTINGS,
                                R.string.main_dialog_allfiles_title_location to DialogDemo.ALL_FILES,
                                R.string.main_dialog_conflict_title to DialogDemo.CONFLICT,
                                R.string.screen_import_mode_title to DialogDemo.IMPORT_MODE,
                                R.string.conv_risk_ack_title to DialogDemo.RISK,
                                R.string.update_title_new_version to DialogDemo.UPDATE,
                                R.string.update_downloading_title to DialogDemo.DOWNLOAD,
                                R.string.error_report_title to DialogDemo.ERROR_REPORT,
                                R.string.update_go_remind_title to DialogDemo.GO_REMINDER
                            )
                            rows.forEach { (titleRes, item) ->
                                GlasmioListRow(
                                    title = stringResource(titleRes),
                                    onClick = {
                                        haptic.click()
                                        when (item) {
                                            DialogDemo.SPONSOR_DISCLAIMER -> sponsorDisclaimer = true
                                            DialogDemo.SPONSOR_METHOD -> sponsorMethod = true
                                            DialogDemo.SPONSOR_UNOFFICIAL -> sponsorUnofficial = true
                                            DialogDemo.IMPORT_MODE -> importMode = true
                                            DialogDemo.RISK -> risk = true
                                            else -> demo = item
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }

                // ---- 示例弹窗 ----
                GlasmioAlertDialog(
                    visible = demo == DialogDemo.AGREEMENT,
                    onDismissRequest = { demo = null },
                    title = stringResource(R.string.agreement_title),
                    message = stringResource(R.string.agreement_body).take(500) + "…",
                    buttons = listOf(
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.agreement_agree),
                            onClick = { demo = null }
                        )
                    )
                )

                GlasmioAlertDialog(
                    visible = demo == DialogDemo.SPONSOR_REMINDER,
                    onDismissRequest = { demo = null },
                    title = stringResource(R.string.sponsor_reminder_title),
                    message = stringResource(R.string.sponsor_reminder_body),
                    buttons = listOf(
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.sponsor_later),
                            role = GlasmioAlertButtonRole.Cancel,
                            onClick = { demo = null }
                        ),
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.sponsor_never),
                            role = GlasmioAlertButtonRole.Cancel,
                            onClick = { demo = null }
                        ),
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.sponsor_now),
                            onClick = {
                                demo = null
                                sponsorDisclaimer = true
                            }
                        )
                    ),
                    buttonLayout = io.github.neoruaa.glasmio.ui.GlasmioAlertButtonLayout.Stacked
                )

                GlasmioAlertDialog(
                    visible = demo == DialogDemo.PERMISSION,
                    onDismissRequest = { demo = null },
                    title = stringResource(R.string.main_dialog_permission_title),
                    message = stringResource(R.string.main_dialog_permission_message),
                    buttons = listOf(
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_deny),
                            role = GlasmioAlertButtonRole.Cancel,
                            onClick = { demo = null }
                        ),
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_grant),
                            onClick = { demo = null }
                        )
                    )
                )

                GlasmioAlertDialog(
                    visible = demo == DialogDemo.SETTINGS,
                    onDismissRequest = { demo = null },
                    title = stringResource(R.string.main_dialog_settings_title),
                    message = stringResource(R.string.main_dialog_settings_message),
                    buttons = listOf(
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_cancel),
                            role = GlasmioAlertButtonRole.Cancel,
                            onClick = { demo = null }
                        ),
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_go_settings),
                            onClick = { demo = null }
                        )
                    )
                )

                GlasmioAlertDialog(
                    visible = demo == DialogDemo.ALL_FILES,
                    onDismissRequest = { demo = null },
                    title = stringResource(R.string.main_dialog_allfiles_title_location),
                    message = stringResource(R.string.main_dialog_allfiles_location_message),
                    buttons = listOf(
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_not_now),
                            role = GlasmioAlertButtonRole.Cancel,
                            onClick = { demo = null }
                        ),
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_grant_access),
                            onClick = { demo = null }
                        )
                    )
                )

                GlasmioAlertDialog(
                    visible = demo == DialogDemo.CONFLICT,
                    onDismissRequest = { demo = null },
                    title = stringResource(R.string.main_dialog_conflict_title),
                    message = stringResource(R.string.main_dialog_conflict_message, "IMG_0001.JPG"),
                    buttonLayout = io.github.neoruaa.glasmio.ui.GlasmioAlertButtonLayout.Stacked,
                    buttons = listOf(
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_overwrite),
                            onClick = { demo = null }
                        ),
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_auto_rename),
                            onClick = { demo = null }
                        ),
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_skip),
                            role = GlasmioAlertButtonRole.Cancel,
                            onClick = { demo = null }
                        )
                    )
                ) {
                    // 交互示例：开关
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.main_label_conflict_always),
                            style = IosTypography.body,
                            color = LocalGlassColors.current.content,
                            modifier = Modifier.weight(1f)
                        )
                        GlasmioSwitch(
                            checked = conflictAlways,
                            onCheckedChange = { conflictAlways = it }
                        )
                    }
                }

                GlasmioAlertDialog(
                    visible = demo == DialogDemo.UPDATE,
                    onDismissRequest = { demo = null },
                    title = stringResource(R.string.update_title_new_version),
                    message = stringResource(R.string.update_reinstall_body),
                    buttons = listOf(
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.update_later),
                            role = GlasmioAlertButtonRole.Cancel,
                            onClick = { demo = null }
                        ),
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.settings_reinstall_title),
                            onClick = { demo = null }
                        )
                    )
                )

                GlasmioAlertDialog(
                    visible = demo == DialogDemo.DOWNLOAD,
                    onDismissRequest = { demo = null },
                    title = stringResource(R.string.update_downloading_title),
                    message = "42% · 1.2 MB/s",
                    buttons = listOf(
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.main_btn_cancel),
                            role = GlasmioAlertButtonRole.Cancel,
                            onClick = { demo = null }
                        )
                    )
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        LiquidProgressBar(progress = 0.42f, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(14.dp))
                    }
                }

                GlasmioAlertDialog(
                    visible = demo == DialogDemo.ERROR_REPORT,
                    onDismissRequest = { demo = null },
                    title = stringResource(R.string.error_report_title),
                    message = stringResource(R.string.error_report_message, "示例错误：转换失败"),
                    buttons = listOf(
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.error_report_later),
                            role = GlasmioAlertButtonRole.Cancel,
                            onClick = { demo = null }
                        ),
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.error_report_submit),
                            onClick = { demo = null }
                        )
                    )
                )

                GlasmioAlertDialog(
                    visible = demo == DialogDemo.GO_REMINDER,
                    onDismissRequest = { demo = null },
                    title = stringResource(R.string.update_go_remind_title),
                    message = stringResource(R.string.update_go_remind_body),
                    buttons = listOf(
                        GlasmioAlertButtonSpec(
                            label = stringResource(R.string.update_go_continue),
                            onClick = { demo = null }
                        )
                    )
                )

                // ---- 真实弹窗组件 ----
                SponsorDisclaimerHost(
                    visible = sponsorDisclaimer,
                    onDismiss = { sponsorDisclaimer = false },
                    onAgree = {
                        sponsorDisclaimer = false
                        sponsorMethod = true
                    }
                )
                LiquidSponsorMethodDialog(
                    visible = sponsorMethod,
                    onDismiss = { sponsorMethod = false }
                )
                LiquidUnofficialDialog(
                    visible = sponsorUnofficial,
                    onDismiss = { sponsorUnofficial = false }
                )
                LiquidImportModeDialog(
                    visible = importMode,
                    onDismiss = { importMode = false },
                    onPick = { _: ImportMode -> importMode = false }
                )
                LiquidRiskAckDialog(
                    visible = risk,
                    replaceOriginal = true,
                    onDismiss = { risk = false },
                    onConfirm = { risk = false }
                )
            }
        }
    }
}
