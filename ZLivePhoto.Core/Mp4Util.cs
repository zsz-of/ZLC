using System.Buffers.Binary;
using System.Text;

namespace ZLivePhoto.Core;

public sealed class Mp4Exception(string message) : Exception(message);

/// <summary>
/// MP4 (ISOBMFF) box 级工具：box 遍历、流边界定位、vivo uuid 处理、
/// lpex box 插入（含 stco/co64 偏移修复）、视频轨信息解析。
/// 纯字节级操作，不重编码。
/// </summary>
public static class Mp4Util
{
    public static readonly byte[] VivoUuid = "vivoMediaExtInfo"u8.ToArray(); // 16 字节

    private static byte[] Be32(uint v)
    {
        var b = new byte[4];
        BinaryPrimitives.WriteUInt32BigEndian(b, v);
        return b;
    }

    /// <summary>
    /// 遍历 [start, end) 区间内的顶层 box，返回 (typeStr, offset, size, headerLen)。
    /// 遇到非法 box 即停止。box type 必须为 4 个可打印 ASCII。
    /// </summary>
    public static IEnumerable<(string type, int offset, int size, int headerLen)>
        IterateBoxes(byte[] data, int start, int end)
    {
        int pos = start;
        while (pos + 8 <= end)
        {
            uint size32 = BinaryPrimitives.ReadUInt32BigEndian(data.AsSpan(pos, 4));

            // 校验 box type 为可打印 ASCII（OPPO 私有浮点块 type 非 ASCII，借此截断）
            bool validType = true;
            for (int i = pos + 4; i < pos + 8; i++)
            {
                if (data[i] < 0x20 || data[i] > 0x7E) { validType = false; break; }
            }
            if (!validType) yield break;

            long size = size32;
            int header = 8;
            if (size32 == 1)
            {
                if (pos + 16 > end) yield break;
                size = (long)BinaryPrimitives.ReadUInt64BigEndian(data.AsSpan(pos + 8, 8));
                header = 16;
            }
            else if (size32 == 0)
            {
                size = end - pos;
            }

            if (size < header || pos + size > end)
                yield break;

            yield return (Encoding.Latin1.GetString(data, pos + 4, 4), pos, (int)size, header);
            pos += (int)size;
        }
    }

    /// <summary>
    /// 从文件头遍历顶层 box，返回合法 MP4 流的总长度（忽略流后附加数据）。
    /// </summary>
    public static int StreamLength(byte[] data)
    {
        int lastEnd = 0;
        foreach (var (_, offset, size, _) in IterateBoxes(data, 0, data.Length))
            lastEnd = offset + size;
        return lastEnd;
    }

    public static bool HasFtyp(byte[] data)
        => data.Length >= 12 && data.AsSpan(4, 4).SequenceEqual("ftyp"u8);

    /// <summary>
    /// 若 MP4 末尾存在 UUID 为 'vivoMediaExtInfo' 的 uuid box，则去除。
    /// </summary>
    public static byte[] StripVivoUuid(byte[] data)
    {
        var boxes = IterateBoxes(data, 0, data.Length).ToList();
        if (boxes.Count == 0) return data;

        var (type, offset, _, _) = boxes[^1];
        if (type == "uuid" && data.AsSpan(offset + 8, 16).SequenceEqual(VivoUuid))
            return data[..offset];
        return data;
    }

    private static IEnumerable<(string type, int offset, int size, int headerLen)>
        WalkInto(byte[] data, int offset, int size, int headerLen, string[] pathTypes)
    {
        foreach (var (t, coff, csize, cheader) in IterateBoxes(data, offset + headerLen, offset + size))
        {
            yield return (t, coff, csize, cheader);
            if (pathTypes.Contains(t))
            {
                foreach (var inner in WalkInto(data, coff, csize, cheader, pathTypes))
                    yield return inner;
            }
        }
    }

