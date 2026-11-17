package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.BinaryUtils
import com.zsz.zlivephoto.core.CoreText
import com.zsz.zlivephoto.core.ExifUtil
import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.Mp4Util
import com.zsz.zlivephoto.core.VideoTrackSanitizer
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

/**
 * Apple Live Photo 格式（JPG + MOV 双文件）。
 */
internal class ApplePlugin : FormatPlugin() {
    override val name: String = "apple"
    override val displayRes: Int = R.string.fmt_apple

    companion object {
        /** Apple MakerNote 里的配对标识键（exiftool Apple.pm：0x0011 ContentIdentifier，ASCII 字符串）。 */
        private const val TAG_CONTENT_IDENTIFIER = 0x0011

        /** 修复 ftyp 时只在文件前 1 MiB 内找（足够跨过 wide/free 之类的填充 atom）。 */
        private const val FTYP_SCAN_LIMIT = 1 shl 20

        /** 允许「没有 ftyp」的顶层首 box：ISO BMFF 里 ftyp 本就是可选 box。 */
        private val PREFIX_TOLERANT_TYPES = setOf("moov", "mdat", "wide", "free", "skip", "uuid", "pnot")
    }

    /**
     * 补齐/前移 ftyp：`ftyp` 在 ISO BMFF/QuickTime 里是**可选** box，真实语料中存在
     * 直接从 `moov`/`mdat`/`wide` 开头的 MOV（第三方导出、备份工具还原），旧实现只认
     * 「偏移 4 处是 ftyp」，把这批文件一律判成「MOV 文件缺少 ftyp box」直接失败。
     */
    private fun ensureFtyp(data: ByteArray): ByteArray {
        if (Mp4Util.hasFtyp(data)) return data
        if (data.size < 8) return data

        // ① ftyp 被 wide/free/skip 等填充 atom 顶到了后面：找到后整体前移
        val head = data.copyOfRange(0, minOf(data.size, FTYP_SCAN_LIMIT))
        val ftypIdx = EmbeddedReader.findFtyp(head)
        if (ftypIdx > 0) return data.copyOfRange(ftypIdx, data.size)

        // ② 文件本身没有 ftyp（合法布局）：补 20 字节 QuickTime ftyp 头
        val firstType = String(data, 4, 4, Charsets.ISO_8859_1)
        if (firstType in PREFIX_TOLERANT_TYPES) {
            val ftyp = byteArrayOf(
                0x00, 0x00, 0x00, 0x14, // size = 20
                0x66, 0x74, 0x79, 0x70, // "ftyp"
                0x71, 0x74, 0x20, 0x20, // major_brand "qt  "
                0x00, 0x00, 0x02, 0x00, // minor_version
                0x71, 0x74, 0x20, 0x20, // compatible_brands "qt  "
            )
            return ftyp + data
        }
        return data
    }

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
        log("info", CoreText.of(R.string.fmt_log_parse_apple), "Apple")
        val movPath = findMovSibling(path)
            ?: throw IOException(CoreText.of(R.string.fmt_err_apple_mov_missing))

        val primary = readBytes(path)
        val movData = ensureFtyp(readBytes(movPath))
        if (!Mp4Util.hasFtyp(movData)) {
            throw IOException(CoreText.of(R.string.fmt_err_apple_mov_no_ftyp))
        }

        // Apple MOV 为 QuickTime 容器（含 mett 元数据轨 + com.apple.quicktime.* 元数据），
        // 仅改 ftyp 品牌（qt  → isom）后相册可识别但无法播放；
        // 统一交给 VideoTrackSanitizer 重封装为只含音视频轨的 MP4
        // （它要求视频轨必须加入成功，否则返回 null，不会产出只有音频的产物）。
        val remuxed = VideoTrackSanitizer.sanitize(movData, log)
        val videoMp4: ByteArray
        if (remuxed != null) {
            videoMp4 = remuxed
        } else {
            // 重封装失败时只能做字节级品牌补丁，这种产物相册可能识别但无法播放；
            // 必须告警，并且只有在产物确实带视频轨时才允许继续（format-matrix R3）。
            val patched = Mp4Util.movToMp4(movData)
            val codec = Mp4Util.getTrackInfo(patched)?.get("codec") as? String
            if (!Mp4Util.hasFtyp(patched) || codec.isNullOrEmpty()) {
                throw IOException(CoreText.of(R.string.fmt_err_apple_remux_failed))
            }
            log("warning", CoreText.of(R.string.fmt_log_apple_remux_fallback), "Apple")
            videoMp4 = patched
        }

