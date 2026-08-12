# -*- coding: utf-8 -*-
"""MP4 (ISOBMFF) box 级工具：box 遍历、流边界定位、vivo uuid 处理、
lpex box 插入（含 stco/co64 偏移修复）、视频轨信息解析。

纯字节级操作，不重编码。
"""
import struct

VIVO_UUID = b'vivoMediaExtInfo'  # 16 字节，恰好作 vivo uuid box 的 UUID 字段


class Mp4Error(ValueError):
    pass


def iter_boxes(data: bytes, start: int, end: int):
    """遍历 [start, end) 区间内的顶层 box，yield (type_str, offset, size, header_len)。

    遇到非法 box 即停止。box type 按 ISOBMFF 必须为 4 个可打印 ASCII
    （OPPO 视频流后的私有浮点块 size 字段恰好合法但 type 非 ASCII，借此截断）。
    """
    pos = start
    while pos + 8 <= end:
        size = struct.unpack('>I', data[pos:pos + 4])[0]
        btype = data[pos + 4:pos + 8]
        if not all(0x20 <= b <= 0x7E for b in btype):
            break
        header = 8
        if size == 1:
            if pos + 16 > end:
                break
            size = struct.unpack('>Q', data[pos + 8:pos + 16])[0]
            header = 16
        elif size == 0:
            size = end - pos
        if size < header or pos + size > end:
            break
        yield btype.decode('latin1'), pos, size, header
        pos += size


def stream_length(data: bytes) -> int:
    """从文件头遍历顶层 box，返回合法 MP4 流的总长度（忽略流后附加数据）。"""
    last_end = 0
    for _t, _off, size, _h in iter_boxes(data, 0, len(data)):
        last_end = _off + size
    return last_end


def has_ftyp(data: bytes) -> bool:
    return len(data) >= 12 and data[4:8] == b'ftyp'


def strip_vivo_uuid(data: bytes) -> bytes:
    """若 MP4 末尾存在 UUID 为 'vivoMediaExtInfo' 的 uuid box，则去除。"""
    boxes = list(iter_boxes(data, 0, len(data)))
    if not boxes:
        return data
    t, off, size, _h = boxes[-1]
    if t == 'uuid' and data[off + 8:off + 24] == VIVO_UUID:
        return data[:off]
    return data


def _walk_into(data, off, size, header, path_types):
    """递归遍历容器 box，yield 所有后代 box。"""
    for t, coff, csize, cheader in iter_boxes(data, off + header, off + size):
        yield t, coff, csize, cheader
        if t in path_types:
            yield from _walk_into(data, coff, csize, cheader, path_types)


def insert_box_into_moov(data: bytes, box_type: bytes, payload: bytes) -> bytes:
    """把一个顶层自定义 box 追加为 moov 的最后一个子 box，并修复 stco/co64 偏移。

    插入会使 moov 之后（faststart 布局）的 mdat 数据整体后移，
    因此所有 >= 插入点的 chunk offset 都需要加上插入增量。
    """
    boxes = list(iter_boxes(data, 0, len(data)))
    moov = next((b for b in boxes if b[0] == 'moov'), None)
    if moov is None:
        raise Mp4Error('MP4 缺少 moov box')
    _t, moov_off, moov_size, _h = moov
    insert_at = moov_off + moov_size
    new_box = struct.pack('>I', len(payload) + 8) + box_type + payload
    delta = len(new_box)

    buf = bytearray(data)
    # 修复 moov 内所有 stco/co64 条目
    containers = ('trak', 'mdia', 'minf', 'stbl')
    for t, coff, csize, cheader in _walk_into(data, moov_off, moov_size, 8, containers):
        if t in ('stco', 'co64'):
            entry_size = 8 if t == 'co64' else 4
            fmt = '>Q' if t == 'co64' else '>I'
            body = coff + cheader  # 跳过 size+type
            count = struct.unpack('>I', data[body + 4:body + 8])[0]
            entries_start = body + 8
            for i in range(count):
                epos = entries_start + i * entry_size
                val = struct.unpack(fmt, data[epos:epos + entry_size])[0]
                if val >= insert_at:
                    struct.pack_into(fmt, buf, epos, val + delta)
    # 更新 moov size 并插入新 box
    struct.pack_into('>I', buf, moov_off, moov_size + delta)
    return bytes(buf[:insert_at]) + new_box + bytes(buf[insert_at:])


