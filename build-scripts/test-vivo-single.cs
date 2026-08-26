// vivo_single 合成测试：构造最小 JPEG+MP4 → google 写出 → 转 vivo_single → detect 回读
#:project ../ZLivePhoto.Core/ZLivePhoto.Core.csproj
using ZLivePhoto.Core;
using ZLivePhoto.Core.Formats;
using ZLivePhoto.Core.Models;

string tmp = Path.Combine(Path.GetTempPath(), "zlpc_vivo_single_test");
if (Directory.Exists(tmp)) Directory.Delete(tmp, true);
Directory.CreateDirectory(tmp);

// ── 最小合法 JPEG（SOI + APP0 + SOF0 16x16 灰度 + SOS + 熵数据 + EOI）──
byte[] jpeg =
[
    0xFF, 0xD8,                                             // SOI
    0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00,   // APP0 JFIF
    0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
    0xFF, 0xC0, 0x00, 0x0B, 0x08, 0x00, 0x10, 0x00, 0x10,   // SOF0 16x16 8bit
    0x01, 0x01, 0x11, 0x00,
    0xFF, 0xDA, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, // SOS 单分量
    0x00, 0x11, 0x22, 0x33,                                 // 熵编码数据（示意）
    0xFF, 0xD9,                                             // EOI
];

// ── 最小 MP4（ftyp + moov 空盒，供 lpex 插入）──
byte[] MakeBox(string type, byte[] payload)
{
    var size = 8 + payload.Length;
    var box = new byte[size];
    box[0] = (byte)(size >> 24); box[1] = (byte)(size >> 16);
    box[2] = (byte)(size >> 8); box[3] = (byte)size;
    System.Text.Encoding.ASCII.GetBytes(type).CopyTo(box, 4);
    payload.CopyTo(box, 8);
    return box;
}
byte[] mp4 = [.. MakeBox("ftyp", "isom"u8.ToArray()
    .Concat("isom"u8.ToArray()).Concat("iso2"u8.ToArray())
    .Concat(new byte[4] { 0, 0, 2, 0 }).ToArray()),
    .. MakeBox("moov", [])];

var asset = new LivePhotoAsset
{
    PrimaryJpeg = jpeg,
    GainmapJpeg = null,
    VideoMp4 = mp4,
    SourceFormat = "google",
    PresentationTsUs = 1_000_000,
    VideoInfo = new() { ["width"] = 16, ["height"] = 16, ["rotation"] = 0 }
};

// 带 EXIF（含 GPS/拍摄参数）的 JPEG：验证 vivo_single 转换后元数据透传保留
// EXIF APP1: "Exif\0\0" + TIFF 头（小端）+ GPS IFD 标记 + Make/Camera 示意
byte[] exifPayload =
[
    0x45, 0x78, 0x69, 0x66, 0x00, 0x00,             // "Exif\0\0"
    0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00, // TIFF little-endian + IFD0 offset
    0x01, 0x00,                                     // IFD0: 1 个条目
    0x0F, 0x01, 0x02, 0x00, 0x04, 0x00, 0x00, 0x00, // Tag=0x010F Make, Type=ASCII, Count=4
    0x47, 0x50, 0x53, 0x00,                         // "GPS\0"（值内联，充当示意的元数据）
    0x00, 0x00, 0x00, 0x00                          // next IFD = 0
];
byte[] exifJpeg =
[
    .. jpeg[..2],                                    // SOI
    0xFF, 0xE1, (byte)(exifPayload.Length + 2 >> 8), (byte)(exifPayload.Length + 2), // APP1 EXIF
    .. exifPayload,
    .. jpeg[2..],                                    // 其余段
];
var assetExif = new LivePhotoAsset
{
    PrimaryJpeg = exifJpeg,
    VideoMp4 = mp4,
    SourceFormat = "google",
    PresentationTsUs = 1_000_000,
    VideoInfo = new() { ["width"] = 16, ["height"] = 16, ["rotation"] = 0 }
};

int pass = 0, fail = 0;
void Check(string name, bool ok)
{
    if (ok) { pass++; Console.WriteLine($"  PASS  {name}"); }
    else { fail++; Console.WriteLine($"  FAIL  {name}"); }
}

// 1) google 写出（基准源文件）
var googlePlugin = FormatRegistry.ByName["google"]!;
var outs1 = googlePlugin.Write(asset, tmp, "gsrc", (_, _, _) => { }, new());
Check("google 写出成功", outs1.Count == 1 && File.Exists(outs1[0]));
string googleFile = outs1[0];

// 2) google → vivo_single 转换 + 回读识别
var outs2 = Converter.ConvertFile(googleFile, "vivo_single", tmp,
    (lvl, msg, tag) => { if (lvl == "error") Console.WriteLine($"    [err][{tag}] {msg}"); });
Check("google→vivo_single 转换产出 1 文件", outs2.Count == 1);
var (p2, s2) = FormatRegistry.DetectBest(outs2[0]);
Check($"google→vivo_single 回读识别 = vivo_single（实际 {p2?.Name}/{s2}）", p2?.Name == "vivo_single" && s2 >= 85);

