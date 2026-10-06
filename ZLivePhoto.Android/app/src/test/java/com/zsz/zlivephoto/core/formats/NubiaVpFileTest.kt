package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 回归测试：努比亚（Nubia）动态照片格式 `nubiaVpfile`。
 *
 * 规范（逆向 `cn.nubia.gallery3d`，见 `.agents/research/nubia-gallery-requirements.md`）：
 * ```
 * [JPEG][0x00][MP4][BE64(JPEG 长度 L)][UTF-16BE "nubiaVpfile"(22B)]
 * ```
 * 识别另外要求 JPEG 的 EXIF `UserComment`(0x9286) 里含子串 `"livep"`；
 * 尾部与 UserComment 必须**双写**，缺一即失败。
 */
class NubiaVpFileTest {

    private fun log(level: String, msg: String, tag: String) {
        println("[$level][$tag] $msg")
    }

    // ---------------------------------------------------------------- 工具

    private fun concat(vararg parts: ByteArray): ByteArray {
        val out = ByteArray(parts.sumOf { it.size })
        var p = 0
        for (x in parts) { System.arraycopy(x, 0, out, p, x.size); p += x.size }
        return out
    }

    private fun indexOfText(data: ByteArray, token: String, from: Int = 0): Int {
        val t = token.toByteArray(Charsets.ISO_8859_1)
        outer@ for (i in from..data.size - t.size) {
            for (j in t.indices) if (data[i + j] != t[j]) continue@outer
            return i
        }
        return -1
    }

