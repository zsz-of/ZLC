using System.Buffers.Binary;
using System.Text;
using ZLivePhoto.Core.Models;

namespace ZLivePhoto.Core.Formats;

/// <summary>
/// 荣耀动态照片格式（Honor Motion Photo）。
/// 结构：JPEG(含 Google Container XMP) + MP4(ftyp→moov→free→mdat[large size]) + uuid box(extend_type_matrix + EIS JSON) + 60B 尾部(v2_fXX + 比例 + LIVE_ID)。
/// XMP 不含 MotionPhoto 标签，仅靠 Container Directory + 文件尾 LIVE_ 标记识别。
/// </summary>
public sealed class HonorPlugin : FormatPlugin
{
    public override string Name => "honor";
    public override string Display => "荣耀动态照片";

    // uuid box 内的 ASCII 标识（非标准 16B extended_type，而是 17B ASCII）
    private static readonly byte[] HonorExtendType = "extend_type_matrix"u8.ToArray();

    // 60B 固定尾部的三段，每段 20B，空格(0x20)填充
    private const int TailSegmentLen = 20;
    private const string DefaultVersion = "v2_f01";
    private const string DefaultRatio = "100:1000";

    public override int Detect(string path)
    {
        try
        {
            // 必须是 JPEG
            using var fs = new FileStream(path, FileMode.Open, FileAccess.Read);
            var head = new byte[Math.Min(64, fs.Length)];
            int read = fs.Read(head, 0, head.Length);
            if (read < 2 || head[0] != 0xFF || head[1] != 0xD8)
                return 0;

            // 尾部必须有 LIVE_ 标记（60B 固定尾部的最后 20B 段）
            fs.Seek(-32, SeekOrigin.End);
            var tail = new byte[32];
            fs.ReadExactly(tail);
            if (tail.AsSpan().IndexOf("LIVE_"u8) < 0)
                return 0;

            // XMP 必须有 Google Container 但不含 MotionPhoto/MicroVideo/oplus
            var xmpText = GooglePlugin.SniffXmp(path);
            var info = XmpTemplate.ParseMotionXmp(xmpText);
            if (info.IsMotion || info.HasOplus)
                return 0; // 有 MotionPhoto 标签 → 不是荣耀（是 Google/小米/OPPO）

            // 有 Container Directory 且无 MotionPhoto → 荣耀强特征
            if (info.Items.Count > 0)
                return 92;

            // 无 XMP 但有 LIVE_ 尾部（部分非 HDR 样本可能无 XMP）
            return 80;
        }
        catch
        {
            return 0;
        }
    }

    public override LivePhotoAsset Read(string path, Action<string, string, string> log)
    {
        log("info", "按荣耀动态照片解析（MP4 large size + uuid EIS matrix）", "荣耀");
        return EmbeddedReader.ReadEmbedded(path, Name, log);
    }

    public override List<string> Write(LivePhotoAsset asset, string outDir, string stem,
        Action<string, string, string> log, Dictionary<string, object> options)
    {
        var video = asset.VideoMp4;
        // XMP：Adobe XMP Core 5.1.2 + Google Container（无 MotionPhoto 标签，无视频项）
        var xmp = XmpTemplate.BuildHonorXmp(asset.GainmapLength);
        var primary = JpegUtil.ReplaceOrInsertXmp(asset.PrimaryJpeg, xmp);

        // uuid box：含 extend_type_matrix + EIS JSON 数组
        var uuidBox = BuildHonorUuidBox(asset);

        // 60B 固定尾部
        var tail = BuildHonorTail(asset);

        // 拼接：JPEG(+GainMap) + MP4 + uuid box + tail
        using var ms = new MemoryStream(primary.Length + (asset.GainmapJpeg?.Length ?? 0)
                                       + video.Length + uuidBox.Length + tail.Length);
        ms.Write(primary);
        if (asset.GainmapJpeg is { } gm)
            ms.Write(gm);
        ms.Write(video);
        ms.Write(uuidBox);
        ms.Write(tail);

        string outPath = Path.Combine(outDir, stem + ".jpg");
        WriteBytes(outPath, ms.ToArray());
        log("info", $"写出荣耀格式：{Path.GetFileName(outPath)}" +
                    $"（图像 {primary.Length:N0}B + 视频 {video.Length:N0}B + EIS {uuidBox.Length:N0}B）", "荣耀");
        return [outPath];
    }

