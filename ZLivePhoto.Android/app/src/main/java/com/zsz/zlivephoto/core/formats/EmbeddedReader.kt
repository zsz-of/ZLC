package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.Mp4Util
import com.zsz.zlivephoto.core.XmpTemplate
import java.io.File
import java.io.IOException

/**
 * 单文件内嵌格式的共用读取逻辑（Google / OPPO / 小米）。
 */
internal object EmbeddedReader {
    fun readEmbedded(
        path: String, sourceFormat: String,
        log: (String, String, String) -> Unit
    ): LivePhotoAsset {
        val data = File(path).readBytes()
        val xmpFound = JpegUtil.findXmpSegment(data)
        val xmpInfo = XmpTemplate.parseMotionXmp(xmpFound?.xmpText ?: "")

        val (jpegs, consumed) = JpegUtil.splitJpegs(data)
        if (jpegs.isEmpty()) throw IOException("未找到主 JPEG 图像")
        val primary = jpegs[0]
        val gainmap = if (jpegs.size > 1) jpegs[1] else null

        // 视频定位
        val offset = xmpInfo.microVideoOffset
        val videoPayload: ByteArray = if (xmpInfo.isLegacyMicro && offset != null) {
            // 旧版 MicroVideo：视频起点 = 文件大小 - MicroVideoOffset
            data.copyOfRange(data.size - offset, data.size)
        } else {
            data.copyOfRange(consumed, data.size)
        }

        // 荣耀等格式在 JPEG EOI 和 ftyp 之间可能有非标准数据（EXIF preview 等），
        // 需在剩余数据中搜索 ftyp box 起始位置。
        var payload = videoPayload
        if (!Mp4Util.hasFtyp(payload)) {
            val ftypIdx = findFtyp(payload)
            if (ftypIdx < 0)
                throw IOException("JPEG 之后未找到有效的 MP4 视频（缺少 ftyp box）")
            payload = payload.copyOfRange(ftypIdx, payload.size)
        }

        val mp4Len = Mp4Util.streamLength(payload)
        if (mp4Len <= 0) throw IOException("MP4 视频流解析失败")
        val video = payload.copyOfRange(0, mp4Len)

        val asset = LivePhotoAsset(
            primaryJpeg = primary,
            gainmapJpeg = gainmap,
            videoMp4 = video,
            sourceFormat = sourceFormat,
        )
        asset.presentationTsUs = xmpInfo.ptsUs
        asset.videoInfo = Mp4Util.getTrackInfo(video) ?: mutableMapOf()
        asset.extras["payload_trailer"] = payload.copyOfRange(mp4Len, payload.size)
        return asset
    }

    /**
     * 在数据中搜索 MP4 ftyp box 起始位置。
     * ftyp box 格式：[size 4B][type='ftyp' 4B]，搜索 "ftyp" 字符串后回退 4 字节。
     */
    private fun findFtyp(data: ByteArray): Int {
        for (i in 4 until data.size - 4) {
            if (data[i] == 'f'.code.toByte() && data[i + 1] == 't'.code.toByte() &&
                data[i + 2] == 'y'.code.toByte() && data[i + 3] == 'p'.code.toByte()) {
                val boxStart = i - 4
                val size = ((data[boxStart].toInt() and 0xFF) shl 24) or
                    ((data[boxStart + 1].toInt() and 0xFF) shl 16) or
                    ((data[boxStart + 2].toInt() and 0xFF) shl 8) or
                    (data[boxStart + 3].toInt() and 0xFF)
                if (size >= 8 && boxStart + size <= data.size)
                    return boxStart
            }
        }
        return -1
    }
}
