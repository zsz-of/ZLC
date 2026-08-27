package com.zsz.zlivephoto.core

/**
 * XMP 模板与源 XMP 字段提取。
 * 模板逐字来自真实样本（OPPO Find X9s Pro / vivo iQOO 15），
 * 保证转换输出与原生格式的 XMP 结构、属性顺序、缩进完全一致。
 * 注意：除 Tail 外所有模板均以换行结尾（原始字符串闭合符前的空行即该尾换行）。
 */
internal object XmpTemplate {
    // ---------------------------------------------------------------- 模板

    private const val GoogleHeadHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
      hdrgm:Version="1.0"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="{pts}">

"""

    private const val GoogleHeadNonHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="{pts}">

"""

    private const val OppoHeadHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:OpCamera="http://ns.oplus.com/photos/1.0/camera/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        xmlns:VCamera="http://ns.vivo.com/photos/1.0/camera/"
      hdrgm:Version="1.0"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="{pts}"
      OpCamera:MotionPhotoPrimaryPresentationTimestampUs="{pts}"
      OpCamera:MotionPhotoOwner="oplus"
      OpCamera:OLivePhotoVersion="2"
      OpCamera:VideoLength="{mp4_len}"
      OpCamera:MotionPhotoEnable="True"
      VCamera:VMotionPhotoVersion="1"
      VCamera:VMediaKitVersion="1.0.0.8">

"""

    private const val OppoHeadNonHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:OpCamera="http://ns.oplus.com/photos/1.0/camera/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        xmlns:VCamera="http://ns.vivo.com/photos/1.0/camera/"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="{pts}"
      OpCamera:MotionPhotoPrimaryPresentationTimestampUs="{pts}"
      OpCamera:MotionPhotoOwner="oplus"
      OpCamera:OLivePhotoVersion="2"
      OpCamera:VideoLength="{mp4_len}"
      OpCamera:MotionPhotoEnable="True"
      VCamera:VMotionPhotoVersion="1"
      VCamera:VMediaKitVersion="1.0.0.8">

"""

    private const val VivoHeadHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.2">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
        hdrgm:Version="1.0">

"""

    private const val VivoHeadNonHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.2">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/">

"""

    private const val Tail: String = """
      </Container:Directory>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
"""

    private const val ItemPrimary: String = """
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="image/jpeg"
              Item:Semantic="Primary"
              Item:Length="0"
              Item:Padding="0"/>
          </rdf:li>

"""

    private const val ItemGainmap: String = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="image/jpeg"
              Item:Semantic="GainMap"
              Item:Length="{gainmap_len}"
              Item:Padding="0"/>
          </rdf:li>

"""

    private const val ItemVideo: String = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="video/mp4"
              Item:Semantic="MotionPhoto"
              Item:Length="{video_len}"
              Item:Padding="0"/>
          </rdf:li>
        </rdf:Seq>

"""

    // vivo 模板使用稍有不同的缩进（来自 vivo 样本）
    private const val VivoItemPrimary: String = """
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item
             Item:Semantic="Primary"
             Item:Mime="image/jpeg"/>
          </rdf:li>

"""

    private const val VivoItemGainmap: String = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
             Item:Semantic="GainMap"
             Item:Mime="image/jpeg"
             Item:Length="{gainmap_len}"/>
          </rdf:li>

"""

    private const val VivoTailClose: String = """
        </rdf:Seq>

"""

    // vivo 单文件实况模板（逐字来自 vivo 相册「关闭实况」合并产物的真实样本）
    // 结构 = Google 容器（Primary + GainMap + MotionPhoto 视频项）+ VCamera 私有字段；
    // vivo 相册识别单文件实况的关键是 GCamera:MotionPhoto="1"（="0" 即关闭实况状态）
    private const val VivoSingleHeadHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:VCamera="http://ns.vivo.com/photos/1.0/camera/"
      hdrgm:Version="1.0"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="{pts}"
      VCamera:VMotionPhotoVersion="1"
      VCamera:VMediaKitVersion="1.0.0.9">
"""
    private const val VivoSingleHeadNonHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:VCamera="http://ns.vivo.com/photos/1.0/camera/"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="{pts}"
      VCamera:VMotionPhotoVersion="1"
      VCamera:VMediaKitVersion="1.0.0.9">
"""
    private const val VivoSingleItemPrimary: String = """
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Semantic="Primary"
              Item:Mime="image/jpeg"/>
          </rdf:li>
"""
    private const val VivoSingleItemGainmap: String = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Semantic="GainMap"
              Item:Mime="image/jpeg"
              Item:Length="{gainmap_len}"/>
          </rdf:li>
"""
    private const val VivoSingleItemVideo: String = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="video/mp4"
              Item:Semantic="MotionPhoto"
              Item:Length="{video_len}"
              Item:Padding="0"/>
          </rdf:li>
        </rdf:Seq>
      </Container:Directory>
"""
    private const val VivoSingleTail: String = """
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>"""

    // 荣耀模板（Adobe XMP Core 5.1.2 + Google Container，无 MotionPhoto 标签）
    private const val HonorHeadHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.2">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description
     xmlns:Container="http://ns.google.com/photos/1.0/container/"
     xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
     xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
     hdrgm:Version="1.0">

"""

