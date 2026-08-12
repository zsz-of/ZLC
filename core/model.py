# -*- coding: utf-8 -*-
"""格式无关的动态照片数据模型。"""
from dataclasses import dataclass, field


@dataclass
class LivePhotoAsset:
    """一张动态照片的规范化表示（所有字节均为无损透传）。"""

    primary_jpeg: bytes                 # 主 JPEG（SOI 到 EOI，含全部 APPn 段）
    gainmap_jpeg: bytes | None          # GainMap JPEG（Ultra HDR，无则 None）
    video_mp4: bytes                    # 纯 MP4 流（不含厂商附加数据/footer）
    source_format: str = ''             # 'google' | 'google-legacy' | 'oppo' | 'vivo'
    presentation_ts_us: int = -1        # 封面帧视频内时间戳（微秒），-1 未知
    livephoto_id: str | None = None     # vivo 关联 ID（28 字符）
    image_time: int | None = None       # footer JSON 的 imageTime（封面帧序号）
    video_info: dict = field(default_factory=dict)   # codec/width/height/fps/duration_us/rotation
    extras: dict = field(default_factory=dict)       # 厂商私有附加（ oppo blob 等，仅供同格式参考）

    @property
    def gainmap_len(self) -> int | None:
        return len(self.gainmap_jpeg) if self.gainmap_jpeg else None

    def effective_pts_us(self) -> int:
        """封面帧时间戳：未知时按 Google 规范取视频中点。"""
        if self.presentation_ts_us is not None and self.presentation_ts_us >= 0:
            return self.presentation_ts_us
        dur = self.video_info.get('duration_us', -1)
        return dur // 2 if dur and dur > 0 else -1

    def effective_image_time(self) -> int:
        """footer JSON 的 imageTime（封面帧序号）= round(pts * fps)。"""
        if self.image_time is not None:
            return self.image_time
        pts = self.effective_pts_us()
        fps = self.video_info.get('fps', 0.0)
        if pts >= 0 and fps > 0:
            return round(pts / 1_000_000 * fps)
        return self.video_info.get('frame_count', 0) // 2
