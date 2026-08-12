# -*- coding: utf-8 -*-
"""JPEG 段级工具：marker 扫描、EOI 定位、XMP APP1 替换/插入。

所有操作均为字节级，不重编码，保证图像数据无损。
"""
import struct

XMP_APP1_PREFIX = b'http://ns.adobe.com/xap/1.0/\x00'

# 无长度字段的独立 marker
_STANDALONE = {0x01, 0xD8, 0xD9, *range(0xD0, 0xD8)}


class JpegError(ValueError):
    pass


def iter_segments(data: bytes):
    """遍历 JPEG 头部段（SOS 之前）。

    yield (marker, seg_start, total_len, payload_start, payload_len)
    total_len 含 marker 2 字节与长度 2 字节。SOS 时停止（其后为熵编码数据）。
    """
    if len(data) < 4 or data[0] != 0xFF or data[1] != 0xD8:
        raise JpegError('不是有效的 JPEG（缺少 SOI）')
    yield 0xD8, 0, 2, 2, 0
    pos = 2
    size = len(data)
    while pos + 4 <= size:
        if data[pos] != 0xFF:
            raise JpegError(f'段边界错位 @{pos}')
        marker = data[pos + 1]
        if marker in _STANDALONE:
            yield marker, pos, 2, pos + 2, 0
            pos += 2
            continue
        seg_len = struct.unpack('>H', data[pos + 2:pos + 4])[0]
        if seg_len < 2 or pos + 2 + seg_len > size:
            raise JpegError(f'段长度非法 @{pos}')
        yield marker, pos, 2 + seg_len, pos + 4, seg_len - 2
        pos += 2 + seg_len
        if marker == 0xDA:  # SOS
            return


def find_eoi_end(data: bytes, sos_payload_end: int) -> int:
    """从 SOS 段有效载荷起点扫描熵编码数据，返回 EOI(FFD9) 之后的偏移。

    熵编码规则：FF 00 为转义字面量；FF D0-D7 为重启 marker；FF D9 为 EOI。
    """
    i = sos_payload_end
    size = len(data)
    while i + 1 < size:
        if data[i] == 0xFF:
            nxt = data[i + 1]
            if nxt == 0x00 or 0xD0 <= nxt <= 0xD7:
                i += 2
                continue
            if nxt == 0xD9:
                return i + 2
            # 其他 marker（理论上不应出现在熵编码中），按 2 字节跳过
            i += 2
            continue
        i += 1
    raise JpegError('未找到 EOI（文件可能损坏）')


def split_jpegs(data: bytes):
    """把可能由多个 JPEG 顺序拼接的数据拆成单个 JPEG 字节块列表。

    返回 [jpeg1, jpeg2, ...]，剩余非 JPEG 字节不在结果中。
    """
    out = []
    pos = 0
    size = len(data)
    while pos + 4 <= size and data[pos] == 0xFF and data[pos + 1] == 0xD8:
        sos_payload_end = None
        for marker, _s, _t, _ps, pl in iter_segments(data[pos:]):
            if marker == 0xDA:
                sos_payload_end = pos + _ps + pl
                break
        if sos_payload_end is None:
            raise JpegError('JPEG 缺少 SOS 段')
        eoi_end = find_eoi_end(data, sos_payload_end)
        out.append(data[pos:eoi_end])
        pos = eoi_end
        # 跳过后续 JPEG 之间可能的填充 0xFF
        while pos < size and data[pos] == 0xFF and pos + 1 < size and data[pos + 1] == 0xFF:
            pos += 1
    return out, pos


def get_dimensions(jpeg: bytes):
    """从 SOF0/SOF2 段读取图像尺寸，返回 (width, height)；失败返回 (0, 0)。"""
    for marker, _s, _t, payload_start, payload_len in iter_segments(jpeg):
        if marker in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
                      0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
            if payload_len >= 5:
                height = struct.unpack('>H', jpeg[payload_start + 1:payload_start + 3])[0]
                width = struct.unpack('>H', jpeg[payload_start + 3:payload_start + 5])[0]
                return width, height
    return 0, 0


def find_xmp_segment(jpeg: bytes):
    """定位 XMP APP1 段，返回 (seg_start, total_len, xmp_text)；无则 None。"""
    for marker, seg_start, total_len, payload_start, payload_len in iter_segments(jpeg):
        if marker == 0xE1 and jpeg[payload_start:payload_start + len(XMP_APP1_PREFIX)] == XMP_APP1_PREFIX:
            xmp = jpeg[payload_start + len(XMP_APP1_PREFIX):payload_start + payload_len]
            return seg_start, total_len, xmp.decode('utf-8', 'replace')
    return None


def build_xmp_app1(xmp_text: str) -> bytes:
    """构造 XMP APP1 段字节。"""
    payload = XMP_APP1_PREFIX + xmp_text.encode('utf-8')
    return b'\xff\xe1' + struct.pack('>H', len(payload) + 2) + payload


def replace_or_insert_xmp(jpeg: bytes, new_xmp_text: str) -> bytes:
    """替换已有 XMP APP1 段；不存在则插入到第一个 APP1(Exif) 之后（无 APP1 则紧随 SOI）。"""
    new_seg = build_xmp_app1(new_xmp_text)
    found = find_xmp_segment(jpeg)
    if found:
        seg_start, total_len, _ = found
        return jpeg[:seg_start] + new_seg + jpeg[seg_start + total_len:]
    # 插入位置：第一个 APP1(Exif) 之后，否则紧随 SOI
    insert_at = 2
    for marker, seg_start, total_len, payload_start, _pl in iter_segments(jpeg):
        if marker in _STANDALONE:
            continue
        if marker == 0xE1 and jpeg[payload_start:payload_start + 6] == b'Exif\x00\x00':
            insert_at = seg_start + total_len
        break  # 只看第一个非独立段
    return jpeg[:insert_at] + new_seg + jpeg[insert_at:]
