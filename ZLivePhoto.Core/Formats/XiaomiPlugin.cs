using ZLivePhoto.Core.Models;

namespace ZLivePhoto.Core.Formats;

/// <summary>
/// 小米动态照片格式（Xiaomi Motion Photo）。
/// 本质 = Google Motion Photo + EXIF 0x8897 标签 + XMP 双标签（MicroVideo + MotionPhoto）。
/// </summary>
public sealed class XiaomiPlugin : FormatPlugin
{
    public override string Name => "xiaomi";
    public override string Display => "小米动态照片";

    /// <summary>小米相册识别的 EXIF 标签（十进制 34967）</summary>
    public const int XiaomiExifTag = 0x8897;

    public override int Detect(string path)
    {
        var xmpText = GooglePlugin.SniffXmp(path);
        var info = XmpTemplate.ParseMotionXmp(xmpText);

        // 双标签并存是小米的强特征
        if (info.HasBoth && !info.HasOplus)
            return 95;

        // EXIF 0x8897 存在也是小米特征（小米相机写在 ExifIFD，可能无 MicroVideo 双标签，
        // 布局与 Google 纯 Container 相同；92 分压过 Google 的 90 避免误判）
        try
        {
            using var fs = new FileStream(path, FileMode.Open, FileAccess.Read);
            var head = new byte[Math.Min(2 * 1024 * 1024, fs.Length)];
            int read = fs.Read(head, 0, head.Length);
            if (read >= 2 && head[0] == 0xFF && head[1] == 0xD8 &&
                ExifUtil.HasExifTag(head[..read], XiaomiExifTag))
                return 92;
        }
        catch { /* 读取失败 */ }
        return 0;
    }

    public override LivePhotoAsset Read(string path, Action<string, string, string> log)
    {
        log("info", "按小米动态照片解析（Google 兼容）", "小米");
        return EmbeddedReader.ReadEmbedded(path, Name, log);
    }

    public override List<string> Write(LivePhotoAsset asset, string outDir, string stem,
        Action<string, string, string> log, Dictionary<string, object> options)
    {
        var video = asset.VideoMp4;
        var pts = asset.EffectivePtsUs();
        var xmp = XmpTemplate.BuildXiaomiXmp(pts, asset.GainmapLength, video.Length);
        var primary = JpegUtil.ReplaceOrInsertXmp(asset.PrimaryJpeg, xmp);
        // 写入 EXIF 0x8897 = 1（小米相册识别标签；写入 ExifIFD，与小米相机一致，
        // 采用追加+指针改写策略，不移动既有 EXIF 数据，GPS/镜头等元数据零损坏）
        primary = ExifUtil.AddExifIfdTag(primary, XiaomiExifTag, 1, 1);

        var output = new byte[primary.Length + (asset.GainmapJpeg?.Length ?? 0) + video.Length];
        int pos = 0;
        primary.CopyTo(output, pos); pos += primary.Length;
        asset.GainmapJpeg?.CopyTo(output, pos); pos += asset.GainmapJpeg?.Length ?? 0;
        video.CopyTo(output, pos);

        string outPath = Path.Combine(outDir, stem + ".jpg");
        WriteBytes(outPath, output);
        log("info", $"写出小米格式：{Path.GetFileName(outPath)}" +
                    $"（图像 {primary.Length:N0}B + 视频 {video.Length:N0}B，EXIF 0x8897=1）", "小米");
        return [outPath];
    }
}
