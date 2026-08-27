package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.ExifUtil
import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.XmpTemplate
import java.io.File
import java.io.FileInputStream

/**
 * 小米动态照片格式（Xiaomi Motion Photo）。
 * 本质 = Google Motion Photo + EXIF 0x8897 标签 + XMP 双标签（MicroVideo + MotionPhoto）。
 */
internal class XiaomiPlugin : FormatPlugin() {
    override val name: String = "xiaomi"
    override val display: String = "小米动态照片"

    companion object {
        /** 小米相册识别的 EXIF 标签（十进制 34967） */
        const val xiaomiExifTag: Int = 0x8897
    }

    override fun detect(path: String): Int {
        val xmpText = GooglePlugin.sniffXmp(path)
        val info = XmpTemplate.parseMotionXmp(xmpText)

        // 双标签并存是小米的强特征
        if (info.hasBoth && !info.hasOplus) return 95

        // EXIF 0x8897 存在也是小米特征（小米相机写在 ExifIFD，可能无 MicroVideo 双标签，
        // 布局与 Google 纯 Container 相同；92 分压过 Google 的 90 避免误判）
        try {
            FileInputStream(path).use { fs ->
                val fileSize = File(path).length().toInt()
                val bufSize = minOf(2 * 1024 * 1024, fileSize)
                if (bufSize < 2) return 0
                val head = ByteArray(bufSize)
                var read = 0
                while (read < bufSize) {
                    val n = fs.read(head, read, bufSize - read)
                    if (n < 0) break
                    read += n
                }
                if (read >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() &&
                    ExifUtil.hasExifTag(head.copyOfRange(0, read), xiaomiExifTag)
                ) return 92
            }
        } catch (e: Exception) {
            // 读取失败
        }
        return 0
    }

    override fun read(path: String, log: (String, String, String) -> Unit): LivePhotoAsset {
        log("info", "按小米动态照片解析（Google 兼容）", "小米")
        return EmbeddedReader.readEmbedded(path, name, log)
    }

    override fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?>
    ): MutableList<String> {
        val video = asset.videoMp4
        val pts = asset.effectivePtsUs()
        val xmp = XmpTemplate.buildXiaomiXmp(pts, asset.gainmapLength, video.size)
        var primary = JpegUtil.replaceOrInsertXmp(asset.primaryJpeg, xmp)
        // 写入 EXIF 0x8897 = 1（小米相册识别标签；写入 ExifIFD，与小米相机一致，
        // 采用追加+指针改写策略，不移动既有 EXIF 数据，GPS/镜头等元数据零损坏）
        primary = ExifUtil.addExifIfdTag(primary, xiaomiExifTag, 1, 1)

        val gainmapLen = asset.gainmapJpeg?.size ?: 0
        val output = ByteArray(primary.size + gainmapLen + video.size)
        var pos = 0
        System.arraycopy(primary, 0, output, pos, primary.size); pos += primary.size
        asset.gainmapJpeg?.let {
            System.arraycopy(it, 0, output, pos, it.size); pos += it.size
        }
        System.arraycopy(video, 0, output, pos, video.size)

        val outPath = File(outDir, "$stem.jpg").path
        writeBytes(outPath, output)
        log("info", "写出小米格式：${File(outPath).name}" +
            "（图像 ${primary.size}B + 视频 ${video.size}B，EXIF 0x8897=1）", "小米")
        return mutableListOf(outPath)
    }
}
