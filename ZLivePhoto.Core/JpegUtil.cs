using System.Buffers.Binary;
using System.Text;

namespace ZLivePhoto.Core;

public sealed class JpegException(string message) : Exception(message);

/// <summary>
/// JPEG 段级工具：marker 扫描、EOI 定位、XMP APP1 替换/插入。
/// 所有操作均为字节级，不重编码，保证图像数据无损。
/// </summary>
public static class JpegUtil
{
    public static readonly byte[] XmpApp1Prefix = "http://ns.adobe.com/xap/1.0/\0"u8.ToArray();

    // 无长度字段的独立 marker
    private static readonly HashSet<byte> StandaloneMarkers =
        [0x01, 0xD8, 0xD9, .. Enumerable.Range(0xD0, 8).Select(x => (byte)x)];

    /// <summary>
    /// 遍历 JPEG 头部段（SOS 之前）。
    /// 返回 (marker, segStart, totalLen, payloadStart, payloadLen)。
    /// totalLen 含 marker 2 字节与长度 2 字节。SOS 时停止。
    /// </summary>
    public static IEnumerable<(byte marker, int segStart, int totalLen, int payloadStart, int payloadLen)>
        IterateSegments(byte[] data, int start = 0)
    {
        if (data.Length - start < 4 || data[start] != 0xFF || data[start + 1] != 0xD8)
            throw new JpegException("不是有效的 JPEG（缺少 SOI）");

        yield return (0xD8, start, 2, start + 2, 0);
        int pos = start + 2;
        int size = data.Length;

        while (pos + 4 <= size)
        {
            if (data[pos] != 0xFF)
                throw new JpegException($"段边界错位 @{pos}");

            byte marker = data[pos + 1];
            if (StandaloneMarkers.Contains(marker))
            {
                yield return (marker, pos, 2, pos + 2, 0);
                pos += 2;
                continue;
            }

            ushort segLen = BinaryPrimitives.ReadUInt16BigEndian(data.AsSpan(pos + 2, 2));
            if (segLen < 2 || pos + 2 + segLen > size)
                throw new JpegException($"段长度非法 @{pos}");

            yield return (marker, pos, 2 + segLen, pos + 4, segLen - 2);
            pos += 2 + segLen;

            if (marker == 0xDA) // SOS
                yield break;
        }
    }

    /// <summary>
    /// 从 SOS 段有效载荷终点扫描熵编码数据，返回 EOI(FFD9) 之后的偏移。
    /// 熵编码规则：FF 00 为转义字面量；FF D0-D7 为重启 marker；FF D9 为 EOI。
    /// </summary>
    public static int FindEoiEnd(byte[] data, int sosPayloadEnd)
    {
        int i = sosPayloadEnd;
        int size = data.Length;

        while (i + 1 < size)
        {
            if (data[i] == 0xFF)
            {
                byte nxt = data[i + 1];
                if (nxt == 0x00 || (nxt >= 0xD0 && nxt <= 0xD7))
                {
                    i += 2;
                    continue;
                }
                if (nxt == 0xD9)
                    return i + 2;
                i += 2; // 其他 marker 按 2 字节跳过
                continue;
            }
            i++;
        }
        throw new JpegException("未找到 EOI（文件可能损坏）");
    }

    /// <summary>
    /// 把可能由多个 JPEG 顺序拼接的数据拆成单个 JPEG 字节块列表。
    /// 返回 (jpegList, consumed)。剩余非 JPEG 字节不在结果中。
    /// </summary>
    public static (List<byte[]> jpegs, int consumed) SplitJpegs(byte[] data)
    {
        var result = new List<byte[]>();
        int pos = 0;
        int size = data.Length;

        while (pos + 4 <= size && data[pos] == 0xFF && data[pos + 1] == 0xD8)
        {
            int? sosPayloadEnd = null;
            foreach (var (marker, _, _, payloadStart, payloadLen) in IterateSegments(data, pos))
            {
                if (marker == 0xDA)
                {
                    sosPayloadEnd = payloadStart + payloadLen;
                    break;
                }
            }
            if (sosPayloadEnd is null)
                throw new JpegException("JPEG 缺少 SOS 段");

            int eoiEnd = FindEoiEnd(data, sosPayloadEnd.Value);
            result.Add(data[pos..eoiEnd]);
            pos = eoiEnd;

            // 跳过后续 JPEG 之间可能的填充 0xFF
            while (pos < size && data[pos] == 0xFF && pos + 1 < size && data[pos + 1] == 0xFF)
                pos++;
        }
        return (result, pos);
    }