    /// <summary>修复 moov 内所有 stco/co64 条目：位于 insertAt 之后的偏移整体后移 delta。</summary>
    private static void FixChunkOffsets(byte[] data, byte[] buf, int moovOff, int moovSize,
        int insertAt, int delta)
    {
        var containers = new[] { "trak", "mdia", "minf", "stbl" };
        foreach (var (t, coff, _, cheader) in WalkInto(data, moovOff, moovSize, 8, containers))
        {
            if (t is not ("stco" or "co64")) continue;
            int entrySize = t == "co64" ? 8 : 4;
            int body = coff + cheader;
            uint count = BinaryPrimitives.ReadUInt32BigEndian(data.AsSpan(body + 4, 4));
            int entriesStart = body + 8;

            for (int i = 0; i < count; i++)
            {
                int epos = entriesStart + i * entrySize;
                if (t == "co64")
                {
                    ulong val = BinaryPrimitives.ReadUInt64BigEndian(data.AsSpan(epos, 8));
                    if (val >= (ulong)insertAt)
                        BinaryPrimitives.WriteUInt64BigEndian(buf.AsSpan(epos, 8), val + (ulong)delta);
                }
                else
                {
                    uint val = BinaryPrimitives.ReadUInt32BigEndian(data.AsSpan(epos, 4));
                    if (val >= insertAt)
                        BinaryPrimitives.WriteUInt32BigEndian(buf.AsSpan(epos, 4), val + (uint)delta);
                }
            }
        }
    }

    /// <summary>
    /// 把一个顶层自定义 box 追加为 moov 的最后一个子 box，并修复 stco/co64 偏移。
    /// </summary>
    public static byte[] InsertBoxIntoMoov(byte[] data, string boxType, byte[] payload)
    {
        var boxes = IterateBoxes(data, 0, data.Length).ToList();
        var moov = boxes.FirstOrDefault(b => b.type == "moov");
        if (moov == default)
            throw new Mp4Exception("MP4 缺少 moov box");

        int moovOff = moov.offset;
        int moovSize = moov.size;
        int insertAt = moovOff + moovSize;

        var newBox = new byte[8 + payload.Length];
        BinaryPrimitives.WriteUInt32BigEndian(newBox.AsSpan(0, 4), (uint)(payload.Length + 8));
        Encoding.Latin1.GetBytes(boxType).CopyTo(newBox, 4);
        payload.CopyTo(newBox, 8);
        int delta = newBox.Length;

        var buf = data.ToArray();
        FixChunkOffsets(data, buf, moovOff, moovSize, insertAt, delta);

        // 更新 moov size
        BinaryPrimitives.WriteUInt32BigEndian(buf.AsSpan(moovOff, 4), (uint)(moovSize + delta));

        var result = new byte[buf.Length + delta];
        Array.Copy(buf, 0, result, 0, insertAt);
        Array.Copy(newBox, 0, result, insertAt, delta);
        Array.Copy(buf, insertAt, result, insertAt + delta, buf.Length - insertAt);
        return result;
    }

    /// <summary>
    /// 把 MP4 的 ftyp 改为 QuickTime MOV 格式（major_brand=qt  ）。
    /// </summary>
    public static byte[] Mp4ToMov(byte[] data)
    {
        if (!HasFtyp(data))
            throw new Mp4Exception("缺少 ftyp box");
        var buf = data.ToArray();
        "qt  "u8.CopyTo(buf.AsSpan(8));
        return buf;
    }

    /// <summary>
    /// 把 QuickTime MOV（major_brand=qt  ）恢复为标准 MP4（major_brand=isom）。
    /// 用于从 Apple 读回时归一化视频流：vivo/Google/小米等 MP4 格式若保留
    /// "qt  " 品牌，会导致相册能识别动态照片却无法正常播放。
    /// </summary>
    public static byte[] MovToMp4(byte[] data)
    {
        if (!HasFtyp(data))
            return data;
        var buf = data.ToArray();
        if (buf.AsSpan(8, 4).SequenceEqual("qt  "u8))
            "isom"u8.CopyTo(buf.AsSpan(8));
        return buf;
    }

    private static byte[] PackBox(string type, byte[] payload)
    {
        var result = new byte[8 + payload.Length];
        BinaryPrimitives.WriteUInt32BigEndian(result.AsSpan(0, 4), (uint)(payload.Length + 8));
        Encoding.Latin1.GetBytes(type).CopyTo(result, 4);
        payload.CopyTo(result, 8);
        return result;
    }

