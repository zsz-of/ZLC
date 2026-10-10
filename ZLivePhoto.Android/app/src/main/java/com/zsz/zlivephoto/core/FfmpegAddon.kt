package com.zsz.zlivephoto.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zsz.zlivephoto.BuildConfig
import com.zsz.zlivephoto.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToLong

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
 *
 * 合成动态照片遇到不符合格式的视频时怎么处理，由用户在设置页三选一（见 [mode] /
 * [MODE_OFF] / [MODE_REMUX] / [MODE_ENCODE]），默认 [MODE_REMUX]（只换容器不重新编码）。
 */
internal class AddonException(message: String) : Exception(message)

/**
 * core 层的统一取文案入口。
 *
 * [Converter] / [AppUpdater] / [UpdateChecker] / [FfmpegAddon] 都是没有 Context 的
 * object，而文案又必须随系统/应用语言变化，因此统一用 [FfmpegAddon.init] 时缓存的
 * applicationContext 取资源（该 init 在 MainActivity.onCreate 首帧组合前调用，
 * 早于任何转换与更新流程）。
 *
 * 取不到 Context 时（进程极早期，或纯 JVM 单测没有 Android 环境）返回 [ofOr] 的
 * 兜底文案，**绝不触碰 android 框架 API** —— 否则单测会抛
 * 「Method getString in android.content.Context not mocked」。
 */
internal object CoreText {
    /** 取资源文案；无 Context 时为空串 */
    fun of(resId: Int, vararg args: Any?): String = ofOr(resId, "", *args)

    /**
     * 取资源文案；无 Context 时返回 [fallback]（JVM 单测依赖该兜底保持既有契约）。
     *
     * [fallback] 与资源一样可以带 `%1$s` / `%1$d` 之类的占位符：无 Context 时会用
     * [args] 就地格式化，保证单测看到的兜底文案与真机上的资源文案结构一致。
     * 只使用 `java.lang.String.format`，不触碰任何 android 框架 API。
     */
    fun ofOr(resId: Int, fallback: String, vararg args: Any?): String {
        val ctx = FfmpegAddon.cachedContext()
            ?: return if (args.isEmpty()) fallback else String.format(Locale.ROOT, fallback, *args)
        return if (args.isEmpty()) ctx.getString(resId) else ctx.getString(resId, *args)
    }
}

object FfmpegAddon {
    /** 标准 MP4 视频编码 fourcc（H.264 / H.265）；其余（vp09/av01/mp4v 等）需转码 */
    val STANDARD_MP4_CODECS = setOf("avc1", "avc3", "hev1", "hvc1")

    /** 转码方式：完全不使用（不调用 ffmpeg，只做零拷贝的容器品牌改写） */
    const val MODE_OFF = "off"

    /** 转码方式：仅重封装容器（只换容器、不重新编码）—— 默认值 */
    const val MODE_REMUX = "remux"

    /** 转码方式：重新编码（同时重封装容器） */
    const val MODE_ENCODE = "encode"

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
    /** 转码方式：见 [MODE_OFF] / [MODE_REMUX] / [MODE_ENCODE]，默认仅重封装容器 */
    var mode by mutableStateOf(MODE_REMUX)
        private set

    private lateinit var prefs: SharedPreferences
    private lateinit var appCtx: Context

    fun init(context: Context) {
        appCtx = context.applicationContext
        prefs = appCtx.getSharedPreferences("zlivephoto", Context.MODE_PRIVATE)
        crf = prefs.getInt("ffmpeg_crf", 18).coerceIn(10, 30)
        preset = prefs.getString("ffmpeg_preset", "slow") ?: "slow"
        codec = prefs.getString("ffmpeg_codec", "h265") ?: "h265"
        // 老用户升级后默认「仅重封装容器」（不重新编码），未写过的 prefs 键即为此值
        mode = normalizeMode(prefs.getString("ffmpeg_mode", MODE_REMUX))
    }

