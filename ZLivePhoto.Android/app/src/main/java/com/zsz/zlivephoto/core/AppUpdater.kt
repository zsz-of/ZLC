package com.zsz.zlivephoto.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.json.JSONObject

/**
 * 应用内一键下载更新：
 * - GitHub release 资产直链（*.apk）直接下载；
 * - 蓝奏云：由 UI 层用内置 WebView 打开分享页，用户自行点击下载并拦截到最终直链，
 *   再把直链交给 [downloadToCache] 下载（本类不再自行解析蓝奏页面）；
 * - 下载/解压全程使用非 `.apk` 临时文件名，规整完成后才落成 `update.apk`，
 *   因此下载过程中不会产生可被误识别/误安装的半成品 APK。
 * - 下载协程可取消：取消时立即中断并清理缓存目录。
 *
 * 安装权限（Android 8+「安装未知应用」）与安装动作分离：
 * 调用方先用 [needsInstallPermission] 判断，若需要则通过 [installPermissionIntent]
 * 引导用户授权；授权返回后（权限已授予）直接 [installApk]，无需重新下载。
 */
internal class UpdaterException(message: String) : Exception(message)

internal object AppUpdater {
    /** 当前正在进行的下载连接（下载协程注册，取消/关闭浏览器时由 [abortActiveDownload] 断开，
     *  使阻塞在网络读上的线程立刻返回，避免协程悬挂到读超时才结束） */
    @Volatile
    private var activeConn: HttpURLConnection? = null

    /**
     * 立即中断当前正在进行的下载（若有）：断开活跃的 HTTP 连接。
     * 配合协程 [Job.cancel] 使用——先取消任务再断开连接，阻塞中的
     * read 会立刻抛异常，经 ensureActive 转成 CancellationException 静默退出。
     */
    fun abortActiveDownload() {
        val c = activeConn ?: return
        activeConn = null
        runCatching { c.disconnect() }
    }

    /**
     * 下载与内置浏览器统一使用的桌面 Chrome UA：
     * 蓝奏分享页对移动 UA 返回无下载框的 WAP 页，桌面 UA 才能命中可下载的 PC 版页面。
     */
    internal const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    /** 下载/解压的临时缓存目录（cache 下，启动自动清理覆盖） */
    internal fun cacheDir(context: Context): File = File(context.cacheDir, "zlc_update")

    // ---------- 蓝奏云直链解析（原生 HTTP，替代内置浏览器） ----------