    /// <summary>
    /// 在 MP4/MOV 的 moov/udta 中添加 Apple QuickTime metadata。
    /// 写入 com.apple.quicktime.content.identifier 和 com.apple.quicktime.still-image-time。
    /// 如果 udta 不存在则创建。
    /// </summary>
    public static byte[] AddAppleMetadata(byte[] data, string contentId)
    {
        var cidBytes = Encoding.UTF8.GetBytes(contentId);

        // keys box：entry_count=2 + 两个 mdta key 项
        var key1 = "com.apple.quicktime.content.identifier"u8.ToArray();
        var key2 = "com.apple.quicktime.still-image-time"u8.ToArray();
        var keysPayload = new List<byte>();
        keysPayload.AddRange(Be32(2));
        keysPayload.AddRange(PackBox("mdta", key1));
        keysPayload.AddRange(PackBox("mdta", key2));
        var keysBox = PackBox("keys", [.. new byte[4], .. keysPayload]);

        // ilst box：item1 = content.identifier（UTF-8），item2 = still-image-time（int32 0）
        var data1 = PackBox("data", [.. Be32(1), .. Be32(0), .. cidBytes]);
        var item1Box = PackBox("item", [.. Be32(1), .. data1]);
        var data2 = PackBox("data", [.. Be32(22), .. Be32(0), .. Be32(0)]);
        var item2Box = PackBox("item", [.. Be32(2), .. data2]);
        var ilstBox = PackBox("ilst", [.. item1Box, .. item2Box]);

        // hdlr box（mdir handler）
        var hdlrBox = PackBox("hdlr",
            [.. new byte[4], .. new byte[4], .. "mdir"u8.ToArray(), .. new byte[12], (byte)0]);

        // meta box（QuickTime 格式：FullBox header）
        var metaBox = PackBox("meta", [.. new byte[4], .. hdlrBox, .. keysBox, .. ilstBox]);

        // 检查是否已有 udta
        var boxes = IterateBoxes(data, 0, data.Length).ToList();
        var moov = boxes.FirstOrDefault(b => b.type == "moov");
        if (moov == default)
            throw new Mp4Exception("MP4 缺少 moov box");

        int moovOff = moov.offset;
        int moovSize = moov.size;

        var moovChildren = IterateBoxes(data, moovOff + 8, moovOff + moovSize).ToList();
        var udta = moovChildren.FirstOrDefault(b => b.type == "udta");

        if (udta != default)
        {
            // udta 已存在：在 udta 内追加 meta，并修复 stco/co64（moov 变大导致 mdat 后移）
            int udtaOff = udta.offset;
            int udtaSize = udta.size;
            int delta = metaBox.Length;
            int insertAt = udtaOff + udtaSize;

            var buf = data.ToArray();
            FixChunkOffsets(data, buf, moovOff, moovSize, insertAt, delta);

            BinaryPrimitives.WriteUInt32BigEndian(buf.AsSpan(udtaOff, 4), (uint)(udtaSize + delta));
            BinaryPrimitives.WriteUInt32BigEndian(buf.AsSpan(moovOff, 4), (uint)(moovSize + delta));

            var result = new byte[buf.Length + delta];
            Array.Copy(buf, 0, result, 0, insertAt);
            Array.Copy(metaBox, 0, result, insertAt, delta);
            Array.Copy(buf, insertAt, result, insertAt + delta, buf.Length - insertAt);
            return result;
        }

        // 创建 udta，追加到 moov 末尾（InsertBoxIntoMoov 内部修复 stco）
        return InsertBoxIntoMoov(data, "udta", metaBox);
    }

