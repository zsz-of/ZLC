package com.zsz.zlivephoto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 蓝奏云直链解析的纯逻辑回归。
 *
 * 背景（2026-10 实测）：
 * - 分享链接的「接口主机」已从「分享页域名 + /ajaxfile.php」迁移到帧页里声明的
 *   `apifile.woozooo.com` / `apifile.lanzouw.com`；旧主机 POST 会返回 **HTTP 407 空响应**。
 * - 分享页域名会轮换，同一 `<fileID>` 在所有官方别名域名下都能打开同一文件页，
 *   因此换域名重试是安全回退。
 * - 分享被取消时分享页仍返回 **HTTP 200**，只能靠页面文案识别。
 * - 非会员账号新上传的文件会被蓝奏**强制加提取码**（`task=23` 关码接口返回「此功能仅会员使用」），
 *   带码分享页内联 `down_p()` 且**两次声明 `var isngis`**（第一次为空），提取码由服务端强校验。
 *
 * 这里只覆盖纯函数（不联网）；真实链路已由 `.agents/tools/lanzou_probe.py` 实测。
 */
class LanzouResolveTest {

    private val shareUrl = "https://wwapb.lanzout.com/irBhz489iv5g"

    /** 帧页片段：与实测抓到的 `/fn` 页结构一致 */
    private val frameHtml = """
        <script>
        var domain1='https://apifile.woozooo.com/ajaxfile.php?file=317939766';
        var domain2='https://apifile.lanzouw.com/ajaxfile.php?file=317939766';
        var ajaxdata='r1AH';
        var wp_sign='0123456789abcdef';
        var kdns=1;
        </script>
    """.trimIndent()

    @Test
    fun lanzouShareHosts_keepsOriginalFirstAndAppendsAliasDomains() {
        val hosts = AppUpdater.lanzouShareHosts(shareUrl)

        assertEquals("原地址必须排第一（优先用用户拿到的链接）", shareUrl, hosts.first())
        assertEquals("末位为 www.lanzoui.com 兜底", "https://www.lanzoui.com/irBhz489iv5g", hosts.last())
        // 6 个别名域名中 lanzout 与原地址重复，去重后剩 5 个 + www 兜底
        assertEquals(7, hosts.size)
        assertEquals("列表内不得有重复项", hosts.size, hosts.distinct().size)
        listOf("lanzouw", "lanzoui", "lanzoux", "lanzouv", "lanzoul").forEach { alias ->
            assertTrue(
                "缺少别名域名 $alias",
                hosts.contains("https://wwapb.$alias.com/irBhz489iv5g")
            )
        }
    }

    @Test
    fun lanzouShareHosts_keepsWwwHostWithoutInventingSubdomain() {
        val hosts = AppUpdater.lanzouShareHosts("https://www.lanzout.com/iABC123")

        assertEquals(2, hosts.size)
        assertEquals("https://www.lanzout.com/iABC123", hosts[0])
        assertEquals("https://www.lanzoui.com/iABC123", hosts[1])
    }

    @Test
    fun lanzouShareHosts_returnsInputWhenNotAUrl() {
        assertEquals(listOf("lanzou.com/xyz"), AppUpdater.lanzouShareHosts("lanzou.com/xyz"))
        assertEquals(listOf(""), AppUpdater.lanzouShareHosts(""))
    }

    @Test
    fun lanzouGoneReason_detectsCancelledSharePage() {
        val gone = "<html><body>来晚啦，该文件取消分享</body></html>"
        val reason = AppUpdater.lanzouGoneReason(gone)
        assertTrue("应识别为分享已取消", reason != null && reason.contains("GitHub"))
        assertNull("正常分享页不应误判", AppUpdater.lanzouGoneReason("<iframe src=\"/fn?x\"></iframe>"))
    }

    @Test
    fun lanzouAjaxEndpoints_prefersDeclaredApifileHostsThenLegacy() {
        val endpoints = AppUpdater.lanzouAjaxEndpoints(frameHtml, "317939766", shareUrl)

        assertEquals(3, endpoints.size)
        assertEquals("https://apifile.woozooo.com/ajaxfile.php?file=317939766", endpoints[0])
        assertEquals("https://apifile.lanzouw.com/ajaxfile.php?file=317939766", endpoints[1])
        assertEquals(
            "旧链路（分享页域名）只能作为最后兜底",
            "https://wwapb.lanzout.com/ajaxfile.php?file=317939766",
            endpoints[2]
        )
    }

    @Test
    fun lanzouAjaxEndpoints_fallsBackToLegacyWhenFrameDeclaresNothing() {
        val endpoints = AppUpdater.lanzouAjaxEndpoints("<html>no js here</html>", "42", shareUrl)

        assertEquals(listOf("https://wwapb.lanzout.com/ajaxfile.php?file=42"), endpoints)
    }

