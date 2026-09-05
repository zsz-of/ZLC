package com.zsz.zlivephoto.core

import com.zsz.zlivephoto.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 检查结果：有新版本 */
data class UpdateInfo(
    /** 新版本号（已去 v 前缀，如 "2.5.0"） */
    val version: String,
    /** APK 直接下载地址（GitHub release asset 的 browser_download_url；无 APK 资产时为 release 页） */
    val downloadUrl: String,
    /** GitHub release 页面地址 */
    val releaseUrl: String,
    /** 更新说明（release body，可空） */
    val notes: String?
)

/** 更新检查结果：区分「有新版本 / 已是最新 / 网络错误」 */
sealed class UpdateCheckResult {
    data class Update(val info: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    object NetworkError : UpdateCheckResult()
}

/**
 * GitHub 更新检查：读取 releases/latest 的 tag_name 与本地版本号语义化比对。
 * 无第三方依赖：HttpURLConnection + org.json（Android 内置）。
 */
object UpdateChecker {
    private const val REPO = "zsz-of/ZLC"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    /**
     * 检查更新（IO 协程执行）。
     * @param currentVersion 本地版本号（BuildConfig.VERSION_NAME）
     * @return Update 有新版本 / UpToDate 已是最新 / NetworkError 网络或解析失败
     */
    suspend fun check(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API_LATEST).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) return@withContext UpdateCheckResult.NetworkError
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            val remote = tag.removePrefix("v").trim()
            if (remote.isEmpty() || !isNewer(remote, currentVersion)) {
                return@withContext UpdateCheckResult.UpToDate
            }

            // 优先取 APK 资产的直接下载地址（跳浏览器即可下载）。
            // normal / go 双版本：按当前 flavor 匹配对应 APK——
            //   go 版（给老安卓用，minSdk 23）只匹配资产名含 "go"（Go 版本后缀）的 APK；
            //   normal 版匹配资产名不含 "go" 的 APK。
            // 匹配不到本版本的 APK 时不再回退到任意 APK：go 版若误下 normal 版会因
            // minSdk 29 装不上，统一回退到 release 页面让用户手动选择对应安装包。
            val isGo = BuildConfig.FLAVOR == "go"
            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                val apkAssets = mutableListOf<Pair<String, String>>() // (name, url)
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val name = a.optString("name", "")
                    val url = a.optString("browser_download_url")
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotEmpty()) {
                        apkAssets.add(name to url)
                    }
                }
                fun matches(name: String): Boolean {
                    val lower = name.lowercase()
                    return if (isGo) lower.contains("go") else !lower.contains("go")
                }
                apkUrl = apkAssets.firstOrNull { matches(it.first) }?.second
            }
            val releaseUrl = json.optString("html_url", "https://github.com/$REPO/releases")
            UpdateCheckResult.Update(
                UpdateInfo(
                    version = remote,
                    // 直链下载：命中本版本 APK 资产则直链；否则指向 release 页面，
                    // 保证 Go 版跳转的是 Go 版本 APK 所在页面而非错误版本
                    downloadUrl = apkUrl ?: releaseUrl,
                    releaseUrl = releaseUrl,
                    notes = json.optString("body").ifEmpty { null }
                )
            )
        } catch (_: Exception) {
            UpdateCheckResult.NetworkError
        }
    }

    /**
     * 语义化版本比较：按 . 和 - 分段逐段数值比较（非数字段按 0 处理）。
     * 示例：("2.5.0", "2.4.0") = true；("2.4.0", "2.4.0") = false
     */
    private fun isNewer(remote: String, current: String): Boolean {
        fun parse(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val r = parse(remote)
        val c = parse(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrNull(i) ?: 0
            val b = c.getOrNull(i) ?: 0
            if (a != b) return a > b
        }
        return false
    }
}
