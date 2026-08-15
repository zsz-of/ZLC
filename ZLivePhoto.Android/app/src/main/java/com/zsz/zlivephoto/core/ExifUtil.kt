package com.zsz.zlivephoto.core

/**
 * EXIF APP1 段的字节级读写：在 JPEG 的 IFD0 中添加/检测标签。
 * 纯字节级操作，不影响图像数据。用于小米 0x8897 标签。
 */
internal class ExifException(message: String) : Exception(message)

internal object ExifUtil {
    val exifPrefix: ByteArray = JpegUtil.exifPrefix // "Exif\0\0"

    // TIFF 数据类型大小（字节）
    private val typeSizes: Map<Int, Int> = mapOf(
        1 to 1, 2 to 1, 3 to 2, 4 to 4, 5 to 8,
        6 to 1, 7 to 1, 8 to 2, 9 to 4, 10 to 8,
        11 to 4, 12 to 8
    )

    private class IfdEntry(val tid: Int, val ttype: Int, val tcount: Long, val tval: ByteArray)

    private data class ExifLocation(val segStart: Int, val totalLen: Int, val tiffStart: Int)

    private fun pack16(le: Boolean, v: Int): ByteArray {
        val b = ByteArray(2)
        if (le) BinaryUtils.writeU16LE(b, 0, v) else BinaryUtils.writeU16BE(b, 0, v)
        return b
    }

    private fun pack32(le: Boolean, v: Long): ByteArray {
        val b = ByteArray(4)
        if (le) BinaryUtils.writeU32LE(b, 0, v) else BinaryUtils.writeU32BE(b, 0, v)
        return b
    }

    private fun read16(d: ByteArray, off: Int, le: Boolean): Int =
        if (le) BinaryUtils.readU16LE(d, off) else BinaryUtils.readU16BE(d, off)

    private fun read32(d: ByteArray, off: Int, le: Boolean): Long =
        if (le) BinaryUtils.readU32LE(d, off) else BinaryUtils.readU32BE(d, off)

    /** 定位 EXIF APP1 段，返回 (segStart, totalLen, tiffStart)；无则 null。 */
    private fun findExifApp1(jpeg: ByteArray): ExifLocation? {
        for (seg in JpegUtil.iterateSegments(jpeg)) {
            val m = seg.marker.toInt() and 0xFF
            if (m == 0xE1 && BinaryUtils.arrayEquals(jpeg, seg.payloadStart, exifPrefix)) {
                return ExifLocation(seg.segStart, seg.totalLen, seg.payloadStart + 6)
            }
        }
        return null
    }

    /** 返回 TIFF 字节序：true=小端(II)，false=大端(MM)。 */
    private fun isLittleEndian(jpeg: ByteArray, tiffStart: Int): Boolean {
        if (jpeg[tiffStart] == 'I'.code.toByte() && jpeg[tiffStart + 1] == 'I'.code.toByte()) return true
        if (jpeg[tiffStart] == 'M'.code.toByte() && jpeg[tiffStart + 1] == 'M'.code.toByte()) return false
        throw ExifException("无效的 TIFF 字节序标记")
    }

