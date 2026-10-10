package com.zsz.zlivephoto.core.formats

import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.BinaryUtils
import com.zsz.zlivephoto.core.CoreText
import com.zsz.zlivephoto.core.FooterUtil
import com.zsz.zlivephoto.core.JpegUtil
import com.zsz.zlivephoto.core.LivePhotoAsset
import com.zsz.zlivephoto.core.Mp4Util
import com.zsz.zlivephoto.core.XmpTemplate
import java.io.File

/**
 * vivo 单文件实况照片。
 *
 * vivo 相册「关闭实况」时会把双文件（IMG_xxx.jpg + IMG_xxx.mp4）合并为一个 jpg：
 * 结构 = JPG 主体（Primary + GainMap）+ MP4（保留 vivoMediaEStream 实况标识、剥 vivoMediaExtInfo
 * 源 footer 包装）+ lpex box + convert footer，
 * XMP 使用 Google Container（含 MotionPhoto 视频项）并附带 VCamera 私有字段，
 * 其中 GCamera:MotionPhoto="0" 表示关闭实况，改成 "1" 即恢复为单文件动态照片。
 *
 * 识别关键（经用户真机实测 + 二进制逆向确认）：vivo 相册同时依赖 vivoMediaEStream uuid box、
 * lpex box 与 convert footer 三者；缺 lpex 或保留 vivoMediaExtInfo（源 footer 包装）均会导致不被识别。
 *
 * 本插件：
 * - detect：识别 vivo 合并的单文件（MotionPhoto 为 0 或 1 均可）
 * - write：输出 MotionPhoto="1" 的单文件实况（双文件合并 / 修复关闭实况的文件）
 */
internal class VivoSinglePlugin : FormatPlugin() {
    override val name: String = "vivo_single"
    override val displayRes: Int = R.string.fmt_vivo_single

    override fun detect(path: String): Int {
        val xmp = GooglePlugin.sniffXmp(path)
        val info = XmpTemplate.parseMotionXmp(xmp)
        if (info.hasOplus) return 0 // OPPO 格式同样带 VCamera 字段，让位给 OPPO 插件
        if (!xmp.contains("ns.vivo.com/photos")) return 0
        val hasVideoItem = info.items.any { it.mime == "video/mp4" }
        if (!hasVideoItem) return 0
        // MotionPhoto="0" 是 vivo 相册关闭实况后的合并产物，同样识别（写出时自动置回 1）
        return if (info.isMotion) 95 else 85
    }

    override fun read(path: String, log: (String, String, String) -> Unit): LivePhotoAsset {
        log("info", CoreText.of(R.string.fmt_log_parse_vivo_single), "vivo")
        return EmbeddedReader.readEmbedded(path, name, log)
    }

    override fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?>
    ): MutableList<String> {
        // ── 1. 处理源视频：剥 vivoMediaExtInfo（源 footer 包装），保留 vivoMediaEStream ──
        // vivo 双文件 mp4 尾部布局：
        //   [ftyp…mdat][vivoMediaEStream uuid 138B][vivoMediaExtInfo uuid 2691B(内嵌源 cameralbum footer)]
        // 两个 uuid box 性质完全不同：
        //   - vivoMediaEStream：vivo 相册识别「实况视频」的关键标识 → 必须保留
        //   - vivoMediaExtInfo：其内容即源 cameralbum footer（双文件 mp4 自带的旧 footer）→ 必须剥掉，
        //     否则视频段会内嵌一个 cameralbum footer，与尾部 convert footer 重复，破坏识别。
        // stripVivoUuid 正是「只剥 vivoMediaExtInfo、保留 vivoMediaEStream」——
        // 与用户实测可被 vivo 相册识别的 OPPO 输出完全一致。
        var video = Mp4Util.stripVivoUuid(asset.videoMp4)

        // ── 2. 插入 lpex (LivePhotoExtension) box 到 moov ──
        // 用户实测「可被 vivo 相册识别」的 OPPO 输出视频流里带 lpex box；
        // vivo_single 旧版缺 lpex → 不被识别。复用 OppoPlugin.buildLpexPayload 保证逐字一致。
        if (video.size >= 8) {
            val searchEnd = minOf(65536, video.size)
            val lpexMarker = byteArrayOf(0x6C, 0x70, 0x65, 0x78) // "lpex"
            if (BinaryUtils.indexOf(video.copyOfRange(0, searchEnd), lpexMarker) < 0) {
                try {
                    video = Mp4Util.insertBoxIntoMoov(video, "lpex", OppoPlugin.buildLpexPayload(asset))
                    log("info", CoreText.of(R.string.fmt_log_lpex_ok), "vivo")
                } catch (ex: Exception) {
                    log("warning", CoreText.of(R.string.fmt_log_lpex_fail, ex.message), "vivo")
                }
            }
        }

        // ── 3. vivo 相册专属 convert footer（字段逐字对齐「可被识别」的 OPPO 输出） ──
        val imageTime = asset.effectiveImageTime()
        val footerJson = FooterUtil.buildFooterJson(linkedMapOf(
            "com.vivo.gallery.livePhoto.otherPhone.MotionRotationOffset" to 0,
            "com.android.camera.imageTime" to imageTime,
            "com.vivo.gallery.file.convert" to 10004,
            "com.vivo.gallery.livePhoto.otherPhone.MotionRotationCheck" to 1,
            "com.android.camera.livephoto" to FooterUtil.oppoFixedId,
            "version" to 2200
        ))
        val footer = FooterUtil.buildFooter(footerJson, FooterUtil.oppoFixedId, FooterUtil.extPrefix)

        // 真机尾部元数据不是裸 payload，而是完整的 uuid box：[size]["uuid"][payload]（与 OPPO 同构）。
        val trailer = Mp4Util.wrapVivoUuidBox(footer)

        // ── 4. XMP：视频项 Item:Length = video + trailer（含 uuid box 头，与真机一致） ──
        val pts = asset.effectivePtsUs()
        val xmp = XmpTemplate.buildVivoSingleXmp(pts, asset.gainmapLength, video.size + trailer.size)
        val primary = JpegUtil.replaceOrInsertXmp(asset.primaryJpeg, xmp)

        // ── 5. 拼装输出：[JPEG+XMP][GainMap][video(含 vivoMediaEStream + lpex)][uuid box trailer] ──
        val gainmapLen = asset.gainmapJpeg?.size ?: 0
        val output = ByteArray(primary.size + gainmapLen + video.size + trailer.size)
        var pos = 0
        System.arraycopy(primary, 0, output, pos, primary.size); pos += primary.size
        asset.gainmapJpeg?.let { System.arraycopy(it, 0, output, pos, it.size); pos += it.size }
        System.arraycopy(video, 0, output, pos, video.size); pos += video.size
        System.arraycopy(trailer, 0, output, pos, trailer.size)

        // vivo 相册自己的合并文件不带 _MP 后缀，保持一致
        val outPath = File(outDir, "$stem.jpg").path
        writeBytes(outPath, output)
        log("info", CoreText.of(R.string.fmt_log_vivo_single_write, File(outPath).name,
            primary.size, video.size, trailer.size), "vivo")
        return mutableListOf(outPath)
    }
}
