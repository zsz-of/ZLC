using ZLivePhoto.Core.Models;

namespace ZLivePhoto.Core.Formats;

/// <summary>
/// 单文件内嵌格式的共用读取逻辑（Google / OPPO / 小米）。
/// </summary>
public static class EmbeddedReader
{
    public static LivePhotoAsset ReadEmbedded(string path, string sourceFormat,
        Action<string, string, string> log)
    {
        var data = File.ReadAllBytes(path);
        var xmpFound = JpegUtil.FindXmpSegment(data);
        var xmpInfo = XmpTemplate.ParseMotionXmp(xmpFound?.xmpText ?? string.Empty);

        var (jpegs, consumed) = JpegUtil.SplitJpegs(data);
        if (jpegs.Count == 0)
            throw new InvalidDataException("未找到主 JPEG 图像");
        var primary = jpegs[0];
        var gainmap = jpegs.Count > 1 ? jpegs[1] : null;

        // 视频定位
        byte[] videoPayload;
        if (xmpInfo.IsLegacyMicro && xmpInfo.MicroVideoOffset.HasValue)
        {
            // 旧版 MicroVideo：视频起点 = 文件大小 - MicroVideoOffset
            int offset = data.Length - xmpInfo.MicroVideoOffset.Value;
            videoPayload = data[offset..];
        }
        else
        {
            videoPayload = data[consumed..];
        }

        // 荣耀等格式在 JPEG EOI 和 ftyp 之间可能有非标准数据（EXIF preview 等），
        // 需在剩余数据中搜索 ftyp box 起始位置。
        if (!Mp4Util.HasFtyp(videoPayload))
        {
            int ftypIdx = FindFtyp(videoPayload);
            if (ftypIdx < 0)
                throw new InvalidDataException("JPEG 之后未找到有效的 MP4 视频（缺少 ftyp box）");
            videoPayload = videoPayload[ftypIdx..];
        }

        int mp4Len = Mp4Util.StreamLength(videoPayload);
        if (mp4Len <= 0)
            throw new InvalidDataException("MP4 视频流解析失败");
        var video = videoPayload[..mp4Len];

        var asset = new LivePhotoAsset
        {
            PrimaryJpeg = primary,
            GainmapJpeg = gainmap,
            VideoMp4 = video,
            SourceFormat = sourceFormat,
            PresentationTsUs = xmpInfo.PtsUs
        };
        asset.VideoInfo = Mp4Util.GetTrackInfo(video) ?? new Dictionary<string, object>();
        asset.Extras["payload_trailer"] = videoPayload[mp4Len..];
        return asset;
    }

    /// <summary>
    /// 在数据中搜索 MP4 ftyp box 起始位置。
    /// ftyp box 格式：[size 4B][type='ftyp' 4B]，搜索 "ftyp" 字符串后回退 4 字节。
    /// </summary>
    private static int FindFtyp(byte[] data)
    {
        // 搜索 "ftyp" 后回退 4 字节到 size 字段，校验 size 合法性
        for (int i = 4; i < data.Length - 4; i++)
        {
            if (data[i] == 'f' && data[i + 1] == 't' && data[i + 2] == 'y' && data[i + 3] == 'p')
            {
                int boxStart = i - 4;
                uint size = System.Buffers.Binary.BinaryPrimitives.ReadUInt32BigEndian(data.AsSpan(boxStart, 4));
                if (size >= 8 && boxStart + size <= data.Length)
                    return boxStart;
            }
        }
        return -1;
    }
}