    private const val HonorHeadNonHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.2">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description
     xmlns:Container="http://ns.google.com/photos/1.0/container/"
     xmlns:Item="http://ns.google.com/photos/1.0/container/item/">

"""

    private const val HonorItemPrimary: String = """
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item
             Item:Semantic="Primary"
             Item:Mime="image/jpeg"/>
          </rdf:li>

"""

    private const val HonorItemGainmap: String = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
             Item:Semantic="GainMap"
             Item:Mime="image/jpeg"
             Item:Length="{gainmap_len}"/>
          </rdf:li>

"""

    // ---------------------------------------------------------------- 构建

    fun buildGoogleXmp(ptsUs: Long, gainmapLen: Int?, videoLen: Int): String {
        val head = if (gainmapLen != null) GoogleHeadHdr else GoogleHeadNonHdr
        val parts = mutableListOf(head.replace("{pts}", ptsUs.toString()), ItemPrimary)
        if (gainmapLen != null) {
            parts.add(ItemGainmap.replace("{gainmap_len}", gainmapLen.toString()))
        }
        parts.add(ItemVideo.replace("{video_len}", videoLen.toString()))
        parts.add(Tail)
        return parts.joinToString("")
    }

    /** videoLen 为 Container 声明的视频项长度（含 footer），mp4Len 为纯 MP4 流长度。 */
    fun buildOppoXmp(ptsUs: Long, gainmapLen: Int?, videoLen: Int, mp4Len: Int): String {
        val head = if (gainmapLen != null) OppoHeadHdr else OppoHeadNonHdr
        val parts = mutableListOf(
            head.replace("{pts}", ptsUs.toString()).replace("{mp4_len}", mp4Len.toString()),
            ItemPrimary
        )
        if (gainmapLen != null) {
            parts.add(ItemGainmap.replace("{gainmap_len}", gainmapLen.toString()))
        }
        parts.add(ItemVideo.replace("{video_len}", videoLen.toString()))
        parts.add(Tail)
        return parts.joinToString("")
    }

    /** 小米格式 = Google XMP + MicroVideo 旧版标签（双标签并存）。 */
    fun buildXiaomiXmp(ptsUs: Long, gainmapLen: Int?, videoLen: Int): String {
        var head = if (gainmapLen != null) GoogleHeadHdr else GoogleHeadNonHdr
        // 在 MotionPhoto 标签后追加 MicroVideo 标签：
        // head 以 '      GCamera:MotionPhotoPresentationTimestampUs="{pts}">\n' 结尾，
        // 替换最后的 '">' 为 MicroVideo 标签 + '>'
        val microTags =
            "      GCamera:MicroVideo=\"1\"\n" +
            "      GCamera:MicroVideoVersion=\"1\"\n" +
            "      GCamera:MicroVideoOffset=\"$videoLen\"\n" +
            "      GCamera:MicroVideoPresentationTimestampUs=\"$ptsUs\">\n"
        head = head.trimEnd()
        if (head.endsWith("\">")) {
            head = head.substring(0, head.length - 2) + "\n" + microTags
        }
        head = head.replace("{pts}", ptsUs.toString())
        val parts = mutableListOf(head, ItemPrimary)
        if (gainmapLen != null) {
            parts.add(ItemGainmap.replace("{gainmap_len}", gainmapLen.toString()))
        }
        parts.add(ItemVideo.replace("{video_len}", videoLen.toString()))
        parts.add(Tail)
        return parts.joinToString("")
    }

    fun buildVivoXmp(gainmapLen: Int?): String {
        val head = if (gainmapLen != null) VivoHeadHdr else VivoHeadNonHdr
        val parts = mutableListOf(head, VivoItemPrimary)
        if (gainmapLen != null) {
            parts.add(VivoItemGainmap.replace("{gainmap_len}", gainmapLen.toString()))
        }
        parts.add(VivoTailClose)
        parts.add(Tail)
        return parts.joinToString("")
    }

    /** vivo 单文件实况：Google 容器 + VCamera 私有字段，MotionPhoto 恒为 1。 */
    fun buildVivoSingleXmp(ptsUs: Long, gainmapLen: Int?, videoLen: Int): String {
        val head = if (gainmapLen != null) VivoSingleHeadHdr else VivoSingleHeadNonHdr
        val parts = mutableListOf(head.replace("{pts}", ptsUs.toString()), VivoSingleItemPrimary)
        if (gainmapLen != null) {
            parts.add(VivoSingleItemGainmap.replace("{gainmap_len}", gainmapLen.toString()))
        }
        parts.add(VivoSingleItemVideo.replace("{video_len}", videoLen.toString()))
        parts.add(VivoSingleTail)
        return parts.joinToString("")
    }