def get_track_info(data: bytes):
    """解析主视频轨信息。

    返回 dict(codec, width, height, rotation, duration_us, fps, frame_count)。
    找不到视频轨时返回 None。
    """
    boxes = list(iter_boxes(data, 0, len(data)))
    moov = next((b for b in boxes if b[0] == 'moov'), None)
    if moov is None:
        return None
    _t, moov_off, moov_size, _h = moov

    info = {'codec': None, 'width': 0, 'height': 0, 'rotation': 0,
            'duration_us': -1, 'fps': 0.0, 'frame_count': 0}
    for t, off, size, header in iter_boxes(data, moov_off + 8, moov_off + moov_size):
        if t != 'trak':
            continue
        trak = list(_walk_into(data, off, size, header, ('mdia', 'minf', 'stbl')))
        hdlr = next((b for b in trak if b[0] == 'hdlr'), None)
        # hdlr 为 FullBox：version/flags(4) + pre_defined(4) + handler_type(4)
        if hdlr is None or data[hdlr[1] + hdlr[3] + 8:hdlr[1] + hdlr[3] + 12] != b'vide':
            continue
        # tkhd：宽高为末尾两个 16.16 定点数；旋转矩阵在其前 36 字节
        tkhd = next((b for b in trak if b[0] == 'tkhd'), None)
        if tkhd:
            body = tkhd[1] + tkhd[3]
            w16 = struct.unpack('>I', data[tkhd[1] + tkhd[2] - 8:tkhd[1] + tkhd[2] - 4])[0]
            h16 = struct.unpack('>I', data[tkhd[1] + tkhd[2] - 4:tkhd[1] + tkhd[2]])[0]
            info['width'] = w16 >> 16
            info['height'] = h16 >> 16
            m = tkhd[1] + tkhd[2] - 8 - 36
            a, b, c, d = struct.unpack('>iiii', data[m:m + 16])
            if (a, b, c, d) == (0, 65536, -65536, 0):
                info['rotation'] = 90
            elif (a, b, c, d) == (-65536, 0, 0, -65536):
                info['rotation'] = 180
            elif (a, b, c, d) == (0, -65536, 65536, 0):
                info['rotation'] = 270
        # mdhd：timescale + duration
        mdhd = next((b for b in trak if b[0] == 'mdhd'), None)
        duration_s = 0.0
        if mdhd:
            body = mdhd[1] + mdhd[3]
            version = data[body]
            if version == 1:
                timescale, duration = struct.unpack('>IQ', data[body + 12:body + 24])
            else:
                timescale, duration = struct.unpack('>II', data[body + 12:body + 20])
            if timescale:
                duration_s = duration / timescale
                info['duration_us'] = int(duration_s * 1_000_000)
        # stts：样本总数 → fps
        stts = next((b for b in trak if b[0] == 'stts'), None)
        if stts:
            body = stts[1] + stts[3]
            count = struct.unpack('>I', data[body + 4:body + 8])[0]
            total = 0
            for i in range(count):
                sc = struct.unpack('>I', data[body + 8 + i * 8:body + 12 + i * 8])[0]
                total += sc
            info['frame_count'] = total
            if duration_s > 0:
                info['fps'] = total / duration_s
        # stsd：编码 fourcc
        stsd = next((b for b in trak if b[0] == 'stsd'), None)
        if stsd:
            body = stsd[1] + stsd[3]
            info['codec'] = data[body + 12:body + 16].decode('latin1')
        return info
    return None
