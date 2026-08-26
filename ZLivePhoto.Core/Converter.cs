using Microsoft.Win32.SafeHandles;
using System.Runtime.InteropServices;
using System.Text;
using ZLivePhoto.Core.Formats;

namespace ZLivePhoto.Core;

/// <summary>
/// 转换管线：detect → read → write。同格式转换 = 原样复制（零损耗直通）。
/// </summary>
public static class Converter
{
    public sealed class ConvertException(string message) : Exception(message);

    /// <summary>
    /// 转换单个文件为指定格式，返回输出文件路径列表。
    /// </summary>
    /// <param name="path">源文件路径</param>
    /// <param name="target">目标格式：google | apple | oppo | vivo | xiaomi</param>
    /// <param name="outDir">输出目录</param>
    /// <param name="log">日志回调 (level, message, tag)</param>
    /// <param name="options">选项：google_mp_suffix (bool)</param>
    public static List<string> ConvertFile(string path, string target, string outDir,
        Action<string, string, string> log, Dictionary<string, object>? options = null)
    {
        if (!FormatRegistry.ByName.TryGetValue(target, out var targetPlugin))
            throw new ConvertException($"未知目标格式：{target}");

        var (plugin, score) = FormatRegistry.DetectBest(path);
        if (plugin is null || score < 50)
            throw new ConvertException("无法识别的动态照片格式（非 Google/OPPO/vivo/小米/Apple 动态照片）");

        log("info", $"识别为 {plugin.Display}（置信度 {score}）", "转换");

        string stem = Path.GetFileNameWithoutExtension(path);
        Directory.CreateDirectory(outDir);
        options ??= new Dictionary<string, object>();

        // 同格式直通：原样复制，零损耗
        // 例外 vivo_single：源可能是 vivo 相册「关闭实况」的合并产物（MotionPhoto="0"），
        // 需走完整写出流程修复回 "1" 恢复动态效果
        if (plugin.Name == target && plugin.Name != "vivo_single")
        {
            var directOuts = new List<string>();
            string dst = Path.Combine(outDir, Path.GetFileName(path));
            File.Copy(path, dst, overwrite: true);
            directOuts.Add(dst);

            if (plugin.Name == "vivo")
            {
                string mp4 = Path.Combine(Path.GetDirectoryName(path)!,
                    Path.GetFileNameWithoutExtension(path) + ".mp4");
                if (File.Exists(mp4))
                {
                    string dstMp4 = Path.Combine(outDir, Path.GetFileName(mp4));
                    File.Copy(mp4, dstMp4, overwrite: true);
                    directOuts.Add(dstMp4);
                }
            }
            else if (plugin.Name == "apple")
            {
                string mov = Path.Combine(Path.GetDirectoryName(path)!,
                    Path.GetFileNameWithoutExtension(path) + ".mov");
                if (File.Exists(mov))
                {
                    string dstMov = Path.Combine(outDir, Path.GetFileName(mov));
                    File.Copy(mov, dstMov, overwrite: true);
                    directOuts.Add(dstMov);
                }
            }
            log("info", "源与目标格式相同，已原样复制（零损耗）", "转换");
            return directOuts;
        }

        var asset = plugin.Read(path, log);
        if (asset.PresentationTsUs < 0)
            log("warning", "源缺少封面帧时间戳，按规范回退为视频中点", "转换");

        var outputs = targetPlugin.Write(asset, outDir, stem, log, options);

        // 保留源文件的时间戳（创建时间、修改时间、访问时间）
        foreach (var outPath in outputs)
        {
            try
            {
                CopyTimestamps(path, outPath);
            }
            catch { /* 时间戳复制失败不阻塞转换 */ }
        }

        return outputs;
    }

    private static void CopyTimestamps(string src, string dst)
    {
        var st = File.GetLastWriteTimeUtc(src);
        var at = File.GetLastAccessTimeUtc(src);
        File.SetLastWriteTimeUtc(dst, st);
        File.SetLastAccessTimeUtc(dst, at);

        // Windows 创建时间
        if (OperatingSystem.IsWindows())
        {
            try
            {
                var ct = File.GetCreationTimeUtc(src);
                SetFileCreationTime(dst, ct);
            }
            catch { /* 创建时间设置失败 */ }
        }
    }

    // 只设置创建时间，传 null 保留已设置的写入/访问时间
    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern unsafe bool SetFileTime(SafeFileHandle hFile,
        long* lpCreationTime, long* lpLastAccessTime, long* lpLastWriteTime);

    private static void SetFileCreationTime(string path, DateTime creationTimeUtc)
    {
        using var handle = File.OpenHandle(path, FileMode.Open, FileAccess.Write, FileShare.None);
        long ft = creationTimeUtc.ToFileTimeUtc();
        unsafe { SetFileTime(handle, &ft, null, null); }
    }
}
