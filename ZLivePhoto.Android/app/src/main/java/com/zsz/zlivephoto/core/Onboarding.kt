package com.zsz.zlivephoto.core

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * 首次启动的使用协议状态。
 *
 * 未同意前整块界面被协议弹窗遮挡，无法操作；同意后写入应用私有 SharedPreferences，
 * 之后启动不再弹出。卸载或清除数据后会重新弹出。
 */
object Onboarding {
    private const val PREFS = "onboarding"
    private const val KEY_AGREED = "agreement_agreed_v1"

    fun isAgreed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AGREED, false)

    fun agree(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AGREED, true).apply()
    }
}

/** 从 ContextWrapper 链里找到承载的 Activity（用于「不同意并退出」）。 */
tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}
