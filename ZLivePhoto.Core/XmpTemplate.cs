using System.Text.RegularExpressions;

namespace ZLivePhoto.Core;

/// <summary>
/// XMP 模板与源 XMP 字段提取。
/// 模板逐字来自真实样本（OPPO Find X9s Pro / vivo iQOO 15），
/// 保证转换输出与原生格式的 XMP 结构、属性顺序、缩进完全一致。
/// 注意：除 Tail 外所有模板均以换行结尾（原始字符串闭合符前的空行即该尾换行）。
/// </summary>
public static partial class XmpTemplate
{
    // ---------------------------------------------------------------- 模板

    private const string GoogleHeadHdr = """
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

""";

    private const string GoogleHeadNonHdr = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="{pts}">

""";

    private const string OppoHeadHdr = """
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

""";

    private const string OppoHeadNonHdr = """
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

""";

    private const string VivoHeadHdr = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.2">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
        hdrgm:Version="1.0">

""";

    private const string VivoHeadNonHdr = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.2">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/">

""";

    private const string Tail = """
      </Container:Directory>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
""";

    private const string ItemPrimary = """
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="image/jpeg"
              Item:Semantic="Primary"
              Item:Length="0"
              Item:Padding="0"/>
          </rdf:li>

""";

    private const string ItemGainmap = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="image/jpeg"
              Item:Semantic="GainMap"
              Item:Length="{gainmap_len}"
              Item:Padding="0"/>
          </rdf:li>

""";

    private const string ItemVideo = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="video/mp4"
              Item:Semantic="MotionPhoto"
              Item:Length="{video_len}"
              Item:Padding="0"/>
          </rdf:li>
        </rdf:Seq>

""";

    // 荣耀模板（Adobe XMP Core 5.1.2 + Google Container，无 MotionPhoto 标签）
    private const string HonorHeadHdr = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.2">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description
     xmlns:Container="http://ns.google.com/photos/1.0/container/"
     xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
     xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
     hdrgm:Version="1.0">

""";

    private const string HonorHeadNonHdr = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.2">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description
     xmlns:Container="http://ns.google.com/photos/1.0/container/"
     xmlns:Item="http://ns.google.com/photos/1.0/container/item/">

""";

    // 荣耀 Item 模板（无 Padding 属性，缩进同 vivo）
    private const string HonorItemPrimary = """
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item
             Item:Semantic="Primary"
             Item:Mime="image/jpeg"/>
          </rdf:li>

""";

    private const string HonorItemGainmap = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
             Item:Semantic="GainMap"
             Item:Mime="image/jpeg"
             Item:Length="{gainmap_len}"/>
          </rdf:li>

""";

    // vivo 模板使用稍有不同的缩进（来自 vivo 样本）
    private const string VivoItemPrimary = """
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item
             Item:Semantic="Primary"
             Item:Mime="image/jpeg"/>
          </rdf:li>

""";

