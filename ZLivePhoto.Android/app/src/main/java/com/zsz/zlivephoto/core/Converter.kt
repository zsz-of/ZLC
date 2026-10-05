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
    suspend fun convertFile(
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

        // 同格式直通：原样复制，零损耗。
        // 两个例外都要改走完整写出流程：
        //   1) vivo_single：源可能是 vivo 相册「关闭实况」的合并产物（MotionPhoto="0"），
        //      需重写 XMP 修复回 "1" 才能恢复动态效果；
        //   2) 源视频夹带非音视频轨（`mett`/`tmcd` 等）：直通只是把缺陷原样复制，
        //      用户「把旧产物再转一次」这种最常见的修法会失效 —— 完整写出流程会在写前净化。
        // 判定需要读一次源（只读解析，不写），干净时才走字节拷贝。
        var reusedAsset: LivePhotoAsset? = null
        if (plugin.name == target && plugin.name != "vivo_single") {
            reusedAsset = runCatching { plugin.read(path, { _, _, _ -> }) }.getOrNull()
            val extraTracks = reusedAsset?.let { VideoTrackSanitizer.nonAvHandlers(it.videoMp4) }
            if (extraTracks.isNullOrEmpty()) {
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
                        // 大小写不敏感：iPhone 导出可能是 IMG_x.MOV
                        val mov = findCompanion(parent, stem, "mov")
                        if (mov != null) {
                            val dstMov = File(outDir, File(mov).name).path
                            File(mov).copyTo(File(dstMov), overwrite = true)
                            directOuts.add(dstMov)
                        }
                    }
                }
                log("info", "源与目标格式相同，已原样复制（零损耗）", "转换")
                return directOuts
            }
            log("info", "源视频含非音视频轨（${extraTracks.joinToString("/")}），"
                + "跳过同格式直通，改走完整写出流程以净化", "转换")
        }

        val asset = reusedAsset ?: plugin.read(path, log)
        // 输入源夹带非音视频轨时先净化（见 sanitizeAssetVideo 注释）
        sanitizeAssetVideo(asset, log)
        // iPhone 默认「高效」格式的封面是 HEIC：除 Apple 目标外都必须内嵌 JPEG
        normalizeCover(asset, targetPlugin.name, log)
        // 10bit/HDR/杜比视界/hev1/PCM 音轨/镜像矩阵：仅重封装解决不了，需按设置决定是否重新编码
        reencodeIfNeeded(asset, log)
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

    /**
     * 写产物前净化视频轨：输入源自带非音视频轨（Apple MOV 的 `mebx`/`mett` 元数据轨、时间码
     * `tmcd` 等）时，本工具的字节级搬运会把它原样带进产物，在部分机型上表现为「相册能识别为
     * 动态照片，但长按无法播放 / 无法编辑」。同时把 `qt  `（QuickTime）品牌归一成 `isom`——
     * 只改 major_brand 会在 compatible_brands 里留下 `qt  `，解析器仍按 QuickTime 处理产物。
     *
     * 净化失败时**不再静默搬运原字节**：至少仍做零风险的品牌归一，并把失败原因报给用户。
     */
    private fun sanitizeAssetVideo(
        asset: LivePhotoAsset, log: (String, String, String) -> Unit
    ) {
        val extra = VideoTrackSanitizer.nonAvHandlers(asset.videoMp4)
        val brandNormalized = Mp4Util.normalizeFtyp(asset.videoMp4)
        if (extra.isEmpty()) {
            // 无附加轨：只做字节数不变的品牌归一（Apple 来源即便已剔轨也可能残留 qt 品牌）
            if (!brandNormalized.contentEquals(asset.videoMp4)) {
                asset.videoMp4 = brandNormalized
                log("info", "已归一化视频容器品牌（QuickTime → isom）", "转换")
            }
            return
        }
        val cleaned = VideoTrackSanitizer.sanitize(asset.videoMp4, log)
        if (cleaned == null) {
            asset.videoMp4 = brandNormalized
            log(
                "warning",
                "视频含附加轨（${extra.joinToString("/")}），无法净化，已按原样输出（该产物在部分机型可能无法长按播放）",
                "转换"
            )
            return
        }
        log("info", "已剔除视频附加轨（${extra.joinToString("/")}）", "转换")
        asset.videoMp4 = cleaned
        asset.videoInfo = Mp4Util.getTrackInfo(cleaned) ?: asset.videoInfo
    }

    /**
     * 「仅重封装容器」改动的是容器，改不动码流；下列问题只能靠**重新编码**解决
     * （见 [Mp4Util.videoCompat]）：10bit H.265、HDR（PQ/HLG）、杜比视界、`hev1` 标记、
     * 非 AAC 音轨（Apple 常见 PCM）、镜像变换矩阵、非 H.264/H.265 编码。
     *
     * 处理策略尊重「设置 → 视频转码 → 转码方式」这一显式选择：
     * - 已选「重新编码」且内置转码器可用 → 转成 8bit H.264/H.265 + AAC（兼容性最好）；
     * - 其它情况（默认「仅重封装容器」/「不使用」/ go 轻量版）→ **不擅自重编码**，
     *   只明确告知风险与开关位置，产物仍与改动前一致（不会因为本判定而失败）。
     */
    private suspend fun reencodeIfNeeded(
        asset: LivePhotoAsset, log: (String, String, String) -> Unit
    ) {
        val compat = Mp4Util.videoCompat(asset.videoMp4)
        if (!compat.needsReencode) return
        val why = compat.reasons.joinToString("；")

        if (FfmpegAddon.mode != FfmpegAddon.MODE_ENCODE || !FfmpegAddon.isReady()) {
            val blocker = if (FfmpegAddon.mode == FfmpegAddon.MODE_ENCODE) {
                "内置转码器不可用"
            } else {
                "当前「转码方式」不重新编码"
            }
            log(
                "warning",
                "源视频$why；仅重封装容器无法解决（$blocker），"
                    + "如产物仍无法长按播放，请在「设置 → 视频转码 → 转码方式」改选「重新编码」",
                "转码"
            )
            return
        }

        val tmpIn = FfmpegAddon.tempFile("reencode_src_", ".mp4")
        try {
            tmpIn.writeBytes(asset.videoMp4)
            log("info", "源视频$why，按设置重新编码为 8bit + AAC 以提升相册兼容性", "转码")
            val out = FfmpegAddon.transcodeToMp4(tmpIn.path, log)
            val bytes = runCatching { out.readBytes() }.getOrDefault(ByteArray(0))
            runCatching { out.delete() }
            if (bytes.isEmpty() || !Mp4Util.hasFtyp(bytes)) {
                log("warning", "重新编码结果不可用，已按原样输出（该产物在部分机型可能无法长按播放）", "转码")
                return
            }
            asset.videoMp4 = Mp4Util.normalizeFtyp(bytes)
            asset.videoInfo = Mp4Util.getTrackInfo(asset.videoMp4) ?: asset.videoInfo
            val after = Mp4Util.videoCompat(asset.videoMp4)
            if (after.needsReencode) {
                // 例：镜像矩阵、或设置里选了 H.265 时的 HDR 传输特性 —— 没有彻底消除
                log("info", "重新编码后仍存在风险项（${after.reasons.joinToString("；")}）", "转码")
            }
        } catch (e: Exception) {
            log("warning", "重新编码失败（${e.message}），已按原样输出（该产物在部分机型可能无法长按播放）", "转码")
        } finally {
            runCatching { tmpIn.delete() }
        }
    }

    /**
     * 在 [parent] 目录内查找同名同伴文件（大小写不敏感，如 `IMG_x.MOV`）。
     * iPhone 导出的视频扩展名可能是 `.MOV`，只认小写会丢掉视频。
     */
    private fun findCompanion(parent: File?, stem: String, ext: String): String? {
        if (parent == null) return null
        for (candidate in listOf(ext.lowercase(), ext.uppercase())) {
            val f = File(parent, "$stem.$candidate")
            if (f.exists()) return f.path
        }
        return null
    }

    /**
     * 封面归一：iPhone 默认「高效」格式导出的实况照片主图是 HEIC，而 Google/OPPO/vivo/小米等
     * 目标格式的容器都只声明并内嵌 JPEG。此前 HEIC 会在 XMP 写入时抛
     * `JpegException("不是有效的 JPEG（缺少 SOI）")` 导致整次转换失败；这里统一转成标准 JPEG。
     *
     * Apple → Apple 保留原字节（写回 `.heic`），不损失画质。
     */
    private fun normalizeCover(
        asset: LivePhotoAsset, target: String, log: (String, String, String) -> Unit
    ) {
        val raw = asset.primaryJpeg
        val isJpeg = raw.size >= 2 && raw[0] == 0xFF.toByte() && raw[1] == 0xD8.toByte()
        if (isJpeg || target == "apple") return
        if (BuildConfig.FLAVOR == "go") {
            throw ConvertException("封面是 HEIC 图片（轻量版不支持转码，请在 iPhone 上导出为 JPEG 后再转换）")
        }
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: throw ConvertException("封面不是有效的图片（HEIC 需要系统支持 HEIF 解码）")
        val bmp = if (decoded.hasAlpha()) compositeOnWhite(decoded) else decoded
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, bos)
        bmp.recycle()
        asset.primaryJpeg = bos.toByteArray()
        log("info", "封面为 HEIC（非 JPEG），已转码为标准 JPEG 后再封装", "转换")
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
            // 合成素材同样可能夹带附加轨（例如用户直接用带 mett 的 MP4 当素材）
            sanitizeAssetVideo(asset, log)
            log("info", "合成：照片 ${jpeg.size}B + 视频 ${asset.videoMp4.size}B → ${targetPlugin.display}", "合成")

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
