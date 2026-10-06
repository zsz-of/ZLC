package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.ExifUtil
import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.Mp4Util
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 回归测试：Apple Live Photo 的两个 P0 缺口（见 `.agents/research/apple-livephoto-gaps.md`）。
 *
 * ① 图片侧配对标识此前写成 XMP 的 `apple-fi:ContentIdentifier`（Final Cut 命名空间），
 *    Apple 真正读的是静态图 **EXIF MakerNote（0x927C）内部 IFD 的 0x0011**（ASCII 字符串）。
 * ② 静帧时刻此前被硬编码为 0：真实 Apple MOV 用 `mebx` timed metadata 轨 + `elst` 空 edit
 *    记录（实测 iPhone 15 Pro：movie timescale 600、空 edit 740 → 1.2333 s）。
 */
class AppleLivePhotoTest {

    private fun log(level: String, msg: String, tag: String) {
        println("[$level][$tag] $msg")
    }

    // ---------------------------------------------------------------- 字节工具

    /** 大端 16 位（MP4 与 MOV 的盒结构）。 */
    private fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    /** 大端 32 位。 */
    private fun u32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)

    // EXIF 内部要按 TIFF 字节序读（我们的新 EXIF 是 II = 小端）
    private fun r16(b: ByteArray, off: Int, le: Boolean): Int =
        if (le) (((b[off + 1].toInt() and 0xFF) shl 8) or (b[off].toInt() and 0xFF)) else u16(b, off)

    private fun r32(b: ByteArray, off: Int, le: Boolean): Long =
        if (le) (((b[off + 3].toLong() and 0xFF) shl 24) or ((b[off + 2].toLong() and 0xFF) shl 16) or
            ((b[off + 1].toLong() and 0xFF) shl 8) or (b[off].toLong() and 0xFF)) else u32(b, off)

    private fun be32(v: Long): ByteArray {
        val b = ByteArray(4)
        b[0] = (v shr 24).toByte(); b[1] = (v shr 16).toByte(); b[2] = (v shr 8).toByte(); b[3] = v.toByte()
        return b
    }

    private fun be32(v: Int): ByteArray = be32(v.toLong())

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArray(8 + payload.size)
        System.arraycopy(be32((8 + payload.size).toLong()), 0, out, 0, 4)
        System.arraycopy(type.toByteArray(Charsets.ISO_8859_1), 0, out, 4, 4)
        System.arraycopy(payload, 0, out, 8, payload.size)
        return out
    }

    private fun fullBox(type: String, payload: ByteArray): ByteArray = box(type, ByteArray(4) + payload)

    private fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArray(parts.sumOf { it.size })
        var p = 0
        for (x in parts) { System.arraycopy(x, 0, out, p, x.size); p += x.size }
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

    /** 最小可解析 JPEG：SOI + XMP APP1 + SOF0 + SOS + EOI。 */
    private fun minimalJpeg(): ByteArray {
        val xmp = """<?xpacket begin=""?><x:xmpmeta xmlns:x="adobe:ns:meta/">
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description rdf:about="" xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/">
            <hdrgm:Version>1.0</hdrgm:Version></rdf:Description></rdf:RDF></x:xmpmeta>"""
        val sof = byteArrayOf(
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 0x08, 0x00, 0x02, 0x00, 0x03, 0x03,
            0x01, 0x11, 0x00, 0x02, 0x11, 0x00, 0x03, 0x11, 0x00
        )
        val sos = byteArrayOf(
            0xFF.toByte(), 0xDA.toByte(), 0x00, 0x0C, 0x03, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x00, 0x3F, 0x00
        )
        return concat(
            byteArrayOf(0xFF.toByte(), 0xD8.toByte()), JpegUtil.buildXmpApp1(xmp), sof, sos,
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        )
    }

    /** 定位 EXIF APP1 的 TIFF 起点 + 字节序；无则 null。 */
    private fun findTiff(jpeg: ByteArray): Pair<Int, Boolean>? {
        var p = 2
        while (p + 4 <= jpeg.size) {
            if (jpeg[p].toInt() and 0xFF != 0xFF) break
            val marker = jpeg[p + 1].toInt() and 0xFF
            val segLen = u16(jpeg, p + 2)
            if (marker == 0xE1 && String(jpeg, p + 4, 6, Charsets.ISO_8859_1) == "Exif\u0000\u0000") {
                val tiff = p + 10
                val le = jpeg[tiff] == 'I'.code.toByte()
                return tiff to le
            }
            p += 2 + segLen
        }
        return null
    }

    /** 从 JPEG 的 Apple MakerNote（0x927C）里按 tagId 读 ASCII 字符串（镜像 EXIF 规范）。 */
    private fun readMakerNoteAscii(jpeg: ByteArray, tagId: Int): String? {
        val (tiff, le) = findTiff(jpeg) ?: return null
        val ifd0 = tiff + r32(jpeg, tiff + 4, le).toInt()
        val ifd0Count = r16(jpeg, ifd0, le)
        var exifIfd = -1
        for (i in 0 until ifd0Count) {
            val e = ifd0 + 2 + i * 12
            if (r16(jpeg, e, le) == 0x8769) exifIfd = tiff + r32(jpeg, e + 8, le).toInt()
        }
        if (exifIfd < 0) return null
        val exifCount = r16(jpeg, exifIfd, le)
        for (i in 0 until exifCount) {
            val e = exifIfd + 2 + i * 12
            if (r16(jpeg, e, le) != 0x927C) continue
            val mn = tiff + r32(jpeg, e + 8, le).toInt()
            val mnCount = r16(jpeg, mn, le)
            for (k in 0 until mnCount) {
                val me = mn + 2 + k * 12
                if (r16(jpeg, me, le) != tagId) continue
                val type = r16(jpeg, me + 2, le)
                val count = r32(jpeg, me + 4, le).toInt()
                assertEquals("MakerNote ASCII 标签类型应为 2", 2, type)
                val valueAbs = tiff + r32(jpeg, me + 8, le).toInt()
                return String(jpeg, valueAbs, count, Charsets.US_ASCII).trimEnd('\u0000')
            }
        }
        return null
    }

    /** 读 MakerNote IFD 的条目总数（校验不产生重复标签）。 */
    private fun makerNoteEntryCount(jpeg: ByteArray): Int {
        val (tiff, le) = findTiff(jpeg) ?: return -1
        val ifd0 = tiff + r32(jpeg, tiff + 4, le).toInt()
        val n = r16(jpeg, ifd0, le)
        for (i in 0 until n) {
            val e = ifd0 + 2 + i * 12
            if (r16(jpeg, e, le) == 0x8769) {
                val exifIfd = tiff + r32(jpeg, e + 8, le).toInt()
                val en = r16(jpeg, exifIfd, le)
                for (k in 0 until en) {
                    val ee = exifIfd + 2 + k * 12
                    if (r16(jpeg, ee, le) == 0x927C) {
                        return r16(jpeg, tiff + r32(jpeg, ee + 8, le).toInt(), le)
                    }
                }
            }
        }
        return -1
    }

    // ---------------------------------------------------------------- ① MakerNote 配对标识

    @Test
    fun upsertMakerNoteAsciiTag_createsExifWhenAbsent() {
        val uuid = "1E874403-E522-4589-948A-E97AC157F32D"
        val jpeg = minimalJpeg()
        val out = ExifUtil.upsertMakerNoteAsciiTag(jpeg, 0x0011, uuid)

        assertTrue("输出应比输入更长（新增 EXIF/MakerNote）", out.size > jpeg.size)
        assertEquals("应能读回 0x0011 的 ASCII 值", uuid, readMakerNoteAscii(out, 0x0011))
        assertEquals(0xFF, out[0].toInt() and 0xFF)
        assertEquals(0xD8, out[1].toInt() and 0xFF)
        assertTrue("应保留原有 XMP 段", indexOfText(out, "x:xmpmeta") > 0)
    }

    @Test
    fun upsertMakerNoteAsciiTag_extendsExistingMakerNoteAndKeepsOtherTags() {
        val uuid = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"
        val first = ExifUtil.upsertMakerNoteAsciiTag(minimalJpeg(), 0x0003, "RUNTIME")
        assertEquals("RUNTIME", readMakerNoteAscii(first, 0x0003))

        val out = ExifUtil.upsertMakerNoteAsciiTag(first, 0x0011, uuid)
        assertEquals("新标签应写入", uuid, readMakerNoteAscii(out, 0x0011))
        assertEquals("旧标签必须完好保留", "RUNTIME", readMakerNoteAscii(out, 0x0003))
    }

    @Test
    fun upsertMakerNoteAsciiTag_replacesSameTagWithoutDuplicating() {
        val out1 = ExifUtil.upsertMakerNoteAsciiTag(minimalJpeg(), 0x0011, "OLD-IDENTIFIER")
        val out2 = ExifUtil.upsertMakerNoteAsciiTag(out1, 0x0011, "NEW-IDENTIFIER")
        assertEquals("应替换而不是追加重复标签", "NEW-IDENTIFIER", readMakerNoteAscii(out2, 0x0011))
        assertEquals("MakerNote IFD 条目数应为 1", 1, makerNoteEntryCount(out2))
    }

    // ---------------------------------------------------------------- ② 静帧时刻

    /** 造一条带 mebx 轨 + 空 edit 的 Apple 风格 MOV。 */
    private fun appleMovWithEmptyEdit(movieTimescale: Int, emptyEditDuration: Int): ByteArray {
        val ftyp = box(
            "ftyp",
            concat("qt  ".toByteArray(Charsets.US_ASCII), be32(0x200), "qt  ".toByteArray(Charsets.US_ASCII))
        )
        // mvhd / mdhd：version/flags + creation + modification + timescale + duration
        // （顺序不能错：timescale 必须落在 payload+12，解析器按此读取）
        fun timingBox(type: String) = fullBox(
            type,
            concat(be32(0), be32(0), be32(movieTimescale), be32(0))
        )
        val hdlr = fullBox("hdlr", concat(be32(0), "meta".toByteArray(Charsets.US_ASCII), ByteArray(12), byteArrayOf(0)))
        // stsd：entry_count=1 + [size][type='mebx']
        val stsd = fullBox("stsd", concat(be32(1), be32(16), "mebx".toByteArray(Charsets.US_ASCII), ByteArray(4)))
        val stbl = box("stbl", stsd)
        val minf = box("minf", stbl)
        val mdia = box("mdia", concat(timingBox("mdhd"), hdlr, minf))
        // elst：segment_duration=740, media_time=-1, media_rate=1.0
        val elst = fullBox("elst", concat(be32(1), be32(emptyEditDuration), be32(-1), be32(0x00010000)))
        val edts = box("edts", elst)
        val trak = box("trak", concat(edts, mdia))
        val moov = box("moov", concat(timingBox("mvhd"), trak))
        return concat(ftyp, moov)
    }

    @Test
    fun appleStillImageTimeUs_readsEmptyEditOfMebxTrack() {
        // iPhone 15 Pro / iOS 18.5 实测：movie timescale 600、空 edit 740 → 1.2333 s
        val mov = appleMovWithEmptyEdit(600, 740)
        // 失败时列出顶层盒，便于区分「fixture 写错」与「解析器写错」
        val diag = StringBuilder("size=${mov.size} |")
        var p = 0
        while (p + 8 <= mov.size) {
            val size = u32(mov, p)
            diag.append(" ${String(mov, p + 4, 4, Charsets.ISO_8859_1)}@$p+$size")
            if (size <= 0L) break
            p += size.toInt()
        }
        assertEquals("布局诊断:$diag", 1_233_333L, Mp4Util.appleStillImageTimeUs(mov))
    }

    @Test
    fun appleStillImageTimeUs_returnsMinusOneWithoutMebxOrElst() {
        val plain = concat(
            box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + be32(0x200)),
            box("moov", fullBox("mvhd", concat(be32(0), be32(0), be32(0), be32(600), be32(0))))
        )
        assertEquals(-1L, Mp4Util.appleStillImageTimeUs(plain))
    }

    // ---------------------------------------------------------------- ③ 整条写路径

    @Test
    fun appleWrite_putsPairingIdIntoMakerNoteAndContentIdentifierIntoMov() {
        val video = concat(
            box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + be32(0x200) + "isom".toByteArray(Charsets.US_ASCII)),
            box("moov", fullBox("mvhd", concat(be32(0), be32(0), be32(0), be32(600), be32(0))))
        )
        val asset = LivePhotoAsset(
            primaryJpeg = minimalJpeg(),
            gainmapJpeg = null,
            videoMp4 = video,
            sourceFormat = "google"
        )
        asset.presentationTsUs = 1_200_000L

        val outDir = Files.createTempDirectory("apple_live_test").toFile()
        try {
            val outs = ApplePlugin().write(asset, outDir.path, "case", ::log, mutableMapOf())
            assertEquals(2, outs.size)
            val jpg = File(outs.first { it.endsWith(".jpg") }).readBytes()
            val mov = File(outs.first { it.endsWith(".mov") }).readBytes()

            val id = readMakerNoteAscii(jpg, 0x0011)
            assertNotNull("图片侧必须写入 MakerNote 0x0011 配对标识", id)
            assertTrue(
                "配对标识应是带连字符的 UUID，实际 '$id'",
                Regex("^[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$").matches(id!!)
            )
            assertEquals("MOV 侧的 content.identifier 必须与图片侧一致", id, readMovContentIdentifier(mov))
        } finally {
            outDir.deleteRecursively()
        }
    }

    /** 从 MOV 的 keys/ilst 里读 com.apple.quicktime.content.identifier 的值。 */
    private fun readMovContentIdentifier(mov: ByteArray): String? {
        val keyIdx = indexOfText(mov, "com.apple.quicktime.content.identifier")
        if (keyIdx < 0) return null
        val re = Regex("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}")
        return re.find(String(mov, keyIdx, mov.size - keyIdx, Charsets.ISO_8859_1))?.value
    }
}