    // ---------------------------------------------------------- uuid box 构建

    /// <summary>
    /// 构建荣耀 uuid box：[size 4B]['uuid' 4B]['extend_type_matrix' 17B][EIS JSON 数组]。
    /// 每帧生成默认单位矩阵（从 VideoInfo 取宽高，无则用 0）。
    /// </summary>
    private static byte[] BuildHonorUuidBox(LivePhotoAsset asset)
    {
        int width = asset.VideoInfo.TryGetValue("width", out var w) && w is int wi ? wi : 0;
        int height = asset.VideoInfo.TryGetValue("height", out var h) && h is int hi ? hi : 0;
        long frameCount = asset.VideoInfo.TryGetValue("frame_count", out var fc) && fc is long fcv ? fcv : 1;
        if (frameCount < 1) frameCount = 1;

        // 单帧 matrix JSON 模板（单位矩阵）
        string matrixJson = "{\"decayGain\":0.0,\"eis3x3Matrix\":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0],"
            + "\"mctf3x3Matrix\":[1.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,1.0],"
            + $"\"srcDstWh\":[{width},{height},{width},{height}]}}"
            + "\\n"; // 注意：样本中是字面 \n（两个字符），非真换行

        var sb = new StringBuilder("[");
        for (int i = 1; i <= frameCount; i++)
        {
            if (i > 1) sb.Append(',');
            sb.Append("{\"frameNum\":").Append(i).Append(",\"matrixInfo\":\"").Append(matrixJson).Append("\"}");
        }
        sb.Append(']');

        var jsonBytes = Encoding.UTF8.GetBytes(sb.ToString());
        // size = 4(size) + 4(uuid) + 17(extend_type_matrix) + json.Length
        int totalSize = 8 + HonorExtendType.Length + jsonBytes.Length;

        var box = new byte[totalSize];
        BinaryPrimitives.WriteUInt32BigEndian(box.AsSpan(0, 4), (uint)totalSize);
        "uuid"u8.CopyTo(box.AsSpan(4, 4));
        HonorExtendType.CopyTo(box.AsSpan(8, HonorExtendType.Length));
        jsonBytes.CopyTo(box.AsSpan(8 + HonorExtendType.Length, jsonBytes.Length));
        return box;
    }

    // ---------------------------------------------------------- 60B 尾部构建

    /// <summary>
    /// 60B 固定尾部：[v2_fXX 20B][NNN:NNNN 20B][LIVE_XXXXXXXX 20B]，空格填充。
    /// LIVE_ID 优先复用源文件 ID（vivo 关联 ID 或 Extras["honor_live_id"]），无则生成 9 位随机。
    /// </summary>
    private static byte[] BuildHonorTail(LivePhotoAsset asset)
    {
        string liveId = "LIVE_" + GenerateLiveId(asset);
        string version = asset.Extras.TryGetValue("honor_version", out var v) && v is string vs ? vs : DefaultVersion;
        string ratio = asset.Extras.TryGetValue("honor_ratio", out var r) && r is string rs ? rs : DefaultRatio;

        var tail = new byte[TailSegmentLen * 3];
        FillSegment(tail, 0, version);
        FillSegment(tail, TailSegmentLen, ratio);
        FillSegment(tail, TailSegmentLen * 2, liveId);
        return tail;
    }

    private static void FillSegment(byte[] buf, int offset, string text)
    {
        for (int i = 0; i < TailSegmentLen; i++)
            buf[offset + i] = (byte)' ';
        var bytes = Encoding.ASCII.GetBytes(text);
        int len = Math.Min(bytes.Length, TailSegmentLen);
        bytes.AsSpan(0, len).CopyTo(buf.AsSpan(offset, len));
    }

    /// <summary>生成 9 位 LIVE_ID。优先复用源 ID，否则随机。</summary>
    private static string GenerateLiveId(LivePhotoAsset asset)
    {
        if (asset.Extras.TryGetValue("honor_live_id", out var id) && id is string ids && ids.Length == 9)
            return ids;
        // 从时间戳生成稳定的 9 位数字（确保转换可复现）
        long seed = asset.EffectivePtsUs() >= 0 ? asset.EffectivePtsUs() : DateTime.UtcNow.Ticks;
        var rnd = new Random((int)(seed & 0x7FFFFFFF));
        return rnd.Next(100_000_000, 1_000_000_000).ToString();
    }
}
