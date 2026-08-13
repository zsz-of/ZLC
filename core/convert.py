# -*- coding: utf-8 -*-
"""转换管线：detect → read → write。同格式转换 = 原样复制（零损耗直通）。"""
import ctypes
import os
import shutil
import sys

from . import formats


class ConvertError(Exception):
    pass


def _copy_timestamps(src: str, dst: str):
    """把源文件的创建时间、修改时间、访问时间复制到目标文件。

    Windows 上 ctime 为创建时间，需通过 SetFileTime API 设置；
    mtime/atime 用 os.utime（跨平台）。
    """
    st = os.stat(src)
    # atime + mtime（跨平台）
    os.utime(dst, (st.st_atime, st.st_mtime))
    # Windows 创建时间（ctime）
    if sys.platform == 'win32':
        try:
            # FILETIME 是 100ns 为单位的时间戳，epoch 从 1601-01-01 起
            EPOCH_DIFF = 116444736000000000
            ctime_ft = int((st.st_ctime + EPOCH_DIFF / 1e7) * 1e7)

            kernel32 = ctypes.windll.kernel32
            CreateFileW = kernel32.CreateFileW
            SetFileTime = kernel32.SetFileTime
            CloseHandle = kernel32.CloseHandle

            GENERIC_WRITE = 0x40000000
            OPEN_EXISTING = 3
            FILE_FLAG_BACKUP_SEMANTICS = 0x02000000

            handle = CreateFileW(dst, GENERIC_WRITE, 0, None,
                                 OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, None)
            if handle != -1 and handle != 0:
                ft = ctypes.c_ulonglong(ctime_ft)
                SetFileTime(handle, ctypes.byref(ft), None, None)
                CloseHandle(handle)
        except Exception:
            pass


def convert_file(path: str, target: str, out_dir: str, log, options: dict) -> list:
    """转换单个文件为指定格式，返回输出文件路径列表。

    target: 'google' | 'apple' | 'oppo' | 'vivo' | 'xiaomi'
    options: {'google_mp_suffix': bool}
    """
    if target not in formats.BY_NAME:
        raise ConvertError(f'未知目标格式：{target}')

    plugin, score = formats.detect_best(path)
    if plugin is None or score < 50:
        raise ConvertError('无法识别的动态照片格式（非 Google/OPPO/vivo/小米/Apple 动态照片）')
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
        elif plugin.name == 'apple':
            mov = os.path.splitext(path)[0] + '.mov'
            if os.path.isfile(mov):
                dst_mov = os.path.join(out_dir, os.path.basename(mov))
                shutil.copy2(mov, dst_mov)
                outputs.append(dst_mov)
        log('info', '源与目标格式相同，已原样复制（零损耗）', '转换')
        return outputs

    asset = plugin.read(path, log)
    if asset.presentation_ts_us < 0:
        log('warning', '源缺少封面帧时间戳，按规范回退为视频中点', '转换')
    outputs = target_plugin.write(asset, out_dir, stem, log, options or {})

    # 保留源文件的时间戳（创建时间、修改时间、访问时间）
    for out_path in outputs:
        try:
            _copy_timestamps(path, out_path)
        except Exception:
            pass

    return outputs
