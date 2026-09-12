package com.zsz.zlivephoto.core

import com.zsz.zlivephoto.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 检查结果：有新版本 */
data class UpdateInfo(
    /** 新版本号（已去 v 前缀，如 "3.1.0"） */
    val version: String,
    /** GitHub release 资产直链（browser_download_url；无匹配 APK 资产时为 release 页） */
    val downloadUrl: String,
    /** GitHub release 页面地址 */
    val releaseUrl: String,
    /** 更新说明（release body，完整原文，可空） */
    val notes: String?,
    /** 蓝奏云直链（与当前版本 flavor 匹配：标准版 / Go版），Release 正文里没写时为 null */
    val lanzouUrl: String?,
)

/** 更新检查结果：区分「有新版本 / 已是最新 / 网络错误」 */
sealed class UpdateCheckResult {
    data class Update(val info: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    object NetworkError : UpdateCheckResult()
}

/**
 * GitHub 更新检查：读取 releases/latest 的 tag_name 与本地版本号语义化比对。
 * 同时从 Release 正文按「规范化标签」解析蓝奏云直链：
 *
 *     [蓝奏云-标准版]: https://www.lanzoux.com/xxxx
 *     [蓝奏云-Go版]:   https://www.lanzoux.com/yyyy
 *
 * 规则：
 * - 标签必须写成 `[蓝奏云-标准版]` 或 `[蓝奏云-Go版]`（英文中括号 + 冒号），URL 写在后面。
 * - 标准版（normal flavor）只认「标准版」标签；Go 版（go flavor）只认「Go版」标签。
 * - 没写本版本对应标签行时 lanzouUrl=null，App 将自动回退从 GitHub 下载。
 *
 * 无第三方依赖：HttpURLConnection + org.json（Android 内置）。
 */
object UpdateChecker {
    private const val REPO = "zsz-of/ZLC"
    private const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    /** 蓝奏云标签行：`[蓝奏云-标准版]: url`（支持全角冒号与行内空格） */
    private val LANZOU_LINE = Regex("""\[蓝奏云-(标准版|Go版)\]\s*[：:]\s*(\S+)""")

    /** 转码器附加项标签（键值对形式，推荐）；旧版位置式仍兼容 */
    private const val ADDON_TAG = "[转码器附加项]"

    /**
     * 转码器附加项键值对：`github=<url> sha1=<sha1> version=<ver> lanzou=<url> appver=<ver[,ver]>`
     * 键名不区分大小写；除 github/url 外均可省略。
     */
    private val ADDON_KV = Regex("""([A-Za-z0-9]+)\s*=\s*(\S+)""")

    /** 旧版位置式附加项行：`[转码器附加项]: <url> <sha1> <version>` */
    private val ADDON_LINE = Regex("""\[转码器附加项\]\s*[：:]\s*(\S+)\s+(\S+)\s+(\S+)""")

    /** 蓝奏云标签名（发布规范：go=Go版，其余=标准版） */
    private fun lanzouLabel(isGo: Boolean): String =
        if (isGo) "Go版" else "标准版"

    /**
     * 检查更新（IO 协程执行）。
     * @param currentVersion 本地版本号（BuildConfig.VERSION_NAME）
     * @return Update 有新版本 / UpToDate 已是最新 / NetworkError 网络或解析失败
     */
    suspend fun check(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val info = fetchLatest()
        when {
            info == null -> UpdateCheckResult.NetworkError
            isNewer(info.version, currentVersion) -> UpdateCheckResult.Update(info)
            else -> UpdateCheckResult.UpToDate
        }
    }

    /**
     * 拉取 GitHub 最新 release 的完整信息（**不做版本号比对**，与本地版本是否相同都返回）。
     * 供「检查更新」做版本比对；也供「重新安装本版本」直接把当前最新版当作更新目标展示，
     * 从而无需降级/重装旧版即可随时回归验证「下载→安装」链路。
     * @return null 表示网络或解析失败
     */
    suspend fun fetchLatest(): UpdateInfo? = fetchLatestInternal(BuildConfig.FLAVOR == "go")

    /**
     * 强制按「标准版」（normal）匹配拉取最新 release。
     * 供 Go 版在 Android 10+ 上「切换到正常版本」时用：即使当前是 go flavor，
     * 也按 normal 的 APK 资产与「标准版」蓝奏云标签解析，得到正常版的下载信息。
     */
    suspend fun fetchLatestForNormal(): UpdateInfo? = fetchLatestInternal(false)

    private suspend fun fetchLatestInternal(isGo: Boolean): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API_LATEST).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            // 注明 UA，避免被 GitHub 按默认 UA 限流
            conn.setRequestProperty("User-Agent", "ZLC-Android-Updater")
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            val remote = tag.removePrefix("v").trim()
            if (remote.isEmpty()) return@withContext null

            // 优先取 APK 资产的直接下载地址（跳浏览器即可下载）。
            // normal / go 双版本：按参数 isGo 匹配对应 APK——
            //   go 版（给老安卓用，minSdk 23）只匹配资产名含 "go"（Go 版本后缀）的 APK；
            //   normal 版匹配资产名不含 "go" 的 APK。
            // 匹配不到本版本的 APK 时不再回退到任意 APK：go 版若误下 normal 版会因
            // minSdk 29 装不上，统一回退到 release 页面让用户手动选择对应安装包。
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

