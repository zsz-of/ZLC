# -*- coding: utf-8 -*-
"""vivo/OPPO 共用的 cameralbum! footer 编解码。

结构（已从样本字节级验证）：
    [prefix]                      'vivo'（vivo jpg）
                                  'vivoMediaExtInfo'+'vivo'（oppo 文件尾 / vivo mp4 uuid 内容）
    [json_bytes]                  UTF-8 JSON
    [uint32be json_len]
    'cameralbum!'                 11 字节标识
    [uint32be 47]                 其后 43 字节 + 本字段 4 字节
    [id_str 28B]                  livephoto ID（'0' 填充至 28 字节）
    [FF FF FF FF]
    [magic 11B]                   固定签名
"""
import json
import random
import string
import struct

MAGIC = bytes([0x1B, 0x2A, 0x39, 0x48, 0x57, 0x66, 0x75, 0x84, 0x93, 0xA2, 0xB3])
MARKER = b'cameralbum!'
VIVO_PREFIX = b'vivo'
EXT_PREFIX = b'vivoMediaExtInfovivo'  # vivoMediaExtInfo + vivo
ID_LEN = 28

# OPPO 内嵌单文件使用的固定 ID（样本实测：'motionphoto' + '0'*17）
OPPO_FIXED_ID = 'motionphoto' + '0' * 17


class FooterError(ValueError):
    pass


def generate_livephoto_id() -> str:
    """生成 vivo 风格 livephoto ID：'-<数字><8位随机字符>'，'0' 填充至 28 字符。"""
    num = random.randint(1, 2147483647)
    rand = ''.join(random.choices(string.ascii_letters + string.digits, k=8))
    return f'-{num}{rand}'.ljust(ID_LEN, '0')[:ID_LEN]


def build_footer(json_bytes: bytes, id_str: str, prefix: bytes) -> bytes:
    """构造完整 footer（含前缀）。"""
    id_bytes = id_str.encode('ascii')
    if len(id_bytes) != ID_LEN:
        id_bytes = id_bytes.ljust(ID_LEN, b'0')[:ID_LEN]
    tail = id_bytes + b'\xff\xff\xff\xff' + MAGIC
    return (prefix + json_bytes
            + struct.pack('>I', len(json_bytes))
            + MARKER
            + struct.pack('>I', len(tail) + 4)
            + tail)


def build_footer_json(fields: dict) -> bytes:
    """按样本键序生成 footer JSON（紧凑分隔符，与原生一致）。"""
    return json.dumps(fields, ensure_ascii=False, separators=(',', ':')).encode('utf-8')


def parse_footer(data: bytes):
    """从数据尾部解析 footer。

    返回 dict(json=dict, id=str, prefix=bytes, footer_start=int, version=int|None)，
    无合法 footer 时返回 None。
    """
    idx = data.rfind(MARKER)
    if idx == -1 or idx + 15 + 43 > len(data):
        return None
    len2 = struct.unpack('>I', data[idx + 11:idx + 15])[0]
    tail = data[idx + 15:idx + 15 + len2 - 4]
    if len(tail) != len2 - 4 or len(tail) != 43:
        return None
    if tail[28:32] != b'\xff\xff\xff\xff' or tail[32:43] != MAGIC:
        return None
    id_str = tail[:28].decode('ascii', 'replace')
    if idx < 4:
        return None
    len1 = struct.unpack('>I', data[idx - 4:idx])[0]
    json_start = idx - 4 - len1
    if json_start < 0:
        return None
    json_bytes = data[json_start:idx - 4]
    try:
        payload = json.loads(json_bytes.decode('utf-8'))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return None
    # 前缀识别
    prefix = b''
    footer_start = json_start
    if data[json_start - 20:json_start] == EXT_PREFIX:
        prefix = EXT_PREFIX
        footer_start = json_start - 20
    elif data[json_start - 4:json_start] == VIVO_PREFIX:
        prefix = VIVO_PREFIX
        footer_start = json_start - 4
    return {
        'json': payload,
        'id': id_str,
        'prefix': prefix,
        'footer_start': footer_start,
        'version': payload.get('version'),
        'image_time': payload.get('com.android.camera.imageTime'),
        'livephoto_id': payload.get('com.android.camera.livephoto'),
    }
