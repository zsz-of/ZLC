namespace ZLivePhoto.Core.Formats;

/// <summary>
/// 格式插件注册表。
/// </summary>
public static class FormatRegistry
{
    /// <summary>已注册插件（检测按置信度取最高）</summary>
    public static readonly IReadOnlyList<FormatPlugin> Plugins =
    [
        new GooglePlugin(),
        new ApplePlugin(),
        new OppoPlugin(),
        new VivoPlugin(),
        new XiaomiPlugin()
    ];

    /// <summary>name -> plugin 索引</summary>
    public static readonly IReadOnlyDictionary<string, FormatPlugin> ByName =
        Plugins.ToDictionary(p => p.Name);

    /// <summary>返回置信度最高的插件及其置信度；全部不识别时返回 (null, 0)。</summary>
    public static (FormatPlugin? plugin, int score) DetectBest(string path)
    {
        FormatPlugin? best = null;
        int bestScore = 0;
        foreach (var plugin in Plugins)
        {
            int score;
            try
            {
                score = plugin.Detect(path);
            }
            catch
            {
                score = 0;
            }
            if (score > bestScore)
            {
                best = plugin;
                bestScore = score;
            }
        }
        return (best, bestScore);
    }
}
