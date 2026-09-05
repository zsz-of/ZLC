package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import java.io.File
import java.io.IOException

/**
 * 拆解输出（仅作目标格式，不作来源识别）：把动态照片拆成「照片 + 视频」两个文件。
 * - 照片：主 JPEG（去除 motion XMP，保留 EXIF），扩展名按实际内容 .jpg / .heic
 * - 视频：纯 MP4 流，扩展名 .mp4
 * 图片与视频保持同一 stem（同名的 jpg+mp4），便于相册按组查看与二次处理。
 */
internal class ExtractPlugin : FormatPlugin() {
    override val name: String = "extract"
    override val display: String = "拆解（照片+视频）"

    override fun detect(path: String): Int = 0 // 拆解只是输出格式，从不作为来源识别

    override fun read(path: String, log: (String, String, String) -> Unit): LivePhotoAsset {
        throw IOException("拆解是输出格式，不应作为输入格式读取")
    }

    override fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?>
    ): MutableList<String> {
        // 照片输出：保留 HDR(GainMap)。
        // 只移除动态照片标记（MotionPhoto/MicroVideo/厂商私有 LivePhoto 字段），
        // 保留 hdrgm:* 与 Container:Directory 结构；存在 GainMap JPEG 时拼回主图之后，
        // 输出即一张可正常显示的 Ultra HDR 静态照片（SDR 设备看 SDR，HDR 设备高光不丢）。
        val primary = asset.primaryJpeg
        val isJpeg = primary.size >= 2 && primary[0] == 0xFF.toByte() && primary[1] == 0xD8.toByte()
        val photo: ByteArray = if (!isJpeg) {
            primary // HEIC 等原样保留（无独立 GainMap 处理路径）
        } else if (asset.gainmapJpeg != null) {
            JpegUtil.sanitizeStillPhoto(primary) + asset.gainmapJpeg!!
        } else {
            JpegUtil.sanitizeStillPhoto(primary)
        }
        val photoExt = if (isJpeg) "jpg" else "heic"
        val photoPath = File(outDir, "$stem.$photoExt").path
        writeBytes(photoPath, photo)

        // 视频输出：纯 MP4 流字节直写，不重编码 —— 视频码流内 HDR10/HDR10+ 的
        // VUI/SEI 元数据随样本数据原样保留，不会丢失。
        val videoPath = File(outDir, "$stem.mp4").path
        writeBytes(videoPath, asset.videoMp4)

        log("info", "拆解输出：$stem.$photoExt（照片 ${photo.size}B，HDR=${asset.gainmapJpeg != null}）+ " +
            "$stem.mp4（视频 ${asset.videoMp4.size}B）", "拆解")
        return mutableListOf(photoPath, videoPath)
    }
}
