package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.BinaryUtils

/**
 * 三星 SEF（Samsung Extended Format）尾部容器 —— 三星 Galaxy 动态照片的字节级格式。
 *
 * 逆向自三星相册 `com.sec.android.gallery3d` 15.7.00.43 的两套独立实现：
 * `com.samsung.android.gallery.support.library.sef.{TailData,Sdr,SefParser,SefData,Drdh}`
 * 与 `com.samsung.android.motionphoto.utils.ex.{MotionPhotoParser,MotionPhotoUtilsConst}`。
 *
 * ## 文件整体布局
 * ```
 * [JPEG 主图（可含 GainMap JPEG）] [载荷 0] [载荷 1] … [SEFH 头 + 记录表 + 总长 + SEFT]
 * ```
 * 载荷区紧贴在尾部块之前，**顺序与记录表一致**；JPEG 与载荷之间没有额外填充。
 *
 * ## 尾部块（`TailData.pack` 的逐字节还原，全部小端）
 * ```
 * +0   "SEFH"                     4B
 * +4   version                    u32   （0 视为 106 = 0x6A）
 * +8   count                      u32   记录条数（解析端上限 100）
 * +12  记录[0] … 记录[count-1]     每条 12B：value u32 / drsp u32 / drsl u32
 * +12+12n  blockSize - 8          u32   = 12 + 12*count
 * +16+12n  "SEFT"                 4B
 * 块总长 = 20 + 12*count
 * ```
 * 解析端 `TailData.load` 先读**最后 8 字节**：`[u32][SEFT]`，其中 u32 = 块总长 - 8；
 * 于是块起点 = `fileSize - (u32 + 8)`，再校验该处是 `SEFH`。
 *
 * ## 记录表字段
 * - `value = (dataType shl 16) or (option==1 ? 0x8000 : 0)`（`Drdh` 的构造与解析互为逆运算）
 * - `drsp` = 该载荷起点到尾部块起点的距离，即 `载荷起点 = 块起点 - drsp`
 * - `drsl` = 该载荷的完整字节数
 *
 * ## 载荷自身（`SefData(J,[B)` 的解析逆推）
 * ```
 * +0   value     u32   与记录表的 value 相同
 * +4   nameLen   u32   名称字节数
 * +8   dsiLen    u32   仅当 option != 0 时存在
 * +8/12  name    nameLen 字节（UTF-8）
 * 之后   data    `SefParser.addData` 写入的业务数据（动态照片时就是 MP4 字节）
 * ```
 * `SefParser.addData(name, dataType, bytes)` 创建记录时 `option` 恒为 0，故本项目写出端
 * 只产生「无 dsiLen」这一种形态；读取端对两种形态都做兼容。
 *
 * ## 与其它格式的差异
 * 三星**不使用** Google 的 XMP（`GCamera:MicroVideo*` / `MotionPhoto*`），也不使用
 * vivo/OPPO 的 footer JSON。它的「这里是动态照片」完全由 SEF 尾部块承载；因此
 * [com.zsz.zlivephoto.core.formats.SamsungPlugin] 的识别不依赖 XMP。
 *
 * ## 未在真机验证
 * 上述布局来自反编译（`spec` 级还原），本仓库没有三星设备可做端到端验证；
 * 读取端已做成**布局无关**（不依赖 drsp/drsl，改在 JPEG 之后直接搜 `ftyp`），
 * 写出端严格按 `TailData.pack`/`Sdr.pack`/`SefData` 的字节语义生成。
 */
internal object SamsungSef {
    /** 尾部块起始标记 */
    val HEADER: ByteArray = byteArrayOf(0x53, 0x45, 0x46, 0x48) // "SEFH"

    /** 尾部块结束标记（文件最末 4 字节） */
    val TRAILER: ByteArray = byteArrayOf(0x53, 0x45, 0x46, 0x54) // "SEFT"

    /** 动态照片视频数据（`MotionPhotoParser.MOTION_PHOTO_DATA_TYPE`） */
    const val TYPE_MOTION_PHOTO_DATA: Int = 0xA30

    /** 动态照片版本（`MOTION_PHOTO_VERSION_DATA_TYPE`） */
    const val TYPE_MOTION_PHOTO_VERSION: Int = 0xA31

    /** 动态照片附加信息（`MOTION_PHOTO_INFO_DATA_TYPE`） */
    const val TYPE_MOTION_PHOTO_INFO: Int = 0xA32

