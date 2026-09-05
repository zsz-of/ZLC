package com.zsz.zlivephoto.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拆解输出照片的“净化”验证：sanitizeStillPhoto 后主 JPEG 头部不得残留任何
 * MotionPhoto / MicroVideo 标记字节（否则会被 QuickClassify / detect 误判成动态照片，
 * 无法作为普通封面照片用于合成），同时 Ultra HDR（hdrgm / GainMap）元数据需保留。
 */
class JpegSanitizeTest {

    /** 组装最小 JPEG：SOI + XMP APP1 段 + 空 SOS（标记解析无需真实图像数据）。 */
    private fun jpegWithXmps(vararg xmps: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(0xFF); out.write(0xD8) // SOI
        xmps.forEach { out.write(JpegUtil.buildXmpApp1(it)) }
        out.write(0xFF); out.write(0xDA); out.write(0x00); out.write(0x02) // SOS 长度=2
        return out.toByteArray()
    }

    private fun containsToken(data: ByteArray, token: String): Boolean {
        val t = token.toByteArray(Charsets.US_ASCII)
        outer@ for (i in 0..data.size - t.size) {
            for (j in t.indices) {
                if (data[i + j] != t[j]) continue@outer
            }
            return true
        }
        return false
    }

    private fun ascii(data: ByteArray): String = String(data, Charsets.ISO_8859_1)

    /** Google 单文件：MotionPhoto 属性 + Container 视频项（视频项里有 MotionPhoto 语义）。 */
    private val googleXmp = """<?xpacket begin=""?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
      xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
      xmlns:Container="http://ns.google.com/photos/1.0/container/"
      xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
      GCamera:MotionPhoto="1" GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="1000000">
   <Container:Directory>
    <rdf:Seq>
     <rdf:li rdf:parseType="Resource"><Container:Item Item:Semantic="Primary" Item:Length="0"/></rdf:li>
     <rdf:li rdf:parseType="Resource"><Container:Item Item:Semantic="MotionPhoto" Item:Length="12345"/></rdf:li>
    </rdf:Seq>
   </Container:Directory>
   <hdrgm:Version>1.0</hdrgm:Version>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>"""

    /** 小米双标签：MicroVideo 属性；外加未知厂商前缀的元素写法。 */
    private val xiaomiXmp = """<?xpacket begin=""?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
      xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
      xmlns:ABC="http://example.com/abc/"
      GCamera:MicroVideo="1" GCamera:MicroVideoVersion="1"
      GCamera:MicroVideoOffset="999999">
   <ABC:MotionPhotoVersion>1</ABC:MotionPhotoVersion>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>"""

    private fun assertClean(jpeg: ByteArray) {
        assertFalse("不应残留 MotionPhoto 字节", containsToken(jpeg, "MotionPhoto"))
        assertFalse("不应残留 MicroVideo 字节", containsToken(jpeg, "MicroVideo"))
    }

    @Test
    fun googleXmp_removesAllMotionMarkers_keepsHdr() {
        val out = JpegUtil.sanitizeStillPhoto(jpegWithXmps(googleXmp))
        assertClean(out)
        val text = ascii(out)
        assertTrue("应保留 Ultra HDR 的 hdrgm 版本信息", text.contains("hdrgm:Version"))
        assertTrue("应保留 Container:Directory", text.contains("Container:Directory"))
        assertTrue("应保留 Primary 项", text.contains("Item:Semantic=\"Primary\""))
    }

    @Test
    fun xiaomiMicroVideo_andUnknownPrefixElement_areRemoved() {
        val out = JpegUtil.sanitizeStillPhoto(jpegWithXmps(xiaomiXmp))
        assertClean(out)
    }

    @Test
    fun multipleXmpSegments_allAreCleaned() {
        // 第 1 段不含 motion（如纯 hdrgm），第 2 段含 MotionPhoto —— 两段都要处理
        val xmpKeep = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description rdf:about="" xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"><hdrgm:Version>1.0</hdrgm:Version></rdf:Description>
            </rdf:RDF></x:xmpmeta>"""
        val out = JpegUtil.sanitizeStillPhoto(jpegWithXmps(xmpKeep, googleXmp))
        assertClean(out)
        val text = ascii(out)
        assertTrue("两段处理后的剩余内容仍应保留 hdrgm", text.contains("hdrgm:Version"))
    }
}
