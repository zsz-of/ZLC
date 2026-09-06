package com.zsz.zlivephoto.ui

import android.annotation.SuppressLint
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zsz.zlivephoto.core.AppUpdater
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 内置浏览器（蓝奏云下载页）全屏弹层：
 * - 用桌面 UA 加载蓝奏分享页，让用户自行点击页面中的下载按钮；
 * - 通过「URL 特征 / 新窗口 / DownloadListener」三层拦截拿到最终下载直链后，
 *   立即回调 [onDownload]（调用方随即关页并转入既有下载/解压/安装进度流程）；
 * - 系统返回键 / 顶部返回箭头均关闭本弹层并回到「发现新版本」弹窗；
 *   顶部线性进度条反映页面加载进度；进入有从右滑入动画。
 */
@Composable
internal fun LanzouBrowserDialog(
    url: String,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    vibrate: () -> Unit = {}
) {
    val context = LocalContext.current
    // 拦截到下载后只回调一次，防止 DownloadListener 与 URL 拦截双重触发重复下载
    val handledFlag = remember { AtomicBoolean(false) }
    var title by remember { mutableStateOf("蓝奏云下载") }
    var loading by remember { mutableStateOf(true) }
    val currentOnDownload by rememberUpdatedState(onDownload)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentVibrate by rememberUpdatedState(vibrate)

    /** 供 WebView 各层回调统一调用的下载入口（须在主线程执行） */
    val fireDownload: (String) -> Unit = { direct ->
        if (handledFlag.compareAndSet(false, true)) {
            currentOnDownload(direct)
        }
    }

    /** 判断是否为蓝奏云文件下载直链（dom + /file/ + url 结构，或常规压缩包/安装包后缀） */
    val looksLikeDownload: (String) -> Boolean = { u ->
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            false
        } else {
            val low = u.lowercase()
            low.contains("/file/") ||
                low.endsWith(".zip") || low.endsWith(".apk") ||
                low.endsWith(".rar") || low.endsWith(".7z") || low.endsWith(".exe")
        }
    }

    // 所有新窗口（target=_blank 弹出的下载页）使用的 WebView，统一在弹层关闭时销毁
    val children = remember { mutableListOf<WebView>() }

    val web = remember {
        @SuppressLint("SetJavaScriptEnabled")
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // 桌面 UA：蓝奏对移动 UA 只返回无下载框的 WAP 页
            settings.userAgentString = AppUpdater.DESKTOP_UA

            webViewClient = object : WebViewClient() {
                // API 24+：主框架加载新地址时先判断是否下载直链
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val u = request?.url?.toString() ?: return false
                    if (looksLikeDownload(u)) {
                        fireDownload(u)
                        return true
                    }
                    return false
                }

                @Suppress("DEPRECATION")
                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val u = url ?: return false
                    if (looksLikeDownload(u)) {
                        fireDownload(u)
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    loading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loading = false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loading = newProgress < 100
                }

                override fun onReceivedTitle(view: WebView?, t: String?) {
                    if (!t.isNullOrBlank()) title = t
                }

                // 蓝奏下载按钮以 target=_blank 开新窗口：承接进隐藏 WebView 并从中拦截直链
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message
                ): Boolean {
                    if (handledFlag.get()) return false
                    val base = view ?: return false
                    val child = WebView(base.context).apply {
                        settings.javaScriptEnabled = true
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                v: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val u = request?.url?.toString() ?: return false
                                if (looksLikeDownload(u)) {
                                    fireDownload(u)
                                    return true
                                }
                                // 非下载的新窗口一律拦截，避免弹出广告/外链页面
                                return true
                            }

                            @Suppress("DEPRECATION")
                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(v: WebView?, url: String?): Boolean {
                                val u = url ?: return false
                                if (looksLikeDownload(u)) {
                                    fireDownload(u)
                                    return true
                                }
                                return true
                            }
                        }
                        setDownloadListener { u, _, _, _, _ -> fireDownload(u) }
                    }
                    children += child
                    val transport = resultMsg.obj as? WebView.WebViewTransport
                        ?: return false
                    transport.webView = child
                    resultMsg.sendToTarget()
                    return true
                }
            }

            // 兜底：直链命中 WebView 自身下载逻辑（Content-Disposition 附件等）时也能拦到
            setDownloadListener { u, _, _, _, _ -> fireDownload(u) }

            loadUrl(url)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            children.forEach { child ->
                runCatching { child.stopLoading() }
                runCatching { child.destroy() }
            }
            children.clear()
            runCatching { web.stopLoading() }
            runCatching { web.destroy() }
        }
    }

    Dialog(
        onDismissRequest = {
            // 系统返回键 / 点击弹层外区域（若有）：关闭内置浏览器，回到更新主弹窗
            currentVibrate()
            currentOnDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(
            visible = shown,
            enter = slideInHorizontally(tween(340, easing = FancyEasing)) { it } +
                fadeIn(tween(240))
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── 顶部栏：返回（关闭）/ 标题 / 关闭 ──
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .height(52.dp)
                    ) {
                        IconButton(onClick = {
                            currentVibrate()
                            currentOnDismiss()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "请点击页面中的下载按钮，开始下载后自动返回本应用",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            currentVibrate()
                            currentOnDismiss()
                        }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    // ── 加载进度条（不足 2dp 高度时隐藏）──
                    if (loading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    } else {
                        Spacer(Modifier.height(3.dp))
                    }
                    // ── 网页内容 ──
                    AndroidView(
                        factory = { web },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    // ── 底部操作提示 ──
                    Text(
                        text = "检测到下载后本页会自动关闭并继续安装；若长时间无反应，请改用「从 GitHub 下载」。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