    @Test
    fun lanzouKd_readsKdnsOrDefaultsToOne() {
        assertEquals("1", AppUpdater.lanzouKd(frameHtml))
        assertEquals("133", AppUpdater.lanzouKd("var kdns=133;"))
        assertEquals("1", AppUpdater.lanzouKd("<html>no kdns</html>"))
    }

    // ---------- 带提取码的分享页（2026-10 实测） ----------

    /**
     * 带提取码的分享页会**连续声明两次** `var isngis`：第一次为空串，第二次才是真签名。
     * 只取第一次（旧实现的正则 find）会拿到空串，服务端一律回「文件无法识别」——
     * 这就是「带提取码的分享怎么都解析不了」的根因，必须在纯逻辑层钉死。
     */
    @Test
    fun lanzouSign_takesLastNonEmptyDeclaration() {
        val realPage = """
            var kdns =1;
            var isngis = '';
            var isngis = 'AGZaZAs6BTQHDgE_bBDRUaFc_c';
        """.trimIndent()

        assertEquals("AGZaZAs6BTQHDgE_bBDRUaFc_c", AppUpdater.lanzouSign(realPage))
        assertEquals("ONLY", AppUpdater.lanzouSign("var isngis = 'ONLY';"))
        assertNull("全为空串时视为没有签名", AppUpdater.lanzouSign("var isngis = '';"))
        assertNull("页面没有该变量", AppUpdater.lanzouSign("<html>no sign</html>"))
    }

    /** 需提取码的页面与普通分享页互斥：「有密码输入区 + 没有 /fn 下载帧」才算需要提取码 */
    @Test
    fun isLanzouPasswordShare_distinguishesPasswordPageFromFramePage() {
        val passwordPage = """
            <div id="passwddiv"><input id="pwd" class="passwdinput"></div>
            <script>url : 'https://apifile.woozooo.com/ajaxfile.php?file=322573829',</script>
        """.trimIndent()
        assertTrue("密码页应判定为需要提取码", AppUpdater.isLanzouPasswordShare(passwordPage))

        val framePage = "<html><body><iframe src=\"/fn?kdsdn=1\"></iframe></body></html>"
        assertFalse("普通分享页有 /fn 帧，不能误判", AppUpdater.isLanzouPasswordShare(framePage))
        assertFalse(
            "同时含密码区标记与 /fn 帧时以帧页为准",
            AppUpdater.isLanzouPasswordShare("$passwordPage$framePage")
        )
        assertFalse("无关页面", AppUpdater.isLanzouPasswordShare("<html>nothing</html>"))
    }

    @Test
    fun lanzouFileIdAndShareApi_readEndpointFromPasswordPage() {
        val page = """
            var isngis = 'XYZ';
            url : 'https://apifile.woozooo.com/ajaxfile.php?file=322573829',
        """.trimIndent()

        assertEquals("322573829", AppUpdater.lanzouFileId(page))
        assertEquals(
            "https://apifile.woozooo.com/ajaxfile.php?file=322573829",
            AppUpdater.lanzouShareApi(page, "322573829")
        )
        assertEquals(
            "页面没写绝对地址时回退官方接口",
            "https://apifile.woozooo.com/ajaxfile.php?file=42",
            AppUpdater.lanzouShareApi("<html>no endpoint</html>", "42")
        )
        assertNull(AppUpdater.lanzouFileId("<html>no id</html>"))
    }

    /** Release 正文里的提取码（可选）解析，且必须只认本 flavor 的标签行 */
    @Test
    fun parseLanzouLine_readsOptionalPasswdPerFlavor() {
        val body = """
            ## 📦 下载安装
            [蓝奏云-标准版]: https://wwapb.lanzout.com/irENZ4b0uj4j 提取码：3e4i
            [蓝奏云-Go版]:   https://wwapb.lanzout.com/i2s7d4b0uj6b 提取码: 41c4
        """.trimIndent()

        assertEquals(
            "https://wwapb.lanzout.com/irENZ4b0uj4j" to "3e4i",
            UpdateChecker.parseLanzouLine(body, isGo = false)
        )
        assertEquals(
            "https://wwapb.lanzout.com/i2s7d4b0uj6b" to "41c4",
            UpdateChecker.parseLanzouLine(body, isGo = true)
        )
        // 老版式（不带提取码）必须继续可用
        assertEquals(
            "https://wapb.lanzout.com/old" to null,
            UpdateChecker.parseLanzouLine("[蓝奏云-标准版]: https://wapb.lanzout.com/old", isGo = false)
        )
        assertEquals(null to null, UpdateChecker.parseLanzouLine("[蓝奏云-Go版]: https://x/y", isGo = false))
        assertEquals(null to null, UpdateChecker.parseLanzouLine("", isGo = false))
    }
}
