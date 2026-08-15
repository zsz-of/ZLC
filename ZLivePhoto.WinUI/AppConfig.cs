using System.Text.Json;

namespace ZLivePhoto.WinUI;

/// <summary>
/// zAPP 配置（%USERPROFILE%\.zAPP\zlivephoto_config.json），与 Python 版键名兼容。
/// 读取失败静默回退默认值。
/// </summary>
public sealed class AppConfig
{
    public string Target { get; set; } = "google";
    public string OutDir { get; set; } = "";
    public int Width { get; set; } = 900;
    public int Height { get; set; } = 700;
    public int? X { get; set; }
    public int? Y { get; set; }

    private const string ConfigFile = "zlivephoto_config.json";

    private static string ZappDir()
    {
        string dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".zAPP");
        Directory.CreateDirectory(dir);
        return dir;
    }

    private static string ConfigPath => Path.Combine(ZappDir(), ConfigFile);

    public static AppConfig Load()
    {
        var cfg = new AppConfig();
        try
        {
            string path = ConfigPath;
            if (!File.Exists(path)) return cfg;

            using var doc = JsonDocument.Parse(File.ReadAllText(path));
            var root = doc.RootElement;
            if (root.ValueKind != JsonValueKind.Object) return cfg;

            if (root.TryGetProperty("target", out var t) && t.ValueKind == JsonValueKind.String)
                cfg.Target = t.GetString() ?? "google";
            if (root.TryGetProperty("out_dir", out var o) && o.ValueKind == JsonValueKind.String)
                cfg.OutDir = o.GetString() ?? "";

            // geometry: "WxH+X+Y"（与 Python/tkinter 版一致）
            if (root.TryGetProperty("geometry", out var g) && g.ValueKind == JsonValueKind.String)
            {
                var geo = g.GetString();
                if (!string.IsNullOrEmpty(geo))
                {
                    var m = System.Text.RegularExpressions.Regex.Match(
                        geo, @"^(\d+)x(\d+)([+-]\d+)?([+-]\d+)?$");
                    if (m.Success)
                    {
                        cfg.Width = int.Parse(m.Groups[1].Value);
                        cfg.Height = int.Parse(m.Groups[2].Value);
                        if (m.Groups[3].Success && int.TryParse(m.Groups[3].Value, out int gx))
                        {
                            // 屏幕外坐标（如最小化的 -32000）不恢复
                            if (gx > -10000) cfg.X = gx;
                        }
                        if (m.Groups[4].Success && int.TryParse(m.Groups[4].Value, out int gy))
                        {
                            if (gy > -10000) cfg.Y = gy;
                        }
                    }
                }
            }
        }
        catch { /* 配置损坏静默回退默认 */ }
        return cfg;
    }

    public void Save()
    {
        try
        {
            string geo = X is int x && Y is int y
                ? $"{Width}x{Height}+{x}+{y}"
                : $"{Width}x{Height}";

            using var ms = new MemoryStream();
            using (var w = new Utf8JsonWriter(ms, new JsonWriterOptions { Indented = true }))
            {
                w.WriteStartObject();
                w.WriteString("target", Target);
                w.WriteString("out_dir", OutDir);
                w.WriteString("geometry", geo);
                w.WriteEndObject();
            }

            string tmp = ConfigPath + ".tmp";
            File.WriteAllBytes(tmp, ms.ToArray());
            File.Move(tmp, ConfigPath, overwrite: true);
        }
        catch { /* 写入失败不阻塞退出 */ }
    }

    public static void Delete()
    {
        try { File.Delete(ConfigPath); }
        catch { /* 不存在则忽略 */ }
    }
}