    /**
     * 荣耀 XMP：Adobe XMP Core 5.1.2 + Google Container{Primary, GainMap}。
     * 注意：Container 内只含图像项，不含视频项（视频靠文件尾 LIVE_ 标记定位）。
     */
    fun buildHonorXmp(gainmapLen: Int?): String {
        val head = if (gainmapLen != null) HonorHeadHdr else HonorHeadNonHdr
        val parts = mutableListOf(head, HonorItemPrimary)
        if (gainmapLen != null) {
            parts.add(HonorItemGainmap.replace("{gainmap_len}", String.format("%08d", gainmapLen)))
        }
        parts.add(VivoTailClose) // 荣耀闭合缩进同 vivo
        parts.add(Tail)
        return parts.joinToString("")
    }

    // ---------------------------------------------------------------- 解析

    internal class MotionXmpInfo {
        var isMotion: Boolean = false
        var isLegacyMicro: Boolean = false
        var ptsUs: Long = -1L
        var microVideoOffset: Int? = null
        var hasOplus: Boolean = false
        var oppoVideoLen: Int? = null
        var hasBoth: Boolean = false
        val items: MutableList<ContainerItem> = mutableListOf()
    }

    internal class ContainerItem {
        var semantic: String? = null
        var mime: String? = null
        var length: Int? = null
        var padding: Int = 0
    }

    private val motionPhotoRegex = Regex("""GCamera:MotionPhoto="(\d+)"""")
    private val microVideoRegex = Regex("""GCamera:MicroVideo="(\d+)"""")
    private val motionPtsRegex = Regex("""GCamera:MotionPhotoPresentationTimestampUs="(-?\d+)"""")
    private val microOffsetRegex = Regex("""GCamera:MicroVideoOffset="(\d+)"""")
    private val microPtsRegex = Regex("""GCamera:MicroVideoPresentationTimestampUs="(-?\d+)"""")
    private val oppoVideoLenRegex = Regex("""OpCamera:VideoLength="(\d+)"""")
    // 注意：属性值允许含 '/'（如 Item:Mime="video/mp4"），故不能用 [^>/] 排除斜杠，
    // 否则 video item 匹配在斜杠处截断（与 C# 端 [^>]*? 保持一致）
    private val containerItemRegex = Regex("""<Container:Item\b([^>]*?)/?>""", RegexOption.DOT_MATCHES_ALL)
    private val semanticRegex = Regex("""Item:Semantic="([^"]+)"""")
    private val mimeRegex = Regex("""Item:Mime="([^"]+)"""")
    private val lengthRegex = Regex("""Item:Length="(\d+)"""")
    private val paddingRegex = Regex("""Item:Padding="(\d+)"""")

    fun parseMotionXmp(xmpText: String): MotionXmpInfo {
        val info = MotionXmpInfo()
        if (xmpText.isEmpty()) return info

        val motionMatch = motionPhotoRegex.find(xmpText)
        val hasMotion = motionMatch != null && motionMatch.groupValues[1] == "1"

        val microMatch = microVideoRegex.find(xmpText)
        val hasMicro = microMatch != null && microMatch.groupValues[1] == "1"

        if (hasMotion) {
            info.isMotion = true
            val ptsMatch = motionPtsRegex.find(xmpText)
            if (ptsMatch != null) {
                ptsMatch.groupValues[1].toLongOrNull()?.let { info.ptsUs = it }
            }
        }
        if (hasMicro) {
            info.isMotion = true
            info.isLegacyMicro = true
            val offsetMatch = microOffsetRegex.find(xmpText)
            if (offsetMatch != null) {
                offsetMatch.groupValues[1].toIntOrNull()?.let { info.microVideoOffset = it }
            }
            val ptsMatch = microPtsRegex.find(xmpText)
            if (ptsMatch != null) {
                ptsMatch.groupValues[1].toLongOrNull()?.let { info.ptsUs = it }
            }
        }

        info.hasBoth = hasMotion && hasMicro

        if (xmpText.contains("ns.oplus.com/photos") || xmpText.contains("OpCamera:")) {
            info.hasOplus = true
            val lenMatch = oppoVideoLenRegex.find(xmpText)
            if (lenMatch != null) {
                lenMatch.groupValues[1].toIntOrNull()?.let { info.oppoVideoLen = it }
            }
        }

        for (im in containerItemRegex.findAll(xmpText)) {
            val attrs = im.groupValues[1]
            val item = ContainerItem()
            semanticRegex.find(attrs)?.let { item.semantic = it.groupValues[1] }
            mimeRegex.find(attrs)?.let { item.mime = it.groupValues[1] }
            lengthRegex.find(attrs)?.let { match ->
                match.groupValues[1].toIntOrNull()?.let { len -> item.length = len }
            }
            paddingRegex.find(attrs)?.let { match ->
                match.groupValues[1].toIntOrNull()?.let { pad -> item.padding = pad }
            }
            info.items.add(item)
        }

        return info
    }
}
