# Android APK 命令行构建脚本（无 Android Studio）
# 用法：在仓库根（Source/）下执行  .\build-scripts\build-android.ps1
# 前置：.NET 10 SDK + android 工作负载；Android SDK 位于 D:\Android\Sdk（或设置 ANDROID_HOME）
# 签名：Release 使用 ..\Installer\Android\signing.props（本地私有，不存在则回退调试签名）

$ErrorActionPreference = 'Stop'
$proj = Join-Path $PSScriptRoot '..\ZLivePhoto.Android\ZLivePhoto.Android.csproj'

dotnet build $proj -c Release
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$apk = Join-Path $PSScriptRoot '..\ZLivePhoto.Android\bin\Release\net10.0-android\com.zsz.zlivephoto-Signed.apk'
$dst = Join-Path $PSScriptRoot '..\..\Application\Android'
New-Item -ItemType Directory -Force -Path $dst | Out-Null
Copy-Item $apk (Join-Path $dst 'Z-LivePhoto-Converter.apk') -Force
Write-Host "APK 输出：$dst\Z-LivePhoto-Converter.apk"
