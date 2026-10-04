package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.FooterUtil
import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.Mp4Util
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 回归测试：尾部 cameralbum footer 必须是**完整的 ISOBMFF uuid box**，
 * 而不是裸 payload。
 *
 * 真机（OPPO / vivo 单文件动态照片）尾部逐字节形如：
 *   [00 00 00 F0]["uuid"]["vivoMediaExtInfo"(16B)]["vivo" …json… "cameralbum!" … 43B tail]
 *
 * 本工具曾直接落盘裸 payload（缺 8 字节 box 头）。此时相册仍凭 XMP 把文件识别成
 * 动态照片（角标 / 封面正常），但尾部字节不再是良构 ISOBMFF：payload 前 4 字节是
 * `vivo`（实测 0x7669766F ≈ 1.85GiB），会被读成远超剩余字节数的 box size，
 * 其后的 type 字段读作 `Medi`。真机侧因此取不到视频
 * → 症状「相册能识别，但长按无法播放」。
 *
 * 注意：OPPO 播放器 `Mp4Extractor` 对超大未解析 atom 会走 payload 跳过逻辑，
 * 因此这里的断言只覆盖「布局必须良构」，不宣称裸 trailer 必然抛异常。
 * 真正被逆向证实的是接受契约，见 [OppoDeviceContractTest]。
 */
