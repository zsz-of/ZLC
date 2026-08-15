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

        if (!Mp4Util.hasFtyp(videoPayload))
            throw IOException("JPEG 之后未找到有效的 MP4 视频（缺少 ftyp box）")

        val mp4Len = Mp4Util.streamLength(videoPayload)
        if (mp4Len <= 0) throw IOException("MP4 视频流解析失败")
        val video = videoPayload.copyOfRange(0, mp4Len)

        val asset = LivePhotoAsset(
            primaryJpeg = primary,
            gainmapJpeg = gainmap,
            videoMp4 = video,
            sourceFormat = sourceFormat,
        )
        asset.presentationTsUs = xmpInfo.ptsUs
        asset.videoInfo = Mp4Util.getTrackInfo(video) ?: mutableMapOf()
        asset.extras["payload_trailer"] = videoPayload.copyOfRange(mp4Len, videoPayload.size)
        return asset
    }
}