        val asset = LivePhotoAsset(
            primaryJpeg = primary,
            gainmapJpeg = null,
            videoMp4 = videoMp4,
            sourceFormat = name,
        )
        // 静帧时刻：真实 Apple MOV 由 `mebx` timed metadata 轨（elst 空 edit）承载。
        // 此前硬编码 0，会让 Apple → 小米/Google/OPPO/vivo 的
        // MotionPhotoPresentationTimestampUs 恒为 0、封面被定位到视频第 0 帧。
        val stillUs = Mp4Util.appleStillImageTimeUs(movData)
        asset.presentationTsUs = stillUs
        if (stillUs >= 0L) {
            log("info", CoreText.of(R.string.fmt_log_apple_still_found, stillUs / 1000), "Apple")
        } else {
            log("warning", CoreText.of(R.string.fmt_log_apple_still_missing), "Apple")
        }
        asset.videoInfo = Mp4Util.getTrackInfo(movData) ?: mutableMapOf()
        return asset
    }

    override fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?>
    ): MutableList<String> {
        // Apple 规范要求带连字符的标准 UUID（如 1E874403-E522-4589-948A-E97AC157F32D）
        val contentId = UUID.randomUUID().toString().uppercase()

        val xmp = buildAppleXmp(contentId)
        // 主图可能是 HEIC（iPhone「高效」格式）：HEIC 不能插入 JPEG APP1 XMP 段，
        // 此时保持原字节并按真实扩展名写出，配对标识只落在 MOV 的 udta/meta 里。
        val isJpeg = asset.primaryJpeg.size >= 2 &&
            asset.primaryJpeg[0] == 0xFF.toByte() && asset.primaryJpeg[1] == 0xD8.toByte()
        val primary = if (isJpeg) {
            // ① XMP（保持既有兼容性）；② Apple 真正用来配对的标识：
            //    EXIF MakerNote（0x927C）内部 IFD 的 0x0011 = ContentIdentifier（ASCII UUID）。
            //    官方 AVCapturePhotoSettings.livePhotoMovieMetadata 文档指向
            //    kCGImagePropertyExifMakerNote；exiftool Apple.pm 给出键号 0x0011。
            ExifUtil.upsertMakerNoteAsciiTag(
                JpegUtil.replaceOrInsertXmp(asset.primaryJpeg, xmp),
                TAG_CONTENT_IDENTIFIER,
                contentId
            )
        } else {
            log(
                "warning",
                CoreText.of(R.string.fmt_log_apple_heic_no_pair),
                "Apple"
            )
            asset.primaryJpeg
        }
        val ext = if (isJpeg) "jpg" else "heic"
        val jpgPath = File(outDir, "$stem.$ext").path
        writeBytes(jpgPath, primary)

        val ptsUs = asset.effectivePtsUs()
        if (ptsUs >= 0L) {
            // 真实 Apple 用 mebx timed metadata 轨记录静帧时刻；本工具暂不合成该轨，
            // MOV 里的 still-image-time 按 Apple 约定写 -1（见 Mp4Util.addAppleMetadata）。
            log(
                "warning",
                CoreText.of(R.string.fmt_log_apple_still_no_carrier, ptsUs / 1000),
                "Apple"
            )
        }
        var movData = Mp4Util.mp4ToMov(asset.videoMp4)
        movData = Mp4Util.addAppleMetadata(movData, contentId)
        val movPath = File(outDir, "$stem.mov").path
        writeBytes(movPath, movData)

        log(
            "info",
            CoreText.of(R.string.fmt_log_apple_write, "$stem.$ext", "$stem.mov", contentId.substring(0, 8)),
            "Apple"
        )
        return mutableListOf(jpgPath, movPath)
    }
}
