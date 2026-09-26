package com.zsz.zlivephoto.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.zsz.zlivephoto.BuildConfig
import com.zsz.zlivephoto.core.formats.FormatRegistry
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 转换管线：detect → read → write。同格式转换 = 原样复制（零损耗直通）。
 */
internal open class ConvertException(message: String) : Exception(message)

/**
 * 合成视频的容器不受支持（缺少 ftyp 头 / 是 QuickTime MOV 品牌等）。
 * 抛给 UI 后把该任务标记为失败，message 提示用户先转码视频为 MP4 再合成。
 */
internal class VideoContainerException(message: String) : ConvertException(message)

internal object Converter {
    /**
     * 转换单个文件为指定格式，返回输出文件路径列表。
     *
     * @param path 源文件路径
     * @param target 目标格式：google | apple | oppo | vivo | xiaomi
     * @param outDir 输出目录
     * @param log 日志回调 (level, message, tag)
     * @param options 选项：google_mp_suffix (bool)
     */
    fun convertFile(
        path: String, target: String, outDir: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?> = mutableMapOf()
    ): MutableList<String> {
        val targetPlugin = FormatRegistry.byName[target]
            ?: throw ConvertException("未知目标格式：$target")

        val (plugin, score) = FormatRegistry.detectBest(path)
        if (plugin == null || score < 50) {
            throw ConvertException("无法识别的动态照片格式（非 Google/OPPO/vivo/小米/Apple 动态照片）")
        }

        log("info", "识别为 ${plugin.display}", "转换")

        val stem = File(path).nameWithoutExtension
        File(outDir).mkdirs()

        // 同格式直通：原样复制，零损耗
        // 例外 vivo_single：源可能是 vivo 相册「关闭实况」的合并产物（MotionPhoto="0"），
        // 需走完整写出流程修复回 "1" 恢复动态效果
        if (plugin.name == target && plugin.name != "vivo_single") {
            val directOuts = mutableListOf<String>()
            val dst = File(outDir, File(path).name).path
            File(path).copyTo(File(dst), overwrite = true)
            directOuts.add(dst)

            val parent = File(path).parentFile
            when (plugin.name) {
                "vivo" -> {
                    val mp4 = if (parent != null) File(parent, "$stem.mp4").path else "$stem.mp4"
                    if (File(mp4).exists()) {
                        val dstMp4 = File(outDir, File(mp4).name).path
                        File(mp4).copyTo(File(dstMp4), overwrite = true)
                        directOuts.add(dstMp4)
                    }
                }
                "apple" -> {
                    val mov = if (parent != null) File(parent, "$stem.mov").path else "$stem.mov"
                    if (File(mov).exists()) {
                        val dstMov = File(outDir, File(mov).name).path
                        File(mov).copyTo(File(dstMov), overwrite = true)
                        directOuts.add(dstMov)
                    }
                }
            }
            log("info", "源与目标格式相同，已原样复制（零损耗）", "转换")
            return directOuts
        }

        val asset = plugin.read(path, log)
        if (asset.presentationTsUs < 0) {
            log("warning", "源缺少封面帧时间戳，按规范回退为视频中点", "转换")
        }

        val outputs = targetPlugin.write(asset, outDir, stem, log, options)

        // 保留源文件的时间戳（修改时间）
        for (outPath in outputs) {
            try {
                copyTimestamps(path, outPath)
            } catch (e: Exception) {
                /* 时间戳复制失败不阻塞转换 */
            }
        }

        return outputs
    }

    /** 复制源文件的修改时间到目标文件（访问时间/创建时间在 Android/Linux 上无原生 API，省略）。 */
    private fun copyTimestamps(src: String, dst: String) {
        val srcFile = File(src)
        val dstFile = File(dst)
        dstFile.setLastModified(srcFile.lastModified())
    }

