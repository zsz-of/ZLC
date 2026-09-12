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
     */
    suspend fun compose(
        photoPath: String, videoPath: String, target: String, outDir: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?> = mutableMapOf()
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

        // 容器头判断：非 MP4（缺 ftyp）或 QuickTime(MOV，brand=qt  ) 需要转码为标准 MP4
        val head = ByteArray(12)
        video.inputStream().use { ins -> ins.read(head) }
        val hasFtyp = Mp4Util.hasFtyp(head)
        val brand = if (hasFtyp) String(head, 8, 4, Charsets.US_ASCII) else ""

        var transcoded: File? = null
        var mp4: ByteArray? = null
        if (hasFtyp && brand != "qt  ") {
            // 标准 MP4 容器：进一步检查视频编码是否为标准 H.264/H.265，
            // 非标准编码（vp09/av01/mp4v 等）也需转码
            val bytes = video.readBytes()
            val codec = (Mp4Util.getTrackInfo(bytes)?.get("codec") as? String).orEmpty()
            if (codec.isEmpty() || codec in FfmpegAddon.STANDARD_MP4_CODECS) {
                mp4 = bytes
            }
        }

        if (mp4 == null) {
            // 需要转码为标准 MP4（H.265/H.264）
            if (!FfmpegAddon.isReady()) {
                throw VideoContainerException(videoTranscodeHint())
            }
            transcoded = try {
                FfmpegAddon.transcodeToMp4(videoPath, log)
            } catch (e: AddonException) {
                throw VideoContainerException("视频转码失败：${e.message}")
            }
            mp4 = transcoded.readBytes()
            if (mp4.size > maxVideoBytes) {
                transcoded.delete()
                throw ConvertException("转码后视频过大（${mp4.size / 1024 / 1024}MB），无法合成动态照片")
            }
        }

        val mp4Bytes = mp4 ?: throw VideoContainerException(videoTranscodeHint())

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
            // 转码临时产物：合成结束（成功/失败）立即删除
            transcoded?.let { runCatching { it.delete() } }
        }
    }

    /** 视频需转码但不可用时的用户提示（go 轻量版不含内置转码器） */
    private fun videoTranscodeHint(): String =
        "视频不是标准 MP4（H.264/H.265 编码），无法直接合成动态照片。\n\n" +
        "正常版本内置 ffmpeg 编码器，会自动转码为标准 MP4；" +
        "Go 轻量版不含转码器，请改用正常版本，或先把视频转码为标准 MP4。"

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