    /** 自动播放（`MOTION_PHOTO_AUTOPLAY_DATA_TYPE`） */
    const val TYPE_MOTION_PHOTO_AUTOPLAY: Int = 0xA33

    /** 拍摄 UTC 时间（`IMAGE_UTC_DATA_TYPE`） */
    const val TYPE_IMAGE_UTC_DATA: Int = 0xA01

    /** 默认版本号：`TailData.getVersion()` 在 version==0 时回落 0x6A */
    const val DEFAULT_VERSION: Int = 0x6A

    /** 单条记录的表项长度 */
    const val RECORD_SIZE: Int = 12

    /** 尾部块除记录表外的固定开销：SEFH(4) + version(4) + count(4) + 总长(4) + SEFT(4) */
    const val BLOCK_OVERHEAD: Int = 20

    /** 名称最大字节数（防御性上限，解析端 count 上限 100 同理） */
    private const val MAX_NAME_BYTES = 4096

    /** 解析端接受的记录条数上限（对齐 `MAX_SEF_DATA_COUNT = 0x64`） */
    private const val MAX_RECORDS = 100

    /** 动态照片数据的记录名（`MOTION_PHOTO_DATA_KEY_NAME`） */
    const val KEY_MOTION_PHOTO_DATA: String = "MotionPhoto_Data"

    /**
     * 一条 SEF 记录。
     *
     * @param type   数据类型（见 `TYPE_*`）
     * @param option `Drdh.option`，0 或 1
     * @param name   记录名（写入载荷头的 UTF-8 名称）
     * @param payload 业务数据（不含载荷头）
     */
    class Record(
        val type: Int,
        val option: Int,
        val name: String,
        val payload: ByteArray
    ) {
        /** 名称字节（缓存，避免重复编码） */
        val nameBytes: ByteArray = name.toByteArray(Charsets.UTF_8)

        /** 载荷头长度：value(4) + nameLen(4) [+ dsiLen(4) 当 option != 0] */
        val headerLen: Int get() = 8 + if (option != 0) 4 else 0

        /** 载荷整段字节数（含载荷头） */
        val size: Int get() = headerLen + nameBytes.size + payload.size

        /** `Drdh.value` 的编码：`(type shl 16) or (option==1 ? 0x8000 : 0)` */
        val value: Int get() = (type shl 16) or if (option == 1) 0x8000 else 0

        /** 序列化为载荷整段（含头），对应 `SefData.packet`。 */
        fun encode(): ByteArray {
            val out = ByteArray(size)
            BinaryUtils.writeU32LE(out, 0, value.toLong() and 0xFFFFFFFFL)
            BinaryUtils.writeU32LE(out, 4, nameBytes.size.toLong())
            if (option != 0) BinaryUtils.writeU32LE(out, 8, 0L)
            System.arraycopy(nameBytes, 0, out, headerLen, nameBytes.size)
            System.arraycopy(payload, 0, out, headerLen + nameBytes.size, payload.size)
            return out
        }

        companion object {
            /** 按载荷整段（含头）反解出一条记录；不合法返回 null。 */
            fun decode(bytes: ByteArray): Record? {
                if (bytes.size < 8) return null
                val value = BinaryUtils.readU32LE(bytes, 0).toInt()
                val type = value ushr 16
                val option = if (value and 0x8000 != 0) 1 else 0
                val nameLen = BinaryUtils.readU32LE(bytes, 4).toInt()
                if (nameLen < 0 || nameLen > MAX_NAME_BYTES) return null
                val headerLen = 8 + if (option != 0) 4 else 0
                if (headerLen + nameLen > bytes.size) return null
                val name = String(bytes, headerLen, nameLen, Charsets.UTF_8)
                val data = bytes.copyOfRange(headerLen + nameLen, bytes.size)
                return Record(type, option, name, data)
            }
        }
    }

    /** 解析结果。 */
    class Parsed(
        /** 尾部块内声明的版本号（已按 `getVersion()` 的规则把 0 折算成 106） */
        val version: Int,
        /** 记录表（顺序与载荷在文件中的顺序一致） */
        val records: List<Record>,
        /** 尾部块在文件中的起始偏移 */
        val blockStart: Int
    ) {
        /** 载荷区起点（即尾部块之前紧邻的一段）；不可能小于 0 */
        val payloadAreaStart: Int get() = 0

        /** 取第一条指定类型的记录；没有返回 null。 */
        fun firstOf(type: Int): Record? = records.firstOrNull { it.type == type }
    }

