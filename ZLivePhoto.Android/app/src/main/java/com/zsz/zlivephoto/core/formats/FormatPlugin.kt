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