    /**
     * 解析蓝奏云分享页得到最终可下载的 CDN 直链。
     *
     * 完整链路（已命令行实测还原，与开源解析器 WhY15w/lanzou 一致）：
     *   1. 首次 GET 分享页可能触发 acw_sc__v2 反爬（返回含 `arg1='...'` 的挑战页），
     *      用 [acwScV2] 解出 cookie 后重试；
     *   2. 从分享页 HTML 提取 `<iframe src="/fn?..."` 下载帧地址；
     *   3. GET `/fn` 帧页，提取 `ajaxfile.php?file=<id>`、`ajaxdata`、`wp_sign`；
     *   4. POST `ajaxfile.php`（action=downprocess 等）得到 `dom` + `url`；
     *   5. GET `dom + "/file/" + url`，**Referer 必须是 `/fn` 帧页地址**（而非分享页），
     *      并附带随机 X-FORWARDED-FOR/CLIENT-IP 头规避「网络异常需验证」页 → 302 到最终直链。
     *
     * @return 最终 CDN 直链（带 sg/e 签名的 .zip/.apk 地址）
     * @throws UpdaterException 解析失败（链接失效 / 需密码 / 验证页）
     */
    suspend fun resolveLanzouDirectLink(shareUrl: String): String = withContext(Dispatchers.IO) {
        val client = LanzouHttpClient()
        try {
            val share = shareUrl.trim()
            // 1. 首次访问，自动处理 acw_sc__v2 反爬
            var shareHtml = client.get(share, referer = "https://${hostOf(share)}/")
            if (shareHtml.isAcwChallenge()) {
                val arg1 = shareHtml.acwArg1() ?: throw UpdaterException("蓝奏云反爬校验失败")
                client.setCookie("acw_sc__v2", acwScV2(arg1))
                shareHtml = client.get(share, referer = "https://${hostOf(share)}/")
            }

            // 2. 提取 iframe 下载帧
            val iframeSrc = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(shareHtml)?.groupValues?.get(1)
                ?: throw UpdaterException("无法解析蓝奏云下载页（未找到下载帧）")
            val fnUrl = if (iframeSrc.startsWith("http")) iframeSrc
            else "https://${hostOf(share)}$iframeSrc"

            // 3. GET /fn 帧页，提取 ajax 参数
            val fnHtml = client.get(fnUrl, referer = share)
            val fileId = Regex("""ajaxfile\.php\?file=(\d+)""").find(fnHtml)?.groupValues?.get(1)
                ?: throw UpdaterException("无法解析蓝奏云文件标识")
            val ajaxdata = Regex("""var\s+ajaxdata\s*=\s*'([^']*)'""").find(fnHtml)?.groupValues?.get(1)
                ?: throw UpdaterException("无法解析蓝奏云签名")
            val wpSign = Regex("""var\s+wp_sign\s*=\s*'([^']*)'""").find(fnHtml)?.groupValues?.get(1)
                ?: throw UpdaterException("无法解析蓝奏云签名")

            // 4. POST ajaxfile.php
            val form = mapOf(
                "action" to "downprocess",
                "websignkey" to ajaxdata,
                "signs" to ajaxdata,
                "sign" to wpSign,
                "websign" to "",
                "kd" to "1",
                "ves" to "1",
            )
            val ajaxResp = client.post(
                "https://${hostOf(share)}/ajaxfile.php?file=$fileId",
                form = form,
                referer = fnUrl
            )
            val json = JSONObject(ajaxResp)
            if (json.optInt("zt", 0) != 1) {
                throw UpdaterException(json.optString("inf", "蓝奏云下载链接解析失败"))
            }
            val dom = json.optString("dom")
            val token = json.optString("url")
            if (dom.isEmpty() || token.isEmpty()) {
                throw UpdaterException("蓝奏云返回空下载地址")
            }
            val dispatchUrl = "$dom/file/$token"

            // 5. 请求分发链接拿最终直链：Referer 必须是 /fn 帧页，并附带随机 IP 头
            val direct = client.location(dispatchUrl, referer = fnUrl)
                ?: throw UpdaterException("蓝奏云下载地址已失效，请重新获取")
            direct
        } finally {
            client.close()
        }
    }

    /** 蓝奏云 acw_sc__v2 反爬 cookie 解密（对 arg1 做定序重排 + 与固定掩码异或） */
    internal fun acwScV2(arg1: String): String {
        val pos = intArrayOf(
            15, 35, 29, 24, 33, 16, 1, 38, 10, 9, 19, 31, 40, 27, 22, 23, 25, 13,
            6, 11, 39, 18, 20, 8, 14, 21, 32, 26, 2, 30, 7, 4, 17, 5, 3, 28, 34, 37, 12, 36
        )
        val mask = "3000176000856006061501533003690027800375"
        val out = CharArray(40)
        for (i in arg1.indices) {
            val ch = arg1[i]
            for (j in pos.indices) {
                if (pos[j] == i + 1) out[j] = ch
            }
        }
        val arg2 = String(out)
        val sb = StringBuilder()
        var i = 0
        while (i < arg2.length && i + 1 < mask.length) {
            val h = arg2.substring(i, i + 2).toInt(16)
            val m = mask.substring(i, i + 2).toInt(16)
            sb.append(String.format("%02x", h xor m))
            i += 2
        }
        return sb.toString()
    }

    private fun hostOf(url: String): String = URL(url).host

    private fun String.isAcwChallenge(): Boolean = contains("acw_sc__v2")

    private fun String.acwArg1(): String? =
        Regex("""arg1='([0-9A-F]+)'""", RegexOption.IGNORE_CASE)
            .find(this)?.groupValues?.get(1)

