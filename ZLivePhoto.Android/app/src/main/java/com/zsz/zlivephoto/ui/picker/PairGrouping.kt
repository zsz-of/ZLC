package com.zsz.zlivephoto.ui.picker

/**
 * 导入分组：把一次选择器确认的媒体自动分成三类（纯顺序逻辑，无 Android 依赖，可单测）。
 *
 * 规则（用户需求）：
 * - 动态照片（[MediaKind.LIVE]）→ 直接按输出格式转换，不参与配对
 * - 其余图片与视频 → 按**选择顺序**两两配对识别为「合并动态照片」（图片 + 视频 → 动态照片）
 * - 多出来的单图 / 单视频 → 无法合成，单独返回以便提示跳过
 *
 * 配对按**用户点选顺序**进行：第 1 张图片配第 1 个视频，第 2 张图片配第 2 个视频，依此类推，
 * 第 `min(图片数, 视频数)` 个之后剩余文件视为未配对。名称是否相同不参与判定，
 * 因此同目录下名称部分相同但并非同一组的图片/视频不会被误合并。
 */
object PairGrouping {

    /** 一次导入的分组结果 */
    data class Plan(
        /** 动态照片：按输出格式转换 */
        val converts: List<MediaItem>,
        /** 图片 + 配对视频：合并为动态照片 */
        val pairs: List<Pair<MediaItem, MediaItem>>,
        /** 未配对的单图 / 单视频：无法合成，导入时跳过并提示 */
        val unpaired: List<MediaItem>
    )

    /** 对一次选择结果做分组（[items] 为选择器中的点选顺序） */
    fun plan(items: List<MediaItem>): Plan {
        val converts = items.filter { it.kind == MediaKind.LIVE }
        val rest = items.filter { it.kind != MediaKind.LIVE }
        val images = rest.filter { !it.isVideo }
        val videos = rest.filter { it.isVideo }

        val pairCount = minOf(images.size, videos.size)
        val pairs = (0 until pairCount).map { images[it] to videos[it] }
        val unpaired = images.drop(pairCount) + videos.drop(pairCount)
        return Plan(converts, pairs, unpaired)
    }
}
