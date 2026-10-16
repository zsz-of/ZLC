package com.zsz.zlivephoto.core.formats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 三星 SEF（Samsung Extended Format）尾部容器的字节级回归。
 *
 * 期望值全部来自三星相册 `com.sec.android.gallery3d` 15.7.00.43 反编译得到的
 * `TailData.pack` / `Sdr.pack` / `Drdh` / `SefData` 语义，见 `SamsungSef` 的 KDoc：
 * 全部字段小端；记录表项 = value(4) + drsp(4) + drsl(4)；`value = (type shl 16) | …`；
 * `drsp` = 载荷起点到尾部块起点的距离。
 */
class SamsungSefTest {

    private fun le32(v: Int): ByteArray {
        val b = ByteArray(4)
        b[0] = (v and 0xFF).toByte()
        b[1] = ((v ushr 8) and 0xFF).toByte()
        b[2] = ((v ushr 16) and 0xFF).toByte()
        b[3] = ((v ushr 24) and 0xFF).toByte()
        return b
    }

    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

    @Test
    fun recordValueEncodesTypeInHighHalf() {
        val r = SamsungSef.Record(SamsungSef.TYPE_MOTION_PHOTO_DATA, 0, "x", ByteArray(0))
        assertEquals(0x0A300000, r.value)
        val withOption = SamsungSef.Record(SamsungSef.TYPE_MOTION_PHOTO_DATA, 1, "x", ByteArray(0))
        assertEquals(0x0A308000, withOption.value)
        // option=1 时载荷头多一个 dsiLen 字段
        assertEquals(12, withOption.headerLen)
        assertEquals(8, r.headerLen)
    }

    @Test
    fun singleRecordLayoutMatchesReversedFormat() {
        val rec = SamsungSef.Record(
            SamsungSef.TYPE_MOTION_PHOTO_DATA, 0,
            SamsungSef.KEY_MOTION_PHOTO_DATA, ascii("ABCD")
        )
        // 载荷头 8 + 名 16 + 数据 4 = 28
        assertEquals(28, rec.size)

        val seg = SamsungSef.buildSegment(SamsungSef.DEFAULT_VERSION, listOf(rec))
        // 载荷 28 + 尾部块 (20 + 12) = 60
        assertEquals(60, seg.size)

        // ---- 载荷 ----
        assertEquals(0x0A300000.toLong(), readLE(seg, 0))
        assertEquals(16L, readLE(seg, 4))
        assertEquals("MotionPhoto_Data", String(seg, 8, 16, Charsets.UTF_8))
        assertEquals("ABCD", String(seg, 24, 4, Charsets.UTF_8))

        // ---- 尾部块（从 28 开始）----
        val t = 28
        assertEquals("SEFH", String(seg, t, 4, Charsets.US_ASCII))
        assertEquals(106L, readLE(seg, t + 4))          // version 缺省 0x6A
        assertEquals(1L, readLE(seg, t + 8))            // count
        assertEquals(0x0A300000L, readLE(seg, t + 12))  // value
        assertEquals(28L, readLE(seg, t + 16))          // drsp = 距块起点 28
        assertEquals(28L, readLE(seg, t + 20))          // drsl
        assertEquals(24L, readLE(seg, t + 24))          // 块内总长 = 12 + 12*1
        assertEquals("SEFT", String(seg, t + 28, 4, Charsets.US_ASCII))
    }

