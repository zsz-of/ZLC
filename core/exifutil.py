# -*- coding: utf-8 -*-
"""EXIF APP1 段的字节级读写：在 JPEG 的 IFD0 中添加/检测标签。

纯字节级操作，不影响图像数据。用于小米 0x8897 标签和 Apple ContentIdentifier。
"""
import struct

EXIF_PREFIX = b'Exif\x00\x00'

# TIFF 数据类型大小（字节）
_TYPE_SIZES = {
    1: 1,   # BYTE
    2: 1,   # ASCII
    3: 2,   # SHORT
    4: 4,   # LONG
    5: 8,   # RATIONAL
    6: 1,   # SBYTE
    7: 1,   # UNDEFINED
    8: 2,   # SSHORT
    9: 4,   # SLONG
    10: 8,  # SRATIONAL
    11: 4,  # FLOAT
    12: 8,  # DOUBLE
}


class ExifError(ValueError):
    pass


def _find_exif_app1(jpeg: bytes):
    """定位 EXIF APP1 段，返回 (seg_start, total_len, tiff_start)；无则 None。"""
    from .jpegutil import iter_segments
    for marker, seg_start, total_len, payload_start, payload_len in iter_segments(jpeg):
        if marker == 0xE1 and jpeg[payload_start:payload_start + 6] == EXIF_PREFIX:
            tiff_start = payload_start + 6
            return seg_start, total_len, tiff_start
    return None


def _tiff_endian(jpeg: bytes, tiff_start: int) -> str:
    """返回 TIFF 字节序格式前缀（'<' 或 '>'）。"""
    bo = jpeg[tiff_start:tiff_start + 2]
    if bo == b'II':
        return '<'
    if bo == b'MM':
        return '>'
    raise ExifError('无效的 TIFF 字节序标记')


def has_exif_tag(jpeg: bytes, tag_id: int) -> bool:
    """检测 JPEG 的 IFD0 中是否存在指定 EXIF 标签。"""
    found = _find_exif_app1(jpeg)
    if not found:
        return False
    _seg_start, _total_len, tiff_start = found
    try:
        e = _tiff_endian(jpeg, tiff_start)
        ifd0_off = struct.unpack_from(e + 'I', jpeg, tiff_start + 4)[0]
        ifd0_abs = tiff_start + ifd0_off
        count = struct.unpack_from(e + 'H', jpeg, ifd0_abs)[0]
        for i in range(count):
            entry_off = ifd0_abs + 2 + i * 12
            tid = struct.unpack_from(e + 'H', jpeg, entry_off)[0]
            if tid == tag_id:
                return True
    except (struct.error, IndexError):
        pass
    return False


def read_exif_tag_value(jpeg: bytes, tag_id: int):
    """读取 IFD0 中指定标签的值（仅支持 inline 值，即 data_size <= 4）。"""
    found = _find_exif_app1(jpeg)
    if not found:
        return None
    _seg_start, _total_len, tiff_start = found
    try:
        e = _tiff_endian(jpeg, tiff_start)
        ifd0_off = struct.unpack_from(e + 'I', jpeg, tiff_start + 4)[0]
        ifd0_abs = tiff_start + ifd0_off
        count = struct.unpack_from(e + 'H', jpeg, ifd0_abs)[0]
        for i in range(count):
            entry_off = ifd0_abs + 2 + i * 12
            tid, ttype, tcount = struct.unpack_from(e + 'HHI', jpeg, entry_off)
            if tid == tag_id:
                type_size = _TYPE_SIZES.get(ttype, 1)
                data_size = type_size * tcount
                if data_size <= 4:
                    # inline 值
                    val_bytes = jpeg[entry_off + 8:entry_off + 8 + data_size]
                    if ttype == 1:  # BYTE
                        return val_bytes[0]
                    if ttype == 3:  # SHORT
                        return struct.unpack(e + 'H', val_bytes[:2])[0]
                    if ttype == 4:  # LONG
                        return struct.unpack(e + 'I', val_bytes[:4])[0]
                    return val_bytes
                else:
                    # 偏移引用
                    data_off = struct.unpack_from(e + 'I', jpeg, entry_off + 8)[0]
                    data_abs = tiff_start + data_off
                    return jpeg[data_abs:data_abs + data_size]
    except (struct.error, IndexError):
        pass
    return None


