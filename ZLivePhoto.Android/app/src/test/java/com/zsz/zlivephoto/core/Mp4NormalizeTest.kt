package com.zsz.zlivephoto.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Apple MOV → 标准 MP4 的**确定性**字节级归一回归：
 *
 * 1. `normalizeFtyp`：`qt  `（QuickTime）品牌在 major_brand 与 compatible_brands 里都必须被
 *    清除 —— 只改 major 会让解析器（Media3/AOSP brandSet）继续把产物当 QuickTime 处理；
 * 2. `pruneNonAvTracks`：Apple MOV 的 `meta`(`mebx`) / `tmcd` / `mett` 轨必须被剔除，且
 *    **样本数据零改动**（mdat 逐字节不变、stco 偏移在 moov 前移时同步扣减）；
 * 3. `VideoTrackSanitizer.sanitize`：这类输入必须走纯字节路径，**不触碰 MediaMuxer**
 *    （JVM 单测环境没有可用的 MediaExtractor/MediaMuxer，一旦回退到重封装就会返回 null）；
 * 4. `JpegUtil.replaceOrInsertXmp`：非 JPEG（HEIC 封面）原样返回，不再抛异常中断转换；
 * 5. `QuickClassify.sniff`：HEIC + 同名 MOV 必须被判为动态照片。
 */
class Mp4NormalizeTest {

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

    /** hdlr：version/flags(4) + pre_defined(4) + handler_type(4)。 */
    private fun hdlr(handler: String): ByteArray =
        box("hdlr", ByteArray(8), handler.toByteArray(Charsets.ISO_8859_1))

    private fun stsd(fourcc: String): ByteArray = box("stsd", u32(0), box(fourcc, ByteArray(4)))

    /** trak：mdia 里放 hdlr（+ 可选 sample entry），够本工具的浅层解析用。 */
    private fun trak(handler: String, fourcc: String? = null): ByteArray =
        box("trak", box("mdia", hdlr(handler), fourcc?.let { stsd(it) } ?: ByteArray(0)))

    /** stco：version/flags(4) + entry_count(4) + entries(4 each)。 */
    private fun stco(offsets: List<Long>): ByteArray =
        box("stco", u32(0), u32(offsets.size.toLong()), *offsets.map { u32(it) }.toTypedArray())

    /** ftyp：major_brand + minor_version + compatible_brands。 */
    private fun ftyp(major: String, minor: Long, vararg compat: String): ByteArray {
        var payload = major.toByteArray(Charsets.ISO_8859_1) + u32(minor)
        for (c in compat) payload += c.toByteArray(Charsets.ISO_8859_1)
        return box("ftyp", payload)
    }

