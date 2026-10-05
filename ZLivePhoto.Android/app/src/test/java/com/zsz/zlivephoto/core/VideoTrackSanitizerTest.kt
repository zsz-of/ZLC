package com.zsz.zlivephoto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归测试：视频轨清单解析与非音视频轨识别。
 *
 * 背景：本工具的写路径是**字节级搬运**输入视频。输入源若夹带 QuickTime 元数据轨（`mett`）、
 * 时间码轨（`tmcd`）之类的非音视频轨，就会被原样带进动态照片产物，在部分机型上表现为
 * 「相册能识别为动态照片，但长按无法播放 / 无法编辑」。真实语料中已实测到 15 份这样的产物
 * （`handler=meta` + `stsd=mett`，轨序 meta→soun→vide）。
 *
 * 这里只覆盖**纯字节**部分（[Mp4Util.trackHandlers] 与
 * [VideoTrackSanitizer.nonAvHandlers]）——真正调用 MediaExtractor/MediaMuxer 的重封装
 * 无法在 JVM 单测里跑，由 `:app` 之外的冒烟矩阵（`.agents/tools/smoke_run.py` 的 A14 断言
 * + 真实 mett 素材）覆盖。
 */
class VideoTrackSanitizerTest {

    // ---------------------------------------------------------------- 字节工具

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArray(8 + payload.size)
        val size = (8 + payload.size).toLong()
        out[0] = (size shr 24).toByte()
        out[1] = (size shr 16).toByte()
        out[2] = (size shr 8).toByte()
        out[3] = size.toByte()
        System.arraycopy(type.toByteArray(Charsets.US_ASCII), 0, out, 4, 4)
        System.arraycopy(payload, 0, out, 8, payload.size)
        return out
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArray(parts.sumOf { it.size })
        var off = 0
        for (p in parts) {
            System.arraycopy(p, 0, out, off, p.size)
            off += p.size
        }
        return out
    }

    /** hdlr（FullBox）：version/flags(4) + pre_defined(4) + handler_type(4) + reserved(12) + name */
    private fun hdlr(handlerType: String, name: String): ByteArray {
        val head = ByteArray(24)
        System.arraycopy(handlerType.toByteArray(Charsets.US_ASCII), 0, head, 8, 4)
        val nameBytes = name.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        return box("hdlr", head + nameBytes)
    }

    private fun trak(handlerType: String, name: String): ByteArray =
        box("trak", box("mdia", hdlr(handlerType, name)))

    private fun mp4With(vararg traks: ByteArray): ByteArray =
        concat(box("ftyp", "mp42".toByteArray(Charsets.US_ASCII)), box("moov", concat(*traks)))

    // ---------------------------------------------------------------- 用例

    @Test
    fun trackHandlers_readsEveryTrackInOrder() {
        val mp4 = mp4With(
            trak("vide", "VideoHandle"),
            trak("soun", "SoundHandle"),
            trak("meta", "MetadHandle"),
        )
        assertEquals(listOf("vide", "soun", "meta"), Mp4Util.trackHandlers(mp4))
    }

    @Test
    fun trackHandlers_ordersAsModeledAfterRealMettProduct() {
        // 真实语料里的形态：meta 在前，soun/vide 在后（与相机原生 vide→soun→meta 相反）
        val mp4 = mp4With(
            trak("meta", "MetadHandle"),
            trak("soun", "SoundHandle"),
            trak("vide", "VideoHandle"),
        )
        assertEquals(listOf("meta", "soun", "vide"), Mp4Util.trackHandlers(mp4))
        assertEquals(listOf("meta"), VideoTrackSanitizer.nonAvHandlers(mp4))
    }

    @Test
    fun nonAvHandlers_emptyForCleanAvMp4() {
        // 干净的动态照片视频：必须判为「无需净化」，否则会白白走一次重封装（丢旋转/耗时）
        val mp4 = mp4With(
            trak("vide", "VideoHandle"),
            trak("soun", "SoundHandle"),
        )
        assertTrue(VideoTrackSanitizer.nonAvHandlers(mp4).isEmpty())
    }

    @Test
    fun nonAvHandlers_flagsTimecodeAndTextTracks() {
        val mp4 = mp4With(
            trak("vide", "VideoHandle"),
            trak("tmcd", "TimeCodeHandler"),
            trak("text", "TextHandler"),
        )
        assertEquals(listOf("tmcd", "text"), VideoTrackSanitizer.nonAvHandlers(mp4))
    }

    @Test
    fun trackHandlers_emptyWhenNoMoov() {
        val mp4 = box("ftyp", "mp42".toByteArray(Charsets.US_ASCII))
        assertTrue(Mp4Util.trackHandlers(mp4).isEmpty())
        assertTrue(VideoTrackSanitizer.nonAvHandlers(mp4).isEmpty())
    }

    @Test
    fun trackHandlers_skipsTraksWithoutHdlr() {
        // 缺 hdlr 的 trak 不能抛异常，也不能被算成一类轨
        val mp4 = mp4With(
            box("trak", box("mdia", box("mdhd", ByteArray(24)))),
            trak("vide", "VideoHandle"),
        )
        assertEquals(listOf("vide"), Mp4Util.trackHandlers(mp4))
    }
}
