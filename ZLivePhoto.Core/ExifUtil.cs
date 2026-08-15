using System.Buffers.Binary;
using System.Text;

namespace ZLivePhoto.Core;

public sealed class ExifException(string message) : Exception(message);

/// <summary>
/// EXIF APP1 段的字节级读写：在 JPEG 的 IFD0 中添加/检测标签。
/// 纯字节级操作，不影响图像数据。用于小米 0x8897 标签。
/// </summary>
public static class ExifUtil
{
    public static readonly byte[] ExifPrefix = "Exif\0\0"u8.ToArray();

    // TIFF 数据类型大小（字节）
    private static readonly Dictionary<int, int> TypeSizes = new()
    {
        [1] = 1, [2] = 1, [3] = 2, [4] = 4, [5] = 8,
        [6] = 1, [7] = 1, [8] = 2, [9] = 4, [10] = 8,
        [11] = 4, [12] = 8
    };

    private static byte[] Pack16(bool le, ushort v)
    {
        var b = new byte[2];
        if (le) BinaryPrimitives.WriteUInt16LittleEndian(b, v);
        else BinaryPrimitives.WriteUInt16BigEndian(b, v);
        return b;
    }

    private static byte[] Pack32(bool le, uint v)
    {
        var b = new byte[4];
        if (le) BinaryPrimitives.WriteUInt32LittleEndian(b, v);
        else BinaryPrimitives.WriteUInt32BigEndian(b, v);
        return b;
    }

    private static ushort Read16(byte[] d, int off, bool le) => le
        ? BinaryPrimitives.ReadUInt16LittleEndian(d.AsSpan(off, 2))
        : BinaryPrimitives.ReadUInt16BigEndian(d.AsSpan(off, 2));

    private static uint Read32(byte[] d, int off, bool le) => le
        ? BinaryPrimitives.ReadUInt32LittleEndian(d.AsSpan(off, 4))
        : BinaryPrimitives.ReadUInt32BigEndian(d.AsSpan(off, 4));

    /// <summary>定位 EXIF APP1 段，返回 (segStart, totalLen, tiffStart)；无则 null。</summary>
    private static (int segStart, int totalLen, int tiffStart)? FindExifApp1(byte[] jpeg)
    {
        foreach (var (marker, segStart, totalLen, payloadStart, _) in JpegUtil.IterateSegments(jpeg))
        {
            if (marker == 0xE1 && jpeg.AsSpan(payloadStart, 6).SequenceEqual(ExifPrefix))
                return (segStart, totalLen, payloadStart + 6);
        }
        return null;
    }

    /// <summary>返回 TIFF 字节序：true=小端(II)，false=大端(MM)。</summary>
    private static bool IsLittleEndian(byte[] jpeg, int tiffStart)
    {
        if (jpeg[tiffStart] == (byte)'I' && jpeg[tiffStart + 1] == (byte)'I') return true;
        if (jpeg[tiffStart] == (byte)'M' && jpeg[tiffStart + 1] == (byte)'M') return false;
        throw new ExifException("无效的 TIFF 字节序标记");
    }

    /// <summary>检测 JPEG 的 IFD0 中是否存在指定 EXIF 标签。</summary>
    public static bool HasExifTag(byte[] jpeg, int tagId)
    {
        var found = FindExifApp1(jpeg);
        if (found is null) return false;

        var (_, _, tiffStart) = found.Value;
        try
        {
            bool le = IsLittleEndian(jpeg, tiffStart);
            int ifd0Abs = tiffStart + (int)Read32(jpeg, tiffStart + 4, le);
            ushort count = Read16(jpeg, ifd0Abs, le);
            for (int i = 0; i < count; i++)
            {
                int entryOff = ifd0Abs + 2 + i * 12;
                if (Read16(jpeg, entryOff, le) == tagId)
                    return true;
            }
        }
        catch { /* 解析失败视为无标签 */ }
        return false;
    }

    /// <summary>读取 IFD0 中指定标签的值（inline 值或偏移引用）。</summary>
    public static object? ReadExifTagValue(byte[] jpeg, int tagId)
    {
        var found = FindExifApp1(jpeg);
        if (found is null) return null;