    /** 检测 JPEG 的 IFD0 中是否存在指定 EXIF 标签。 */
    fun hasExifTag(jpeg: ByteArray, tagId: Int): Boolean {
        val found = findExifApp1(jpeg) ?: return false
        val (_, _, tiffStart) = found
        return try {
            val le = isLittleEndian(jpeg, tiffStart)
            val ifd0Abs = tiffStart + read32(jpeg, tiffStart + 4, le).toInt()
            val count = read16(jpeg, ifd0Abs, le)
            for (i in 0 until count) {
                val entryOff = ifd0Abs + 2 + i * 12
                if (read16(jpeg, entryOff, le) == tagId) return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /** 读取 IFD0 中指定标签的值（inline 值或偏移引用）。 */
    fun readExifTagValue(jpeg: ByteArray, tagId: Int): Any? {
        val found = findExifApp1(jpeg) ?: return null
        val (_, _, tiffStart) = found
        return try {
            val le = isLittleEndian(jpeg, tiffStart)
            val ifd0Abs = tiffStart + read32(jpeg, tiffStart + 4, le).toInt()
            val count = read16(jpeg, ifd0Abs, le)
            for (i in 0 until count) {
                val entryOff = ifd0Abs + 2 + i * 12
                if (read16(jpeg, entryOff, le) != tagId) continue

                val ttype = read16(jpeg, entryOff + 2, le)
                val tcount = read32(jpeg, entryOff + 4, le)
                val typeSize = typeSizes[ttype] ?: 1
                val dataSize = typeSize * tcount.toInt()

                if (dataSize <= 4) {
                    val valBytes = jpeg.copyOfRange(entryOff + 8, entryOff + 8 + dataSize)
                    return when (ttype) {
                        1 -> valBytes[0]
                        3 -> if (le) BinaryUtils.readU16LE(valBytes, 0) else BinaryUtils.readU16BE(valBytes, 0)
                        4 -> if (le) BinaryUtils.readU32LE(valBytes, 0) else BinaryUtils.readU32BE(valBytes, 0)
                        else -> valBytes
                    }
                }

                val dataAbs = tiffStart + read32(jpeg, entryOff + 8, le).toInt()
                return jpeg.copyOfRange(dataAbs, dataAbs + dataSize)
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 在 JPEG 的 EXIF IFD0 中添加一个标签（仅支持 inline 值：BYTE/SHORT/LONG）。
     * 若 JPEG 无 EXIF APP1 段，创建最小段。若已有该标签，替换之。
     */
    fun addIfd0Tag(jpeg: ByteArray, tagId: Int, tagType: Int, value: Int): ByteArray {
        if (!typeSizes.containsKey(tagType)) {
            throw ExifException("不支持的 TIFF 类型 $tagType")
        }

        // 编码值到 4 字节 inline（小端布局，与 Python 原版一致）
        val valInline: ByteArray = when (tagType) {
            1 -> byteArrayOf(value.toByte(), 0, 0, 0)
            3 -> byteArrayOf(
                (value and 0xFF).toByte(),
                ((value ushr 8) and 0xFF).toByte(),
                0, 0
            )
            4 -> byteArrayOf(
                (value and 0xFF).toByte(),
                ((value ushr 8) and 0xFF).toByte(),
                ((value ushr 16) and 0xFF).toByte(),
                ((value ushr 24) and 0xFF).toByte()
            )
            else -> throw ExifException("不支持的 inline 类型 $tagType")
        }
        val count = 1L

        val found = findExifApp1(jpeg)
        if (found == null) {
            // 创建最小 EXIF APP1 段（小端 II）
            val ifd0 = mutableListOf<Byte>()
            ifd0.addAll(pack16(true, 1).toList())
            ifd0.addAll(pack16(true, tagId).toList())
            ifd0.addAll(pack16(true, tagType).toList())
            ifd0.addAll(pack32(true, count).toList())
            ifd0.addAll(valInline.toList())
            ifd0.addAll(pack32(true, 0L).toList()) // next IFD offset

            val tiff = mutableListOf<Byte>()
            tiff.addAll(byteArrayOf('I'.code.toByte(), 'I'.code.toByte()).toList()) // "II"
            tiff.addAll(pack16(true, 42).toList())
            tiff.addAll(pack32(true, 8L).toList())
            tiff.addAll(ifd0)

            val tiffArr = tiff.toByteArray()
            val payload = ByteArray(exifPrefix.size + tiffArr.size)
            System.arraycopy(exifPrefix, 0, payload, 0, exifPrefix.size)
            System.arraycopy(tiffArr, 0, payload, exifPrefix.size, tiffArr.size)

            val app1 = ByteArray(4 + payload.size)
            app1[0] = 0xFF.toByte()
            app1[1] = 0xE1.toByte()
            BinaryUtils.writeU16BE(app1, 2, payload.size + 2)
            System.arraycopy(payload, 0, app1, 4, payload.size)

            val combined = ByteArray(jpeg.size + app1.size)
            System.arraycopy(jpeg, 0, combined, 0, 2)
            System.arraycopy(app1, 0, combined, 2, app1.size)
            System.arraycopy(jpeg, 2, combined, 2 + app1.size, jpeg.size - 2)
            return combined
        }

        val (segStart, totalLen, tiffStart) = found
        val le = isLittleEndian(jpeg, tiffStart)

        val ifd0Off = read32(jpeg, tiffStart + 4, le)
        val ifd0Abs = tiffStart + ifd0Off.toInt()
        val oldCount = read16(jpeg, ifd0Abs, le)

        // 读取现有 entry（排除同 tag，实现替换语义）
        val entries = mutableListOf<IfdEntry>()
        for (i in 0 until oldCount) {
            val entryOff = ifd0Abs + 2 + i * 12
            val tid = read16(jpeg, entryOff, le)
            if (tid == tagId) continue
            val ttype = read16(jpeg, entryOff + 2, le)
            val tcount = read32(jpeg, entryOff + 4, le)
            entries.add(IfdEntry(tid, ttype, tcount, jpeg.copyOfRange(entryOff + 8, entryOff + 12)))
        }

        // 新增/替换一个 entry → 净增 12 字节
        val delta = 12L
        val dataAreaStartRel = ifd0Off + 2L + oldCount.toLong() * 12L + 4L

        // 修复旧 entry 中的数据区偏移引用
        val fixedEntries = mutableListOf<IfdEntry>()
        for (e in entries) {
            var tval = e.tval
            val ts = typeSizes[e.ttype] ?: 1
            if (ts * e.tcount.toInt() > 4) {
                val oldOff = if (le) BinaryUtils.readU32LE(tval, 0) else BinaryUtils.readU32BE(tval, 0)
                if (oldOff >= dataAreaStartRel) {
                    tval = pack32(le, oldOff + delta)
                }
            }
            fixedEntries.add(IfdEntry(e.tid, e.ttype, e.tcount, tval))
        }

        // 合并新 entry 并按 tag ID 排序
        val allEntries = (fixedEntries + IfdEntry(tagId, tagType, count, valInline))
            .sortedBy { it.tid }

        val newIfd0 = mutableListOf<Byte>()
        newIfd0.addAll(pack16(le, allEntries.size).toList())
        for (e in allEntries) {
            newIfd0.addAll(pack16(le, e.tid).toList())
            newIfd0.addAll(pack16(le, e.ttype).toList())
            newIfd0.addAll(pack32(le, e.tcount).toList())
            newIfd0.addAll(e.tval.toList())
        }

        // next IFD offset
        val oldNextPos = ifd0Abs + 2 + oldCount * 12
        var oldNext = read32(jpeg, oldNextPos, le)
        if (oldNext != 0L) {
            oldNext += delta
        }
        newIfd0.addAll(pack32(le, oldNext).toList())

        // IFD0 数据区（原样保留）
        val app1End = segStart + totalLen
        val oldData = jpeg.copyOfRange(oldNextPos + 4, app1End)

        // 重建 APP1 段
        val newIfd0Arr = newIfd0.toByteArray()
        val newTiff = ByteArray(ifd0Off.toInt() + newIfd0Arr.size + oldData.size)
        System.arraycopy(jpeg, tiffStart, newTiff, 0, ifd0Off.toInt())
        System.arraycopy(newIfd0Arr, 0, newTiff, ifd0Off.toInt(), newIfd0Arr.size)
        System.arraycopy(oldData, 0, newTiff, ifd0Off.toInt() + newIfd0Arr.size, oldData.size)

        val newPayload = ByteArray(exifPrefix.size + newTiff.size)
        System.arraycopy(exifPrefix, 0, newPayload, 0, exifPrefix.size)
        System.arraycopy(newTiff, 0, newPayload, exifPrefix.size, newTiff.size)

        val newApp1 = ByteArray(4 + newPayload.size)
        newApp1[0] = 0xFF.toByte()
        newApp1[1] = 0xE1.toByte()
        BinaryUtils.writeU16BE(newApp1, 2, newPayload.size + 2)
        System.arraycopy(newPayload, 0, newApp1, 4, newPayload.size)

        val result = ByteArray(jpeg.size - totalLen + newApp1.size)
        System.arraycopy(jpeg, 0, result, 0, segStart)
        System.arraycopy(newApp1, 0, result, segStart, newApp1.size)
        System.arraycopy(jpeg, app1End, result, segStart + newApp1.size, jpeg.size - app1End)
        return result
    }
}
