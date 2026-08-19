using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Runtime.InteropServices;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.ApplicationModel.DataTransfer;
using Windows.UI.Text;

namespace ZLivePhoto.WinUI;

/// <summary>文件列表条目。</summary>
public sealed partial class FileItem : INotifyPropertyChanged
{
    public required string Path { get; init; }
    public string DisplayName => System.IO.Path.GetFileName(Path);

    private string _formatName = "";
    public string FormatName
    {
        get => _formatName;
        set { _formatName = value; OnPropertyChanged(); }
    }

    private string _status = "待转换";
    public string Status
    {
        get => _status;
        set { _status = value; OnPropertyChanged(); }
    }

    private bool _isUnrecognized;
    /// <summary>是否为无法识别的文件（用于红色+删除线显示）。</summary>
    public bool IsUnrecognized
    {
        get => _isUnrecognized;
        set
        {
            _isUnrecognized = value;
            OnPropertyChanged();
            OnPropertyChanged(nameof(ForegroundBrush));
            OnPropertyChanged(nameof(TextDecorations));
        }
    }

    private static readonly SolidColorBrush RedBrush = new(Microsoft.UI.Colors.Red);

    /// <summary>无法识别时返回红色画笔，否则返回主题默认文本色画笔。</summary>
    public Brush ForegroundBrush =>
        _isUnrecognized ? RedBrush : (Brush)Application.Current.Resources["TextFillColorPrimaryBrush"];

    /// <summary>无法识别时返回删除线，否则返回无装饰。</summary>
    public TextDecorations TextDecorations =>
        _isUnrecognized ? TextDecorations.Strikethrough : TextDecorations.None;

    public event PropertyChangedEventHandler? PropertyChanged;
    private void OnPropertyChanged([CallerMemberName] string? name = null)
        => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}

public sealed partial class MainWindow : Window
{
    private readonly ObservableCollection<FileItem> _items = [];
    private readonly AppConfig _cfg;
    private bool _converting;

    // 输出格式（Google/Apple 靠前）
    private static readonly (string Name, string Display)[] Targets =
    [
        ("google", "Google"),
        ("apple", "Apple"),
        ("oppo", "OPPO"),
        ("vivo", "vivo"),
        ("xiaomi", "小米"),
        ("honor", "荣耀"),
    ];

    // 最小窗口尺寸（逻辑像素 DIP，按当前 DPI 换算为物理像素后生效）
    private const double MinWidthDip = 560;
    private const double MinHeightDip = 500;

    [DllImport("user32.dll")]
    private static extern uint GetDpiForWindow(IntPtr hwnd);

    private uint _lastDpi;
    private bool _placed;

    public MainWindow()
    {
        InitializeComponent();
        ExtendsContentIntoTitleBar = false;

        _cfg = AppConfig.Load();

        FileList.ItemsSource = _items;

        // 格式单选
        foreach (var (name, display) in Targets)
            FormatRadios.Items.Add(new RadioButton { Content = display, Tag = name });
        int sel = Array.FindIndex(Targets, t => t.Name == _cfg.Target);
        FormatRadios.SelectedIndex = sel >= 0 ? sel : 0;

        OutDirBox.Text = _cfg.OutDir;

        // 构造阶段窗口尚未显示，GetDpiForWindow 只能拿到默认 96 DPI；
        // 最小尺寸与位置恢复推迟到首次激活（此时 HWND 已挂到目标显示器，DPI 正确）
        AppWindow.Changed += AppWindow_Changed;
        Activated += MainWindow_Activated;
        Closed += MainWindow_Closed;
    }

    private void MainWindow_Activated(object sender, WindowActivatedEventArgs args)
    {
        if (_placed) return;
        _placed = true;
        ApplyMinSize();
        RestorePlacement();
    }

    // ---------------------------------------------------------- 最小窗口尺寸（DPI 感知）

