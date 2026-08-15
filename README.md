# Z-LivePhoto-Converter

> 动态照片格式互转工具：Google Motion Photo / OPPO / vivo / 小米 / Apple Live Photo 字节级无损转换

**开发者**: zsz & Kimi-K3  
**版本**: v2.0.0

---

## 系统要求

| 平台 | 最低系统版本 | 架构 |
|------|-------------|------|
| Windows | Windows 10 1809 (17763) 64-Bit | x64 |
| Android | Android 10 (API 29) | arm64-v8a / x86_64 |

Windows 端无需额外安装运行时（自包含部署）。Android 端无需额外运行时。

## 下载安装

前往 [Releases](https://github.com/zsz-of/Z-LivePhoto-Converter/releases) 下载最新版本。

| 平台 | 安装包 | 格式 |
|------|--------|------|
| Windows | Z-LivePhoto-Converter_Installer.exe | Inno Setup |
| Windows | Z-LivePhoto-Converter_Installer.msi | Windows Installer |
| Android | Z-LivePhoto-Converter.apk | APK |

### Windows 安装步骤
1. 下载 `.msi` 或 `.exe` 安装包
2. 双击运行，按向导完成安装
3. 从开始菜单启动「Z-LivePhoto-Converter」

### Android 安装步骤
1. 下载 `.apk` 文件
2. 允许「来自未知来源」安装
3. 点击 APK 完成安装

## 项目简介

本程序实现了五种主流动态照片格式之间的字节级无损互转：

- **Google Motion Photo**：JPEG(+GainMap) + MP4 裸拼，XMP 标记 Container Directory
- **OPPO**：Google 格式 + XMP 私有标签（OpCamera/OLivePhoto/VCamera）+ moov 内 lpex box
- **vivo**：双文件（JPG + MP4），footer JSON 关联 ID
- **小米**：双 XMP 标签（MotionPhoto + MicroVideo）+ EXIF 0x8897
- **Apple Live Photo**：双文件（JPG + MOV），ContentIdentifier UUID 配对

核心解析纯字节级操作（JPEG/MP4 box 遍历），不依赖 FFmpeg 或外部工具。转换后保留源文件的 EXIF 元数据（GPS、拍摄时间等）和修改时间。

### 技术栈

| 平台 | 语言 | 框架 |
|------|------|------|
| Windows | C# 13 / .NET 10 | WinUI 3 / Material 3 |
| Android | Kotlin | Jetpack Compose / Material 3 |
| 核心 | C# / Kotlin | 纯 stdlib 字节级解析 |

### 入口文件

| 平台 | 入口 |
|------|------|
| Windows | `ZLivePhoto.WinUI/MainWindow.xaml.cs` |
| Android | `app/src/main/java/com/zsz/zlivephoto/MainActivity.kt` |
| CLI | `ZLivePhoto.Cli/Program.cs` |

## 功能特性

| 功能 | 说明 |
|------|------|
| 五格式互转 | Google / OPPO / vivo / 小米 / Apple 之间任意互转 |
| 字节级无损 | 纯字节解析重组，不重新编码 |
| EXIF 保留 | 保留 GPS、拍摄时间等所有 EXIF 元数据 |
| 时间戳保留 | 输出文件保留源文件的修改时间 |
| 同格式直通 | 源与目标格式相同时原样复制（零损耗） |
| 自动配对 | vivo/Apple 双文件自动查找同目录视频 |
| 批量转换 | 支持批量添加、批量转换 |
| 系统分享 | Android 支持从系统分享接收照片转换 |

## 目录结构

```
Source/
├── ZLivePhoto.Core/              # 核心转换库（C#）
│   ├── Formats/                  #   格式插件（Google/OPPO/vivo/小米/Apple）
│   ├── Models/                   #   数据模型
│   ├── Converter.cs              #   转换管线
│   ├── JpegUtil.cs               #   JPEG 段解析
│   ├── Mp4Util.cs                #   MP4 box 解析
│   ├── XmpTemplate.cs            #   XMP 模板
│   ├── FooterUtil.cs             #   cameralbum! footer
│   └── ExifUtil.cs               #   EXIF 处理
├── ZLivePhoto.WinUI/             # Windows 桌面应用
│   ├── MainWindow.xaml           #   主窗口 UI
│   ├── MainWindow.xaml.cs        #   主窗口逻辑
│   └── AppConfig.cs              #   配置管理
├── ZLivePhoto.Cli/               # 命令行工具
│   └── Program.cs                #   CLI 入口
├── ZLivePhoto.Android/           # Android 应用
│   ├── app/src/main/java/com/zsz/zlivephoto/
│   │   ├── MainActivity.kt       #   主 Activity
│   │   ├── core/                 #   核心转换库（Kotlin 移植）
│   │   └── ui/                   #   Compose UI
│   └── app/src/main/res/         #   资源文件
├── Directory.Build.props         # .NET 构建配置
└── .gitignore
```

### 从源码恢复开发环境

#### Windows 端
1. 安装 [.NET 10 SDK](https://dotnet.microsoft.com/download)
2. 安装 [WiX Toolset 5](https://wixtoolset.org/)（可选，用于 MSI 打包）
3. 安装 [Inno Setup 6](https://jrsoftware.org/isdl.php)（可选，用于 EXE 打包）

```bash
cd Source
dotnet build ZLivePhoto.WinUI/ZLivePhoto.WinUI.csproj -c Debug
dotnet run --project ZLivePhoto.WinUI/ZLivePhoto.WinUI.csproj
```

#### Android 端
1. 安装 [Android SDK](https://developer.android.com/studio)（Command-line tools 即可）
2. 配置 `local.properties` 指向 SDK 路径

```bash
cd Source/ZLivePhoto.Android
./gradlew assembleDebug
```

## 运行方式

### Windows
- **GUI**：从开始菜单启动，或运行 `Z-LivePhoto-Converter.exe`
- **CLI**：`zlpc --cli --to {google|oppo|vivo|xiaomi|apple} <文件路径>`

### Android
- 启动应用 → 添加动态照片 → 选择输出格式 → 点击转换
- 或从系统相册分享照片到本应用

## 开源引用

| 项目 | 用途 |
|------|------|
| [WinUI 3](https://github.com/microsoft/microsoft-ui-xaml) | Windows UI 框架 |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | Android UI 框架 |
| [Material 3](https://m3.material.io/) | 设计系统 |
