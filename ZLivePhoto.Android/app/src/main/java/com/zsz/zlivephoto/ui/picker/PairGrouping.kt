package com.zsz.zlivephoto.ui.picker

/**
 * 导入分组：把一次选择器确认的媒体自动分成三类（纯名称逻辑，无 Android 依赖，可单测）。
 *
 * 规则（用户需求）：
 * - 动态照片（[MediaKind.LIVE]）→ 直接按全局输出格式转换，不参与配对
 * - 其余图片与视频 → 按**名称配对**识别为「合并动态照片」（图片 + 视频 → 动态照片）
 * - 配不上的单图 / 单视频 → 无法合成，单独返回以便提示跳过
 *
 * 配对分两轮（避免误合并不同照片）：
 * 1. **主干精确同名**（忽略大小写与扩展名）：覆盖 vivo / OPPO / 小米 / Google / Apple
 *    等所有厂商导出的成对文件（`IMG_0001.jpg` + `IMG_0001.mp4`）
 * 2. **宽松主干兜底**：仅对第一轮剩余的图片与视频生效，再去掉尾部序号/编辑标记
 *    （`_1`、` (1)`、`_HDR`、`_MP`、`_EDIT` 等），用于「名称部分相同」的成对文件
 *
 * 注意：第二轮只在「剩余的图片」与「剩余的视频」之间配对。因此
 * `IMG_0001.jpg` 与 `IMG_0002.jpg` 这类**同为图片**的文件永远不会被合并，
 * 不会把不同照片误判成一组（同目录非动态照片但名称部分相同的问题即源于此）。
 */
object PairGrouping {

    /** 一次导入的分组结果 */
    data class Plan(
        /** 动态照片：按全局输出格式转换 */
        val converts: List<MediaItem>,
        /** 图片 + 配对视频：合并为动态照片 */
        val pairs: List<Pair<MediaItem, MediaItem>>,
        /** 未配对的单图 / 单视频：无法合成，导入时跳过并提示 */
        val unpaired: List<MediaItem>
    )

    /** 名称主干：去掉扩展名并小写（配对判定的第一轮键） */
    fun stem(name: String): String = name.substringBeforeLast('.', name).lowercase()

    /**
     * 尾部序号 / 编辑标记（第二轮宽松匹配时裁掉）：
     * `_1`、`-2`、` (3)`、`_HDR`、`_MP`、`_EDIT`、`_COVER`、`_BURST`、`_原图`、`_副本`、`_CROP`
     */
    private val LOOSE_SUFFIX =
        Regex("[ _\\-]?(\\(?\\d{1,2}\\)?|hdr|mp|edit|cover|burst|crop|rotated|原图|副本|已编辑)$")

    /** 宽松主干：在 [stem] 基础上反复裁掉尾部序号/编辑标记（用于部分同名的兜底配对） */
    fun looseStem(name: String): String {
        var s = stem(name)
        while (true) {
            val m = LOOSE_SUFFIX.find(s) ?: break
            if (m.range.first == 0) break // 整个主干都是标记：保留原值，避免退化成空串
            s = s.substring(0, m.range.first)
        }
        return s
    }

    /** 对一次选择结果做分组 */
    fun plan(items: List<MediaItem>): Plan {
        val converts = items.filter { it.kind == MediaKind.LIVE }
        val rest = items.filter { it.kind != MediaKind.LIVE }
        val images = rest.filter { !it.isVideo }
        val videos = rest.filter { it.isVideo }

        val pairs = ArrayList<Pair<MediaItem, MediaItem>>()
        val usedImage = BooleanArray(images.size)
        val usedVideo = BooleanArray(videos.size)

        // 第一轮：主干精确同名（按名称顺序取首个未占用的配对视频）
        val exact = HashMap<String, MutableList<Int>>()
        videos.forEachIndexed { i, v -> exact.getOrPut(stem(v.name)) { ArrayList() }.add(i) }
        images.forEachIndexed { i, img ->
            val bucket = exact[stem(img.name)] ?: return@forEachIndexed
            val j = bucket.firstOrNull { !usedVideo[it] } ?: return@forEachIndexed
            usedVideo[j] = true
            usedImage[i] = true
            pairs.add(img to videos[j])
        }

        // 第二轮：宽松主干兜底（仅针对第一轮剩余的图片与视频）
        val loose = HashMap<String, MutableList<Int>>()
        videos.forEachIndexed { i, v ->
            if (!usedVideo[i]) loose.getOrPut(looseStem(v.name)) { ArrayList() }.add(i)
        }
        images.forEachIndexed { i, img ->
            if (usedImage[i]) return@forEachIndexed
            val bucket = loose[looseStem(img.name)] ?: return@forEachIndexed
            val j = bucket.firstOrNull { !usedVideo[it] } ?: return@forEachIndexed
            usedVideo[j] = true
            usedImage[i] = true
            pairs.add(img to videos[j])
        }

        val unpaired = ArrayList<MediaItem>()
        images.forEachIndexed { i, im -> if (!usedImage[i]) unpaired.add(im) }
        videos.forEachIndexed { i, v -> if (!usedVideo[i]) unpaired.add(v) }
        return Plan(converts, pairs, unpaired)
    }
}
