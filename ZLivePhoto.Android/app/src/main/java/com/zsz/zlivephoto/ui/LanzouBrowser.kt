package com.zsz.zlivephoto.ui

import android.annotation.SuppressLint
import android.os.Message
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.graphicsLayer
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
 * - **不再按 URL 特征提前拦截**：蓝奏文件服务器带 acw_sc__v2 反爬，需由 WebView
 *   真正发出请求并跑完 JS 挑战拿到 Cookie。因此只通过 `setDownloadListener` 在
 *   WebView 确认「正在下载真实文件」的那一刻才回调 [onDownload]（含已解出的会话
 *   Cookie，由 AppUpdater 用同一 Cookie 续传下载），避免下载到网页挑战页。
 * - `target=_blank` 的下载窗口承接进隐藏子 WebView（挂到隐藏容器保证 JS 能跑），
 *   从子 WebView 同样拦 DownloadListener；
 * - 系统返回键 / 顶部返回箭头均关闭本弹层并回到「发现新版本」弹窗；
 * - 全屏窗口首帧即满尺寸，内容整体从右边缘整屏滑入（无窗口二次弹出/缩放伪影）；
 *   顶栏背景延伸到状态栏区域，底部提示延伸到手势条区域，避免出现色块/条纹。
 */
@Composable
internal fun LanzouBrowserDialog(
    url: String,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    vibrate: () -> Unit = {}
) {
    val context = LocalContext.current
    // 拦截到下载后只回调一次，防止主/子 WebView 的 DownloadListener 重复触发
    val handledFlag = remember { AtomicBoolean(false) }
    var title by remember { mutableStateOf("蓝奏云下载") }
    var loading by remember { mutableStateOf(true) }
    val currentOnDownload by rememberUpdatedState(onDownload)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentVibrate by rememberUpdatedState(vibrate)

    /** 供主/子 WebView 的 DownloadListener 统一调用的下载入口（须在主线程执行） */
    val fireDownload: (String) -> Unit = { direct ->
        if (handledFlag.compareAndSet(false, true)) {
            currentOnDownload(direct)
        }
    }

    // target=_blank 新窗口的子 WebView 统一挂到这个隐藏容器上（仅保证进程内真正
    // 跑页面/JS 以解出反爬 Cookie），并随弹层关闭统一销毁。
    val childHost = remember { FrameLayout(context) }
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

                // 蓝奏下载按钮以 target=_blank 开新窗口：承接进隐藏子 WebView，让其
                // 真正加载下载页/解出 Cookie，命中真实下载时由子 WebView 的
                // DownloadListener 上报，URL 特征匹配一律不再提前拦截。
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
                        settings.domStorageEnabled = true
                        webChromeClient = WebChromeClient()
                        // 子 WebView 正常加载目标内容（含反爬挑战），不做 URL 拦截
                        setDownloadListener { u, _, _, _, _ -> fireDownload(u) }
                    }
                    childHost.addView(
                        child,
                        FrameLayout.LayoutParams(1, 1)
                    )
                    children += child
                    val transport = resultMsg.obj as? WebView.WebViewTransport
                        ?: return false
                    transport.webView = child
                    resultMsg.sendToTarget()
                    return true
                }
            }

            // 兜底：主框架直接命中真实文件下载（Content-Disposition 附件等）时上报
            setDownloadListener { u, _, _, _, _ -> fireDownload(u) }

            loadUrl(url)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            children.forEach { child ->
                runCatching { child.stopLoading() }
                runCatching { (child.parent as? ViewGroup)?.removeView(child) }
                runCatching { child.destroy() }
            }
            children.clear()
            runCatching { childHost.removeAllViews() }
            runCatching { web.stopLoading() }
            runCatching { web.destroy() }
        }
    }

    // 从右边缘整屏滑入：窗口首帧即为全屏，纯内容平移，避免 Dialog 窗口初始
    // 空内容导致「二次弹出/缩放」的方向假象。
    val enterOffset = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        enterOffset.animateTo(0f, tween(340, easing = FancyEasing))
    }

    Dialog(
        onDismissRequest = {
            // 系统返回键：关闭内置浏览器，回到更新主弹窗
            currentVibrate()
            currentOnDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false // 内容铺满含状态栏/手势条区域，消除顶部色块
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 滑动留白处用同底色铺底（不露出刺眼的遮罩色）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
            // 滑入的整页内容
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = size.width * enterOffset.value
                    }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // ── 顶部栏：返回（关闭）/ 标题（背景延伸覆盖状态栏区域）──
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp),
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
                                // 与左侧返回按钮对称占位，标题保持居中；右上角不再放 ×
                                Spacer(Modifier.width(48.dp))
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
                        // ── 网页内容 + 隐藏的子 WebView 挂载容器 ──
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            AndroidView(
                                factory = { web },
                                modifier = Modifier.fillMaxSize()
                            )
                            // 仅作为 target=_blank 子 WebView 的进程内挂载点（不可见）
                            AndroidView(
                                factory = { childHost },
                                modifier = Modifier.size(1.dp)
                            )
                        }
                        // ── 底部操作提示（背景延伸覆盖手势条区域）──
                        Text(
                            text = "检测到下载后本页会自动关闭并继续安装；若长时间无反应，请改用「从 GitHub 下载」。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
