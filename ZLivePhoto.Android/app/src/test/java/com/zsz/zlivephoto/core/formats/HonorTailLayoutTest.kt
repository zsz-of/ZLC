package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 回归测试：荣耀动态照片的 60 字节尾部必须能让荣耀相册**精确**定位到内嵌 MP4。
 *
 * 荣耀 `com.hihonor.photos` 的 `Lcom/hihonor/gallery/livephoto/LiveUtils;` 逆向结论：
 * ```
 * [len-60, len-40) = "vX_fYY"    // VERSION_TAG="v2_"，PATTERN ^[vV](\d+)_[fF](\d+)
 * [len-40, len-20) = 播放信息串   // 按 ':' split，默认 ["0","500"]
 * [len-20, len  ) = "LIVE_<N>"   // getVideoOffset 读最后 20B，Long.parseLong(split("_")[1])
 * ```
 * 且 `SpecialMediaUtils.extractLivePhoto` / `getLiveExtractInfo` 用
 * `videoOffset = (len - 40) - N` 去 `QueryVideoInfoUtils.queryFrameRate/queryWidthAndHeight`
 * 真实解码；偏移不对就取不到帧率/宽高 → `hn_livephoto_decode_info` 为空 → 相册不认。
 *
 * 本工具曾把第三段写成 `LIVE_` + **9 位随机 ID**（`generateLiveId`），导致荣耀算出的
 * 偏移是垃圾值 —— 这正是「荣耀不识别 / 识别了也播不了」的容器级原因。
 */
class HonorTailLayoutTest {

    private fun log(level: String, msg: String, tag: String) {
        println("[$level][$tag] $msg")
    }

    // ---------------------------------------------------------------- 字节工具