def add_ifd0_tag(jpeg: bytes, tag_id: int, tag_type: int, value) -> bytes:
    """在 JPEG 的 EXIF IFD0 中添加一个标签。

    仅支持 inline 值（data_size <= 4）：BYTE/SHORT/LONG。
    若 JPEG 无 EXIF APP1 段，创建最小段。若已有该标签，替换之。

    返回修改后的 JPEG 字节。
    """
    type_size = _TYPE_SIZES.get(tag_type)
    if type_size is None:
        raise ExifError(f'不支持的 TIFF 类型 {tag_type}')

    # 编码值到 4 字节 inline
    if tag_type == 1:  # BYTE
        val_inline = struct.pack('B', value) + b'\x00' * 3
    elif tag_type == 3:  # SHORT
        val_inline = struct.pack('<H', value) + b'\x00' * 2
    elif tag_type == 4:  # LONG
        val_inline = struct.pack('<I', value)
    else:
        raise ExifError(f'不支持的 inline 类型 {tag_type}')

    count = 1
    found = _find_exif_app1(jpeg)

    if found is None:
        # 创建最小 EXIF APP1 段
        ifd0 = struct.pack('<H', 1)
        ifd0 += struct.pack('<HHI', tag_id, tag_type, count) + val_inline
        ifd0 += struct.pack('<I', 0)
        tiff = b'II' + struct.pack('<H', 42) + struct.pack('<I', 8) + ifd0
        payload = EXIF_PREFIX + tiff
        app1 = b'\xff\xe1' + struct.pack('>H', len(payload) + 2) + payload
        return jpeg[:2] + app1 + jpeg[2:]

    seg_start, total_len, tiff_start = found
    e = _tiff_endian(jpeg, tiff_start)

    ifd0_off = struct.unpack_from(e + 'I', jpeg, tiff_start + 4)[0]
    ifd0_abs = tiff_start + ifd0_off
    old_count = struct.unpack_from(e + 'H', jpeg, ifd0_abs)[0]

    # 读取现有 entry（排除同 tag，实现替换语义）
    entries = []
    for i in range(old_count):
        entry_off = ifd0_abs + 2 + i * 12
        tid, ttype, tcount = struct.unpack_from(e + 'HHI', jpeg, entry_off)
        if tid == tag_id:
            continue
        entries.append((tid, ttype, tcount, jpeg[entry_off + 8:entry_off + 12]))

    # 新增/替换一个 entry → 净增 12 字节
    delta = 12

    # 修复旧 entry 中的数据区偏移引用
    data_area_start_rel = ifd0_off + 2 + old_count * 12 + 4
    fixed = []
    for tid, ttype, tcount, tval in entries:
        ts = _TYPE_SIZES.get(ttype, 1)
        if ts * tcount > 4:
            old_off = struct.unpack(e + 'I', tval)[0]
            if old_off >= data_area_start_rel:
                tval = struct.pack(e + 'I', old_off + delta)
        fixed.append((tid, ttype, tcount, tval))

    # 合并新 entry 并按 tag ID 排序
    all_entries = sorted(fixed + [(tag_id, tag_type, count, val_inline)],
                         key=lambda x: x[0])
    new_ifd0 = struct.pack(e + 'H', len(all_entries))
    for tid, ttype, tcount, tval in all_entries:
        new_ifd0 += struct.pack(e + 'HHI', tid, ttype, tcount) + tval

    # next IFD offset
    old_next_pos = ifd0_abs + 2 + old_count * 12
    old_next = struct.unpack_from(e + 'I', jpeg, old_next_pos)[0]
    if old_next != 0:
        old_next += delta
    new_ifd0 += struct.pack(e + 'I', old_next)

    # IFD0 数据区（原样保留）
    app1_end = seg_start + total_len
    old_data = jpeg[old_next_pos + 4:app1_end]

    # 重建 APP1 段
    new_tiff = jpeg[tiff_start:tiff_start + ifd0_off] + new_ifd0 + old_data
    new_payload = EXIF_PREFIX + new_tiff
    new_app1 = b'\xff\xe1' + struct.pack('>H', len(new_payload) + 2) + new_payload

    return jpeg[:seg_start] + new_app1 + jpeg[app1_end:]
