using ZLivePhoto.Core.Models;

namespace ZLivePhoto.Core.Formats;

/// <summary>
/// vivo 单文件实况照片。
///
/// vivo 相册「关闭实况」时会把双文件（IMG_xxx.jpg + IMG_xxx.mp4）合并为一个 jpg：
/// 结构 = JPG 主体（Primary + GainMap）+ MP4（保留 vivoMediaEStream 实况标识、剥 vivoMediaExtInfo
/// 源 footer 包装）+ lpex box + convert footer，
/// XMP 使用 Google Container（含 MotionPhoto 视频项）并附带 VCamera 私有字段，
/// 其中 GCamera:MotionPhoto="0" 表示关闭实况，改成 "1" 即恢复为单文件动态照片。
///
/// 识别关键（经真机实测 + 二进制逆向确认）：vivo 相册同时依赖 vivoMediaEStream uuid box、
/// lpex box 与 convert footer 三者；缺 lpex 或保留 vivoMediaExtInfo（源 footer 包装）均会导致不被识别。
///
/// 本插件：
/// - Detect：识别 vivo 合并的单文件（MotionPhoto 为 0 或 1 均可）
/// - Write：输出 MotionPhoto="1" 的单文件实况（双文件合并 / 修复关闭实况的文件）
/// </summary>
public sealed class VivoSinglePlugin : FormatPlugin
{
    public override string Name => "vivo_single";
    public override string Display => "vivo 单文件实况（JPG+MP4 合并为一个文件）";

    public override int Detect(string path)
    {
        var xmp = GooglePlugin.SniffXmp(path);
        var info = XmpTemplate.ParseMotionXmp(xmp);
        if (info.HasOplus) return 0; // OPPO 格式同样带 VCamera 字段，让位给 OPPO 插件
        if (!xmp.Contains("ns.vivo.com/photos")) return 0;
        bool hasVideoItem = false;
        foreach (var item in info.Items)
        {
            if (item.Mime == "video/mp4") { hasVideoItem = true; break; }
        }
        if (!hasVideoItem) return 0;
        // MotionPhoto="0" 是 vivo 相册关闭实况后的合并产物，同样识别（写出时自动置回 1）
        return info.IsMotion ? 95 : 85;
    }

    public override LivePhotoAsset Read(string path, Action<string, string, string> log)
    {
        log("info", "按 vivo 单文件实况解析", "vivo");
        return EmbeddedReader.ReadEmbedded(path, Name, log);
    }

    public override List<string> Write(LivePhotoAsset asset, string outDir, string stem,
        Action<string, string, string> log, Dictionary<string, object> options)
    {
        // ── 1. 处理源视频：剥 vivoMediaExtInfo（源 footer 包装），保留 vivoMediaEStream ──
        // vivo 双文件 mp4 尾部布局：
        //   [ftyp…mdat][vivoMediaEStream uuid 138B][vivoMediaExtInfo uuid 2691B(内嵌源 cameralbum footer)]
        // 两个 uuid box 性质完全不同：
        //   - vivoMediaEStream：vivo 相册识别「实况视频」的关键标识 → 必须保留
        //   - vivoMediaExtInfo：其内容即源 cameralbum footer（双文件 mp4 自带的旧 footer）→ 必须剥掉，
        //     否则视频段会内嵌一个 cameralbum footer，与尾部 convert footer 重复，破坏识别。
        // StripVivoUuid 正是「只剥 vivoMediaExtInfo、保留 vivoMediaEStream」——
        // 与真机实测可被 vivo 相册识别的 OPPO 输出完全一致。
        var video = Mp4Util.StripVivoUuid(asset.VideoMp4);

        // ── 2. 插入 lpex (LivePhotoExtension) box 到 moov ──
        // 真机实测「可被 vivo 相册识别」的 OPPO 输出视频流里带 lpex box；
        // vivo_single 缺 lpex → 不被识别。复用 OppoPlugin.BuildLpexPayload 保证逐字一致。
        if (video.Length >= 8)
        {
            int searchEnd = Math.Min(65536, video.Length);
            if (video.AsSpan(0, searchEnd).IndexOf("lpex"u8) < 0)
            {
                try
                {
                    video = Mp4Util.InsertBoxIntoMoov(video, "lpex", OppoPlugin.BuildLpexPayload(asset));
                    log("info", "已合成 lpex box（LivePhotoExtension）插入 moov", "vivo");
                }
                catch (Exception ex)
                {
                    log("warning", $"lpex 合成失败，跳过（不影响播放）：{ex.Message}", "vivo");
                }
            }
        }

        // ── 3. vivo 相册专属 convert footer（字段逐字对齐「可被识别」的 OPPO 输出） ──
        long imageTime = asset.EffectiveImageTime();
        var footerJson = FooterUtil.BuildFooterJson(new Dictionary<string, object>
        {
            ["com.vivo.gallery.livePhoto.otherPhone.MotionRotationOffset"] = 0,
            ["com.android.camera.imageTime"] = imageTime,
            ["com.vivo.gallery.file.convert"] = 10004,
            ["com.vivo.gallery.livePhoto.otherPhone.MotionRotationCheck"] = 1,
            ["com.android.camera.livephoto"] = FooterUtil.OppoFixedId,
            ["version"] = 2200
        });
        var footer = FooterUtil.BuildFooter(footerJson, FooterUtil.OppoFixedId, FooterUtil.ExtPrefix);

        // ── 4. XMP：视频项 Item:Length = video + footer（与 OPPO 约定一致，已被验证可识别） ──
        var pts = asset.EffectivePtsUs();
        var xmp = XmpTemplate.BuildVivoSingleXmp(pts, asset.GainmapLength, video.Length + footer.Length);
        var primary = JpegUtil.ReplaceOrInsertXmp(asset.PrimaryJpeg, xmp);

        // ── 5. 拼装输出：[JPEG+XMP][GainMap][video(含 vivoMediaEStream + lpex)][convert footer] ──
        int gainmapLen = asset.GainmapJpeg?.Length ?? 0;
        var output = new byte[primary.Length + gainmapLen + video.Length + footer.Length];
        int pos = 0;
        primary.CopyTo(output, pos); pos += primary.Length;
        if (asset.GainmapJpeg is not null) { asset.GainmapJpeg.CopyTo(output, pos); pos += asset.GainmapJpeg.Length; }
        video.CopyTo(output, pos); pos += video.Length;
        footer.CopyTo(output, pos);

        // vivo 相册自己的合并文件不带 _MP 后缀，保持一致
        string outPath = Path.Combine(outDir, stem + ".jpg");
        WriteBytes(outPath, output);
        log("info", $"写出 vivo 单文件实况：{Path.GetFileName(outPath)}" +
                    $"（图像 {primary.Length:N0}B + 视频 {video.Length:N0}B（含 vivoMediaEStream + lpex）" +
                    $" + footer {footer.Length}B）", "vivo");
        return [outPath];
    }
}
