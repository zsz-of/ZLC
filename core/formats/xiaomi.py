# -*- coding: utf-8 -*-
"""小米动态照片格式（Xiaomi Motion Photo）。

本质 = Google Motion Photo + EXIF 0x8897 标签 + XMP 双标签（MicroVideo + MotionPhoto）。
小米相册通过 EXIF 0x8897（十进制 34967）判定动态照片。
单文件内嵌格式（JPG + MP4 拼接），与 Google 布局相同。
"""
import os

from .. import exifutil, jpegutil, xmptmpl
from . import _embedded
from .base import FormatPlugin

# 小米相册识别的 EXIF 标签（十进制 34967）
XIAOMI_EXIF_TAG = 0x8897


class XiaomiPlugin(FormatPlugin):
    name = 'xiaomi'
    display = '小米动态照片'

    def detect(self, path: str) -> int:
        from .google import sniff_xmp
        xmp_text = sniff_xmp(path)
        info = xmptmpl.parse_motion_xmp(xmp_text)
        # 双标签并存是小米的强特征
        if info['has_both'] and not info['has_oplus']:
            return 95
        # EXIF 0x8897 存在也是小米特征
        try:
            with open(path, 'rb') as f:
                head = f.read(2 * 1024 * 1024)
            if head[:2] == b'\xff\xd8' and exifutil.has_exif_tag(head, XIAOMI_EXIF_TAG):
                return 90
        except OSError:
            pass
        return 0

    def read(self, path: str, log):
        log('info', '按小米动态照片解析（Google 兼容）', '小米')
        return _embedded.read_embedded(path, self.name, log)

    def write(self, asset, out_dir: str, stem: str, log, options: dict) -> list:
        video = asset.video_mp4
        pts = asset.effective_pts_us()
        xmp = xmptmpl.build_xiaomi_xmp(pts, asset.gainmap_len, len(video))
        primary = jpegutil.replace_or_insert_xmp(asset.primary_jpeg, xmp)
        # 写入 EXIF 0x8897 = 1（小米相册识别标签）
        primary = exifutil.add_ifd0_tag(primary, XIAOMI_EXIF_TAG, 1, 1)

        out = primary + (asset.gainmap_jpeg or b'') + video

        out_path = os.path.join(out_dir, stem + '.jpg')
        self._write_bytes(out_path, out)
        log('info', f'写出小米格式：{os.path.basename(out_path)}'
                    f'（图像 {len(primary):,}B + 视频 {len(video):,}B，EXIF 0x8897=1）', '小米')
        return [out_path]