    private fun u32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or
            ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or
            (b[off + 3].toLong() and 0xFF)

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
        var pos = 0
        for (p in parts) {
            System.arraycopy(p, 0, out, pos, p.size)
            pos += p.size
        }
        return out
    }

    private fun indexOfText(data: ByteArray, token: String): Int {
        val t = token.toByteArray(Charsets.US_ASCII)
        outer@ for (i in 0..data.size - t.size) {
            for (j in t.indices) if (data[i + j] != t[j]) continue@outer
            return i
        }
        return -1
    }

    /** 最小可解析 JPEG：SOI + XMP APP1 + SOF0(2×3) + SOS + EOI。 */
    private fun minimalJpeg(): ByteArray {
        val xmp = """<?xpacket begin=""?><x:xmpmeta xmlns:x="adobe:ns:meta/">
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description rdf:about="" xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/">
            <hdrgm:Version>1.0</hdrgm:Version></rdf:Description></rdf:RDF></x:xmpmeta>"""
        val sof = byteArrayOf(
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 0x08,
            0x00, 0x02,
            0x00, 0x03,
            0x03,
            0x01, 0x11, 0x00,
            0x02, 0x11, 0x00,
            0x03, 0x11, 0x00
        )
        val sos = byteArrayOf(
            0xFF.toByte(), 0xDA.toByte(), 0x00, 0x0C, 0x03,
            0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x00, 0x3F, 0x00
        )
        return concat(
            byteArrayOf(0xFF.toByte(), 0xD8.toByte()),
            JpegUtil.buildXmpApp1(xmp),
            sof, sos,
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        )
    }

    private fun video(): ByteArray = concat(
        box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + ByteArray(12)),
        box("moov", ByteArray(16) { 0x33 }),
        box("mdat", ByteArray(64) { 0x44 })
    )

    // ---------------------------------------------------------------- 断言

    /**
     * 镜像荣耀读取路径：`(len-40) - Long.parseLong(LIVE_ 后的数字)` 必须落在 MP4 起点，
     * 且 uuid box 的 usertype 必须是 `" honor.org.video"`（16B，含前导空格）。
     */
    private fun assertHonorReadable(data: ByteArray, label: String) {
        val len = data.size
        assertTrue("$label: 文件过短，荣耀要求 ≥ 60B 尾部", len > 120)

        // 1) 版本段
        val version = String(data, len - 60, 20, Charsets.US_ASCII).trim()
        assertTrue("$label: 版本段应为 vX_fYY，实际 '$version'", Regex("""^v\d+_f\d+$""").matches(version))

        // 2) 播放信息段（荣耀按 ':' split，默认 ["0","500"]）
        val playInfo = String(data, len - 40, 20, Charsets.US_ASCII).trim()
        assertEquals("$label: 播放信息段应为 起点:时长", 2, playInfo.split(':').size)

        // 3) LIVE_<N>：荣耀 getVideoOffset 就是取这个数当视频长度
        val liveTag = String(data, len - 20, 20, Charsets.US_ASCII).trim()
        assertTrue("$label: 尾部最后 20B 必须以 LIVE_ 开头，实际 '$liveTag'", liveTag.startsWith("LIVE_"))
        val n = liveTag.removePrefix("LIVE_").trim().toLong()

        // 4) 关键不变式：荣耀侧 videoOffset = (len-40) - N 必须精确落在 MP4 起点
        val ftypIdx = indexOfText(data, "ftyp")
        assertTrue("$label: 输出中应能定位 ftyp", ftypIdx >= 4)
        val videoStart = (ftypIdx - 4).toLong()
        assertEquals(
            "$label: (len-40)-N 必须等于视频起始偏移（N=$n, len=$len, videoStart=$videoStart）",
            videoStart, (len.toLong() - 40L) - n
        )

        // 5) 该偏移处必须真的是 MP4 起始
        assertEquals("$label: 偏移处应是 ftyp box", "ftyp", String(data, ftypIdx, 4, Charsets.US_ASCII))

        // 6) uuid box：usertype 必须是荣耀 VIDEO_USERTYPE
        val uuidIdx = indexOfText(data, "uuid")
        assertTrue("$label: 应存在 uuid box", uuidIdx >= 4)
        assertEquals(
            "$label: uuid usertype 必须是荣耀的 \" honor.org.video\"（16B，含前导空格）",
            " honor.org.video", String(data, uuidIdx + 4, 16, Charsets.US_ASCII)
        )
        // size 字段应恰好覆盖 usertype + payload（即 box 结束于尾段之前）
        val boxStart = uuidIdx - 4
        assertEquals(
            "$label: uuid box 的 size 应等于 usertype+payload 长度",
            (data.size - boxStart - 60).toLong(), u32(data, boxStart)
        )
        // 反证：N 不是随机的 9 位 ID
        assertNotEquals("$label: N 不应再是随机 9 位 ID", 9, liveTag.removePrefix("LIVE_").trim().length)
    }

    @Test
    fun honorWrite_liveTagLocatesVideoStart_withoutGainmap() {
        val asset = LivePhotoAsset(
            primaryJpeg = minimalJpeg(),
            gainmapJpeg = null,
            videoMp4 = video(),
            sourceFormat = "google"
        )
        asset.presentationTsUs = 1_000_000L

        val outDir = Files.createTempDirectory("honor_tail_test").toFile()
        try {
            val outs = HonorPlugin().write(asset, outDir.path, "case", ::log, mutableMapOf())
            assertEquals(1, outs.size)
            assertHonorReadable(File(outs[0]).readBytes(), "无 GainMap")
        } finally {
            outDir.deleteRecursively()
        }
    }

    @Test
    fun honorWrite_liveTagLocatesVideoStart_withGainmap() {
        val gainmap = ByteArray(37) { 0x55 }
        val asset = LivePhotoAsset(
            primaryJpeg = minimalJpeg(),
            gainmapJpeg = gainmap,
            videoMp4 = video(),
            sourceFormat = "google"
        )
        asset.presentationTsUs = 2_000_000L

        val outDir = Files.createTempDirectory("honor_tail_gm_test").toFile()
        try {
            val outs = HonorPlugin().write(asset, outDir.path, "case", ::log, mutableMapOf())
            val data = File(outs.single()).readBytes()
            assertHonorReadable(data, "含 GainMap")

            // GainMap 必须位于主图与 MP4 之间，且 N 要把它算进去
            val gmIdx = indexOfText(data, String(gainmap, Charsets.ISO_8859_1))
            val ftypIdx = indexOfText(data, "ftyp")
            assertTrue("GainMap 应在 MP4 之前", gmIdx in 1 until ftypIdx)
        } finally {
            outDir.deleteRecursively()
        }
    }
}
