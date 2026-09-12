package com.zsz.zlivephoto.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zsz.zlivephoto.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * ffmpeg 附加项（可选视频转码器）：
 * - 随每次 GitHub Release 以超高压缩 7z 发布（内含 ffmpeg 二进制 + `ffmpeg-meta.json` 元数据）
 * - 设置页下载 → sha-1 校验 → 解压到 filesDir/ffmpeg → 删除 7z
 * - 解压完成前 [isReady]=false，转码功能禁用；go 轻量版禁用全部转码
 * - 元数据（`ffmpeg-meta.json`）用于识别编码器版本
 */
internal class AddonException(message: String) : Exception(message)

/** ffmpeg 附加项下载通道（蓝奏云优先，GitHub 次选；由 UI 层让用户选择） */
enum class DownloadChannel { LANZOU, GITHUB }

object FfmpegAddon {
    /** 标准 MP4 视频编码 fourcc（H.264 / H.265）；其余（vp09/av01/mp4v 等）需转码 */
    val STANDARD_MP4_CODECS = setOf("avc1", "avc3", "hev1", "hvc1")

    /**
     * 转码器元数据（从 Release 正文解析，下载入口使用）。
     * @param url GitHub release 资产直链（主通道）
     * @param sha1 7z 的 SHA-1（两个通道共用同一份文件，校验值相同）
     * @param version 编码器版本号
     * @param mirrorUrl 蓝奏云分享页地址（备用通道，可为空；下载时先解析出直链）
     */
    data class AddonMeta(
        val url: String,
        val sha1: String,
        val version: String,
        val mirrorUrl: String? = null
    )

    // —— 可观察状态（设置页 UI 直接观察）——
    /** 下载进度 0..1；-1 表示未在下载 */
    var downloadProgress by mutableStateOf(-1f)
        private set
    /** 下载/校验/解压进行中 */
    var busy by mutableStateOf(false)
        private set
    /** 已安装的编码器版本（空串=未安装） */
    var installedVersion by mutableStateOf("")
        private set
    /** 当前软件版本期望的编码器版本（空串=未知，不参与匹配约束） */
    var expectedVersion by mutableStateOf("")
        private set
    /** 最近一次安装失败说明 */
    var installError by mutableStateOf<String?>(null)
        private set

    // —— ffmpeg 参数（用户可自选）——
    /** CRF（cq）质量：默认 18，范围 10..30 */
    var crf by mutableStateOf(18)
        private set
    /** 压缩预设（preset），默认 slow */
    var preset by mutableStateOf("slow")
        private set
    /** 编码器：h265（默认）| h264 */
    var codec by mutableStateOf("h265")
        private set

    private lateinit var prefs: SharedPreferences
    private lateinit var appCtx: Context

    fun init(context: Context) {
        appCtx = context.applicationContext
        prefs = appCtx.getSharedPreferences("zlivephoto", Context.MODE_PRIVATE)
        installedVersion = prefs.getString("ffmpeg_version", "") ?: ""
        expectedVersion = prefs.getString("ffmpeg_expected", "") ?: ""
        crf = prefs.getInt("ffmpeg_crf", 18).coerceIn(10, 30)
        preset = prefs.getString("ffmpeg_preset", "slow") ?: "slow"
        codec = prefs.getString("ffmpeg_codec", "h265") ?: "h265"
    }

    fun isGo(): Boolean = BuildConfig.FLAVOR == "go"

    /** 转码是否可用：二进制已解压且可执行、版本与当前软件版本匹配，且非 go 轻量版 */
    fun isReady(): Boolean {
        if (isGo()) return false
        val f = binaryFile() ?: return false
        return f.exists() && f.length() > 0 && f.canExecute() && isVersionMatched()
    }

    /** 已安装版本与当前软件版本期望版本是否匹配（期望版本未知时不强制约束） */
    fun isVersionMatched(): Boolean =
        expectedVersion.isEmpty() || installedVersion.isEmpty() || installedVersion == expectedVersion

    /** 记录当前软件版本期望的编码器版本（软件升级后若与已装版本不一致，转码将被禁用直到重新安装） */
    fun updateExpectedVersion(v: String) {
        expectedVersion = v
        prefs.edit().putString("ffmpeg_expected", v).apply()
    }

    /** 当前 ABI 解压后的 ffmpeg 二进制路径 */
    fun binaryFile(): File? =
        if (::appCtx.isInitialized) File(appCtx.filesDir, "ffmpeg/ffmpeg") else null

    // —— ffmpeg 参数设置 ——
    fun updateCrf(v: Int) {
        crf = v.coerceIn(10, 30)
        prefs.edit().putInt("ffmpeg_crf", crf).apply()
    }

    fun updatePreset(v: String) {
        preset = v
        prefs.edit().putString("ffmpeg_preset", v).apply()
    }

    fun updateCodec(v: String) {
        codec = if (v == "h264") "h264" else "h265"
        prefs.edit().putString("ffmpeg_codec", codec).apply()
    }