class OppoTrailerUuidTest {

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
        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var pos = 0
        for (p in parts) {
            System.arraycopy(p, 0, out, pos, p.size)
            pos += p.size
        }
        return out
    }

    private fun indexOf(data: ByteArray, token: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..data.size - token.size) {
            for (j in token.indices) if (data[i + j] != token[j]) continue@outer
            return i
        }
        return -1
    }

    private fun indexOfText(data: ByteArray, token: String): Int =
        indexOf(data, token.toByteArray(Charsets.US_ASCII))

    private fun ascii(data: ByteArray): String = String(data, Charsets.ISO_8859_1)

    /** 最小可解析 JPEG：SOI + XMP APP1 + SOF0(2×3) + SOS + EOI。 */
    private fun minimalJpeg(): ByteArray {
        val xmp = """<?xpacket begin=""?><x:xmpmeta xmlns:x="adobe:ns:meta/">
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description rdf:about="" xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/">
            <hdrgm:Version>1.0</hdrgm:Version></rdf:Description></rdf:RDF></x:xmpmeta>"""
        val sof = byteArrayOf(
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 0x08,
            0x00, 0x02, // height = 2
            0x00, 0x03, // width = 3
            0x03, // 3 components
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

    private fun sampleFooter(): ByteArray {
        val json = FooterUtil.buildFooterJson(
            linkedMapOf(
                "com.android.camera.imageTime" to 34,
                "com.vivo.gallery.file.convert" to 10004,
                "com.android.camera.livephoto" to FooterUtil.oppoFixedId,
                "version" to 2200
            )
        )
        return FooterUtil.buildFooter(json, FooterUtil.oppoFixedId, FooterUtil.extPrefix)
    }

    // ---------------------------------------------------------------- 单元断言

    /**
     * 真机布局：box 头 8 字节 + footer 载荷；载荷本身以 16 字节 user type 开头，
     * 所以 box 总长 = footer.size + 8（不是 +24）。
     */
    @Test
    fun wrapVivoUuidBox_matchesDeviceLayout() {
        val footer = sampleFooter()
        val trailer = Mp4Util.wrapVivoUuidBox(footer)

        assertEquals("box 总长应为 payload + 8", footer.size + 8, trailer.size)
        assertEquals("size 字段应等于 box 总长", trailer.size.toLong(), u32(trailer, 0))
        assertEquals("type 应为 uuid", "uuid", String(trailer, 4, 4, Charsets.US_ASCII))
        assertEquals(
            "user type 应为 vivoMediaExtInfo（footer 载荷自带，不重复写）",
            "vivoMediaExtInfo", String(trailer, 8, 16, Charsets.US_ASCII)
        )
        assertArrayEquals("载荷应逐字节保留", footer, trailer.copyOfRange(8, trailer.size))

        // 反证：裸 payload 的前 4 字节会被当成一个远超自身的 box size
        assertTrue(
            "裸 payload 首 4 字节不是合法 size（这正是遍历中断的原因）",
            u32(footer, 0) > footer.size.toLong()
        )
    }

    /** 加了 box 头之后，尾部 trailer 必须能被遍历器正确排除，不能再算进视频。 */
    @Test
    fun boxWalker_doesNotSwallowTrailer_afterWrap() {
        val video = concat(
            box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + ByteArray(12)),
            box("moov", ByteArray(8) { 0x11 })
        )
        val footer = sampleFooter()
        val trailer = Mp4Util.wrapVivoUuidBox(footer)

        val wrapped = concat(video, trailer)

        // 裸写：trailer 首 4 字节是「vivo」= 0x7669766F 这样的巨量假 size，
        // 遍历在 moov 之后立刻越界中断 → trailer 侥幸被排除在外。
        assertEquals(
            "裸写时遍历中断，trailer 恰好被排除（旧行为：侥幸正确）",
            video.size, Mp4Util.streamLength(concat(video, footer))
        )

        // 包成合法 box 后：遍历能一路走通，streamLength 会把 trailer 也算进「视频」。
        assertEquals(
            "包成合法 box 后 streamLength 会把 trailer 算进视频 —— 这正是必须先 strip 的原因",
            wrapped.size, Mp4Util.streamLength(wrapped)
        )

        // EmbeddedReader 修复的核心：先 strip 再 streamLength，
        // 否则（原生文件 / 已修产物再次输入）会把 240B trailer 算进视频并产生第二个 trailer。
        assertEquals(
            "先 strip 再算，视频长度必须停在 moov 末尾",
            video.size, Mp4Util.streamLength(Mp4Util.stripVivoUuid(wrapped))
        )
        assertArrayEquals(
            "stripVivoUuid 必须只剥掉尾部 uuid box",
            video, Mp4Util.stripVivoUuid(wrapped)
        )
        assertEquals("没有 trailer 时 strip 应为无操作", video.size, Mp4Util.stripVivoUuid(video).size)
    }

    // ---------------------------------------------------------------- 端到端

    /**
     * 走真实写路径：OppoPlugin.write → 落盘文件。
     * 断言输出尾部是完整 uuid box，且 XMP 的 Item:Length == 文件尾 − 视频起点。
     */
    @Test
    fun oppoWrite_emitsWrappedUuidTrailer() {
        val video = concat(
            box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + ByteArray(12)),
            box("moov", ByteArray(8) { 0x22 })
        )
        val asset = LivePhotoAsset(
            primaryJpeg = minimalJpeg(),
            gainmapJpeg = null,
            videoMp4 = video,
            sourceFormat = "google"
        )
        asset.presentationTsUs = 1_000_000L

        val outDir = Files.createTempDirectory("oppo_trailer_test").toFile()
        try {
            val outs = OppoPlugin().write(asset, outDir.path, "case", ::log, mutableMapOf())
            assertEquals(1, outs.size)
            val data = File(outs[0]).readBytes()

            // 定位内嵌 MP4 起点（stripVivoUuid / streamLength 只作用于 MP4 区段，
            // 整份文件以 JPEG 开头，MP4 box 遍历器到 JPEG 头部就会停下）
            val ftypIdx = indexOfText(data, "ftyp")
            assertTrue("输出中应能定位 ftyp", ftypIdx >= 4)
            val videoStart = ftypIdx - 4
            val mp4Region = data.copyOfRange(videoStart, data.size)

            // 1) 尾部必须是完整 uuid box
            val stripped = Mp4Util.stripVivoUuid(mp4Region)
            val trailerLen = mp4Region.size - stripped.size
            val trailerStart = videoStart + stripped.size
            assertTrue("输出尾部应存在 uuid box trailer，实际 trailerLen=$trailerLen", trailerLen > 8)
            assertEquals("trailer 的 size 字段应等于自身长度", trailerLen.toLong(), u32(data, trailerStart))
            assertEquals("uuid", String(data, trailerStart + 4, 4, Charsets.US_ASCII))
            assertEquals(
                "vivoMediaExtInfo",
                String(data, trailerStart + 8, 16, Charsets.US_ASCII)
            )
            assertTrue(
                "trailer 之前必须是完整视频（strip 后再遍历长度不变）",
                Mp4Util.streamLength(stripped) == stripped.size
            )

            // 2) 自家解析器仍能读出 footer（向后兼容，且前缀是 extPrefix）
            val info = FooterUtil.parseFooter(data)
            assertNotNull("输出尾部应能被 parseFooter 解析", info)
            assertEquals(
                "footer 起点应正好在 trailer 载荷处",
                trailerStart + 8, info!!.footerStart
            )
            assertArrayEquals(FooterUtil.extPrefix, info.prefix)
            assertEquals(FooterUtil.oppoFixedId, info.id)

            // 3) XMP 与真机共同约定：Item:Length == 文件尾 − 视频起点
            val text = ascii(data)
            val itemLen = Regex("""Item:Length="(\d+)"""").findAll(text)
                .map { it.groupValues[1].toLong() }
                .maxOrNull()
            assertNotNull("XMP 中应存在 Item:Length", itemLen)
            assertEquals(
                "Item:Length 必须等于 文件尾 − 视频起点（与真机 5/5 原生样本一致）",
                (data.size - videoStart).toLong(), itemLen!!.toLong()
            )

            // 4) OpCamera:VideoLength 语义 = 视频区长度（不含 trailer）
            val videoLenDecl = Regex("""OpCamera:VideoLength="(\d+)"""")
                .find(text)?.groupValues?.get(1)?.toLong()
            assertNotNull("XMP 中应存在 OpCamera:VideoLength", videoLenDecl)
            assertEquals(
                "VideoLength 应等于 mp4Len（不含 trailer，与修复前保持一致）",
                (data.size - videoStart - trailerLen).toLong(), videoLenDecl!!.toLong()
            )
        } finally {
            outDir.deleteRecursively()
        }
    }

    /**
     * 往返回归：把本工具写出的 OPPO 文件**再**读进来转成其它格式。
     *
     * 若 EmbeddedReader 没有先 strip 掉尾部 uuid box，`streamLength` 会把 trailer
     * 算进视频，转出的文件里就会夹带一段 vivo 元数据 —— 再写回 OPPO 时形成
     * 「双 trailer」的损坏文件。这里断言转出文件里不含任何 vivo 元数据字节。
     */
    @Test
    fun converterRoundTrip_stripsIncomingTrailer() {
        val video = concat(
            box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + ByteArray(12)),
            box("moov", ByteArray(8) { 0x33 })
        )
        val asset = LivePhotoAsset(
            primaryJpeg = minimalJpeg(),
            gainmapJpeg = null,
            videoMp4 = video,
            sourceFormat = "google"
        )
        asset.presentationTsUs = 1_000_000L

        val outDirA = Files.createTempDirectory("oppo_rt_a").toFile()
        val outDirB = Files.createTempDirectory("oppo_rt_b").toFile()
        try {
            val fileA = OppoPlugin().write(asset, outDirA.path, "a", ::log, mutableMapOf()).single()
            val dataA = File(fileA).readBytes()
            val videoStartA = indexOfText(dataA, "ftyp") - 4
            val mp4Region = dataA.copyOfRange(videoStartA, dataA.size)
            val stripped = Mp4Util.stripVivoUuid(mp4Region)
            assertTrue("前置条件：A 必须带 uuid box trailer", stripped.size < mp4Region.size)

            val outsB = com.zsz.zlivephoto.core.Converter.convertFile(
                fileA, "google", outDirB.path, ::log
            )
            assertEquals(1, outsB.size)
            val dataB = File(outsB[0]).readBytes()

            assertEquals(
                "转出文件不得夹带 vivo 元数据（说明读入时已 strip 掉 trailer）",
                -1, indexOfText(dataB, "vivoMediaExtInfo")
            )
            val videoStartB = indexOfText(dataB, "ftyp") - 4
            assertArrayEquals(
                "转出的视频区必须与读入时剥掉 trailer 的视频逐字节相同",
                stripped, dataB.copyOfRange(videoStartB, dataB.size)
            )
        } finally {
            outDirA.deleteRecursively()
            outDirB.deleteRecursively()
        }
    }
}
