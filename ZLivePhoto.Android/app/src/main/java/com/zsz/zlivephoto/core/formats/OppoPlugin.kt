package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.BinaryUtils
import com.zsz.zlivephoto.core.CoreText
import com.zsz.zlivephoto.core.FooterUtil
import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.Mp4Util
import com.zsz.zlivephoto.core.XmpTemplate
import java.io.File
import java.io.FileInputStream

/**
 * OPPO / oplus 单文件动态照片格式。
 */
internal class OppoPlugin : FormatPlugin() {
    override val name: String = "oppo"
    override val displayRes: Int = R.string.fmt_oppo

    internal companion object {
        /** 合成 lpex (LivePhotoExtension) box 载荷（vivo/OPPO 共用；字段逐字对齐可被相册识别的输出） */
        fun buildLpexPayload(asset: LivePhotoAsset): ByteArray {
            val vi = asset.videoInfo
            val vw = (vi["width"] as? Int) ?: 0
            val vh = (vi["height"] as? Int) ?: 0
            val (iw, ih) = JpegUtil.getDimensions(asset.primaryJpeg)

            val payload = linkedMapOf<String, Any?>(
                "coverFramePts" to asset.effectivePtsUs(),
                "cropRect" to intArrayOf(0, 0, vw, vh),
                "desc" to "OppoMotionVideoExt",
                "matrixCount" to 0,
                "originPhotoSize" to intArrayOf(iw, ih),
                "photoCropFactor" to 1.0,
                "photoCropMatrix" to doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
                "photoCropRect" to intArrayOf(0, 0, iw, ih),
                "photoEisCropFactor" to doubleArrayOf(1.0, 1.0),
                "photoEisMatrix" to doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0),
                "subVideoScaleFactor" to 0.5,
                "version" to 1,
                "videoOrientation" to ((vi["rotation"] as? Int) ?: 0),
                "videoSize" to intArrayOf(vw, vh)
            )
            val jsonBytes = FooterUtil.buildFooterJson(payload)
            val prefix = "LivePhotoExtension".toByteArray(Charsets.US_ASCII)
            return prefix + jsonBytes
        }
    }

    override fun detect(path: String): Int {
        val xmp = GooglePlugin.sniffXmp(path)
        if (xmp.isEmpty()) return 0

        val info = XmpTemplate.parseMotionXmp(xmp)
        if (info.isMotion && info.hasOplus) return 95

        // 无 OpCamera 标签但文件尾有 cameralbum footer 也按 OPPO 处理
        if (info.isMotion) {
            try {
                val f = File(path)
                val size = f.length()
                FileInputStream(path).use { fs ->
                    val skipBytes = maxOf(0L, size - 4096)
                    fs.skip(skipBytes)
                    val tail = ByteArray(minOf(4096L, size).toInt())
                    var read = 0
                    while (read < tail.size) {
                        val n = fs.read(tail, read, tail.size - read)
                        if (n < 0) break
                        read += n
                    }
                    if (FooterUtil.parseFooter(tail.copyOfRange(0, read)) != null) return 85
                }
            } catch (e: Exception) {
                // 读取失败
            }
        }
        return 0
    }

    override fun read(path: String, log: (String, String, String) -> Unit): LivePhotoAsset {
        log("info", CoreText.of(R.string.fmt_log_parse_oppo), "OPPO")
        val asset = EmbeddedReader.readEmbedded(path, name, log)

        // OPPO 附加信息：footer JSON
        val data = readBytes(path)
        val footer = FooterUtil.parseFooter(data)
        if (footer != null) {
            asset.imageTime = footer.imageTime
            asset.extras["oppo_footer"] = footer
        }
        return asset
    }

    override fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?>
    ): MutableList<String> {
        var video = asset.videoMp4

        // 源视频无 lpex 时合成插入
        val searchEnd = minOf(65536, video.size)
        val lpexMarker = byteArrayOf(0x6C, 0x70, 0x65, 0x78) // "lpex"
        if (BinaryUtils.indexOf(video.copyOfRange(0, searchEnd), lpexMarker) < 0) {
            try {
                video = Mp4Util.insertBoxIntoMoov(video, "lpex", buildLpexPayload(asset))
                log("info", CoreText.of(R.string.fmt_log_lpex_ok), "OPPO")
            } catch (ex: Exception) {
                log("warning", CoreText.of(R.string.fmt_log_lpex_fail, ex.message), "OPPO")
            }
        }

        val imageTime = asset.effectiveImageTime()
        val footerJson = FooterUtil.buildFooterJson(linkedMapOf(
            "com.android.camera.imageTime" to imageTime,
            "com.vivo.gallery.file.convert" to 10004,
            "com.android.camera.livephoto" to FooterUtil.oppoFixedId,
            "version" to 2200
        ))
        val footer = FooterUtil.buildFooter(footerJson, FooterUtil.oppoFixedId, FooterUtil.extPrefix)

        // 真机尾部元数据不是裸 payload，而是完整的 uuid box：[size]["uuid"][payload]。
        // 加这 8 字节头后，MP4 区字节与真机同构、是良构的 box 链；
        // 设备端因果边界见 Mp4Util.wrapVivoUuidBox 的 KDoc（不主张唯一根因）。
        val trailer = Mp4Util.wrapVivoUuidBox(footer)

        val pts = asset.effectivePtsUs()
        val xmp = XmpTemplate.buildOppoXmp(
            pts, asset.gainmapLength,
            videoLen = video.size + trailer.size,
            mp4Len = video.size
        )
        val primary = JpegUtil.replaceOrInsertXmp(asset.primaryJpeg, xmp)

        val gainmapLen = asset.gainmapJpeg?.size ?: 0
        val output = ByteArray(primary.size + gainmapLen + video.size + trailer.size)
        var pos = 0
        System.arraycopy(primary, 0, output, pos, primary.size); pos += primary.size
        asset.gainmapJpeg?.let {
            System.arraycopy(it, 0, output, pos, it.size); pos += it.size
        }
        System.arraycopy(video, 0, output, pos, video.size); pos += video.size
        System.arraycopy(trailer, 0, output, pos, trailer.size)

        val outPath = File(outDir, "$stem.jpg").path
        writeBytes(outPath, output)
        log("info", CoreText.of(R.string.fmt_log_oppo_write, File(outPath).name,
            primary.size, video.size, trailer.size), "OPPO")
        return mutableListOf(outPath)
    }
}
