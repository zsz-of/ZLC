package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.BinaryUtils
import com.zsz.zlivephoto.core.FooterUtil
import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.Mp4Util
import com.zsz.zlivephoto.core.XmpTemplate
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.round

/**
 * vivo 双文件动态照片格式（IMG_xxx.jpg + IMG_xxx.mp4）。
 */
internal class VivoPlugin : FormatPlugin() {
    override val name: String = "vivo"
    override val display: String = "vivo 动态照片（JPG + MP4 双文件）"

    private val vivoVersion: Int = 2107

    private fun siblingMp4(path: String): String? {
        val file = File(path)
        val parent = file.parentFile
        val mp4 = if (parent != null) File(parent, "${file.nameWithoutExtension}.mp4").path
                  else "${file.nameWithoutExtension}.mp4"
        return if (File(mp4).exists()) mp4 else null
    }

    private fun buildJson(imageTime: Long, liveId: String): ByteArray =
        FooterUtil.buildFooterJson(linkedMapOf(
            "com.android.camera.imageTime" to imageTime,
            "com.android.camera.livephoto" to liveId,
            "version" to vivoVersion
        ))

    private fun buildUuidBox(jsonBytes: ByteArray, liveId: String): ByteArray {
        val footer = FooterUtil.buildFooter(jsonBytes, liveId, FooterUtil.vivoPrefix)
        val payload = ByteArray(Mp4Util.vivoUuid.size + footer.size)
        System.arraycopy(Mp4Util.vivoUuid, 0, payload, 0, Mp4Util.vivoUuid.size)
        System.arraycopy(footer, 0, payload, Mp4Util.vivoUuid.size, footer.size)

        val result = ByteArray(8 + payload.size)
        BinaryUtils.writeU32BE(result, 0, (payload.size + 8).toLong())
        // "uuid" = 0x75 0x75 0x69 0x64
        result[4] = 0x75.toByte()
        result[5] = 0x75.toByte()
        result[6] = 0x69.toByte()
        result[7] = 0x64.toByte()
        System.arraycopy(payload, 0, result, 8, payload.size)
        return result
    }

    override fun detect(path: String): Int {
        if (!path.endsWith(".jpg", ignoreCase = true) && !path.endsWith(".jpeg", ignoreCase = true))
            return 0

        val xmp = GooglePlugin.sniffXmp(path)
        val info = XmpTemplate.parseMotionXmp(xmp)
        if (info.isMotion) return 0 // 内嵌式不归 vivo 管

        return try {
            val f = File(path)
            val size = f.length()
            FileInputStream(path).use { fs ->
                fs.skip(maxOf(0L, size - 8192))
                val tail = ByteArray(minOf(8192L, size).toInt())
                var read = 0
                while (read < tail.size) {
                    val n = fs.read(tail, read, tail.size - read)
                    if (n < 0) break
                    read += n
                }
                val footer = FooterUtil.parseFooter(tail.copyOfRange(0, read))
                if (footer?.livephotoId == null) return 0
                if (siblingMp4(path) != null) 90 else 40
            }
        } catch (e: Exception) {
            0
        }
    }

    override fun read(path: String, log: (String, String, String) -> Unit): LivePhotoAsset {
        log("info", "按 vivo 双文件动态照片解析", "vivo")
        val data = readBytes(path)
        val footer = FooterUtil.parseFooter(data)
        if (footer?.livephotoId == null)
            throw IOException("JPG 尾部未找到 vivo livephoto 标记")

        val liveId = footer.livephotoId!!
        val mp4Path = siblingMp4(path)
            ?: throw IOException("缺少伴生视频文件：${File(path).nameWithoutExtension}.mp4")

        // JPG 主体（去除 footer）→ 拆 Primary / GainMap
        val body = data.copyOfRange(0, footer.footerStart)
        val (jpegs, _) = JpegUtil.splitJpegs(body)
        if (jpegs.isEmpty()) throw IOException("JPG 主体解析失败")
        val primary = jpegs[0]
        val gainmap = if (jpegs.size > 1) jpegs[1] else null

        // MP4：剥离末尾 vivo uuid box
        val mp4Raw = readBytes(mp4Path)
        val mp4Footer = FooterUtil.parseFooter(mp4Raw)
        var imageTime: Long? = footer.imageTime
        if (mp4Footer != null) {
            if (imageTime == null) imageTime = mp4Footer.imageTime
            val mp4Id = mp4Footer.livephotoId
            if (mp4Id != null && mp4Id != liveId) {
                log("warning", "JPG 与 MP4 的 livephoto ID 不一致：$liveId / $mp4Id", "vivo")
            }
        }

        val video = Mp4Util.stripVivoUuid(mp4Raw)
        if (!Mp4Util.hasFtyp(video))
            throw IOException("伴生 MP4 无效（缺少 ftyp box）")

        val asset = LivePhotoAsset(
            primaryJpeg = primary,
            gainmapJpeg = gainmap,
            videoMp4 = video,
            sourceFormat = name,
        )
        asset.livephotoId = liveId
        asset.imageTime = imageTime
        asset.videoInfo = Mp4Util.getTrackInfo(video) ?: mutableMapOf()

        // 由 imageTime（帧序号）反推封面时间戳
        val imageTimeVal = asset.imageTime
        val fps = asset.videoInfo["fps"] as? Double
        if (imageTimeVal != null && fps != null && fps > 0.0) {
            asset.presentationTsUs = round(imageTimeVal.toDouble() / fps * 1_000_000.0).toLong()
        }
        return asset
    }

    override fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?>
    ): MutableList<String> {
        val liveId = asset.livephotoId ?: FooterUtil.generateLivephotoId()
        val imageTime = asset.effectiveImageTime()

        // JPG：vivo XMP（无 motion 标签）+ vivo footer
        val xmp = XmpTemplate.buildVivoXmp(asset.gainmapLength)
        val primary = JpegUtil.replaceOrInsertXmp(asset.primaryJpeg, xmp)
        val jpgJson = buildJson(imageTime, liveId)
        val jpgFooter = FooterUtil.buildFooter(jpgJson, liveId, FooterUtil.vivoPrefix)
        val gainmapLen = asset.gainmapJpeg?.size ?: 0
        val jpgOut = ByteArray(primary.size + gainmapLen + jpgFooter.size)
        var pos = 0
        System.arraycopy(primary, 0, jpgOut, pos, primary.size); pos += primary.size
        asset.gainmapJpeg?.let {
            System.arraycopy(it, 0, jpgOut, pos, it.size); pos += it.size
        }
        System.arraycopy(jpgFooter, 0, jpgOut, pos, jpgFooter.size)

        // MP4：纯视频流 + 末尾 uuid box
        val video = Mp4Util.stripVivoUuid(asset.videoMp4)
        val mp4Json = buildJson(imageTime, liveId)
        val uuidBox = buildUuidBox(mp4Json, liveId)
        val mp4Out = ByteArray(video.size + uuidBox.size)
        System.arraycopy(video, 0, mp4Out, 0, video.size)
        System.arraycopy(uuidBox, 0, mp4Out, video.size, uuidBox.size)

        val jpgPath = File(outDir, "$stem.jpg").path
        val mp4Path = File(outDir, "$stem.mp4").path
        writeBytes(jpgPath, jpgOut)
        writeBytes(mp4Path, mp4Out)
        log("info", "写出 vivo 格式：$stem.jpg + $stem.mp4（livephoto ID: $liveId）", "vivo")
        return mutableListOf(jpgPath, mp4Path)
    }
}
