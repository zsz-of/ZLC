package com.zsz.zlivephoto.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipInputStream

/**
 * 应用内一键下载更新：
 * - GitHub release 资产直链（*.apk）直接下载；
 * - 蓝奏云分享页 → 解析出直链 → 下载（通常为 zip 压缩包）→ 解压出 APK。
 * 蓝奏云直链解析为「尽力而为」实现（页面结构可能随蓝奏改版变化），
 * 解析失败会抛 [UpdaterException]，由 UI 层回退询问是否改用 GitHub 下载。
 */
internal class UpdaterException(message: String) : Throwable(message)

internal object AppUpdater {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    // ---------- 通用下载 ----------

    /**
     * 下载 URL 到 cache/zlc_update 目录，边下边回调进度。
     * @param onProgress (已下载字节, 总字节)；总字节未知为 -1
     */
    suspend fun downloadToCache(
        context: Context,
        url: String,
        referer: String? = null,
        onProgress: (Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "zlc_update").apply { mkdirs() }
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val raw = File(dir, "download.bin")

        var code = -1
        val conn: HttpURLConnection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", UA)
                if (referer != null) setRequestProperty("Referer", referer)
                instanceFollowRedirects = true
            }.also { code = it.responseCode }
        } catch (e: Exception) {
            throw UpdaterException("无法连接下载服务器：${e.message}")
        }
        if (code !in 200..299) {
            conn.disconnect()
            throw UpdaterException("下载失败（HTTP $code）")
        }
        try {
            val total = conn.contentLengthLong
            val input = conn.inputStream
            FileOutputStream(raw).use { fos ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    fos.write(buf, 0, n)
                    done += n
                    onProgress(done, if (total > 0) total else -1L)
                }
            }
            if (!raw.exists() || raw.length() <= 0L) throw UpdaterException("下载内容为空")
            raw
        } catch (e: Exception) {
            runCatching { raw.delete() }
            throw if (e is UpdaterException) e else UpdaterException("下载中断：${e.message}")
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /**
     * 把下载产物规整为可直接安装的 APK 文件：
     * 若文件是 zip 压缩包（内部含 .apk）则解压；否则自身即 APK（GitHub 直链场景）。
     * 返回最终 APK 文件；失败抛 [UpdaterException]。
     */
    suspend fun resolveApk(context: Context, downloaded: File): File = withContext(Dispatchers.IO) {
        val apkFromZip = try { extractApkFromZip(downloaded) } catch (_: Exception) { null }
        if (apkFromZip != null) {
            downloaded.delete()
            return@withContext apkFromZip
        }
        // 不是压缩包（本身是 apk）：重命名为 .apk 便于识别
        if (!downloaded.name.endsWith(".apk", ignoreCase = true)) {
            val apk = File(downloaded.parentFile, "update.apk")
            if (downloaded.renameTo(apk)) return@withContext apk
            return@withContext downloaded
        }
        downloaded
    }

    /** 尝试把 zip 中的第一个 .apk 解压到同目录；不是压缩包 / 无 apk 则返回 null。 */
    private fun extractApkFromZip(zipFile: File): File? {
        val zin = ZipInputStream(zipFile.inputStream().buffered())
        val outDir = zipFile.parentFile ?: return null
        var target: File? = null
        try {
            while (true) {
                val entry = zin.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                    // 蓝奏云一般把 APK 直接放在 zip 根目录；防多层目录用扁平名
                    val apk = File(outDir, "update.apk")
                    FileOutputStream(apk).use { out -> zin.copyTo(out, 128 * 1024) }
                    target = apk
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

    /**
     * 调起系统安装器安装 APK（FileProvider 授权）。
     * @return null 表示已成功拉起；否则返回给用户的失败说明。
     * Android 8+ 需要本应用获得「安装未知应用」授权，未授权时返回提示。
     */
    fun installApk(context: Context, apkFile: File): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pm = context.packageManager
            if (!pm.canRequestPackageInstalls()) {
                // 带用户去授权页
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                return "请先允许「安装未知应用」，然后再点一次下载更新"
            }
        }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            "无法调起系统安装器（${e.message}）"
        }
    }

    // ---------- 蓝奏云直链解析（尽力而为） ----------

    /**
     * 解析蓝奏云分享页 → 最终直链。
     * @throws UpdaterException 页面结构不支持 / 网络异常 / 需要密码时抛错（由 UI 回退 GitHub）。
     */
    suspend fun lanzouDirectUrl(pageUrl: String, password: String? = null): String =
        withContext(Dispatchers.IO) {
            val home = httpGetText(pageUrl, null)
            if (home == null) throw UpdaterException("无法访问蓝奏云页面")
            if (home.contains("文件取消") || home.contains("已删除") ||
                home.contains("链接不存在") || home.contains("文件已删除")
            ) throw UpdaterException("该蓝奏云文件已失效")

            if (password.isNullOrEmpty() && needsPassword(home)) {
                throw UpdaterException("该蓝奏云分享需要访问密码，请在上方输入密码")
            }

            val frameSrc = iframeSrc(home)
                ?: throw UpdaterException("蓝奏云页面结构无法解析（找不到下载框）")
            val frameUrl = resolveUrl(pageUrl, frameSrc)
            val origin = URL(frameUrl).let { "${it.protocol}://${it.authority}" }

            val frame = httpGetText(frameUrl, pageUrl)
                ?: throw UpdaterException("无法打开蓝奏云下载页")
            if (frame.contains("文件取消") || frame.contains("已删除")) {
                throw UpdaterException("该蓝奏云文件已失效")
            }

            val sign = jsVar(frame, "sign") ?: jsVar(frame, "new_sign")
                ?: throw UpdaterException("蓝奏云页面缺少下载签名（可能已改版）")
            val signs = jsVar(frame, "signs") ?: jsVar(frame, "skdklds") ?: ""
            val websignkey = jsVar(frame, "websignkey") ?: ""
            val p = if (password.isNullOrEmpty()) "" else URLEncoder.encode(password, "UTF-8")

            val body = buildString {
                append("action=downprocess")
                append("&sign=").append(URLEncoder.encode(sign, "UTF-8"))
                append("&signs=").append(URLEncoder.encode(signs, "UTF-8"))
                if (p.isNotEmpty()) append("&p=").append(p)
                append("&websign=&ves=1")
                if (websignkey.isNotEmpty()) {
                    append("&websignkey=").append(URLEncoder.encode(websignkey, "UTF-8"))
                }
            }
            val res = httpPostText("$origin/ajaxm.php", body, frameUrl)
                ?: throw UpdaterException("蓝奏云直链请求失败")

            if (res.contains("密码错误") || res.contains("请输入密码") && p.isEmpty()) {
                throw UpdaterException("蓝奏云密码错误或需要密码")
            }

            // 情形1：JSON 返回 dom + url → 再跳一次拿最终直链
            val dom = jsonField(res, "dom")
            val urlPart = jsonField(res, "url")
            val direct: String? = when {
                !dom.isNullOrEmpty() && !urlPart.isNullOrEmpty() ->
                    followLocation("$dom$urlPart", frameUrl)
                !urlPart.isNullOrEmpty() && urlPart.startsWith("http") ->
                    followLocation(urlPart, frameUrl)
                else -> {
                    // 情形2：直接返回 download 字段 / 其它字段里的完整地址
                    jsonField(res, "download")?.takeIf { it.startsWith("http") }
                        ?: Regex("""https?://[^\s"'\\]+""").find(res)?.value
                }
            }
            direct?.takeIf { it.startsWith("http") }
                ?: throw UpdaterException("未能从蓝奏云解析出下载链接（可能已改版）")
        }

    /** 是否需要输入访问密码（分享页出现密码输入框） */
    private fun needsPassword(html: String): Boolean =
        html.contains("passwddiv") ||
            html.contains("请输入密码") ||
            html.contains("访问密码") ||
            html.contains("name=\"pwd\"")

    /** 提取下载 iframe 的 src（多种写法兼容） */
    private fun iframeSrc(html: String): String? {
        Regex("""<iframe[^>]*class=["']ifr2["'][^>]*src=["']([^"']+)["']""").find(html)?.let {
            return it.groupValues[1]
        }
        Regex("""<iframe[^>]*src=["']([^"']+)["'][^>]*class=["']ifr2["']""").find(html)?.let {
            return it.groupValues[1]
        }
        // 兜底：任意带 name=noframe 的 iframe
        Regex("""<iframe[^>]*name=["']noframe["'][^>]*src=["']([^"']+)["']""").find(html)?.let {
            return it.groupValues[1]
        }
        return null
    }

    /** 从页面 JS 中抓形如 `var xxx = "value"` / `xxx:'value'` 的值 */
    private fun jsVar(html: String, name: String): String? {
        val quoted = "\"((?:[^\"\\\\]|\\\\.)*)\""
        Regex("""(?:var\s+)?${Regex.escape(name)}\s*[=:]\s*$quoted""").find(html)?.let {
            return unescapeJs(it.groupValues[1])
        }
        Regex("""${Regex.escape(name)}\s*=\s*'((?:[^'\\]|\\.)*)'""").find(html)?.let {
            return unescapeJs(it.groupValues[1])
        }
        return null
    }

    private fun unescapeJs(s: String): String = s.replace("\\/", "/").replace("\\u0026", "&")

    /** 从 ajax 返回中取 JSON 字段值（值可能是 json 数组等，仅取字符串） */
    private fun jsonField(res: String, key: String): String? {
        Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(res)?.let {
            return it.groupValues[1].replace("\\/", "/")
        }
        Regex("'${Regex.escape(key)}'\\s*:\\s*'((?:[^'\\\\]|\\\\.)*)'").find(res)?.let {
            return it.groupValues[1].replace("\\/", "/")
        }
        return null
    }

    /** 请求一个不跟跳转的 GET，返回最终 Location（不存在则返回 null）。 */
    private fun followLocation(url: String, referer: String?): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", UA)
            if (referer != null) conn.setRequestProperty("Referer", referer)
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                loc?.takeIf { it.startsWith("http") } ?: loc?.let { resolveUrl(url, it) }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveUrl(base: String, rel: String): String {
        if (rel.startsWith("http")) return rel
        return try {
            URL(URL(base), rel).toString()
        } catch (_: Exception) {
            rel
        }
    }

    private fun httpGetText(url: String, referer: String?): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "*/*")
            if (referer != null) conn.setRequestProperty("Referer", referer)
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()
            text
        } catch (_: Exception) {
            null
        }
    }

    private fun httpPostText(url: String, body: String, referer: String?): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Referer", referer ?: url)
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()
            text
        } catch (_: Exception) {
            null
        }
    }
}
