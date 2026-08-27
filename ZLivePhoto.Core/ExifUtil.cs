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

    /// <summary>检测 JPEG 的 IFD0 / ExifIFD 中是否存在指定 EXIF 标签。
    /// 小米相机把 0x8897 写在 ExifIFD（0x8769 子 IFD）而非 IFD0，两处都要扫。</summary>
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
            int exifIfdAbs = -1;
            for (int i = 0; i < count; i++)
            {
                int entryOff = ifd0Abs + 2 + i * 12;
                ushort tag = Read16(jpeg, entryOff, le);
                if (tag == tagId) return true;
                if (tag == 0x8769)
                    exifIfdAbs = tiffStart + (int)Read32(jpeg, entryOff + 8, le);
            }
            if (exifIfdAbs > tiffStart)
            {
                ushort exifCount = Read16(jpeg, exifIfdAbs, le);
                for (int i = 0; i < exifCount; i++)
                {
                    if (Read16(jpeg, exifIfdAbs + 2 + i * 12, le) == tagId)
                        return true;
                }
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
    /// 在 EXIF 的 ExifIFD 中添加一个标签（仅支持 inline 值：BYTE/SHORT/LONG）。
    /// 与小米相机行为一致（0x8897 写在 ExifIFD 而非 IFD0）。
    ///
    /// 采用「追加 + 指针改写」策略，绝不移动既有数据（零损坏风险）：
    /// - 已有 ExifIFD：新 ExifIFD（旧 entry 逐字节复制 + 新 entry）追加到段尾，
    ///   仅改写 IFD0 中 0x8769 指针的 inline 值；已有同 tag 则原位改写其值。
    /// - 无 ExifIFD：新 IFD0（旧 entry 逐字节复制 + 0x8769 指针）与新 ExifIFD 追加到段尾，
    ///   仅改写 TIFF 头的 IFD0 偏移。
    /// - 无 EXIF 段：创建最小 APP1 插到 SOI 后。
    /// 旧数据（GPS/ExifIFD/MakerNote/缩略图）全部保持原偏移。
    /// 段长超 64KB 或解析失败时返回原 jpeg（识别仍可靠 XMP 双标签兜底）。
    /// </summary>
    public static byte[] AddExifIfdTag(byte[] jpeg, int tagId, int tagType, int value)
    {
        if (!TypeSizes.ContainsKey(tagType))
            throw new ExifException($"不支持的 TIFF 类型 {tagType}");

        var found = FindExifApp1(jpeg);
        if (found is null)
        {
            // 创建最小 EXIF：II + IFD0{0x8769→ExifIFD} + ExifIFD{tag}
            const bool le = true;
            int ifd0Size = 2 + 12 + 4; // count + 1 entry + next
            int exifIfdOff = 8 + ifd0Size;
            var ifd0 = Pack16(le, 1).Concat(Pack16(le, 0x8769)).Concat(Pack16(le, 4))
                .Concat(Pack32(le, 1)).Concat(Pack32(le, (uint)exifIfdOff)).Concat(Pack32(le, 0)).ToArray();
            var newEntry = Pack16(le, (ushort)tagId).Concat(Pack16(le, (ushort)tagType))
                .Concat(Pack32(le, 1)).Concat(EncodeInline(le, tagType, value)).ToArray();
            var exifIfd = Pack16(le, 1).Concat(newEntry).Concat(Pack32(le, 0)).ToArray();

            var tiff = "II"u8.ToArray().Concat(Pack16(le, 42)).Concat(Pack32(le, 8))
                .Concat(ifd0).Concat(exifIfd).ToArray();
            var payload = new byte[ExifPrefix.Length + tiff.Length];
            ExifPrefix.CopyTo(payload, 0);
            tiff.CopyTo(payload, ExifPrefix.Length);

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
        try
        {
            bool le = IsLittleEndian(jpeg, tiffStart);
            int ifd0Off = (int)Read32(jpeg, tiffStart + 4, le);
            int ifd0Abs = tiffStart + ifd0Off;
            ushort ifd0Count = Read16(jpeg, ifd0Abs, le);

            // 段尾追加位置（TIFF 相对偏移）：段绝对终点 - TIFF 起点
            int appendRel = (segStart + totalLen) - tiffStart;

            // 找 IFD0 中的 0x8769（ExifIFD 指针）
            int exifPtrEntryOff = -1;
            for (int i = 0; i < ifd0Count; i++)
            {
                int entryOff = ifd0Abs + 2 + i * 12;
                if (Read16(jpeg, entryOff, le) == 0x8769)
                {
                    exifPtrEntryOff = entryOff;
                    break;
                }
            }

            var newEntry = Pack16(le, (ushort)tagId).Concat(Pack16(le, (ushort)tagType))
                .Concat(Pack32(le, 1)).Concat(EncodeInline(le, tagType, value)).ToArray();
            byte[] appended;

            if (exifPtrEntryOff >= 0)
            {
                int exifIfdAbs = tiffStart + (int)Read32(jpeg, exifPtrEntryOff + 8, le);
                ushort exifCount = Read16(jpeg, exifIfdAbs, le);
                // 已有同 tag → 原位改写值（零增长）
                for (int i = 0; i < exifCount; i++)
                {
                    int e = exifIfdAbs + 2 + i * 12;
                    if (Read16(jpeg, e, le) == tagId)
                    {
                        var patched = (byte[])jpeg.Clone();
                        EncodeInline(le, tagType, value).CopyTo(patched, e + 8);
                        return patched;
                    }
                }
                // 新 ExifIFD = 旧 entries 逐字节复制 + 新 entry + next 指针归零
                // （旧 next 指针不可带入 entries 区，否则新 entry 错位 4 字节）
                var oldBytes = jpeg[(exifIfdAbs + 2)..(exifIfdAbs + 2 + exifCount * 12)];
                appended = Pack16(le, (ushort)(exifCount + 1)).Concat(oldBytes)
                    .Concat(newEntry).Concat(Pack32(le, 0)).ToArray();
                if (totalLen + appended.Length > 65535) return jpeg; // APP1 段长上限

                var result = InsertBytes(jpeg, segStart + totalLen, appended);
                // 改写 0x8769 指针 → 新 ExifIFD 偏移（原位，4 字节）
                Pack32(le, (uint)appendRel).CopyTo(result, exifPtrEntryOff + 8);
                UpdateSegLen(result, segStart, totalLen + appended.Length);
                return result;
            }

            // IFD0 无 ExifIFD：新 IFD0（旧 entries + 0x8769 + next 归零）+ 新 ExifIFD
            var oldIfd0Bytes = jpeg[(ifd0Abs + 2)..(ifd0Abs + 2 + ifd0Count * 12)];
            int newIfd0Size = 2 + (ifd0Count + 1) * 12 + 4;
            int exifIfdOffNew = appendRel + newIfd0Size;
            var ptrEntry = Pack16(le, 0x8769).Concat(Pack16(le, 4))
                .Concat(Pack32(le, 1)).Concat(Pack32(le, (uint)exifIfdOffNew)).ToArray();
            var newIfd0 = Pack16(le, (ushort)(ifd0Count + 1)).Concat(oldIfd0Bytes)
                .Concat(ptrEntry).Concat(Pack32(le, 0)).ToArray();
            var newExifIfd = Pack16(le, 1).Concat(newEntry).Concat(Pack32(le, 0)).ToArray();
            appended = newIfd0.Concat(newExifIfd).ToArray();
            if (totalLen + appended.Length > 65535) return jpeg; // APP1 段长上限

            var result2 = InsertBytes(jpeg, segStart + totalLen, appended);
            // 改写 TIFF 头 IFD0 偏移（原位，4 字节）
            Pack32(le, (uint)appendRel).CopyTo(result2, tiffStart + 4);
            UpdateSegLen(result2, segStart, totalLen + appended.Length);
            return result2;
        }
        catch
        {
            // 解析失败：返回原 jpeg，识别兜底靠 XMP 双标签
            return jpeg;
        }
    }

    /// <summary>按字节序编码 4 字节 inline 值（BYTE/SHORT/LONG）。</summary>
    private static byte[] EncodeInline(bool le, int tagType, int value) => tagType switch
    {
        1 => le ? [(byte)value, 0, 0, 0] : [0, 0, 0, (byte)value],
        3 => Pack16(le, (ushort)value).Concat(new byte[2]).ToArray(),
        4 => Pack32(le, (uint)value),
        _ => throw new ExifException($"不支持的 inline 类型 {tagType}")
    };

    /// <summary>在 pos 处插入 bytes，返回新数组。</summary>
    private static byte[] InsertBytes(byte[] src, int pos, byte[] bytes)
    {
        var outArr = new byte[src.Length + bytes.Length];
        Array.Copy(src, 0, outArr, 0, pos);
        Array.Copy(bytes, 0, outArr, pos, bytes.Length);
        Array.Copy(src, pos, outArr, pos + bytes.Length, src.Length - pos);
        return outArr;
    }

    /// <summary>更新 APP1 段长度字段；超 64KB 返回 false。</summary>
    private static bool UpdateSegLen(byte[] jpeg, int segStart, int newTotal)
    {
        if (newTotal > 65535) return false;
        BinaryPrimitives.WriteUInt16BigEndian(jpeg.AsSpan(segStart + 2, 2), (ushort)newTotal);
        return true;
    }
}
