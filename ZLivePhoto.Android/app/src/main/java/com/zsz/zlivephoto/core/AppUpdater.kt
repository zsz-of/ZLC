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
import java.util.zip.ZipInputStream

/**
 * 应用内一键下载更新：下载 GitHub release 资产直链（*.apk）到缓存目录，
 * 规整成 APK 后调系统安装器安装。
 * 蓝奏云渠道在 UI 层改为「跳外部浏览器」，本文件不再解析蓝奏云直链。
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
}
