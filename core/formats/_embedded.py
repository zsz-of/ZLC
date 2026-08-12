# -*- coding: utf-8 -*-
"""单文件内嵌格式的共用读取逻辑（Google / OPPO）。"""
from .. import jpegutil, mp4util, xmptmpl
from ..model import LivePhotoAsset


def read_embedded(path: str, source_format: str, log) -> LivePhotoAsset:
    """读取 JPEG+视频 内嵌式动态照片。

    布局：Primary JPEG [+ GainMap JPEG] + MP4 视频 [+ 厂商附加数据]。
    """
    data = open(path, 'rb').read()
    xmp_found = jpegutil.find_xmp_segment(data)
    xmp_info = xmptmpl.parse_motion_xmp(xmp_found[2] if xmp_found else '')

    jpegs, consumed = jpegutil.split_jpegs(data)
    if not jpegs:
        raise ValueError('未找到主 JPEG 图像')
    primary = jpegs[0]
    gainmap = jpegs[1] if len(jpegs) > 1 else None

    # 视频定位
    if xmp_info['is_legacy_micro'] and xmp_info['microvideo_offset']:
        # 旧版 MicroVideo：视频起点 = 文件大小 - MicroVideoOffset
        video_payload = data[len(data) - xmp_info['microvideo_offset']:]
    else:
        video_payload = data[consumed:]
    if not mp4util.has_ftyp(video_payload):
        raise ValueError('JPEG 之后未找到有效的 MP4 视频（缺少 ftyp box）')
    mp4_len = mp4util.stream_length(video_payload)
    if mp4_len <= 0:
        raise ValueError('MP4 视频流解析失败')
    video = video_payload[:mp4_len]

    asset = LivePhotoAsset(
        primary_jpeg=primary,
        gainmap_jpeg=gainmap,
        video_mp4=video,
        source_format=source_format,
        presentation_ts_us=xmp_info['pts_us'],
    )
    asset.video_info = mp4util.get_track_info(video) or {}
    # 保留厂商附加数据（blob 等）供参考/同格式诊断
    asset.extras['payload_trailer'] = video_payload[mp4_len:]
    return asset