            // 从 Release 正文解析当前版本对应的蓝奏云直链（找不到返回 null，回退 GitHub）
            val releaseBody = json.optString("body")

            UpdateInfo(
                version = remote,
                // 直链下载：命中本版本 APK 资产则直链；否则指向 release 页面，
                // 保证 Go 版跳转的是 Go 版本 APK 所在页面而非错误版本
                downloadUrl = apkUrl ?: releaseUrl,
                releaseUrl = releaseUrl,
                notes = releaseBody.trim().ifEmpty { null },
                lanzouUrl = parseLanzouUrl(releaseBody, isGo)
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 按 tag 拉取指定版本的 Release 正文（供 ffmpeg 附加项按当前软件版本精确匹配）。
     * @param tag 形如 "v3.4.0"；返回 null 表示不存在该版本或网络失败
     */
    suspend fun fetchReleaseBody(tag: String): String? = withContext(Dispatchers.IO) {
        try {
            val conn = URL("https://api.github.com/repos/$REPO/releases/tags/${tag}").openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "ZLC-Android-Updater")
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optString("body").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 从 Release 正文拆分出与 [isGo] 匹配的蓝奏云直链。
     * 格式约定（每行独立）：
     * ```
     * [蓝奏云-标准版]: https://xxx.lanzouX.com/xxxx
     * [蓝奏云-Go版]:   https://xxx.lanzouX.com/yyyy
     * ```
     */
    fun parseLanzouUrl(body: String, isGo: Boolean): String? {
        if (body.isBlank()) return null
        val want = lanzouLabel(isGo)
        for (line in body.lineSequence()) {
            val m = LANZOU_LINE.find(line.trim()) ?: continue
            if (m.groupValues[1] == want) return m.groupValues[2].trimEnd(')', '，', ',', '。')
        }
        return null
    }

    /**
     * 从 Release 正文解析 ffmpeg 转码器附加项元数据（**按软件版本匹配**）。
     *
     * 推荐格式（键值对，单行内可同时给出主/备下载通道）：
     * ```
     * [转码器附加项] github=<GitHub 资产直链> sha1=<sha1> version=<编码器版本> lanzou=<蓝奏云分享页> appver=<适用软件版本[,版本…]>
     * ```
     * 规则：
     * - `appver` 省略或写 `*` → 视为通用，任意软件版本可用；
     * - `appver` 写了具体版本（可逗号分隔多个）→ 仅当与 [currentVersion] 完全一致时采用；
     * - 正文存在多条附加项行时，优先返回与当前软件版本精确匹配的那条；
     *   若都不匹配，则回退到未限定版本的通用行；仍无则返回 null。
     * - 兼容旧版位置式写法：`[转码器附加项]: <url> <sha1> <version>`。
     *
     * @param currentVersion 当前软件版本号（默认取 BuildConfig.VERSION_NAME，可带 v 前缀）
     */
    fun parseAddonMeta(
        body: String,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): FfmpegAddon.AddonMeta? {
        if (body.isBlank()) return null
        val cur = currentVersion.removePrefix("v").trim()
        var universal: FfmpegAddon.AddonMeta? = null
        for (raw in body.lineSequence()) {
            val line = raw.trim()
            if (!line.contains(ADDON_TAG)) continue

            // 键值对形式（优先）
            if (line.contains('=')) {
                val kv = ADDON_KV.findAll(line).associate {
                    it.groupValues[1].lowercase() to it.groupValues[2]
                }
                val github = kv["github"] ?: kv["url"] ?: continue
                val sha1 = kv["sha1"] ?: continue
                val meta = FfmpegAddon.AddonMeta(
                    url = github,
                    sha1 = sha1,
                    version = kv["version"] ?: "",
                    mirrorUrl = kv["lanzou"] ?: kv["mirror"]
                )
                val appver = kv["appver"]
                if (appver.isNullOrBlank() || appver == "*") {
                    if (universal == null) universal = meta
                    continue
                }
                if (appverMatches(appver, cur)) return meta
                continue
            }

            // 旧版位置式：无版本限定，作为通用回退项
            val m = ADDON_LINE.find(line) ?: continue
            if (universal == null) {
                universal = FfmpegAddon.AddonMeta(
                    url = m.groupValues[1],
                    sha1 = m.groupValues[2],
                    version = m.groupValues[3]
                )
            }
        }
        return universal
    }

    /** `appver` 是否适用当前软件版本（支持逗号/斜杠分隔多版本，自动忽略 v 前缀） */
    private fun appverMatches(spec: String, current: String): Boolean =
        spec.split(',', '，', '/', '|')
            .map { it.trim().removePrefix("v") }
            .any { it == current }

    /**
     * 语义化版本比较：按 . 和 - 分段逐段数值比较（非数字段按 0 处理）。
     * 示例：("2.5.0", "2.4.0") = true；("2.4.0", "2.4.0") = false
     */
    fun isNewer(remote: String, current: String): Boolean {
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
