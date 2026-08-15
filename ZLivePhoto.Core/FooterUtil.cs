using System.Buffers.Binary;
using System.Text;
using System.Text.Json;

namespace ZLivePhoto.Core;

public sealed class FooterException(string message) : Exception(message);

/// <summary>
/// vivo/OPPO 共用的 cameralbum! footer 编解码。
/// </summary>
public static class FooterUtil
{
    public static readonly byte[] Magic = [0x1B, 0x2A, 0x39, 0x48, 0x57, 0x66, 0x75, 0x84, 0x93, 0xA2, 0xB3];
    public static readonly byte[] Marker = "cameralbum!"u8.ToArray();
    public static readonly byte[] VivoPrefix = "vivo"u8.ToArray();
    public static readonly byte[] ExtPrefix = "vivoMediaExtInfovivo"u8.ToArray(); // vivoMediaExtInfo + vivo
    public const int IdLen = 28;

    /// <summary>OPPO 内嵌单文件使用的固定 ID（'motionphoto' + '0'*17）</summary>
    public static readonly string OppoFixedId = "motionphoto" + new string('0', 17);

    public sealed class FooterInfo
    {
        public JsonElement? Json { get; set; }
        public string? Id { get; set; }
        public byte[]? Prefix { get; set; }
        public int FooterStart { get; set; }
        public int? Version { get; set; }
        public long? ImageTime { get; set; }
        public string? LivephotoId { get; set; }
    }

    /// <summary>
    /// 生成 vivo 风格 livephoto ID：'-<数字><8位随机字符>'，'0' 填充至 28 字符。
    /// </summary>
    public static string GenerateLivephotoId()
    {
        var num = Random.Shared.Next(1, 2147483647);
        const string chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        var rand = new string([.. Enumerable.Range(0, 8).Select(_ => chars[Random.Shared.Next(chars.Length)])]);
        return $"-{num}{rand}".PadRight(IdLen, '0')[..IdLen];
    }

    /// <summary>
    /// 构造完整 footer（含前缀）。
    /// </summary>
    public static byte[] BuildFooter(byte[] jsonBytes, string idStr, byte[] prefix)
    {
        var idBytes = Encoding.ASCII.GetBytes(idStr);
        if (idBytes.Length != IdLen)
        {
            var padded = new byte[IdLen];
            idBytes.CopyTo(padded, 0);
            for (int i = idBytes.Length; i < IdLen; i++) padded[i] = (byte)'0';
            idBytes = padded;
        }

        var tail = new byte[IdLen + 4 + Magic.Length];
        idBytes.CopyTo(tail, 0);
        tail[IdLen] = 0xFF; tail[IdLen + 1] = 0xFF; tail[IdLen + 2] = 0xFF; tail[IdLen + 3] = 0xFF;
        Magic.CopyTo(tail, IdLen + 4);

        var result = new byte[prefix.Length + jsonBytes.Length + 4 + Marker.Length + 4 + tail.Length];
        int pos = 0;
        prefix.CopyTo(result, pos); pos += prefix.Length;
        jsonBytes.CopyTo(result, pos); pos += jsonBytes.Length;
        BinaryPrimitives.WriteUInt32BigEndian(result.AsSpan(pos, 4), (uint)jsonBytes.Length); pos += 4;
        Marker.CopyTo(result, pos); pos += Marker.Length;
        BinaryPrimitives.WriteUInt32BigEndian(result.AsSpan(pos, 4), (uint)(tail.Length + 4)); pos += 4;
        tail.CopyTo(result, pos);
        return result;
    }

    /// <summary>
    /// 按样本键序生成 footer JSON（紧凑分隔符，UTF-8 直出，与 Python ensure_ascii=False 一致）。
    /// AOT 安全：手写 Utf8JsonWriter，无反射。
    /// </summary>
    public static byte[] BuildFooterJson(Dictionary<string, object> fields)
    {
        using var ms = new MemoryStream();
        using (var w = new Utf8JsonWriter(ms, new JsonWriterOptions
        {
            Indented = false,
            Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping
        }))
        {
            w.WriteStartObject();
            foreach (var (key, value) in fields)
            {
                w.WritePropertyName(key);
                WriteJsonValue(w, value);
            }
            w.WriteEndObject();
        }
        return ms.ToArray();
    }

