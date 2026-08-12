# -*- coding: utf-8 -*-
"""格式插件注册表。

新增格式（apple / honor / huawei ...）：新建插件文件后在此 import 并加入 PLUGINS。
"""
from .base import FormatPlugin
from .google import GooglePlugin
from .oppo import OppoPlugin
from .vivo import VivoPlugin

#: 已注册插件（GUI 下拉顺序即此顺序；检测按置信度取最高）
PLUGINS: list[FormatPlugin] = [
    GooglePlugin(),
    OppoPlugin(),
    VivoPlugin(),
]

#: name -> plugin 索引
BY_NAME: dict[str, FormatPlugin] = {p.name: p for p in PLUGINS}


def detect_best(path: str) -> tuple[FormatPlugin | None, int]:
    """返回置信度最高的插件及其置信度；全部不识别时返回 (None, 0)。"""
    best, best_score = None, 0
    for plugin in PLUGINS:
        try:
            score = plugin.detect(path)
        except Exception:
            score = 0
        if score > best_score:
            best, best_score = plugin, score
    return best, best_score
