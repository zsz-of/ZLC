# -*- coding: utf-8 -*-
"""转换管线：detect → read → write。同格式转换 = 原样复制（零损耗直通）。"""
import os
import shutil

from . import formats


class ConvertError(Exception):
    pass


def convert_file(path: str, target: str, out_dir: str, log, options: dict) -> list:
    """转换单个文件为指定格式，返回输出文件路径列表。

    target: 'google' | 'oppo' | 'vivo'
    options: {'google_mp_suffix': bool}
    """
    if target not in formats.BY_NAME:
        raise ConvertError(f'未知目标格式：{target}')

    plugin, score = formats.detect_best(path)
    if plugin is None or score < 50:
        raise ConvertError('无法识别的动态照片格式（非 Google/OPPO/vivo 动态照片）')
    log('info', f'识别为 {plugin.display}（置信度 {score}）', '转换')

    target_plugin = formats.BY_NAME[target]
    stem = os.path.splitext(os.path.basename(path))[0]
    os.makedirs(out_dir, exist_ok=True)

    # 同格式直通：原样复制，零损耗
    if plugin.name == target:
        outputs = []
        dst = os.path.join(out_dir, os.path.basename(path))
        shutil.copy2(path, dst)
        outputs.append(dst)
        if plugin.name == 'vivo':
            mp4 = os.path.splitext(path)[0] + '.mp4'
            if os.path.isfile(mp4):
                dst_mp4 = os.path.join(out_dir, os.path.basename(mp4))
                shutil.copy2(mp4, dst_mp4)
                outputs.append(dst_mp4)
        log('info', '源与目标格式相同，已原样复制（零损耗）', '转换')
        return outputs

    asset = plugin.read(path, log)
    if asset.presentation_ts_us < 0:
        log('warning', '源缺少封面帧时间戳，按规范回退为视频中点', '转换')
    return target_plugin.write(asset, out_dir, stem, log, options or {})
