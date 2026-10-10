package com.zsz.zlivephoto.core

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.zsz.zlivephoto.R
import java.io.File
import java.nio.ByteBuffer

/**
 * 视频轨净化：把「夹带非音视频轨」的 MP4 重封装成只含音视频轨的 MP4。
 *
 * 为什么需要：本工具的写路径是**字节级搬运**输入视频（`Converter.convertFile` 读到的
 * `LivePhotoAsset.videoMp4` 直接拼进产物）。若输入源本身带 QuickTime 元数据轨（`mett`）、
 * 时间码轨（`tmcd`）之类的非音视频轨，这些轨会被原样搬进动态照片产物，在部分机型上表现为
 * 「相册能识别为动态照片，但长按无法播放 / 无法编辑」。真实语料里已实测到 15 份这样的产物
 * （`handler=meta` + `stsd=mett`，轨序 meta→soun→vide，裸 trailer）。
 *
 * 设计要点：
 * - **只在需要时才付代价**：先做纯字节检测（[Mp4Util.trackHandlers]），没有任何非音视频轨时
 *   不调用 MediaExtractor/MediaMuxer，原有「零拷贝直通」性能不受影响；
 * - **视频轨必须成功**：音频轨允许 `addTrack` 失败（个别编码 MediaMuxer 不支持），但视频轨
 *   失败就整体判失败并返回 null，由调用方保留原字节（绝不产出「只有音频」的产物）；
 * - **保留旋转**：重封装会丢掉字节级 `tkhd` 旋转矩阵，故用 `setOrientationHint` 补回，
 *   避免净化把竖屏视频变成横屏。
 */
internal object VideoTrackSanitizer {

    /** 动态照片只需要这两类轨；其余（meta/tmcd/text/hint…）都应剔除。 */
    private val AV_HANDLERS = setOf("vide", "soun")

    /** 返回需要剔除的轨的 handler_type 列表（空列表 = 无需净化）。纯字节解析，可单测。 */
    fun nonAvHandlers(mp4: ByteArray): List<String> =
        Mp4Util.trackHandlers(mp4).filter { it !in AV_HANDLERS }

    /**
     * 用 MediaExtractor + MediaMuxer 重封装为仅含音视频轨的 MP4。
     * @return 重封装后的字节；未重封装或失败返回 null（调用方应保留原字节）。
     */
    fun sanitize(
        mp4: ByteArray,
        log: (level: String, msg: String, tag: String) -> Unit = { _, _, _ -> }
    ): ByteArray? {
        if (!Mp4Util.hasFtyp(mp4)) return null

        // ① 零拷贝确定性路径：纯字节级剔除末尾 trak（mebx/mett/tmcd…）并归一化 ftyp 品牌。
        //    样本数据（mdat）逐字节不变，不依赖 MediaExtractor/MediaMuxer，因此行为确定、
        //    也不存在重封装引入的编码/时间戳差异。仅当 moov 中确有可安全删除的末尾 trak
        //    或品牌需要归一化时才走这条路。
        val normalized = Mp4Util.normalizeMovToMp4(mp4)
        if (!normalized.contentEquals(mp4)) {
            val tag = CoreText.of(R.string.conv_tag_sanitize)
            // 两条文案各自是完整独立的一句话，分两行输出：早期版本把「剔除附加轨（…）、」
            // 塞进「已按字节级%1$s归一化…」的 %1$s，但各语言把 %1$s 放进了括号/介词后，
            // 拼接结果在非中文下会读不通（「… by Dropped extra tracks (meta), (zero-copy …)」）。
            val dropped = nonAvHandlers(mp4)
            if (dropped.isNotEmpty()) {
                log(
                    "info",
                    CoreText.of(R.string.conv_log_sanitize_dropped_prefix, dropped.joinToString("/")),
                    tag
                )
            }
            log("info", CoreText.of(R.string.conv_log_sanitize_normalized), tag)
            return normalized
        }

        // ② 回退：结构不在可安全字节级处理的范围（例如非末尾 trak）时，用
        //    MediaExtractor/MediaMuxer 重封装。
        val rotation = (Mp4Util.getTrackInfo(mp4)?.get("rotation") as? Int) ?: 0

        var tmpIn: File? = null
        var tmpOut: File? = null
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        return try {
            tmpIn = File.createTempFile("zlive_sanitize_in", ".mp4")
            tmpIn.writeBytes(mp4)
            extractor.setDataSource(tmpIn.absolutePath)

            // 收集音视频轨，并标出哪一条是视频轨
            val srcTracks = ArrayList<Int>()
            val isVideo = HashMap<Int, Boolean>()
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                val video = mime.startsWith("video/")
                if (video || mime.startsWith("audio/")) {
                    srcTracks.add(i)
                    isVideo[i] = video
                }
            }
            if (srcTracks.none { isVideo[it] == true }) {
                log("warning", CoreText.of(R.string.conv_log_sanitize_no_video_track), CoreText.of(R.string.conv_tag_sanitize))
                return null
            }

            tmpOut = File.createTempFile("zlive_sanitize_out", ".mp4")
            muxer = MediaMuxer(tmpOut.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (rotation != 0) {
                // 补回重封装丢掉的旋转信息（0/90/180/270 都接受）
                runCatching { muxer.setOrientationHint(rotation) }
            }

            // addTrack 可能因 MediaMuxer 不支持某轨（如 PCM 音频）而抛异常，逐轨跳过
            val dstTracks = HashMap<Int, Int>()
            for (src in srcTracks) {
                try {
                    dstTracks[src] = muxer.addTrack(extractor.getTrackFormat(src))
                } catch (e: Exception) {
                    if (isVideo[src] == true) {
                        // 视频轨加不进去就不能产出这样的产物：宁可放弃净化
                        log("warning", CoreText.of(R.string.conv_log_sanitize_track_add_failed, e.message), CoreText.of(R.string.conv_tag_sanitize))
                        return null
                    }
                }
            }

            muxer.start()
            started = true

            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val dst = dstTracks[extractor.sampleTrackIndex]
                if (dst != null) {
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                        MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    muxer.writeSampleData(dst, buffer, info)
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null

            val out = tmpOut.readBytes()
            if (out.isNotEmpty() && Mp4Util.hasFtyp(out)) out else null
        } catch (e: Exception) {
            log("warning", CoreText.of(R.string.conv_log_sanitize_exception, e.message), CoreText.of(R.string.conv_tag_sanitize))
            null
        } finally {
            if (muxer != null) {
                try { if (started) muxer.stop() } catch (_: Exception) {}
                try { muxer.release() } catch (_: Exception) {}
            }
            try { extractor.release() } catch (_: Exception) {}
            try { tmpIn?.delete() } catch (_: Exception) {}
            try { tmpOut?.delete() } catch (_: Exception) {}
        }
    }
}
