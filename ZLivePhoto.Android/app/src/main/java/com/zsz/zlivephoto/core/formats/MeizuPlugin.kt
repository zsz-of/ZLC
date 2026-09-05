package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.XmpTemplate
import java.io.File

/**
 * 魅族动态照片格式（Meizu Motion Photo）。
 * 结构 = Google Motion Photo 结构（JPEG + MP4 裸拼）+ MZCamera 私有 XMP 字段；
 * XMP 使用 Camera: 前缀（非 GCamera:）声明 MotionPhoto 标签，
 * Container 结构与 Google 一致（Primary + MotionPhoto 视频项）。
 */
internal class MeizuPlugin : FormatPlugin() {
    override val name: String = "meizu"
    override val display: String = "魅族动态照片"

    override fun detect(path: String): Int {
        val info = XmpTemplate.parseMotionXmp(GooglePlugin.sniffXmp(path))
        return if (info.hasMeizu) 95 else 0
    }

    override fun read(path: String, log: (String, String, String) -> Unit): LivePhotoAsset {
        log("info", "按魅族动态照片解析", "魅族")
        val asset = EmbeddedReader.readEmbedded(path, name, log)

        // 保留 MZCamera 私有字段（同格式转出时复用，避免丢失厂商元数据）
        val info = XmpTemplate.parseMotionXmp(GooglePlugin.sniffXmp(path))
        info.mzCaptureMode?.let { asset.extras["mz_capture_mode"] = it }
        info.mzIsHdrActive?.let { asset.extras["mz_is_hdr_active"] = it }
        info.mzLensFacing?.let { asset.extras["mz_lens_facing"] = it }
        info.mzSceneType?.let { asset.extras["mz_scene_type"] = it }
        info.meizuLivePhotoId?.let { asset.extras["meizu_livephoto_id"] = it }
        return asset
    }

    override fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?>
    ): MutableList<String> {
        val video = asset.videoMp4
        val pts = asset.effectivePtsUs()
        val xmp = XmpTemplate.buildMeizuXmp(
            ptsUs = pts,
            videoLen = video.size,
            captureMode = asset.extras["mz_capture_mode"] as? String ?: "AUTO",
            isHdrActive = asset.extras["mz_is_hdr_active"] as? String ?: "False",
            lensFacing = asset.extras["mz_lens_facing"] as? String ?: "Back",
            sceneType = asset.extras["mz_scene_type"] as? String ?: "-1"
        )
        val primary = JpegUtil.replaceOrInsertXmp(asset.primaryJpeg, xmp)

        // 魅族样本无 GainMap；GainMap 若存在也不追加（缺少 Container 声明会破坏解析），
        // 仅透传主图（含 EXIF 位置/镜头元数据）+ 纯 MP4 视频
        val output = ByteArray(primary.size + video.size)
        System.arraycopy(primary, 0, output, 0, primary.size)
        System.arraycopy(video, 0, output, primary.size, video.size)

        val outPath = File(outDir, "$stem.jpg").path
        writeBytes(outPath, output)
        log("info", "写出魅族格式：${File(outPath).name}（图像 ${primary.size}B + 视频 ${video.size}B）", "魅族")
        return mutableListOf(outPath)
    }
}