    /**
     * 从整文件字节解析 SEF 尾部块；不是三星动态照片时返回 null。
     *
     * 只依赖「文件尾 4 字节 == SEFT」与「块起点 4 字节 == SEFH」两条自校验条件，
     * 不信任 `drsp`/`drsl`（读取业务视频改用 ftyp 扫描，见 `SamsungPlugin.read`）。
     */
    fun parse(data: ByteArray): Parsed? {
        val n = data.size
        if (n < BLOCK_OVERHEAD + RECORD_SIZE) return null
        if (!BinaryUtils.arrayEquals(data, n - TRAILER.size, TRAILER)) return null

        val blockSizeMinus8 = BinaryUtils.readU32LE(data, n - 8)
        if (blockSizeMinus8 <= 0L || blockSizeMinus8 > Int.MAX_VALUE) return null
        val blockSize = blockSizeMinus8.toInt() + 8
        val blockStart = n - blockSize
        if (blockStart < 0) return null
        if (!BinaryUtils.arrayEquals(data, blockStart, HEADER)) return null

        val rawVersion = BinaryUtils.readU32LE(data, blockStart + 4).toInt()
        val count = BinaryUtils.readU32LE(data, blockStart + 8).toInt()
        if (count <= 0 || count > MAX_RECORDS) return null
        if (blockStart + BLOCK_OVERHEAD - 8 + count * RECORD_SIZE > n) return null

        val version = if (rawVersion == 0) DEFAULT_VERSION else rawVersion
        val records = ArrayList<Record>(count)
        for (i in 0 until count) {
            val off = blockStart + 12 + i * RECORD_SIZE
            val drsp = BinaryUtils.readU32LE(data, off + 4).toInt()
            val drsl = BinaryUtils.readU32LE(data, off + 8).toInt()
            // 载荷起点 = 块起点 - drsp（SefParser.unpack: pos = fileSize - (packetSize + drsp)）
            val start = blockStart - drsp
            val payload = if (drsl > 0 && start >= 0 && start + drsl <= n) {
                data.copyOfRange(start, start + drsl)
            } else {
                ByteArray(0)
            }
            val rec = Record.decode(payload) ?: continue
            records.add(rec)
        }
        if (records.isEmpty()) return null
        return Parsed(version, records, blockStart)
    }

    /**
     * 生成尾部块（含记录表），对应 `TailData.pack`。
     *
     * 载荷在文件中的排布由调用方负责：**紧贴尾部块之前、顺序与 [records] 一致**。
     * 本方法只产出尾部块本身。
     */
    fun buildTail(version: Int, records: List<Record>): ByteArray {
        if (records.isEmpty()) return ByteArray(0)
        val count = records.size
        val total = records.sumOf { it.size }
        val block = ByteArray(BLOCK_OVERHEAD + count * RECORD_SIZE)
        System.arraycopy(HEADER, 0, block, 0, HEADER.size)
        BinaryUtils.writeU32LE(block, 4, (if (version == 0) DEFAULT_VERSION else version).toLong())
        BinaryUtils.writeU32LE(block, 8, count.toLong())

        var remaining = total
        for (i in 0 until count) {
            val rec = records[i]
            val off = 12 + i * RECORD_SIZE
            // value 与载荷头内的 value 同源
            BinaryUtils.writeU32LE(block, off, rec.value.toLong() and 0xFFFFFFFFL)
            // drsp = 载荷起点到块起点的距离 = 本记录及其后所有载荷之和
            BinaryUtils.writeU32LE(block, off + 4, remaining.toLong() and 0xFFFFFFFFL)
            BinaryUtils.writeU32LE(block, off + 8, rec.size.toLong() and 0xFFFFFFFFL)
            remaining -= rec.size
        }
        // 块内总长字段 = 12 + 12*count
        BinaryUtils.writeU32LE(block, 12 + count * RECORD_SIZE, (12 + count * RECORD_SIZE).toLong())
        System.arraycopy(TRAILER, 0, block, 16 + count * RECORD_SIZE, TRAILER.size)
        return block
    }

    /** 把载荷区与尾部块拼成完整的 SEF 段（不含前面的 JPEG）。 */
    fun buildSegment(version: Int, records: List<Record>): ByteArray {
        val payloads = records.map { it.encode() }
        val tail = buildTail(version, records)
        val out = ByteArray(payloads.sumOf { it.size } + tail.size)
        var pos = 0
        for (p in payloads) {
            System.arraycopy(p, 0, out, pos, p.size)
            pos += p.size
        }
        System.arraycopy(tail, 0, out, pos, tail.size)
        return out
    }
}
