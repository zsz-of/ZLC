package com.zsz.zlivephoto.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume

/**
 * 视频「重新封装」工具：把非标准 MP4 容器（WebM/MKV/AVI/MOV/TS 等）转为标准 MP4。
 * 两级策略：
 *  1. [remuxContainer]：直接重封装（MediaExtractor→MediaMuxer，零重编码）。
 *     仅当源编码已可放入 MP4 容器（H.264/H.265/AAC 等）时成功（如 MOV→MP4）。
 *  2. [transcodeToMp4]：media3-Transformer 解码后重编码为 H.264/AAC MP4
 *     （等价于简单 ffmpeg：-c:v libx264 -c:a aac）。
 */
internal object VideoRemux {

    /**
     * 直接重封装（不重编码）。成功返回 true。
     * 复制音/视频轨的样本数据；任一轨 addTrack 成功即视为可封装。
     *
     * 注意：源里只要有视频轨，就必须成功装进 MP4 才返回 true。
     * 否则（如 AV1/VP9 在低版本 Android 无法 addTrack、只剩音频轨成功）会产生
     * 「只有音频、没有视频」的半成品 MP4，直接拿去合成会生成无法播放的动态照片，
     * 必须视为失败并回退到转码（[transcodeToMp4]）。
     */
    fun remuxContainer(srcPath: String, dstPath: String): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        return try {
            extractor.setDataSource(srcPath)
            val srcTracks = ArrayList<Int>()
            var srcHasVideo = false
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) srcHasVideo = true
                if (mime.startsWith("video/") || mime.startsWith("audio/")) srcTracks.add(i)
            }
            if (srcTracks.isEmpty()) return false

            File(dstPath).parentFile?.mkdirs()
            muxer = MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // 某轨道编码无法放入 MP4（如 VP9/Opus）时 addTrack 抛异常，跳过该轨道
            val dstTracks = IntArray(srcTracks.size)
            var valid = 0
            var videoAdded = false
            for ((idx, src) in srcTracks.withIndex()) {
                try {
                    val fmt = extractor.getTrackFormat(src)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    dstTracks[idx] = muxer.addTrack(fmt)
                    valid++
                    if (mime.startsWith("video/")) videoAdded = true
                } catch (_: Exception) {
                    dstTracks[idx] = -1
                }
            }
            // 源含视频轨却一个都没装进 MP4：拒绝「只剩音频」的半成品
            if (valid == 0 || (srcHasVideo && !videoAdded)) return false

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

            val header = ByteArray(16)
            FileInputStream(dstPath).use { it.read(header) }
            File(dstPath).length() > 0L && Mp4Util.hasFtyp(header)
        } catch (_: Exception) {
            false
        } finally {
            if (muxer != null) {
                try { if (started) muxer.stop() } catch (_: Exception) {}
                try { muxer.release() } catch (_: Exception) {}
            }
            try { extractor.release() } catch (_: Exception) {}
            try { if (!started) File(dstPath).delete() } catch (_: Exception) {}
        }
    }

    /**
     * 用 media3-Transformer 转码为标准 MP4（H.264 + AAC）。成功返回 true。
     * Transformer 异步导出，需在带 Looper 的主线程启动并等待回调。
     */
    suspend fun transcodeToMp4(context: Context, srcPath: String, dstPath: String): Boolean =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                var transformer: Transformer? = null
                try {
                    File(dstPath).parentFile?.mkdirs()
                    val mediaItem = MediaItem.fromUri(Uri.fromFile(File(srcPath)))
                    val edited = EditedMediaItem.Builder(mediaItem).build()
                    val sequence = EditedMediaItemSequence.Builder()
                        .addItem(edited)
                        .build()
                    val composition = Composition.Builder(sequence).build()
                    val builder = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    transformer = builder.build()
                    transformer!!.addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(
                                File(dstPath).exists() && File(dstPath).length() > 0L
                            )
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                    cont.invokeOnCancellation {
                        try { transformer?.cancel() } catch (_: Exception) {}
                    }
                    transformer!!.start(composition, dstPath)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(false)
                }
            }
        }
}