    private static void WriteJsonValue(Utf8JsonWriter w, object value)
    {
        switch (value)
        {
            case string s: w.WriteStringValue(s); break;
            case int i: w.WriteNumberValue(i); break;
            case long l: w.WriteNumberValue(l); break;
            case double d: w.WriteNumberValue(d); break;
            case bool b: w.WriteBooleanValue(b); break;
            case int[] ai:
                w.WriteStartArray();
                foreach (var x in ai) w.WriteNumberValue(x);
                w.WriteEndArray();
                break;
            case long[] al:
                w.WriteStartArray();
                foreach (var x in al) w.WriteNumberValue(x);
                w.WriteEndArray();
                break;
            case double[] ad:
                w.WriteStartArray();
                foreach (var x in ad) w.WriteNumberValue(x);
                w.WriteEndArray();
                break;
            case string[] aStr:
                w.WriteStartArray();
                foreach (var x in aStr) w.WriteStringValue(x);
                w.WriteEndArray();
                break;
            default:
                throw new FooterException($"不支持的 JSON 值类型：{value.GetType().Name}");
        }
    }

    /// <summary>
    /// 从数据尾部解析 footer。
    /// </summary>
    public static FooterInfo? ParseFooter(ReadOnlySpan<byte> data)
    {
        int idx = data.LastIndexOf(Marker);
        if (idx == -1 || idx + 15 + 43 > data.Length)
            return null;

        uint len2 = BinaryPrimitives.ReadUInt32BigEndian(data.Slice(idx + 11, 4));
        var tail = data.Slice(idx + 15, (int)len2 - 4);
        if (tail.Length != len2 - 4 || tail.Length != 43)
            return null;
        if (!tail.Slice(28, 4).SequenceEqual(new byte[] { 0xFF, 0xFF, 0xFF, 0xFF }) ||
            !tail.Slice(32, 11).SequenceEqual(Magic))
            return null;

        string idStr = Encoding.ASCII.GetString(tail.Slice(0, 28));
        if (idx < 4)
            return null;

        uint len1 = BinaryPrimitives.ReadUInt32BigEndian(data.Slice(idx - 4, 4));
        int jsonStart = idx - 4 - (int)len1;
        if (jsonStart < 0)
            return null;

        var jsonBytes = data.Slice(jsonStart, (int)len1);
        JsonElement? payload;
        try
        {
            // Clone 使 JsonElement 脱离 JsonDocument 生命周期
            payload = JsonDocument.Parse(jsonBytes.ToArray()).RootElement.Clone();
        }
        catch
        {
            return null;
        }

        // 前缀识别
        byte[] prefix = [];
        int footerStart = jsonStart;
        if (jsonStart >= 20 && data.Slice(jsonStart - 20, 20).SequenceEqual(ExtPrefix))
        {
            prefix = ExtPrefix;
            footerStart = jsonStart - 20;
        }
        else if (jsonStart >= 4 && data.Slice(jsonStart - 4, 4).SequenceEqual(VivoPrefix))
        {
            prefix = VivoPrefix;
            footerStart = jsonStart - 4;
        }

        var info = new FooterInfo
        {
            Json = payload,
            Id = idStr,
            Prefix = prefix,
            FooterStart = footerStart
        };

        if (payload is { ValueKind: JsonValueKind.Object } root)
        {
            if (root.TryGetProperty("version", out var v) && v.ValueKind == JsonValueKind.Number)
                info.Version = v.GetInt32();
            if (root.TryGetProperty("com.android.camera.imageTime", out var it) && it.ValueKind == JsonValueKind.Number)
                info.ImageTime = it.GetInt64();
            if (root.TryGetProperty("com.android.camera.livephoto", out var lid) && lid.ValueKind == JsonValueKind.String)
                info.LivephotoId = lid.GetString();
        }

        return info;
    }
}