    /**
     * 合成动态照片：普通照片（JPEG 封面）+ 视频 → 指定目标格式。
     * 构造 LivePhotoAsset 后直接走目标插件 write（与转换同一写出管线）。
     * 视频时长不限（超过 3 秒的兼容性警告由 UI 层处理）。
     *
     * @param photoPath 封面照片路径（普通 JPEG）
     * @param videoPath 视频路径（MP4）
     * @param target 目标格式：google | oppo | vivo | vivo_single | xiaomi | honor | meizu
     * @param onTranscodeProgress 视频转码进度回调 (已处理帧, 总帧数, 预计剩余秒数)；仅转码时触发
     */
    suspend fun compose(
        photoPath: String, videoPath: String, target: String, outDir: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?> = mutableMapOf(),
        onTranscodeProgress: (frame: Long, total: Long, etaSec: Long) -> Unit = { _, _, _ -> }
    ): MutableList<String> {
        val targetPlugin = FormatRegistry.byName[target]
            ?: throw ConvertException("未知目标格式：$target")

        val photo = File(photoPath)
        if (!photo.exists() || photo.length() < 4) throw ConvertException("封面照片不存在或为空")
        val jpeg = decodeCoverToJpeg(photo)

        val video = File(videoPath)
        if (!video.exists() || video.length() < 12) throw ConvertException("视频不存在或为空")
        // 合成会把整个视频读入内存再与封面拼接，过大时会触发 OOM（OutOfMemoryError 属于
        // Error，不会被上层 catch (e: Exception) 捕获，表现为闪退）。这里设安全上限，
        // 超限时抛可捕获的 ConvertException，由 UI 显示友好提示而非崩溃。
        val maxVideoBytes = 128L * 1024 * 1024
        if (video.length() > maxVideoBytes) {
            val mb = video.length() / 1024 / 1024
            throw ConvertException("视频过大（${mb}MB），无法合成为动态照片，请选择更短的视频")
        }

        // 按内部文件结构（而非扩展名）判断视频是否「符合动态照片所需的标准 MP4」：
        //   1) 不是 MP4 容器（文件头 4 字节非 "ftyp"，如 MKV/WebM/AVI）→ 不符合；
        //   2) 是 MP4 但 brand 为 QuickTime("qt  "，即 MOV) → 不符合；
        //   3) 是 MP4 但视频编码不是标准 H.264/H.265（vp09/av01/mp4v 等）→ 不符合。
        // 不符合时按用户在「设置 → 视频转码 → 转码方式」选的方式处理，见 prepareUnsupportedVideo。
        val head = ByteArray(12)
        video.inputStream().use { ins -> ins.read(head) }
        val hasFtyp = Mp4Util.hasFtyp(head)
        val brand = if (hasFtyp) String(head, 8, 4, Charsets.US_ASCII) else ""
        val isMp4 = hasFtyp && brand != "qt  "

        var mp4: ByteArray? = null
        // 转码进度用的精确总帧数：标准 MP4 分支已解析出轨道信息，可直接复用（免二次解析）
        var frameHint = -1L
        if (isMp4) {
            // 标准 MP4 容器：进一步检查视频编码是否为标准 H.264/H.265，
            // 非标准编码（vp09/av01/mp4v 等）也需按所选转码方式处理
            val bytes = video.readBytes()
            val trackInfo = Mp4Util.getTrackInfo(bytes)
            val codec = (trackInfo?.get("codec") as? String).orEmpty()
            if (codec.isEmpty() || codec in FfmpegAddon.STANDARD_MP4_CODECS) {
                mp4 = bytes
            } else {
                frameHint = (trackInfo?.get("frame_count") as? Long) ?: -1L
            }
        }

        val prepared = if (mp4 == null) {
            prepareUnsupportedVideo(video, isMp4, frameHint, maxVideoBytes, log, onTranscodeProgress)
        } else {
            PreparedVideo(mp4, null)
        }
        val mp4Bytes = prepared.bytes

        try {
            // 视频轨信息（时长/fps 等，vivo 等格式 footer 需要）
            val stem = photo.nameWithoutExtension
            File(outDir).mkdirs()

            val asset = LivePhotoAsset(
                primaryJpeg = jpeg,
                gainmapJpeg = null,
                videoMp4 = mp4Bytes,
                sourceFormat = "compose"
            )
            asset.videoInfo = Mp4Util.getTrackInfo(mp4Bytes) ?: mutableMapOf()
            log("info", "合成：照片 ${jpeg.size}B + 视频 ${mp4Bytes.size}B → ${targetPlugin.display}", "合成")

            return targetPlugin.write(asset, outDir, stem, log, options)
        } finally {
            // 转码 / 重封装的临时产物：合成结束（成功/失败）立即删除
            prepared.temp?.let { runCatching { it.delete() } }
        }
    }