    /// <summary>
    /// OverlappedPresenter.PreferredMinimum* 接受物理像素且不感知 DPI（WinAppSDK 已知行为），
    /// 故按窗口当前 DPI 将 DIP 最小尺寸换算为物理像素；跨显示器移动或系统缩放变化时重算。
    /// </summary>
    private void ApplyMinSize()
    {
        if (AppWindow.Presenter is not OverlappedPresenter presenter) return;

        uint dpi = GetDpiForWindow(WinRT.Interop.WindowNative.GetWindowHandle(this));
        if (dpi == 0) dpi = 96;
        _lastDpi = dpi;

        double scale = dpi / 96.0;
        int minW = (int)Math.Round(MinWidthDip * scale);
        int minH = (int)Math.Round(MinHeightDip * scale);
        presenter.PreferredMinimumWidth = minW;
        presenter.PreferredMinimumHeight = minH;

        // DPI 变化后窗口当前尺寸可能低于新最小值，立即放大
        var size = AppWindow.Size;
        if (size.Width < minW || size.Height < minH)
            AppWindow.Resize(new Windows.Graphics.SizeInt32(
                Math.Max(size.Width, minW), Math.Max(size.Height, minH)));
    }

    private void AppWindow_Changed(AppWindow sender, AppWindowChangedEventArgs args)
    {
        // 窗口位置/尺寸变化可能伴随 DPI 变化（跨屏移动、系统缩放调整），检测并重算最小尺寸
        uint dpi = GetDpiForWindow(WinRT.Interop.WindowNative.GetWindowHandle(this));
        if (dpi != 0 && dpi != _lastDpi)
            DispatcherQueue.TryEnqueue(ApplyMinSize);
    }

    // ---------------------------------------------------------- 窗口位置记忆

    private void RestorePlacement()
    {
        var appWindow = AppWindow;
        if (appWindow is null) return;

        double scale = _lastDpi / 96.0;
        int minW = (int)Math.Round(MinWidthDip * scale);
        int minH = (int)Math.Round(MinHeightDip * scale);
        var area = DisplayArea.GetFromWindowId(appWindow.Id, DisplayAreaFallback.Primary).WorkArea;

        int w, h;
        if (_cfg.Width > 400 && _cfg.Height > 300)
        {
            // 恢复上次尺寸，但不低于最小尺寸（防止 DPI 变化后恢复出过小窗口）
            w = Math.Max(_cfg.Width, minW);
            h = Math.Max(_cfg.Height, minH);
        }
        else
        {
            // 默认尺寸为 DIP，按 DPI 换算为物理像素
            w = (int)Math.Round(900 * scale);
            h = (int)Math.Round(700 * scale);
        }
        appWindow.Resize(new Windows.Graphics.SizeInt32(
            Math.Min(w, area.Width), Math.Min(h, area.Height)));

        if (_cfg.X is int x && _cfg.Y is int y)
        {
            // 屏幕外坐标回退（防止最小化保存的 -32000 等问题）
            if (x >= area.X - 100 && y >= area.Y - 100 && x < area.X + area.Width && y < area.Y + area.Height)
                appWindow.Move(new Windows.Graphics.PointInt32(x, y));
        }
    }

    private void MainWindow_Closed(object sender, WindowEventArgs args)
    {
        var appWindow = AppWindow;
        if (appWindow is not null)
        {
            _cfg.Width = appWindow.Size.Width;
            _cfg.Height = appWindow.Size.Height;
            _cfg.X = appWindow.Position.X;
            _cfg.Y = appWindow.Position.Y;
        }
        if (FormatRadios.SelectedItem is RadioButton rb)
            _cfg.Target = (string)rb.Tag;
        _cfg.OutDir = OutDirBox.Text.Trim();
        _cfg.Save();
    }

    // ---------------------------------------------------------- 文件管理

