# -*- coding: utf-8 -*-
"""Apple Live Photo 格式（JPG + MOV 双文件）。

配对机制：ContentIdentifier UUID
- 图片端：XMP apple-fi:ContentIdentifier
- 视频端：QuickTime metadata com.apple.quicktime.content.identifier + still-image-time=0
文件类型：.jpg（或 .heic）+ .mov
"""
import os
import uuid

from .. import jpegutil, mp4util, xmptmpl
from ..model import LivePhotoAsset
from .base import FormatPlugin

# Apple XMP 命名空间
_APPLE_XMP_NS = 'xmlns:apple-fi="http://ns.apple.com/finalcut/1.0/"'


def _find_mov_sibling(path: str) -> str | None:
    """查找同名 .mov 文件。"""
    base = os.path.splitext(path)[0]
    for ext in ('.mov', '.MOV'):
        mov = base + ext
        if os.path.isfile(mov):
            return mov
    return None


def _build_apple_xmp(content_id: str) -> str:
    """构建包含 ContentIdentifier 的最小 XMP。"""
    return f'''<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        {_APPLE_XMP_NS}
      apple-fi:ContentIdentifier="{content_id}"/>
  </rdf:RDF>
</x:xmpmeta>'''


class ApplePlugin(FormatPlugin):
    name = 'apple'
    display = 'Apple Live Photo'

    def detect(self, path: str) -> int:
        # Apple Live Photo：同名 .mov 文件存在
        ext = os.path.splitext(path)[1].lower()
        if ext not in ('.jpg', '.jpeg', '.heic'):
            return 0
        mov = _find_mov_sibling(path)
        if mov is None:
            return 0
        # 进一步确认：MOV 文件是 QuickTime 格式
        try:
            with open(mov, 'rb') as f:
                header = f.read(16)
            if len(header) >= 12 and header[4:8] == b'ftyp':
                brand = header[8:12]
                if brand == b'qt  ':
                    return 90
                # MP4 品牌也可能是 Apple 导出的（iPhone 有时用 mp4）
                if brand in (b'isom', b'mp41', b'mp42', b'MSNV'):
                    return 75
        except OSError:
            pass
        return 60

    def read(self, path: str, log):
        log('info', '按 Apple Live Photo 解析', 'Apple')
        mov_path = _find_mov_sibling(path)
        if mov_path is None:
            raise ValueError('未找到同名 .mov 视频文件')

        # 读取 JPG
        primary = self._read_bytes(path)
        # 读取 MOV（视频流）
        mov_data = self._read_bytes(mov_path)
        if not mp4util.has_ftyp(mov_data):
            raise ValueError('MOV 文件缺少 ftyp box')

        asset = LivePhotoAsset(
            primary_jpeg=primary,
            gainmap_jpeg=None,
            video_mp4=mov_data,
            source_format=self.name,
            presentation_ts_us=0,  # Apple StillImageTime=0
        )
        asset.video_info = mp4util.get_track_info(mov_data) or {}
        return asset

    def write(self, asset, out_dir: str, stem: str, log, options: dict) -> list:
        # 生成配对 UUID
        content_id = str(uuid.uuid4()).upper()

        # 图片端：写入 XMP ContentIdentifier
        xmp = _build_apple_xmp(content_id)
        primary = jpegutil.replace_or_insert_xmp(asset.primary_jpeg, xmp)
        jpg_path = os.path.join(out_dir, stem + '.jpg')
        self._write_bytes(jpg_path, primary)

        # 视频端：MOV 格式 + Apple metadata
        mov_data = mp4util.mp4_to_mov(asset.video_mp4)
        mov_data = mp4util.add_apple_metadata(mov_data, content_id)
        mov_path = os.path.join(out_dir, stem + '.mov')
        self._write_bytes(mov_path, mov_data)

        log('info', f'写出 Apple 格式：{stem}.jpg + {stem}.mov'
                    f'（ContentIdentifier={content_id[:8]}...）', 'Apple')
        return [jpg_path, mov_path]