    @Test
    fun parseRoundTripsRecord() {
        val video = ByteArray(64) { it.toByte() }
        val rec = SamsungSef.Record(
            SamsungSef.TYPE_MOTION_PHOTO_DATA, 0,
            SamsungSef.KEY_MOTION_PHOTO_DATA, video
        )
        val seg = SamsungSef.buildSegment(SamsungSef.DEFAULT_VERSION, listOf(rec))
        val file = ascii("\u0000jpeg-body").copyOf(16) + seg

        val parsed = SamsungSef.parse(file)
        assertNotNull(parsed)
        assertEquals(106, parsed!!.version)
        assertEquals(1, parsed.records.size)
        // 文件尾到块起点的距离 = 尾部块自身大小（12 字节头 + 1×12 记录 + 4 总长 + 4 SEFT）
        assertEquals(
            (SamsungSef.BLOCK_OVERHEAD + SamsungSef.RECORD_SIZE).toLong(),
            (file.size - parsed.blockStart).toLong()
        )
        val got = parsed.firstOf(SamsungSef.TYPE_MOTION_PHOTO_DATA)
        assertNotNull(got)
        assertEquals(SamsungSef.KEY_MOTION_PHOTO_DATA, got!!.name)
        assertEquals(0, got.option)
        assertTrue(got.payload.contentEquals(video))
    }

    @Test
    fun multiRecordDrspCountsFromBlockStart() {
        val a = SamsungSef.Record(SamsungSef.TYPE_MOTION_PHOTO_DATA, 0, "A", ByteArray(10))
        val b = SamsungSef.Record(SamsungSef.TYPE_IMAGE_UTC_DATA, 0, "B", ByteArray(6))
        val seg = SamsungSef.buildSegment(SamsungSef.DEFAULT_VERSION, listOf(a, b))
        val blockStart = a.size + b.size
        assertEquals(blockStart, seg.size - SamsungSef.BLOCK_OVERHEAD - 2 * SamsungSef.RECORD_SIZE)

        // drsp[0] = a.size + b.size（a 的载荷最靠前）；drsp[1] = b.size
        assertEquals((a.size + b.size).toLong(), readLE(seg, blockStart + 12 + 4))
        assertEquals(b.size.toLong(), readLE(seg, blockStart + 12 + SamsungSef.RECORD_SIZE + 4))

        val parsed = SamsungSef.parse(seg)
        assertNotNull(parsed)
        assertEquals(2, parsed!!.records.size)
        assertTrue(parsed.records[0].payload.contentEquals(ByteArray(10)))
        assertTrue(parsed.records[1].payload.contentEquals(ByteArray(6)))
    }

    @Test
    fun nonSefDataIsRejected() {
        assertNull(SamsungSef.parse(ByteArray(64)))
        assertNull(SamsungSef.parse(ascii("SEFH") + ByteArray(40)))
    }

    @Test
    fun pluginDetectUsesTailMarkersOnly() {
        val plugin = SamsungPlugin()
        assertEquals("samsung", plugin.name)

        val dir = File(System.getProperty("java.io.tmpdir"), "zlc-samsung-test").apply { mkdirs() }
        val video = ByteArray(32) { (it * 3).toByte() }
        val rec = SamsungSef.Record(
            SamsungSef.TYPE_MOTION_PHOTO_DATA, 0,
            SamsungSef.KEY_MOTION_PHOTO_DATA, video
        )
        val withTail = File(dir, "has_sef.jpg")
        // 前置一段伪 JPEG 头，仅用于让 detect 的文件形态成立
        withTail.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(30) +
            SamsungSef.buildSegment(SamsungSef.DEFAULT_VERSION, listOf(rec)))
        assertEquals(95, plugin.detect(withTail.path))

        val withoutTail = File(dir, "no_sef.jpg")
        withoutTail.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(60))
        assertEquals(0, plugin.detect(withoutTail.path))

        // HEIC 变体（SEF 里带 mpv2 的 HEIF item）暂不支持：扩展名即返回 0
        val heic = File(dir, "motion.heic")
        heic.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(30) +
            SamsungSef.buildSegment(SamsungSef.DEFAULT_VERSION, listOf(rec)))
        assertEquals(0, plugin.detect(heic.path))

        dir.deleteRecursively()
    }

    private fun readLE(b: ByteArray, off: Int): Long {
        return (b[off].toLong() and 0xFF) or
            ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or
            ((b[off + 3].toLong() and 0xFF) shl 24)
    }
}