    private fun indexOf(data: ByteArray, text: String): Int {
        val needle = text.toByteArray(Charsets.ISO_8859_1)
        outer@ for (i in 0..data.size - needle.size) {
            for (j in needle.indices) if (data[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun contains(data: ByteArray, text: String) = indexOf(data, text) >= 0

    // ---------------------------------------------------------------- 1. 品牌归一

    @Test
    fun normalizeFtyp_scrubsQuickTimeEverywhere() {
        val mov = ftyp("qt  ", 0, "qt  ")
        val out = Mp4Util.normalizeFtyp(mov)

        assertEquals("major_brand 必须改为 isom", "isom", String(out, 8, 4, Charsets.ISO_8859_1))
        assertEquals(
            "compatible_brands 里的 qt   也必须清掉（只改 major 仍会被判为 QuickTime）",
            "isom", String(out, 16, 4, Charsets.ISO_8859_1)
        )
        assertTrue("产物 ftyp 中不得残留任何 qt   标记", !contains(out, "qt  "))
        assertEquals("字节数不变（不移动 box，无需修 stco）", mov.size, out.size)
    }

    @Test
    fun normalizeFtyp_leavesStandardFtypUntouched() {
        val mp4 = ftyp("isom", 512, "isom", "iso2", "avc1", "mp41")
        assertSame("已是标准 MP4 时必须零改动（返回原引用）", mp4, Mp4Util.normalizeFtyp(mp4))
    }

    // ---------------------------------------------------------------- 2. 剔除附加轨

    @Test
    fun pruneNonAvTracks_removesTrailingMetaTrackAndKeepsMdat() {
        val metaTrak = trak("meta", "mebx")
        val mdat = box("mdat", ByteArray(64) { 0x5A })
        val mov = ftyp("qt  ", 0, "qt  ") + mdat + box("moov", trak("vide", "avc1"), metaTrak)
        assertEquals(
            "前置条件：Apple 形态 = vide + meta 两条轨",
            listOf("vide", "meta"), Mp4Util.trackHandlers(mov)
        )

        val out = Mp4Util.normalizeMovToMp4(mov)

        assertEquals("只应剩视频轨", listOf("vide"), Mp4Util.trackHandlers(out))
        assertTrue("不得再出现 mebx 元数据轨", !contains(out, "mebx"))
        assertTrue("不得残留 QuickTime 品牌", !contains(out, "qt  "))
        assertEquals("文件应恰好缩小被删 trak 的字节数", mov.size - metaTrak.size, out.size)
        // mdat 载荷逐字节不变（零拷贝：不重编码、不重封装）
        val mdatOut = out.copyOfRange(indexOf(out, "mdat") - 4, indexOf(out, "mdat") - 4 + mdat.size)
        assertArrayEquals("mdat 必须逐字节保持原样", mdat, mdatOut)
    }

    @Test
    fun pruneNonAvTracks_shiftsChunkOffsetsWhenMoovPrecedesMdat() {
        val ftypBox = ftyp("qt  ", 0, "qt  ")
        val metaTrak = trak("meta", "mebx")
        // stco 条目先占位（0），随后按真实布局回填
        val videoTrak = box(
            "trak",
            box("mdia", hdlr("vide"), box("minf", box("stbl", stco(listOf(0L)))))
        )
        val moov = box("moov", videoTrak, metaTrak)
        val mdat = box("mdat", ByteArray(32) { 0x11 })
        val mov = ftypBox + moov + mdat

        // stco 布局：size(4) + "stco"(4) + version/flags(4) + entry_count(4) + entries
        // indexOf 给的是 fourcc 位置（= box 起点 + 4），故条目起点 = stcoAt + 12
        val mdatPayloadOffset = indexOf(mov, "mdat") + 4
        val stcoAt = indexOf(mov, "stco")
        BinaryUtils.writeU32BE(mov, stcoAt + 12, mdatPayloadOffset.toLong())

        assertEquals(
            "前置条件：moov 内应为 vide + meta 两条轨",
            listOf("vide", "meta"), Mp4Util.trackHandlers(mov)
        )
        val metaTrakAt = indexOf(mov, "meta")
        assertTrue(
            "前置条件：meta trak 必须落在 moov 末尾（mov.size=${mov.size}, metaTrak.size=${metaTrak.size}," +
                " meta trak 起点=$metaTrakAt, mdat 起点=${indexOf(mov, "mdat")}）",
            metaTrakAt < indexOf(mov, "mdat")
        )
        val pruned = Mp4Util.pruneNonAvTracks(mov)
        assertEquals(
            "前置条件：moov 位于 mdat 之前时也必须剔掉末尾 meta trak" +
                "（mov=${mov.size}, pruned=${pruned.size}, metaTrak=${metaTrak.size}）",
            mov.size - metaTrak.size, pruned.size
        )

        val out = Mp4Util.normalizeMovToMp4(mov)

        val newMdatPayloadOffset = indexOf(out, "mdat") + 4
        assertEquals("mdat 必须整体前移被删 trak 的字节数", mdatPayloadOffset - metaTrak.size, newMdatPayloadOffset)
        val stcoOut = indexOf(out, "stco")
        val entry = BinaryUtils.readU32BE(out, stcoOut + 12).toInt()
        assertEquals("stco 必须同步扣减，仍精确指向 mdat 载荷", newMdatPayloadOffset, entry)
    }

    @Test
    fun pruneNonAvTracks_keepsPureAvProductUnchanged() {
        val mp4 = ftyp("isom", 512, "isom", "iso2", "mp41") +
            box("moov", trak("vide", "avc1"), trak("soun", "mp4a")) +
            box("mdat", ByteArray(16))
        assertSame("只有音视频轨时必须零改动", mp4, Mp4Util.pruneNonAvTracks(mp4))
    }

    @Test
    fun pruneNonAvTracks_removesLeadingTrackAndFixesFollowingStco() {
        // 非末尾（此处为开头）的非音视频轨：删除区间**之后**还有视频轨，其 stco 条目随 box 前移。
        // 必须先按旧坐标扣减条目值、再删除字节；顺序反了就会把条目写到错位的字节上。
        val metaTrak = trak("meta", "mebx")
        val videoTrak = box(
            "trak",
            box("mdia", hdlr("vide"), box("minf", box("stbl", stco(listOf(0L)))))
        )
        val mp4 = ftyp("qt  ", 0, "qt  ") +
            box("moov", metaTrak, videoTrak) +
            box("mdat", ByteArray(32) { 0x33 })
        val mdatPayloadOffset = indexOf(mp4, "mdat") + 4
        BinaryUtils.writeU32BE(mp4, indexOf(mp4, "stco") + 12, mdatPayloadOffset.toLong())

        val out = Mp4Util.pruneNonAvTracks(mp4)

        assertEquals("只应剩视频轨", listOf("vide"), Mp4Util.trackHandlers(out))
        assertEquals("必须恰好缩小被删 trak 的字节数", mp4.size - metaTrak.size, out.size)
        assertTrue("meta 轨的 mebx 必须消失", !contains(out, "mebx"))
        val newMdatPayloadOffset = indexOf(out, "mdat") + 4
        assertEquals(
            "前置条件：mdat 载荷必须整体前移被删 trak 的字节数",
            mdatPayloadOffset - metaTrak.size, newMdatPayloadOffset
        )
        assertEquals(
            "stco 条目必须同步扣减且仍精确指向 mdat 载荷",
            newMdatPayloadOffset,
            BinaryUtils.readU32BE(out, indexOf(out, "stco") + 12).toInt()
        )
    }

    @Test
    fun pruneNonAvTracks_keepsFileWhenDropsAreNotContiguous() {
        // 两条非音视频轨之间夹着视频轨：单段删除无法表达，必须原样返回交给重封装回退
        val mp4 = ftyp("qt  ", 0, "qt  ") +
            box("moov", trak("meta", "mebx"), trak("vide", "avc1"), trak("tmcd", "tmcd")) +
            box("mdat", ByteArray(16))
        assertSame("被删 trak 不连续时不得做字节级减法", mp4, Mp4Util.pruneNonAvTracks(mp4))
    }

    @Test
    fun sanitize_handlesRealAppleLayoutWhereMoovFollowsMdat() {
        // 真机 Apple MOV（Apple 官方 Live Photo 样例 pairedVideo.mov）的实测形态：
        // ftyp(major="qt  ", minor=0, compatible=["qt  "]) + wide + mdat + moov（moov 在最后），
        // moov 内轨序 vide(avc1) + meta(mebx)。
        val mdat = box("mdat", ByteArray(96) { 0x3C })
        val mov = ftyp("qt  ", 0, "qt  ") + box("wide") + mdat +
            box("moov", trak("vide", "avc1"), trak("meta", "mebx"))
        assertEquals(
            "前置条件：真机形态 = vide + meta 两条轨",
            listOf("vide", "meta"), Mp4Util.trackHandlers(mov)
        )
        assertEquals("前置条件：moov 必须落在 mdat 之后", true, indexOf(mov, "moov") > indexOf(mov, "mdat"))

        val out = VideoTrackSanitizer.sanitize(mov) { _, _, _ -> }!!

        assertEquals("附加轨必须被剔除", listOf("vide"), Mp4Util.trackHandlers(out))
        assertTrue("不得残留 QuickTime 品牌（major 与 compatible 都要清）", !contains(out, "qt  "))
        assertTrue("不得残留 mebx 元数据轨", !contains(out, "mebx"))
        assertEquals(
            "moov 在 mdat 之后：删轨不得移动 mdat 载荷",
            indexOf(mov, "mdat"), indexOf(out, "mdat")
        )
        val at = indexOf(out, "mdat") - 4
        assertArrayEquals("mdat 必须逐字节保持原样", mdat, out.copyOfRange(at, at + mdat.size))
    }

    @Test
    fun pruneNonAvTracks_removesMetaTrackThatPrecedesUdta() {
        // 真机 Apple MOV 更常见的 moov 顺序：mvhd → trak(vide) → trak(meta) → udta。
        // 被删 trak 之后还有要保留的 udta（配对标识/拍摄参数），因此不能「删到 moov 末尾」。
        val metaTrak = trak("meta", "mebx")
        val udta = box("udta", box("meta", ByteArray(12) { 0x01 }))
        val mdat = box("mdat", ByteArray(48) { 0x77 })
        val mov = ftyp("qt  ", 0, "qt  ") + mdat +
            box("moov", trak("vide", "avc1"), metaTrak, udta)
        assertEquals(listOf("vide", "meta"), Mp4Util.trackHandlers(mov))

        val out = Mp4Util.normalizeMovToMp4(mov)

        assertEquals("只应剩视频轨", listOf("vide"), Mp4Util.trackHandlers(out))
        assertTrue("udta（配对标识所在）必须保留", contains(out, "udta"))
        assertTrue("meta 轨的 mebx 必须消失", !contains(out, "mebx"))
        assertTrue("qt   品牌必须清除", !contains(out, "qt  "))
        assertEquals("moov 必须恰好缩小被删 trak 的字节数", mov.size - metaTrak.size, out.size)
        val at = indexOf(out, "mdat") - 4
        assertArrayEquals("mdat 必须逐字节保持原样", mdat, out.copyOfRange(at, at + mdat.size))
        assertEquals("moov 在 mdat 之后：mdat 位置不得移动", indexOf(mov, "mdat"), indexOf(out, "mdat"))
    }

    @Test
    fun mp4ToMov_writesQuickTimeBrandsAndKeepsMinorVersion() {
        val mp4 = ftyp("isom", 512, "isom", "iso2", "avc1", "mp41") + box("mdat", ByteArray(8))
        val mov = Mp4Util.mp4ToMov(mp4)

        assertEquals("major_brand 必须是 qt  ", "qt  ", String(mov, 8, 4, Charsets.ISO_8859_1))
        assertEquals(
            "minor_version 必须保持 512（不能把版本字段写成 qt  ）",
            512L, BinaryUtils.readU32BE(mov, 12)
        )
        for (i in 16 until 32 step 4) {
            assertEquals(
                "compatible_brands[$i] 必须是 qt  （只改 major 会被当作普通 MP4）",
                "qt  ", String(mov, i, 4, Charsets.ISO_8859_1)
            )
        }
        assertEquals("等长改写：不得改变文件大小", mp4.size, mov.size)
    }

    // ---------------------------------------------------------------- 3. 净化入口

    @Test
    fun sanitize_usesPureBytePathAndNeverNeedsMediaMuxer() {
        val mov = ftyp("qt  ", 0, "qt  ") +
            box("moov", trak("vide", "avc1"), trak("meta", "mebx")) +
            box("mdat", ByteArray(48) { 0x7F })

        val out = VideoTrackSanitizer.sanitize(mov) { _, _, _ -> }

        assertNotNull("必须返回净化结果（纯字节路径，不得依赖 JVM 不可用的 MediaMuxer）", out)
        assertEquals(listOf("vide"), Mp4Util.trackHandlers(out!!))
        assertTrue("不得残留 qt   品牌", !contains(out, "qt  "))
        assertTrue("不得残留 mebx 轨", !contains(out, "mebx"))
    }

    @Test
    fun sanitize_returnsNullWhenNotIsobmff() {
        assertNull("非 ISOBMFF 输入不得净化", VideoTrackSanitizer.sanitize(ByteArray(32) { 1 }))
    }

    // ---------------------------------------------------------------- 4. 非 JPEG 封面

    @Test
    fun replaceOrInsertXmp_returnsNonJpegUnchanged() {
        val heic = ByteArray(24) { 0x22 }
        val out = JpegUtil.replaceOrInsertXmp(heic, "<x:xmpmeta/>")
        assertArrayEquals("HEIC 封面不得被解析，也不得抛异常中断转换", heic, out)
    }

    // ---------------------------------------------------------------- 5. HEIC 粗筛

    @Test
    fun sniff_acceptsHeicWithCompanionMov() {
        val dir = Files.createTempDirectory("apple_heic").toFile()
        try {
            // HEIC：ftyp major_brand = heic
            val heic = File(dir, "IMG_0001.heic")
            heic.writeBytes(ftyp("heic", 0, "mif1", "heic"))
            assertTrue("HEIC 主图：无 MOV 时不得判为动态照片", !QuickClassify.sniff(heic.path))
            File(dir, "IMG_0001.MOV").writeBytes(ftyp("qt  ", 0, "qt  ") + ByteArray(16))
            assertTrue("HEIC + 同名 .MOV 必须判为动态照片（此前被直接跳过）", QuickClassify.sniff(heic.path))
        } finally {
            dir.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------- 6. 真实 Apple 样本

    /**
     * 用**真实** Apple 配对视频（Apple 官方 Sample Code 的 `pairedVideo.mov`）跑一遍完整归一。
     *
     * 该文件的实测形态：`ftyp(major='qt  ', minor=0, compat=['qt  ']) + wide + mdat +
     * moov(mvhd, trak(vide: avc1 720x960 + colr), trak(meta: mebx), udta[com.apple.quicktime.*])`
     * —— 正是「相册能识别、长按不播放」这类产物的源头形态。
     *
     * 样本不在仓库里，故用 `Assume` 在缺失时跳过（CI 不依赖它）；本机跑时是真实字节的回归。
     */
    @Test
    fun normalizeMovToMp4_onRealApplePairedVideo() {
        val path = System.getenv("ZLC_APPLE_MOV")
            ?: "D:\\Code\\Program\\ZLC\\.agents\\tmp\\apple-real\\pairedVideo.mov"
        val file = File(path)
        org.junit.Assume.assumeTrue("缺少真实 Apple 样本（$path），跳过", file.isFile)

        val data = file.readBytes()
        assertEquals(
            "前置条件：真实 Apple 配对视频应是 vide + meta 两条轨",
            listOf("vide", "meta"), Mp4Util.trackHandlers(data)
        )
        val mdatAt = indexOf(data, "mdat")
        val mdatSize = BinaryUtils.readU32BE(data, mdatAt - 4).toInt()
        val mdat = data.copyOfRange(mdatAt - 4, mdatAt - 4 + mdatSize)

        val out = Mp4Util.normalizeMovToMp4(data)

        assertEquals("meta(mebx) 轨必须被剔除，视频轨必须保留", listOf("vide"), Mp4Util.trackHandlers(out))
        assertTrue("视频 sample entry 必须仍是 avc1", contains(out, "avc1"))
        assertTrue("不得残留 mebx 轨数据", !contains(out, "mebx"))
        assertEquals("major_brand 必须改为 isom", "isom", String(out, 8, 4, Charsets.ISO_8859_1))
        assertTrue(
            "ftyp 内不得残留 qt   品牌（compat 里也写过 qt  ）",
            !contains(out.copyOfRange(0, 32), "qt  ")
        )
        assertTrue("文件必须缩小（剔掉 meta trak）", out.size < data.size)
        val newMdatAt = indexOf(out, "mdat")
        assertEquals(
            "moov 在 mdat 之后：mdat 位置不得移动",
            mdatAt - 4, newMdatAt - 4
        )
        assertArrayEquals(
            "mdat 必须逐字节不变（零拷贝、样本数据未改动）",
            mdat, out.copyOfRange(newMdatAt - 4, newMdatAt - 4 + mdatSize)
        )
        val compat = Mp4Util.videoCompat(out)
        assertEquals("真实样本的视频编码应是 avc1", "avc1", compat.videoCodec)
    }
}
