package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.FooterUtil
import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.Mp4Util
import com.zsz.zlivephoto.core.XmpTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 真机契约回归测试：把 OPPO 相册 APK（com.coloros.gallery3d 17.10.7）里
 * 播放器侧的算法**逐条复刻**成 Kotlin，再喂本工具写出的文件，验证契约成立。
 *
 * 逆向来源（workspaceId 3jtpznah）：
 *
 * 1. `Lcom/oplus/tbl/exoplayer2/extractor/jpeg/JpegExtractor;`
 *    - 扫到 APP1 且段头为 `http://ns.adobe.com/xap/1.0/` 时调用
 *      `getMotionPhotoMetadata(String xmp, long fileSize)`（第二参 = `input.getLength()`）；
 *    - 成功后把 `MotionPhotoMetadata.videoStartPosition` 存进 `mp4StartPosition`，
 *      并以此构造 `StartOffsetExtractorInput` / `StartOffsetExtractorOutput` 交给 `Mp4Extractor`。
 *    即：**播放起点完全由 XMP 决定，扫描器不自己找 MP4**。
 *
 * 2. `Lcom/oplus/tbl/exoplayer2/extractor/jpeg/MotionPhotoDescription;->getMotionPhotoMetadata(J)`
 *    的反编译算法（见 [deviceMotionPhotoMetadata]）化简后为：
 *      videoStartPosition = fileSize − Item:Length(最后一个 video/mp4 项)
 *      videoSize          = Item:Length
 *    且要求 `Container:Directory` 至少 2 项、其中一项 `Item:Mime="video/mp4"`。
 *
 * 3. `Lcom/oplus/tbl/exoplayer2/extractor/mp4/Mp4Extractor;->readAtomHeader` 唯一的
 *    size 校验是 `atomSize < atomHeaderBytesRead` → 抛
 *    `ParserException("Atom size less than header length (unsupported).")`；
 *    `atomSize` 在本 fork 里是 **long**（`readUnsignedInt()`），所以 0x7669766F ≈ 1.85GiB
 *    这样的假 size 不会被这条校验拦住，只能靠 ISO box 布局本身合法。
 *
 * 结论：尾部 trailer 必须是**完整 ISOBMFF box**，且 XMP 的 `Item:Length` 必须等于
 * 「MP4 起点 → 文件尾」的全部字节数。本文件把这两条都固化成断言。
 */
class OppoDeviceContractTest {

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

    private fun indexOfText(data: ByteArray, token: String, from: Int = 0): Int {
        val t = token.toByteArray(Charsets.US_ASCII)
        outer@ for (i in from..data.size - t.size) {
            for (j in t.indices) if (data[i + j] != t[j]) continue@outer
            return i
        }
        return -1
    }

    private fun ascii(data: ByteArray): String = String(data, Charsets.ISO_8859_1)

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