    /** 处理后的视频字节 + 需在合成结束后删除的临时文件（无需清理时为 null） */
    private class PreparedVideo(val bytes: ByteArray, val temp: File?)

    /**
     * 视频不符合标准 MP4 时的处理：按用户在`设置 → 视频转码 → 转码方式`选的方式处理。
     *
     * 三种方式都**不保证成功** —— 成不成功取决于源视频本身，失败时抛 [VideoContainerException]，
     * 并把「该去设置里改成哪一项」写进提示：
     * - [FfmpegAddon.MODE_ENCODE] 重新编码：内置 ffmpeg 重编码为 H.264/H.265，兼容性最好、最慢；
     * - [FfmpegAddon.MODE_REMUX]（默认）仅重封装容器：只换容器不重新编码，快且无损，但源视频编码
     *   本身不是 H.264/H.265 时无从补救；
     * - [FfmpegAddon.MODE_OFF] 完全不用转码器：不调用 ffmpeg，只有已带 MP4 头的视频（QuickTime
     *   MOV 改写品牌即可）能直接使用，MKV/WebM/AVI 等其它容器直接判定失败。
     * 本版本没有转码器时（Go 轻量版）按 [FfmpegAddon.MODE_OFF] 处理。
     *
     * 后两种不改变视频编码，因此产物统一复核：必须是能被解析的 MP4、且视频编码为标准
     * H.264/H.265。不满足就报失败 —— 宁可任务失败，也不产出「可识别但无法播放」的损坏动态照片。
     */
    private suspend fun prepareUnsupportedVideo(
        video: File, isMp4: Boolean, frameHint: Long, maxVideoBytes: Long,
        log: (String, String, String) -> Unit,
        onTranscodeProgress: (frame: Long, total: Long, etaSec: Long) -> Unit
    ): PreparedVideo {
        val ready = FfmpegAddon.isReady()

        // 1) 重新编码：ffmpeg 重编码为标准 MP4（H.265/H.264）
        if (ready && FfmpegAddon.mode == FfmpegAddon.MODE_ENCODE) {
            val out = try {
                FfmpegAddon.transcodeToMp4(video.path, log, frameHint, onTranscodeProgress)
            } catch (e: AddonException) {
                throw VideoContainerException("视频重新编码失败：${e.message}")
            }
            val bytes = out.readBytes()
            if (bytes.size > maxVideoBytes) {
                out.delete()
                throw ConvertException("转码后视频过大（${bytes.size / 1024 / 1024}MB），无法合成动态照片")
            }
            return PreparedVideo(bytes, out)
        }

        // 2) 仅重封装容器 / 完全不用转码器：只换容器，绝不重新编码
        var temp: File? = null
        val bytes = if (isMp4) {
            // 已经是 MP4 容器，只是视频编码不是 H.264/H.265：换容器改变不了编码
            throw VideoContainerException(remuxCannotFixHint(video))
        } else {
            val source = video.readBytes()
            val qt = source.size >= 12 && Mp4Util.hasFtyp(source) &&
                String(source, 8, 4, Charsets.US_ASCII) == "qt  "
            if (!ready || FfmpegAddon.mode == FfmpegAddon.MODE_OFF) {
                // 不调用 ffmpeg：只有 QuickTime MOV 能靠改写 ftyp 品牌零拷贝变成 MP4
                if (!qt) throw VideoContainerException(noEncoderHint())
                log("info", "未启用转码器：只改写容器品牌（MOV → MP4），不重新编码", "转码")
                Mp4Util.movToMp4(source)
            } else {
                val out = try {
                    FfmpegAddon.remuxToMp4(video.path, log)
                } catch (e: AddonException) {
                    throw VideoContainerException("容器重封装失败：${e.message}")
                }
                temp = out
                out.readBytes()
            }
        }

        if (bytes.size > maxVideoBytes) {
            temp?.delete()
            throw ConvertException("视频过大（${bytes.size / 1024 / 1024}MB），无法合成动态照片")
        }
        val codec = (Mp4Util.getTrackInfo(bytes)?.get("codec") as? String).orEmpty()
        if (codec !in FfmpegAddon.STANDARD_MP4_CODECS) {
            temp?.delete()
            throw VideoContainerException(remuxCannotFixHint(video, codec))
        }
        return PreparedVideo(bytes, temp)
    }