        var (_, _, tiffStart) = found.Value;
        try
        {
            bool le = IsLittleEndian(jpeg, tiffStart);
            int ifd0Abs = tiffStart + (int)Read32(jpeg, tiffStart + 4, le);
            ushort count = Read16(jpeg, ifd0Abs, le);
            for (int i = 0; i < count; i++)
            {
                int entryOff = ifd0Abs + 2 + i * 12;
                if (Read16(jpeg, entryOff, le) != tagId) continue;

                ushort ttype = Read16(jpeg, entryOff + 2, le);
                uint tcount = Read32(jpeg, entryOff + 4, le);
                int typeSize = TypeSizes.GetValueOrDefault(ttype, 1);
                int dataSize = typeSize * (int)tcount;

                if (dataSize <= 4)
                {
                    var valBytes = jpeg.AsSpan(entryOff + 8, dataSize);
                    return ttype switch
                    {
                        1 => valBytes[0],
                        3 => le ? BinaryPrimitives.ReadUInt16LittleEndian(valBytes)
                                : BinaryPrimitives.ReadUInt16BigEndian(valBytes),
                        4 => le ? BinaryPrimitives.ReadUInt32LittleEndian(valBytes)
                                : BinaryPrimitives.ReadUInt32BigEndian(valBytes),
                        _ => valBytes.ToArray()
                    };
                }

                int dataAbs = tiffStart + (int)Read32(jpeg, entryOff + 8, le);
                return jpeg[dataAbs..(dataAbs + dataSize)];
            }
        }
        catch { /* 解析失败 */ }
        return null;
    }

