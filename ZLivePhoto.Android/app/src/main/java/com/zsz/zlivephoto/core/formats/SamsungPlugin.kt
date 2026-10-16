package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.BinaryUtils
import com.zsz.zlivephoto.core.CoreText
import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.Mp4Util
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * 三星 Galaxy 动态照片（Motion Photo）单文件格式。
 *
 * 与其它厂商最根本的差异：**没有任何 XMP / footer 标记**，动态属性完全由文件尾的
 * [SamsungSef] 尾部块（`SEFH` … `SEFT`）承载。因此
 * - [detect] 只看尾部块，不看 XMP；
 * - Google 插件对这类文件返回 0（无 motion XMP），两者不会互相抢识别。
 *
 * 文件形态：`[JPEG 主图] [MP4 载荷（SEF 记录 0xA30 "MotionPhoto_Data"）] [SEF 尾部块]`。
 * JPEG 之后**没有** `MicroVideoOffset` 那种「从文件尾回数」的约定，视频长度由 MP4 自身的
 * box 结构决定，故读取端在 JPEG 之后直接搜 `ftyp` 再取 `Mp4Util.streamLength`。
 *
 * 另有一种 HEIC 变体（尾部块内是 `MotionPhoto_Data` + `mpv2` 签名，视频作为 HEIF item 存放），
 * 需要完整解析 HEIF `meta`/`iloc`/`iinf` box 才能取出，本插件**暂不支持**（[detect] 对
 * `.heic` 直接返回 0，避免误判为可读）。
 */
internal class SamsungPlugin : FormatPlugin() {
    override val name: String = "samsung"
    override val displayRes: Int = R.string.fmt_samsung

    /** 只看文件尾 8 字节 + 块起点 4 字节，避免为识别而读整文件。 */
    private fun looksLikeSefTail(path: String): Boolean {
        return try {
            val f = File(path)
            val size = f.length()
            if (size < (SamsungSef.BLOCK_OVERHEAD + SamsungSef.RECORD_SIZE).toLong()) return false
            FileInputStream(path).use { fis ->
                val tail8 = ByteArray(8)
                fis.channel.position(size - 8)
                var read = 0
                while (read < 8) {
                    val n = fis.read(tail8, read, 8 - read)
                    if (n < 0) return false
                    read += n
                }
                if (!BinaryUtils.arrayEquals(tail8, 4, SamsungSef.TRAILER)) return false
                val blockSizeMinus8 = BinaryUtils.readU32LE(tail8, 0)
                if (blockSizeMinus8 <= 0L || blockSizeMinus8 > Int.MAX_VALUE) return false
                val blockStart = size - (blockSizeMinus8 + 8)
                if (blockStart < 0) return false
                val head = ByteArray(4)
                fis.channel.position(blockStart)
                read = 0
                while (read < 4) {
                    val n = fis.read(head, read, 4 - read)
                    if (n < 0) return false
                    read += n
                }
                BinaryUtils.arrayEquals(head, 0, SamsungSef.HEADER)
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun detect(path: String): Int {
        val lower = path.lowercase()
        if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) return 0
        // HEIC 变体（SEF 里带 mpv2 的 HEIF）暂不可读，别把它算作三星 JPEG 动态照片。
        return if (looksLikeSefTail(path)) 95 else 0
    }

    override fun read(path: String, log: (String, String, String) -> Unit): LivePhotoAsset {
        log("info", CoreText.of(R.string.fmt_log_parse_samsung), "Samsung")
        val data = readBytes(path)
        val sef = SamsungSef.parse(data)
            ?: throw IOException(CoreText.of(R.string.fmt_err_samsung_no_sef))

        val (jpegs, consumed) = JpegUtil.splitJpegs(data)
        if (jpegs.isEmpty()) throw IOException(CoreText.of(R.string.fmt_err_no_primary_jpeg))
        val primary = jpegs[0]
        val gainmap = if (jpegs.size > 1) jpegs[1] else null

        // 视频区 = JPEG 之后 ~ 尾部块之前。三星这里可能还夹着别的 SEF 载荷，
        // 因此先搜 ftyp 定位 MP4 起点，再按 MP4 自身 box 结构算出真实长度。
        val regionEnd = sef.blockStart.coerceIn(consumed, data.size)
        val region = data.copyOfRange(consumed.coerceIn(0, data.size), regionEnd)
        val ftypIdx = EmbeddedReader.findFtyp(region)
        if (ftypIdx < 0) throw IOException(CoreText.of(R.string.fmt_err_samsung_no_video))
        val mp4Region = region.copyOfRange(ftypIdx, region.size)
        val mp4Len = Mp4Util.streamLength(mp4Region)
        if (mp4Len <= 0) throw IOException(CoreText.of(R.string.fmt_err_mp4_parse_failed))
        val video = mp4Region.copyOfRange(0, mp4Len)

        val asset = LivePhotoAsset(
            primaryJpeg = primary,
            gainmapJpeg = gainmap,
            videoMp4 = video,
            sourceFormat = name,
        )
        asset.videoInfo = Mp4Util.getTrackInfo(video) ?: mutableMapOf()
        // 三星不写 XMP 时间戳；封面帧时刻未知，交由 Converter 取视频中点并把曲线记进日志。
        asset.presentationTsUs = -1L
        asset.extras["samsung_sef_version"] = sef.version
        asset.extras["samsung_sef_types"] = sef.records.map { it.type }
        log(
            "info",
            CoreText.of(R.string.fmt_log_samsung_sef, sef.version, sef.records.size),
            "Samsung"
        )
        return asset
    }

    override fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?>
    ): MutableList<String> {
        val video = asset.videoMp4

        // 载荷：类型 0xA30 / option 0 / 名 "MotionPhoto_Data"，业务数据就是 MP4 本体。
        val record = SamsungSef.Record(
            SamsungSef.TYPE_MOTION_PHOTO_DATA, 0,
            SamsungSef.KEY_MOTION_PHOTO_DATA, video
        )
        val segment = SamsungSef.buildSegment(SamsungSef.DEFAULT_VERSION, listOf(record))

        val gainmapLen = asset.gainmapJpeg?.size ?: 0
        val output = ByteArray(asset.primaryJpeg.size + gainmapLen + segment.size)
        var pos = 0
        System.arraycopy(asset.primaryJpeg, 0, output, pos, asset.primaryJpeg.size)
        pos += asset.primaryJpeg.size
        asset.gainmapJpeg?.let {
            System.arraycopy(it, 0, output, pos, it.size)
            pos += it.size
        }
        System.arraycopy(segment, 0, output, pos, segment.size)

        val outPath = File(outDir, "$stem.jpg").path
        writeBytes(outPath, output)
        log(
            "info",
            CoreText.of(
                R.string.fmt_log_samsung_write, File(outPath).name,
                asset.primaryJpeg.size, video.size, segment.size
            ),
            "Samsung"
        )
        return mutableListOf(outPath)
    }
}
