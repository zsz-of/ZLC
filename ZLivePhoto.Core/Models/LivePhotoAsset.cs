namespace ZLivePhoto.Core.Models;

/// <summary>
/// 一张动态照片的规范化表示（所有字节均为无损透传）。
/// </summary>
public sealed class LivePhotoAsset
{
    /// <summary>主 JPEG（SOI 到 EOI，含全部 APPn 段）</summary>
    public required byte[] PrimaryJpeg { get; init; }

    /// <summary>GainMap JPEG（Ultra HDR，无则 null）</summary>
    public byte[]? GainmapJpeg { get; init; }

    /// <summary>纯 MP4 流（不含厂商附加数据/footer）</summary>
    public required byte[] VideoMp4 { get; init; }

    /// <summary>来源格式标识</summary>
    public string SourceFormat { get; init; } = string.Empty;

    /// <summary>封面帧视频内时间戳（微秒），-1 未知</summary>
    public long PresentationTsUs { get; set; } = -1;

    /// <summary>vivo 关联 ID（28 字符）</summary>
    public string? LivephotoId { get; set; }

    /// <summary>footer JSON 的 imageTime（封面帧序号）</summary>
    public long? ImageTime { get; set; }

    /// <summary>视频轨信息：codec/width/height/fps/duration_us/rotation</summary>
    public Dictionary<string, object> VideoInfo { get; set; } = new();

    /// <summary>厂商私有附加（仅供同格式参考）</summary>
    public Dictionary<string, object> Extras { get; } = new();

    public int? GainmapLength => GainmapJpeg?.Length;

    /// <summary>封面帧时间戳：未知时按 Google 规范取视频中点。</summary>
    public long EffectivePtsUs()
    {
        if (PresentationTsUs >= 0)
            return PresentationTsUs;
        if (VideoInfo.TryGetValue("duration_us", out var durObj) && durObj is long dur and > 0)
            return dur / 2;
        return -1;
    }

    /// <summary>footer JSON 的 imageTime（封面帧序号）= round(pts * fps)。</summary>
    public long EffectiveImageTime()
    {
        if (ImageTime.HasValue)
            return ImageTime.Value;
        var pts = EffectivePtsUs();
        if (pts >= 0 && VideoInfo.TryGetValue("fps", out var fpsObj) && fpsObj is double fps and > 0)
            return (long)Math.Round(pts / 1_000_000.0 * fps);
        if (VideoInfo.TryGetValue("frame_count", out var fcObj) && fcObj is long fc)
            return fc / 2;
        return 0;
    }
}