    private void AddPaths(IEnumerable<string> paths)
    {
        // 显示扫描进度
        ScanProgressPanel.Visibility = Visibility.Visible;

        // 快照已存在路径（用于后台线程快速去重）
        var existing = new HashSet<string>(
            _items.Select(i => i.Path),
            StringComparer.OrdinalIgnoreCase);
        var pathList = paths.ToList();

        Task.Run(() =>
        {
            try
            {
                foreach (var p in pathList)
                {
                    if (!File.Exists(p)) continue;
                    string ext = System.IO.Path.GetExtension(p).ToLowerInvariant();
                    if (ext is not (".jpg" or ".jpeg" or ".heic")) continue;

                    // 后台快速去重
                    lock (existing)
                    {
                        if (!existing.Add(p)) continue;
                    }

                    // 后台线程检测类型
                    string formatName;
                    bool isUnrecognized = false;
                    try
                    {
                        var (plugin, score) = Core.Formats.FormatRegistry.DetectBest(p);
                        if (plugin is null)
                        {
                            formatName = "无法识别";
                            isUnrecognized = true;
                        }
                        else
                        {
                            formatName = $"{plugin.Display}（{score}）";
                        }
                    }
                    catch
                    {
                        formatName = "读取失败";
                        isUnrecognized = true;
                    }

                    // 检测完成后再加入列表（UI 线程），不显示"识别中"
                    var capturedPath = p;
                    var capturedFormat = formatName;
                    var capturedUnrec = isUnrecognized;
                    DispatcherQueue.TryEnqueue(() =>
                    {
                        // UI 线程再校验一次防止并发重复
                        if (_items.Any(i => string.Equals(i.Path, capturedPath, StringComparison.OrdinalIgnoreCase)))
                            return;
                        _items.Add(new FileItem
                        {
                            Path = capturedPath,
                            FormatName = capturedFormat,
                            IsUnrecognized = capturedUnrec,
                        });
                    });
                }
            }
            finally
            {
                DispatcherQueue.TryEnqueue(() =>
                {
                    ScanProgressPanel.Visibility = Visibility.Collapsed;
                });
            }
        });
    }

    private async void AddFiles_Click(object sender, RoutedEventArgs e)
    {
        var picker = new Windows.Storage.Pickers.FileOpenPicker();
        WinRT.Interop.InitializeWithWindow.Initialize(picker,
            WinRT.Interop.WindowNative.GetWindowHandle(this));
        picker.FileTypeFilter.Add(".jpg");
        picker.FileTypeFilter.Add(".jpeg");
        picker.FileTypeFilter.Add(".heic");
        var files = await picker.PickMultipleFilesAsync();
        if (files is not null)
            AddPaths(files.Select(f => f.Path));
    }

    private async void AddFolder_Click(object sender, RoutedEventArgs e)
    {
        var picker = new Windows.Storage.Pickers.FolderPicker();
        WinRT.Interop.InitializeWithWindow.Initialize(picker,
            WinRT.Interop.WindowNative.GetWindowHandle(this));
        picker.FileTypeFilter.Add("*");
        var folder = await picker.PickSingleFolderAsync();
        if (folder is null) return;

        var jpgs = Directory.EnumerateFiles(folder.Path, "*.*", SearchOption.AllDirectories)
            .Where(f => f.EndsWith(".jpg", StringComparison.OrdinalIgnoreCase)
                     || f.EndsWith(".jpeg", StringComparison.OrdinalIgnoreCase)
                     || f.EndsWith(".heic", StringComparison.OrdinalIgnoreCase));
        AddPaths(jpgs);
    }

    private void RemoveSelected_Click(object sender, RoutedEventArgs e)
    {
        var selected = FileList.SelectedItems.Cast<FileItem>().ToList();
        foreach (var item in selected)
            _items.Remove(item);
    }

    private void ClearList_Click(object sender, RoutedEventArgs e) => _items.Clear();

    // ---------------------------------------------------------- 拖放

    private void FileList_DragEnter(object sender, DragEventArgs e)
    {
        if (e.DataView.Contains(StandardDataFormats.StorageItems))
            e.AcceptedOperation = DataPackageOperation.Copy;
    }

