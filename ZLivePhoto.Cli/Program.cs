using ZLivePhoto.Core;
using ZLivePhoto.Core.Formats;

// zlpc — Z-LivePhoto-Converter 命令行入口
// 用法:
//   zlpc detect <文件>                 识别格式
//   zlpc convert <文件> --to <格式> [--out <目录>]
//   zlpc formats                       列出支持的格式

static void PrintLog(string level, string msg, string tag)
    => Console.Error.WriteLine($"[{level}][{tag}] {msg}");

static int Usage()
{
    Console.Error.WriteLine("""
        用法:
          zlpc detect <文件>                            识别动态照片格式
          zlpc convert <文件> --to <格式> [--out <目录>]  转换动态照片
          zlpc formats                                  列出支持的格式
        格式: google | apple | oppo | vivo_single | vivo | xiaomi | honor
        """);
    return 2;
}

if (args.Length == 0) return Usage();

switch (args[0].ToLowerInvariant())
{
    case "formats":
        foreach (var p in FormatRegistry.Plugins)
            Console.WriteLine($"{p.Name,-10} {p.Display}");
        return 0;

    case "detect":
    {
        if (args.Length < 2) return Usage();
        var path = args[1];
        if (!File.Exists(path))
        {
            Console.Error.WriteLine($"文件不存在: {path}");
            return 1;
        }
        var (plugin, score) = FormatRegistry.DetectBest(path);
        if (plugin is null)
        {
            Console.WriteLine($"unknown (0)");
            return 1;
        }
        Console.WriteLine($"{plugin.Name} ({score})");
        return 0;
    }

    case "convert":
    {
        if (args.Length < 2) return Usage();
        var path = args[1];
        string? target = null;
        string? outDir = null;
        for (int i = 2; i < args.Length; i++)
        {
            if (args[i] == "--to" && i + 1 < args.Length) target = args[++i];
            else if (args[i] == "--out" && i + 1 < args.Length) outDir = args[++i];
        }
        if (target is null) return Usage();
        if (!File.Exists(path))
        {
            Console.Error.WriteLine($"文件不存在: {path}");
            return 1;
        }
        outDir ??= Path.GetDirectoryName(Path.GetFullPath(path))!;
        try
        {
            var outputs = Converter.ConvertFile(path, target, outDir, PrintLog);
            foreach (var o in outputs)
                Console.WriteLine(o);
            return 0;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"转换失败: {ex.Message}");
            return 1;
        }
    }

    default:
        return Usage();
}
