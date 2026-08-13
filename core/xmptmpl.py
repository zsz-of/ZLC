# -*- coding: utf-8 -*-
"""XMP 模板与源 XMP 字段提取。

模板逐字来自真实样本（OPPO Find X9s Pro / vivo iQOO 15），
保证转换输出与原生格式的 XMP 结构、属性顺序、缩进完全一致。
"""
import re

# ---------------------------------------------------------------- 模板
#
# HDR (Ultra HDR) 与非 HDR 的区别：
# - HDR 照片有 GainMap JPEG，Primary XMP 声明 xmlns:hdrgm + hdrgm:Version="1.0"，
#   Container:Directory 含 GainMap 项。
# - 非 HDR 照片无 GainMap JPEG，Primary XMP 不应出现 hdrgm 命名空间。
# 以下每个格式的 HEAD 拆为 _HDR / _NONHDR 两个版本，由 build_* 函数按 gainmap 选择。

_GOOGLE_HEAD_HDR = '''<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
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
'''

_GOOGLE_HEAD_NONHDR = '''<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="{pts}">
'''

_OPPO_HEAD_HDR = '''<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
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
'''

_OPPO_HEAD_NONHDR = '''<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
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
'''

_VIVO_HEAD_HDR = '''<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.2">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
        hdrgm:Version="1.0">
'''

_VIVO_HEAD_NONHDR = '''<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.2">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/">
'''

_TAIL = '''      </Container:Directory>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>'''

_ITEM_PRIMARY = '''      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="image/jpeg"
              Item:Semantic="Primary"
              Item:Length="0"
              Item:Padding="0"/>
          </rdf:li>
'''

_ITEM_GAINMAP = '''          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="image/jpeg"
              Item:Semantic="GainMap"
              Item:Length="{gainmap_len}"
              Item:Padding="0"/>
          </rdf:li>
'''

_ITEM_VIDEO = '''          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="video/mp4"
              Item:Semantic="MotionPhoto"
              Item:Length="{video_len}"
              Item:Padding="0"/>
          </rdf:li>
        </rdf:Seq>
'''

# vivo 模板使用稍有不同的缩进（来自 vivo 样本）
_VIVO_ITEM_PRIMARY = '''      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item
             Item:Semantic="Primary"
             Item:Mime="image/jpeg"/>
          </rdf:li>
'''

_VIVO_ITEM_GAINMAP = '''          <rdf:li rdf:parseType="Resource">
            <Container:Item
             Item:Semantic="GainMap"
             Item:Mime="image/jpeg"
             Item:Length="{gainmap_len}"/>
          </rdf:li>
'''

_VIVO_TAIL_CLOSE = '''        </rdf:Seq>
'''


def build_google_xmp(pts_us: int, gainmap_len: int | None, video_len: int) -> str:
    head = _GOOGLE_HEAD_HDR if gainmap_len is not None else _GOOGLE_HEAD_NONHDR
    parts = [head.format(pts=pts_us), _ITEM_PRIMARY]
    if gainmap_len is not None:
        parts.append(_ITEM_GAINMAP.format(gainmap_len=gainmap_len))
    parts.append(_ITEM_VIDEO.format(video_len=video_len))
    parts.append(_TAIL)
    return ''.join(parts)


def build_oppo_xmp(pts_us: int, gainmap_len: int | None, video_len: int, mp4_len: int) -> str:
    """video_len 为 Container 声明的视频项长度（含 footer），mp4_len 为纯 MP4 流长度。"""
    head = _OPPO_HEAD_HDR if gainmap_len is not None else _OPPO_HEAD_NONHDR
    parts = [head.format(pts=pts_us, mp4_len=mp4_len), _ITEM_PRIMARY]
    if gainmap_len is not None:
        parts.append(_ITEM_GAINMAP.format(gainmap_len=gainmap_len))
    parts.append(_ITEM_VIDEO.format(video_len=video_len))
    parts.append(_TAIL)
    return ''.join(parts)


