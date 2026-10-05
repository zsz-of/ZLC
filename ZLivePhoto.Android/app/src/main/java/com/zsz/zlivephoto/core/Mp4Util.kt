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

    /**
     * 把 cameralbum footer 载荷包成真机同构的 ISOBMFF uuid box：
     *   [u32(payload.size + 8)]["uuid"][payload]
     *
     * 真机（OPPO/vivo 单文件动态照片）尾部元数据盒逐字节形如：
     *   [00 00 00 F0]["uuid"]["vivoMediaExtInfo"(16B)]["vivo" …json… "cameralbum!" … 43B tail]
     * 而 FooterUtil.extPrefix == "vivoMediaExtInfo" + "vivo"，
     * 也就是 footer 载荷本身已经以 16 字节 user type 开头，因此这里只需补 8 字节 box 头。
     *
     * 缺这 8 字节 box 头时，尾部载荷前 4 字节（"vivo" = 0x7669766F ≈ 1.85 GiB）会被 ISO box
     * 遍历当成 box size、且紧随的 4 字节恰好是可打印 ASCII（"Medi"），该段字节因此不是
     * 良构的 box 链（回归用例 OppoDeviceContractTest.bareTrailer_isNotWellFormedIsobmff
     * 固定的就是这个布局事实）。
     *
     * 边界（逆向 ColorOS 相册 com.coloros.gallery3d 17.10.7 得到）：它 fork 的 ExoPlayer
     * `Mp4Extractor` 只在 `atomSize < atomHeaderBytesRead` 时抛 ParserException；超大未知
     * atom 走「PositionHolder 请求 seek 到当前偏移 + size」的路径，并没有观察到「遇到裸
     * trailer 必抛异常」。所以本改动只主张「产物尾部回到与真机同构的良构 box」这一可验证
     * 事实，不主张它是设备端「可识别不可播放」的唯一根因。
     */
    fun wrapVivoUuidBox(payload: ByteArray): ByteArray = packBox("uuid", payload)

    /**
     * 按文件顺序列出 moov 下每条 trak 的 hdlr `handler_type`（如 `vide`/`soun`/`meta`）。
     *
     * 用途：判断输入视频里是否夹带了非音视频轨（Apple MOV 常见的 `mett` 元数据轨、
     * `tmcd` 时间码轨等）。这类轨会让产物在部分机型上「相册能识别、无法长按播放/编辑」，
     * 需要在写动态照片之前剔除（见 [VideoTrackSanitizer]）。
     * 纯字节解析，无 Android 依赖，可在 JVM 单测里直接验证。
     */
    fun trackHandlers(data: ByteArray): List<String> {
        val boxes = iterateBoxes(data, 0, data.size).toList()
        val moov = boxes.firstOrNull { it.type == "moov" } ?: return emptyList()
        val out = ArrayList<String>()
        for (b in iterateBoxes(data, moov.offset + moov.headerLen, moov.offset + moov.size)) {
            if (b.type != "trak") continue
            val trak = walkInto(data, b.offset, b.size, b.headerLen, setOf("mdia")).toList()
            val hdlr = trak.firstOrNull { it.type == "hdlr" } ?: continue
            // hdlr 为 FullBox：version/flags(4) + pre_defined(4) + handler_type(4)
            val body = hdlr.offset + hdlr.headerLen
            if (body + 12 > data.size) continue
            out.add(String(data, body + 8, 4, Charsets.ISO_8859_1))
        }
        return out
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

    /** 修复 moov 内所有 stco/co64 条目：位于 insertAt 之后的偏移整体前移/后移 delta。 */
    private fun fixChunkOffsets(
        data: ByteArray, buf: ByteArray, moovOff: Int, moovSize: Int,
        insertAt: Int, delta: Int, moovHeaderLen: Int = 8
    ) {
        val containers = setOf("trak", "mdia", "minf", "stbl")
        for (b in walkInto(data, moovOff, moovSize, moovHeaderLen, containers)) {
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

    /**
     * 把 MP4 的 ftyp 改为 QuickTime MOV 格式：major_brand 与 compatible_brands 全部写为
     * `qt  `（真机 Apple MOV 形如 `major='qt  ' minor=0 compat=['qt  ']`）。
     *
     * 只改 major 会留下 `isom/avc1/mp41` 这类 MP4 品牌，QuickTime/照片 App 按兼容品牌
     * 判定时会把它当普通 MP4 处理，配对标识（content.identifier）可能不被识别。
     * 改写等长（每个品牌 4 字节），**字节数不变**，无需修 stco。
     */
    fun mp4ToMov(data: ByteArray): ByteArray {
        if (!hasFtyp(data)) throw Mp4Exception("缺少 ftyp box")
        val ftypSize = BinaryUtils.readU32BE(data, 0).toInt()
        if (ftypSize < 16 || ftypSize > data.size) throw Mp4Exception("ftyp box 尺寸非法")
        val buf = data.copyOf()
        // major_brand(8) 与 compatible_brands(16..ftypSize) 都写 qt  ；
        // minor_version 在 12，**必须跳过**（真机 Apple MOV 是 0，写上 "qt  " 会写坏版本字段）
        var pos = 8
        while (pos + 4 <= ftypSize) {
            if (pos != 12) {
                buf[pos] = 0x71.toByte()
                buf[pos + 1] = 0x74.toByte()
                buf[pos + 2] = 0x20.toByte()
                buf[pos + 3] = 0x20.toByte()
            }
            pos += 4
        }
        return buf
    }

    /**
     * 归一化 ftyp 品牌：`major_brand` 与 `compatible_brands` 中出现的 `qt  `（QuickTime）
     * 全部改写为 `isom`。**字节数不变**（不移动任何 box），因此不需要修 stco/co64。
     *
     * 真机 Apple MOV 形如 `major='qt  ' minor=0 compat=['qt  ']`；只改 major 会在
     * compatible_brands 里留下 `qt  `，使解析器（Media3/AOSP 的 brandSet）仍把产物
     * 判为 QuickTime 文件，部分厂商相册因此不按 MP4 播放。
     */
    fun normalizeFtyp(data: ByteArray): ByteArray {
        if (!hasFtyp(data) || data.size < 16) return data
        val ftypSize = BinaryUtils.readU32BE(data, 0).toInt()
        if (ftypSize < 16 || ftypSize > data.size) return data
        val qt = byteArrayOf(0x71, 0x74, 0x20, 0x20) // "qt  "
        val buf = data.copyOf()
        var patched = false
        // major_brand(8) / minor_version(12) / compatible_brands(16..ftypSize)，按 4 字节对齐扫描
        var pos = 8
        while (pos + 4 <= ftypSize) {
            if (BinaryUtils.arrayEquals(buf, pos, qt)) {
                buf[pos] = 0x69 // 'i'
                buf[pos + 1] = 0x73 // 's'
                buf[pos + 2] = 0x6F // 'o'
                buf[pos + 3] = 0x6D // 'm'
                patched = true
            }
            pos += 4
        }
        return if (patched) buf else data
    }

    /**
     * 把 QuickTime MOV 恢复为标准 MP4：归一化 ftyp 品牌（含 compatible_brands 中的
     * `qt  `）。用于从 Apple 读回时归一化视频流：vivo/Google/小米等 MP4 格式若保留
     * "qt  " 品牌，会让解析器把产物当 QuickTime 文件处理。
     */
    fun movToMp4(data: ByteArray): ByteArray = normalizeFtyp(data)

    /**
     * 纯字节级剔除 moov 下 `hdlr` 不是 `vide`/`soun` 的 trak。
     *
     * Apple MOV 的实况视频除视频轨外带一条 `meta` 轨（sample entry `mebx`，存放
     * content.identifier / still-image-time），QuickTime 录制还会带 `tmcd` 时间码轨；
     * Google 规范用 `mett` 元数据轨。这些轨对 Android 相册/播放器无意义，且是
     * 「能识别动态照片但无法长按播放/编辑」的已知诱因之一。
     *
     * 与 [VideoTrackSanitizer] 的 MediaMuxer 重封装相比，这里是**零拷贝**：样本数据
     * （mdat）逐字节不变，只删 moov 里的 trak box，因此不存在重封装引入的画质/时间戳/
     * 编码差异。仅在「被删 trak 恰好是 moov 子 box 链末尾的连续区间」这一可安全处理的
     * 情形下生效，否则原样返回，由调用方回退到重封装路径。
     */
    fun pruneNonAvTracks(data: ByteArray): ByteArray {
        if (!hasFtyp(data)) return data
        val boxes = iterateBoxes(data, 0, data.size).toList()
        val moov = boxes.firstOrNull { it.type == "moov" } ?: return data
        val moovEnd = moov.offset + moov.size
        val children = iterateBoxes(data, moov.offset + moov.headerLen, moovEnd).toList()
        if (children.isEmpty()) return data

        val drop = ArrayList<Box>()
        for (b in children) {
            if (b.type != "trak") continue
            val hdlr = walkInto(data, b.offset, b.size, b.headerLen, setOf("mdia"))
                .firstOrNull { it.type == "hdlr" } ?: return data // 结构异常：交给重封装路径
            val body = hdlr.offset + hdlr.headerLen
            if (body + 12 > data.size) return data
            val handler = String(data, body + 8, 4, Charsets.ISO_8859_1)
            if (handler != "vide" && handler != "soun") drop.add(b)
        }
        if (drop.isEmpty()) return data

        // 只删一段连续字节：被删 trak 彼此必须紧邻，且其区间内不得夹着要保留的子 box。
        // （真机 Apple MOV 的 moov 常见顺序是 `mvhd → trak(vide) → trak(meta) → udta`，
        // 要保留的 udta 就在被删 meta 轨之后，因此不能要求「删到 moov 末尾」。）
        val first = drop.first()
        val last = drop.last()
        val spanEnd = last.offset + last.size
        for (i in 1 until drop.size) {
            if (drop[i - 1].offset + drop[i - 1].size != drop[i].offset) return data
        }
        for (b in children) {
            if (drop.any { it.offset == b.offset }) continue
            if (b.offset >= first.offset && b.offset + b.size <= spanEnd) return data
        }

        val removed = spanEnd - first.offset
        val newMoovSize = moov.size - removed
        // 关键顺序：先在**旧坐标**下扣减 chunk 偏移（stco 条目的位置与值都是旧坐标），
        // 再做字节级删除 —— 落在被删区间之后的 trak 的条目会随其 box 一起前移。
        // 若反过来先删除再按旧坐标写，跟在被删 trak 之后的 stco 会被写到错位的字节上。
        val fixed = data.copyOf()
        fixChunkOffsets(data, fixed, moov.offset, moov.size, spanEnd, -removed, moov.headerLen)
        val out = ByteArray(data.size - removed)
        System.arraycopy(fixed, 0, out, 0, first.offset)
        // moov 内被删区间之后的子 box（udta/meta 等）与 moov 之后的 mdat 一起前移
        val tailLen = data.size - spanEnd
        if (tailLen > 0) System.arraycopy(fixed, spanEnd, out, first.offset, tailLen)
        BinaryUtils.writeU32BE(out, moov.offset, newMoovSize.toLong())
        return out
    }

    /**
     * Apple MOV → 标准 MP4 的**确定性**字节级归一（无 MediaMuxer、无重编码）：
     * ① 剔除非音视频轨（`mebx`/`mett`/`tmcd` 等）；② ftyp 品牌归一（清除 `qt  `）。
     *
     * 字节不变则返回原引用，调用方据此判断「无需净化」。
     */
    fun normalizeMovToMp4(data: ByteArray): ByteArray = normalizeFtyp(pruneNonAvTracks(data))

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

        val moovChildren = iterateBoxes(data, moovOff + moov.headerLen, moovOff + moovSize).toList()
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

    /** 安卓侧常见的可解码视频编码（fourcc）。 */
    private val commonVideoFourccs = setOf("avc1", "avc3", "hev1", "hvc1")

    /** 动态照片规范要求的音轨编码：只有 AAC 的 `mp4a`。 */
    private const val AAC_FOURCC = "mp4a"

    /**
     * 安卓兼容性判定结果：主视频/音频编码 + **仅重封装容器无法解决**的问题清单。
     */
    internal class VideoCompat(
        val videoCodec: String,
        val audioCodec: String,
        val reasons: List<String>
    ) {
        val needsReencode: Boolean get() = reasons.isNotEmpty()
    }

    /**
     * 在 sample entry（`avc1`/`hvc1`/`mp4a` …）内部定位类型为 [type] 的子 box。
     *
     * 先按各类 sample entry 的标准字段长度试一次，失败则按 4 字节对齐扫描 ——
     * Apple 的 `mebx`、加密的 `encv` 等变体字段长度与标准不同，硬编码字段长度会漏检。
     */
    private fun childBox(data: ByteArray, entry: Box, type: String, canonicalOffset: Int): Box? {
        // 声明尺寸可能被写坏（真实文件里也见过），绝不能读出数组边界
        val end = minOf(entry.offset + entry.size, data.size)
        // 1) 标准字段长度：从子 box 链起点按声明尺寸逐个步进。子 box 未必 4 字节对齐
        //    （hvcC 的 payload 是 23 字节、box 总长 31），所以不能用固定步长扫描。
        iterateBoxes(data, entry.offset + canonicalOffset, end)
            .firstOrNull { it.type == type }
            ?.let { return it }
        // 2) 兜底：字段长度非标准的变体（`mebx`/`encv`…）——从 box 头之后按 4 字节对齐粗扫
        var pos = entry.offset + 8
        while (pos + 8 <= end) {
            val size = BinaryUtils.readU32BE(data, pos).toInt()
            if (size >= 8 && pos + size <= end &&
                String(data, pos + 4, 4, Charsets.ISO_8859_1) == type
            ) {
                return Box(type, pos, size, 8)
            }
            pos += 4
        }
        return null
    }

    /**
     * 视觉 sample entry 的标准子 box 起点：8 字节 box 头 + 8 字节
     * `reserved[6] + data_reference_index[2]` + 70 字节 VisualSampleEntry 字段 = 86。
     */
    private const val VISUAL_ENTRY_CHILD_OFFSET = 86

    /** 判定 tkhd 的变换矩阵是否含镜像（行列式 < 0）。 */
    private fun hasMirrorMatrix(data: ByteArray, tkhd: Box): Boolean {
        val m = tkhd.offset + tkhd.size - 8 - 36
        if (m < tkhd.offset + tkhd.headerLen) return false
        val a = BinaryUtils.readI32BE(data, m).toLong()
        val b = BinaryUtils.readI32BE(data, m + 4).toLong()
        val c = BinaryUtils.readI32BE(data, m + 12).toLong()
        val d = BinaryUtils.readI32BE(data, m + 16).toLong()
        return a * d - b * c < 0L
    }

    /**
     * 判定「只重封装容器」是否足以让产物在安卓相册里正常播放/长按。
     *
     * 容器层面的问题（品牌残留、附加轨、厂商尾部）由 [normalizeMovToMp4] 解决；
     * 这里只列**容器改不动**的码流问题，返回空清单即表示可以零拷贝直通：
     *
     * 1. 10bit H.265（iPhone「高效」+ HDR 实况）—— 部分机型相册解码器只吃 8bit；
     * 2. HDR 传输特性（PQ/HLG）—— 同上；
     * 3. 杜比视界（`dvcC`/`dvvC`）—— 必须转 SDR；
     * 4. `hev1` 标记 —— 部分机型只认 `hvc1`；
     * 5. 音轨不是 AAC —— 谷歌动态照片规范要求音轨必须是 AAC（Apple 常见 PCM）；
     * 6. 镜像变换矩阵 —— MP4 的旋转矩阵表达不了镜像，直通会得到镜像画面；
     * 7. 视频编码不是 H.264/H.265（如 ProRes）。
     *
     * 纯字节解析，无 Android 依赖，可在 JVM 单测里直接验证。
     */
    fun videoCompat(data: ByteArray): VideoCompat {
        var videoCodec = ""
        var audioCodec = ""
        val reasons = ArrayList<String>()
        if (!hasFtyp(data)) return VideoCompat(videoCodec, audioCodec, reasons)
        val boxes = iterateBoxes(data, 0, data.size).toList()
        val moov = boxes.firstOrNull { it.type == "moov" }
            ?: return VideoCompat(videoCodec, audioCodec, reasons)

        for (b in iterateBoxes(data, moov.offset + moov.headerLen, moov.offset + moov.size)) {
            if (b.type != "trak") continue
            val trak = walkInto(data, b.offset, b.size, b.headerLen, setOf("mdia", "minf", "stbl")).toList()
            val hdlr = trak.firstOrNull { it.type == "hdlr" } ?: continue
            val body = hdlr.offset + hdlr.headerLen
            if (body + 12 > data.size) continue
            val handler = String(data, body + 8, 4, Charsets.ISO_8859_1)

            val stsd = trak.firstOrNull { it.type == "stsd" } ?: continue
            val entryOffset = stsd.offset + stsd.headerLen + 8
            if (entryOffset + 8 > stsd.offset + stsd.size) continue
            val entrySize = BinaryUtils.readU32BE(data, entryOffset).toInt()
            val fourcc = String(data, entryOffset + 4, 4, Charsets.ISO_8859_1)
            val entry = Box(fourcc, entryOffset, entrySize, 8)

            when (handler) {
                "vide" -> {
                    videoCodec = fourcc
                    if (fourcc !in commonVideoFourccs) {
                        reasons.add("视频编码 $fourcc 不是安卓通用的 H.264/H.265")
                    }
                    if (fourcc == "hev1") {
                        reasons.add("H.265 标记为 hev1（部分机型只认 hvc1）")
                    }
                    val colr = childBox(data, entry, "colr", VISUAL_ENTRY_CHILD_OFFSET)
                    if (colr != null) {
                        val colourType = String(data, colr.offset + 8, 4, Charsets.ISO_8859_1)
                        // nclx：primaries(2) + transfer_characteristics(2) + matrix(2) + full_range(1)
                        if (colourType == "nclx") {
                            // transfer_characteristics 在 payload + 6（2 字节）；32 位读的高 16 位才是它
                            val transfer =
                                (BinaryUtils.readU32BE(data, colr.offset + 8 + 6) ushr 16).toInt() and 0xFFFF
                            if (transfer == 16 || transfer == 18) {
                                val name = if (transfer == 16) "PQ" else "HLG"
                                reasons.add("视频为 HDR（$name 传输特性），部分机型相册会花屏或黑屏")
                            }
                        }
                    }
                    if (childBox(data, entry, "dvcC", VISUAL_ENTRY_CHILD_OFFSET) != null ||
                        childBox(data, entry, "dvvC", VISUAL_ENTRY_CHILD_OFFSET) != null
                    ) {
                        reasons.add("视频为杜比视界（Dolby Vision），安卓相册普遍不支持")
                    }
                    val hvcC = childBox(data, entry, "hvcC", VISUAL_ENTRY_CHILD_OFFSET)
                    if (hvcC != null) {
                        // HEVCDecoderConfigurationRecord：configurationVersion(1) …
                        // reserved+chromaFormat(1) 在 +16，reserved+bitDepthLumaMinus8(1) 在 +17
                        val payload = hvcC.offset + hvcC.headerLen
                        if (payload + 19 <= hvcC.offset + hvcC.size) {
                            val bitDepth = data[payload + 17].toInt() and 0x07
                            if (bitDepth > 0) {
                                reasons.add("视频为 ${bitDepth + 8}bit H.265（HDR 实况），部分机型相册解码不了")
                            }
                        }
                    }
                    val tkhd = trak.firstOrNull { it.type == "tkhd" }
                    if (tkhd != null && hasMirrorMatrix(data, tkhd)) {
                        reasons.add("视频带镜像变换矩阵（MP4 旋转矩阵无法表达镜像）")
                    }
                }
                "soun" -> {
                    audioCodec = fourcc
                    if (fourcc != AAC_FOURCC) {
                        reasons.add("音轨编码 $fourcc 不是 AAC（动态照片规范要求 AAC，Apple 常为 PCM）")
                    }
                }
            }
        }
        return VideoCompat(videoCodec, audioCodec, reasons)
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

        for (b in iterateBoxes(data, moovOff + moov.headerLen, moovOff + moovSize)) {
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
                    // v1：creation(8) + modification(8) 都是 64 位，timescale 后移 8 字节
                    timescale = BinaryUtils.readU32BE(data, body + 20)
                    duration = BinaryUtils.readU64BE(data, body + 24)
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
