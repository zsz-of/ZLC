package com.zsz.zlivephoto.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zsz.zlivephoto.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 内置视频转码器（ffmpeg）：
 *
 * Android 10（API 29）起 SELinux 禁止从应用可写目录执行二进制（`error=13, Permission denied`），
 * 唯一可行位置是 APK 的 native 库目录（`nativeLibraryDir`）。因此 ffmpeg 以 native 库形式
 * 随 APK 打包（`app/src/normal/jniLibs/arm64-v8a/libffmpeg.so`），安装时由系统解压到
 * `nativeLibraryDir`，运行时直接以该路径 `ProcessBuilder` 调用，无需下载、校验、解压，
 * 也不存在版本失配问题。
 *
 * go 轻量版不含该二进制（体积优先），[isReady] 恒为 false，转码功能整体禁用。
 */
internal class AddonException(message: String) : Exception(message)

object FfmpegAddon {
    /** 标准 MP4 视频编码 fourcc（H.264 / H.265）；其余（vp09/av01/mp4v 等）需转码 */
    val STANDARD_MP4_CODECS = setOf("avc1", "avc3", "hev1", "hvc1")

    /** 随包内置的 ffmpeg 版本（与 jniLibs 中的 libffmpeg.so 一致） */
    const val BUNDLED_VERSION = "n8.1.2.7"

    /** 内置二进制在 nativeLibraryDir 中的文件名 */
    private const val BINARY_NAME = "libffmpeg.so"

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
        crf = prefs.getInt("ffmpeg_crf", 18).coerceIn(10, 30)
        preset = prefs.getString("ffmpeg_preset", "slow") ?: "slow"
        codec = prefs.getString("ffmpeg_codec", "h265") ?: "h265"
    }

    fun isGo(): Boolean = BuildConfig.FLAVOR == "go"

    /** 转码是否可用：内置二进制存在且非空（go 轻量版不含转码器） */
    fun isReady(): Boolean {
        if (isGo()) return false
        val f = binaryFile() ?: return false
        return f.exists() && f.length() > 0
    }

    /**
     * 内置 ffmpeg 二进制路径：APK native 库目录下的 `libffmpeg.so`。
     * 该目录由系统在安装时解压生成，带执行权限，是 Android 10+ 唯一可执行位置。
     */
    private fun binaryFile(): File? =
        if (::appCtx.isInitialized) {
            File(appCtx.applicationInfo.nativeLibraryDir, BINARY_NAME)
        } else null

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

    // —— 视频转码 ——

    /**
     * 用内置 ffmpeg 把 [input] 转码为「标准 MP4 容器（H.265/H.264, CRF, preset）」。
     * 输出到 cache/ffmpeg_tmp，返回输出文件；失败抛 [AddonException]。
     */
    suspend fun transcodeToMp4(
        input: String,
        log: (String, String, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val bin = binaryFile() ?: throw AddonException("内置转码器不可用")
        if (!bin.exists() || bin.length() <= 0L) throw AddonException("内置转码器不可用")

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
