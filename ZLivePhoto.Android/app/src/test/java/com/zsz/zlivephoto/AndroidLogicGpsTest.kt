package com.zsz.zlivephoto

import com.zsz.zlivephoto.core.Converter
import org.junit.Test
import java.io.File

/**
 * Android 版转换逻辑的 JVM 回归测试：
 * 用真实 Kotlin 核心代码转换带 GPS 的动态照片，验证输出文件 GPS 不被破坏。
 * 输出路径打印到日志，由外部脚本（verify_gps.py / dump_exif_full.py）做字节级校验。
 */
class AndroidLogicGpsTest {

    private fun log(level: String, msg: String, tag: String) {
        println("[$level][$tag] $msg")
    }

    @Test
    fun convertXiaomiPreservesGps() {
        val src = "C:/Users/zhong/Downloads/IMG_20260827_194214.jpg"
        val outDir = "D:/Code/Program/Z-LivePhoto-Converter/_samples/android_gps_test"
        File(outDir).deleteRecursively()
        val outs = Converter.convertFile(src, "xiaomi", outDir, ::log)
        println("ANDROID_XIAOMI_OUTPUTS: " + outs.joinToString(","))
        check(outs.size == 1) { "xiaomi 应输出 1 个文件" }
        check(File(outs[0]).exists()) { "输出文件不存在" }
    }

    @Test
    fun convertGooglePreservesGps() {
        val src = "C:/Users/zhong/Downloads/IMG_20260827_194214.jpg"
        val outDir = "D:/Code/Program/Z-LivePhoto-Converter/_samples/android_gps_test_google"
        File(outDir).deleteRecursively()
        val outs = Converter.convertFile(src, "google", outDir, ::log)
        println("ANDROID_GOOGLE_OUTPUTS: " + outs.joinToString(","))
        check(outs.size == 1) { "google 应输出 1 个文件" }
        check(File(outs[0]).exists()) { "输出文件不存在" }
    }

    @Test
    fun detectXiaomiSentViaChat() {
        // 模拟「第三方传输剥掉 EXIF」的小米照片：剥离 EXIF 后仅剩 XIAOMI_CUSTOMIZE 段
        val stripped = "D:/Code/Program/Z-LivePhoto-Converter/_samples/小米动图_no_exif.jpg"
        val (plugin, score) = com.zsz.zlivephoto.core.formats.FormatRegistry.detectBest(stripped)
        println("ANDROID_DETECT_STRIPPED: " + (plugin?.name ?: "unknown") + " ($score)")
        check(plugin?.name == "xiaomi") { "剥掉 EXIF 的小米照片应识别为 xiaomi，实际 ${plugin?.name} ($score)" }
    }
}
