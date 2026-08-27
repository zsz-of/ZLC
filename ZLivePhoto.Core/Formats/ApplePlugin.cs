using ZLivePhoto.Core.Models;

namespace ZLivePhoto.Core.Formats;

/// <summary>
/// Apple Live Photo 格式（JPG + MOV 双文件）。
/// </summary>
public sealed class ApplePlugin : FormatPlugin
{
    public override string Name => "apple";
    public override string Display => "Apple Live Photo";

    private const string AppleXmpNs = "xmlns:apple-fi=\"http://ns.apple.com/finalcut/1.0/\"";

    private static string? FindMovSibling(string path)
    {
        var baseName = Path.Combine(Path.GetDirectoryName(path)!, Path.GetFileNameWithoutExtension(path));
        foreach (var ext in new[] { ".mov", ".MOV" })
        {
            var mov = baseName + ext;
            if (File.Exists(mov))
                return mov;
        }
        return null;
    }

    private static string BuildAppleXmp(string contentId)
    {
        return $"""
            <x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about=""
                    {AppleXmpNs}
                  apple-fi:ContentIdentifier="{contentId}"/>
              </rdf:RDF>
            </x:xmpmeta>
            """;
    }

    public override int Detect(string path)
    {
        var ext = Path.GetExtension(path).ToLowerInvariant();
        if (ext is not (".jpg" or ".jpeg" or ".heic"))
            return 0;

        var mov = FindMovSibling(path);
        if (mov is null)
            return 0;

        try
        {
            using var fs = new FileStream(mov, FileMode.Open, FileAccess.Read);
            var header = new byte[16];
            int read = fs.Read(header, 0, 16);
            if (read >= 12 && header[4..8].SequenceEqual("ftyp"u8))
            {
                var brand = header[8..12];
                if (brand.SequenceEqual("qt  "u8))
                    return 90;
                if (brand.SequenceEqual("isom"u8) || brand.SequenceEqual("mp41"u8) ||
                    brand.SequenceEqual("mp42"u8) || brand.SequenceEqual("MSNV"u8))
                    return 75;
            }
        }
        catch { /* 读取失败 */ }
        return 60;
    }

    public override LivePhotoAsset Read(string path, Action<string, string, string> log)
    {
        log("info", "按 Apple Live Photo 解析", "Apple");
        var movPath = FindMovSibling(path)
            ?? throw new InvalidDataException("未找到同名 .mov 视频文件");

        var primary = ReadBytes(path);
        var movData = ReadBytes(movPath);
        if (!Mp4Util.HasFtyp(movData))
            throw new InvalidDataException("MOV 文件缺少 ftyp box");

        // 归一化：MOV 的 major_brand "qt  " 恢复为标准 MP4 "isom"，
        // 否则转回 vivo/Google/小米等 MP4 格式时相册可识别但无法播放。
        var videoMp4 = Mp4Util.MovToMp4(movData);

        var asset = new LivePhotoAsset
        {
            PrimaryJpeg = primary,
            GainmapJpeg = null,
            VideoMp4 = videoMp4,
            SourceFormat = Name,
            PresentationTsUs = 0 // Apple StillImageTime=0
        };
        asset.VideoInfo = Mp4Util.GetTrackInfo(movData) ?? new Dictionary<string, object>();
        return asset;
    }

    public override List<string> Write(LivePhotoAsset asset, string outDir, string stem,
        Action<string, string, string> log, Dictionary<string, object> options)
    {
        // Apple 规范要求带连字符的标准 UUID（如 1E874403-E522-4589-948A-E97AC157F32D）
        var contentId = Guid.NewGuid().ToString().ToUpperInvariant();

        // 图片端：写入 XMP ContentIdentifier
        var xmp = BuildAppleXmp(contentId);
        var primary = JpegUtil.ReplaceOrInsertXmp(asset.PrimaryJpeg, xmp);
        string jpgPath = Path.Combine(outDir, stem + ".jpg");
        WriteBytes(jpgPath, primary);

        // 视频端：MOV 格式 + Apple metadata
        var movData = Mp4Util.Mp4ToMov(asset.VideoMp4);
        movData = Mp4Util.AddAppleMetadata(movData, contentId);
        string movPath = Path.Combine(outDir, stem + ".mov");
        WriteBytes(movPath, movData);

        log("info", $"写出 Apple 格式：{stem}.jpg + {stem}.mov" +
                    $"（ContentIdentifier={contentId[..8]}...）", "Apple");
        return [jpgPath, movPath];
    }
}
