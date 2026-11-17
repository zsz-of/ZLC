package com.zsz.zlivephoto.core

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * 无 ffmpeg 可用时（Go 版 / 追加包未就绪）的视频截取。
 *
 * 用 [MediaExtractor] 读取源视频前 [maxSec] 秒的编码样本，[MediaMuxer] **原样封装**
 * 成 MP4（不重编码：速度快、画质零损失）。样本按时间截断，落在截断点后的样本丢弃。
 */
internal object VideoTrimmer {

    /** 截取 [srcPath] 前 [maxSec] 秒写入 [dstPath]；成功返回 true */
    fun trimTo(srcPath: String, dstPath: String, maxSec: Double): Boolean {
        if (maxSec <= 0.0) return false
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        return try {
            extractor.setDataSource(srcPath)
            muxer = MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            // 轨道下标映射：extractor 轨 → muxer 轨
            val map = HashMap<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                extractor.selectTrack(i)
                map[i] = muxer.addTrack(fmt)
            }
            if (map.isEmpty()) return false
            muxer.start()
            started = true

            val buffer = ByteBuffer.allocateDirect(4 shl 20)
            val info = MediaCodec.BufferInfo()
            val endUs = (maxSec * 1_000_000.0).toLong()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val track = extractor.sampleTrackIndex
                val timeUs = extractor.sampleTime
                val dst = map[track]
                if (dst == null || timeUs < 0L || timeUs > endUs) {
                    if (timeUs > endUs) break
                    extractor.advance()
                    continue
                }
                info.offset = 0
                info.size = size
                info.presentationTimeUs = timeUs
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(dst, buffer, info)
                extractor.advance()
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { if (started) muxer?.stop() }
            runCatching { muxer?.release() }
            extractor.release()
        }.let { ok ->
            ok && File(dstPath).let { it.exists() && it.length() > 0L }
        }
    }
}
