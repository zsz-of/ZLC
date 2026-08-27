package com.zsz.zlivephoto.core

/**
 * MP4 (ISOBMFF) box 级工具：box 遍历、流边界定位、vivo uuid 处理、
 * lpex box 插入（含 stco/co64 偏移修复）、视频轨信息解析。
 * 纯字节级操作，不重编码。
 */
internal class Mp4Exception(message: String) : Exception(message)

internal object Mp4Util {
    val vivoUuid: ByteArray = "vivoMediaExtInfo".toByteArray(Charsets.US_ASCII) // 16 字节

    private fun be32(v: Long): ByteArray {
        val b = ByteArray(4)
        BinaryUtils.writeU32BE(b, 0, v)
        return b
    }

    internal class Box(val type: String, val offset: Int, val size: Int, val headerLen: Int)

    /**
     * 遍历 [start, end) 区间内的顶层 box，返回 Box 序列。
     * 遇到非法 box 即停止。box type 必须为 4 个可打印 ASCII。
     */
    fun iterateBoxes(data: ByteArray, start: Int, end: Int): Sequence<Box> = sequence {
        var pos = start
        while (pos + 8 <= end) {
            val size32 = BinaryUtils.readU32BE(data, pos)

            // 校验 box type 为可打印 ASCII（OPPO 私有浮点块 type 非 ASCII，借此截断）
            var validType = true
            for (i in pos + 4 until pos + 8) {
                val c = data[i].toInt() and 0xFF
                if (c < 0x20 || c > 0x7E) { validType = false; break }
            }
            if (!validType) return@sequence

            var size = size32
            var header = 8
            if (size32 == 1L) {
                if (pos + 16 > end) return@sequence
                size = BinaryUtils.readU64BE(data, pos + 8)
                header = 16
            } else if (size32 == 0L) {
                size = (end - pos).toLong()
            }

            if (size < header || pos + size > end) return@sequence

            val typeStr = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            yield(Box(typeStr, pos, size.toInt(), header))
            pos += size.toInt()
        }
    }

    /**
     * 从文件头遍历顶层 box，返回合法 MP4 流的总长度（忽略流后附加数据）。
     */
    fun streamLength(data: ByteArray): Int {
        var lastEnd = 0
        for (b in iterateBoxes(data, 0, data.size)) {
            lastEnd = b.offset + b.size
        }
        return lastEnd
    }

    fun hasFtyp(data: ByteArray): Boolean {
        if (data.size < 12) return false
        val ftyp = byteArrayOf(0x66, 0x74, 0x79, 0x70) // "ftyp"
        return BinaryUtils.arrayEquals(data, 4, ftyp)
    }

    /** 若 MP4 末尾存在 UUID 为 'vivoMediaExtInfo' 的 uuid box，则去除。 */
    fun stripVivoUuid(data: ByteArray): ByteArray {
        val boxes = iterateBoxes(data, 0, data.size).toList()
        if (boxes.isEmpty()) return data

        val last = boxes.last()
        if (last.type == "uuid" && BinaryUtils.arrayEquals(data, last.offset + 8, vivoUuid)) {
            return data.copyOfRange(0, last.offset)
        }
        return data
    }

    private fun walkInto(
        data: ByteArray, offset: Int, size: Int, headerLen: Int, pathTypes: Set<String>
    ): Sequence<Box> = sequence {
        for (b in iterateBoxes(data, offset + headerLen, offset + size)) {
            yield(b)
            if (b.type in pathTypes) {
                yieldAll(walkInto(data, b.offset, b.size, b.headerLen, pathTypes))
            }
        }
    }

