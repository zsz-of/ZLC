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

    /** 检测 JPEG 的 IFD0 / ExifIFD 中是否存在指定 EXIF 标签。
     *  小米相机把 0x8897 写在 ExifIFD（0x8769 子 IFD）而非 IFD0，两处都要扫。 */
    fun hasExifTag(jpeg: ByteArray, tagId: Int): Boolean {
        val found = findExifApp1(jpeg) ?: return false
        val (_, _, tiffStart) = found
        return try {
            val le = isLittleEndian(jpeg, tiffStart)
            val ifd0Abs = tiffStart + read32(jpeg, tiffStart + 4, le).toInt()
            val count = read16(jpeg, ifd0Abs, le)
            var exifIfdAbs = -1
            for (i in 0 until count) {
                val entryOff = ifd0Abs + 2 + i * 12
                val tag = read16(jpeg, entryOff, le)
                if (tag == tagId) return true
                if (tag == 0x8769) {
                    exifIfdAbs = tiffStart + read32(jpeg, entryOff + 8, le).toInt()
                }
            }
            if (exifIfdAbs > tiffStart) {
                val exifCount = read16(jpeg, exifIfdAbs, le)
                for (i in 0 until exifCount) {
                    if (read16(jpeg, exifIfdAbs + 2 + i * 12, le) == tagId) return true
                }
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
     * 在 EXIF 的 ExifIFD 中添加一个标签（仅支持 inline 值：BYTE/SHORT/LONG）。
     * 与小米相机行为一致（0x8897 写在 ExifIFD 而非 IFD0）。
     *
     * 采用「追加 + 指针改写」策略，绝不移动既有数据（零损坏风险）：
     * - 已有 ExifIFD：新 ExifIFD（旧 entry 逐字节复制 + 新 entry）追加到段尾，
     *   仅改写 IFD0 中 0x8769 指针的 inline 值；已有同 tag 则原位改写其值。
     * - 无 ExifIFD：新 IFD0（旧 entry 逐字节复制 + 0x8769 指针）与新 ExifIFD 追加到段尾，
     *   仅改写 TIFF 头的 IFD0 偏移。
     * - 无 EXIF 段：创建最小 APP1 插到 SOI 后。
     * 旧数据（GPS/ExifIFD/MakerNote/缩略图）全部保持原偏移。
     * 段长超 64KB 或解析失败时返回原 jpeg（识别仍可靠 XMP 双标签兜底）。
     */
    fun addExifIfdTag(jpeg: ByteArray, tagId: Int, tagType: Int, value: Int): ByteArray {
        if (!typeSizes.containsKey(tagType)) {
            throw ExifException("不支持的 TIFF 类型 $tagType")
        }

        val found = findExifApp1(jpeg)
        if (found == null) {
            // 创建最小 EXIF：II + IFD0{0x8769→ExifIFD} + ExifIFD{tag}
            val le = true
            val ifd0Size = 2 + 12 + 4 // count + 1 entry + next
            val exifIfdOff = 8 + ifd0Size
            val ifd0 = pack16(le, 1) + pack16(le, 0x8769) + pack16(le, 4) +
                pack32(le, 1) + pack32(le, exifIfdOff.toLong()) + pack32(le, 0)
            val newEntry = pack16(le, tagId) + pack16(le, tagType) +
                pack32(le, 1) + encodeInline(le, tagType, value)
            val exifIfd = pack16(le, 1) + newEntry + pack32(le, 0)

            val tiff = byteArrayOf('I'.code.toByte(), 'I'.code.toByte()) +
                pack16(le, 42) + pack32(le, 8) + ifd0 + exifIfd
            val payload = exifPrefix + tiff

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
        try {
            val le = isLittleEndian(jpeg, tiffStart)
            val ifd0Off = read32(jpeg, tiffStart + 4, le).toInt()
            val ifd0Abs = tiffStart + ifd0Off
            val ifd0Count = read16(jpeg, ifd0Abs, le)

            // 段尾追加位置（TIFF 相对偏移）：段绝对终点 - TIFF 起点
            val appendRel = (segStart + totalLen) - tiffStart

            // 找 IFD0 中的 0x8769（ExifIFD 指针）
            var exifPtrEntryOff = -1
            for (i in 0 until ifd0Count) {
                val entryOff = ifd0Abs + 2 + i * 12
                if (read16(jpeg, entryOff, le) == 0x8769) {
                    exifPtrEntryOff = entryOff
                    break
                }
            }

            val newEntry = pack16(le, tagId) + pack16(le, tagType) +
                pack32(le, 1) + encodeInline(le, tagType, value)
            var appended: ByteArray

            if (exifPtrEntryOff >= 0) {
                val exifIfdAbs = tiffStart + read32(jpeg, exifPtrEntryOff + 8, le).toInt()
                val exifCount = read16(jpeg, exifIfdAbs, le)
                // 已有同 tag → 原位改写值（零增长）
                for (i in 0 until exifCount) {
                    val e = exifIfdAbs + 2 + i * 12
                    if (read16(jpeg, e, le) == tagId) {
                        val result = jpeg.copyOf()
                        val inline = encodeInline(le, tagType, value)
                        System.arraycopy(inline, 0, result, e + 8, 4)
                        return result
                    }
                }
                // 新 ExifIFD = 旧 entries 逐字节复制 + 新 entry + next 指针归零
                // （旧 next 指针不可带入 entries 区，否则新 entry 错位 4 字节）
                val oldBytes = jpeg.copyOfRange(
                    exifIfdAbs + 2, exifIfdAbs + 2 + exifCount * 12)
                appended = pack16(le, exifCount + 1) + oldBytes + newEntry + pack32(le, 0)
                if (totalLen + appended.size > 65535) return jpeg // APP1 段长上限

                val result = insertBytes(jpeg, segStart + totalLen, appended)
                // 改写 0x8769 指针 → 新 ExifIFD 偏移（原位，4 字节）
                val ptr = pack32(le, appendRel.toLong())
                System.arraycopy(ptr, 0, result, exifPtrEntryOff + 8, 4)
                updateSegLen(result, segStart, totalLen + appended.size)
                return result
            }

            // IFD0 无 ExifIFD：新 IFD0（旧 entries + 0x8769 + next 归零）+ 新 ExifIFD
            val oldIfd0Bytes = jpeg.copyOfRange(
                ifd0Abs + 2, ifd0Abs + 2 + ifd0Count * 12)
            val newIfd0Size = 2 + (ifd0Count + 1) * 12 + 4
            val exifIfdOff = appendRel + newIfd0Size
            val ptrEntry = pack16(le, 0x8769) + pack16(le, 4) +
                pack32(le, 1) + pack32(le, exifIfdOff.toLong())
            val newIfd0 = pack16(le, ifd0Count + 1) + oldIfd0Bytes + ptrEntry + pack32(le, 0)
            val newExifIfd = pack16(le, 1) + newEntry + pack32(le, 0)
            appended = newIfd0 + newExifIfd
            if (totalLen + appended.size > 65535) return jpeg // APP1 段长上限

            val result = insertBytes(jpeg, segStart + totalLen, appended)
            // 改写 TIFF 头 IFD0 偏移（原位，4 字节）
            val hdr = pack32(le, appendRel.toLong())
            System.arraycopy(hdr, 0, result, tiffStart + 4, 4)
            updateSegLen(result, segStart, totalLen + appended.size)
            return result
        } catch (e: Exception) {
            // 解析失败：返回原 jpeg，识别兜底靠 XMP 双标签
            return jpeg
        }
    }

    /** 按字节序编码 4 字节 inline 值（BYTE/SHORT/LONG）。 */
    private fun encodeInline(le: Boolean, tagType: Int, value: Int): ByteArray {
        val b = ByteArray(4)
        when (tagType) {
            1 -> if (le) b[0] = value.toByte() else b[3] = value.toByte()
            3 -> if (le) BinaryUtils.writeU16LE(b, 0, value) else BinaryUtils.writeU16BE(b, 0, value)
            4 -> if (le) BinaryUtils.writeU32LE(b, 0, value.toLong()) else BinaryUtils.writeU32BE(b, 0, value.toLong())
            else -> throw ExifException("不支持的 inline 类型 $tagType")
        }
        return b
    }

    /** 在 pos 处插入 bytes，返回新数组。 */
    private fun insertBytes(src: ByteArray, pos: Int, bytes: ByteArray): ByteArray {
        val out = ByteArray(src.size + bytes.size)
        System.arraycopy(src, 0, out, 0, pos)
        System.arraycopy(bytes, 0, out, pos, bytes.size)
        System.arraycopy(src, pos, out, pos + bytes.size, src.size - pos)
        return out
    }

    /** 更新 APP1 段长度字段；超 64KB 返回 false。 */
    private fun updateSegLen(jpeg: ByteArray, segStart: Int, newTotal: Int): Boolean {
        if (newTotal > 65535) return false
        BinaryUtils.writeU16BE(jpeg, segStart + 2, newTotal)
        return true
    }
}
