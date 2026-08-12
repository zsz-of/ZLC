# -*- coding: utf-8 -*-
"""Google Motion Photo 标准格式（含旧版 MicroVideo 读取）。

布局：Primary JPEG [+ GainMap JPEG] + MP4 视频（视频为文件最后一项，其后无任何字节）。
XMP：GCamera:MotionPhoto=1 / MotionPhotoVersion=1 / MotionPhotoPresentationTimestampUs
     + Container:Directory（Primary / GainMap / MotionPhoto）。
文件名约定：basename 以 'MP' 结尾（如 PXL_xxx_MP.jpg），写出时默认遵循。
"""
import os

from .. import jpegutil, xmptmpl
from . import _embedded
from .base import FormatPlugin


def sniff_xmp(path: str, limit: int = 2 * 1024 * 1024) -> str:
    """从文件头部直接定位 XMP 文本（检测用，容忍截断）。"""
    try:
        with open(path, 'rb') as f:
            head = f.read(limit)
    except OSError:
        return ''
    if not head.startswith(b'\xff\xd8'):
        return ''
    idx = head.find(jpegutil.XMP_APP1_PREFIX)
    if idx == -1:
        return ''
    end = head.find(b'</x:xmpmeta>', idx)
    if end == -1:
        return ''
    return head[idx:end + 12].decode('utf-8', 'replace')


class GooglePlugin(FormatPlugin):
    name = 'google'
    display = 'Google Motion Photo（标准格式）'

    def detect(self, path: str) -> int:
        info = xmptmpl.parse_motion_xmp(sniff_xmp(path))
        if info['is_motion'] and not info['is_legacy_micro'] and not info['has_oplus']:
            return 90
        if info['is_legacy_micro']:
            return 80
        return 0

    def read(self, path: str, log):
        log('info', '按 Google Motion Photo 解析', 'Google')
        return _embedded.read_embedded(path, self.name, log)

    def write(self, asset, out_dir: str, stem: str, log, options: dict) -> list:
        # Google 规范：视频必须是文件最后一项，其后不允许有任何字节
        video = asset.video_mp4
        pts = asset.effective_pts_us()
        xmp = xmptmpl.build_google_xmp(pts, asset.gainmap_len, len(video))
        primary = jpegutil.replace_or_insert_xmp(asset.primary_jpeg, xmp)

        out = primary + (asset.gainmap_jpeg or b'') + video

        # 文件名约定：basename 以 MP 结尾（可在 options 关闭）
        if options.get('google_mp_suffix', True) and not stem.upper().endswith('MP'):
            stem = stem + '_MP'
        out_path = os.path.join(out_dir, stem + '.jpg')
        self._write_bytes(out_path, out)
        log('info', f'写出 Google 格式：{os.path.basename(out_path)}'
                    f'（图像 {len(primary):,}B + 视频 {len(video):,}B）', 'Google')
        return [out_path]
