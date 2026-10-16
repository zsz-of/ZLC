package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.Mp4Util
import com.zsz.zlivephoto.core.XmpTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `format-matrix.md` 风险清单 R6 / R7 / R11 / R12 的回归测试。
 *
 * 每条用例都把「真机字节布局」固化成断言，防止后续重构把修复改回去：
 * - R6  lpex 去重必须解析 moov 子 box，而不是 64 KiB 字节窗口（窗口对 moov 在 mdat 之后必然漏判）
 * - R7  丢弃 GainMap 的输出必须同步移除主图 MPF APP2 段（否则 MPF img1 指向视频区）
 * - R11 lpex 几何矩阵必须是整数（真机为 `[1,0,0,0,1,0,0,0,1]`，写成 Double 会多 ~36 字节）
 * - R12 OPPO / vivo_single 模板必须带真机普遍存在的 `Camera:` 三件套
 */
class FormatFixContractTest {

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
        var pos = 0
        for (p in parts) {
            System.arraycopy(p, 0, out, pos, p.size)
            pos += p.size
        }
        return out
    }

    private fun ascii(data: ByteArray): String = String(data, Charsets.ISO_8859_1)

    private fun indexOfText(data: ByteArray, token: String, from: Int = 0): Int {
        val t = token.toByteArray(Charsets.US_ASCII)
        outer@ for (i in from..data.size - t.size) {
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
            0x00, 0x02, 0x00, 0x03, 0x03,
            0x01, 0x11, 0x00, 0x02, 0x11, 0x00, 0x03, 0x11, 0x00
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

    /** APP2 段（marker 0xFFE2 + 长度 + 载荷）。 */
    private fun app2(payload: ByteArray): ByteArray {
        val len = payload.size + 2
        return concat(byteArrayOf(0xFF.toByte(), 0xE2.toByte(), (len shr 8).toByte(), len.toByte()), payload)
    }

    private fun sampleAsset(): LivePhotoAsset {
        val asset = LivePhotoAsset(
            primaryJpeg = minimalJpeg(),
            gainmapJpeg = null,
            videoMp4 = box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + ByteArray(12)),
            sourceFormat = "oppo"
        )
        asset.videoInfo = mutableMapOf("width" to 1920, "height" to 1080, "rotation" to 0)
        asset.presentationTsUs = 1_000_000L
        return asset
    }

    // ---------------------------------------------------------------- R11

    @Test
    fun lpexGeometryMatrices_areIntegers() {
        val payload = OppoPlugin.buildLpexPayload(sampleAsset())
        val json = ascii(payload)

        assertTrue(
            "photoCropMatrix 必须写成整数矩阵（真机布局），实际 JSON：$json",
            json.contains("\"photoCropMatrix\":[1,0,0,0,1,0,0,0,1]")
        )
        assertTrue(
            "photoEisMatrix 必须写成整数矩阵（真机布局）",
            json.contains("\"photoEisMatrix\":[1,0,0,0,1,0,0,0,1]")
        )
        assertFalse(
            "几何矩阵不得再出现 Double 形式 1.0（会让 lpex box 比真机多 ~36 字节）",
            json.contains("photoCropMatrix\":[1.0") || json.contains("photoEisMatrix\":[1.0")
        )
        // 因子仍必须是浮点（真机确为 1.0 / [1.0,1.0]）
        assertTrue("photoCropFactor 仍应为浮点 1.0", json.contains("\"photoCropFactor\":1.0"))
        assertTrue("photoEisCropFactor 仍应为浮点数组", json.contains("\"photoEisCropFactor\":[1.0,1.0]"))
    }

    // ---------------------------------------------------------------- R12

    @Test
    fun oppoXmp_carriesCameraTriple() {
        val xmp = XmpTemplate.buildOppoXmp(ptsUs = 1_234_000L, gainmapLen = null, videoLen = 300, mp4Len = 200)

        assertTrue("OPPO XMP 必须声明 Camera 命名空间", xmp.contains("xmlns:Camera=\"http://ns.google.com/photos/1.0/camera/\""))
        assertTrue("OPPO XMP 缺少 Camera:MotionPhoto", xmp.contains("Camera:MotionPhoto=\"1\""))
        assertTrue("OPPO XMP 缺少 Camera:MotionPhotoVersion", xmp.contains("Camera:MotionPhotoVersion=\"1\""))
        assertTrue(
            "OPPO XMP 的 Camera:MotionPhotoPresentationTimestampUs 必须与 GCamera 同值",
            xmp.contains("Camera:MotionPhotoPresentationTimestampUs=\"1234000\"")
        )
        assertTrue("GCamera:MotionPhoto 仍应保留", xmp.contains("GCamera:MotionPhoto=\"1\""))
    }

    @Test
    fun vivoSingleXmp_carriesCameraTriple() {
        val xmp = XmpTemplate.buildVivoSingleXmp(ptsUs = 1_234_000L, gainmapLen = null, videoLen = 300)

        assertTrue("vivo_single XMP 必须声明 Camera 命名空间", xmp.contains("xmlns:Camera=\"http://ns.google.com/photos/1.0/camera/\""))
        assertTrue("vivo_single XMP 缺少 Camera:MotionPhoto", xmp.contains("Camera:MotionPhoto=\"1\""))
        assertTrue("vivo_single XMP 缺少 Camera:MotionPhotoVersion", xmp.contains("Camera:MotionPhotoVersion=\"1\""))
        assertTrue(
            "vivo_single 的 Camera 时间戳必须与 GCamera 同值",
            xmp.contains("Camera:MotionPhotoPresentationTimestampUs=\"1234000\"")
        )
        assertTrue("GCamera:MotionPhoto 仍应保留", xmp.contains("GCamera:MotionPhoto=\"1\""))
    }

    // ---------------------------------------------------------------- R6

    @Test
    fun hasMoovChild_findsLpexWhenMoovFollowsMdat() {
        // moov 在 mdat 之后（非 faststart）且 mdat 远大于 64 KiB：
        // 旧的 64 KiB 字节窗口搜索必然漏判，box 解析必须命中。
        val lpex = box("lpex", ByteArray(24))
        val moov = box("moov", concat(box("mvhd", ByteArray(8)), lpex, box("trak", ByteArray(8))))
        val data = concat(
            box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + ByteArray(12)),
            box("mdat", ByteArray(70_000)),
            moov
        )

        val lpexPos = indexOfText(data, "lpex")
        assertTrue("前置条件：lpex 应位于 64 KiB 窗口之外", lpexPos > 65_536)

        assertTrue("box 解析必须判定 moov 内含 lpex", Mp4Util.hasMoovChild(data, "lpex"))
        assertFalse("moov 内没有的 box 类型必须返回 false", Mp4Util.hasMoovChild(data, "uuid"))

        // 反面控制：moov 位于 mdat 之前、不含 lpex 时同样必须为 false
        val noLpex = concat(
            box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + ByteArray(12)),
            box("moov", concat(box("mvhd", ByteArray(8)), box("trak", ByteArray(8)))),
            box("mdat", ByteArray(1024))
        )
        assertFalse("moov 无 lpex 时应返回 false", Mp4Util.hasMoovChild(noLpex, "lpex"))
    }

    // ---------------------------------------------------------------- R7

    @Test
    fun stripMpfApp2_removesOnlyMpfSegment() {
        val mpf = app2("MPF\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(24) { 0x11 })
        val xmp = JpegUtil.buildXmpApp1(
            """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description rdf:about=""/></rdf:RDF></x:xmpmeta>"""
        )
        val sof = byteArrayOf(
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11, 0x08,
            0x00, 0x02, 0x00, 0x03, 0x03,
            0x01, 0x11, 0x00, 0x02, 0x11, 0x00, 0x03, 0x11, 0x00
        )
        val sos = byteArrayOf(
            0xFF.toByte(), 0xDA.toByte(), 0x00, 0x0C, 0x03,
            0x01, 0x00, 0x02, 0x00, 0x03, 0x00, 0x00, 0x3F, 0x00
        )
        val eoi = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        val src = concat(byteArrayOf(0xFF.toByte(), 0xD8.toByte()), mpf, xmp, sof, sos, eoi)

        val out = JpegUtil.stripMpfApp2(src)

        assertEquals("应恰好移除 MPF APP2 段", src.size - mpf.size, out.size)
        assertEquals("MPF 段必须已消失", -1, indexOfText(out, "MPF"))
        assertTrue("XMP APP1 必须保留", indexOfText(out, "xmpmeta") > 0)
        assertTrue("SOI 必须保留", out[0] == 0xFF.toByte() && out[1] == 0xD8.toByte())
        assertTrue(
            "EOI 必须保留",
            out[out.size - 2] == 0xFF.toByte() && out[out.size - 1] == 0xD9.toByte()
        )

        // 无 MPF 时必须原样返回（不能因为去掉 APP2 而误伤普通照片）
        val plain = concat(byteArrayOf(0xFF.toByte(), 0xD8.toByte()), xmp, sof, sos, eoi)
        assertEquals("无 MPF 时不得改动字节", plain.size, JpegUtil.stripMpfApp2(plain).size)
    }
}
