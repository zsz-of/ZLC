# Z-LivePhoto-Converter

> 动态照片格式互转工具：Google Motion Photo / OPPO 单文件 / vivo 双文件 / 小米 / Apple Live Photo 格式任意互转，字节级无损。

**开发者**: zsz & Kimi-K3
**版本**: v1.0.0

## 系统要求

- Windows 10 64-Bit 及以上
- Python 3.10+（源码运行）；封装版无需任何运行时
- 无需 ffmpeg 等外部依赖（核心解析全部基于 Python 标准库）

## 项目简介

把不同厂商的"动态照片"（Live Photo / Motion Photo）在保持图像与视频数据完全无损的前提下互相转换：

- **Google Motion Photo**（标准格式，Pixel 及遵循 Android 规范的设备）：单文件 JPEG，视频附加在图像后，XMP 含 `GCamera:MotionPhoto*` + `Container:Directory`
- **Apple Live Photo**（JPG + 同名 MOV 双文件）：JPG 的 XMP `apple-fi:ContentIdentifier` 与 MOV 的 QuickTime metadata `com.apple.quicktime.content.identifier` 配对同一 UUID
- **OPPO 动态照片**（单文件）：Google 标准 + `OpCamera:*` 私有标签 + moov 内 `lpex`（LivePhotoExtension）box + 文件尾 `cameralbum!` footer
- **vivo 动态照片**（JPG + 同名 MP4 双文件）：JPG 与 MP4 内嵌相同的 28 字符 livephoto ID 与 `cameralbum!` footer，MP4 尾部为 `uuid` box（UUID = `vivoMediaExtInfo`）
- **小米动态照片**（单文件）：Google 标准 + MicroVideo 旧版标签双标签并存 + EXIF `0x8897`=1（小米相册识别依据）

五种格式均完整保留 Ultra HDR GainMap（`hdrgm` / MPF），转换不重编码、不重压缩，视频流与图像数据字节级透传。

## 功能特性

| 功能 | 说明 | 依赖 |
|------|------|------|
| 格式自动识别 | Google / Apple / OPPO / vivo / 小米（含 Google 旧版 MicroVideo），置信度评分 | 无 |
| 任意格式互转 | 二十种转换方向全覆盖；同格式转换 = 原样复制 | 无 |
| 队列批量处理 | 多文件/整目录扫描，后台线程顺序转换，可随时停止 | 无 |
| 无损透传 | 图像/GainMap/视频流字节级保留，仅重写 XMP 与厂商标记 | 无 |
| OPPO lpex 合成 | 目标为 OPPO 且源视频无 lpex 时自动合成并修复 chunk 偏移 | 无 |
| 配置记忆 | 记忆窗口位置、目标格式、输出目录（可一键清除） | 无 |
| CLI 模式 | 命令行批处理，便于脚本集成 | 无 |

## 目录结构

```
Source/
├── main.py                 # 程序入口（GUI + CLI）
├── core/
│   ├── model.py            # LivePhotoAsset 格式无关数据模型
│   ├── jpegutil.py         # JPEG 段扫描/EOI 定位/XMP 段替换
│   ├── mp4util.py          # MP4 box 遍历/lpex 插入/stco-co64 修复/轨道信息/MOV 转换
│   ├── exifutil.py         # EXIF TIFF/IFD 解析与标签写入（小米 0x8897）
│   ├── footer.py           # cameralbum! footer 编解码（vivo/OPPO 共用）
│   ├── xmptmpl.py          # XMP 模板（提取自真实样本）与字段解析
│   ├── formats/
│   │   ├── base.py         # FormatPlugin 抽象基类（detect/read/write）
│   │   ├── google.py       # Google Motion Photo
│   │   ├── oppo.py         # OPPO 单文件
│   │   ├── vivo.py         # vivo 双文件
│   │   ├── xiaomi.py       # 小米单文件（双标签 + EXIF 0x8897）
│   │   ├── apple.py        # Apple Live Photo 双文件（ContentIdentifier 配对）
│   │   └── __init__.py     # 插件注册表（新格式在此注册）
│   ├── convert.py          # 转换管线
│   └── queue_runner.py     # 队列执行器
└── README.md
```

**扩展新格式**（如荣耀 / 华为）：在 `core/formats/` 下新建插件文件实现
`detect()` / `read()` / `write()` 三个接口，并在 `formats/__init__.py` 的
`PLUGINS` 中注册即可，无需改动其他模块。

### 从源码恢复开发环境

前提：Python 3.10+（<https://www.python.org/>），仅需标准库，无第三方依赖。

```bat
git clone <仓库地址>
cd Source
python main.py
```

## 运行方式

```bat
:: GUI 模式
python main.py

:: GUI 调试模式（显示控制台）
python main.py --console

:: CLI 模式
python main.py --cli --to google --out 输出目录 文件1.jpg 文件2.jpg ...
```

- `--to`：`google` / `apple` / `oppo` / `vivo` / `xiaomi`
- `--out`：输出目录（省略时为源文件所在目录的 `converted` 子目录）

Google 输出始终遵循 `*MP.jpg` 命名约定（部分相册按文件名识别动态照片）。

双文件格式说明：
- **vivo**：输入选择 JPG 文件即可，程序自动配对同目录同名 MP4；转换为 vivo 时会同时输出 `<名称>.jpg` 与 `<名称>.mp4`
- **Apple**：输入选择 JPG 文件即可，程序自动配对同目录同名 MOV；转换为 Apple 时会同时输出 `<名称>.jpg` 与 `<名称>.mov`，两端写入同一 ContentIdentifier UUID 完成配对

## 配置位置

用户设置（窗口位置、目标格式、输出目录、命名选项）保存在
`%USERPROFILE%\.zAPP\zlivephoto_config.json`，界面内提供"清除记忆"按钮一键删除。

## 开源引用

| 项目 | 用途 | 链接 |
|------|------|------|
| Android Motion Photo 规范 | Google 标准格式依据 | <https://developer.android.com/media/platform/motion-photo-format> |
| ExifTool XMP 文档 | GCamera 标签定义参考 | <https://exiftool.org/TagNames/Google.html> |
| AppleLivePhotoConvert | 小米 EXIF 0x8897 与 Apple ContentIdentifier 配对机制参考 | <https://github.com/ZhiQiu-Kinsey/AppleLivePhotoConvert> |