    /// <summary>
    /// 在 JPEG 的 EXIF IFD0 中添加一个标签（仅支持 inline 值：BYTE/SHORT/LONG）。
    /// 若 JPEG 无 EXIF APP1 段，创建最小段。若已有该标签，替换之。
    /// </summary>
    public static byte[] AddIfd0Tag(byte[] jpeg, int tagId, int tagType, int value)
    {
        if (!TypeSizes.ContainsKey(tagType))
            throw new ExifException($"不支持的 TIFF 类型 {tagType}");

        // 编码值到 4 字节 inline（小端布局，与 Python 原版一致）
        byte[] valInline = tagType switch
        {
            1 => [(byte)value, 0, 0, 0],
            3 => [(byte)(value & 0xFF), (byte)(value >> 8), 0, 0],
            4 => [(byte)(value & 0xFF), (byte)(value >> 8), (byte)(value >> 16), (byte)(value >> 24)],
            _ => throw new ExifException($"不支持的 inline 类型 {tagType}")
        };
        const int count = 1;

        var found = FindExifApp1(jpeg);
        if (found is null)
        {
            // 创建最小 EXIF APP1 段（小端 II）
            var ifd0 = new List<byte>();
            ifd0.AddRange(Pack16(true, 1));
            ifd0.AddRange(Pack16(true, (ushort)tagId));
            ifd0.AddRange(Pack16(true, (ushort)tagType));
            ifd0.AddRange(Pack32(true, count));
            ifd0.AddRange(valInline);
            ifd0.AddRange(Pack32(true, 0)); // next IFD offset

            var tiff = new List<byte>();
            tiff.AddRange("II"u8.ToArray());
            tiff.AddRange(Pack16(true, 42));
            tiff.AddRange(Pack32(true, 8));
            tiff.AddRange(ifd0);

            var payload = new byte[ExifPrefix.Length + tiff.Count];
            ExifPrefix.CopyTo(payload, 0);
            tiff.ToArray().CopyTo(payload, ExifPrefix.Length);

            var app1 = new byte[4 + payload.Length];
            app1[0] = 0xFF;
            app1[1] = 0xE1;
            BinaryPrimitives.WriteUInt16BigEndian(app1.AsSpan(2, 2), (ushort)(payload.Length + 2));
            payload.CopyTo(app1, 4);

            var combined = new byte[jpeg.Length + app1.Length];
            Array.Copy(jpeg, 0, combined, 0, 2);
            Array.Copy(app1, 0, combined, 2, app1.Length);
            Array.Copy(jpeg, 2, combined, 2 + app1.Length, jpeg.Length - 2);
            return combined;
        }

        var (segStart, totalLen, tiffStart) = found.Value;
        bool le = IsLittleEndian(jpeg, tiffStart);

        uint ifd0Off = Read32(jpeg, tiffStart + 4, le);
        int ifd0Abs = tiffStart + (int)ifd0Off;
        ushort oldCount = Read16(jpeg, ifd0Abs, le);

        // 读取现有 entry（排除同 tag，实现替换语义）
        var entries = new List<(ushort tid, ushort ttype, uint tcount, byte[] tval)>();
        for (int i = 0; i < oldCount; i++)
        {
            int entryOff = ifd0Abs + 2 + i * 12;
            ushort tid = Read16(jpeg, entryOff, le);
            if (tid == tagId) continue;
            ushort ttype = Read16(jpeg, entryOff + 2, le);
            uint tcount = Read32(jpeg, entryOff + 4, le);
            entries.Add((tid, ttype, tcount, jpeg[(entryOff + 8)..(entryOff + 12)]));
        }

        // 新增/替换一个 entry → 净增 12 字节
        const int delta = 12;
        uint dataAreaStartRel = ifd0Off + 2u + (uint)oldCount * 12u + 4u;

        // 修复旧 entry 中的数据区偏移引用
        var fixedEntries = new List<(ushort tid, ushort ttype, uint tcount, byte[] tval)>();
        foreach (var e in entries)
        {
            var tval = e.tval;
            int ts = TypeSizes.GetValueOrDefault(e.ttype, 1);
            if (ts * (int)e.tcount > 4)
            {
                uint oldOff = le
                    ? BinaryPrimitives.ReadUInt32LittleEndian(tval)
                    : BinaryPrimitives.ReadUInt32BigEndian(tval);
                if (oldOff >= dataAreaStartRel)
                    tval = Pack32(le, oldOff + delta);
            }
            fixedEntries.Add((e.tid, e.ttype, e.tcount, tval));
        }

        // 合并新 entry 并按 tag ID 排序
        var allEntries = fixedEntries
            .Append(((ushort)tagId, (ushort)tagType, (uint)count, valInline))
            .OrderBy(x => x.Item1)
            .ToList();

        var newIfd0 = new List<byte>();
        newIfd0.AddRange(Pack16(le, (ushort)allEntries.Count));
        foreach (var (tid, ttype, tcount, tval) in allEntries)
        {
            newIfd0.AddRange(Pack16(le, tid));
            newIfd0.AddRange(Pack16(le, ttype));
            newIfd0.AddRange(Pack32(le, tcount));
            newIfd0.AddRange(tval);
        }

        // next IFD offset
        int oldNextPos = ifd0Abs + 2 + oldCount * 12;
        uint oldNext = Read32(jpeg, oldNextPos, le);
        if (oldNext != 0)
            oldNext += delta;
        newIfd0.AddRange(Pack32(le, oldNext));

        // IFD0 数据区（原样保留）
        int app1End = segStart + totalLen;
        var oldData = jpeg[(oldNextPos + 4)..app1End];

        // 重建 APP1 段
        var newIfd0Arr = newIfd0.ToArray();
        var newTiff = new byte[(int)ifd0Off + newIfd0Arr.Length + oldData.Length];
        Array.Copy(jpeg, tiffStart, newTiff, 0, (int)ifd0Off);
        Array.Copy(newIfd0Arr, 0, newTiff, (int)ifd0Off, newIfd0Arr.Length);
        Array.Copy(oldData, 0, newTiff, (int)ifd0Off + newIfd0Arr.Length, oldData.Length);

        var newPayload = new byte[ExifPrefix.Length + newTiff.Length];
        ExifPrefix.CopyTo(newPayload, 0);
        newTiff.CopyTo(newPayload, ExifPrefix.Length);

        var newApp1 = new byte[4 + newPayload.Length];
        newApp1[0] = 0xFF;
        newApp1[1] = 0xE1;
        BinaryPrimitives.WriteUInt16BigEndian(newApp1.AsSpan(2, 2), (ushort)(newPayload.Length + 2));
        newPayload.CopyTo(newApp1, 4);

        var result = new byte[jpeg.Length - totalLen + newApp1.Length];
        Array.Copy(jpeg, 0, result, 0, segStart);
        Array.Copy(newApp1, 0, result, segStart, newApp1.Length);
        Array.Copy(jpeg, app1End, result, segStart + newApp1.Length, jpeg.Length - app1End);
        return result;
    }
}
