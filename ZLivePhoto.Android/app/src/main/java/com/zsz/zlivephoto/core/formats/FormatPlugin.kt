package com.zsz.zlivephoto.core.formats

import androidx.annotation.StringRes
import com.zsz.zlivephoto.R
import com.zsz.zlivephoto.core.LivePhotoAsset
import java.io.File

/**
 * 格式插件抽象基类。
 */
internal abstract class FormatPlugin {
    /** 格式唯一标识（小写） */
    abstract val name: String

    /**
     * 展示名资源 id：展示名属用户可见文案，统一放 res/values/strings_formats.xml，
     * UI 侧用 stringResource(displayRes) 取文案（119 语种翻译入口）。
     */
    @get:StringRes
    abstract val displayRes: Int

    /**
     * 兼容旧调用点的展示名（MainActivity.kt:1551 / ui/picker/AlbumScanner.kt:144 /
     * core/Converter.kt:45,339 仍按 String 使用，本次 i18n 未覆盖这三个文件）。
     * 中文兜底表与 res/values/strings_formats.xml 一一对应。
     * TODO(i18n)：上述调用点改成 stringResource(displayRes) 后，删除本属性与 [LEGACY_ZH_NAMES]。
     */
    val display: String get() = LEGACY_ZH_NAMES[displayRes] ?: name

    /** 返回 0-100 的置信度；0 表示确定不是本格式。 */
    abstract fun detect(path: String): Int

    /**
     * 解析为 LivePhotoAsset。失败应抛出带可读说明的异常
     * （文案走 CoreText.of(R.string.fmt_err_*)，无 Context 时为空串）。
     */
    abstract fun read(path: String, log: (String, String, String) -> Unit): LivePhotoAsset

    /** 把 asset 写为本格式文件，返回写出的文件路径列表。 */
    abstract fun write(
        asset: LivePhotoAsset, outDir: String, stem: String,
        log: (String, String, String) -> Unit, options: MutableMap<String, Any?>
    ): MutableList<String>

    protected fun readBytes(path: String): ByteArray = File(path).readBytes()
    protected fun writeBytes(path: String, data: ByteArray) {
        File(path).parentFile?.mkdirs()
        File(path).writeBytes(data)
    }
}

/** 旧调用点（无 Context 场景）用的中文兜底表；正式文案在 res/values/strings_formats.xml */
private val LEGACY_ZH_NAMES: Map<Int, String> = mapOf(
    R.string.fmt_apple to "Apple Live Photo",
    R.string.fmt_extract to "拆解",
    R.string.fmt_google to "Google Motion Photo",
    R.string.fmt_honor to "荣耀动态照片",
    R.string.fmt_meizu to "魅族动态照片",
    R.string.fmt_nubia to "努比亚动态照片",
    R.string.fmt_oppo to "OPPO 动态照片",
    R.string.fmt_vivo to "vivo 动态照片",
    R.string.fmt_vivo_single to "vivo 单文件实况",
    R.string.fmt_xiaomi to "小米动态照片",
)
