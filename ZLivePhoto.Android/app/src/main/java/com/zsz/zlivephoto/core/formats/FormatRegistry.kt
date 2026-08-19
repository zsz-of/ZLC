package com.zsz.zlivephoto.core.formats

/**
 * 格式插件注册表。
 */
internal object FormatRegistry {
    /** 已注册插件（检测按置信度取最高） */
    val plugins: List<FormatPlugin> = listOf(
        GooglePlugin(),
        ApplePlugin(),
        OppoPlugin(),
        VivoPlugin(),
        XiaomiPlugin(),
        HonorPlugin()
    )

    /** name -> plugin 索引 */
    val byName: Map<String, FormatPlugin> = plugins.associateBy { it.name }

    /** 返回置信度最高的插件及其置信度；全部不识别时返回 (null, 0)。 */
    fun detectBest(path: String): Pair<FormatPlugin?, Int> {
        var best: FormatPlugin? = null
        var bestScore = 0
        for (plugin in plugins) {
            val score = try {
                plugin.detect(path)
            } catch (e: Exception) {
                0
            }
            if (score > bestScore) {
                best = plugin
                bestScore = score
            }
        }
        return best to bestScore
    }
}