    private fun readU64BE(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

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

    private fun fakeMp4(): ByteArray {
        val ftyp = ByteArray(20)
        ftyp[3] = 0x14
        System.arraycopy("ftyp".toByteArray(Charsets.US_ASCII), 0, ftyp, 4, 4)
        System.arraycopy("isom".toByteArray(Charsets.US_ASCII), 0, ftyp, 8, 4)
        return concat(ftyp, ByteArray(64) { (it + 1).toByte() })
    }

    /** 按 EXIF 规范读 UserComment(0x9286) 的值（类型 UNDEFINED）。 */
    private fun readUserComment(jpeg: ByteArray): String? {
        var p = 2
        var tiff = -1
        var le = false
        while (p + 4 <= jpeg.size) {
            if (jpeg[p].toInt() and 0xFF != 0xFF) break
            val marker = jpeg[p + 1].toInt() and 0xFF
            val segLen = ((jpeg[p + 2].toInt() and 0xFF) shl 8) or (jpeg[p + 3].toInt() and 0xFF)
            if (marker == 0xE1 && segLen >= 8 && String(jpeg, p + 4, 6, Charsets.ISO_8859_1) == "Exif\u0000\u0000") {
                tiff = p + 10
                le = jpeg[tiff] == 'I'.code.toByte()
                break
            }
            p += 2 + segLen
        }
        if (tiff < 0) return null
        fun r16(off: Int): Int = if (le) {
            ((jpeg[off + 1].toInt() and 0xFF) shl 8) or (jpeg[off].toInt() and 0xFF)
        } else {
            ((jpeg[off].toInt() and 0xFF) shl 8) or (jpeg[off + 1].toInt() and 0xFF)
        }
        fun r32(off: Int): Long {
            var v = 0L
            if (le) for (i in 3 downTo 0) v = (v shl 8) or (jpeg[off + i].toLong() and 0xFF)
            else for (i in 0 until 4) v = (v shl 8) or (jpeg[off + i].toLong() and 0xFF)
            return v
        }
        val ifd0 = tiff + r32(tiff + 4).toInt()
        val n = r16(ifd0)
        for (i in 0 until n) {
            val e = ifd0 + 2 + i * 12
            if (r16(e) != 0x8769) continue
            val exifIfd = tiff + r32(e + 8).toInt()
            val en = r16(exifIfd)
            for (k in 0 until en) {
                val ee = exifIfd + 2 + k * 12
                if (r16(ee) != 0x9286) continue
                val count = r32(ee + 4).toInt()
                val abs = tiff + r32(ee + 8).toInt()
                return String(jpeg, abs, count, Charsets.ISO_8859_1)
            }
        }
        return null
    }

    // ---------------------------------------------------------------- 断言

    @Test
    fun write_emitsVpfileTailAndLivepUserComment() {
        val video = fakeMp4()
        val jpeg = minimalJpeg()
        val asset = LivePhotoAsset(
            primaryJpeg = jpeg, gainmapJpeg = null, videoMp4 = video, sourceFormat = "google"
        )
        val outDir = Files.createTempDirectory("nubia_write").toFile()
        try {
            val path = NubiaPlugin().write(asset, outDir.path, "case", ::log, mutableMapOf()).single()
            val data = File(path).readBytes()

            // 尾部魔数：UTF-16BE
            val magic = "nubiaVpfile".toByteArray(Charsets.UTF_16BE)
            assertEquals(22, magic.size)
            assertArrayEquals("尾部必须是 UTF-16BE 的 nubiaVpfile", magic, data.copyOfRange(data.size - 22, data.size))

            // BE64 的 JPEG 长度 + 1 字节 0x00 填充 + 视频区间
            val l = readU64BE(data, data.size - 30)
            assertTrue("JPEG 长度应大于原图（多出 EXIF/UserComment）", l > jpeg.size)
            assertEquals("JPEG 之后必须是 1 字节 0x00 填充", 0, data[l.toInt()].toInt())
            assertEquals("总长应 = L + 1 + 视频 + 30", data.size.toLong(), l + 1 + video.size + 30)
            assertArrayEquals(
                "视频必须逐字节保留",
                video, data.copyOfRange(l.toInt() + 1, data.size - 30)
            )

            // UserComment 含 livep
            val comment = readUserComment(data.copyOfRange(0, l.toInt()))
            assertTrue("UserComment 应含 livep，实际 '$comment'", comment != null && comment.contains("livep"))
        } finally {
            outDir.deleteRecursively()
        }
    }

    @Test
    fun detect_recognizesWrittenFileAndReadsBack() {
        val video = fakeMp4()
        val asset = LivePhotoAsset(
            primaryJpeg = minimalJpeg(), gainmapJpeg = null, videoMp4 = video, sourceFormat = "google"
        )
        val outDir = Files.createTempDirectory("nubia_roundtrip").toFile()
        try {
            val plugin = NubiaPlugin()
            val path = plugin.write(asset, outDir.path, "case", ::log, mutableMapOf()).single()

            assertEquals("写入的文件应被本插件识别", 95, plugin.detect(path))
            val back = plugin.read(path, ::log)
            assertArrayEquals("读回的视频必须逐字节一致", video, back.videoMp4)
            assertEquals("读回的封面应等于 JPEG 段长度", readU64BE(File(path).readBytes(), File(path).length().toInt() - 30).toInt(), back.primaryJpeg.size)
        } finally {
            outDir.deleteRecursively()
        }
    }

    @Test
    fun detect_rejectsPlainJpegAndAsciiMagic() {
        val outDir = Files.createTempDirectory("nubia_negative").toFile()
        try {
            val plugin = NubiaPlugin()
            val plain = File(outDir, "plain.jpg")
            plain.writeBytes(minimalJpeg())
            assertEquals("普通 JPEG 不应被识别", 0, plugin.detect(plain.path))

            // 负例：把魔数换成 ASCII 编码（相册不认）→ 不应识别
            val video = fakeMp4()
            val asset = LivePhotoAsset(
                primaryJpeg = minimalJpeg(), gainmapJpeg = null, videoMp4 = video, sourceFormat = "google"
            )
            val ok = File(plugin.write(asset, outDir.path, "ok", ::log, mutableMapOf()).single()).readBytes()
            val bad = ok.copyOf()
            System.arraycopy("nubiaVpfile".toByteArray(Charsets.US_ASCII), 0, bad, bad.size - 22, 11)
            val badFile = File(outDir, "ascii_magic.jpg")
            badFile.writeBytes(bad)
            assertEquals("ASCII 魔数（非 UTF-16BE）不应被识别", 0, plugin.detect(badFile.path))
        } finally {
            outDir.deleteRecursively()
        }
    }
}
