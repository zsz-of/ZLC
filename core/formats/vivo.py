# -*- coding: utf-8 -*-
"""vivo 双文件动态照片格式（IMG_xxx.jpg + IMG_xxx.mp4）。

JPG：Ultra HDR（Primary + GainMap），XMP 仅含 Container（无 motion 标签），
     文件尾为 vivo footer：'vivo' + JSON + cameralbum! 块。
MP4：标准 MP4 + 文件尾 uuid box（UUID 字段 = 'vivoMediaExtInfo'，
     内容 = 'vivo' + JSON + cameralbum! 块）。
关联：JPG 与 MP4 的 JSON 中 com.android.camera.livephoto 为同一 28 字符 ID，
     且两文件 basename 相同。
"""
import os
import struct

from .. import jpegutil, mp4util, xmptmpl
from ..footer import (VIVO_PREFIX, build_footer, build_footer_json,
                      generate_livephoto_id, parse_footer)
from ..model import LivePhotoAsset
from .base import FormatPlugin
from .google import sniff_xmp

VIVO_VERSION = 2107


class VivoPlugin(FormatPlugin):
    name = 'vivo'
    display = 'vivo 动态照片（JPG + MP4 双文件）'

    # ---- 内部辅助 ----
    @staticmethod
    def _sibling_mp4(path: str) -> str | None:
        mp4 = os.path.splitext(path)[0] + '.mp4'
        return mp4 if os.path.isfile(mp4) else None

    @staticmethod
    def _build_json(image_time: int, live_id: str) -> bytes:
        return build_footer_json({
            'com.android.camera.imageTime': image_time,
            'com.android.camera.livephoto': live_id,
            'version': VIVO_VERSION,
        })

    @staticmethod
    def _build_uuid_box(json_bytes: bytes, live_id: str) -> bytes:
        payload = mp4util.VIVO_UUID + build_footer(json_bytes, live_id, VIVO_PREFIX)
        return struct.pack('>I', len(payload) + 8) + b'uuid' + payload

    # ---- 插件接口 ----

    def detect(self, path: str) -> int:
        if not path.lower().endswith(('.jpg', '.jpeg')):
            return 0
        xmp = sniff_xmp(path)
        info = xmptmpl.parse_motion_xmp(xmp)
        if info['is_motion']:
            return 0  # 内嵌式不归 vivo 管
        try:
            size = os.path.getsize(path)
            with open(path, 'rb') as f:
                f.seek(max(0, size - 8192))
                tail = f.read()
        except OSError:
            return 0
        footer = parse_footer(tail)
        if footer is None or not footer.get('livephoto_id'):
            return 0
        # jpg 尾部 livephoto ID + 同名 mp4 存在 → vivo
        return 90 if self._sibling_mp4(path) else 40

    def read(self, path: str, log):
        log('info', '按 vivo 双文件动态照片解析', 'vivo')
        data = self._read_bytes(path)
        footer = parse_footer(data)
        if footer is None or not footer.get('livephoto_id'):
            raise ValueError('JPG 尾部未找到 vivo livephoto 标记')
        live_id = footer['livephoto_id']

        mp4_path = self._sibling_mp4(path)
        if not mp4_path:
            raise ValueError(f'缺少伴生视频文件：{os.path.splitext(path)[0]}.mp4')

        # JPG 主体（去除 footer）→ 拆 Primary / GainMap
        body = data[:footer['footer_start']]
        jpegs, _consumed = jpegutil.split_jpegs(body)
        if not jpegs:
            raise ValueError('JPG 主体解析失败')
        primary = jpegs[0]
        gainmap = jpegs[1] if len(jpegs) > 1 else None

        # MP4：剥离末尾 vivo uuid box，并从其 footer 补充 imageTime、交叉校验 ID
        mp4_raw = self._read_bytes(mp4_path)
        mp4_footer = parse_footer(mp4_raw)
        image_time = footer.get('image_time')
        if mp4_footer:
            if image_time is None:
                image_time = mp4_footer.get('image_time')
            mp4_id = mp4_footer.get('livephoto_id')
            if mp4_id and mp4_id != live_id:
                log('warning',
                    f'JPG 与 MP4 的 livephoto ID 不一致：{live_id} / {mp4_id}', 'vivo')
        video = mp4util.strip_vivo_uuid(mp4_raw)
        if not mp4util.has_ftyp(video):
            raise ValueError('伴生 MP4 无效（缺少 ftyp box）')

        asset = LivePhotoAsset(
            primary_jpeg=primary,
            gainmap_jpeg=gainmap,
            video_mp4=video,
            source_format=self.name,
            livephoto_id=live_id,
            image_time=image_time,
        )
        asset.video_info = mp4util.get_track_info(video) or {}
        # 由 imageTime（帧序号）反推封面时间戳
        fps = asset.video_info.get('fps', 0.0)
        if asset.image_time is not None and fps > 0:
            asset.presentation_ts_us = round(asset.image_time / fps * 1_000_000)
        return asset

    def write(self, asset, out_dir: str, stem: str, log, options: dict) -> list:
        live_id = asset.livephoto_id or generate_livephoto_id()
        image_time = asset.effective_image_time()

        # JPG：vivo XMP（无 motion 标签）+ vivo footer
        xmp = xmptmpl.build_vivo_xmp(asset.gainmap_len)
        primary = jpegutil.replace_or_insert_xmp(asset.primary_jpeg, xmp)
        jpg_json = self._build_json(image_time, live_id)
        jpg_out = primary + (asset.gainmap_jpeg or b'') \
            + build_footer(jpg_json, live_id, VIVO_PREFIX)

        # MP4：纯视频流 + 末尾 uuid box
        video = mp4util.strip_vivo_uuid(asset.video_mp4)
        mp4_json = self._build_json(image_time, live_id)
        mp4_out = video + self._build_uuid_box(mp4_json, live_id)

        jpg_path = os.path.join(out_dir, stem + '.jpg')
        mp4_path = os.path.join(out_dir, stem + '.mp4')
        self._write_bytes(jpg_path, jpg_out)
        self._write_bytes(mp4_path, mp4_out)
        log('info', f'写出 vivo 格式：{stem}.jpg + {stem}.mp4（livephoto ID: {live_id}）',
            'vivo')
        return [jpg_path, mp4_path]
