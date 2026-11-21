package com.zsz.zlivephoto.ui

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.zsz.zlivephoto.ui.picker.AlbumInfo
import com.zsz.zlivephoto.ui.picker.AlbumScanner
import com.zsz.zlivephoto.ui.picker.ImportMode
import com.zsz.zlivephoto.ui.picker.MediaItem

/** 顶层页面路由：Main 主页 / Picker 内置选择器 / Settings 设置页 */
enum class AppScreen { Main, Picker, Settings }

/** 「所有文件访问」引导弹窗的用途：决定弹窗文案与标题 */
enum class AllFilesPurpose { LOCATION, DELETE, REPLACE }

/** 文件名冲突处理动作 */
enum class ConflictAction { SKIP, OVERWRITE, RENAME }

/** 冲突询问请求（挂起协程 ↔ 弹窗之间的桥） */
class ConflictRequest(
    val displayNames: String,
    val onChoose: (ConflictAction) -> Unit
)

/**
 * 应用外壳（AppShell）与业务逻辑（[com.zsz.zlivephoto.MainActivity]）之间的契约。
 *
 * 双 UI 实现（normal 液态玻璃 / go Material 3）只依赖本接口：
 * - 状态由宿主（Activity）持有，UI 只读；
 * - 所有会改变业务状态的操作都通过这里的动作方法回调宿主，
 *   保证两套 UI 的转换、导入、权限、持久化行为完全一致。
 */
interface AppHost {
    // ---------- 列表与运行状态 ----------
    val files: SnapshotStateList<FileItem>
    val statusText: String
    val progress: Float
    val progressDetail: String
    val isImporting: Boolean
    val importProgress: Float
    val isConverting: Boolean
    val isPickerOpening: Boolean
    /** 清空/处理收尾动画播放中：清空按钮禁用 */
    val clearBusy: Boolean
    val selectedFormat: String
    val outputMode: OutputMode
    val outputModeUsable: Boolean
    val isDarkTheme: Boolean

    // ---------- 页面与选择器 ----------
    var screen: AppScreen
    val albums: List<AlbumInfo>
    val scanner: AlbumScanner
    val importMode: ImportMode

    // ---------- 应用级弹窗状态 ----------
    var showPermissionDialog: Boolean
    var showSettingsDialog: Boolean
    var showAllFilesDialog: Boolean
    var allFilesDialogPurpose: AllFilesPurpose
    var importModeDialogVisible: Boolean
    var riskDialogVisible: Boolean
    val conflictRequest: ConflictRequest?
    var conflictAlways: Boolean

    // ---------- 动作 ----------
    fun attachHaptics(controller: HapticController)
    fun onAddFiles()
    fun onBatchImport()
    fun clearFiles()
    fun startConvert()
    fun stopConvert()
    fun selectOutputMode(mode: OutputMode)
    fun setItemFormat(path: String, formatKey: String)
    fun removeFile(path: String)
    fun setOutputFormat(fmt: String)
    fun importPickedItems(items: List<MediaItem>, coverSec: Double)
    fun onPermissionConfirm()
    fun openAppDetailSettings()
    fun requestAllFilesAccess()
    fun pickImportMode(mode: ImportMode)
    /** 深浅色模式（系统/强制）重新计算，驱动主题即时切换 */
    fun refreshDarkTheme()
    /** 升级用户路径：有读权限但缺「所有文件访问」时弹一次引导 */
    fun maybePromptAllFilesAccess()
    /** 风险确认弹窗结果；skipNext=true 表示用户勾选了「下次直接确认」 */
    fun confirmRisk(skipNext: Boolean)
    fun chooseConflict(action: ConflictAction)
}
