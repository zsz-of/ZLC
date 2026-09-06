package com.zsz.zlivephoto.ui

import android.annotation.SuppressLint
import android.os.Message
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
 * - **主/子 WebView 双捕获（3.1.0 实测可靠的方案）**：
 *   1. 蓝奏下载按钮 href 直接指向 `developer2.lanrar.com/file/?<token>` 分发 URL
 *      （URL 路径含 `/file/`），点击以 target=_blank / window.open 弹新窗 →
 *      `onCreateWindow` 把新窗口承接进隐藏子 WebView；
 *   2. 主、子 WebView 的 `shouldOverrideUrlLoading` 用**宽泛判定**（含 `/file/` 或
 *      .zip/.apk/.rar/.7z/.exe 后缀）识别下载直链，命中即回调 [onDownload] 关页转
 *      应用内下载，早于 302 跳转、不依赖 DownloadListener；
 *   3. 子 WebView 未命中时一律拦截（return true）避免弹窗逃逸/加载广告外链；
 *   4. `setDownloadListener` 兜底：真正进入文件下载（Content-Disposition 附件）时也上报。
 * - 拦截到下载后只回调一次（AtomicBoolean 去重），避免主/子 WebView 重复触发；
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
    // 拦截到下载后只回调一次，防止主/子 WebView 的多个监听器重复触发
    val handledFlag = remember { AtomicBoolean(false) }
    var title by remember { mutableStateOf("蓝奏云下载") }
    var loading by remember { mutableStateOf(true) }
    val currentOnDownload by rememberUpdatedState(onDownload)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentVibrate by rememberUpdatedState(vibrate)

    /** 命中真实文件直链/真正开始下载后的统一下载入口（须在主线程调用） */
    val fireDownload: (String) -> Unit = { direct ->
        if (handledFlag.compareAndSet(false, true)) {
            currentOnDownload(direct)
        }
    }

    /**
     * 判断 URL 是否为蓝奏云文件下载直链（3.1.0 实测可靠的宽泛判定）：
     * - 蓝奏下载按钮 href 直接指向 `developer2.lanrar.com/file/?<token>` 这类**分发 URL**，
     *   URL 路径里含 `/file/`，服务端对其直接 302 到带签名 CDN 直链。因此在按钮被点击、
     *   新窗口首次导航到该分发 URL 时就能命中拦截（早于 302、不依赖 DownloadListener），
     *   这是 3.1.0 下载捕获正常的关键；后续版本误改为 host 后缀匹配，漏掉了分发 URL，
     *   只能等 302 到 CDN 或 DownloadListener 触发，二者在部分机型不可靠才导致卡页面。
     * - 同时兼容常规压缩包/安装包后缀（.zip/.apk/.rar/.7z/.exe）等静态直链。
     */
    fun looksLikeDirectFile(u: String?): Boolean {
        if (u.isNullOrBlank()) return false
        if (!u.startsWith("http://") && !u.startsWith("https://")) return false
        val low = u.lowercase()
        return low.contains("/file/") ||
            low.endsWith(".zip") || low.endsWith(".apk") ||
            low.endsWith(".rar") || low.endsWith(".7z") || low.endsWith(".exe")
    }

    /**
     * 生成带「下载捕获」的 WebViewClient：
     * - [isMain] 时同步驱动页面加载进度/标题所在的上层 loading 状态；
     * - 命中下载直链的导航立即回调 [fire]（返回 true 终止继续加载）；
     * - 未命中时：主 WebView 放行（return false 留在本视图加载分享页/iframe），
     *   子 WebView 一律拦截（return true，避免 target=_blank 弹窗逃逸到系统浏览器
     *   或加载广告/外链页面）——与 3.1.0 行为一致。
     */
    fun makeDownloadAwareClient(isMain: Boolean): WebViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            if (isMain) loading = true
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (isMain) loading = false
        }

        // 兜底一：任何导航命中下载直链 → 立即转入下载流程，不再继续加载
        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            val u = url
            if (u != null && looksLikeDirectFile(u)) {
                fireDownload(u)
                return true
            }
            return !isMain
        }

        // 兜底二（新版 API）：同上
        @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val u = request?.url?.toString()
            if (u != null && looksLikeDirectFile(u)) {
                fireDownload(u)
                return true
            }
            return !isMain
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
            // 关键：开启多窗口。蓝奏下载按钮以 target=_blank / window.open 打开中间下载页，
            // 在 onCreateWindow 中把新窗口承接进隐藏子 WebView 加载（让其跑 JS 解出 Cookie 并
            // 触发真实下载），避免 ROM 默认把新窗口交给系统浏览器或静默下载而应用毫无感知。
            settings.setSupportMultipleWindows(true)
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // 桌面 UA：蓝奏对移动 UA 只返回无下载框的 WAP 页
            settings.userAgentString = AppUpdater.DESKTOP_UA

            webViewClient = makeDownloadAwareClient(isMain = true)

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loading = newProgress < 100
                }

                override fun onReceivedTitle(view: WebView?, t: String?) {
                    if (!t.isNullOrBlank()) title = t
                }

                // 蓝奏下载按钮以 target=_blank 开新窗口：承接进隐藏子 WebView，让其
                // 真正加载下载中间页/解出 Cookie；子 WebView 的 WebViewClient 与
                // DownloadListener 同样挂载「下载捕获」，命中真实直链立即回调并关页。
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
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(true)
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.userAgentString = AppUpdater.DESKTOP_UA
                        webViewClient = makeDownloadAwareClient(isMain = false)
                        webChromeClient = WebChromeClient()
                        // 兜底：子视图真正进入文件下载（Content-Disposition 等）时上报最终 URL
                        setDownloadListener { u, _, _, _, _ -> fireDownload(u) }
                    }
                    val transport = resultMsg.obj as? WebView.WebViewTransport
                        ?: return false
                    childHost.addView(child, FrameLayout.LayoutParams(1, 1))
                    children += child
                    transport.webView = child
                    resultMsg.sendToTarget()
                    return true
                }
            }

            // 兜底：主框架真正开始下载文件（Content-Disposition 等）时上报最终 URL。
            // 蓝奏 CDN 直链带短时效签名（sg/e 参数），捕获到 URL 后应立即转交下载。
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