    /** 预设可选项（按速度从慢到快；默认 slow） */
    val PRESETS = listOf("veryslow", "slower", "slow", "medium", "fast", "faster", "veryfast", "superfast", "ultrafast")

    // —— 下载 / 校验 / 解压 ——

    /**
     * 按用户选择的通道下载「zip 壳」压缩包（蓝奏云 / GitHub 均为同一份 zip，内含内层 7z）。
     * 两个通道下载的是同一文件，共用同一 SHA-1 校验值。
     */
    private suspend fun downloadZip(meta: AddonMeta, channel: DownloadChannel): File {
        return when (channel) {
            DownloadChannel.GITHUB -> AppUpdater.downloadToCache(appCtx, meta.url) { done, total ->
                downloadProgress = if (total > 0) done.toFloat() / total else -1f
            }
            DownloadChannel.LANZOU -> {
                val mirror = meta.mirrorUrl
                if (mirror.isNullOrBlank()) {
                    throw AddonException("该版本未提供蓝奏云下载链接，可改用 GitHub 下载")
                }
                AppUpdater.downloadToCache(
                    appCtx,
                    AppUpdater.resolveLanzouDirectLink(mirror),
                    referer = mirror
                ) { done, total ->
                    downloadProgress = if (total > 0) done.toFloat() / total else -1f
                }
            }
        }
    }

    /**
     * 下载 + SHA-1 校验：校验失败时删除已下载的压缩包并自动重试（最多 3 次）。
     * 全部失败抛 [AddonException]。
     */
    private suspend fun downloadWithVerify(meta: AddonMeta, channel: DownloadChannel): File {
        var lastError: Exception? = null
        for (attempt in 1..3) {
            var archive: File? = null
            try {
                archive = downloadZip(meta, channel)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
            if (archive != null) {
                val actual = sha1Hex(archive)
                if (actual.equals(meta.sha1, ignoreCase = true)) return archive
                runCatching { archive.delete() }
                lastError = AddonException("SHA-1 校验失败，正在重新下载（第 $attempt 次）")
            }
        }
        throw AddonException("下载转码器失败：${lastError?.message ?: "未知错误"}")
    }

    /**
     * 蓝奏云镜像上传的是「zip 包裹的 7z」（蓝奏对 `.7z` 强制提取码 + 直链验证页，无法直接分发），
     * 下载后若是 zip 容器则解出内层 7z 返回，其余情况原样返回。
     * 解出的仍是同一份 7z，故 SHA-1 校验与 GitHub 主通道完全一致。
     */
    private fun unwrapMirrorArchive(archive: File): File {
        // CDN 直链无扩展名，只能按文件头判断（PK\x03\x04 = zip）
        val magic = ByteArray(4)
        val isZip = runCatching {
            FileInputStream(archive).use { it.read(magic) == 4 } &&
                magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
        }.getOrDefault(false)
        if (!isZip) return archive

        val out = File(archive.parentFile, "ffmpeg-android.7z")
        var found = false
        ZipInputStream(FileInputStream(archive).buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null && !entry.name.endsWith(".7z", ignoreCase = true)) {
                entry = zin.nextEntry
            }
            if (entry != null) {
                found = true
                FileOutputStream(out).use { fos -> zin.copyTo(fos) }
            }
        }
        if (!found) {
            runCatching { out.delete() }
            throw AddonException("镜像压缩包内未找到转码器文件")
        }
        runCatching { archive.delete() }
        return out
    }

    /**
     * 下载（zip 壳）→ SHA-1 校验（失败自动重试）→ 解出内层 7z → 解压安装 → 删除压缩包。
     * 失败抛 [AddonException]，成功更新 installedVersion。
     * @param channel 下载通道（蓝奏云优先 / GitHub 次选），由 UI 层让用户选择。
     */
    suspend fun downloadAndInstall(meta: AddonMeta, channel: DownloadChannel) {
        busy = true
        installError = null
        downloadProgress = 0f
        try {
            // 1) 下载 zip 壳并校验 SHA-1（失败自动删除重下）
            val zipArchive = downloadWithVerify(meta, channel)
            downloadProgress = 1f

            // 2) 解出内层 7z（zip 壳在解出后即删除）
            val sevenz = unwrapMirrorArchive(zipArchive)

            // 3) 解压 7z 安装
            val dest = File(appCtx.filesDir, "ffmpeg")
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val extracted = extract7z(sevenz, dest)

            val binSrc = extracted.firstOrNull {
                it.name == "ffmpeg" && it.parentFile?.name == abi
            } ?: extracted.firstOrNull { it.name == "ffmpeg" }
                ?: throw AddonException("7z 内未找到 ffmpeg 二进制（$abi）")

            val target = File(dest, "ffmpeg")
            binSrc.copyTo(target, overwrite = true)
            target.setExecutable(true, false)
            target.setReadable(true, false)

            // 读取元数据识别版本
            val metaFile = extracted.firstOrNull { it.name == "ffmpeg-meta.json" }
            val ver = metaFile?.let { f ->
                runCatching {
                    JSONObject(f.readText(Charsets.UTF_8)).optString("version", "")
                }.getOrDefault("")
            }.orEmpty().ifEmpty { meta.version }

            installedVersion = ver
            prefs.edit().putString("ffmpeg_version", ver).apply()
            updateExpectedVersion(meta.version.ifBlank { ver })

            // 清理：删除 7z 与临时解压目录中除二进制外的其余内容
            runCatching { sevenz.delete() }
            dest.listFiles()?.forEach { f ->
                if (f.name != "ffmpeg") runCatching { f.deleteRecursively() }
            }
        } catch (e: AddonException) {
            installError = e.message
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            installError = e.message ?: "安装转码器失败"
            throw AddonException(e.message ?: "安装转码器失败")
        } finally {
            busy = false
            downloadProgress = -1f
        }
    }

