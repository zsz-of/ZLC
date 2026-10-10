# Z-LivePhoto-Converter

[English](#english) · [日本語](#日本語) · [한국어](#한국어) · [简体中文](#简体中文) · [繁體中文](#繁體中文) · [Русский](#русский) · [Français](#français) · [Latina](#latina)

**Developer / 开发者**: zsz · **Version / 版本**: v3.6.2 · **License**: [GPL-3.0-or-later](LICENSE)

---

## English

> Live-photo format converter: convert, split and merge between Google Motion Photo / OPPO / vivo / Xiaomi / Honor / Meizu / Nubia / Apple Live Photo.

Android app (Kotlin + Jetpack Compose) that performs **byte-level, lossless** conversion between the mainstream live-photo formats. The app UI is available in **121 languages** (120 resource directories fully translated, 404/404 keys) and supports RTL.

### Supported formats

| Format | Structure |
|--------|-----------|
| Google Motion Photo | JPEG(+GainMap) + MP4, XMP Container Directory |
| OPPO | Google layout + private XMP tags (OpCamera/OLivePhoto/VCamera) + `lpex` box in `moov` |
| vivo | Two files (JPG + MP4) or single file with footer JSON link ID |
| Xiaomi | Two XMP tags (MotionPhoto + MicroVideo) + EXIF 0x8897 |
| Honor | JPEG(+GainMap) + MP4 (large size) + uuid box (extend_type_matrix + EIS JSON) + 60-byte tail (LIVE_ID) |
| Meizu | MZCamera live photo (detect / read / export) |
| Apple Live Photo | Two files (JPG/MOV or HEIC/MOV) paired by ContentIdentifier UUID; bidirectional `ftyp` brand cleaning, byte-level removal of non-A/V tracks |

Split export (photo + video as two files) and merge (JPEG cover + up to 3 s video) are both supported. All parsing is pure byte-level (JPEG segments / MP4 boxes); EXIF metadata (GPS, capture time) and the file modification time are preserved.

### Editions and system requirements

| Edition | Minimum OS | ABI |
|---------|-----------|-----|
| ZLC (full) | Android 10 (API 29) | arm64-v8a |
| ZLC Go (light) | Android 6 (API 23) | arm64-v8a / armeabi-v7a / x86 / x86_64 |

The full edition bundles the ffmpeg transcoder (`libffmpeg.so`, arm64-v8a only). The Go edition ships without it and therefore still covers more ABI; it is no longer adapted for Android 10+, where the full edition should be used. Both editions have different package names (`com.zsz.zlivephoto` / `com.zsz.zlivephoto.go`) and can be installed side by side.

### Download

Get the latest build from [Releases](https://github.com/zsz-of/ZLC/releases) — `ZLC_<version>.apk` (full) and `ZLC_Go_<version>.apk` (Go) — with Lanzou cloud mirrors listed in the release notes. Changes are documented in [CHANGELOG](CHANGELOG.md). Install steps: download the APK, allow "install from unknown sources", tap the file.

### Features

- **Multi-format conversion** between Google / OPPO / vivo / Xiaomi / Honor / Meizu / Apple
- **Merge**: JPEG cover + up to 3 s video; non-JPEG covers and non-standard MP4 videos are transcoded automatically
- **Split export**: one live photo → photo + video
- **Byte-level lossless** repacking, no re-encoding
- **EXIF and timestamp preservation** (GPS, capture time, modification time)
- **Album-hierarchy output**: results are grouped in sub-folders named after the source album (`Pictures/Z-LivePhoto-Converter/Camera/`, `.../微信/`), can be disabled in Settings → Output
- **Same-format passthrough** (straight copy, zero loss)
- **Delete originals after processing** (requires "All files access"; Android 10 and below unsupported)
- **Replace mode** (toolbox): write results back over the source files, with a one-time risk notice
- **Automatic pairing** of vivo/Apple companion files
- **Two editions**: ZLC (Android 10+, built-in transcoder) and ZLC Go (Android 6+, no transcoder)
- **Material You**: dynamic color on Android 12+, 7 preset themes + custom hue
- **121-language UI** with per-app language switching and RTL support
- **Update checker**: in-app download from GitHub or Lanzou (including password-protected shares), "skip this version" (Go edition matches the Go package)
- **Dialog queueing**: permission / update / all-files-access dialogs are serialized by priority
- **Batch import** with folder scanning (including sub-directories) and streaming processing
- **Sort & search** in the built-in picker (by date / size / name, ascending or descending, plus name search)
- **Built-in transcoding**: encoder, CRF and preset are configurable; three policies — re-encode (best compatibility), remux container only (default, fast and lossless), or never use the transcoder
- **Transcode progress** per list item (processed / total frames, estimated remaining time)
- **High refresh rate**: requests the highest supported display mode at launch
- **List persistence**: the pending list is stored as local JSON and can be restored after a crash
- **System share target**: receive photos shared from other apps
- **Fully localized UI text**: the interface is available in 120 languages (follows the system or the in-app language setting)

### Build from source

1. Install the [Android SDK](https://developer.android.com/studio) (command-line tools are enough) and point `local.properties` to it.
2. For the full edition, place the prebuilt `libffmpeg.so` (from [vidra-ffmpeg](https://github.com/chomusuke-mk/vidra-ffmpeg)) at `app/src/normal/jniLibs/arm64-v8a/libffmpeg.so`; without it the full edition still builds, but without transcoding.
3. `cd Source/ZLivePhoto.Android && ./gradlew assembleDebug` (release builds need `signing.properties`).

Repository layout: `Source/ZLivePhoto.Android/` (Android app), `.github/workflows/android-release.yml` (tag → signed APKs + GitHub Release), `CHANGELOG.md`, `LICENSE`. The retired Windows desktop version (C# / WinUI 3) lives in the `v2.3.0` branch; every released version keeps its code in a `v<version>` branch.

### Known limitations

- Apple live photos converted to other formats had compatibility bugs (recognised but not playable/editable on some devices). Two causes — leftover QuickTime `mett`/`tmcd` tracks and residual `compatible_brands` — are deterministically fixed at the byte level **since v3.4.15**, together with HEIC covers and a "remux alone cannot fix this" detector (10-bit/HDR/Dolby Vision/non-AAC trigger a warning or automatic re-encode). On-device playback/edit improvement still awaits real-device confirmation.
- Videos that are not standard MP4 (MOV / WebM / MKV / non H.264·H.265) are handled by Settings → Video transcoding → policy (re-encode / remux only / never transcode).

### Credits

[Jetpack Compose](https://developer.android.com/jetpack/compose) · [Material 3](https://m3.material.io/) · [FFmpeg](https://ffmpeg.org/) (prebuilt by [vidra-ffmpeg](https://github.com/chomusuke-mk/vidra-ffmpeg))

### License

GNU General Public License v3.0 or later (GPL-3.0-or-later). Copyright (C) 2026 zsz. This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; see [LICENSE](LICENSE).

---

## 日本語

> ライブフォト形式の相互変換ツール：Google Motion Photo / OPPO / vivo / Xiaomi / Honor / Meizu / Nubia / Apple Live Photo の相互変換・分解・合成。

Kotlin + Jetpack Compose で作られた Android アプリで、主要なライブフォト形式を**バイト単位で無劣化**変換します。UI は **121 言語**（120 のリソースディレクトリを完全翻訳、404/404 キー）に対応し、RTL にも対応しています。

### 対応フォーマット

| 形式 | 構造 |
|------|------|
| Google Motion Photo | JPEG(+GainMap) + MP4、XMP Container Directory |
| OPPO | Google 形式 + 独自 XMP タグ（OpCamera/OLivePhoto/VCamera）+ `moov` 内 `lpex` box |
| vivo | 2 ファイル（JPG + MP4）または footer JSON で関連付けた単一ファイル |
| Xiaomi | 2 つの XMP タグ（MotionPhoto + MicroVideo）+ EXIF 0x8897 |
| Honor | JPEG(+GainMap) + MP4（large size）+ uuid box（extend_type_matrix + EIS JSON）+ 60 バイト末尾（LIVE_ID） |
| Meizu | MZCamera ライブフォト（検出 / 読み取り / 書き出し） |
| Apple Live Photo | 2 ファイル（JPG/MOV または HEIC/MOV）、ContentIdentifier UUID でペアリング。`ftyp` ブランドの双方向クリーニング、非 A/V トラックのバイト単位削除 |

「分解」（写真 + 動画の 2 ファイル出力）と「合成」（JPEG カバー + 3 秒以内の動画）に対応。解析はすべてバイトレベル（JPEG セグメント / MP4 box）で、EXIF（GPS・撮影日時）とファイル更新日時を保持します。

### エディションと動作環境

| エディション | 最低 OS | ABI |
|--------------|---------|-----|
| ZLC（フル版） | Android 10 (API 29) | arm64-v8a |
| ZLC Go（軽量版） | Android 6 (API 23) | arm64-v8a / armeabi-v7a / x86 / x86_64 |

フル版は ffmpeg トランスコーダ（`libffmpeg.so`、arm64-v8a のみ）を内蔵し、Go 版は非搭載のためより多くの ABI をカバーします。Go 版は Android 10 以上には対応していないため、フル版をご利用ください。両版はパッケージ名が異なる（`com.zsz.zlivephoto` / `com.zsz.zlivephoto.go`）ため同時インストールできます。

### ダウンロード

最新版は [Releases](https://github.com/zsz-of/ZLC/releases) から（`ZLC_<バージョン>.apk` = フル版、`ZLC_Go_<バージョン>.apk` = Go 版）。Lanzou クラウドのミラーはリリースノートに記載しています。変更履歴は [CHANGELOG](CHANGELOG.md)。APK をダウンロードし、「提供元不明のアプリ」を許可してインストールします。

### 主な機能

- **多形式相互変換**：Google / OPPO / vivo / Xiaomi / Honor / Meizu / Apple
- **合成**：JPEG カバー + 3 秒以内の動画。非 JPEG カバーや非標準 MP4 は自動でトランスコード
- **分解出力**：ライブフォト → 写真 + 動画
- **バイト単位の無劣化**：再エンコードなし
- **EXIF・タイムスタンプ保持**（GPS、撮影日時、更新日時）
- **アルバム階層出力**：元アルバム名のサブフォルダに保存（設定 → 出力で無効化可能）
- **同一形式はそのままコピー**（ゼロ損失）
- **処理後に元ファイルを削除**（「すべてのファイルへのアクセス」が必要、Android 10 以下は非対応）
- **置換モード**（ツールボックス）：変換結果を元ファイルへ直接書き戻し（初回のみリスク警告）
- **自動ペアリング**（vivo / Apple の 2 ファイル）
- **2 つのエディション**：ZLC（Android 10+、トランスコーダ内蔵）と ZLC Go（Android 6+、非搭載）
- **Material You**：Android 12+ の動的配色、プリセット 7 色 + カスタム色相
- **121 言語対応**の UI（アプリ単位の言語切替、RTL 対応）
- **アップデート確認**：GitHub / Lanzou からアプリ内ダウンロード（パスワード付き共有にも対応）、「このバージョンをスキップ」
- **ダイアログのキュー管理**：権限・更新・アクセス許可のダイアログを優先度順に 1 つずつ表示
- **一括インポート**：フォルダ走査（サブフォルダ含む）とストリーミング処理
- **並べ替えと検索**：日付 / サイズ / 名前（昇順・降順）と名前検索（v3.6.1 で検索バー展開時の位置ずれを修正）
- **トランスコード内蔵**：エンコーダ / CRF / プリセットを設定可能。方式は再エンコード / コンテナ再ラップのみ（既定）/ トランスコーダ未使用の 3 択
- **進捗表示**：処理済み / 総フレーム数と残り時間の推定
- **高リフレッシュレート**：起動時に端末の最大リフレッシュレートを要求
- **リスト永続化**：待機リストをローカル JSON に保存し、異常終了後も復元
- **共有受け取り**：他アプリからの共有写真を変換
- **UI テキストの全言語対応**：アプリの UI テキストは 120 言語に対応（システムまたはアプリ内の言語設定に従います）

### ソースからのビルド

1. [Android SDK](https://developer.android.com/studio)（コマンドラインツールで可）を導入し、`local.properties` を設定します。
2. フル版は事前ビルド済み `libffmpeg.so`（[vidra-ffmpeg](https://github.com/chomusuke-mk/vidra-ffmpeg) 由来）を `app/src/normal/jniLibs/arm64-v8a/libffmpeg.so` に配置します（未配置でもビルドは可能ですがトランスコード機能はありません）。
3. `cd Source/ZLivePhoto.Android && ./gradlew assembleDebug`（リリースビルドには `signing.properties` が必要）。

構成：`Source/ZLivePhoto.Android/`（Android アプリ）、`.github/workflows/android-release.yml`（タグ → 署名済み APK と GitHub Release）、`CHANGELOG.md`、`LICENSE`。廃止した Windows デスクトップ版（C# / WinUI 3）は `v2.3.0` ブランチに、各リリースのコードは `v<バージョン>` ブランチに保存されています。

### 既知の制限

- Apple のライブフォトを他形式へ変換すると、一部端末で認識されても再生・編集できない互換性問題がありました。原因（QuickTime の `mett`/`tmcd` トラック残留と `compatible_brands` の残留）は **v3.4.15 以降**バイトレベルで確定的に修正済みで、HEIC カバーと「再ラップでは解決不能」の検出も追加されています。実機での再生・編集の改善は実機確認待ちです。
- 標準 MP4 でない動画（MOV / WebM / MKV / H.264・H.265 以外）は「設定 → 動画トランスコード → 方式」で決まります。

### ライセンス

GNU General Public License v3.0 以降（GPL-3.0-or-later）。Copyright (C) 2026 zsz. 本プログラムは役立つことを願って配布されますが、いかなる保証もありません。詳細は [LICENSE](LICENSE) を参照してください。

---

## 한국어

> 라이브 포토 형식 변환 도구: Google Motion Photo / OPPO / vivo / Xiaomi / Honor / Meizu / Nubia / Apple Live Photo 간 상호 변환·분해·합성.

Kotlin + Jetpack Compose로 만든 Android 앱으로, 주요 라이브 포토 형식을 **바이트 단위 무손실**로 변환합니다. UI는 **121개 언어**(리소스 디렉터리 120개 100% 번역, 404/404 키)를 지원하며 RTL도 지원합니다.

### 지원 형식

| 형식 | 구조 |
|------|------|
| Google Motion Photo | JPEG(+GainMap) + MP4, XMP Container Directory |
| OPPO | Google 형식 + 전용 XMP 태그(OpCamera/OLivePhoto/VCamera) + `moov` 내 `lpex` box |
| vivo | 두 파일(JPG + MP4) 또는 footer JSON으로 연결한 단일 파일 |
| Xiaomi | 두 개의 XMP 태그(MotionPhoto + MicroVideo) + EXIF 0x8897 |
| Honor | JPEG(+GainMap) + MP4(large size) + uuid box(extend_type_matrix + EIS JSON) + 60바이트 꼬리(LIVE_ID) |
| Meizu | MZCamera 라이브 포토(감지 / 읽기 / 내보내기) |
| Apple Live Photo | 두 파일(JPG/MOV 또는 HEIC/MOV), ContentIdentifier UUID로 페어링. `ftyp` 브랜드 양방향 정리, 비 A/V 트랙 바이트 단위 제거 |

'분해'(사진 + 동영상 두 파일)와 '합성'(JPEG 커버 + 3초 이내 동영상)을 지원합니다. 모든 파싱은 바이트 수준(JPEG 세그먼트 / MP4 box)이며 EXIF(GPS·촬영 시각)와 파일 수정 시각을 보존합니다.

### 에디션 및 시스템 요구 사항

| 에디션 | 최소 OS | ABI |
|--------|---------|-----|
| ZLC(전체판) | Android 10 (API 29) | arm64-v8a |
| ZLC Go(경량판) | Android 6 (API 23) | arm64-v8a / armeabi-v7a / x86 / x86_64 |

전체판은 ffmpeg 트랜스코더(`libffmpeg.so`, arm64-v8a 전용)를 내장하고, Go판은 미탑재이므로 더 많은 ABI를 지원합니다. Go판은 Android 10 이상에 최적화되어 있지 않으므로 전체판을 사용하세요. 두 판은 패키지 이름이 달라(`com.zsz.zlivephoto` / `com.zsz.zlivephoto.go`) 동시에 설치할 수 있습니다.

### 다운로드

최신 버전은 [Releases](https://github.com/zsz-of/ZLC/releases)에서 받으세요(`ZLC_<버전>.apk` = 전체판, `ZLC_Go_<버전>.apk` = Go판). Lanzou 클라우드 미러는 릴리스 노트에 있습니다. 변경 내역은 [CHANGELOG](CHANGELOG.md). APK를 내려받아 '알 수 없는 출처' 설치를 허용한 뒤 설치합니다.

### 주요 기능

- **다중 형식 상호 변환**: Google / OPPO / vivo / Xiaomi / Honor / Meizu / Apple
- **합성**: JPEG 커버 + 3초 이내 동영상, 비 JPEG 커버와 비표준 MP4는 자동 트랜스코딩
- **분해 내보내기**: 라이브 포토 → 사진 + 동영상
- **바이트 단위 무손실** 재포장(재인코딩 없음)
- **EXIF·타임스탬프 보존**(GPS, 촬영 시각, 수정 시각)
- **앨범 계층 출력**: 원본 앨범 이름의 하위 폴더에 저장(설정 → 출력에서 해제 가능)
- **동일 형식 그대로 복사**(무손실)
- **처리 후 원본 삭제**('모든 파일 액세스' 필요, Android 10 이하 미지원)
- **교체 모드**(도구함): 변환 결과를 원본 파일에 직접 덮어쓰기(최초 1회 위험 안내)
- **자동 페어링**(vivo / Apple 두 파일)
- **두 가지 에디션**: ZLC(Android 10+, 트랜스코더 내장) / ZLC Go(Android 6+, 미탑재)
- **Material You**: Android 12+ 동적 색상, 프리셋 7종 + 사용자 색조
- **121개 언어 UI**(앱별 언어 전환, RTL 지원)
- **업데이트 확인**: GitHub / Lanzou에서 앱 내 다운로드(암호가 있는 공유 지원), '이 버전 건너뛰기'
- **대화상자 큐잉**: 권한·업데이트·파일 접근 안내를 우선순위대로 하나씩 표시
- **일괄 가져오기**: 폴더 검색(하위 폴더 포함)과 스트리밍 처리
- **정렬·검색**: 날짜 / 크기 / 이름(오름·내림차순)과 이름 검색(v3.6.1에서 검색창 확장 시 위치 흔들림 수정)
- **내장 트랜스코딩**: 인코더 / CRF / 프리셋 설정, 방식은 재인코딩 / 컨테이너 재포장만(기본) / 트랜스코더 미사용 3가지
- **진행률 표시**: 처리 프레임 / 전체 프레임과 예상 남은 시간
- **고주사율**: 실행 시 기기가 지원하는 최대 주사율 요청
- **목록 영속화**: 대기 목록을 로컬 JSON으로 저장하고 비정상 종료 후 복원
- **공유 받기**: 다른 앱에서 공유한 사진 변환
- **UI 텍스트 전면 현지화**: 앱 UI 텍스트는 120개 언어를 지원합니다(시스템 또는 앱 내 언어 설정을 따름)

### 소스에서 빌드

1. [Android SDK](https://developer.android.com/studio)(커맨드라인 도구 가능)를 설치하고 `local.properties`를 설정합니다.
2. 전체판은 미리 빌드된 `libffmpeg.so`([vidra-ffmpeg](https://github.com/chomusuke-mk/vidra-ffmpeg) 제공)를 `app/src/normal/jniLibs/arm64-v8a/libffmpeg.so`에 둡니다(없어도 빌드되지만 트랜스코딩 기능이 없습니다).
3. `cd Source/ZLivePhoto.Android && ./gradlew assembleDebug` (릴리스 빌드에는 `signing.properties` 필요).

구성: `Source/ZLivePhoto.Android/`(Android 앱), `.github/workflows/android-release.yml`(태그 → 서명된 APK와 GitHub Release), `CHANGELOG.md`, `LICENSE`. 종료된 Windows 데스크톱판(C# / WinUI 3)은 `v2.3.0` 브랜치에, 각 릴리스 코드는 `v<버전>` 브랜치에 보존됩니다.

### 알려진 제한

- Apple 라이브 포토를 다른 형식으로 변환할 때 일부 기기에서 인식은 되지만 재생·편집이 안 되는 호환성 문제가 있었습니다. 원인(QuickTime `mett`/`tmcd` 트랙 잔여, `compatible_brands` 잔여)은 **v3.4.15부터** 바이트 수준에서 확정적으로 수정되었고 HEIC 커버와 '재포장만으로 해결 불가' 감지도 추가되었습니다. 실기기 재생·편집 개선은 실기기 확인 대기 중입니다.
- 표준 MP4가 아닌 동영상(MOV / WebM / MKV / H.264·H.265 외)은 '설정 → 동영상 트랜스코딩 → 방식'에 따릅니다.

### 라이선스

GNU General Public License v3.0 이상(GPL-3.0-or-later). Copyright (C) 2026 zsz. 이 프로그램은 유용하게 쓰이기를 바라며 배포되지만 어떠한 보증도 제공하지 않습니다. 자세한 내용은 [LICENSE](LICENSE)를 참조하세요.

---

## 简体中文

> 动态照片格式互转工具：Google Motion Photo / OPPO / vivo / 小米 / 荣耀 / 魅族 / 努比亚 / Apple Live Photo 互转、拆解与合成。

**开发者**: zsz · **版本**: v3.6.2 · **许可证**: GPL-3.0-or-later

本程序实现了主流动态照片格式之间的字节级无损互转，并支持动态照片的拆解与合成。界面支持 **121 种语言**（含简繁中文各变体、藏/傣泐/彝/维吾尔等少数民族语言），**120 个语言目录全部 100% 翻译**（404/404 key），设置页可单独切换应用语言，并已适配 RTL（从右到左）语言。

### 支持的格式

- **Google Motion Photo**：JPEG(+GainMap) + MP4 裸拼，XMP 标记 Container Directory
- **OPPO**：Google 格式 + XMP 私有标签（OpCamera/OLivePhoto/VCamera）+ moov 内 lpex box
- **vivo**：双文件（JPG + MP4）与单文件，footer JSON 关联 ID
- **小米**：双 XMP 标签（MotionPhoto + MicroVideo）+ EXIF 0x8897
- **荣耀**：JPEG(+GainMap) + MP4(large size) + uuid box(extend_type_matrix + EIS JSON) + 60B tail(LIVE_ID)
- **魅族**：MZCamera 动态照片（识别 / 读取 / 转出）
- **Apple Live Photo**：双文件（JPG/MOV 或 HEIC/MOV），ContentIdentifier UUID 配对；`ftyp` 品牌双向清洗（MOV↔MP4 都清掉对方的品牌残留），非音视频轨用纯字节级剔除

此外支持「拆解」输出（导出照片 + 视频双文件），以及「合成」（封面照片 + ≤3 秒视频生成动态照片）。核心解析纯字节级操作（JPEG/MP4 box 遍历），转换后保留源文件的 EXIF 元数据（GPS、拍摄时间等）和修改时间。

### 版本与系统要求

> ⚠️ **维护声明**：自 v3.0.0 起停止维护与发布 **Windows 桌面版**，此后仅发布 Android 版本。Windows 版（C# / WinUI 3，上一完整版本 v2.3.0）的完整源码保留在 **`v2.3.0` 分支**；每个已发布版本的代码都保留在对应的 `v<版本>` 分支，`main` 分支只包含当前 Android 端代码。

| 版本 | 最低系统版本 | 架构 |
|------|-------------|------|
| ZLC（完整版） | Android 10 (API 29) | arm64-v8a |
| ZLC Go（轻量版） | Android 6 (API 23) | arm64-v8a / armeabi-v7a / x86 / x86_64 |

Android 端无需额外运行时。完整版内置 ffmpeg 转码器，仅提供 arm64-v8a（当前绝大多数机型）；Go 轻量版不含转码器，因此仍覆盖更多架构。Go 版在 Android 10 及以上已不再适配，需改用完整版。两版包名不同（`com.zsz.zlivephoto` / `com.zsz.zlivephoto.go`），可同时安装。

### 下载安装

前往 [Releases](https://github.com/zsz-of/ZLC/releases) 下载最新版本，更新内容见 [CHANGELOG](CHANGELOG.md)，发布说明中另附蓝奏云镜像下载。

| 平台 | 安装包 | 格式 |
|------|--------|------|
| Android（完整版） | ZLC_<版本>.apk | APK |
| Android（轻量版） | ZLC_Go_<版本>.apk | APK |

安装步骤：① 下载 `.apk` 文件 → ② 允许「来自未知来源」安装 → ③ 点击 APK 完成安装。

### 功能特性

| 功能 | 说明 |
|------|------|
| 多格式互转 | Google / OPPO / vivo / 小米 / 荣耀 / 魅族 / Apple 之间互转 |
| 合成动态照片 | 封面照片 + ≤3 秒视频合成动态照片；非 JPEG 封面与非标准 MP4 视频自动转码 |
| 拆解导出 | 将动态照片拆解为「照片 + 视频」双文件 |
| 字节级无损 | 纯字节解析重组，不重新编码 |
| EXIF 保留 | 保留 GPS、拍摄时间等所有 EXIF 元数据 |
| 时间戳保留 | 输出文件保留源文件的修改时间 |
| 按源文件夹层级输出 | 默认按**原相册名**分子目录存放（`Pictures/Z-LivePhoto-Converter/Camera/`、`.../微信/`），可在设置 → 输出中关闭以恢复平铺 |
| 同格式直通 | 源与目标格式相同时原样复制（零损耗） |
| 处理完成后删除原图 | 批次全部处理成功后直接删除磁盘原文件并清理媒体库条目；**必须先授予「所有文件访问」**才可开启，Android 10 及以下不支持该功能 |
| 替换模式（工具箱） | 转换结果**直接写回原文件**，不在相册生成新文件；**必须先授予「所有文件访问」**，与「删除原图」互斥，首次开启弹一次风险与免责提示 |
| 自动配对 | vivo/Apple 双文件自动查找同目录视频 |
| 双版本 | ZLC 完整版（Android 10+，内置 ffmpeg 转码器）与 ZLC Go 轻量版（Android 6+，适配老设备，不含转码器） |
| Material You | Android 12+ 动态取色跟随壁纸；7 种预制主题色 + 自定义色相 |
| 多语言 | 界面支持 **121 种语言**（**120 个语言目录全部 100% 翻译，404/404 key**）；设置 → 外观可单独切换「应用语言」（首项「跟随系统」）；不支持的语言回落简体中文；已适配 RTL |
| 检查更新 | 启动/手动检查新版本；GitHub / 蓝奏云应用内直接下载安装（蓝奏压缩包自动解出 APK，**支持带提取码的分享**）；支持「跳过此版本」（Go 版匹配 Go 安装包） |
| 弹窗排队 | 权限请求 / 应用更新 / 所有文件访问引导等会话型弹窗统一按优先级排队，同一时刻只弹一个 |
| 批量导入 | Android 支持文件夹批量扫描导入（含子目录），流式处理不卡顿 |
| 相册排序与搜索 | 内置选择器按大小 / 日期 / 名称排序（正序 / 倒序）并支持按名称搜索；**v3.6.1 修复展开搜索栏时搜索框上下跳动** |
| 内置视频转码 | ffmpeg 随完整版安装包内置（`libffmpeg.so`），处理方式三选一（重新编码 / 仅重封装容器 / 完全不使用），设置页可调 CRF / 编码器 / 预设 |
| 转码进度 | 合成转码时在对应列表项内展示「已处理帧 / 总帧数」进度条与预计剩余时间 |
| 高刷新率 | 完整版启动时自动请求设备支持的最高刷新率档位，动画跑满屏幕帧率上限 |
| 列表持久化 | Android 列表自动落盘本地 JSON，异常退出可检测并恢复 |
| 系统分享 | Android 支持从系统分享接收照片转换 |
| 界面文案全量本地化 | 应用界面文案已支持 120 种语言（跟随系统或应用内语言设置切换） |

### 从源码构建

1. 安装 [Android SDK](https://developer.android.com/studio)（Command-line tools 即可），配置 `local.properties` 指向 SDK 路径；
2. 完整版的内置转码器为预编译 ffmpeg 二进制（未入库，来自 [vidra-ffmpeg](https://github.com/chomusuke-mk/vidra-ffmpeg)），需放入 `app/src/normal/jniLibs/arm64-v8a/libffmpeg.so`；不放置时完整版编译通过但不含转码能力；
3. 执行 `cd Source/ZLivePhoto.Android && ./gradlew assembleDebug`（发布版构建需要 `signing.properties`）。

目录结构：`Source/ZLivePhoto.Android/`（Android 应用）、`.github/workflows/android-release.yml`（打 tag 自动构建两个 flavor 并发布 GitHub Release）、`CHANGELOG.md`、`LICENSE`。

| 内容 | 位置 |
|------|------|
| 应用设置（主题 / 触感 / 转码方式与参数 / 替换模式 / 跳过版本等） | 应用私有 SharedPreferences，文件 `zlivephoto.xml` |
| 转换产物 | 相册目录 `Pictures/Z-LivePhoto-Converter/`；开启「按源文件夹层级输出」时为其下的原相册名子目录；开启**替换模式**时不产生新文件 |
| 待处理列表的持久化快照 | 应用私有目录下的本地 JSON |
| 崩溃日志 | 外部存储根目录 `Z-LivePhoto-Crash.log` |

### 已知限制

- Apple 动态照片**转换为其他格式曾存在兼容性 bug**：产物在部分机型（如 vivo 相册）中可被识别为动态照片、封面与 EXIF 均正常，但无法长按播放、无法编辑（提示图片已破损）。原因有两条：① 视频轨夹带 QuickTime 遗留的 `mett`/`tmcd` 等非音视频轨；② `ftyp` 只改了 `major_brand`，`compatible_brands` 里残留 QuickTime 品牌。**v3.4.15 起**两条都在纯字节层确定性修复，并新增 HEIC 封面支持与「仅重封装解决不了」的检测。**真机的播放/编辑改善仍待设备确认**。
- **合成时若输入视频不符合标准 MP4**（MOV / WebM / MKV / 非 H.264·H.265 编码），处理方式由「设置 → 视频转码 → 转码方式」决定：**重新编码**（兼容性最好）、**仅重封装容器**（默认，快且无损）、**完全不使用转码器**。Go 轻量版按「完全不使用转码器」处理。

### 开源引用

| 项目 | 用途 |
|------|------|
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | Android UI 框架 |
| [Material 3](https://m3.material.io/) | 设计系统 |
| [FFmpeg](https://ffmpeg.org/) | 视频转码（完整版内置，预编译自 [vidra-ffmpeg](https://github.com/chomusuke-mk/vidra-ffmpeg)） |

### 许可证

本程序基于 **GNU General Public License v3.0 (GPLv3)** 开源协议发布。Copyright (C) 2026 zsz. 本程序分发时附带希望其有用的保证，但不提供任何担保；甚至不提供适销性或特定用途适用性的默示担保。详见 [LICENSE](LICENSE)。

---

## 繁體中文

> 動態照片格式互轉工具：Google Motion Photo / OPPO / vivo / 小米 / 榮耀 / 魅族 / 努比亞 / Apple Live Photo 互轉、拆解與合成。

**開發者**：zsz ・ **版本**：v3.6.2 ・ **授權條款**：GPL-3.0-or-later

本程式可在主流動態照片格式之間進行位元組級無損互轉，並支援動態照片的拆解與合成。介面支援 **121 種語言**（含簡繁中文各變體、藏／傣泐／彝／維吾爾等少數民族語言），**120 個語言目錄全部 100% 翻譯**（404/404 key），設定頁可單獨切換應用程式語言，並已支援 RTL（由右至左）語言。

### 支援的格式

- **Google Motion Photo**：JPEG(+GainMap) + MP4 裸拼，XMP 標記 Container Directory
- **OPPO**：Google 格式 + XMP 私有標籤（OpCamera/OLivePhoto/VCamera）+ moov 內 lpex box
- **vivo**：雙檔案（JPG + MP4）與單一檔案，footer JSON 關聯 ID
- **小米**：雙 XMP 標籤（MotionPhoto + MicroVideo）+ EXIF 0x8897
- **榮耀**：JPEG(+GainMap) + MP4(large size) + uuid box(extend_type_matrix + EIS JSON) + 60B tail(LIVE_ID)
- **魅族**：MZCamera 動態照片（識別／讀取／轉出）
- **Apple Live Photo**：雙檔案（JPG/MOV 或 HEIC/MOV），以 ContentIdentifier UUID 配對；`ftyp` 品牌雙向清洗，非音視訊軌以純位元組方式剔除

另支援「拆解」輸出（匯出照片 + 影片雙檔案）與「合成」（封面照片 + ≤3 秒影片產生動態照片）。核心解析為純位元組操作（JPEG／MP4 box 走訪），轉換後保留來源檔案的 EXIF 中繼資料（GPS、拍攝時間等）與修改時間。

### 版本與系統需求

> ⚠️ **維護聲明**：自 v3.0.0 起停止維護與發布 **Windows 桌面版**，之後僅發布 Android 版本。Windows 版（C# / WinUI 3，最後完整版本 v2.3.0）原始碼保留在 **`v2.3.0` 分支**；每個已發布版本的程式碼都保留在對應的 `v<版本>` 分支。

| 版本 | 最低系統版本 | 架構 |
|------|-------------|------|
| ZLC（完整版） | Android 10 (API 29) | arm64-v8a |
| ZLC Go（輕量版） | Android 6 (API 23) | arm64-v8a / armeabi-v7a / x86 / x86_64 |

Android 端無需額外執行環境。完整版內建 ffmpeg 轉碼器，僅提供 arm64-v8a；Go 輕量版不含轉碼器，因此涵蓋更多架構。Go 版在 Android 10 以上已不再適配，請改用完整版。兩版套件名稱不同（`com.zsz.zlivephoto` / `com.zsz.zlivephoto.go`），可同時安裝。

### 下載安裝

前往 [Releases](https://github.com/zsz-of/ZLC/releases) 下載最新版本（`ZLC_<版本>.apk` 完整版、`ZLC_Go_<版本>.apk` 輕量版），更新內容見 [CHANGELOG](CHANGELOG.md)，發布說明另附藍奏雲鏡像。安裝步驟：下載 `.apk` → 允許「來自未知來源」安裝 → 點擊 APK 完成安裝。

### 功能特色

- **多格式互轉**：Google / OPPO / vivo / 小米 / 榮耀 / 魅族 / Apple 之間互轉
- **合成動態照片**：封面照片 + ≤3 秒影片；非 JPEG 封面與非標準 MP4 影片自動轉碼
- **拆解匯出**：動態照片 → 照片 + 影片
- **位元組級無損**：純位元組解析重組，不重新編碼
- **保留 EXIF 與時間戳**（GPS、拍攝時間、修改時間）
- **依來源資料夾層級輸出**：預設以原相簿名建立子目錄，可在設定 → 輸出中關閉
- **同格式直通**：來源與目標格式相同時原樣複製（零損耗）
- **處理完成後刪除原圖**（需先授予「所有檔案存取」，Android 10 及以下不支援）
- **取代模式**（工具箱）：結果直接寫回原檔案（首次開啟彈出一次風險提示）
- **自動配對**：vivo／Apple 雙檔案自動尋找同目錄影片
- **雙版本**：ZLC 完整版（Android 10+，內建轉碼器）與 ZLC Go 輕量版（Android 6+，不含轉碼器）
- **Material You**：Android 12+ 動態取色，7 種預設主題色 + 自訂色相
- **121 種語言介面**（可單獨切換應用程式語言，支援 RTL）
- **檢查更新**：GitHub／藍奏雲應用程式內直接下載安裝（支援帶提取碼的分享），可「跳過此版本」
- **彈窗排隊**：權限、更新、檔案存取引導等彈窗依優先順序逐一顯示
- **批次匯入**：資料夾掃描匯入（含子目錄），串流處理不卡頓
- **相簿排序與搜尋**：依大小／日期／名稱排序（正序／倒序）並支援名稱搜尋；**v3.6.1 修正展開搜尋列時搜尋框上下跳動**
- **內建影片轉碼**：可調 CRF／編碼器／預設；方式三選一（重新編碼／僅重封裝容器／完全不使用）
- **轉碼進度**：顯示「已處理影格／總影格數」與預計剩餘時間
- **高更新率**：啟動時要求裝置支援的最高更新率
- **清單持久化**：待處理清單自動存為本機 JSON，異常結束後可恢復
- **系統分享**：可接收其他應用程式分享的照片進行轉換
- **介面文案全量在地化**：應用程式介面文案已支援 120 種語言（跟隨系統或應用程式內語言設定切換）

### 從原始碼建置

1. 安裝 [Android SDK](https://developer.android.com/studio)（Command-line tools 即可），設定 `local.properties` 指向 SDK 路徑；
2. 完整版的內建轉碼器為預編譯 ffmpeg 二進位（未入庫，來自 [vidra-ffmpeg](https://github.com/chomusuke-mk/vidra-ffmpeg)），需放入 `app/src/normal/jniLibs/arm64-v8a/libffmpeg.so`；
3. 執行 `cd Source/ZLivePhoto.Android && ./gradlew assembleDebug`（發布版建置需要 `signing.properties`）。

目錄結構：`Source/ZLivePhoto.Android/`（Android 應用程式）、`.github/workflows/android-release.yml`（推送 tag 自動建置並發布 GitHub Release）、`CHANGELOG.md`、`LICENSE`。

### 已知限制

- Apple 動態照片轉換為其他格式曾有相容性問題：產物在部分機型可被辨識但無法長按播放或編輯。原因為 ① 影片軌夾帶 QuickTime 遺留非音視訊軌；② `compatible_brands` 殘留 QuickTime 品牌。**v3.4.15 起**已在純位元組層確定性修復，並新增 HEIC 封面與「僅重封裝無法解決」的偵測；**實機播放／編輯改善仍待裝置確認**。
- 若輸入影片不符合標準 MP4（MOV / WebM / MKV / 非 H.264·H.265），處理方式由「設定 → 影片轉碼 → 轉碼方式」決定（重新編碼／僅重封裝容器／完全不使用）。

### 授權條款

本程式依 **GNU General Public License v3.0 (GPLv3)** 發布。Copyright (C) 2026 zsz. 本程式希望對使用者有幫助，但不提供任何擔保，包含不提供可商用性或特定用途適用性的默示擔保。詳見 [LICENSE](LICENSE)。

---

## Русский

> Конвертер форматов живых фотографий: взаимное преобразование, разделение и объединение Google Motion Photo / OPPO / vivo / Xiaomi / Honor / Meizu / Nubia / Apple Live Photo.

Android-приложение (Kotlin + Jetpack Compose) выполняет **побайтовое преобразование без потерь** между основными форматами живых фотографий. Интерфейс доступен на **121 языке** (120 каталогов ресурсов переведены полностью, 404/404 ключа), поддерживается RTL.

### Поддерживаемые форматы

| Формат | Структура |
|--------|-----------|
| Google Motion Photo | JPEG(+GainMap) + MP4, XMP Container Directory |
| OPPO | формат Google + приватные теги XMP (OpCamera/OLivePhoto/VCamera) + box `lpex` в `moov` |
| vivo | два файла (JPG + MP4) или один файл со связкой через footer JSON |
| Xiaomi | два тега XMP (MotionPhoto + MicroVideo) + EXIF 0x8897 |
| Honor | JPEG(+GainMap) + MP4 (large size) + uuid box (extend_type_matrix + EIS JSON) + 60-байтовый хвост (LIVE_ID) |
| Meizu | живые фото MZCamera (обнаружение / чтение / экспорт) |
| Apple Live Photo | два файла (JPG/MOV или HEIC/MOV), связка по ContentIdentifier UUID; двусторонняя очистка брендов `ftyp`, побайтовое удаление не-A/V дорожек |

Поддерживаются «разделение» (фото + видео двумя файлами) и «объединение» (обложка JPEG + видео до 3 секунд). Разбор полностью побайтовый (сегменты JPEG / box MP4); метаданные EXIF (GPS, время съёмки) и время изменения файла сохраняются.

### Редакции и системные требования

| Редакция | Минимальная ОС | ABI |
|----------|----------------|-----|
| ZLC (полная) | Android 10 (API 29) | arm64-v8a |
| ZLC Go (лёгкая) | Android 6 (API 23) | arm64-v8a / armeabi-v7a / x86 / x86_64 |

Полная редакция содержит встроенный транскодер ffmpeg (`libffmpeg.so`, только arm64-v8a); лёгкая редакция Go его не содержит и потому поддерживает больше ABI. На Android 10 и выше Go-редакция больше не адаптируется — используйте полную. Имена пакетов различаются (`com.zsz.zlivephoto` / `com.zsz.zlivephoto.go`), обе версии можно установить одновременно.

### Загрузка

Последняя версия — в разделе [Releases](https://github.com/zsz-of/ZLC/releases) (`ZLC_<версия>.apk` — полная, `ZLC_Go_<версия>.apk` — Go); зеркала на облаке Lanzou указаны в примечаниях к релизу. Список изменений — в [CHANGELOG](CHANGELOG.md). Скачайте APK, разрешите установку из неизвестных источников и нажмите на файл.

### Возможности

- **Взаимное преобразование форматов**: Google / OPPO / vivo / Xiaomi / Honor / Meizu / Apple
- **Объединение**: обложка JPEG + видео до 3 секунд; обложки не-JPEG и нестандартные MP4 транскодируются автоматически
- **Разделение**: живое фото → фото + видео
- **Без потерь на уровне байтов** (без перекодирования)
- **Сохранение EXIF и меток времени** (GPS, время съёмки, время изменения)
- **Вывод с иерархией альбомов**: подпапки с именем исходного альбома (отключается в «Настройки → Вывод»)
- **Копирование при совпадении форматов** (без потерь)
- **Удаление исходников после обработки** (нужен доступ ко всем файлам; Android 10 и ниже — нет)
- **Режим замены** (инструменты): запись результата поверх исходных файлов (однократное предупреждение)
- **Автосвязывание** парных файлов vivo/Apple
- **Две редакции**: ZLC (Android 10+, со встроенным транскодером) и ZLC Go (Android 6+, без него)
- **Material You**: динамический цвет на Android 12+, 7 предустановленных тем + свой оттенок
- **Интерфейс на 121 языке** с переключением языка приложения и поддержкой RTL
- **Проверка обновлений**: загрузка из GitHub или Lanzou прямо в приложении (включая ссылки с паролем), «пропустить эту версию»
- **Очередь диалогов**: разрешения, обновления и запросы доступа показываются по одному в порядке приоритета
- **Пакетный импорт**: сканирование папок (с подпапками) и потоковая обработка
- **Сортировка и поиск**: по дате / размеру / имени (по возрастанию и убыванию), поиск по имени (в v3.6.1 исправлено смещение строки поиска при раскрытии)
- **Встроенное транскодирование**: настройка кодировщика / CRF / пресета; три режима — перекодирование, только переупаковка контейнера (по умолчанию), без транскодера
- **Прогресс транскодирования**: обработано / всего кадров и оценка оставшегося времени
- **Высокая частота обновления**: при запуске запрашивается максимальная поддерживаемая частота
- **Сохранение списка**: очередь хранится в локальном JSON и восстанавливается после сбоя
- **Приём из «Поделиться»**: преобразование фото, отправленных из других приложений
- **Полная локализация интерфейса**: текст интерфейса доступен на 120 языках (в зависимости от системного или внутрипрограммного выбора языка)

### Сборка из исходников

1. Установите [Android SDK](https://developer.android.com/studio) (достаточно command-line tools) и укажите путь в `local.properties`.
2. Для полной редакции положите готовый `libffmpeg.so` (из [vidra-ffmpeg](https://github.com/chomusuke-mk/vidra-ffmpeg)) в `app/src/normal/jniLibs/arm64-v8a/libffmpeg.so`; без него сборка пройдёт, но без транскодирования.
3. `cd Source/ZLivePhoto.Android && ./gradlew assembleDebug` (для release-сборки нужен `signing.properties`).

Структура: `Source/ZLivePhoto.Android/` (приложение), `.github/workflows/android-release.yml` (тег → подписанные APK и GitHub Release), `CHANGELOG.md`, `LICENSE`. Снятая с поддержки версия для Windows (C# / WinUI 3) хранится в ветке `v2.3.0`, код каждого релиза — в ветке `v<версия>`.

### Известные ограничения

- У преобразования Apple Live Photo в другие форматы были проблемы совместимости: файл распознаётся, но не воспроизводится и не редактируется на части устройств. Причины (остаточные дорожки QuickTime `mett`/`tmcd` и остаточные бренды `compatible_brands`) детерминированно исправлены на уровне байтов **начиная с v3.4.15**, добавлены поддержка обложек HEIC и обнаружение случаев «переупаковкой не исправить». Улучшение на реальных устройствах ещё требует проверки.
- Видео, не являющееся стандартным MP4 (MOV / WebM / MKV / не H.264·H.265), обрабатывается согласно «Настройки → Транскодирование видео → Режим».

### Лицензия

GNU General Public License v3.0 или новее (GPL-3.0-or-later). Copyright (C) 2026 zsz. Программа распространяется в надежде, что она будет полезной, но БЕЗ КАКИХ-ЛИБО ГАРАНТИЙ; подробности — в [LICENSE](LICENSE).

---

## Français

> Convertisseur de formats de photos animées : conversion, séparation et fusion entre Google Motion Photo / OPPO / vivo / Xiaomi / Honor / Meizu / Nubia / Apple Live Photo.

Application Android (Kotlin + Jetpack Compose) réalisant une conversion **octet par octet, sans perte**, entre les principaux formats de photos animées. L'interface est disponible en **121 langues** (120 dossiers de ressources entièrement traduits, 404/404 clés) et prend en charge le RTL.

### Formats pris en charge

| Format | Structure |
|--------|-----------|
| Google Motion Photo | JPEG(+GainMap) + MP4, XMP Container Directory |
| OPPO | structure Google + balises XMP privées (OpCamera/OLivePhoto/VCamera) + box `lpex` dans `moov` |
| vivo | deux fichiers (JPG + MP4) ou un seul fichier relié par un footer JSON |
| Xiaomi | deux balises XMP (MotionPhoto + MicroVideo) + EXIF 0x8897 |
| Honor | JPEG(+GainMap) + MP4 (large size) + uuid box (extend_type_matrix + EIS JSON) + queue de 60 octets (LIVE_ID) |
| Meizu | photos animées MZCamera (détection / lecture / export) |
| Apple Live Photo | deux fichiers (JPG/MOV ou HEIC/MOV) appairés par ContentIdentifier UUID ; nettoyage bidirectionnel des marques `ftyp`, suppression octet par octet des pistes non A/V |

La « séparation » (photo + vidéo en deux fichiers) et la « fusion » (couverture JPEG + vidéo de 3 s maximum) sont prises en charge. L'analyse est entièrement au niveau des octets (segments JPEG / box MP4) ; les métadonnées EXIF (GPS, date de prise de vue) et la date de modification sont conservées.

### Éditions et configuration requise

| Édition | OS minimal | ABI |
|---------|------------|-----|
| ZLC (complète) | Android 10 (API 29) | arm64-v8a |
| ZLC Go (légère) | Android 6 (API 23) | arm64-v8a / armeabi-v7a / x86 / x86_64 |

L'édition complète intègre le transcodeur ffmpeg (`libffmpeg.so`, arm64-v8a uniquement) ; l'édition Go ne l'inclut pas et couvre donc davantage d'ABI. L'édition Go n'est plus adaptée à Android 10 et supérieur : utilisez l'édition complète. Les noms de paquets diffèrent (`com.zsz.zlivephoto` / `com.zsz.zlivephoto.go`) et les deux peuvent coexister.

### Téléchargement

La dernière version est disponible dans [Releases](https://github.com/zsz-of/ZLC/releases) (`ZLC_<version>.apk` pour la complète, `ZLC_Go_<version>.apk` pour la Go) ; des miroirs Lanzou sont indiqués dans les notes de version. L'historique est dans [CHANGELOG](CHANGELOG.md). Téléchargez l'APK, autorisez l'installation de sources inconnues, puis touchez le fichier.

### Fonctionnalités

- **Conversion entre formats** : Google / OPPO / vivo / Xiaomi / Honor / Meizu / Apple
- **Fusion** : couverture JPEG + vidéo ≤ 3 s ; les couvertures non JPEG et les MP4 non standard sont transcodés automatiquement
- **Séparation** : photo animée → photo + vidéo
- **Sans perte au niveau des octets** (aucun réencodage)
- **Conservation EXIF et horodatage** (GPS, date de prise de vue, date de modification)
- **Sortie par hiérarchie d'album** : sous-dossiers au nom de l'album d'origine (désactivable dans Réglages → Sortie)
- **Copie directe** si le format source et cible sont identiques (zéro perte)
- **Suppression des originaux après traitement** (accès à tous les fichiers requis ; non pris en charge sur Android 10 et antérieur)
- **Mode remplacement** (boîte à outils) : écriture directe sur les fichiers d'origine (avertissement unique)
- **Appairage automatique** des fichiers vivo/Apple
- **Deux éditions** : ZLC (Android 10+, transcodeur intégré) et ZLC Go (Android 6+, sans transcodeur)
- **Material You** : couleur dynamique sur Android 12+, 7 thèmes prédéfinis + teinte personnalisée
- **Interface en 121 langues** avec changement de langue par application et prise en charge du RTL
- **Recherche de mises à jour** : téléchargement depuis GitHub ou Lanzou dans l'application (partages protégés par mot de passe inclus), option « ignorer cette version »
- **File de dialogues** : autorisations, mises à jour et accès aux fichiers s'affichent un par un par ordre de priorité
- **Import par lots** : analyse de dossiers (sous-dossiers inclus) et traitement en flux
- **Tri et recherche** : par date / taille / nom (croissant ou décroissant) et recherche par nom (v3.6.1 corrige le décalage de la barre de recherche à l'ouverture)
- **Transcodage intégré** : encodeur / CRF / préréglage configurables ; trois modes — réencodage, remux du conteneur uniquement (par défaut), aucun transcodeur
- **Progression du transcodage** : images traitées / total et temps restant estimé
- **Taux de rafraîchissement élevé** : demande le taux maximal pris en charge au démarrage
- **Persistance de la liste** : file d'attente enregistrée en JSON local et restaurée après un arrêt anormal
- **Partage système** : conversion des photos partagées depuis d'autres applications
- **Textes de l'interface entièrement localisés** : les textes de l'interface sont disponibles en 120 langues (selon la langue du système ou le réglage de langue de l'application)

### Compilation depuis les sources

1. Installez le [Android SDK](https://developer.android.com/studio) (les command-line tools suffisent) et renseignez `local.properties`.
2. Pour l'édition complète, placez le `libffmpeg.so` précompilé (issu de [vidra-ffmpeg](https://github.com/chomusuke-mk/vidra-ffmpeg)) dans `app/src/normal/jniLibs/arm64-v8a/libffmpeg.so` ; sans lui, la compilation réussit mais sans transcodage.
3. `cd Source/ZLivePhoto.Android && ./gradlew assembleDebug` (`signing.properties` est requis pour une version release).

Arborescence : `Source/ZLivePhoto.Android/` (application), `.github/workflows/android-release.yml` (tag → APK signés et GitHub Release), `CHANGELOG.md`, `LICENSE`. La version Windows abandonnée (C# / WinUI 3) reste dans la branche `v2.3.0` ; le code de chaque version publiée est conservé dans une branche `v<version>`.

### Limitations connues

- La conversion des Apple Live Photo vers d'autres formats souffrait de problèmes de compatibilité (reconnu mais non lisible/éditable sur certains appareils). Les deux causes (pistes QuickTime `mett`/`tmcd` résiduelles et marques `compatible_brands` résiduelles) sont corrigées de façon déterministe au niveau des octets **depuis la v3.4.15**, avec la prise en charge des couvertures HEIC et la détection « un simple remux ne suffit pas ». L'amélioration en lecture/édition sur appareil réel reste à confirmer.
- Les vidéos non conformes au MP4 standard (MOV / WebM / MKV / non H.264·H.265) suivent Réglages → Transcodage vidéo → Mode.

### Licence

GNU General Public License v3.0 ou ultérieure (GPL-3.0-or-later). Copyright (C) 2026 zsz. Ce programme est distribué dans l'espoir qu'il sera utile, mais SANS AUCUNE GARANTIE ; voir [LICENSE](LICENSE).

---

## Latina

> Instrumentum conversionis photogrammatum vivorum: conversio, dissolutio et compositio inter Google Motion Photo / OPPO / vivo / Xiaomi / Honor / Meizu / Nubia / Apple Live Photo.

Hoc programma Android (Kotlin + Jetpack Compose) photogrammata viva inter formas principales **ad gradum octeti sine damno** convertit. Interfacies **121 linguis** praesto est (120 directorii facultatum plene versi, 404/404 claves), et linguas RTL sustinet.

### Formae suffultae

| Forma | Structura |
|-------|-----------|
| Google Motion Photo | JPEG(+GainMap) + MP4, XMP Container Directory |
| OPPO | forma Google + tituli XMP privati (OpCamera/OLivePhoto/VCamera) + box `lpex` in `moov` |
| vivo | duo fasciculi (JPG + MP4) vel unus fasciculus per footer JSON coniunctus |
| Xiaomi | duo tituli XMP (MotionPhoto + MicroVideo) + EXIF 0x8897 |
| Honor | JPEG(+GainMap) + MP4 (large size) + uuid box (extend_type_matrix + EIS JSON) + cauda 60 octetorum (LIVE_ID) |
| Meizu | photogrammata viva MZCamera (agnitio / lectio / exportatio) |
| Apple Live Photo | duo fasciculi (JPG/MOV vel HEIC/MOV), per ContentIdentifier UUID copulati; purgatio bilateralis notarum `ftyp`, remotio octetorum tramitum non A/V |

Et « dissolutio » (photogramma + pellicula in duos fasciculos) et « compositio » (integumentum JPEG + pellicula usque ad 3 secundas) sustinentur. Analysis tota ad gradum octeti fit (segmenta JPEG / box MP4); metadata EXIF (GPS, tempus captationis) et tempus mutationis servantur.

### Editiones et postulata systematis

| Editio | Systema minimum | ABI |
|--------|-----------------|-----|
| ZLC (plena) | Android 10 (API 29) | arm64-v8a |
| ZLC Go (levis) | Android 6 (API 23) | arm64-v8a / armeabi-v7a / x86 / x86_64 |

Editio plena transcodificatorem ffmpeg (`libffmpeg.so`, solum arm64-v8a) includit; editio Go eo caret ideoque plures ABI sustinet. Editio Go pro Android 10 vel superiore non iam accommodata est — editio plena adhibenda est. Nomina fasciculorum differunt (`com.zsz.zlivephoto` / `com.zsz.zlivephoto.go`), ut ambae simul installari possint.

### Descriptio

Ultima versio in [Releases](https://github.com/zsz-of/ZLC/releases) praesto est (`ZLC_<versio>.apk` = plena, `ZLC_Go_<versio>.apk` = Go); specula in nube Lanzou in notis editionis indicantur. Historia mutationum in [CHANGELOG](CHANGELOG.md). Fasciculum APK deprime, installationem e fontibus incognitis permitte, deinde fasciculum tange.

### Functiones praecipuae

- **Conversio inter formas**: Google / OPPO / vivo / Xiaomi / Honor / Meizu / Apple
- **Compositio**: integumentum JPEG + pellicula usque ad 3 secundas; integumenta non JPEG et MP4 non standard automatice transcodificantur
- **Dissolutio**: photogramma vivum → photogramma + pellicula
- **Sine damno ad gradum octeti** (nulla recodificatio)
- **EXIF et tempora servantur** (GPS, tempus captationis, tempus mutationis)
- **Output per hierarchiam alborum**: subdirectoria nomine albi fontis (in « Setting → Output » disactivari potest)
- **Translatio directa** si forma fontis et scopus eadem est (nullum damnum)
- **Deletio originalium post processum** (accessus ad omnes fasciculos necessarius; Android 10 et inferior non sustinetur)
- **Modus substitutionis** (instrumenta): eventus in fasciculos fontis directe scribitur (monitio semel)
- **Copulatio automatica** fasciculorum vivo/Apple
- **Duae editiones**: ZLC (Android 10+, transcodificator inclusus) et ZLC Go (Android 6+, sine eo)
- **Material You**: color dynamicus in Android 12+, 7 themata praeparata + color proprius
- **Interfacies 121 linguarum** cum commutatione linguae et RTL
- **Investigatio novitatum**: de GitHub vel Lanzou in ipso programmate (etiam nexus tessera munitus), optio « hanc versionem praeterire »
- **Series dialogorum**: licentiae, novitates et accessus fasciculorum per ordinem prioritatis singillatim monstrantur
- **Importatio gregatim**: scanndo directoriorum (subdirectoriis inclusis) et processus fluens
- **Ordinatio et quaesitio**: per diem / magnitudinem / nomen (ascendens, descendens) et quaesitio nominis (v3.6.1 motum lineae quaesitionis aperiendo corrigit)
- **Transcodificatio inclusa**: encoder / CRF / praeset configurabiles; tres modi — recodificare, solum continens remuxare (praedefinitus), transcodificatore non uti
- **Progressus transcodificationis**: tabulae tractatae / totae et tempus reliquum aestimatum
- **Celeritas renovationis alta**: in initio maxima dispositio sustentata petitur
- **Persistentia indicis**: index pendentium in JSON locali servatur et post casum restituitur
- **Communicatio systematis**: photogrammata ex aliis programmatibus accepta convertuntur
- **Textus interfaciei plene localizatus**: textus interfaciei 120 linguis praesto est (systema vel optionem linguae in programmate sequitur)

### Compilatio e fonte

1. [Android SDK](https://developer.android.com/studio) installa (command-line tools sufficiunt) et viam in `local.properties` scribe.
2. Pro editione plena `libffmpeg.so` praecompilatum (ex [vidra-ffmpeg](https://github.com/chomusuke-mk/vidra-ffmpeg)) in `app/src/normal/jniLibs/arm64-v8a/libffmpeg.so` pone; eo absente compilatio succedit sed transcodificatio deest.
3. `cd Source/ZLivePhoto.Android && ./gradlew assembleDebug` (pro editione release `signing.properties` necessarius est).

Structura: `Source/ZLivePhoto.Android/` (programma Android), `.github/workflows/android-release.yml` (tag → APK signata et GitHub Release), `CHANGELOG.md`, `LICENSE`. Versio Windows desueta (C# / WinUI 3) in ramo `v2.3.0` servatur; codex cuiusque versionis editi in ramo `v<versio>` conservatur.

### Limites noti

- Conversio photogrammatum vivorum Apple in alias formas difficultates congruentiae habebat: in nonnullis machinis agnoscitur sed non legitur neque editur. Causae duae (tramites QuickTime `mett`/`tmcd` residui et notae `compatible_brands` residuae) **a versione v3.4.15** ad gradum octeti determinate correctae sunt, una cum integumentis HEIC et detectione « remux solum non sufficit ». Melioratio in machina vera adhuc confirmanda est.
- Si pellicula non est MP4 standard (MOV / WebM / MKV / non H.264·H.265), modus ex « Setting → Transcodificatio pelliculae → Modus » eligitur.

### Licentia

GNU General Public License v3.0 vel posterior (GPL-3.0-or-later). Copyright (C) 2026 zsz. Hoc programma distribuitur spe utilitatis, sed SINE ULLA FIDE; vide [LICENSE](LICENSE).