    private const string VivoItemGainmap = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
             Item:Semantic="GainMap"
             Item:Mime="image/jpeg"
             Item:Length="{gainmap_len}"/>
          </rdf:li>

""";

    private const string VivoTailClose = """
        </rdf:Seq>

""";

    // ---------------------------------------------------------------- 构建

    public static string BuildGoogleXmp(long ptsUs, int? gainmapLen, int videoLen)
    {
        var head = gainmapLen is not null ? GoogleHeadHdr : GoogleHeadNonHdr;
        var parts = new List<string> { head.Replace("{pts}", ptsUs.ToString()), ItemPrimary };
        if (gainmapLen is not null)
            parts.Add(ItemGainmap.Replace("{gainmap_len}", gainmapLen.Value.ToString()));
        parts.Add(ItemVideo.Replace("{video_len}", videoLen.ToString()));
        parts.Add(Tail);
        return string.Concat(parts);
    }

    /// <summary>videoLen 为 Container 声明的视频项长度（含 footer），mp4Len 为纯 MP4 流长度。</summary>
    public static string BuildOppoXmp(long ptsUs, int? gainmapLen, int videoLen, int mp4Len)
    {
        var head = gainmapLen is not null ? OppoHeadHdr : OppoHeadNonHdr;
        var parts = new List<string>
        {
            head.Replace("{pts}", ptsUs.ToString()).Replace("{mp4_len}", mp4Len.ToString()),
            ItemPrimary
        };
        if (gainmapLen is not null)
            parts.Add(ItemGainmap.Replace("{gainmap_len}", gainmapLen.Value.ToString()));
        parts.Add(ItemVideo.Replace("{video_len}", videoLen.ToString()));
        parts.Add(Tail);
        return string.Concat(parts);
    }

    /// <summary>小米格式 = Google XMP + MicroVideo 旧版标签（双标签并存）。</summary>
    public static string BuildXiaomiXmp(long ptsUs, int? gainmapLen, int videoLen)
    {
        var head = gainmapLen is not null ? GoogleHeadHdr : GoogleHeadNonHdr;
        // 在 MotionPhoto 标签后追加 MicroVideo 标签：
        // head 以 '      GCamera:MotionPhotoPresentationTimestampUs="{pts}">\n' 结尾，
        // 替换最后的 '">' 为 MicroVideo 标签 + '>'
        var microTags =
            "      GCamera:MicroVideo=\"1\"\n" +
            "      GCamera:MicroVideoVersion=\"1\"\n" +
            $"      GCamera:MicroVideoOffset=\"{videoLen}\"\n" +
            $"      GCamera:MicroVideoPresentationTimestampUs=\"{ptsUs}\">\n";
        head = head.TrimEnd();
        if (head.EndsWith("\">", StringComparison.Ordinal))
            head = head[..^2] + "\n" + microTags;
        head = head.Replace("{pts}", ptsUs.ToString());
        var parts = new List<string> { head, ItemPrimary };
        if (gainmapLen is not null)
            parts.Add(ItemGainmap.Replace("{gainmap_len}", gainmapLen.Value.ToString()));
        parts.Add(ItemVideo.Replace("{video_len}", videoLen.ToString()));
        parts.Add(Tail);
        return string.Concat(parts);
    }

    public static string BuildVivoXmp(int? gainmapLen)
    {
        var head = gainmapLen is not null ? VivoHeadHdr : VivoHeadNonHdr;
        var parts = new List<string> { head, VivoItemPrimary };
        if (gainmapLen is not null)
            parts.Add(VivoItemGainmap.Replace("{gainmap_len}", gainmapLen.Value.ToString()));
        parts.Add(VivoTailClose);
        parts.Add(Tail);
        return string.Concat(parts);
    }

    /// <summary>
    /// 荣耀 XMP：Adobe XMP Core 5.1.2 + Google Container{Primary, GainMap}。
    /// 注意：Container 内只含图像项，不含视频项（视频靠文件尾 LIVE_ 标记定位）。
    /// </summary>
    public static string BuildHonorXmp(int? gainmapLen)
    {
        var head = gainmapLen is not null ? HonorHeadHdr : HonorHeadNonHdr;
        var parts = new List<string> { head, HonorItemPrimary };
        if (gainmapLen is not null)
            parts.Add(HonorItemGainmap.Replace("{gainmap_len}", gainmapLen.Value.ToString("D8")));
        parts.Add(VivoTailClose); // 荣耀闭合缩进同 vivo
        parts.Add(Tail);
        return string.Concat(parts);
    }

    // ---------------------------------------------------------------- 解析

    public sealed class MotionXmpInfo
    {
        public bool IsMotion { get; set; }
        public bool IsLegacyMicro { get; set; }
        public long PtsUs { get; set; } = -1;
        public int? MicroVideoOffset { get; set; }
        public bool HasOplus { get; set; }
        public int? OppoVideoLen { get; set; }
        public bool HasBoth { get; set; }
        public List<ContainerItem> Items { get; } = [];
    }

    public sealed class ContainerItem
    {
        public string? Semantic { get; set; }
        public string? Mime { get; set; }
        public int? Length { get; set; }
        public int Padding { get; set; }
    }

    [GeneratedRegex(@"GCamera:MotionPhoto=""(\d+)""")]
    private static partial Regex MotionPhotoRegex();

    [GeneratedRegex(@"GCamera:MicroVideo=""(\d+)""")]
    private static partial Regex MicroVideoRegex();

    [GeneratedRegex(@"GCamera:MotionPhotoPresentationTimestampUs=""(-?\d+)""")]
    private static partial Regex MotionPtsRegex();

    [GeneratedRegex(@"GCamera:MicroVideoOffset=""(\d+)""")]
    private static partial Regex MicroOffsetRegex();

    [GeneratedRegex(@"GCamera:MicroVideoPresentationTimestampUs=""(-?\d+)""")]
    private static partial Regex MicroPtsRegex();

    [GeneratedRegex(@"OpCamera:VideoLength=""(\d+)""")]
    private static partial Regex OppoVideoLenRegex();

    // [^>]*? 懒惰匹配到 /> 之前；不排除 / 因为 "image/jpeg" 含 /
    [GeneratedRegex(@"<Container:Item\b([^>]*?)/\s*>", RegexOptions.Singleline)]
    private static partial Regex ContainerItemRegex();

    [GeneratedRegex(@"Item:Semantic=""([^""]+)""")]
    private static partial Regex SemanticRegex();

    [GeneratedRegex(@"Item:Mime=""([^""]+)""")]
    private static partial Regex MimeRegex();

    [GeneratedRegex(@"Item:Length=""(\d+)""")]
    private static partial Regex LengthRegex();

    [GeneratedRegex(@"Item:Padding=""(\d+)""")]
    private static partial Regex PaddingRegex();

    public static MotionXmpInfo ParseMotionXmp(string xmpText)
    {
        var info = new MotionXmpInfo();
        if (string.IsNullOrEmpty(xmpText))
            return info;

        var motionMatch = MotionPhotoRegex().Match(xmpText);
        bool hasMotion = motionMatch.Success && motionMatch.Groups[1].Value == "1";

        var microMatch = MicroVideoRegex().Match(xmpText);
        bool hasMicro = microMatch.Success && microMatch.Groups[1].Value == "1";

        if (hasMotion)
        {
            info.IsMotion = true;
            var ptsMatch = MotionPtsRegex().Match(xmpText);
            if (ptsMatch.Success && long.TryParse(ptsMatch.Groups[1].Value, out var pts))
                info.PtsUs = pts;
        }
        if (hasMicro)
        {
            info.IsMotion = true;
            info.IsLegacyMicro = true;
            var offsetMatch = MicroOffsetRegex().Match(xmpText);
            if (offsetMatch.Success && int.TryParse(offsetMatch.Groups[1].Value, out var offset))
                info.MicroVideoOffset = offset;
            var ptsMatch = MicroPtsRegex().Match(xmpText);
            if (ptsMatch.Success && long.TryParse(ptsMatch.Groups[1].Value, out var pts))
                info.PtsUs = pts;
        }

        info.HasBoth = hasMotion && hasMicro;

        if (xmpText.Contains("ns.oplus.com/photos", StringComparison.Ordinal) ||
            xmpText.Contains("OpCamera:", StringComparison.Ordinal))
        {
            info.HasOplus = true;
            var lenMatch = OppoVideoLenRegex().Match(xmpText);
            if (lenMatch.Success && int.TryParse(lenMatch.Groups[1].Value, out var len))
                info.OppoVideoLen = len;
        }

        foreach (Match im in ContainerItemRegex().Matches(xmpText))
        {
            var attrs = im.Groups[1].Value;
            var item = new ContainerItem();
            var sm = SemanticRegex().Match(attrs);
            if (sm.Success) item.Semantic = sm.Groups[1].Value;
            var mm = MimeRegex().Match(attrs);
            if (mm.Success) item.Mime = mm.Groups[1].Value;
            var lm = LengthRegex().Match(attrs);
            if (lm.Success && int.TryParse(lm.Groups[1].Value, out var len))
                item.Length = len;
            var pm = PaddingRegex().Match(attrs);
            if (pm.Success && int.TryParse(pm.Groups[1].Value, out var pad))
                item.Padding = pad;
            info.Items.Add(item);
        }

        return info;
    }
}