    private fun syntheticVideo(): ByteArray = concat(
        box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + ByteArray(12)),
        box("moov", ByteArray(8) { 0x44 })
    )

    private fun sampleAsset(gainmap: Boolean): LivePhotoAsset {
        val asset = LivePhotoAsset(
            primaryJpeg = minimalJpeg(),
            gainmapJpeg = if (gainmap) minimalJpeg() else null,
            videoMp4 = syntheticVideo(),
            sourceFormat = "google"
        )
        asset.presentationTsUs = 1_000_000L
        return asset
    }

    // ---------------------------------------------------------------- 设备算法复刻

    /** XMP 里一个 `Container:Item` 的播放器侧视图。 */
    private class DeviceItem(val mime: String?, val length: Long, val padding: Long)

    /**
     * 复刻 `MotionPhotoDescription;->getMotionPhotoMetadata(J)`，返回
     * `longArrayOf(imageStartPosition, imageSize, videoStartPosition, videoSize)`，
     * 契约不成立时返回 null（真机即「取不到视频」）。
     *
     * smali 逐条对照（`.registers 24`，long 占偶对齐双寄存器）：
     * ```
     *   v6 = fileSize; acc(v8) = 0; i = items.size - 1
     *   loop:  v2 = "video/mp4".equals(item.mime) | acc
     *          if (i == 0) { v6 -= padding;  v13 = 0 }
     *          else        { v13 = v6 - length }
     *          // :goto_39 的寄存器交换 —— v6/v13 在此互换角色
     *          v19 = v13; v13 = v6; v6 = v19
     *          if (v2 != 0 && v6 != v13) { v17 = v13 - v6; v15 = v6; acc = 0 } else { acc = v2 }
     *          if (i == 0) { v9 = v6; v11 = v13 }
     *   return new MotionPhotoMetadata(v9, v11, pts, v15, v17)
     * ```
     * 交换后：非首项 `v6 = cur - length`(新)、`v13 = cur`(旧)；首项 `v6 = 0`、`v13 = cur - padding`。
     * 因此 **首项分支里 imageStartPosition = v6 = 0、imageSize = v13 = 主图长度**；
     * videoStartPosition = v15、videoSize = v17 仍为「video 项自身起点与长度」。
     */
    private fun deviceMotionPhotoMetadata(
        items: List<DeviceItem>, fileSize: Long
    ): LongArray? {
        if (items.size < 2) return null
        var cur = fileSize
        var imageStart = -1L
        var imageSize = -1L
        var videoStart = -1L
        var videoSize = -1L
        var acc = false
        for (idx in items.indices.reversed()) {
            val item = items[idx]
            val isVideo = ("video/mp4" == item.mime) || acc
            val before: Long
            if (idx == 0) {
                // 首项（Primary）：只减 padding，并把游标归零；before 换成减完后的值。
                before = cur - item.padding
                cur = 0L
            } else {
                before = cur
                cur -= item.length
            }
            if (isVideo && cur != before) {
                videoSize = before - cur
                videoStart = cur
                acc = false
            } else {
                acc = isVideo
            }
            if (idx == 0) {
                imageStart = cur
                imageSize = before
            }
        }
        if (videoStart < 0 || videoSize < 0 || imageStart < 0 || imageSize < 0) return null
        return longArrayOf(imageStart, imageSize, videoStart, videoSize)
    }

    /** 严格 ISO BMFF 遍历：任何 box 越界即失败，且必须精确闭合到区域末尾。 */
    private fun strictWalk(region: ByteArray, what: String): List<String> {
        val types = mutableListOf<String>()
        var pos = 0
        while (pos < region.size) {
            assertTrue("$what: box 头越界 @$pos", pos + 8 <= region.size)
            val size = u32(region, pos)
            assertTrue(
                "$what: size=$size 的 box 越界 @$pos（区域剩余 ${region.size - pos}）",
                size >= 8 && pos + size <= region.size.toLong()
            )
            types.add(String(region, pos + 4, 4, Charsets.US_ASCII))
            pos += size.toInt()
        }
        assertEquals("$what: 遍历必须精确闭合到区域末尾", region.size, pos)
        return types
    }

    private fun deviceItems(data: ByteArray): List<DeviceItem> {
        val info = XmpTemplate.parseMotionXmp(ascii(data))
        return info.items.map { DeviceItem(it.mime, (it.length ?: 0).toLong(), it.padding.toLong()) }
    }

    // ---------------------------------------------------------------- 断言

    /**
     * 主契约：本工具写出的文件，用 OPPO 播放器的算法解出来的
     * `videoStartPosition` 必须精确落在内嵌 MP4 的起点，`videoSize` 必须覆盖到文件尾。
     */
    @Test
    fun deviceContract_videoStartLandsOnEmbeddedMp4() {
        val outDir = Files.createTempDirectory("oppo_contract").toFile()
        try {
            val asset = sampleAsset(gainmap = false)
            val file = OppoPlugin()
                .write(asset, outDir.path, "plain", ::log, mutableMapOf())
                .single()
            val data = File(file).readBytes()

            val ftypIdx = indexOfText(data, "ftyp")
            assertTrue("输出中应能定位 ftyp", ftypIdx >= 4)
            val mp4Start = (ftypIdx - 4).toLong()

            val items = deviceItems(data)
            assertEquals("无 GainMap 时应为 Primary + MotionPhoto 两项", 2, items.size)
            assertEquals("image/jpeg", items[0].mime)
            assertEquals("video/mp4", items[1].mime)

            val meta = deviceMotionPhotoMetadata(items, data.size.toLong())
            assertNotNull("真机算法必须能从本产物解出 MotionPhotoMetadata（否则长按取不到视频）", meta)
            assertEquals(
                "videoStartPosition 必须等于内嵌 MP4 起点（= 文件尾 − Item:Length）",
                mp4Start, meta!![2]
            )
            assertEquals(
                "videoSize 必须覆盖 MP4 起点直到文件尾（含 uuid box trailer）",
                data.size.toLong() - mp4Start, meta[3]
            )
            assertEquals("imageStartPosition 应为 0（Primary 在最前）", 0L, meta[0])
            // 真机 imageSize = 「MP4 起点之前的全部静态图字节」。注意主图在写盘时已被
            // 插入 XMP APP1 段，因此它大于输入资产的 primaryJpeg.size。
            assertEquals(
                "imageSize 应为 MP4 起点之前的静态图字节数（含写入的 XMP）",
                mp4Start, meta[1]
            )

            // 同一段区间必须是**良构**的 ISOBMFF：ftyp → moov → uuid trailer，精确闭合到 EOF
            val region = data.copyOfRange(mp4Start.toInt(), data.size)
            assertEquals(
                "MP4 区段应为 ftyp/moov/uuid 且无越界 box（裸 trailer 时代这里读出的 size≈1.85GiB）",
                listOf("ftyp", "moov", "uuid"), strictWalk(region, "MP4 区段")
            )
        } finally {
            outDir.deleteRecursively()
        }
    }

    /**
     * 带 GainMap（3 个 Container 项）时，播放器自尾向头走到 Primary 才停，
     * video 起点仍必须落在 MP4 起点。
     */
    @Test
    fun deviceContract_withGainmap_stillLandsOnMp4() {
        val outDir = Files.createTempDirectory("oppo_contract_gm").toFile()
        try {
            val asset = sampleAsset(gainmap = true)
            val file = OppoPlugin()
                .write(asset, outDir.path, "hdr", ::log, mutableMapOf())
                .single()
            val data = File(file).readBytes()
            val mp4Start = (indexOfText(data, "ftyp") - 4).toLong()

            val items = deviceItems(data)
            assertEquals("带 GainMap 时应为 Primary+GainMap+MotionPhoto 三项", 3, items.size)
            assertEquals("GainMap", XmpTemplate.parseMotionXmp(ascii(data)).items[1].semantic)

            val meta = deviceMotionPhotoMetadata(items, data.size.toLong())
            assertNotNull("带 GainMap 的产物同样必须能解出 MotionPhotoMetadata", meta)
            assertEquals("videoStartPosition 仍须等于 MP4 起点", mp4Start, meta!![2])
            assertEquals(
                "videoSize 仍须覆盖到文件尾",
                data.size.toLong() - mp4Start, meta[3]
            )
            assertEquals("GainMap 场景 imageStartPosition 仍为 0", 0L, meta[0])
            assertEquals(
                "imageSize 应为主图长度（= MP4 起点 − GainMap）",
                mp4Start - asset.gainmapJpeg!!.size, meta[1]
            )
            assertEquals(
                "主图 + GainMap 应正好拼到 MP4 起点",
                mp4Start, (meta[1] + asset.gainmapJpeg!!.size).toLong()
            )
        } finally {
            outDir.deleteRecursively()
        }
    }

    /**
     * 反面控制组：把 8 字节 box 头去掉（修复前的写法），同一段字节就不再是良构 ISOBMFF ——
     * 首 4 字节 `vivo`(0x7669766F) 会被读成一个远超剩余字节数的 box size。
     *
     * 注意这里断言的是「布局非良构」这一可证事实；Mp4Extractor 对超大未解析 atom
     * 会走 payload 跳过逻辑，因此**不能**仅凭此断言它就一定抛异常。
     */
    @Test
    fun bareTrailer_isNotWellFormedIsobmff() {
        val json = FooterUtil.buildFooterJson(
            linkedMapOf(
                "com.android.camera.imageTime" to 34,
                "com.android.camera.livephoto" to FooterUtil.oppoFixedId,
                "version" to 2200
            )
        )
        val footer = FooterUtil.buildFooter(json, FooterUtil.oppoFixedId, FooterUtil.extPrefix)
        val trailer = Mp4Util.wrapVivoUuidBox(footer)
        val video = concat(
            box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + ByteArray(12)),
            box("moov", ByteArray(8) { 0x44 })
        )

        val fixed = concat(video, trailer)
        assertEquals(listOf("ftyp", "moov", "uuid"), strictWalk(fixed, "修复后"))

        // 旧写法：裸 payload 紧跟在视频后面
        val legacy = concat(video, footer)
        val legacySize = u32(legacy, video.size)
        assertEquals(
            "裸 trailer 首 4 字节是 'vivo'（= 0x7669766F，约 1.85GiB）",
            0x7669766FL, legacySize
        )
        assertTrue(
            "该假 size 远超区域剩余字节数（${legacy.size - video.size}），布局非良构",
            legacySize > (legacy.size - video.size).toLong()
        )
        // 且 box 类型字段读出来是 "Medi"，不是任何已知 atom
        assertEquals("Medi", String(legacy, video.size + 4, 4, Charsets.US_ASCII))
    }
}