    /// <summary>
    /// 从 SOF0/SOF2 段读取图像尺寸，返回 (width, height)；失败返回 (0, 0)。
    /// </summary>
    public static (int width, int height) GetDimensions(byte[] jpeg)
    {
        foreach (var (marker, _, _, payloadStart, payloadLen) in IterateSegments(jpeg))
        {
            if (marker is 0xC0 or 0xC1 or 0xC2 or 0xC3 or 0xC5 or 0xC6 or 0xC7
                or 0xC9 or 0xCA or 0xCB or 0xCD or 0xCE or 0xCF)
            {
                if (payloadLen >= 5)
                {
                    ushort height = BinaryPrimitives.ReadUInt16BigEndian(jpeg.AsSpan(payloadStart + 1, 2));
                    ushort width = BinaryPrimitives.ReadUInt16BigEndian(jpeg.AsSpan(payloadStart + 3, 2));
                    return (width, height);
                }
            }
        }
        return (0, 0);
    }

    /// <summary>
    /// 定位 XMP APP1 段，返回 (segStart, totalLen, xmpText)；无则 null。
    /// </summary>
    public static (int segStart, int totalLen, string xmpText)? FindXmpSegment(byte[] jpeg)
    {
        foreach (var (marker, segStart, totalLen, payloadStart, payloadLen) in IterateSegments(jpeg))
        {
            if (marker == 0xE1 &&
                jpeg.AsSpan(payloadStart, XmpApp1Prefix.Length).SequenceEqual(XmpApp1Prefix))
            {
                var xmpBytes = jpeg.AsSpan(payloadStart + XmpApp1Prefix.Length,
                    payloadLen - XmpApp1Prefix.Length);
                return (segStart, totalLen, Encoding.UTF8.GetString(xmpBytes));
            }
        }
        return null;
    }

    /// <summary>
    /// 构造 XMP APP1 段字节。
    /// </summary>
    public static byte[] BuildXmpApp1(string xmpText)
    {
        var xmpBytes = Encoding.UTF8.GetBytes(xmpText);
        var payload = new byte[XmpApp1Prefix.Length + xmpBytes.Length];
        XmpApp1Prefix.CopyTo(payload, 0);
        xmpBytes.CopyTo(payload, XmpApp1Prefix.Length);

        var result = new byte[4 + payload.Length];
        result[0] = 0xFF;
        result[1] = 0xE1;
        BinaryPrimitives.WriteUInt16BigEndian(result.AsSpan(2, 2), (ushort)(payload.Length + 2));
        payload.CopyTo(result, 4);
        return result;
    }

    /// <summary>
    /// 替换已有 XMP APP1 段；不存在则插入到第一个 APP1(Exif) 之后（无 APP1 则紧随 SOI）。
    /// </summary>
    public static byte[] ReplaceOrInsertXmp(byte[] jpeg, string newXmpText)
    {
        var newSeg = BuildXmpApp1(newXmpText);
        var found = FindXmpSegment(jpeg);

        if (found is not null)
        {
            var (segStart, totalLen, _) = found.Value;
            var result = new byte[jpeg.Length - totalLen + newSeg.Length];
            Array.Copy(jpeg, 0, result, 0, segStart);
            Array.Copy(newSeg, 0, result, segStart, newSeg.Length);
            Array.Copy(jpeg, segStart + totalLen, result, segStart + newSeg.Length,
                jpeg.Length - segStart - totalLen);
            return result;
        }

        // 插入位置：第一个 APP1(Exif) 之后，否则紧随 SOI
        int insertAt = 2;
        foreach (var (marker, segStart, totalLen, payloadStart, _) in IterateSegments(jpeg))
        {
            if (StandaloneMarkers.Contains(marker))
                continue;
            if (marker == 0xE1 && jpeg.AsSpan(payloadStart, 6).SequenceEqual("Exif\0\0"u8))
                insertAt = segStart + totalLen;
            break; // 只看第一个非独立段
        }

        var final = new byte[jpeg.Length + newSeg.Length];
        Array.Copy(jpeg, 0, final, 0, insertAt);
        Array.Copy(newSeg, 0, final, insertAt, newSeg.Length);
        Array.Copy(jpeg, insertAt, final, insertAt + newSeg.Length, jpeg.Length - insertAt);
        return final;
    }
}