    /** 修复 moov 内所有 stco/co64 条目：位于 insertAt 之后的偏移整体后移 delta。 */
    private fun fixChunkOffsets(
        data: ByteArray, buf: ByteArray, moovOff: Int, moovSize: Int,
        insertAt: Int, delta: Int
    ) {
        val containers = setOf("trak", "mdia", "minf", "stbl")
        for (b in walkInto(data, moovOff, moovSize, 8, containers)) {
            if (b.type != "stco" && b.type != "co64") continue
            val entrySize = if (b.type == "co64") 8 else 4
            val body = b.offset + b.headerLen
            val count = BinaryUtils.readU32BE(data, body + 4).toInt()
            val entriesStart = body + 8

            for (i in 0 until count) {
                val epos = entriesStart + i * entrySize
                if (b.type == "co64") {
                    val v = BinaryUtils.readU64BE(data, epos)
                    if (v.toULong() >= insertAt.toULong()) {
                        BinaryUtils.writeU64BE(buf, epos, v + delta.toLong())
                    }
                } else {
                    val v = BinaryUtils.readU32BE(data, epos)
                    if (v >= insertAt) {
                        BinaryUtils.writeU32BE(buf, epos, v + delta)
                    }
                }
            }
        }
    }

    /** 把一个顶层自定义 box 追加为 moov 的最后一个子 box，并修复 stco/co64 偏移。 */
    fun insertBoxIntoMoov(data: ByteArray, boxType: String, payload: ByteArray): ByteArray {
        val boxes = iterateBoxes(data, 0, data.size).toList()
        val moov = boxes.firstOrNull { it.type == "moov" }
            ?: throw Mp4Exception("MP4 缺少 moov box")

        val moovOff = moov.offset
        val moovSize = moov.size
        val insertAt = moovOff + moovSize

        val newBox = ByteArray(8 + payload.size)
        BinaryUtils.writeU32BE(newBox, 0, (payload.size + 8).toLong())
        val typeBytes = boxType.toByteArray(Charsets.ISO_8859_1)
        System.arraycopy(typeBytes, 0, newBox, 4, typeBytes.size)
        System.arraycopy(payload, 0, newBox, 8, payload.size)
        val delta = newBox.size

        val buf = data.copyOf()
        fixChunkOffsets(data, buf, moovOff, moovSize, insertAt, delta)

        // 更新 moov size
        BinaryUtils.writeU32BE(buf, moovOff, (moovSize + delta).toLong())

        val result = ByteArray(buf.size + delta)
        System.arraycopy(buf, 0, result, 0, insertAt)
        System.arraycopy(newBox, 0, result, insertAt, delta)
        System.arraycopy(buf, insertAt, result, insertAt + delta, buf.size - insertAt)
        return result
    }

    /** 把 MP4 的 ftyp 改为 QuickTime MOV 格式（major_brand=qt  ）。 */
    fun mp4ToMov(data: ByteArray): ByteArray {
        if (!hasFtyp(data)) throw Mp4Exception("缺少 ftyp box")
        val buf = data.copyOf()
        // "qt  " = 0x71 0x74 0x20 0x20
        buf[8] = 0x71.toByte()
        buf[9] = 0x74.toByte()
        buf[10] = 0x20.toByte()
        buf[11] = 0x20.toByte()
        return buf
    }

    /**
     * 把 QuickTime MOV（major_brand=qt  ）恢复为标准 MP4（major_brand=isom）。
     * 用于从 Apple 读回时归一化视频流：vivo/Google/小米等 MP4 格式若保留
     * "qt  " 品牌，会导致相册能识别动态照片却无法正常播放。
     */
    fun movToMp4(data: ByteArray): ByteArray {
        if (!hasFtyp(data)) return data
        val buf = data.copyOf()
        val qt = byteArrayOf(0x71, 0x74, 0x20, 0x20) // "qt  "
        if (BinaryUtils.arrayEquals(buf, 8, qt)) {
            // "isom" = 0x69 0x73 0x6F 0x6D
            buf[8] = 0x69.toByte()
            buf[9] = 0x73.toByte()
            buf[10] = 0x6F.toByte()
            buf[11] = 0x6D.toByte()
        }
        return buf
    }

    private fun packBox(type: String, payload: ByteArray): ByteArray {
        val result = ByteArray(8 + payload.size)
        BinaryUtils.writeU32BE(result, 0, (payload.size + 8).toLong())
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        System.arraycopy(typeBytes, 0, result, 4, typeBytes.size)
        System.arraycopy(payload, 0, result, 8, payload.size)
        return result
    }