    /** 只换容器救不了视频编码时的提示（codec 为空表示连轨道信息都解析不出来） */
    private fun remuxCannotFixHint(video: File, codec: String = ""): String {
        val what = if (codec.isEmpty()) "不是能被识别的 H.264/H.265 视频" else "编码是 $codec"
        return "视频「${video.name}」$what，只换容器（重封装）改变不了编码，无法合成动态照片。\n\n" +
            "请在「设置 → 视频转码 → 转码方式」中改为「重新编码」，" +
            "或先用其它工具把它转成 H.264/H.265 编码的 MP4。"
    }

    /** 没有可用转码器、且视频不是能直接改写品牌的 MOV 时的提示 */
    private fun noEncoderHint(): String =
        "视频不是标准 MP4 容器（MOV / MKV / WebM / AVI 等），" +
        "而当前「转码方式」不重新编码，无法合成动态照片。\n\n" +
        "请在「设置 → 视频转码 → 转码方式」中改为「重新编码」" +
        "（正常版本内置 ffmpeg 编码器；Go 轻量版不含转码器，请改用正常版本），" +
        "或先把视频转成标准 MP4（H.264/H.265）。"

    /**
     * 封面图 → JPEG 字节：JPEG 直接透传（保留 EXIF）；
     * WebP/PNG 等其它格式用内置 Bitmap 解码后以 100% 质量重编码为标准 JPEG。
     * go 轻量版不提供任何转码，非 JPEG 封面直接判失败。
     */
    private fun decodeCoverToJpeg(photo: File): ByteArray {
        val raw = photo.readBytes()
        if (raw.size >= 2 && raw[0] == 0xFF.toByte() && raw[1] == 0xD8.toByte()) return raw
        if (BuildConfig.FLAVOR == "go") {
            throw ConvertException("封面不是 JPEG 图片（轻量版不支持转码，请改用 JPEG 照片）")
        }
        val decoded = BitmapFactory.decodeFile(photo.path)
            ?: throw ConvertException("封面不是有效的图片（仅支持 JPEG/WebP/PNG）")
        // PNG/WebP 透明区域填充纯白：JPEG 无 alpha 通道，直接压缩会把透明区域压成黑色
        val bmp = if (decoded.hasAlpha()) compositeOnWhite(decoded) else decoded
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, bos)
        bmp.recycle()
        return bos.toByteArray()
    }

    /** 透明图片合成到纯白底（返回新位图并回收原图），保证透明区域输出为白色而非黑色。 */
    private fun compositeOnWhite(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, 0f, 0f, null)
        src.recycle()
        return out
    }
}
