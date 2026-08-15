package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.BinaryUtils
import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.Mp4Util
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

/**
 * Apple Live Photo 格式（JPG + MOV 双文件）。
 */
internal class ApplePlugin : FormatPlugin() {
    override val name: String = "apple"
    override val display: String = "Apple Live Photo"

    private val appleXmpNs: String = "xmlns:apple-fi=\"http://ns.apple.com/finalcut/1.0/\""

    private fun findMovSibling(path: String): String? {
        val file = File(path)
        val parent = file.parentFile
        val baseName = if (parent != null) File(parent, file.nameWithoutExtension).path else file.nameWithoutExtension
        for (ext in listOf(".mov", ".MOV")) {
            val mov = baseName + ext
            if (File(mov).exists()) return mov
        }
        return null
    }

    private fun buildAppleXmp(contentId: String): String =
        "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Adobe XMP Core 5.1.0-jc003\">\n" +
        "  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
        "    <rdf:Description rdf:about=\"\"\n" +
        "        $appleXmpNs\n" +
        "      apple-fi:ContentIdentifier=\"$contentId\"/>\n" +
        "  </rdf:RDF>\n" +
        "</x:xmpmeta>\n"

    override fun detect(path: String): Int {
        val ext = File(path).extension.lowercase()
        if (ext != "jpg" && ext != "jpeg" && ext != "heic") return 0

        val mov = findMovSibling(path) ?: return 0

        return try {
            FileInputStream(mov).use { fs ->
                val header = ByteArray(16)
                var read = 0
                while (read < 16) {
                    val n = fs.read(header, read, 16 - read)
                    if (n < 0) break
                    read += n
                }
                val ftypBytes = byteArrayOf(0x66, 0x74, 0x79, 0x70) // "ftyp"
                if (read >= 12 && BinaryUtils.arrayEquals(header, 4, ftypBytes)) {
                    val qt  = byteArrayOf(0x71, 0x74, 0x20, 0x20) // "qt  "
                    val isom = byteArrayOf(0x69, 0x73, 0x6F, 0x6D) // "isom"
                    val mp41 = byteArrayOf(0x6D, 0x70, 0x34, 0x31) // "mp41"
                    val mp42 = byteArrayOf(0x6D, 0x70, 0x34, 0x32) // "mp42"
                    val msnv = byteArrayOf(0x4D, 0x53, 0x4E, 0x56) // "MSNV"
                    when {
                        BinaryUtils.arrayEquals(header, 8, qt) -> 90
                        BinaryUtils.arrayEquals(header, 8, isom) ||
                            BinaryUtils.arrayEquals(header, 8, mp41) ||
                            BinaryUtils.arrayEquals(header, 8, mp42) ||
                            BinaryUtils.arrayEquals(header, 8, msnv) -> 75
                        else -> 60
                    }
                } else 60
            }
        } catch (e: Exception) {
            60
        }
    }

    override fun read(path: String, log: (String, String, String) -> Unit): LivePhotoAsset {
        log("info", "按 Apple Live Photo 解析", "Apple")
        val movPath = findMovSibling(path)
            ?: throw IOException("未找到同名 .mov 视频文件")

        val primary = readBytes(path)
        val movData = readBytes(movPath)
        if (!Mp4Util.hasFtyp(movData)) {
            throw IOException("MOV 文件缺少 ftyp box")
        }

        val asset = LivePhotoAsset(
            primaryJpeg = primary,
            gainmapJpeg = null,
            videoMp4 = movData,
            sourceFormat = name,
        )
        asset.presentationTsUs = 0 // Apple StillImageTime=0
        asset.videoInfo = Mp4Util.getTrackInfo(movData) ?: mutableMapOf()
        return asset
    }

    override fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?>
    ): MutableList<String> {
        val contentId = UUID.randomUUID().toString().replace("-", "").uppercase()

        val xmp = buildAppleXmp(contentId)
        val primary = JpegUtil.replaceOrInsertXmp(asset.primaryJpeg, xmp)
        val jpgPath = File(outDir, "$stem.jpg").path
        writeBytes(jpgPath, primary)

        var movData = Mp4Util.mp4ToMov(asset.videoMp4)
        movData = Mp4Util.addAppleMetadata(movData, contentId)
        val movPath = File(outDir, "$stem.mov").path
        writeBytes(movPath, movData)

        log("info", "写出 Apple 格式：$stem.jpg + $stem.mov" +
            "（ContentIdentifier=${contentId.substring(0, 8)}...）", "Apple")
        return mutableListOf(jpgPath, movPath)
    }
}
