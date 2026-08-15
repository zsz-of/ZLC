using System.Runtime.InteropServices;
using System.Text;
using ZLivePhoto.Core.Formats;

namespace ZLivePhoto.Core;

/// <summary>
/// Native 导出接口：供 Windows P/Invoke 和 Android JNI 调用。
/// 所有函数使用 C 调用约定，字符串为 UTF-8。
/// </summary>
public static unsafe class NativeExports
{
    // ---------------------------------------------------------------- 辅助

    private static byte[] ToUtf8(string s) => Encoding.UTF8.GetBytes(s + '\0');

    private static IntPtr AllocUtf8(string s)
    {
        var bytes = ToUtf8(s);
        var ptr = Marshal.AllocHGlobal(bytes.Length);
        Marshal.Copy(bytes, 0, ptr, bytes.Length);
        return ptr;
    }

    private static string FromUtf8(IntPtr ptr)
    {
        if (ptr == IntPtr.Zero) return string.Empty;
        int len = 0;
        while (Marshal.ReadByte(ptr, len) != 0) len++;
        var bytes = new byte[len];
        Marshal.Copy(ptr, bytes, 0, len);
        return Encoding.UTF8.GetString(bytes);
    }

    // ---------------------------------------------------------------- 导出

    /// <summary>获取支持的格式数量。</summary>
    [UnmanagedCallersOnly(EntryPoint = "zlp_get_format_count")]
    public static int GetFormatCount() => FormatRegistry.Plugins.Count;

    /// <summary>获取第 index 个格式的名称（如 "google"）。调用方需用 zlp_free_string 释放。</summary>
    [UnmanagedCallersOnly(EntryPoint = "zlp_get_format_name")]
    public static IntPtr GetFormatName(int index)
    {
        if (index < 0 || index >= FormatRegistry.Plugins.Count)
            return IntPtr.Zero;
        return AllocUtf8(FormatRegistry.Plugins[index].Name);
    }

    /// <summary>获取第 index 个格式的显示名称。调用方需用 zlp_free_string 释放。</summary>
    [UnmanagedCallersOnly(EntryPoint = "zlp_get_format_display")]
    public static IntPtr GetFormatDisplay(int index)
    {
        if (index < 0 || index >= FormatRegistry.Plugins.Count)
            return IntPtr.Zero;
        return AllocUtf8(FormatRegistry.Plugins[index].Display);
    }

    /// <summary>检测文件格式，返回置信度（0-100）。</summary>
    [UnmanagedCallersOnly(EntryPoint = "zlp_detect_format")]
    public static int DetectFormat(IntPtr pathUtf8)
    {
        var path = FromUtf8(pathUtf8);
        if (string.IsNullOrEmpty(path) || !File.Exists(path))
            return 0;
        var (_, score) = FormatRegistry.DetectBest(path);
        return score;
    }

    /// <summary>检测文件格式，返回格式名称（如 "google"）。调用方需用 zlp_free_string 释放。</summary>
    [UnmanagedCallersOnly(EntryPoint = "zlp_detect_format_name")]
    public static IntPtr DetectFormatName(IntPtr pathUtf8)
    {
        var path = FromUtf8(pathUtf8);
        if (string.IsNullOrEmpty(path) || !File.Exists(path))
            return IntPtr.Zero;
        var (plugin, _) = FormatRegistry.DetectBest(path);
        return plugin is null ? IntPtr.Zero : AllocUtf8(plugin.Name);
    }

    /// <summary>构造结果 JSON（AOT 安全，无反射）。</summary>
    private static IntPtr ResultJson(bool success, List<string>? outputs, string? error)
    {
        using var ms = new MemoryStream();
        using (var w = new System.Text.Json.Utf8JsonWriter(ms))
        {
            w.WriteStartObject();
            w.WriteBoolean("success", success);
            w.WritePropertyName("outputs");
            w.WriteStartArray();
            if (outputs is not null)
                foreach (var o in outputs)
                    w.WriteStringValue(o);
            w.WriteEndArray();
            if (error is null) w.WriteNull("error");
            else w.WriteString("error", error);
            w.WriteEndObject();
        }
        return AllocUtf8(Encoding.UTF8.GetString(ms.ToArray()));
    }

    /// <summary>
    /// 转换文件。
    /// 返回 JSON 字符串：{"success":true,"outputs":["..."],"error":null} 或
    /// {"success":false,"outputs":[],"error":"..."}。
    /// 调用方需用 zlp_free_string 释放。
    /// </summary>
    [UnmanagedCallersOnly(EntryPoint = "zlp_convert_file")]
    public static IntPtr ConvertFile(IntPtr pathUtf8, IntPtr targetUtf8, IntPtr outDirUtf8)
    {
        try
        {
            var path = FromUtf8(pathUtf8);
            var target = FromUtf8(targetUtf8);
            var outDir = FromUtf8(outDirUtf8);

            if (string.IsNullOrEmpty(path) || !File.Exists(path))
                return ResultJson(false, null, "文件不存在");

            if (string.IsNullOrEmpty(target))
                return ResultJson(false, null, "未指定目标格式");

            if (string.IsNullOrEmpty(outDir))
                outDir = Path.GetDirectoryName(path) ?? ".";

            var outputs = Converter.ConvertFile(path, target, outDir,
                (level, msg, tag) => { /* Native 模式无日志回调 */ });

            return ResultJson(true, outputs, null);
        }
        catch (Exception ex)
        {
            return ResultJson(false, null, ex.Message);
        }
    }

    /// <summary>释放 zlp_* 函数分配的字符串。</summary>
    [UnmanagedCallersOnly(EntryPoint = "zlp_free_string")]
    public static void FreeString(IntPtr ptr)
    {
        if (ptr != IntPtr.Zero)
            Marshal.FreeHGlobal(ptr);
    }

    /// <summary>获取核心库版本号。调用方需用 zlp_free_string 释放。</summary>
    [UnmanagedCallersOnly(EntryPoint = "zlp_get_version")]
    public static IntPtr GetVersion() => AllocUtf8("2.0.0");
}