    /**
     * 供 core 层取本地化文案用：init 之后返回 applicationContext，未初始化时为 null。
     * 只在 [CoreText] 里用，业务代码不要直接取它做别的事。
     */
    internal fun cachedContext(): Context? = if (::appCtx.isInitialized) appCtx else null

    fun isGo(): Boolean = BuildConfig.FLAVOR == "go"

    /** 转码是否可用：内置二进制存在且非空（go 轻量版不含转码器） */
    fun isReady(): Boolean {
        if (isGo()) return false
        val f = binaryFile() ?: return false
        return f.exists() && f.length() > 0
    }

    /**
     * 转码中间文件：统一放在应用缓存目录 `cache/ffmpeg_tmp`。
     * 不用 `File.createTempFile`（依赖 `java.io.tmpdir`，在各 ROM 上指向不一致），
     * 也不占用用户可见的输出目录。
     */
    fun tempFile(prefix: String, suffix: String): File {
        val dir = File(appCtx.cacheDir, "ffmpeg_tmp").apply { mkdirs() }
        return File(dir, "$prefix${System.currentTimeMillis()}$suffix")
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

    /** 设置转码方式（非法值回落到默认的「仅重封装容器」） */
    fun updateMode(v: String) {
        mode = normalizeMode(v)
        prefs.edit().putString("ffmpeg_mode", mode).apply()
    }

    private fun normalizeMode(v: String?): String = when (v) {
        MODE_OFF -> MODE_OFF
        MODE_ENCODE -> MODE_ENCODE
        else -> MODE_REMUX
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

    // —— 视频转码（含进度解析）——

    /** ffmpeg 启动日志中的输入时长（"Duration: 00:00:05.12"） */
    private val DURATION_RE = Regex("""Duration:\s*(\d+):(\d{2}):(\d{2}(?:\.\d+)?)""")

    /** ffmpeg 启动日志中的视频流帧率（"... 30 fps, ..."）；进度行的 "fps=23.5" 不会命中 */
    private val FPS_RE = Regex("""(\d+(?:\.\d+)?)\s*fps""")

    /** `-progress pipe:1` 周期性输出的已处理帧号（"frame=123"） */
    private val FRAME_RE = Regex("""frame=\s*(\d+)""")

    /**
     * 用内置 ffmpeg 把 [input] 转码为「标准 MP4 容器（H.265/H.264, CRF, preset）」。
     * 输出到 cache/ffmpeg_tmp，返回输出文件；失败抛 [AddonException]。
     *
     * 只保留首路视频 + 首路音频轨道（`-sn -dn` 丢弃字幕/数据轨道）：
     * 输入为 MKV 等容器时常带 ASS/SRT 字幕与附件，带进 MP4 会破坏动态照片的兼容性。
     * 用 `V` 而非 `v` 选择视频轨，避免 MKV 内嵌封面图（attached pic）被当成主视频。
     *
     * 转码过程通过 [onProgress] 上报进度（已处理帧 / 总帧数 / 预计剩余秒数，未知为 -1）。
     * 命令附带 `-nostats -progress pipe:1`，ffmpeg 按固定周期输出 `frame=` 键值行，
     * 避免默认 `\r` 原地刷新行的解析开销，且进度延迟不超过一个上报周期。
     *
     * 总帧数优先取 [totalFramesHint]（调用方已解析出的精确值），否则用启动日志里的
     * 「输入时长 × 帧率」估算；两者都拿不到时以 0 上报，由 UI 退化为「已处理 N 帧」。
     */
    suspend fun transcodeToMp4(
        input: String,
        log: (String, String, String) -> Unit,
        totalFramesHint: Long = -1L,
        onProgress: (frame: Long, total: Long, etaSec: Long) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val bin = binaryFile() ?: throw AddonException(CoreText.of(R.string.conv_addon_unavailable))
        if (!bin.exists() || bin.length() <= 0L) throw AddonException(CoreText.of(R.string.conv_addon_unavailable))

        val tmpDir = File(appCtx.cacheDir, "ffmpeg_tmp").apply { mkdirs() }
        val out = File(tmpDir, "transcoded_${System.currentTimeMillis()}.mp4")
        val vcodec = if (codec == "h264") "libx264" else "libx265"
        val tag = if (codec == "h264") "avc1" else "hvc1"
        val args = listOf(
            "-y",
            // -nostats 关掉 stderr 上默认的 "\r" 原地刷新统计行；
            // -progress pipe:1 让 ffmpeg 按固定周期向 stdout 输出换行结尾的 frame= 键值行
            "-nostats", "-progress", "pipe:1",
            "-i", input,
            "-map", "0:V:0", "-map", "0:a:0?",
            "-sn", "-dn",
            "-c:v", vcodec,
            "-crf", crf.toString(),
            "-preset", preset,
            "-c:a", "aac", "-b:a", "128k",
            "-movflags", "+faststart",
            "-tag:v", tag,
            out.absolutePath
        )
        log("info", CoreText.of(R.string.conv_log_ffmpeg_transcoding, vcodec, crf, preset), CoreText.of(R.string.conv_tag_transcode))

        val pb = ProcessBuilder(listOf(bin.absolutePath) + args)
        pb.redirectErrorStream(true)
        val proc = pb.start()

        // 边读边解析：stdout+stderr 合流。启动日志提供时长/帧率（估算总帧数），
        // -progress 行提供已处理帧号。逐字符读取并把 '\r' 也当行结束，
        // 阻塞式读（不使用 select，Windows 管道不支持）。
        val tail = StringBuilder()
        var totalFrames = if (totalFramesHint > 0L) totalFramesHint else 0L
        var inputSec = 0.0
        var inputFps = 0.0
        val startedAt = System.nanoTime()
        var lastFrame = -1L

        runCatching {
            proc.inputStream.bufferedReader().use { r ->
                val line = StringBuilder()
                while (true) {
                    val c = r.read()
                    if (c < 0) break
                    val ch = c.toChar()
                    if (ch != '\n' && ch != '\r') {
                        line.append(ch)
                        continue
                    }
                    if (line.isEmpty()) continue
                    val text = line.toString()
                    line.setLength(0)

                    tail.append(text).append('\n')
                    if (tail.length > 8192) tail.delete(0, tail.length - 8192)

                    // 总帧数未知时，用启动日志里的「输入时长 × 帧率」估算
                    if (totalFrames <= 0L) {
                        if (inputSec <= 0.0) {
                            DURATION_RE.find(text)?.let {
                                inputSec = it.groupValues[1].toDouble() * 3600.0 +
                                    it.groupValues[2].toDouble() * 60.0 +
                                    it.groupValues[3].toDouble()
                            }
                        }
                        if (inputFps <= 0.0) {
                            FPS_RE.find(text)?.let { inputFps = it.groupValues[1].toDouble() }
                        }
                        if (inputSec > 0.0 && inputFps > 0.0) {
                            totalFrames = (inputSec * inputFps).roundToLong()
                        }
                    }

                    val frame = FRAME_RE.find(text)?.groupValues?.get(1)?.toLongOrNull()
                    if (frame != null && frame != lastFrame) {
                        lastFrame = frame
                        onProgress(frame, totalFrames, etaSeconds(startedAt, frame, totalFrames))
                    } else if (text.startsWith("progress=end")) {
                        // 收尾：估算的总帧数与实际有偏差时，补一次 100% 上报
                        if (totalFrames > 0L && lastFrame < totalFrames) {
                            lastFrame = totalFrames
                            onProgress(totalFrames, totalFrames, 0L)
                        }
                    }
                }
            }
        }
        val exit = proc.waitFor()

        if (exit != 0 || !out.exists() || out.length() <= 0L) {
            runCatching { out.delete() }
            throw AddonException(CoreText.of(R.string.conv_addon_transcode_failed, tail.toString().trim().takeLast(160)))
        }
        log("info", CoreText.of(R.string.conv_log_transcode_done, out.length() / 1024), CoreText.of(R.string.conv_tag_transcode))
        out
    }

    /**
     * 用内置 ffmpeg 把 [input] **只换容器、不重新编码**（`-c copy`）封装为标准 MP4。
     * 输出到 cache/ffmpeg_tmp，返回输出文件；失败抛 [AddonException]。
     *
     * 与 [transcodeToMp4] 的区别：不碰视频/音频码流，只把它们搬进 MP4 容器，
     * 因此快得多、画质零损失；代价是**改变不了编码本身** —— 源视频编码不是
     * MP4 能承载的 H.264/H.265 时（如 VP9/AV1），产物仍是非标准编码，需调用方复核。
     *
     * 与 [transcodeToMp4] 一致：只保留首路视频 + 首路音频轨道（`-sn -dn` 丢弃字幕/数据轨道），
     * 用 `V` 而非 `v` 选择视频轨，避免 MKV 内嵌封面图被当成主视频。
     * 不指定 `-tag:v`：源是 MP4/MOV 时保留原有的 avc1/hvc1 采样条目标签。
     */
    suspend fun remuxToMp4(
        input: String,
        log: (String, String, String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val bin = binaryFile() ?: throw AddonException(CoreText.of(R.string.conv_addon_unavailable))
        if (!bin.exists() || bin.length() <= 0L) throw AddonException(CoreText.of(R.string.conv_addon_unavailable))

        val tmpDir = File(appCtx.cacheDir, "ffmpeg_tmp").apply { mkdirs() }
        val out = File(tmpDir, "remuxed_${System.currentTimeMillis()}.mp4")
        val args = listOf(
            "-y", "-nostats",
            "-i", input,
            "-map", "0:V:0", "-map", "0:a:0?",
            "-sn", "-dn",
            "-c", "copy",
            "-movflags", "+faststart",
            out.absolutePath
        )
        log("info", CoreText.of(R.string.conv_log_remuxing), CoreText.of(R.string.conv_tag_transcode))

        val pb = ProcessBuilder(listOf(bin.absolutePath) + args)
        pb.redirectErrorStream(true)
        val proc = pb.start()

        // 重封装很快且没有进度可言，只需保留尾部输出用于报错
        val tail = StringBuilder()
        runCatching {
            proc.inputStream.bufferedReader().use { r ->
                var line = r.readLine()
                while (line != null) {
                    tail.append(line).append('\n')
                    if (tail.length > 8192) tail.delete(0, tail.length - 8192)
                    line = r.readLine()
                }
            }
        }
        val exit = proc.waitFor()

        if (exit != 0 || !out.exists() || out.length() <= 0L) {
            runCatching { out.delete() }
            throw AddonException(CoreText.of(R.string.conv_addon_remux_failed, tail.toString().trim().takeLast(160)))
        }
        log("info", CoreText.of(R.string.conv_log_remux_done, out.length() / 1024), CoreText.of(R.string.conv_tag_transcode))
        out
    }

    /**
     * 预计剩余秒数：按「已处理帧 / 已耗时」实测速率外推。
     * 前 0.8 秒速率不稳定（ffmpeg 尚在预热），或总帧数未知时返回 -1（UI 显示为未知）。
     */
    private fun etaSeconds(startedAtNs: Long, frame: Long, totalFrames: Long): Long {
        if (totalFrames <= 0L || frame <= 0L) return -1L
        val elapsed = (System.nanoTime() - startedAtNs) / 1_000_000_000.0
        if (elapsed < 0.8) return -1L
        val rate = frame / elapsed
        if (rate <= 0.0) return -1L
        return ((totalFrames - frame) / rate).toLong().coerceAtLeast(0L)
    }
}
