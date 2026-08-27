package com.zsz.zlivephoto.core

import java.io.File
import java.io.RandomAccessFile

/**
 * 动态照片快速粗筛（选择器扫描与批量处理共用）。
 * 与 Windows 版 GooglePlugin.SniffXmp 对齐：不依赖固定头部窗口，
 * 而是按 JPEG 段结构（marker + length）遍历，定位任意位置的 XMP 标记。
 * 这样即使 EXIF 段很大（>64KB）把 XMP 推到更深的位置也不会漏检。
 *
 * 判定：
 * - JPEG 段内出现 MotionPhoto/MicroVideo 标记 → Google/OPPO/小米/vivo 单文件
 * - 尾部含 LIVE_ → 荣耀
 * - 同目录存在同名 .mp4/.mov → vivo/Apple 双文件
 */
internal object QuickClassify {

    private val MOTION_TAG = "MotionPhoto".toByteArray(Charsets.US_ASCII)
    private val MICRO_TAG = "MicroVideo".toByteArray(Charsets.US_ASCII)
    private val LIVE_TAG = "LIVE_".toByteArray(Charsets.US_ASCII)

    /**
     * @return true = 是动态照片；false = JPEG 但未见标记（仍可能为双文件）；null = 非 JPEG 或读取失败。
     */
    private fun checkJpeg(path: String): Boolean? {
        return try {
            RandomAccessFile(path, "r").use { raf ->
                val len = raf.length()
                if (len < 4) return null
                val soi = ByteArray(2)
                raf.readFully(soi)
                if (soi[0] != 0xFF.toByte() || soi[1] != 0xD8.toByte()) return null
                // 遍历 JPEG 段查找动态照片标记（突破固定 64KB 窗口）
                if (scanSegmentsForMotion(raf, len)) return true
                // 尾部 LIVE_（荣耀 60B 固定尾部）
                if (len >= 64) {
                    raf.seek(len - 64)
                    val tail = ByteArray(64)
                    raf.readFully(tail)
                    if (BinaryUtils.indexOf(tail, LIVE_TAG) >= 0) return true
                }
                false
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 从 SOI 之后（offset 2）按段遍历，遇到 SOS/EOI 停止；在各段载荷中搜索标记。 */
    private fun scanSegmentsForMotion(raf: RandomAccessFile, fileLen: Long): Boolean {
        var pos = 2L
        val markerBuf = ByteArray(2)
        val lenBuf = ByteArray(2)
        var guard = 0
        while (pos + 4 <= fileLen && guard < 128) {
            guard++
            raf.seek(pos)
            raf.readFully(markerBuf)
            if (markerBuf[0] != 0xFF.toByte()) break
            val marker = markerBuf[1].toInt() and 0xFF
            when {
                marker == 0xFF -> { pos += 1; continue }              // 填充字节
                marker == 0x00 -> break                               // 非法/字节填充
                marker == 0xDA || marker == 0xD9 -> break             // SOS / EOI：头部段结束
                marker == 0x01 || marker in 0xD0..0xD7 -> { pos += 2; continue } // 无载荷段
            }
            raf.readFully(lenBuf)
            val segLen = BinaryUtils.readU16BE(lenBuf, 0)
            if (segLen < 2) break
            val payloadLen = segLen - 2
            if (payloadLen > 0) {
                val payload = ByteArray(payloadLen)
                raf.readFully(payload)
                if (BinaryUtils.indexOf(payload, MOTION_TAG) >= 0) return true
                if (BinaryUtils.indexOf(payload, MICRO_TAG) >= 0) return true
            }
            pos += 2 + segLen
        }
        return false
    }

    fun sniff(path: String): Boolean {
        return try {
            when (checkJpeg(path)) {
                true -> return true    // JPEG 内直接命中标记
                null -> return false   // 非 JPEG / 读取失败
                false -> { /* JPEG 未见标记：继续检查伴生视频 */ }
            }
            // 同目录伴生视频（vivo/Apple 双文件），过滤空占位文件
            val stem = path.substringBeforeLast('.')
            File("$stem.mp4").let { it.exists() && it.length() > 8L } ||
                File("$stem.mov").let { it.exists() && it.length() > 8L } ||
                File("$stem.MP4").let { it.exists() && it.length() > 8L } ||
                File("$stem.MOV").let { it.exists() && it.length() > 8L }
        } catch (_: Exception) {
            false
        }
    }
}
