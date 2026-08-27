package com.zsz.zlivephoto.core.formats

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.zsz.zlivephoto.core.BinaryUtils
import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.Mp4Util
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
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

        // Apple MOV 为 QuickTime 容器（含 mett 元数据轨 + com.apple.quicktime.* 元数据），
        // 仅改 ftyp 品牌（qt  → isom）后相册可识别但无法播放；
        // 需用 MediaExtractor/MediaMuxer 重新封装为纯 MP4（仅保留音/视频轨）。
        val videoMp4 = remuxMovToMp4(movPath)
            ?: Mp4Util.movToMp4(movData) // 重封装失败则回退字节级品牌补丁

        val asset = LivePhotoAsset(
            primaryJpeg = primary,
            gainmapJpeg = null,
            videoMp4 = videoMp4,
            sourceFormat = name,
        )
        asset.presentationTsUs = 0 // Apple StillImageTime=0
        asset.videoInfo = Mp4Util.getTrackInfo(movData) ?: mutableMapOf()
        return asset
    }

    /**
     * 用 MediaExtractor + MediaMuxer 把 Apple QuickTime MOV 重新封装为纯 MP4：
     * 仅保留音/视频轨，丢弃 mett 元数据轨与 com.apple.quicktime.* 元数据。
     * 失败返回 null（调用方回退字节级品牌补丁）。
     */
    private fun remuxMovToMp4(movPath: String): ByteArray? {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var tmp: File? = null
        var started = false
        return try {
            extractor.setDataSource(movPath)

            // 收集音视频轨（丢弃 QuickTime 元数据/文本轨）
            val srcTracks = ArrayList<Int>()
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) srcTracks.add(i)
            }
            if (srcTracks.isEmpty()) return null

            tmp = File.createTempFile("zlive_remux", ".mp4")
            muxer = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // addTrack 可能因 MediaMuxer 不支持某轨道（如 PCM 音频）而抛异常，逐轨跳过
            val dstTracks = IntArray(srcTracks.size)
            var validCount = 0
            for ((idx, src) in srcTracks.withIndex()) {
                try {
                    dstTracks[idx] = muxer.addTrack(extractor.getTrackFormat(src))
                    validCount++
                } catch (_: Exception) {
                    dstTracks[idx] = -1
                }
            }
            if (validCount == 0) return null

            muxer.start()
            started = true

            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val src = extractor.sampleTrackIndex
                val dstIdx = srcTracks.indexOf(src)
                if (dstIdx >= 0 && dstTracks[dstIdx] >= 0) {
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                        MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    muxer.writeSampleData(dstTracks[dstIdx], buffer, info)
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null

            val out = tmp.readBytes()
            if (out.isNotEmpty()) out else null
        } catch (_: Exception) {
            null
        } finally {
            if (muxer != null) {
                try { if (started) muxer.stop() } catch (_: Exception) {}
                try { muxer.release() } catch (_: Exception) {}
            }
            try { extractor.release() } catch (_: Exception) {}
            try { tmp?.delete() } catch (_: Exception) {}
        }
    }

    override fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?>
    ): MutableList<String> {
        // Apple 规范要求带连字符的标准 UUID（如 1E874403-E522-4589-948A-E97AC157F32D）
        val contentId = UUID.randomUUID().toString().uppercase()

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