    private fun packBox(type: String, payload: List<ByteArray>): ByteArray =
        packBox(type, concat(payload))

    private fun concat(parts: List<ByteArray>): ByteArray {
        var total = 0
        for (p in parts) total += p.size
        val result = ByteArray(total)
        var off = 0
        for (p in parts) {
            System.arraycopy(p, 0, result, off, p.size)
            off += p.size
        }
        return result
    }

    /**
     * 在 MP4/MOV 的 moov/udta 中添加 Apple QuickTime metadata。
     * 写入 com.apple.quicktime.content.identifier 和 com.apple.quicktime.still-image-time。
     * 如果 udta 不存在则创建。
     */
    fun addAppleMetadata(data: ByteArray, contentId: String): ByteArray {
        val cidBytes = contentId.toByteArray(Charsets.UTF_8)

        // keys box：entry_count=2 + 两个 mdta key 项
        val key1 = "com.apple.quicktime.content.identifier".toByteArray(Charsets.UTF_8)
        val key2 = "com.apple.quicktime.still-image-time".toByteArray(Charsets.UTF_8)
        val keysPayload = concat(listOf(
            be32(2L),
            packBox("mdta", key1),
            packBox("mdta", key2)
        ))
        val keysBox = packBox("keys", concat(listOf(ByteArray(4), keysPayload)))

        // ilst box：item1 = content.identifier（UTF-8），item2 = still-image-time（int32 0）
        val data1 = packBox("data", concat(listOf(be32(1L), be32(0L), cidBytes)))
        val item1Box = packBox("item", concat(listOf(be32(1L), data1)))
        val data2 = packBox("data", concat(listOf(be32(22L), be32(0L), be32(0L))))
        val item2Box = packBox("item", concat(listOf(be32(2L), data2)))
        val ilstBox = packBox("ilst", concat(listOf(item1Box, item2Box)))

        // hdlr box（mdir handler）
        val mdirBytes = byteArrayOf(0x6D, 0x69, 0x64, 0x72) // "mdir"
        val hdlrPayload = concat(listOf(ByteArray(4), ByteArray(4), mdirBytes, ByteArray(12), byteArrayOf(0)))
        val hdlrBox = packBox("hdlr", hdlrPayload)

        // meta box（QuickTime 格式：FullBox header）
        val metaBox = packBox("meta", concat(listOf(ByteArray(4), hdlrBox, keysBox, ilstBox)))

        // 检查是否已有 udta
        val boxes = iterateBoxes(data, 0, data.size).toList()
        val moov = boxes.firstOrNull { it.type == "moov" }
            ?: throw Mp4Exception("MP4 缺少 moov box")

        val moovOff = moov.offset
        val moovSize = moov.size

        val moovChildren = iterateBoxes(data, moovOff + 8, moovOff + moovSize).toList()
        val udta = moovChildren.firstOrNull { it.type == "udta" }

        if (udta != null) {
            // udta 已存在：在 udta 内追加 meta，并修复 stco/co64（moov 变大导致 mdat 后移）
            val udtaOff = udta.offset
            val udtaSize = udta.size
            val delta = metaBox.size
            val insertAt = udtaOff + udtaSize

            val buf = data.copyOf()
            fixChunkOffsets(data, buf, moovOff, moovSize, insertAt, delta)

            BinaryUtils.writeU32BE(buf, udtaOff, (udtaSize + delta).toLong())
            BinaryUtils.writeU32BE(buf, moovOff, (moovSize + delta).toLong())

            val result = ByteArray(buf.size + delta)
            System.arraycopy(buf, 0, result, 0, insertAt)
            System.arraycopy(metaBox, 0, result, insertAt, delta)
            System.arraycopy(buf, insertAt, result, insertAt + delta, buf.size - insertAt)
            return result
        }

        // 创建 udta，追加到 moov 末尾（InsertBoxIntoMoov 内部修复 stco）
        return insertBoxIntoMoov(data, "udta", metaBox)
    }

