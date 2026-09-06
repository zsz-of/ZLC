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
import java.util.zip.ZipInputStream

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
    /**
     * 下载与内置浏览器统一使用的桌面 Chrome UA：
     * 蓝奏分享页对移动 UA 返回无下载框的 WAP 页，桌面 UA 才能命中可下载的 PC 版页面。
     */
    internal const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

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
                setRequestProperty("User-Agent", DESKTOP_UA)
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
}
