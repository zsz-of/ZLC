# -*- coding: utf-8 -*-
"""格式插件抽象基类。

新增格式（apple / honor / huawei ...）时：
1. 在 formats/ 下新建 <name>.py，继承 FormatPlugin 实现 detect/read/write
2. 在 formats/__init__.py 的 PLUGINS 中注册
无需改动 convert.py 等其他模块。
"""
from abc import ABC, abstractmethod


class FormatPlugin(ABC):
    #: 格式唯一标识（小写），如 'google' / 'oppo' / 'vivo'
    name: str = ''
    #: 展示名，如 'Google Motion Photo'
    display: str = ''

    @abstractmethod
    def detect(self, path: str) -> int:
        """返回 0-100 的置信度；0 表示确定不是本格式。"""

    @abstractmethod
    def read(self, path: str, log) -> 'object':
        """解析为 LivePhotoAsset。失败应抛出带中文说明的异常。"""

    @abstractmethod
    def write(self, asset, out_dir: str, stem: str, log, options: dict) -> list:
        """把 asset 写为本格式文件，返回写出的文件路径列表。"""

    # ---- 插件共用辅助 ----
    @staticmethod
    def _read_bytes(path: str) -> bytes:
        with open(path, 'rb') as f:
            return f.read()

    @staticmethod
    def _write_bytes(path: str, data: bytes):
        with open(path, 'wb') as f:
            f.write(data)