    /**
     * 解析主视频轨信息：codec/width/height/rotation/duration_us/fps/frame_count。
     */
    fun getTrackInfo(data: ByteArray): MutableMap<String, Any?>? {
        val boxes = iterateBoxes(data, 0, data.size).toList()
        val moov = boxes.firstOrNull { it.type == "moov" } ?: return null

        val moovOff = moov.offset
        val moovSize = moov.size

        val info: MutableMap<String, Any?> = mutableMapOf(
            "codec" to "",
            "width" to 0,
            "height" to 0,
            "rotation" to 0,
            "duration_us" to -1L,
            "fps" to 0.0,
            "frame_count" to 0L
        )

        for (b in iterateBoxes(data, moovOff + 8, moovOff + moovSize)) {
            if (b.type != "trak") continue

            val trak = walkInto(data, b.offset, b.size, b.headerLen, setOf("mdia", "minf", "stbl")).toList()
            val hdlr = trak.firstOrNull { it.type == "hdlr" } ?: continue

            // hdlr 为 FullBox：version/flags(4) + pre_defined(4) + handler_type(4)
            val hdlrBody = hdlr.offset + hdlr.headerLen
            val videBytes = byteArrayOf(0x76, 0x69, 0x64, 0x65) // "vide"
            if (!BinaryUtils.arrayEquals(data, hdlrBody + 8, videBytes)) continue

            // tkhd：宽高 = 末尾 8 字节（16.16 定点），旋转矩阵在其前 36 字节
            val tkhd = trak.firstOrNull { it.type == "tkhd" }
            if (tkhd != null) {
                val tkhdEnd = tkhd.offset + tkhd.size
                val w16 = BinaryUtils.readU32BE(data, tkhdEnd - 8)
                val h16 = BinaryUtils.readU32BE(data, tkhdEnd - 4)
                info["width"] = (w16 ushr 16).toInt()
                info["height"] = (h16 ushr 16).toInt()

                val m = tkhdEnd - 8 - 36
                val a = BinaryUtils.readI32BE(data, m)
                val b2 = BinaryUtils.readI32BE(data, m + 4)
                val c = BinaryUtils.readI32BE(data, m + 8)
                val d = BinaryUtils.readI32BE(data, m + 12)
                if (a == 0 && b2 == 65536 && c == -65536 && d == 0) info["rotation"] = 90
                else if (a == -65536 && b2 == 0 && c == 0 && d == -65536) info["rotation"] = 180
                else if (a == 0 && b2 == -65536 && c == 65536 && d == 0) info["rotation"] = 270
            }

            // mdhd：timescale + duration
            val mdhd = trak.firstOrNull { it.type == "mdhd" }
            var durationS = 0.0
            if (mdhd != null) {
                val body = mdhd.offset + mdhd.headerLen
                val version = data[body]
                val timescale: Long
                val duration: Long
                if (version.toInt() == 1) {
                    timescale = BinaryUtils.readU32BE(data, body + 12)
                    duration = BinaryUtils.readU64BE(data, body + 16)
                } else {
                    timescale = BinaryUtils.readU32BE(data, body + 12)
                    duration = BinaryUtils.readU32BE(data, body + 16)
                }
                if (timescale > 0) {
                    durationS = duration.toDouble() / timescale
                    info["duration_us"] = (durationS * 1_000_000).toLong()
                }
            }

            // stts：样本总数 → fps
            val stts = trak.firstOrNull { it.type == "stts" }
            if (stts != null) {
                val body = stts.offset + stts.headerLen
                val count = BinaryUtils.readU32BE(data, body + 4).toInt()
                var total = 0L
                for (i in 0 until count) {
                    total += BinaryUtils.readU32BE(data, body + 8 + i * 8)
                }
                info["frame_count"] = total
                if (durationS > 0) {
                    info["fps"] = total / durationS
                }
            }

            // stsd：编码 fourcc
            val stsd = trak.firstOrNull { it.type == "stsd" }
            if (stsd != null) {
                val body = stsd.offset + stsd.headerLen
                info["codec"] = String(data, body + 12, 4, Charsets.ISO_8859_1)
            }

            return info
        }
        return null
    }
}
