package com.zsz.zlivephoto.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import java.util.zip.ZipInputStream

/**
 * 应用内一键下载更新：
 * - GitHub release 资产直链（*.apk）直接下载；
 * - 蓝奏云分享页 → 原生解析出直链（自实现，不依赖任何公益 API）→ 下载；
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
    private const val UA =
        "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    /** 下载/解压的临时缓存目录（cache 下，启动自动清理覆盖） */
    internal fun cacheDir(context: Context): File = File(context.cacheDir, "zlc_update")

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
        val conn: HttpURLConnection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", UA)
                if (referer != null) setRequestProperty("Referer", referer)
                instanceFollowRedirects = true
            }.also { code = it.responseCode }
        } catch (e: CancellationException) {
            throw e
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
                    ensureActive() // 取消时立即中断
                    val n = input.read(buf)
                    if (n < 0) break
                    fos.write(buf, 0, n)
                    done += n
                    onProgress(done, if (total > 0) total else -1L)
                }
            }
            if (!raw.exists() || raw.length() <= 0L) throw UpdaterException("下载内容为空")
            raw
        } catch (e: CancellationException) {
            runCatching { raw.delete() }
            throw e
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
     * 规整过程中目标文件叫 `update.part`，**解压/写入完整结束后**才重命名为 `update.apk`，
     * 保证任何时刻缓存里都不存在未完成的 `.apk`。
     * 返回最终 APK 文件；失败抛 [UpdaterException]。
     */
    suspend fun resolveApk(context: Context, downloaded: File): File = withContext(Dispatchers.IO) {
        val apkFromZip = try { extractApkFromZip(downloaded) } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
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
        return try {
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            "无法调起系统安装器（${e.message}）"
        }
    }

    // ---------- 蓝奏云直链解析（原生自实现，不依赖公益 API） ----------

    /**
     * 解析蓝奏云分享页 → 最终直链。
     * 机制：分享页抓取下载 iframe → iframe 页 JS 提取 sign/signs/websignkey →
     * POST `/ajaxm.php`（action=downprocess）→ 返回 dom+url → 跟随跳转得到最终直链。
     * @throws UpdaterException 页面结构不支持 / 网络异常 / 文件失效时抛出。
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

            if (res.contains("密码错误") || (res.contains("请输入密码") && p.isEmpty())) {
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

    /** 请求一个不跟跳转的 GET，返回最终 Location（不存在则返回 null） */
    private suspend fun followLocation(url: String, referer: String?): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.apply {
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", UA)
                if (referer != null) setRequestProperty("Referer", referer)
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                loc?.takeIf { it.startsWith("http") } ?: loc?.let { resolveUrl(url, it) }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
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
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "*/*")
                if (referer != null) setRequestProperty("Referer", referer)
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun httpPostText(url: String, body: String, referer: String?): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Referer", referer ?: url)
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