// 3) vivo_single 同格式转换（修复路径：不走直通，完整重写）
var outs3 = Converter.ConvertFile(outs2[0], "vivo_single", tmp,
    (lvl, msg, tag) => { if (lvl == "error") Console.WriteLine($"    [err][{tag}] {msg}"); });
Check("vivo_single→vivo_single 修复转换产出 1 文件", outs3.Count == 1);
var (p3, s3) = FormatRegistry.DetectBest(outs3[0]);
Check($"vivo_single 修复后仍识别 = vivo_single（实际 {p3?.Name}/{s3}）", p3?.Name == "vivo_single");

// 4) 结构校验：XMP 含 VCamera + MotionPhoto="1"；文件尾 cameralbum footer；lpex 在 moov
var data = File.ReadAllBytes(outs2[0]);
var head = System.Text.Encoding.ASCII.GetString(data, 0, Math.Min(65536, data.Length));
Check("XMP 含 ns.vivo.com/photos（VCamera）", head.Contains("ns.vivo.com/photos"));
Check("XMP GCamera:MotionPhoto=\"1\"", head.Contains("GCamera:MotionPhoto=\"1\""));
Check("moov 内含 lpex box", head.Contains("lpex"));
Check("文件尾 cameralbum! magic", data.AsSpan(data.Length - 11).SequenceEqual(
    new byte[] { 0x1b, 0x2a, 0x39, 0x48, 0x57, 0x66, 0x75, 0x84, 0x93, 0xa2, 0xb3 }));

// 5) vivo 双文件 → vivo_single
var vivoPlugin = FormatRegistry.ByName["vivo"]!;
// vivo 双文件 read 需要配对 mp4 + footer，直接用 google 源合成太复杂；
// 用 google 文件走 Converter 转 vivo（读 google → 写 vivo 双文件）
var outs4 = Converter.ConvertFile(googleFile, "vivo", tmp, (_, _, _) => { });
if (outs4.Count >= 1)
{
    var outs5 = Converter.ConvertFile(outs4[0], "vivo_single", tmp,
        (lvl, msg, tag) => { if (lvl == "error") Console.WriteLine($"    [err][{tag}] {msg}"); });
    Check("vivo双文件→vivo_single 转换产出 1 文件", outs5.Count == 1);
    if (outs5.Count == 1)
    {
        var (p5, s5) = FormatRegistry.DetectBest(outs5[0]);
        Check($"vivo双文件→vivo_single 回读识别（实际 {p5?.Name}/{s5}）", p5?.Name == "vivo_single");
    }
}
else Check("vivo 双文件写出", false);

// 6) OPPO 源 → vivo_single（OPPO 同为单文件，detect 让位逻辑验证）
var outs6 = Converter.ConvertFile(googleFile, "oppo", tmp, (_, _, _) => { });
var outs7 = Converter.ConvertFile(outs6[0], "vivo_single", tmp,
    (lvl, msg, tag) => { if (lvl == "error") Console.WriteLine($"    [err][{tag}] {msg}"); });
Check("oppo→vivo_single 转换产出 1 文件", outs7.Count == 1);
var (p7, s7) = FormatRegistry.DetectBest(outs7[0]);
Check($"oppo→vivo_single 回读识别（实际 {p7?.Name}/{s7}）", p7?.Name == "vivo_single");

// 7) EXIF 元数据保留：带 EXIF APP1（拍摄参数/GPS）的 JPEG 转换后字节级透传
var outsExifSrc = googlePlugin.Write(assetExif, tmp, "exifsrc", (_, _, _) => { }, new());
var outsExif = Converter.ConvertFile(outsExifSrc[0], "vivo_single", tmp, (_, _, _) => { });
var exifOut = File.ReadAllBytes(outsExif[0]);
// 在输出文件中定位 EXIF 段（FF E1 + 长度 + "Exif\0\0"）并比对整段字节
bool exifKept = false;
for (int i = 0; i < exifOut.Length - 14; i++)
{
    if (exifOut[i] == 0xFF && exifOut[i + 1] == 0xE1 &&
        System.Text.Encoding.ASCII.GetString(exifOut, i + 4, 6) == "Exif\0\0")
    {
        ushort len = (ushort)((exifOut[i + 2] << 8) | exifOut[i + 3]);
        exifKept = len == exifPayload.Length + 2 &&
                   exifOut.AsSpan(i + 4, len - 2).SequenceEqual(exifPayload);
        break;
    }
}
Check("EXIF 段（拍摄/GPS 元数据）字节级保留", exifKept);
var (pE, sE) = FormatRegistry.DetectBest(outsExif[0]);
Check($"带 EXIF 源回读识别（实际 {pE?.Name}/{sE}）", pE?.Name == "vivo_single");

Console.WriteLine($"\n结果：PASS={pass} FAIL={fail}");
return fail == 0 ? 0 : 1;
