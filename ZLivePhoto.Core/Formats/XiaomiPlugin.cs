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

    /// <summary>小米私有 0xE4 段前缀（含 {"8897":"1"} JSON 的 livephotoInfo）</summary>
    private static readonly byte[] XiaomiCustomizePrefix = "XIAOMI_CUSTOMIZE"u8.ToArray();

    /// <summary>遍历文件头 2MB 内是否存在小米私有 0xE4 段（payload 以 XIAOMI_CUSTOMIZE 开头）。</summary>
    private static bool HasXiaomiCustomize(string path)
    {
        try
        {
            using var fs = new FileStream(path, FileMode.Open, FileAccess.Read);
            var head = new byte[Math.Min(2 * 1024 * 1024, fs.Length)];
            int read = ReadFull(fs, head);
            if (read < 2) return false;
            foreach (var (_, _, _, payloadStart, _) in JpegUtil.IterateSegments(head[..read]))
            {
                if (payloadStart + XiaomiCustomizePrefix.Length <= head.Length &&
                    head.AsSpan(payloadStart, XiaomiCustomizePrefix.Length).SequenceEqual(XiaomiCustomizePrefix))
                    return true;
            }
        }
        catch { /* 读取失败 */ }
        return false;
    }

    /// <summary>循环读取直到填满 buffer，返回实际读取字节数。</summary>
    private static int ReadFull(FileStream fs, byte[] buffer)
    {
        int total = 0;
        while (total < buffer.Length)
        {
            int n = fs.Read(buffer, total, buffer.Length - total);
            if (n <= 0) break;
            total += n;
        }
        return total;
    }

    public override int Detect(string path)
    {
        var xmpText = GooglePlugin.SniffXmp(path);
        var info = XmpTemplate.ParseMotionXmp(xmpText);

        // 双标签并存是小米的强特征
        if (info.HasBoth && !info.HasOplus)
            return 95;

        // 小米私有 0xE4 段（XIAOMI_CUSTOMIZE）是小米相机特有；
        // 91 分压过 Google 的 90（EXIF 0x8897 被第三方传输剥掉时仍可识别）
        if (HasXiaomiCustomize(path))
            return 91;

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