    /**
     * 蓝奏云原生 HTTP 客户端：手动管理会话 Cookie、支持 GET/POST、手动跟随 302 取 Location。
     * 全部阻塞 IO，仅由 [resolveLanzouDirectLink] 在 Dispatchers.IO 上调用。
     */
    private class LanzouHttpClient {
        private val cookies = LinkedHashMap<String, String>()

        fun setCookie(name: String, value: String) {
            cookies[name] = value
        }

        fun get(url: String, referer: String): String {
            val conn = open(url, "GET", referer, null)
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw UpdaterException("请求失败（HTTP $code）")
                readCookies(conn)
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }

        fun post(url: String, form: Map<String, String>, referer: String): String {
            val body = form.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            val conn = open(url, "POST", referer, body)
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw UpdaterException("请求失败（HTTP $code）")
                readCookies(conn)
                return conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }

        /** 手动跟随重定向，返回最终 Location（若无则返回 null）；不下载内容体 */
        fun location(url: String, referer: String): String? {
            val conn = open(url, "GET", referer, null)
            try {
                conn.instanceFollowRedirects = false
                val code = conn.responseCode
                readCookies(conn)
                if (code in 300..399) return conn.getHeaderField("Location")
                return null
            } finally {
                conn.disconnect()
            }
        }

        private fun open(url: String, method: String, referer: String, formBody: String?): HttpURLConnection {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.requestMethod = method
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", DESKTOP_UA)
            conn.setRequestProperty("Referer", referer)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            val ip = randomIp()
            conn.setRequestProperty("X-FORWARDED-FOR", ip)
            conn.setRequestProperty("CLIENT-IP", ip)
            if (cookies.isNotEmpty()) {
                conn.setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }
            if (formBody != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(formBody.toByteArray(Charsets.UTF_8)) }
            }
            return conn
        }

