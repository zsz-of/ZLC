package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.R
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
 * 荣耀动态照片格式（Honor Live Photo）。
 * 结构：JPEG(含 Google Container XMP) + MP4 + uuid box(EIS JSON) + 60B 尾部。
 *
 * 60B 尾部三段（每段 20B，空格填充）由荣耀相册 `com.hihonor.photos` 的
 * `Lcom/hihonor/gallery/livephoto/LiveUtils;` 逐行逆向确定：
 * ```
 * [len-60, len-40) = "vX_fYY"      // VERSION_TAG="v2_"，PATTERN ^[vV](\d+)_[fF](\d+)
 * [len-40, len-20) = 播放信息串     // 按 ':' split，默认 ["0","500"]
 * [len-20, len  ) = "LIVE_<N>"     // N 必须使 (len-40)-N 精确落在 MP4 起点
 * ```
 * 证据：
 * - `LiveUtils.getVideoOffset(String)`：读最后 20B → 必须 `startsWith("LIVE_")` →
 *   `Long.parseLong(split("_")[1])`，**当作视频长度**（不是随机 ID）；
 * - `SpecialMediaUtils.extractLivePhoto(...)`：`videoOffset=(len-40)-N`，再用
 *   `QueryVideoInfoUtils.queryFrameRate/queryWidthAndHeight(path, videoOffset, N)` 真实解码，
 *   偏移不对就取不到帧率/宽高，扫描入库的 `hn_livephoto_decode_info` 为空 → 相册不认；
 * - `LiveUtils.getVersionAndFrameNum(String)`：`seek(len-60)` 读 20B 解析版本/帧号。
 *
 * uuid box 的 usertype 也必须是荣耀的 `VIDEO_USERTYPE = " honor.org.video"`（16B），
 * 否则 `LiveUtils.readUUIDBox` 会判 `this is not target uuid box`。
 */
internal class HonorPlugin : FormatPlugin() {
    override val name: String = "honor"
    override val displayRes: Int = R.string.fmt_honor

    companion object {
        private const val TAIL_SEGMENT_LEN = 20
        private const val DEFAULT_VERSION = "v2_f01"
        private const val DEFAULT_RATIO = "100:1000"

        /**
         * uuid box 的 16 字节 usertype：荣耀 `LiveUtils.VIDEO_USERTYPE` 原文
         * （**含前导空格**，共 16 字节；`COVER_USERTYPE` 为 `" honor.org.cover"`）。
         * 读侧 `readUUIDBox(MediaItem, userType)` 会逐字节比对，写错就取不到盒。
         */
        private val HONOR_VIDEO_USERTYPE = " honor.org.video".toByteArray(Charsets.US_ASCII)
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

        // uuid box：usertype 必须是荣耀的 " honor.org.video"，payload 为逐帧 EIS 矩阵 JSON
        val uuidBox = buildHonorUuidBox(asset)

        // 60B 固定尾部。第三段 LIVE_<N> 里的 N 不是随机 ID，而是荣耀用来定位视频的长度：
        // 荣耀侧 videoOffset = (len - 40) - N，必须在 MP4 起点，故按真实布局反算。
        val videoStart = primary.size + (asset.gainmapJpeg?.size ?: 0)
        val totalLen = videoStart + video.size + uuidBox.size + TAIL_SEGMENT_LEN * 3
        val liveLength = totalLen - 40 - videoStart
        val tail = buildHonorTail(asset, liveLength)

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
            "（图像 ${primary.size}B + 视频 ${video.size}B + EIS ${uuidBox.size}B，" +
            "LIVE_$liveLength）", "荣耀")
        return mutableListOf(outPath)
    }

    // ---------------------------------------------------------- uuid box 构建

    /**
     * 构建荣耀 uuid box：[size 4B]['uuid' 4B][16B usertype][EIS JSON 数组]。
     * usertype 必须是荣耀 `LiveUtils.VIDEO_USERTYPE`（`" honor.org.video"`，含前导空格共 16B），
     * 否则 `readUUIDBox` 逐字节比对失败、取不到视频盒。每帧生成默认单位矩阵（宽高取自 VideoInfo，无则 0）。
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
        // size = 4(size) + 4(uuid) + 16(usertype) + json.size
        val totalSize = 8 + HONOR_VIDEO_USERTYPE.size + jsonBytes.size

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(totalSize)
        buf.put("uuid".toByteArray())
        buf.put(HONOR_VIDEO_USERTYPE)
        buf.put(jsonBytes)
        return buf.array()
    }

    // ---------------------------------------------------------- 60B 尾部构建

    /**
     * 60B 固定尾部：[v2_fXX 20B][播放信息 20B][LIVE_<N> 20B]，空格填充。
     *
     * @param liveLength 第三段的 N：荣耀侧 `videoOffset = (len - 40) - N` 必须落在 MP4 起点，
     *   因此 N 由真实布局反算（= 视频起点到 len-40 的字节数），**不是**随机 ID。
     */
    private fun buildHonorTail(asset: LivePhotoAsset, liveLength: Int): ByteArray {
        val version = (asset.extras["honor_version"] as? String) ?: DEFAULT_VERSION
        val ratio = (asset.extras["honor_ratio"] as? String) ?: DEFAULT_RATIO

        val tail = ByteArray(TAIL_SEGMENT_LEN * 3)
        fillSegment(tail, 0, version)
        fillSegment(tail, TAIL_SEGMENT_LEN, ratio)
        fillSegment(tail, TAIL_SEGMENT_LEN * 2, "LIVE_$liveLength")
        return tail
    }

    private fun fillSegment(buf: ByteArray, offset: Int, text: String) {
        // 先全填空格
        for (i in 0 until TAIL_SEGMENT_LEN) buf[offset + i] = ' '.code.toByte()
        val bytes = text.toByteArray(Charsets.US_ASCII)
        val len = minOf(bytes.size, TAIL_SEGMENT_LEN)
        System.arraycopy(bytes, 0, buf, offset, len)
    }
}
