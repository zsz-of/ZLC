package com.zsz.zlivephoto.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
    fun compose(
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
        val mp4 = video.readBytes()
        if (!Mp4Util.hasFtyp(mp4)) {
            // hasFtyp 失败说明文件头 4-7 字节不是 "ftyp"，绝大多数是选错了容器：
            // 下载目录里的视频常为 WebM/MKV（EBML 头）或 AVI/TS，并非标准 MP4。
            // 这里给出具体容器类型，便于用户知道是格式不兼容而非程序崩溃。
            val kind = when {
                mp4.size >= 4 && mp4[0] == 0x1A.toByte() && mp4[1] == 0x45.toByte() &&
                    mp4[2] == 0xDF.toByte() && mp4[3] == 0xA3.toByte() -> "WebM/Matroska"
                mp4.size >= 4 && mp4[0] == 'R'.code.toByte() && mp4[1] == 'I'.code.toByte() &&
                    mp4[2] == 'F'.code.toByte() && mp4[3] == 'F'.code.toByte() -> "AVI"
                mp4.isNotEmpty() && mp4[0] == 0x47.toByte() -> "MPEG-TS"
                else -> "缺少 ftyp 头（可能是 MOV 或非标准 MP4）"
            }
            throw VideoContainerException(
                "视频不是有效的 MP4 文件（检测到 $kind）。\n\n" +
                "请先用其它工具把视频转为标准 MP4（H.264/AAC）后，再重新合成。"
            )
        }
        // ftyp 品牌为 QuickTime(qt  ) 的是 MOV 容器：字节拼接进动态照片后相册/播放器
        // 无法识别，同样需要先转为标准 MP4
        val brand = if (mp4.size >= 12) String(mp4, 8, 4, Charsets.US_ASCII) else ""
        if (brand == "qt  ") {
            throw VideoContainerException(
                "视频是 QuickTime(MOV) 容器，不能直接合成动态照片。\n\n" +
                "请先用其它工具把视频转为标准 MP4（H.264/AAC）后，再重新合成。"
            )
        }

        // 视频轨信息（时长/fps 等，vivo 等格式 footer 需要）
        val stem = photo.nameWithoutExtension
        File(outDir).mkdirs()

        val asset = LivePhotoAsset(
            primaryJpeg = jpeg,
            gainmapJpeg = null,
            videoMp4 = mp4,
            sourceFormat = "compose"
        )
        asset.videoInfo = Mp4Util.getTrackInfo(mp4) ?: mutableMapOf()
        log("info", "合成：照片 ${jpeg.size}B + 视频 ${mp4.size}B → ${targetPlugin.display}", "合成")

        return targetPlugin.write(asset, outDir, stem, log, options)
    }

    /**
     * 封面图 → JPEG 字节：JPEG 直接透传（保留 EXIF）；WebP 等其它格式解码后重编码为 JPEG。
     */
    private fun decodeCoverToJpeg(photo: File): ByteArray {
        val raw = photo.readBytes()
        if (raw.size >= 2 && raw[0] == 0xFF.toByte() && raw[1] == 0xD8.toByte()) return raw
        val bmp = BitmapFactory.decodeFile(photo.path)
            ?: throw ConvertException("封面不是有效的图片（仅支持 JPEG/WebP）")
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, bos)
        bmp.recycle()
        return bos.toByteArray()
    }
}