    /// <summary>
    /// 解析主视频轨信息：codec/width/height/rotation/duration_us/fps/frame_count。
    /// </summary>
    public static Dictionary<string, object>? GetTrackInfo(byte[] data)
    {
        var boxes = IterateBoxes(data, 0, data.Length).ToList();
        var moov = boxes.FirstOrDefault(b => b.type == "moov");
        if (moov == default)
            return null;

        int moovOff = moov.offset;
        int moovSize = moov.size;

        var info = new Dictionary<string, object>
        {
            ["codec"] = string.Empty,
            ["width"] = 0,
            ["height"] = 0,
            ["rotation"] = 0,
            ["duration_us"] = -1L,
            ["fps"] = 0.0,
            ["frame_count"] = 0L
        };

        foreach (var (t, off, size, header) in IterateBoxes(data, moovOff + 8, moovOff + moovSize))
        {
            if (t != "trak") continue;

            var trak = WalkInto(data, off, size, header, ["mdia", "minf", "stbl"]).ToList();
            var hdlr = trak.FirstOrDefault(b => b.type == "hdlr");
            if (hdlr == default) continue;

            // hdlr 为 FullBox：version/flags(4) + pre_defined(4) + handler_type(4)
            int hdlrBody = hdlr.offset + hdlr.headerLen;
            if (!data.AsSpan(hdlrBody + 8, 4).SequenceEqual("vide"u8))
                continue;

            // tkhd：宽高 = 末尾 8 字节（16.16 定点），旋转矩阵在其前 36 字节
            var tkhd = trak.FirstOrDefault(b => b.type == "tkhd");
            if (tkhd != default)
            {
                int tkhdEnd = tkhd.offset + tkhd.size;
                uint w16 = BinaryPrimitives.ReadUInt32BigEndian(data.AsSpan(tkhdEnd - 8, 4));
                uint h16 = BinaryPrimitives.ReadUInt32BigEndian(data.AsSpan(tkhdEnd - 4, 4));
                info["width"] = (int)(w16 >> 16);
                info["height"] = (int)(h16 >> 16);

                int m = tkhdEnd - 8 - 36;
                int a = BinaryPrimitives.ReadInt32BigEndian(data.AsSpan(m, 4));
                int b = BinaryPrimitives.ReadInt32BigEndian(data.AsSpan(m + 4, 4));
                int c = BinaryPrimitives.ReadInt32BigEndian(data.AsSpan(m + 8, 4));
                int d = BinaryPrimitives.ReadInt32BigEndian(data.AsSpan(m + 12, 4));
                if (a == 0 && b == 65536 && c == -65536 && d == 0) info["rotation"] = 90;
                else if (a == -65536 && b == 0 && c == 0 && d == -65536) info["rotation"] = 180;
                else if (a == 0 && b == -65536 && c == 65536 && d == 0) info["rotation"] = 270;
            }

            // mdhd：timescale + duration（与 Python 原版偏移保持一致）
            var mdhd = trak.FirstOrDefault(b => b.type == "mdhd");
            double durationS = 0.0;
            if (mdhd != default)
            {
                int body = mdhd.offset + mdhd.headerLen;
                byte version = data[body];
                uint timescale;
                ulong duration;
                if (version == 1)
                {
                    timescale = BinaryPrimitives.ReadUInt32BigEndian(data.AsSpan(body + 12, 4));
                    duration = BinaryPrimitives.ReadUInt64BigEndian(data.AsSpan(body + 16, 8));
                }
                else
                {
                    timescale = BinaryPrimitives.ReadUInt32BigEndian(data.AsSpan(body + 12, 4));
                    duration = BinaryPrimitives.ReadUInt32BigEndian(data.AsSpan(body + 16, 4));
                }
                if (timescale > 0)
                {
                    durationS = duration / (double)timescale;
                    info["duration_us"] = (long)(durationS * 1_000_000);
                }
            }

            // stts：样本总数 → fps
            var stts = trak.FirstOrDefault(b => b.type == "stts");
            if (stts != default)
            {
                int body = stts.offset + stts.headerLen;
                uint count = BinaryPrimitives.ReadUInt32BigEndian(data.AsSpan(body + 4, 4));
                long total = 0;
                for (int i = 0; i < count; i++)
                    total += BinaryPrimitives.ReadUInt32BigEndian(data.AsSpan(body + 8 + i * 8, 4));
                info["frame_count"] = total;
                if (durationS > 0)
                    info["fps"] = total / durationS;
            }

            // stsd：编码 fourcc
            var stsd = trak.FirstOrDefault(b => b.type == "stsd");
            if (stsd != default)
            {
                int body = stsd.offset + stsd.headerLen;
                info["codec"] = Encoding.Latin1.GetString(data, body + 12, 4);
            }

            return info;
        }
        return null;
    }
}
