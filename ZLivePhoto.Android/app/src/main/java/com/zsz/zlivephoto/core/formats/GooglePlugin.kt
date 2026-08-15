package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.BinaryUtils
import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.XmpTemplate
import java.io.File
import java.io.FileInputStream

/**
 * Google Motion Photo 标准格式（含旧版 MicroVideo 读取）。
 */
internal class GooglePlugin : FormatPlugin() {
    override val name: String = "google"
    override val display: String = "Google Motion Photo（标准格式）"

    companion object {
        /** 从文件头部直接定位 XMP 文本（检测用，容忍截断）。 */
        internal fun sniffXmp(path: String, limit: Int = 2 * 1024 * 1024): String {
            return try {
                val f = File(path)
                val fileSize = f.length().toInt()
                val bufLen = minOf(limit, fileSize)
                if (bufLen < 2) return ""
                val buffer = ByteArray(bufLen)
                FileInputStream(path).use { fis ->
                    var read = 0
                    while (read < bufLen) {
                        val n = fis.read(buffer, read, bufLen - read)
                        if (n < 0) break
                        read += n
                    }
                    if (read < 2 || buffer[0] != 0xFF.toByte() || buffer[1] != 0xD8.toByte()) return@use ""
                    val head = buffer.copyOfRange(0, read)
                    val idx = BinaryUtils.indexOf(head, JpegUtil.xmpApp1Prefix)
                    if (idx == -1) return@use ""
                    val endMarker = "</x:xmpmeta>".toByteArray(Charsets.US_ASCII)
                    val tail = head.copyOfRange(idx, head.size)
                    val end = BinaryUtils.indexOf(tail, endMarker)
                    if (end == -1) return@use ""
                    val length = idx + end + 12
                    return@use String(head.copyOfRange(idx, idx + length), Charsets.UTF_8)
                }
            } catch (e: Exception) {
                ""
            }
        }
    }

    override fun detect(path: String): Int {
        val info = XmpTemplate.parseMotionXmp(sniffXmp(path))
        if (info.isMotion && !info.isLegacyMicro && !info.hasOplus) return 90
        if (info.isLegacyMicro) return 80
        return 0
    }

    override fun read(path: String, log: (String, String, String) -> Unit): LivePhotoAsset {
        log("info", "按 Google Motion Photo 解析", "Google")
        return EmbeddedReader.readEmbedded(path, name, log)
    }

    override fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?>
    ): MutableList<String> {
        val video = asset.videoMp4
        val pts = asset.effectivePtsUs()
        val xmp = XmpTemplate.buildGoogleXmp(pts, asset.gainmapLength, video.size)
        val primary = JpegUtil.replaceOrInsertXmp(asset.primaryJpeg, xmp)

        val gainmapLen = asset.gainmapJpeg?.size ?: 0
        val output = ByteArray(primary.size + gainmapLen + video.size)
        System.arraycopy(primary, 0, output, 0, primary.size)
        asset.gainmapJpeg?.let { System.arraycopy(it, 0, output, primary.size, it.size) }
        System.arraycopy(video, 0, output, primary.size + gainmapLen, video.size)

        // Google 规范：文件名以 MP 结尾
        var stemFinal = stem
        val useSuffix = options["google_mp_suffix"] as? Boolean ?: true
        if (useSuffix && !stemFinal.endsWith("MP", ignoreCase = true)) {
            stemFinal += "_MP"
        }

        val outPath = File(outDir, "$stemFinal.jpg").path
        writeBytes(outPath, output)
        log("info", "写出 Google 格式：${File(outPath).name}（图像 ${primary.size}B + 视频 ${video.size}B）", "Google")
        return mutableListOf(outPath)
    }
}
