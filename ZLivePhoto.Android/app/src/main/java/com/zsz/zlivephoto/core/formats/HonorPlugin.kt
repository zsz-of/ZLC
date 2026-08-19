package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.Mp4Util
import com.zsz.zlivephoto.core.XmpTemplate
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 荣耀动态照片格式（Honor Motion Photo）。
 * 结构：JPEG(含 Google Container XMP) + MP4(ftyp→moov→free→mdat[large size]) + uuid box(extend_type_matrix + EIS JSON) + 60B 尾部(v2_fXX + 比例 + LIVE_ID)。
 * XMP 不含 MotionPhoto 标签，仅靠 Container Directory + 文件尾 LIVE_ 标记识别。
 */
internal class HonorPlugin : FormatPlugin() {
    override val name: String = "honor"
    override val display: String = "荣耀动态照片"

    companion object {
        private const val TAIL_SEGMENT_LEN = 20
        private const val DEFAULT_VERSION = "v2_f01"
        private const val DEFAULT_RATIO = "100:1000"
        private val HONOR_EXTEND_TYPE = "extend_type_matrix".toByteArray()
    }

    override fun detect(path: String): Int {
        try {
            // 必须是 JPEG
            RandomAccessFile(path, "r").use { raf ->
                if (raf.length() < 64) return 0
                val head = ByteArray(2)
                raf.readFully(head)
                if (head[0] != 0xFF.toByte() || head[1] != 0xD8.toByte()) return 0

                // 尾部必须有 LIVE_ 标记（60B 固定尾部的最后 20B 段）
                raf.seek(raf.length() - 32)
                val tail = ByteArray(32)
                raf.readFully(tail)
                if (String(tail).indexOf("LIVE_") < 0) return 0
            }

            // XMP 必须有 Google Container 但不含 MotionPhoto/MicroVideo/oplus
            val xmpText = GooglePlugin.sniffXmp(path)
            val info = XmpTemplate.parseMotionXmp(xmpText)
            if (info.isMotion || info.hasOplus) return 0 // 有 MotionPhoto → 不是荣耀

            // 有 Container Directory 且无 MotionPhoto → 荣耀强特征
            if (info.items.isNotEmpty()) return 92

            // 无 XMP 但有 LIVE_ 尾部（部分非 HDR 样本可能无 XMP）
            return 80
        } catch (e: Exception) {
            return 0
        }
    }

    override fun read(path: String, log: (String, String, String) -> Unit): LivePhotoAsset {
        log("info", "按荣耀动态照片解析（MP4 large size + uuid EIS matrix）", "荣耀")
        return EmbeddedReader.readEmbedded(path, name, log)
    }

    override fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?>
    ): MutableList<String> {
        val video = asset.videoMp4
        // XMP：Adobe XMP Core 5.1.2 + Google Container（无 MotionPhoto 标签，无视频项）
        val xmp = XmpTemplate.buildHonorXmp(asset.gainmapLength)
        var primary = JpegUtil.replaceOrInsertXmp(asset.primaryJpeg, xmp)

        // uuid box：含 extend_type_matrix + EIS JSON 数组
        val uuidBox = buildHonorUuidBox(asset)

        // 60B 固定尾部
        val tail = buildHonorTail(asset)

        // 拼接：JPEG(+GainMap) + MP4 + uuid box + tail
        val baos = ByteArrayOutputStream(
            primary.size + (asset.gainmapJpeg?.size ?: 0) + video.size + uuidBox.size + tail.size
        )
        baos.write(primary)
        asset.gainmapJpeg?.let { baos.write(it) }
        baos.write(video)
        baos.write(uuidBox)
        baos.write(tail)

        val outPath = File(outDir, "$stem.jpg").path
        writeBytes(outPath, baos.toByteArray())
        log("info", "写出荣耀格式：${File(outPath).name}" +
            "（图像 ${primary.size}B + 视频 ${video.size}B + EIS ${uuidBox.size}B）", "荣耀")
        return mutableListOf(outPath)
    }

    // ---------------------------------------------------------- uuid box 构建

    /**
     * 构建荣耀 uuid box：[size 4B]['uuid' 4B]['extend_type_matrix' 17B][EIS JSON 数组]。
     * 每帧生成默认单位矩阵（从 VideoInfo 取宽高，无则用 0）。
     */
    private fun buildHonorUuidBox(asset: LivePhotoAsset): ByteArray {
        val width = (asset.videoInfo["width"] as? Int) ?: 0
        val height = (asset.videoInfo["height"] as? Int) ?: 0
        var frameCount = (asset.videoInfo["frame_count"] as? Long) ?: 1L
        if (frameCount < 1) frameCount = 1

        // 单帧 matrix JSON 模板（单位矩阵）
        val matrixJson = "{\"decayGain\":0.0,\"eis3x3Matrix\":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0]," +
            "\"mctf3x3Matrix\":[1.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,1.0]," +
            "\"srcDstWh\":[$width,$height,$width,$height]}" +
            "\\n" // 注意：样本中是字面 \n（两个字符），非真换行

        val sb = StringBuilder("[")
        for (i in 1..frameCount) {
            if (i > 1) sb.append(',')
            sb.append("{\"frameNum\":").append(i).append(",\"matrixInfo\":\"").append(matrixJson).append("\"}")
        }
        sb.append(']')

        val jsonBytes = sb.toString().toByteArray(Charsets.UTF_8)
        // size = 4(size) + 4(uuid) + 17(extend_type_matrix) + json.size
        val totalSize = 8 + HONOR_EXTEND_TYPE.size + jsonBytes.size

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(totalSize)
        buf.put("uuid".toByteArray())
        buf.put(HONOR_EXTEND_TYPE)
        buf.put(jsonBytes)
        return buf.array()
    }

    // ---------------------------------------------------------- 60B 尾部构建

    /**
     * 60B 固定尾部：[v2_fXX 20B][NNN:NNNN 20B][LIVE_XXXXXXXX 20B]，空格填充。
     */
    private fun buildHonorTail(asset: LivePhotoAsset): ByteArray {
        val liveId = "LIVE_" + generateLiveId(asset)
        val version = (asset.extras["honor_version"] as? String) ?: DEFAULT_VERSION
        val ratio = (asset.extras["honor_ratio"] as? String) ?: DEFAULT_RATIO

        val tail = ByteArray(TAIL_SEGMENT_LEN * 3)
        fillSegment(tail, 0, version)
        fillSegment(tail, TAIL_SEGMENT_LEN, ratio)
        fillSegment(tail, TAIL_SEGMENT_LEN * 2, liveId)
        return tail
    }

    private fun fillSegment(buf: ByteArray, offset: Int, text: String) {
        // 先全填空格
        for (i in 0 until TAIL_SEGMENT_LEN) buf[offset + i] = ' '.code.toByte()
        val bytes = text.toByteArray(Charsets.US_ASCII)
        val len = minOf(bytes.size, TAIL_SEGMENT_LEN)
        System.arraycopy(bytes, 0, buf, offset, len)
    }

    /** 生成 9 位 LIVE_ID。优先复用源 ID，否则基于时间戳生成稳定值。 */
    private fun generateLiveId(asset: LivePhotoAsset): String {
        (asset.extras["honor_live_id"] as? String)?.let { if (it.length == 9) return it }
        val seed = if (asset.effectivePtsUs() >= 0) asset.effectivePtsUs() else System.currentTimeMillis()
        val rnd = java.util.Random(seed)
        return (rnd.nextInt(900_000_000) + 100_000_000).toString()
    }
}
