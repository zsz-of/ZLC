package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.BinaryUtils
import com.zsz.zlivephoto.core.CoreText
import com.zsz.zlivephoto.core.ExifUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * 努比亚（Nubia / 红魔）动态照片格式（`nubiaVpfile`）。
 *
 * 逆向结论（`cn.nubia.gallery3d`，neovision10.0 V11.0.70，见
 * `.agents/research/nubia-gallery-requirements.md`）：
 *
 * - **识别**与 XMP 完全无关：JPEG 的 EXIF `UserComment`(0x9286) 里含子串 `"livep"`
 *   → `LocalImage.getExifSourceType()` → `setSourceType(0xc)` = `TYPE_DYNAMICPHOTO`。
 *   相机侧闭环：`cn/nubia/camera/aa/b.smali:280` 写 `"livep"`，键由 `<clinit>` 推出即 0x9286。
 * - **视频定位**靠固定 30 字节尾部：
 *   `[JPEG][0x00][MP4][BE64(JPEG 长度 L)][UTF-16BE "nubiaVpfile"(22B)]`
 *   播放器按 `setDataSource(fd, L+1, 总长-L-30)` 的字节区间播放（长度比真实视频多 1，
 *   即包含长度字段的首字节——这是相册自己的算法，我们读回时按纯 MP4 区间取）。
 * - 尾部与 `UserComment` **必须双写**，缺一即失败：
 *   只写尾部 → 能播但不被识别为动态照片；只写 `livep` → 被识别但长按播放
 *   `isNubiaVpFile()` 返回 false 而失败。
 * - 写入时 `UserComment` **不得**含 `aper`/`bper`/`image3d`（`LocalImage` 是 `else if`
 *   优先级链，其它特殊类型会抢占）。
 */
internal class NubiaPlugin : FormatPlugin() {
    override val name: String = "nubia"
    override val displayRes: Int = R.string.fmt_nubia

    companion object {
        /** 尾部魔数：UTF-16BE 编码的 `"nubiaVpfile"`（22 字节，**不是** ASCII）。 */
        private val MAGIC: ByteArray = "nubiaVpfile".toByteArray(Charsets.UTF_16BE)

        /** 尾部固定长度：BE64 的 JPEG 长度字段(8) + 魔数(22)。 */
        private const val TAIL_LEN = 8 + 22

        /** JPEG 与 MP4 之间的 1 字节 0x00 填充。 */
        private const val FILL_LEN = 1

        /** UserComment 里出现该子串即被努比亚相册判为动态照片。 */
        private const val LIVE_MARK = "livep"
    }

    override fun detect(path: String): Int {
        val file = File(path)
        val total = file.length()
        if (total < 64 || total < (TAIL_LEN + FILL_LEN + 2).toLong()) return 0
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val head = ByteArray(2)
                raf.readFully(head)
                if (head[0] != 0xFF.toByte() || head[1] != 0xD8.toByte()) return 0

                // 尾部魔数（UTF-16BE）
                raf.seek(total - MAGIC.size)
                val magic = ByteArray(MAGIC.size)
                raf.readFully(magic)
                if (!BinaryUtils.arrayEquals(magic, 0, MAGIC)) return 0

                // JPEG 长度字段必须合法，且其后必须是 0x00 填充字节
                raf.seek(total - TAIL_LEN)
                val lenBytes = ByteArray(8)
                raf.readFully(lenBytes)
                val jpegLen = BinaryUtils.readU64BE(lenBytes, 0)
                if (jpegLen <= 0L || jpegLen + FILL_LEN >= total - TAIL_LEN) return 0
                raf.seek(jpegLen)
                if (raf.read() != 0) return 0

                95
            }
        } catch (e: Exception) {
            0
        }
    }

    override fun read(path: String, log: (String, String, String) -> Unit): LivePhotoAsset {
        val data = readBytes(path)
        val total = data.size
        if (total < TAIL_LEN + FILL_LEN + 2) throw IOException(CoreText.of(R.string.fmt_err_nubia_too_short))
        if (!BinaryUtils.arrayEquals(data, total - MAGIC.size, MAGIC)) {
            throw IOException(CoreText.of(R.string.fmt_err_nubia_no_magic))
        }
        val jpegLen = BinaryUtils.readU64BE(data, total - TAIL_LEN)
        if (jpegLen <= 0L || jpegLen + FILL_LEN >= total - TAIL_LEN) {
            throw IOException(CoreText.of(R.string.fmt_err_nubia_jpeg_len, jpegLen))
        }
        val l = jpegLen.toInt()
        if (data[l] != 0.toByte()) throw IOException(CoreText.of(R.string.fmt_err_nubia_no_padding))
        val videoStart = l + FILL_LEN
        val videoEnd = total - TAIL_LEN
        if (videoEnd <= videoStart) throw IOException(CoreText.of(R.string.fmt_err_nubia_empty_video))

        log("info", "按努比亚动态照片解析（nubiaVpfile 尾部）", "努比亚")
        return LivePhotoAsset(
            primaryJpeg = data.copyOfRange(0, l),
            gainmapJpeg = null,
            videoMp4 = data.copyOfRange(videoStart, videoEnd),
            sourceFormat = name
        )
    }

    override fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?>
    ): MutableList<String> {
        // ① UserComment 标记（识别必需）；② 30B 尾部（视频定位必需）—— 缺一即失败
        val primary = ExifUtil.upsertUserComment(asset.primaryJpeg, LIVE_MARK)
        val video = asset.videoMp4

        val out = ByteArray(primary.size + FILL_LEN + video.size + TAIL_LEN)
        var p = 0
        System.arraycopy(primary, 0, out, p, primary.size); p += primary.size
        out[p] = 0; p += FILL_LEN
        System.arraycopy(video, 0, out, p, video.size); p += video.size
        BinaryUtils.writeU64BE(out, p, primary.size.toLong()); p += 8
        System.arraycopy(MAGIC, 0, out, p, MAGIC.size)

        val outPath = File(outDir, "$stem.jpg").path
        writeBytes(outPath, out)
        log(
            "info",
            "写出努比亚格式：${File(outPath).name}" +
                "（JPEG ${primary.size}B + 填充 1B + 视频 ${video.size}B + 尾部 ${TAIL_LEN}B，" +
                "UserComment 含 \"$LIVE_MARK\"）",
            "努比亚"
        )
        return mutableListOf(outPath)
    }
}
