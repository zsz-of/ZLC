package com.zsz.zlivephoto.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.Onboarding
import com.zsz.zlivephoto.core.findActivityOrNull

/**
 * 首次启动的使用协议页（MD3 版，Go 轻量版与 MD3 外观使用）。
 * 只有「同意并继续」才能进入应用。
 */
@Composable
internal fun Md3AgreementGate() {
    val context = LocalContext.current
    var agreed by remember { mutableStateOf(Onboarding.isAgreed(context)) }

    if (agreed) return

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Text(
                    text = stringResource(R.string.agreement_title),
                    style = MaterialTheme.typography.headlineSmall
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
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        Onboarding.agree(context)
                        agreed = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.agreement_agree))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { context.findActivityOrNull()?.finish() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.agreement_decline))
                }
            }
        }
    }
}
