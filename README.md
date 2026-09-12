# Z-LivePhoto-Converter

> 动态照片格式互转工具：Google Motion Photo / OPPO / vivo / 小米 / 荣耀 / 魅族 / Apple Live Photo 互转、拆解与合成
**开发者**: zsz & Kimi-K3  
**版本**: v3.4.4

## 许可证

本程序基于 **GNU General Public License v3.0 (GPLv3)** 开源协议发布。

Copyright (C) 2026 zsz & Kimi-K3

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

本程序分发时附带希望其有用的保证，但不提供任何担保；甚至不提供适销性或特定用途适用性的默示担保。详见 [LICENSE](LICENSE)。

---

## 系统要求

> ⚠️ **维护声明**：自 v3.0.0 起停止维护与发布 **Windows 桌面版**，此后仅发布 Android 版本。
> Windows 版（C# / WinUI 3，上一完整版本 v2.3.0）的完整源码保留在 **`v2.3.0` 分支**，`main` 分支已不再包含 Windows 端代码。

| 版本 | 最低系统版本 | 架构 |
|------|-------------|------|
| ZLC（完整版） | Android 10 (API 29) | arm64-v8a |
| ZLC Go（轻量版） | Android 6 (API 23) | arm64-v8a / armeabi-v7a / x86 / x86_64 |

Android 端无需额外运行时。完整版内置 ffmpeg 转码器，仅提供 arm64-v8a（当前绝大多数机型）；Go 轻量版不含转码器，因此仍覆盖更多架构。Go 版在 Android 10 及以上已不再适配，需改用完整版。

## 下载安装

