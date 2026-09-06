package com.zsz.zlivephoto.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
 * - **单 WebView 方案**：`setSupportMultipleWindows(false)` 使页面里 target=_blank / window.open
 *   的下载弹窗一律留在当前 WebView 内加载（实测蓝奏下载 = 分享页 iframe → 点击弹新窗口
 *   developer2.lanrar.com 中间页 → JS 自动导航到 CDN 直链），避免弹窗逃逸到系统浏览器；
 * - 命中「真实文件直链」特征（webgetstore/dmpdmp 文件域、.apk/.zip/.7z 后缀）的导航
 *   立即转交下载流程并关页；其余 http(s) 导航一律返回 false 留在视图内加载；
 * - `setDownloadListener` 兜底：真正进入文件下载（Content-Disposition）那一刻也回调
 *   [onDownload]（含已解出的会话 Cookie，AppUpdater 用同一 Cookie 续传下载）；
 * - 不再按 URL 特征提前拦截 acw 挑战页（需由 WebView 真正跑完 JS 才能拿到 Cookie）；
 * - 系统返回键 / 顶部返回箭头均关闭本弹层并回到「发现新版本」弹窗；
 * - 全屏窗口首帧即满尺寸，内容整体从右边缘整屏滑入；顶栏背景延伸到状态栏区域，
 *   底部提示延伸到手势条区域，避免出现色块/条纹。
 */
@Composable
internal fun LanzouBrowserDialog(
    url: String,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    vibrate: () -> Unit = {}
) {
    val context = LocalContext.current
    // 拦截到下载后只回调一次，防止 shouldOverrideUrlLoading / DownloadListener 重复触发
    val handledFlag = remember { AtomicBoolean(false) }
    var title by remember { mutableStateOf("蓝奏云下载") }
    var loading by remember { mutableStateOf(true) }
    val currentOnDownload by rememberUpdatedState(onDownload)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentVibrate by rememberUpdatedState(vibrate)

    /** 命中真实文件直链后的统一下载入口（须在主线程调用） */
    val fireDownload: (String) -> Unit = { direct ->
        if (handledFlag.compareAndSet(false, true)) {
            currentOnDownload(direct)
        }
    }

    /**
     * 判断 URL 是否是「真实文件直链」而不是中间解析页：
     * - 蓝奏文件服务器域名（webgetstore.com / dmpdmp.com），路径形如 /YYYY/MM/DD/{32hex}.zip?sg=...
     * - 或直接以 .apk/.zip/.7z 结尾（兼容其它静态直链）
     * 注意：developer2.lanrar.com 这类中间页必须放行加载，由页面 JS 解析出直链后触发下载。
     */
    fun looksLikeDirectFile(u: String?): Boolean {
        if (u.isNullOrBlank()) return false
        val lower = u.lowercase()
        if (lower.endsWith(".apk") || lower.endsWith(".zip") || lower.endsWith(".7z")) return true
        val host = runCatching { java.net.URI(u).host?.lowercase() }.getOrNull() ?: return false
        return host.endsWith("webgetstore.com") || host.endsWith("dmpdmp.com")
    }

    val web = remember {
        @SuppressLint("SetJavaScriptEnabled")
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // 关键：关闭多窗口。蓝奏下载的 target=_blank 弹窗因此留在本视图内加载，
            // 其后再跳 CDN 直链都经过本视图的 shouldOverrideUrlLoading/DownloadListener，
            // 从机制上杜绝「跳到系统浏览器下载」。
            settings.setSupportMultipleWindows(false)
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

                // 兜底一：任何导航命中真实文件直链 → 立即转入下载流程，不再继续加载
                @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val u = url
                    if (u != null && looksLikeDirectFile(u)) {
                        fireDownload(u)
                        return true
                    }
                    return false
                }

                // 兜底二（新版 API）：同上；其余 URL 返回 false 留在本视图加载，绝不外跳
                @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val u = request?.url?.toString()
                    if (u != null && looksLikeDirectFile(u)) {
                        fireDownload(u)
                        return true
                    }
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loading = newProgress < 100
                }

                override fun onReceivedTitle(view: WebView?, t: String?) {
                    if (!t.isNullOrBlank()) title = t
                }
            }

            // 兜底三：WebView 真正开始下载文件（Content-Disposition 等）时上报最终 URL。
            // 蓝奏 CDN 直链带短时效签名（sg/e 参数），捕获到 URL 后应立即转交下载。
            setDownloadListener { u, _, _, _, _ -> fireDownload(u) }

            loadUrl(url)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
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
                        // ── 网页内容 ──
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            AndroidView(
                                factory = { web },
                                modifier = Modifier.fillMaxSize()
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