    /**
     * 拉取**当前软件版本**对应的 ffmpeg 附加项元数据（按 tag `v{版本}` 取该版本 Release 正文解析）。
     * 返回 null 表示当前版本尚未发布附加项（或网络失败）。
     */
    suspend fun fetchMetaForCurrentVersion(): AddonMeta? {
        val body = UpdateChecker.fetchReleaseBody("v${BuildConfig.VERSION_NAME}") ?: return null
        val meta = UpdateChecker.parseAddonMeta(body, BuildConfig.VERSION_NAME) ?: return null
        // 记录当前版本期望的编码器版本，供「版本不匹配则禁用转码」判断
        if (meta.version.isNotBlank()) updateExpectedVersion(meta.version)
        return meta
    }

    /** 下载并安装当前软件版本对应的转码器（默认走蓝奏云通道）；失败抛 [AddonException] 并设置 installError */
    suspend fun installForCurrentVersion(channel: DownloadChannel = DownloadChannel.LANZOU) {
        val meta = fetchMetaForCurrentVersion()
        if (meta == null) {
            installError = "发布信息中未找到适用于当前版本（v${BuildConfig.VERSION_NAME}）的转码器附加项，请稍后重试"
            throw AddonException(installError!!)
        }
        downloadAndInstall(meta, channel)
    }

    /** 解压 7z，返回所有解压出的文件（含目录内文件） */
    private fun extract7z(archive: File, dest: File): List<File> {
        dest.mkdirs()
        val out = mutableListOf<File>()
        SevenZFile(archive).use { sz ->
            while (true) {
                val entry = sz.nextEntry ?: break
                val f = File(dest, entry.name)
                if (entry.isDirectory) {
                    f.mkdirs()
                    continue
                }
                f.parentFile?.mkdirs()
                FileOutputStream(f).use { fos ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val n = sz.read(buf)
                        if (n < 0) break
                        fos.write(buf, 0, n)
                    }
                }
                out.add(f)
            }
        }
        return out
    }

    /** 文件 SHA-1（16 进制小写） */
    fun sha1Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // —— 视频转码 ——

    /**
     * 用 ffmpeg 把 [input] 转码为「标准 MP4 容器（H.265/H.264, CRF, preset）」。
     * 输出到 cache/ffmpeg_tmp，返回输出文件；失败抛 [AddonException]。
     */
    suspend fun transcodeToMp4(
        input: String,
        log: (String, String, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val bin = binaryFile() ?: throw AddonException("ffmpeg 未安装")
        if (!bin.exists() || !bin.canExecute()) throw AddonException("ffmpeg 不可执行，请重新安装")

        val tmpDir = File(appCtx.cacheDir, "ffmpeg_tmp").apply { mkdirs() }
        val out = File(tmpDir, "transcoded_${System.currentTimeMillis()}.mp4")
        val vcodec = if (codec == "h264") "libx264" else "libx265"
        val tag = if (codec == "h264") "avc1" else "hvc1"
        val args = listOf(
            "-y", "-i", input,
            "-c:v", vcodec,
            "-crf", crf.toString(),
            "-preset", preset,
            "-c:a", "aac", "-b:a", "128k",
            "-movflags", "+faststart",
            "-tag:v", tag,
            out.absolutePath
        )
        log("info", "正在用 ffmpeg 转码视频为标准 MP4（$vcodec / crf $crf / $preset）", "转码")

        val pb = ProcessBuilder(listOf(bin.absolutePath) + args)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val tail = StringBuilder()
        val reader = Thread {
            runCatching {
                proc.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(1024)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        tail.append(buf, 0, n)
                        if (tail.length > 8192) tail.delete(0, tail.length - 8192)
                    }
                }
            }
        }
        reader.start()
        val exit = proc.waitFor()
        reader.join(5000)

        if (exit != 0 || !out.exists() || out.length() <= 0L) {
            runCatching { out.delete() }
            throw AddonException("ffmpeg 转码失败：${tail.toString().trim().takeLast(160)}")
        }
        log("info", "视频转码完成（${out.length() / 1024}KB）", "转码")
        out
    }
}
