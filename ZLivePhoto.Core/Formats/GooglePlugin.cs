using ZLivePhoto.Core.Models;

namespace ZLivePhoto.Core.Formats;

/// <summary>
/// Google Motion Photo 标准格式（含旧版 MicroVideo 读取）。
/// </summary>
public sealed class GooglePlugin : FormatPlugin
{
    public override string Name => "google";
    public override string Display => "Google Motion Photo（标准格式）";

    /// <summary>
    /// 从文件头部直接定位 XMP 文本（检测用，容忍截断）。
    /// </summary>
    public static string SniffXmp(string path, int limit = 2 * 1024 * 1024)
    {
        try
        {
            using var fs = new FileStream(path, FileMode.Open, FileAccess.Read);
            var buffer = new byte[Math.Min(limit, fs.Length)];
            int read = fs.Read(buffer, 0, buffer.Length);
            if (read < 2 || buffer[0] != 0xFF || buffer[1] != 0xD8)
                return string.Empty;

            var head = buffer.AsSpan(0, read);
            int idx = head.IndexOf(JpegUtil.XmpApp1Prefix);
            if (idx == -1) return string.Empty;
            int end = head.Slice(idx).IndexOf("</x:xmpmeta>"u8);
            if (end == -1) return string.Empty;
            return System.Text.Encoding.UTF8.GetString(head.Slice(idx, idx + end + 12));
        }
        catch
        {
            return string.Empty;
        }
    }

    public override int Detect(string path)
    {
        var info = XmpTemplate.ParseMotionXmp(SniffXmp(path));
        if (info.IsMotion && !info.IsLegacyMicro && !info.HasOplus)
            return 90;
        if (info.IsLegacyMicro)
            return 80;
        return 0;
    }

    public override LivePhotoAsset Read(string path, Action<string, string, string> log)
    {
        log("info", "按 Google Motion Photo 解析", "Google");
        return EmbeddedReader.ReadEmbedded(path, Name, log);
    }

    public override List<string> Write(LivePhotoAsset asset, string outDir, string stem,
        Action<string, string, string> log, Dictionary<string, object> options)
    {
        var video = asset.VideoMp4;
        var pts = asset.EffectivePtsUs();
        var xmp = XmpTemplate.BuildGoogleXmp(pts, asset.GainmapLength, video.Length);
        var primary = JpegUtil.ReplaceOrInsertXmp(asset.PrimaryJpeg, xmp);

        var output = new byte[primary.Length + (asset.GainmapJpeg?.Length ?? 0) + video.Length];
        primary.CopyTo(output, 0);
        asset.GainmapJpeg?.CopyTo(output, primary.Length);
        video.CopyTo(output, primary.Length + (asset.GainmapJpeg?.Length ?? 0));

        // Google 规范：文件名以 MP 结尾
        if (options.GetValueOrDefault("google_mp_suffix", true) is bool useSuffix && useSuffix
            && !stem.EndsWith("MP", StringComparison.OrdinalIgnoreCase))
            stem += "_MP";

        string outPath = Path.Combine(outDir, stem + ".jpg");
        WriteBytes(outPath, output);
        log("info", $"写出 Google 格式：{Path.GetFileName(outPath)}" +
                    $"（图像 {primary.Length:N0}B + 视频 {video.Length:N0}B）", "Google");
        return [outPath];
    }
}
