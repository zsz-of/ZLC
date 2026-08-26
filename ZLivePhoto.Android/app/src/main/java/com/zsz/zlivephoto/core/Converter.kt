package com.zsz.zlivephoto.core

import com.zsz.zlivephoto.core.formats.FormatRegistry
import java.io.File

/**
 * 转换管线：detect → read → write。同格式转换 = 原样复制（零损耗直通）。
 */
internal class ConvertException(message: String) : Exception(message)

internal object Converter {
    /**
     * 转换单个文件为指定格式，返回输出文件路径列表。
     *
     * @param path 源文件路径
     * @param target 目标格式：google | apple | oppo | vivo | xiaomi
     * @param outDir 输出目录
     * @param log 日志回调 (level, message, tag)
     * @param options 选项：google_mp_suffix (bool)
     */
    fun convertFile(
        path: String, target: String, outDir: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?> = mutableMapOf()
    ): MutableList<String> {
        val targetPlugin = FormatRegistry.byName[target]
            ?: throw ConvertException("未知目标格式：$target")

        val (plugin, score) = FormatRegistry.detectBest(path)
        if (plugin == null || score < 50) {
            throw ConvertException("无法识别的动态照片格式（非 Google/OPPO/vivo/小米/Apple 动态照片）")
        }

        log("info", "识别为 ${plugin.display}", "转换")

        val stem = File(path).nameWithoutExtension
        File(outDir).mkdirs()

        // 同格式直通：原样复制，零损耗
        // 例外 vivo_single：源可能是 vivo 相册「关闭实况」的合并产物（MotionPhoto="0"），
        // 需走完整写出流程修复回 "1" 恢复动态效果
        if (plugin.name == target && plugin.name != "vivo_single") {
            val directOuts = mutableListOf<String>()
            val dst = File(outDir, File(path).name).path
            File(path).copyTo(File(dst), overwrite = true)
            directOuts.add(dst)

            val parent = File(path).parentFile
            when (plugin.name) {
                "vivo" -> {
                    val mp4 = if (parent != null) File(parent, "$stem.mp4").path else "$stem.mp4"
                    if (File(mp4).exists()) {
                        val dstMp4 = File(outDir, File(mp4).name).path
                        File(mp4).copyTo(File(dstMp4), overwrite = true)
                        directOuts.add(dstMp4)
                    }
                }
                "apple" -> {
                    val mov = if (parent != null) File(parent, "$stem.mov").path else "$stem.mov"
                    if (File(mov).exists()) {
                        val dstMov = File(outDir, File(mov).name).path
                        File(mov).copyTo(File(dstMov), overwrite = true)
                        directOuts.add(dstMov)
                    }
                }
            }
            log("info", "源与目标格式相同，已原样复制（零损耗）", "转换")
            return directOuts
        }

        val asset = plugin.read(path, log)
        if (asset.presentationTsUs < 0) {
            log("warning", "源缺少封面帧时间戳，按规范回退为视频中点", "转换")
        }

        val outputs = targetPlugin.write(asset, outDir, stem, log, options)

        // 保留源文件的时间戳（修改时间）
        for (outPath in outputs) {
            try {
                copyTimestamps(path, outPath)
            } catch (e: Exception) {
                /* 时间戳复制失败不阻塞转换 */
            }
        }

        return outputs
    }

    /** 复制源文件的修改时间到目标文件（访问时间/创建时间在 Android/Linux 上无原生 API，省略）。 */
    private fun copyTimestamps(src: String, dst: String) {
        val srcFile = File(src)
        val dstFile = File(dst)
        dstFile.setLastModified(srcFile.lastModified())
    }
}