        private fun readCookies(conn: HttpURLConnection) {
            val setCookies = conn.headerFields["Set-Cookie"] ?: return
            for (sc in setCookies) {
                val pair = sc.substringBefore(';').trim()
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    cookies[pair.substring(0, idx).trim()] = pair.substring(idx + 1).trim()
                }
            }
        }

        private fun randomIp(): String {
            val a = intArrayOf(218, 218, 66, 66, 218, 218, 60, 60, 202, 204, 66, 66, 66, 59, 61, 60, 222, 221, 66, 59, 60, 60, 66, 218, 218, 62, 63, 64, 66, 66, 122, 211)
            val b = a[kotlin.random.Random.nextInt(a.size)]
            val r = kotlin.random.Random
            return "$b.${r.nextInt(256)}.${r.nextInt(256)}.${r.nextInt(256)}"
        }

        fun close() {
            cookies.clear()
        }
    }

    // ---------- 通用下载 ----------

    /**
     * 下载 URL 到 cache/zlc_update 目录，边下边回调进度。
     * 写入临时文件 `download.bin`（非 .apk 扩展名）；下载被取消时自动删除半成品。
     * @param onProgress (已下载字节, 总字节)；总字节未知为 -1
     */
    suspend fun downloadToCache(
        context: Context,
        url: String,
        referer: String? = null,
        onProgress: (Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = cacheDir(context).apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val raw = File(dir, "download.bin")

        var code = -1
        var conn: HttpURLConnection? = null
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            // 注册为当前活跃连接：取消下载/关闭浏览器时 abortActiveDownload() 断开它，
            // 使阻塞中的 connect/read 立刻抛异常返回，不悬挂到读超时
            activeConn = c
            conn = c
            c.apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", DESKTOP_UA)
                if (referer != null) setRequestProperty("Referer", referer)
                // 蓝奏下载直链一般需要 WebView 已解出（acw_sc__v2 等）的会话 Cookie，
                // 缺失时服务端会返回网页挑战页而不是安装包（旧版因此装到损坏文件报「解析包出错」）
                val cookie = try {
                    CookieManager.getInstance().getCookie(url)
                } catch (_: Exception) {
                    null
                }
                if (!cookie.isNullOrEmpty()) setRequestProperty("Cookie", cookie)
                instanceFollowRedirects = true
            }
            code = c.responseCode
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive() // 已取消：转 CancellationException 静默退出
            throw UpdaterException("无法连接下载服务器：${e.message}")
        }
        if (code !in 200..299) {
            if (activeConn === conn) activeConn = null
            runCatching { conn?.disconnect() }
            throw UpdaterException("下载失败（HTTP $code）")
        }
        val http = conn ?: throw UpdaterException("无法建立下载连接")
        try {
            val total = http.contentLengthLong
            val input = http.inputStream
            FileOutputStream(raw).use { fos ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    ensureActive() // 取消时立即中断
                    val n = input.read(buf)
                    if (n < 0) break
                    fos.write(buf, 0, n)
                    done += n
                    onProgress(done, if (total > 0) total else -1L)
                }
            }
            if (!raw.exists() || raw.length() <= 0L) throw UpdaterException("下载内容为空")
            // 服务端给了明确长度但实际收到的字节数不一致 = 中途被掐断/注入，必须在此拦截，
            // 否则截断的 APK 交给系统安装器只会得到笼统的「解析包出现问题」。
            if (total > 0L && raw.length() != total) {
                runCatching { raw.delete() }
                throw UpdaterException(
                    "下载不完整（已获取 ${raw.length()} / 共 $total 字节），请重新下载"
                )
            }
            raw
        } catch (e: CancellationException) {
            runCatching { raw.delete() }
            throw e
        } catch (e: Exception) {
            runCatching { raw.delete() }
            currentCoroutineContext().ensureActive() // 已取消：不把中断误报成下载失败
            throw if (e is UpdaterException) e else UpdaterException("下载中断：${e.message}")
        } finally {
            if (activeConn === conn) activeConn = null
            runCatching { conn?.disconnect() }
        }
    }

    /**
     * 把下载产物规整为可直接安装的 APK 文件：
     * 若文件是 zip 压缩包（内部含 .apk）则解压；否则自身即 APK（GitHub 直链场景）。
     * 规整过程中目标文件叫 `update.part`，**解压/写入完整结束后**才重命名为 `update.apk`，
     * 保证任何时刻缓存里都不存在未完成的 `.apk`。
     * 若内容不是 ZIP/APK（如蓝奏反爬返回的网页挑战页），抛出 [UpdaterException]
     * 而不是把损坏文件交给系统安装器（避免「解析包出错」）。
     * 返回最终 APK 文件；失败抛 [UpdaterException]。
     */
    suspend fun resolveApk(context: Context, downloaded: File): File = withContext(Dispatchers.IO) {
        // 只有头部是 ZIP/APK 魔数（PK）才可能是安装包或压缩包
        if (hasZipMagic(downloaded)) {
            val apkFromZip = try { extractApkFromZip(downloaded) } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (apkFromZip != null) {
                requireApk(apkFromZip)
                downloaded.delete()
                return@withContext apkFromZip
            }
        }
        // 非 zip（GitHub 直链 apk / 蓝奏未解出的裸 apk）：重命名为 .apk 便于识别；
        // 内容不是 APK 时直接报错，不触发系统安装器
        val apk = if (!downloaded.name.endsWith(".apk", ignoreCase = true)) {
            val f = File(downloaded.parentFile, "update.apk")
            if (downloaded.renameTo(f)) f else downloaded
        } else {
            downloaded
        }
        requireApk(apk)
        apk
    }

    /** 文件头是否为 ZIP/APK 魔数（PK） */
    private fun hasZipMagic(f: File): Boolean = runCatching {
        f.inputStream().use { ins ->
            val head = ByteArray(2)
            ins.read(head) == 2 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()
        }
    }.getOrDefault(false)

    /** 安装前内容校验：非 APK/ZIP 魔数或结构损坏（截断/缺关键条目）直接抛错，
     *  避免系统安装器报「解析包出错」这种无法区分原因的笼统错误。 */
    private fun requireApk(f: File) {
        if (!hasZipMagic(f)) {
            throw UpdaterException(
                "下载到的不是有效的安装包（可能是网页或下载链接已失效），请重新下载或改用 GitHub 下载。"
            )
        }
        // 能读通 ZIP 中央目录 + 存在 Android 必备条目才算完整 APK：
        // 截断/半成品包在 ZipFile 打开时即抛异常，不会走到系统安装器。
        try {
            ZipFile(f).use { zf ->
                var hasManifest = false
                var hasClasses = false
                val e = zf.entries()
                while (e.hasMoreElements()) {
                    val n = e.nextElement().name
                    if (n == "AndroidManifest.xml") hasManifest = true
                    if (n.startsWith("classes") && n.endsWith(".dex")) hasClasses = true
                }
                if (!hasManifest || !hasClasses) {
                    throw UpdaterException("安装包内容不完整，请重新下载或改用 GitHub 下载。")
                }
            }
        } catch (e: UpdaterException) {
            throw e
        } catch (_: Exception) {
            throw UpdaterException("安装包无法解析（可能下载不完整），请重新下载或改用 GitHub 下载。")
        }
    }

    /**
     * 尝试把 zip 中的第一个 .apk 解压到同目录；不是压缩包 / 无 apk 则返回 null。
     * 解压写入 `update.part`，完成后 rename 为 `update.apk`。
     */
    private suspend fun extractApkFromZip(zipFile: File): File? {
        val zin = ZipInputStream(zipFile.inputStream().buffered())
        val outDir = zipFile.parentFile ?: return null
        var target: File? = null
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zin.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                    // 蓝奏云一般把 APK 直接放在 zip 根目录；防多层目录用扁平名
                    val part = File(outDir, "update.part")
                    FileOutputStream(part).use { out ->
                        val buf = ByteArray(128 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = zin.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                    }
                    val apk = File(outDir, "update.apk")
                    target = if (part.renameTo(apk)) apk else part
                    break
                }
                zin.closeEntry()
            }
        } finally {
            runCatching { zin.close() }
        }
        return target
    }

    // ---------- 安装 ----------

    /** Android 8+ 且本应用未获得「安装未知应用」授权时为 true */
    fun needsInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()

    /** 跳转到「允许安装未知应用」的系统设置页的 Intent（不含 NEW_TASK，便于 Launcher 接收返回结果） */
    fun installPermissionIntent(context: Context): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    /**
     * 调起系统安装器安装 APK（FileProvider 授权）。
     * 调用前请确保 [needsInstallPermission] 为 false（已由调用方在授权返回后复核）。
     *
     * 新旧 Android 差异适配（本方法 + [installPermissionIntent] 共同覆盖）：
     * - Android 6（API 23，Go 版最低）：FileProvider 自 API 21 起可用，统一走 content://，
     *   无需区分 file:// 与 content:// 两套逻辑；
     * - Android 7+：禁止 file:// 暴露，必须 FileProvider + FLAG_GRANT_READ_URI_PERMISSION；
     * - Android 8+：应用级「安装未知应用」授权（canRequestPackageInstalls），已单列引导；
     * - Android 11+（targetSdk 30+）：包可见性限制，需在 Manifest 用 <queries> 声明对
     *   「查看 APK 安装包」意图的可见性（见 AndroidManifest.xml），否则可能解析不到安装器；
     * - 主流/国产 ROM 偶发对一次性的 flag 授权不敏感：这里再对解析到的安装器包名
     *   显式 grantUriPermission，确保安装器能读完整 APK，避免误报「解析包出现问题」。
     *
     * @return null 表示已成功拉起；否则返回给用户的失败说明。
     */
    fun installApk(context: Context, apkFile: File): String? {
        if (needsInstallPermission(context)) return "尚未获得「安装未知应用」权限"
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // 显式把读权限授予能处理该意图的系统安装器（含国产 ROM 的自定义安装器）
        val installers = try {
            context.packageManager.queryIntentActivities(intent, 0)
        } catch (_: Exception) {
            emptyList()
        }
        for (ri in installers) {
            try {
                context.grantUriPermission(
                    ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // 个别 ROM 不允许手动授，忽略，FLAG 授权通常仍有效
            }
        }
        return try {
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            "无法调起系统安装器（${e.message}），请到文件管理器手动打开安装包安装"
        }
    }
}