前往 [Releases](https://github.com/zsz-of/ZLC/releases) 下载最新版本，更新内容见 [CHANGELOG](CHANGELOG.md)。

| 平台 | 安装包 | 格式 |
|------|--------|------|
| Android（完整版） | ZLC.apk | APK |
| Android（轻量版） | ZLC Go.apk | APK |

### Android 安装步骤
1. 下载 `.apk` 文件
2. 允许「来自未知来源」安装
3. 点击 APK 完成安装

> 完整版与轻量版包名不同，可同时安装在一台设备上。

## 项目简介

本程序实现了主流动态照片格式之间的字节级无损互转，并支持动态照片的拆解与合成：

- **Google Motion Photo**：JPEG(+GainMap) + MP4 裸拼，XMP 标记 Container Directory
- **OPPO**：Google 格式 + XMP 私有标签（OpCamera/OLivePhoto/VCamera）+ moov 内 lpex box
- **vivo**：双文件（JPG + MP4）与单文件，footer JSON 关联 ID
- **小米**：双 XMP 标签（MotionPhoto + MicroVideo）+ EXIF 0x8897
- **荣耀**：JPEG(+GainMap) + MP4(large size) + uuid box(extend_type_matrix + EIS JSON) + 60B tail(LIVE_ID)
- **魅族**：MZCamera 动态照片（识别 / 读取 / 转出）
- **Apple Live Photo**：双文件（JPG + MOV），ContentIdentifier UUID 配对（输出至 Apple 格式暂不可用，详见「已知限制」）

此外支持「拆解」输出（导出照片 + 视频双文件），以及「合成」（封面照片 + ≤3 秒视频生成动态照片）。

核心解析纯字节级操作（JPEG/MP4 box 遍历），转换后保留源文件的 EXIF 元数据（GPS、拍摄时间等）和修改时间。视频转码为完整版内置功能（ffmpeg 随安装包内置，无需下载），Go 轻量版不含转码器。

> **已知限制**：
> - Apple 动态照片**转换为其他格式存在兼容性 bug**：产物在部分机型（如 vivo 相册）中可被识别为动态照片、封面与 EXIF（位置/拍摄时间/机型等）均正常，但无法长按播放、无法编辑（提示图片已破损），修复排期中。
> - **合成时若输入视频不是标准 MP4 容器**（如 WebM/MKV/AV1），完整版会用内置 ffmpeg 自动转码为标准 MP4（H.265/H.264）；Go 轻量版不含转码器，请改用完整版或先用其它工具转码为标准 MP4 素材。
> - Apple Live Photo 输出格式暂不可用（v2.3.0 起输出选项置灰，识别为 Apple 的照片标记「暂不支持转换」）。

### 技术栈

| 语言 | 框架 |
|------|------|
| Kotlin | Jetpack Compose / Material 3 |
| Kotlin | 纯 stdlib 字节级解析（JPEG 段 / MP4 box） |

### 入口文件

| 入口 |
|------|
| `ZLivePhoto.Android/app/src/main/java/com/zsz/zlivephoto/MainActivity.kt` |

## 功能特性

| 功能 | 说明 |
|------|------|
| 多格式互转 | Google / OPPO / vivo / 小米 / 荣耀 / 魅族 / Apple 之间互转（Apple 输出暂不可用） |
| 合成动态照片 | 封面照片 + ≤3 秒视频合成动态照片；非 JPEG 封面与非标准 MP4 视频自动转码 |
| 拆解导出 | 将动态照片拆解为「照片 + 视频」双文件 |
| 字节级无损 | 纯字节解析重组，不重新编码 |
| EXIF 保留 | 保留 GPS、拍摄时间等所有 EXIF 元数据 |
| 时间戳保留 | 输出文件保留源文件的修改时间 |
| 同格式直通 | 源与目标格式相同时原样复制（零损耗） |
| 自动配对 | vivo/Apple 双文件自动查找同目录视频 |
| 双版本 | ZLC 完整版（Android 10+，内置 ffmpeg 转码器）与 ZLC Go 轻量版（Android 6+，适配老设备，不含转码器） |
| Material You | Android 12+ 动态取色跟随壁纸；7 种预制主题色 + 自定义色相 |
| 动态桌面图标 | 应用图标随主题色 / 壁纸色自动切换 |
| 检查更新 | 启动/手动检查新版本；GitHub/蓝奏云双通道**应用内直接下载安装**（蓝奏压缩包自动解出 APK），支持「跳过此版本」（Go 版匹配 Go 安装包） |
| 弹窗排队 | 权限请求 / 应用更新 / 所有文件访问引导等会话型弹窗统一按优先级排队，同一时刻只弹一个，处理进行中自动延后 |
| 批量导入 | Android 支持文件夹批量扫描导入（含子目录），流式处理不卡顿 |
| 相册排序与搜索 | 内置选择器按大小 / 日期 / 名称排序（正序 / 倒序）并支持按名称搜索 |
| 内置视频转码 | ffmpeg 随完整版安装包内置（`libffmpeg.so`），非标准 MP4 自动转标准 MP4（H.265/H.264），设置页可调 CRF / 编码器 / 预设 |
| 高刷新率 | 完整版启动时自动请求设备支持的最高刷新率（120 / 144 / 185Hz 等），动画跑满屏幕帧率上限 |
| 列表持久化 | Android 列表自动落盘本地 JSON，异常退出可检测并恢复 |
| 系统分享 | Android 支持从系统分享接收照片转换 |

## 目录结构

```
Source/
├── ZLivePhoto.Android/           # Android 应用（Gradle / Kotlin）
│   ├── app/src/main/java/com/zsz/zlivephoto/
│   │   ├── MainActivity.kt       #   主 Activity
│   │   ├── core/                 #   核心转换库（Kotlin，纯字节级解析）
│   │   └── ui/                   #   Compose UI
│   └── app/src/main/res/         #   资源文件
├── CHANGELOG.md                  # 更新日志
├── LICENSE                       # GPLv3
└── .gitignore
```

> Windows 桌面版（C# / WinUI 3）与命令行工具的源码不在 `main` 分支，见 `v2.3.0` 分支。

### 从源码恢复开发环境

1. 安装 [Android SDK](https://developer.android.com/studio)（Command-line tools 即可）
2. 配置 `local.properties` 指向 SDK 路径
3. 完整版的内置转码器为预编译 ffmpeg 二进制（未入库，来自 [vidra-ffmpeg](https://github.com/chomusuke-mk/vidra-ffmpeg)），需放入 `app/src/normal/jniLibs/arm64-v8a/libffmpeg.so`；不放置时完整版编译通过但不含转码能力

```bash
cd Source/ZLivePhoto.Android
./gradlew assembleDebug
```

## 运行方式

- 启动应用 → 添加动态照片 → 选择输出格式 → 点击转换
- 或从系统相册分享照片到本应用

## 开源引用

| 项目 | 用途 |
|------|------|
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | Android UI 框架 |
| [Material 3](https://m3.material.io/) | 设计系统 |
| [FFmpeg](https://ffmpeg.org/) | 视频转码（完整版内置，预编译自 [vidra-ffmpeg](https://github.com/chomusuke-mk/vidra-ffmpeg)） |
