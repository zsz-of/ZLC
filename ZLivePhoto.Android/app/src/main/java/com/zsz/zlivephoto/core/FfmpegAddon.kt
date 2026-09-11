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
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * ffmpeg 附加项（可选视频转码器）：
 * - 随每次 GitHub Release 以超高压缩 7z 发布（内含 ffmpeg 二进制 + `ffmpeg-meta.json` 元数据）
 * - 设置页下载 → sha-1 校验 → 解压到 filesDir/ffmpeg → 删除 7z
 * - 解压完成前 [isReady]=false，转码功能禁用；go 轻量版禁用全部转码
 * - 元数据（`ffmpeg-meta.json`）用于识别编码器版本
 */
internal class AddonException(message: String) : Exception(message)

object FfmpegAddon {
    /** 标准 MP4 视频编码 fourcc（H.264 / H.265）；其余（vp09/av01/mp4v 等）需转码 */
    val STANDARD_MP4_CODECS = setOf("avc1", "avc3", "hev1", "hvc1")

    /** 转码器元数据（从 Release 正文解析，下载入口使用） */
    data class AddonMeta(val url: String, val sha1: String, val version: String)

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
        crf = prefs.getInt("ffmpeg_crf", 18).coerceIn(10, 30)
        preset = prefs.getString("ffmpeg_preset", "slow") ?: "slow"
        codec = prefs.getString("ffmpeg_codec", "h265") ?: "h265"
    }

    fun isGo(): Boolean = BuildConfig.FLAVOR == "go"

    /** 转码是否可用：二进制已解压且可执行，且非 go 轻量版 */
    fun isReady(): Boolean {
        if (isGo()) return false
        val f = binaryFile() ?: return false
        return f.exists() && f.length() > 0 && f.canExecute()
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

    /** 下载 7z → 校验 sha-1 → 解压 → 删除 7z。失败抛 [AddonException]，成功更新 installedVersion。 */
    suspend fun downloadAndInstall(meta: AddonMeta) {
        busy = true
        installError = null
        downloadProgress = 0f
        try {
            val archive = AppUpdater.downloadToCache(appCtx, meta.url) { done, total ->
                downloadProgress = if (total > 0) done.toFloat() / total else -1f
            }
            downloadProgress = 1f

            // sha-1 校验
            val actual = sha1Hex(archive)
            if (!actual.equals(meta.sha1, ignoreCase = true)) {
                runCatching { archive.delete() }
                throw AddonException("7z 校验失败：SHA-1 不匹配，请重新下载")
            }

            // 解压
            val dest = File(appCtx.filesDir, "ffmpeg")
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val extracted = extract7z(archive, dest)

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

            // 清理：删除 7z 与临时解压目录中除二进制外的其余内容
            runCatching { archive.delete() }
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

    /** 从最新 GitHub Release 拉取附加项元数据并下载安装；失败抛 [AddonException] 并设置 installError */
    suspend fun installFromLatestRelease() {
        val meta = UpdateChecker.fetchLatest()?.notes?.let { UpdateChecker.parseAddonMeta(it) }
        if (meta == null) {
            installError = "发布信息中未找到转码器附加项，请稍后重试"
            throw AddonException(installError!!)
        }
        downloadAndInstall(meta)
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
