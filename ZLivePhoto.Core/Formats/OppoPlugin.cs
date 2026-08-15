using ZLivePhoto.Core.Models;

namespace ZLivePhoto.Core.Formats;

/// <summary>
/// OPPO / oplus 单文件动态照片格式。
/// </summary>
public sealed class OppoPlugin : FormatPlugin
{
    public override string Name => "oppo";
    public override string Display => "OPPO 动态照片（单文件）";

    private static byte[] BuildLpexPayload(LivePhotoAsset asset)
    {
        var vi = asset.VideoInfo;
        int vw = vi.GetValueOrDefault("width", 0) is int w ? w : 0;
        int vh = vi.GetValueOrDefault("height", 0) is int h ? h : 0;
        var (iw, ih) = JpegUtil.GetDimensions(asset.PrimaryJpeg);

        var payload = new Dictionary<string, object>
        {
            ["coverFramePts"] = asset.EffectivePtsUs(),
            ["cropRect"] = new[] { 0, 0, vw, vh },
            ["desc"] = "OppoMotionVideoExt",
            ["matrixCount"] = 0,
            ["originPhotoSize"] = new[] { iw, ih },
            ["photoCropFactor"] = 1.0,
            ["photoCropMatrix"] = new[] { 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0 },
            ["photoCropRect"] = new[] { 0, 0, iw, ih },
            ["photoEisCropFactor"] = new[] { 1.0, 1.0 },
            ["photoEisMatrix"] = new[] { 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0 },
            ["subVideoScaleFactor"] = 0.5,
            ["version"] = 1,
            ["videoOrientation"] = vi.GetValueOrDefault("rotation", 0) is int r ? r : 0,
            ["videoSize"] = new[] { vw, vh }
        };

        var jsonBytes = FooterUtil.BuildFooterJson(payload);
        return [.. "LivePhotoExtension"u8.ToArray(), .. jsonBytes];
    }

    public override int Detect(string path)
    {
        var xmp = GooglePlugin.SniffXmp(path);
        if (string.IsNullOrEmpty(xmp))
            return 0;

        var info = XmpTemplate.ParseMotionXmp(xmp);
        if (info.IsMotion && info.HasOplus)
            return 95;

        // 无 OpCamera 标签但文件尾有 cameralbum footer 也按 OPPO 处理
        if (info.IsMotion)
        {
            try
            {
                var size = new FileInfo(path).Length;
                using var fs = new FileStream(path, FileMode.Open, FileAccess.Read);
                fs.Seek(Math.Max(0, size - 4096), SeekOrigin.Begin);
                var tail = new byte[Math.Min(4096, size)];
                int read = fs.Read(tail, 0, tail.Length);
                if (FooterUtil.ParseFooter(tail.AsSpan(0, read)) is not null)
                    return 85;
            }
            catch { /* 读取失败 */ }
        }
        return 0;
    }

    public override LivePhotoAsset Read(string path, Action<string, string, string> log)
    {
        log("info", "按 OPPO 动态照片解析", "OPPO");
        var asset = EmbeddedReader.ReadEmbedded(path, Name, log);

        // OPPO 附加信息：footer JSON
        var data = ReadBytes(path);
        var footer = FooterUtil.ParseFooter(data);
        if (footer is not null)
        {
            asset.ImageTime = footer.ImageTime;
            asset.Extras["oppo_footer"] = footer;
        }
        return asset;
    }

    public override List<string> Write(LivePhotoAsset asset, string outDir, string stem,
        Action<string, string, string> log, Dictionary<string, object> options)
    {
        var video = asset.VideoMp4;

        // 源视频无 lpex 时合成插入
        if (video.AsSpan(0, Math.Min(65536, video.Length)).IndexOf("lpex"u8) < 0)
        {
            try
            {
                video = Mp4Util.InsertBoxIntoMoov(video, "lpex", BuildLpexPayload(asset));
                log("info", "已合成 lpex box（LivePhotoExtension）插入 moov", "OPPO");
            }
            catch (Exception ex)
            {
                log("warning", $"lpex 合成失败，跳过（不影响播放）：{ex.Message}", "OPPO");
            }
        }

        long imageTime = asset.EffectiveImageTime();
        var footerJson = FooterUtil.BuildFooterJson(new Dictionary<string, object>
        {
            ["com.android.camera.imageTime"] = imageTime,
            ["com.vivo.gallery.file.convert"] = 10004,
            ["com.android.camera.livephoto"] = FooterUtil.OppoFixedId,
            ["version"] = 2200
        });
        var footer = FooterUtil.BuildFooter(footerJson, FooterUtil.OppoFixedId, FooterUtil.ExtPrefix);

        var pts = asset.EffectivePtsUs();
        var xmp = XmpTemplate.BuildOppoXmp(
            pts, asset.GainmapLength,
            videoLen: video.Length + footer.Length,  // Container 声明值含 footer
            mp4Len: video.Length);                   // OpCamera:VideoLength 为纯 MP4 流
        var primary = JpegUtil.ReplaceOrInsertXmp(asset.PrimaryJpeg, xmp);

        var output = new byte[primary.Length + (asset.GainmapJpeg?.Length ?? 0) + video.Length + footer.Length];
        int pos = 0;
        primary.CopyTo(output, pos); pos += primary.Length;
        asset.GainmapJpeg?.CopyTo(output, pos); pos += asset.GainmapJpeg?.Length ?? 0;
        video.CopyTo(output, pos); pos += video.Length;
        footer.CopyTo(output, pos);

        string outPath = Path.Combine(outDir, stem + ".jpg");
        WriteBytes(outPath, output);
        log("info", $"写出 OPPO 格式：{Path.GetFileName(outPath)}" +
                    $"（图像 {primary.Length:N0}B + 视频 {video.Length:N0}B + footer {footer.Length}B）",
            "OPPO");
        return [outPath];
    }
}
