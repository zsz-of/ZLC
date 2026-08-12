# -*- coding: utf-8 -*-
"""OPPO / oplus 单文件动态照片格式。

布局：Primary JPEG + GainMap JPEG + MP4 视频 + [私有浮点数据块(可选)] + footer。
XMP：Google GCamera 全套 + OpCamera:*（Owner=oplus / OLivePhotoVersion=2 /
     VideoLength=纯MP4长度 / MotionPhotoEnable=True）+ VCamera:*（跨厂商兼容）。
footer：'vivoMediaExtInfo'+'vivo' + JSON + cameralbum! 块（ID 固定为 motionphoto000...）。
lpex box：moov 内的 LivePhotoExtension（封面 pts / 裁剪 / EIS 矩阵），写入时合成。
"""
import json
import os

from .. import jpegutil, mp4util, xmptmpl
from ..footer import EXT_PREFIX, OPPO_FIXED_ID, build_footer, build_footer_json, parse_footer
from . import _embedded
from .base import FormatPlugin
from .google import sniff_xmp


def build_lpex_payload(asset) -> bytes:
    """按 OPPO 样本合成 lpex（LivePhotoExtension）载荷。"""
    vi = asset.video_info
    vw, vh = vi.get('width', 0), vi.get('height', 0)
    iw, ih = jpegutil.get_dimensions(asset.primary_jpeg)
    payload = {
        'coverFramePts': asset.effective_pts_us(),
        'cropRect': [0, 0, vw, vh],
        'desc': 'OppoMotionVideoExt',
        'matrixCount': 0,
        'originPhotoSize': [iw, ih],
        'photoCropFactor': 1.0,
        'photoCropMatrix': [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0],
        'photoCropRect': [0, 0, iw, ih],
        'photoEisCropFactor': [1.0, 1.0],
        'photoEisMatrix': [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0],
        'subVideoScaleFactor': 0.5,
        'version': 1,
        'videoOrientation': vi.get('rotation', 0),
        'videoSize': [vw, vh],
    }
    return b'LivePhotoExtension' + json.dumps(
        payload, ensure_ascii=False, separators=(',', ':')).encode('utf-8')


class OppoPlugin(FormatPlugin):
    name = 'oppo'
    display = 'OPPO 动态照片（单文件）'

    def detect(self, path: str) -> int:
        xmp = sniff_xmp(path)
        if not xmp:
            return 0
        info = xmptmpl.parse_motion_xmp(xmp)
        if info['is_motion'] and info['has_oplus']:
            return 95
        # 无 OpCamera 标签但文件尾有 cameralbum footer 也按 OPPO 处理
        if info['is_motion']:
            try:
                with open(path, 'rb') as f:
                    f.seek(max(0, os.path.getsize(path) - 4096))
                    tail = f.read()
                if parse_footer(tail) is not None:
                    return 85
            except OSError:
                pass
        return 0

    def read(self, path: str, log):
        log('info', '按 OPPO 动态照片解析', 'OPPO')
        asset = _embedded.read_embedded(path, self.name, log)
        # OPPO 附加信息：footer JSON（imageTime / version）
        data = self._read_bytes(path)
        footer = parse_footer(data)
        if footer:
            asset.image_time = footer.get('image_time')
            asset.extras['oppo_footer'] = footer
        return asset

    def write(self, asset, out_dir: str, stem: str, log, options: dict) -> list:
        video = asset.video_mp4
        # 源视频无 lpex 时合成插入（OPPO 相册编辑特性依赖该 box）
        if b'lpex' not in video[:65536]:
            try:
                video = mp4util.insert_box_into_moov(video, b'lpex', build_lpex_payload(asset))
                log('info', '已合成 lpex box（LivePhotoExtension）插入 moov', 'OPPO')
            except Exception as e:
                log('warning', f'lpex 合成失败，跳过（不影响播放）：{e}', 'OPPO')

        image_time = asset.effective_image_time()
        footer_json = build_footer_json({
            'com.android.camera.imageTime': image_time,
            'com.vivo.gallery.file.convert': 10004,
            'com.android.camera.livephoto': OPPO_FIXED_ID,
            'version': 2200,
        })
        footer = build_footer(footer_json, OPPO_FIXED_ID, EXT_PREFIX)

        pts = asset.effective_pts_us()
        xmp = xmptmpl.build_oppo_xmp(
            pts, asset.gainmap_len,
            video_len=len(video) + len(footer),  # Container 声明值含 footer
            mp4_len=len(video))                  # OpCamera:VideoLength 为纯 MP4 流
        primary = jpegutil.replace_or_insert_xmp(asset.primary_jpeg, xmp)

        out = primary + (asset.gainmap_jpeg or b'') + video + footer
        out_path = os.path.join(out_dir, stem + '.jpg')
        self._write_bytes(out_path, out)
        log('info', f'写出 OPPO 格式：{os.path.basename(out_path)}'
                    f'（图像 {len(primary):,}B + 视频 {len(video):,}B + footer {len(footer)}B）',
            'OPPO')
        return [out_path]
