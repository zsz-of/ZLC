using ZLivePhoto.Core.Models;

namespace ZLivePhoto.Core.Formats;

/// <summary>
/// vivo 双文件动态照片格式（IMG_xxx.jpg + IMG_xxx.mp4）。
/// </summary>
public sealed class VivoPlugin : FormatPlugin
{
    public override string Name => "vivo";
    public override string Display => "vivo 动态照片（JPG + MP4 双文件）";

    private const int VivoVersion = 2107;

    private static string? SiblingMp4(string path)
    {
        var mp4 = Path.Combine(Path.GetDirectoryName(path)!,
            Path.GetFileNameWithoutExtension(path) + ".mp4");
        return File.Exists(mp4) ? mp4 : null;
    }

    private static byte[] BuildJson(long imageTime, string liveId)
    {
        return FooterUtil.BuildFooterJson(new Dictionary<string, object>
        {
            ["com.android.camera.imageTime"] = imageTime,
            ["com.android.camera.livephoto"] = liveId,
            ["version"] = VivoVersion
        });
    }

    private static byte[] BuildUuidBox(byte[] jsonBytes, string liveId)
    {
        var payload = new byte[Mp4Util.VivoUuid.Length +
            FooterUtil.BuildFooter(jsonBytes, liveId, FooterUtil.VivoPrefix).Length];
        Mp4Util.VivoUuid.CopyTo(payload, 0);
        FooterUtil.BuildFooter(jsonBytes, liveId, FooterUtil.VivoPrefix)
            .CopyTo(payload, Mp4Util.VivoUuid.Length);

        var result = new byte[8 + payload.Length];
        System.Buffers.Binary.BinaryPrimitives.WriteUInt32BigEndian(
            result.AsSpan(0, 4), (uint)(payload.Length + 8));
        "uuid"u8.CopyTo(result.AsSpan(4));
        payload.CopyTo(result, 8);
        return result;
    }

    public override int Detect(string path)
    {
        if (!path.EndsWith(".jpg", StringComparison.OrdinalIgnoreCase) &&
            !path.EndsWith(".jpeg", StringComparison.OrdinalIgnoreCase))
            return 0;

        var xmp = GooglePlugin.SniffXmp(path);
        var info = XmpTemplate.ParseMotionXmp(xmp);
        if (info.IsMotion)
            return 0; // 内嵌式不归 vivo 管

        try
        {
            var size = new FileInfo(path).Length;
            using var fs = new FileStream(path, FileMode.Open, FileAccess.Read);
            fs.Seek(Math.Max(0, size - 8192), SeekOrigin.Begin);
            var tail = new byte[Math.Min(8192, size)];
            int read = fs.Read(tail, 0, tail.Length);
            var footer = FooterUtil.ParseFooter(tail.AsSpan(0, read));
            if (footer?.LivephotoId is null)
                return 0;
            return SiblingMp4(path) is not null ? 90 : 40;
        }
        catch
        {
            return 0;
        }
    }

    public override LivePhotoAsset Read(string path, Action<string, string, string> log)
    {
        log("info", "按 vivo 双文件动态照片解析", "vivo");
        var data = ReadBytes(path);
        var footer = FooterUtil.ParseFooter(data);
        if (footer?.LivephotoId is null)
            throw new InvalidDataException("JPG 尾部未找到 vivo livephoto 标记");

        string liveId = footer.LivephotoId;
        var mp4Path = SiblingMp4(path)
            ?? throw new InvalidDataException($"缺少伴生视频文件：{Path.GetFileNameWithoutExtension(path)}.mp4");

        // JPG 主体（去除 footer）→ 拆 Primary / GainMap
        var body = data[..footer.FooterStart];
        var (jpegs, _) = JpegUtil.SplitJpegs(body);
        if (jpegs.Count == 0)
            throw new InvalidDataException("JPG 主体解析失败");
        var primary = jpegs[0];
        var gainmap = jpegs.Count > 1 ? jpegs[1] : null;

        // MP4：剥离末尾 vivo uuid box
        var mp4Raw = ReadBytes(mp4Path);
        var mp4Footer = FooterUtil.ParseFooter(mp4Raw);
        long? imageTime = footer.ImageTime;
        if (mp4Footer is not null)
        {
            imageTime ??= mp4Footer.ImageTime;
            var mp4Id = mp4Footer.LivephotoId;
            if (mp4Id is not null && mp4Id != liveId)
                log("warning", $"JPG 与 MP4 的 livephoto ID 不一致：{liveId} / {mp4Id}", "vivo");
        }

        var video = Mp4Util.StripVivoUuid(mp4Raw);
        if (!Mp4Util.HasFtyp(video))
            throw new InvalidDataException("伴生 MP4 无效（缺少 ftyp box）");

        var asset = new LivePhotoAsset
        {
            PrimaryJpeg = primary,
            GainmapJpeg = gainmap,
            VideoMp4 = video,
            SourceFormat = Name,
            LivephotoId = liveId,
            ImageTime = imageTime
        };
        asset.VideoInfo = Mp4Util.GetTrackInfo(video) ?? new Dictionary<string, object>();

        // 由 imageTime（帧序号）反推封面时间戳
        if (asset.ImageTime.HasValue &&
            asset.VideoInfo.GetValueOrDefault("fps", 0.0) is double fps and > 0)
        {
            asset.PresentationTsUs = (long)Math.Round(asset.ImageTime.Value / fps * 1_000_000);
        }
        return asset;
    }

    public override List<string> Write(LivePhotoAsset asset, string outDir, string stem,
        Action<string, string, string> log, Dictionary<string, object> options)
    {
        string liveId = asset.LivephotoId ?? FooterUtil.GenerateLivephotoId();
        long imageTime = asset.EffectiveImageTime();

        // JPG：vivo XMP（无 motion 标签）+ vivo footer
        var xmp = XmpTemplate.BuildVivoXmp(asset.GainmapLength);
        var primary = JpegUtil.ReplaceOrInsertXmp(asset.PrimaryJpeg, xmp);
        var jpgJson = BuildJson(imageTime, liveId);
        var jpgOut = new byte[primary.Length + (asset.GainmapJpeg?.Length ?? 0) +
            FooterUtil.BuildFooter(jpgJson, liveId, FooterUtil.VivoPrefix).Length];
        int pos = 0;
        primary.CopyTo(jpgOut, pos); pos += primary.Length;
        asset.GainmapJpeg?.CopyTo(jpgOut, pos); pos += asset.GainmapJpeg?.Length ?? 0;
        FooterUtil.BuildFooter(jpgJson, liveId, FooterUtil.VivoPrefix).CopyTo(jpgOut, pos);

        // MP4：纯视频流 + 末尾 uuid box
        var video = Mp4Util.StripVivoUuid(asset.VideoMp4);
        var mp4Json = BuildJson(imageTime, liveId);
        var uuidBox = BuildUuidBox(mp4Json, liveId);
        var mp4Out = new byte[video.Length + uuidBox.Length];
        video.CopyTo(mp4Out, 0);
        uuidBox.CopyTo(mp4Out, video.Length);

        string jpgPath = Path.Combine(outDir, stem + ".jpg");
        string mp4Path = Path.Combine(outDir, stem + ".mp4");
        WriteBytes(jpgPath, jpgOut);
        WriteBytes(mp4Path, mp4Out);
        log("info", $"写出 vivo 格式：{stem}.jpg + {stem}.mp4（livephoto ID: {liveId}）", "vivo");
        return [jpgPath, mp4Path];
    }
}
