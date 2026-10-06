package com.zsz.zlivephoto

import android.app.Application
import android.content.Context
import com.zsz.zlivephoto.core.AppLanguage

/**
 * 应用入口：只为「应用语言」服务 —— 在 attachBaseContext 里按用户选择的语言包装 Context，
 * 让 Android 13 以下也能即时生效（13+ 由系统 LocaleManager 负责）。
 * 其余初始化仍在 MainActivity / AppSettings.init 中完成，这里不做其它事情。
 */
class ZlcApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }
}