def build_xiaomi_xmp(pts_us: int, gainmap_len: int | None, video_len: int) -> str:
    """小米格式 = Google XMP + MicroVideo 旧版标签（双标签并存）。"""
    head = _GOOGLE_HEAD_HDR if gainmap_len is not None else _GOOGLE_HEAD_NONHDR
    # 在 MotionPhoto 标签后追加 MicroVideo 标签
    micro_tags = (
        f'      GCamera:MicroVideo="1"\n'
        f'      GCamera:MicroVideoVersion="1"\n'
        f'      GCamera:MicroVideoOffset="{video_len}"\n'
        f'      GCamera:MicroVideoPresentationTimestampUs="{pts_us}">\n'
    )
    # head 以 '      GCamera:MotionPhotoPresentationTimestampUs="{pts}">\n' 结尾
    # 替换最后的 '">' 为 MicroVideo 标签 + '>'
    head = head.rstrip()
    if head.endswith('">'):
        head = head[:-2] + '\n' + micro_tags
    parts = [head.format(pts=pts_us), _ITEM_PRIMARY]
    if gainmap_len is not None:
        parts.append(_ITEM_GAINMAP.format(gainmap_len=gainmap_len))
    parts.append(_ITEM_VIDEO.format(video_len=video_len))
    parts.append(_TAIL)
    return ''.join(parts)


def build_vivo_xmp(gainmap_len: int | None) -> str:
    head = _VIVO_HEAD_HDR if gainmap_len is not None else _VIVO_HEAD_NONHDR
    parts = [head, _VIVO_ITEM_PRIMARY]
    if gainmap_len is not None:
        parts.append(_VIVO_ITEM_GAINMAP.format(gainmap_len=gainmap_len))
    parts.append(_VIVO_TAIL_CLOSE)
    parts.append(_TAIL)
    return ''.join(parts)


# ---------------------------------------------------------------- 解析

def _m(pattern: str, text: str, cast=str, default=None):
    mm = re.search(pattern, text)
    if not mm:
        return default
    try:
        return cast(mm.group(1))
    except (ValueError, TypeError):
        return default


def parse_motion_xmp(xmp_text: str) -> dict:
    """从源 XMP 提取动态照片相关字段。"""
    info = {
        'is_motion': False, 'is_legacy_micro': False, 'pts_us': -1,
        'microvideo_offset': None, 'has_oplus': False, 'oppo_video_len': None,
        'has_both': False, 'items': [],
    }
    if not xmp_text:
        return info
    has_motion = _m(r'GCamera:MotionPhoto="(\d+)"', xmp_text, int, 0) == 1
    has_micro = _m(r'GCamera:MicroVideo="(\d+)"', xmp_text, int, 0) == 1
    if has_motion:
        info['is_motion'] = True
        info['pts_us'] = _m(r'GCamera:MotionPhotoPresentationTimestampUs="(-?\d+)"', xmp_text, int, -1)
    if has_micro:
        info['is_motion'] = True
        info['is_legacy_micro'] = True
        info['microvideo_offset'] = _m(r'GCamera:MicroVideoOffset="(\d+)"', xmp_text, int)
        info['pts_us'] = _m(r'GCamera:MicroVideoPresentationTimestampUs="(-?\d+)"', xmp_text, int, -1)
    info['has_both'] = has_motion and has_micro
    if 'ns.oplus.com/photos' in xmp_text or 'OpCamera:' in xmp_text:
        info['has_oplus'] = True
        info['oppo_video_len'] = _m(r'OpCamera:VideoLength="(\d+)"', xmp_text, int)
    # Container 目录项
    for im in re.finditer(r'<Container:Item\b([^>/]*)/?>', xmp_text, re.S):
        attrs = im.group(1)
        info['items'].append({
            'semantic': _m(r'Item:Semantic="([^"]+)"', attrs),
            'mime': _m(r'Item:Mime="([^"]+)"', attrs),
            'length': _m(r'Item:Length="(\d+)"', attrs, int),
            'padding': _m(r'Item:Padding="(\d+)"', attrs, int, 0),
        })
    return info