    private async void FileList_Drop(object sender, DragEventArgs e)
    {
        if (!e.DataView.Contains(StandardDataFormats.StorageItems)) return;
        var storageItems = await e.DataView.GetStorageItemsAsync();
        var paths = new List<string>();
        foreach (var si in storageItems)
        {
            if (si is Windows.Storage.StorageFile f)
                paths.Add(f.Path);
            else if (si is Windows.Storage.StorageFolder folder)
                paths.AddRange(Directory.EnumerateFiles(folder.Path, "*.*", SearchOption.AllDirectories));
        }
        AddPaths(paths);
    }

    // ---------------------------------------------------------- 输出目录

    private async void BrowseOutDir_Click(object sender, RoutedEventArgs e)
    {
        var picker = new Windows.Storage.Pickers.FolderPicker();
        WinRT.Interop.InitializeWithWindow.Initialize(picker,
            WinRT.Interop.WindowNative.GetWindowHandle(this));
        picker.FileTypeFilter.Add("*");
        var folder = await picker.PickSingleFolderAsync();
        if (folder is not null)
            OutDirBox.Text = folder.Path;
    }

    // ---------------------------------------------------------- 转换

    private async void Convert_Click(object sender, RoutedEventArgs e)
    {
        if (_converting) return;
        if (_items.Count == 0)
        {
            StatusText.Text = "列表为空";
            return;
        }

        string target = FormatRadios.SelectedItem is RadioButton rb ? (string)rb.Tag : "google";
        string outDir = OutDirBox.Text.Trim();

        _converting = true;
        ConvertBtn.IsEnabled = false;
        Progress.Value = 0;
        StatusText.Text = "转换中…";

        // 仅转换已识别的项目；无法识别的项目保留在列表中跳过
        var snapshot = _items.Where(i => !i.IsUnrecognized).ToList();
        int skipped = _items.Count - snapshot.Count;
        int done = 0, ok = 0;
        foreach (var item in snapshot)
            item.Status = "排队中";

        if (snapshot.Count == 0)
        {
            StatusText.Text = skipped > 0 ? $"跳过 {skipped} 个无法识别的文件" : "无可转换的文件";
            ConvertBtn.IsEnabled = true;
            _converting = false;
            return;
        }

        await Task.Run(() =>
        {
            foreach (var item in snapshot)
            {
                DispatcherQueue.TryEnqueue(() => item.Status = "转换中…");
                try
                {
                    string dir = string.IsNullOrEmpty(outDir)
                        ? System.IO.Path.GetDirectoryName(item.Path)!
                        : outDir;
                    var outputs = Core.Converter.ConvertFile(item.Path, target, dir, (_, _, _) => { });
                    var capturedItem = item;
                    DispatcherQueue.TryEnqueue(() =>
                    {
                        // 转换成功：从列表移除
                        _items.Remove(capturedItem);
                    });
                    ok++;
                }
                catch (Exception ex)
                {
                    var capturedItem = item;
                    var capturedMsg = ex.Message;
                    DispatcherQueue.TryEnqueue(() =>
                    {
                        // 失败：保留在列表中并标注错误
                        capturedItem.Status = $"失败：{capturedMsg}";
                    });
                }
                done++;
                var capturedDone = done;
                DispatcherQueue.TryEnqueue(() =>
                {
                    Progress.Value = (double)capturedDone / snapshot.Count * 100;
                    StatusText.Text = $"转换中… {capturedDone}/{snapshot.Count}";
                });
            }
        });

        int failed = snapshot.Count - ok;
        string summary = $"完成：{ok} 成功";
        if (failed > 0) summary += $"，{failed} 失败";
        if (skipped > 0) summary += $"，{skipped} 跳过";
        StatusText.Text = summary;
        ConvertBtn.IsEnabled = true;
        _converting = false;
    }
}
