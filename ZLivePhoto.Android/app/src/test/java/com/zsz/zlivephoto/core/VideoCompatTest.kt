package com.zsz.zlivephoto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Mp4Util.videoCompat` 回归：判定「仅重封装容器」是否足以让产物在安卓相册正常播放。
 *
 * 这些条件（10bit H.265、HDR PQ/HLG、杜比视界、`hev1`、PCM 音轨、镜像矩阵、非 H.264/H.265）
 * 是 `-c copy` 改不动的，必须靠「重新编码」；判定误报会让用户白白等一次重编码，
 * 漏报则会让产物继续在真机上「相册能识别、长按不播放」。
 */
class VideoCompatTest {

    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        var payload = ByteArray(0)
        for (p in parts) payload += p
        val out = ByteArray(8 + payload.size)
        BinaryUtils.writeU32BE(out, 0, (payload.size + 8).toLong())
        System.arraycopy(type.toByteArray(Charsets.ISO_8859_1), 0, out, 4, 4)
        System.arraycopy(payload, 0, out, 8, payload.size)
        return out
    }

    private fun u32(v: Long): ByteArray {
        val b = ByteArray(4)
        BinaryUtils.writeU32BE(b, 0, v)
        return b
    }

    private fun u16(v: Int): ByteArray =
        byteArrayOf(((v ushr 8) and 0xFF).toByte(), (v and 0xFF).toByte())

    /** VisualSampleEntry：8 字节头 + reserved[6]+data_reference_index[2] + 70 字节字段（= 子 box 起点 86）。 */
    private fun videEntry(fourcc: String, vararg children: ByteArray): ByteArray =
        box(fourcc, ByteArray(8), ByteArray(70), *children)

    /** AudioSampleEntry（v0）：8 字节头 + reserved[6]+data_reference_index[2] + 20 字节字段。 */
    private fun sounEntry(fourcc: String, vararg children: ByteArray): ByteArray =
        box(fourcc, ByteArray(8), ByteArray(20), *children)

    /** stsd = FullBox 头（version/flags）+ entry_count + sample entry。 */
    private fun stsd(entry: ByteArray): ByteArray = box("stsd", u32(0), u32(1), entry)

    private fun hdlr(handler: String): ByteArray =
        box("hdlr", ByteArray(8), handler.toByteArray(Charsets.ISO_8859_1))

    private fun tkhd(mirror: Boolean): ByteArray {
        // 顺序：version/flags(4) + … + matrix(36) + width(4) + height(4)
        val body = ByteArray(4 + 36 + 8)
        val m = 4
        val diag0 = if (mirror) -65536L and 0xFFFFFFFFL else 65536L
        BinaryUtils.writeU32BE(body, m + 0, diag0)
        BinaryUtils.writeU32BE(body, m + 16, 65536L)
        BinaryUtils.writeU32BE(body, m + 32, 65536L)
        return box("tkhd", body)
    }

    private fun videTrak(fourcc: String, children: Array<out ByteArray>, mirror: Boolean = false): ByteArray =
        box(
            "trak", tkhd(mirror),
            box("mdia", hdlr("vide"), box("minf", box("stbl", stsd(videEntry(fourcc, *children)))))
        )

    private fun sounTrak(fourcc: String): ByteArray =
        box("trak", tkhd(false), box("mdia", hdlr("soun"), box("minf", box("stbl", stsd(sounEntry(fourcc))))))

    private fun ftyp() = box(
        "ftyp", "isom".toByteArray(Charsets.ISO_8859_1) + u32(512) +
            "isom".toByteArray(Charsets.ISO_8859_1) + "mp41".toByteArray(Charsets.ISO_8859_1)
    )

    private fun mp4(vararg traks: ByteArray) = ftyp() + box("moov", *traks) + box("mdat", ByteArray(16))

    /** hvcC：configurationVersion(1) … +17 是 reserved(5)+bitDepthLumaMinus8(3)。 */
    private fun hvcC(bitDepthMinus8: Int): ByteArray {
        val payload = ByteArray(23)
        payload[0] = 1
        payload[17] = bitDepthMinus8.toByte()
        return box("hvcC", payload)
    }

    /** colr/nclx：colour_type(4) + primaries(2) + transfer(2) + matrix(2) + full_range(1)。 */
    private fun colr(transfer: Int): ByteArray =
        box(
            "colr",
            "nclx".toByteArray(Charsets.ISO_8859_1) + u16(9) + u16(transfer) + u16(9) + byteArrayOf(1)
        )

    @Test
    fun compat_acceptsEightBitAvcWithAac() {
        val compat = Mp4Util.videoCompat(mp4(videTrak("avc1", arrayOf(box("avcC", ByteArray(4)))), sounTrak("mp4a")))
        assertEquals("avc1", compat.videoCodec)
        assertEquals("mp4a", compat.audioCodec)
        assertFalse("8bit H.264 + AAC 必须判定为可直接重封装：${compat.reasons}", compat.needsReencode)
    }

    @Test
    fun compat_flagsTenBitHevc() {
        val compat = Mp4Util.videoCompat(mp4(videTrak("hvc1", arrayOf(hvcC(2)))))
        assertTrue("10bit H.265 必须判定为需要重新编码：${compat.reasons}", compat.needsReencode)
        assertTrue(
            "原因里要写明 10bit：${compat.reasons}",
            compat.reasons.any { it.contains("10bit") }
        )
    }

    @Test
    fun compat_flagsHdrTransferCharacteristics() {
        val hlq = Mp4Util.videoCompat(mp4(videTrak("hvc1", arrayOf(hvcC(0), colr(18)))))
        assertTrue("HLG 必须判定为需要重新编码：${hlq.reasons}", hlq.reasons.any { it.contains("HLG") })
        val pq = Mp4Util.videoCompat(mp4(videTrak("hvc1", arrayOf(hvcC(0), colr(16)))))
        assertTrue("PQ 必须判定为需要重新编码：${pq.reasons}", pq.reasons.any { it.contains("PQ") })
        // 普通 bt709 (transfer=1) 不应误报
        val sdr = Mp4Util.videoCompat(mp4(videTrak("hvc1", arrayOf(hvcC(0), colr(1)))))
        assertFalse("bt709 8bit 不应误报：${sdr.reasons}", sdr.needsReencode)
    }

    @Test
    fun compat_flagsDolbyVision() {
        val compat = Mp4Util.videoCompat(mp4(videTrak("hvc1", arrayOf(hvcC(0), box("dvcC", ByteArray(24))))))
        assertTrue("杜比视界必须判定为需要重新编码：${compat.reasons}", compat.reasons.any { it.contains("杜比视界") })
    }

    @Test
    fun compat_flagsHev1Tag() {
        val compat = Mp4Util.videoCompat(mp4(videTrak("hev1", arrayOf(hvcC(0)))))
        assertTrue("hev1 标记必须提示：${compat.reasons}", compat.reasons.any { it.contains("hev1") })
    }

    @Test
    fun compat_flagsNonAacAudio() {
        for (f in listOf("lpcm", "sowt", "twos", "alac", "ac-3")) {
            val compat = Mp4Util.videoCompat(mp4(videTrak("avc1", arrayOf(box("avcC", ByteArray(4)))), sounTrak(f)))
            assertTrue("$f 音轨必须判定为需要转 AAC：${compat.reasons}", compat.needsReencode)
        }
    }

    @Test
    fun compat_flagsMirrorMatrix() {
        val mirror = Mp4Util.videoCompat(mp4(videTrak("avc1", arrayOf(box("avcC", ByteArray(4))), mirror = true)))
        assertTrue("镜像矩阵必须判定为需要重新编码：${mirror.reasons}", mirror.reasons.any { it.contains("镜像") })
        // 镜像 + 正常旋转矩阵（无镜像）不能误报
        val plain = Mp4Util.videoCompat(mp4(videTrak("avc1", arrayOf(box("avcC", ByteArray(4))), mirror = false)))
        assertFalse("非镜像矩阵不应误报：${plain.reasons}", plain.reasons.any { it.contains("镜像") })
    }

    @Test
    fun compat_flagsNonStandardVideoCodec() {
        val compat = Mp4Util.videoCompat(mp4(videTrak("apcn", arrayOf(box("avcC", ByteArray(4))))))
        assertEquals("apcn", compat.videoCodec)
        assertTrue("ProRes 必须判定为需要重新编码：${compat.reasons}", compat.needsReencode)
    }

    @Test
    fun compat_acceptsRealAppleMovShape() {
        // 真机 Apple MOV 形态：qt 品牌（未归一） + vide(hvc1 8bit) + meta(mebx)
        val mov = box(
            "ftyp", "qt  ".toByteArray(Charsets.ISO_8859_1) + u32(0) + "qt  ".toByteArray(Charsets.ISO_8859_1)
        ) + box(
            "moov",
            videTrak("hvc1", arrayOf(hvcC(0))),
            box("trak", tkhd(false), box("mdia", hdlr("meta"), box("minf", box("stbl", stsd(box("mebx", ByteArray(4)))))))
        ) + box("mdat", ByteArray(32))
        val compat = Mp4Util.videoCompat(mov)
        assertFalse("8bit H.265 + 无音轨不应误报（附加轨由 normalizeMovToMp4 负责）：${compat.reasons}", compat.needsReencode)
    }
}
