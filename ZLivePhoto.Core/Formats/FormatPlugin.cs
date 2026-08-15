using ZLivePhoto.Core.Models;

namespace ZLivePhoto.Core.Formats;

/// <summary>
/// 格式插件抽象基类。
/// </summary>
public abstract class FormatPlugin
{
    /// <summary>格式唯一标识（小写）</summary>
    public abstract string Name { get; }

    /// <summary>展示名</summary>
    public abstract string Display { get; }

    /// <summary>返回 0-100 的置信度；0 表示确定不是本格式。</summary>
    public abstract int Detect(string path);

    /// <summary>解析为 LivePhotoAsset。失败应抛出带中文说明的异常。</summary>
    public abstract LivePhotoAsset Read(string path, Action<string, string, string> log);

    /// <summary>把 asset 写为本格式文件，返回写出的文件路径列表。</summary>
    public abstract List<string> Write(LivePhotoAsset asset, string outDir, string stem,
        Action<string, string, string> log, Dictionary<string, object> options);

    protected static byte[] ReadBytes(string path) => File.ReadAllBytes(path);
    protected static void WriteBytes(string path, byte[] data) => File.WriteAllBytes(path, data);
}
