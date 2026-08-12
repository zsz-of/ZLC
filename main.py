# -*- coding: utf-8 -*-
"""Z-LivePhoto-Converter：动态照片格式互转工具（GUI + CLI）。

支持 Google Motion Photo / OPPO 单文件 / vivo 双文件格式的任意互转。
GUI 模式：python main.py [--console]
CLI 模式：python main.py --cli --to {google|oppo|vivo} [--out DIR] [--no-mp-suffix] 文件...
"""
import sys
import ctypes


def init_windows_gui(enable_dpi_awareness=True, hide_console=True,
                     allow_console_debug=True):
    """Windows GUI 初始化（windows-gui-init skill）：DPI 感知 + 隐藏控制台。"""
    if sys.platform != 'win32':
        return
    if enable_dpi_awareness:
        try:
            # Per-Monitor V2（Win10 1703+），返回非 0 为成功
            if not ctypes.windll.user32.SetProcessDpiAwarenessContext(-4):
                raise OSError('SetProcessDpiAwarenessContext failed')
        except Exception:
            try:
                hr = ctypes.windll.shcore.SetProcessDpiAwareness(2)
                if hr < 0:
                    ctypes.windll.shcore.SetProcessDpiAwareness(1)
            except Exception:
                pass
    if hide_console:
        if allow_console_debug and '--console' in sys.argv:
            return
        try:
            hwnd = ctypes.windll.kernel32.GetConsoleWindow()
            if hwnd:
                ctypes.windll.user32.ShowWindow(hwnd, 0)  # SW_HIDE
        except Exception:
            pass


init_windows_gui()

import argparse
import datetime
import json
import os
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, ttk
from collections import deque
from pathlib import Path

from core import formats
from core.queue_runner import QueueRunner

APP_TITLE = 'Z-LivePhoto-Converter 动态照片格式转换器'
CONFIG_FILE = 'zlivephoto_config.json'
IMAGE_EXTS = {'.jpg', '.jpeg'}

TARGETS = [(p.name, p.display) for p in formats.PLUGINS]  # 注册表顺序即下拉顺序


# ---------------------------------------------------------------- zAPP 配置

def _get_zapp_dir():
    d = os.path.join(os.path.expanduser('~'), '.zAPP')
    os.makedirs(d, exist_ok=True)
    return d


def _load_config(defaults=None):
    path = os.path.join(_get_zapp_dir(), CONFIG_FILE)
    if os.path.exists(path):
        try:
            with open(path, 'r', encoding='utf-8') as f:
                return json.load(f)
        except (json.JSONDecodeError, OSError):
            pass
    return defaults if defaults is not None else {}


def _save_config(data):
    try:
        path = os.path.join(_get_zapp_dir(), CONFIG_FILE)
        tmp = path + '.tmp'
        with open(tmp, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        os.replace(tmp, path)
    except OSError:
        pass


def _delete_config():
    try:
        os.remove(os.path.join(_get_zapp_dir(), CONFIG_FILE))
        return True
    except FileNotFoundError:
        return False


# ---------------------------------------------------------------- 日志（leveled-logger skill）

class LeveledLogger:
    """线程安全分级日志（UI 解耦）。"""

    LEVEL_LABELS = {'info': '[INFO]', 'warning': '[WARN]',
                    'error': '[ERROR]', 'critical': '[FATAL]'}
    LEVEL_COLORS = {'info': '#2980B9', 'warning': '#B7950B',
                    'error': '#E67E22', 'critical': '#C0392B'}

    def __init__(self, append_callback, refresh_callback, schedule_mainthread,
                 max_entries=5000):
        self._append = append_callback
        self._refresh = refresh_callback
        self._schedule = schedule_mainthread
        self._lock = threading.Lock()
        self._entries = deque(maxlen=max_entries)
        self._visible = {lvl: True for lvl in self.LEVEL_LABELS}

    def log(self, level, message, source='系统'):
        if level not in self.LEVEL_LABELS:
            level = 'info'
        entry = {
            'timestamp': datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'level': level, 'message': message, 'source': source,
        }
        with self._lock:
            self._entries.append(entry)
        self._schedule(lambda e=entry: self._append(e))

    def info(self, m, s='系统'):
        self.log('info', m, s)

    def warning(self, m, s='系统'):
        self.log('warning', m, s)

    def error(self, m, s='系统'):
        self.log('error', m, s)

    def critical(self, m, s='系统'):
        self.log('critical', m, s)

    def set_level_visible(self, level, visible):
        if level in self.LEVEL_LABELS:
            self._visible[level] = visible
            self._schedule(self._refresh)

    def is_visible(self, level):
        return self._visible.get(level, True)

    def get_filtered_entries(self):
        with self._lock:
            return [e for e in self._entries if self._visible.get(e['level'], True)]

    def clear(self):
        with self._lock:
            self._entries.clear()
        self._schedule(self._refresh)

    def format_entry(self, entry):
        label = self.LEVEL_LABELS.get(entry['level'], '[INFO]')
        return f"[{entry['timestamp']}] {label} [{entry['source']}] {entry['message']}"


class LogPanel(ttk.Frame):
    """日志面板（Text + 滚动条 + 级别颜色 tag）。"""

    def __init__(self, master, logger):
        super().__init__(master)
        self.logger = logger
        self.text = tk.Text(self, wrap='char', font=('Consolas', 9),
                            state='disabled', relief='flat', height=12)
        vsb = ttk.Scrollbar(self, orient='vertical', command=self.text.yview)
        self.text.configure(yscrollcommand=vsb.set)
        self.text.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        vsb.pack(side=tk.RIGHT, fill=tk.Y)
        for level, color in LeveledLogger.LEVEL_COLORS.items():
            self.text.tag_config(level, foreground=color)

    def append_entry(self, entry):
        if not self.logger.is_visible(entry['level']):
            return
        self.text.configure(state='normal')
        self.text.insert(tk.END, self.logger.format_entry(entry) + '\n', entry['level'])
        self.text.configure(state='disabled')
        self.text.see(tk.END)

    def refresh(self):
        self.text.configure(state='normal')
        self.text.delete('1.0', tk.END)
        for entry in self.logger.get_filtered_entries():
            self.text.insert(tk.END, self.logger.format_entry(entry) + '\n', entry['level'])
        self.text.configure(state='disabled')
        self.text.see(tk.END)


# ---------------------------------------------------------------- 文件选择（file-selector skill 模式）

class FileSelector(ttk.LabelFrame):
    """双模式文件选择：文件夹扫描 / 手动列表。仅接受 .jpg/.jpeg。"""

    def __init__(self, master, logger, on_files_changed=None):
        super().__init__(master, text='文件选择（动态照片 JPG）', padding=5)
        self.logger = logger
        self.on_files_changed = on_files_changed or (lambda: None)
        self.file_list = []
        self._item_paths = {}
        self.mode_var = tk.StringVar(value='filelist')
        bar = ttk.Frame(self)
        bar.pack(fill=tk.X, pady=(0, 5))
        ttk.Radiobutton(bar, text='手动列表', variable=self.mode_var,
                        value='filelist', command=self._switch).pack(side=tk.LEFT, padx=8)
        ttk.Radiobutton(bar, text='文件夹扫描', variable=self.mode_var,
                        value='folder', command=self._switch).pack(side=tk.LEFT, padx=8)
        self.content = ttk.Frame(self)
        self.content.pack(fill=tk.BOTH, expand=True)
        self._switch()

    def _switch(self):
        for w in self.content.winfo_children():
            w.destroy()
        if self.mode_var.get() == 'folder':
            self._build_folder()
        else:
            self._build_filelist()

    def _make_tree(self, parent, columns):
        tree = ttk.Treeview(parent, columns=[c[0] for c in columns],
                            show='headings', selectmode='extended')
        for col, text, width in columns:
            tree.heading(col, text=text)
            tree.column(col, width=width, anchor='e' if col == 'size' else 'w')
        vsb = ttk.Scrollbar(parent, orient=tk.VERTICAL, command=tree.yview)
        tree.configure(yscrollcommand=vsb.set)
        tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        vsb.pack(side=tk.RIGHT, fill=tk.Y)
        return tree

    def _build_folder(self):
        top = ttk.Frame(self.content)
        top.pack(fill=tk.X, pady=3)
        self.folder_var = tk.StringVar()
        ttk.Entry(top, textvariable=self.folder_var, state='readonly').pack(
            side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 5))
        ttk.Button(top, text='浏览...', command=self._browse).pack(side=tk.RIGHT)
        opts = ttk.Frame(self.content)
        opts.pack(fill=tk.X, pady=3)
        self.recursive_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(opts, text='递归扫描子目录', variable=self.recursive_var).pack(side=tk.LEFT)
        ttk.Button(opts, text='开始扫描', command=self._scan).pack(side=tk.LEFT, padx=10)
        self.scan_label = ttk.Label(opts, text='')
        self.scan_label.pack(side=tk.LEFT, padx=8)
        self.tree = self._make_tree(self.content, (('name', '文件名', 380),
                                                   ('size', '大小', 90)))

    def _build_filelist(self):
        self.tree = self._make_tree(self.content, (('name', '文件名', 380),
                                                   ('size', '大小', 90)))
        bar = ttk.Frame(self.content)
        bar.pack(fill=tk.X, pady=4)
        ttk.Button(bar, text='添加文件', command=self._add).pack(side=tk.LEFT, padx=4)
        ttk.Button(bar, text='移除选中', command=self._remove).pack(side=tk.LEFT, padx=4)
        ttk.Button(bar, text='清空', command=self._clear).pack(side=tk.LEFT, padx=4)
        self.count_label = ttk.Label(bar, text='已添加: 0 个文件')
        self.count_label.pack(side=tk.LEFT, padx=10)

    @staticmethod
    def _size_str(n):
        return f'{n / 1048576:.1f} MB' if n >= 1048576 else f'{n / 1024:.1f} KB'

    def _browse(self):
        folder = filedialog.askdirectory(title='选择要扫描的文件夹')
        if folder:
            self.folder_var.set(folder)

    def _scan(self):
        folder = self.folder_var.get()
        if not folder or not Path(folder).is_dir():
            return
        for item in self.tree.get_children():
            self.tree.delete(item)
        self.file_list.clear()
        self._item_paths.clear()
        scanner = Path(folder).rglob('*') if self.recursive_var.get() else Path(folder).iterdir()
        count = 0
        for fp in scanner:
            if fp.is_file() and fp.suffix.lower() in IMAGE_EXTS:
                abs_path = str(fp.resolve())
                self.file_list.append(abs_path)
                item_id = self.tree.insert('', tk.END, values=(
                    fp.name, self._size_str(fp.stat().st_size)))
                self._item_paths[item_id] = abs_path
                count += 1
        self.scan_label.config(text=f'发现 {count} 个 JPG 文件')
        self.on_files_changed()

    def _add(self):
        files = filedialog.askopenfilenames(
            title='选择动态照片 JPG 文件',
            filetypes=[('JPEG 图像', '*.jpg *.jpeg'), ('所有文件', '*.*')])
        for f in files:
            normalized = str(Path(f).resolve())
            if normalized not in self.file_list:
                self.file_list.append(normalized)
                item_id = self.tree.insert('', tk.END, values=(
                    Path(f).name, self._size_str(os.path.getsize(normalized))))
                self._item_paths[item_id] = normalized
        self.count_label.config(text=f'已添加: {len(self.file_list)} 个文件')
        self.on_files_changed()

    def _remove(self):
        selected = self.tree.selection()
        if not selected:
            return
        to_remove = set()
        for item_id in selected:
            path = self._item_paths.pop(item_id, None)
            if path:
                to_remove.add(path)
            self.tree.delete(item_id)
        if to_remove:
            self.file_list = [f for f in self.file_list if f not in to_remove]
        self.on_files_changed()

    def _clear(self):
        self.file_list.clear()
        self._item_paths.clear()
        for item in self.tree.get_children():
            self.tree.delete(item)
        self.on_files_changed()

    def get_files(self):
        return self.file_list.copy()


# ---------------------------------------------------------------- 主窗口

class App(tk.Tk):

    def __init__(self):
        super().__init__()
        self.title(APP_TITLE)
        self.cfg = _load_config()
        self.geometry(self.cfg.get('geometry', '900x700'))
        self.minsize(760, 600)

        # 日志（先建面板占位，再建 logger 回填，顺序见 leveled-logger skill）
        main = ttk.Frame(self, padding=8)
        main.pack(fill=tk.BOTH, expand=True)

        # ── 转换设置 ──
        settings = ttk.LabelFrame(main, text='转换设置', padding=6)
        settings.pack(fill=tk.X, pady=(0, 6))
        ttk.Label(settings, text='目标格式:').pack(side=tk.LEFT)
        self.target_var = tk.StringVar(value=self.cfg.get('target', TARGETS[0][0]))
        display_map = {name: disp for name, disp in TARGETS}
        self._disp_to_name = {disp: name for name, disp in TARGETS}
        self.target_combo = ttk.Combobox(
            settings, state='readonly', width=34,
            values=[d for _, d in TARGETS])
        self.target_combo.set(display_map.get(self.target_var.get(), TARGETS[0][1]))
        self.target_combo.pack(side=tk.LEFT, padx=6)
        self.target_combo.bind('<<ComboboxSelected>>', self._on_target_change)
        self.mp_suffix_var = tk.BooleanVar(value=self.cfg.get('google_mp_suffix', True))
        self._mp_suffix_saved = self.mp_suffix_var.get()
        self._mp_suffix_forced = False  # 是否处于"非 Google 强制取消"状态
        self.mp_suffix_cb = ttk.Checkbutton(
            settings, text='Google 输出遵循 *MP.jpg 命名约定',
            variable=self.mp_suffix_var)
        self.mp_suffix_cb.pack(side=tk.LEFT, padx=10)
        self._on_target_change()  # 初始化勾选状态

        out_bar = ttk.Frame(settings)
        out_bar.pack(fill=tk.X, pady=(6, 0))
        ttk.Label(out_bar, text='输出目录:').pack(side=tk.LEFT)
        self.out_var = tk.StringVar(value=self.cfg.get('out_dir', ''))
        ttk.Entry(out_bar, textvariable=self.out_var).pack(
            side=tk.LEFT, fill=tk.X, expand=True, padx=6)
        ttk.Button(out_bar, text='浏览...', command=self._browse_out).pack(side=tk.RIGHT)
        ttk.Label(settings, text='（留空则输出到源文件所在目录的 converted 子目录）',
                  foreground='gray').pack(anchor=tk.W, pady=(2, 0))

        # ── 文件选择 ──
        self.selector = FileSelector(main, None)  # logger 稍后回填不影响功能
        self.selector.pack(fill=tk.BOTH, expand=True, pady=(0, 6))

        # ── 队列控制 ──
        ctrl = ttk.Frame(main)
        ctrl.pack(fill=tk.X, pady=(0, 6))
        self.start_btn = ttk.Button(ctrl, text='开始转换', command=self._start)
        self.start_btn.pack(side=tk.LEFT, padx=4)
        self.stop_btn = ttk.Button(ctrl, text='停止', command=self._stop, state=tk.DISABLED)
        self.stop_btn.pack(side=tk.LEFT, padx=4)
        self.progress = ttk.Progressbar(ctrl, orient=tk.HORIZONTAL, mode='determinate')
        self.progress.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=10)
        self.progress_label = ttk.Label(ctrl, text='0/0')
        self.progress_label.pack(side=tk.LEFT)

        # ── 日志面板 ──
        log_frame = ttk.LabelFrame(main, text='日志', padding=4)
        log_frame.pack(fill=tk.BOTH, expand=True)
        filter_bar = ttk.Frame(log_frame)
        filter_bar.pack(fill=tk.X)
        self.log_panel = LogPanel(log_frame, None)
        self.logger = LeveledLogger(
            append_callback=self.log_panel.append_entry,
            refresh_callback=self.log_panel.refresh,
            schedule_mainthread=lambda fn: self.after(0, fn))
        self.log_panel.logger = self.logger
        for level in ('info', 'warning', 'error', 'critical'):
            var = tk.BooleanVar(value=True)
            ttk.Checkbutton(filter_bar, text=level, variable=var,
                            command=lambda l=level, v=var:
                            self.logger.set_level_visible(l, v.get())
                            ).pack(side=tk.LEFT, padx=4)
        ttk.Button(filter_bar, text='清空日志', command=self.logger.clear).pack(side=tk.RIGHT)
        ttk.Button(filter_bar, text='清除记忆', command=self._clear_memory).pack(side=tk.RIGHT, padx=6)
        self.log_panel.pack(fill=tk.BOTH, expand=True)

        # ── 状态栏 ──
        self.status_var = tk.StringVar(value='就绪')
        ttk.Label(main, textvariable=self.status_var, relief=tk.SUNKEN,
                  anchor=tk.W).pack(fill=tk.X, pady=(4, 0))

        self.runner = None
        self.protocol('WM_DELETE_WINDOW', self._on_close)
        self.logger.info('程序启动完成，支持 Google / OPPO / vivo 动态照片互转')

    # ---- 事件 ----

    def _browse_out(self):
        d = filedialog.askdirectory(title='选择输出目录')
        if d:
            self.out_var.set(d)

    def _target_name(self):
        return self._disp_to_name.get(self.target_combo.get(), TARGETS[0][0])

    def _on_target_change(self, event=None):
        """*MP.jpg 命名选项仅 Google 格式可用：其他格式强制取消勾选并禁用，
        切回 Google 时恢复用户之前的勾选状态。"""
        if self._target_name() == 'google':
            if self._mp_suffix_forced:
                self.mp_suffix_var.set(self._mp_suffix_saved)
                self._mp_suffix_forced = False
            self.mp_suffix_cb.config(state=tk.NORMAL)
        else:
            if not self._mp_suffix_forced:
                self._mp_suffix_saved = self.mp_suffix_var.get()
                self.mp_suffix_var.set(False)
                self._mp_suffix_forced = True
            self.mp_suffix_cb.config(state=tk.DISABLED)

    def _resolve_out_dir(self, files):
        out = self.out_var.get().strip()
        if out:
            return out
        return str(Path(files[0]).parent / 'converted')

    def _start(self):
        files = self.selector.get_files()
        if not files:
            messagebox.showwarning('提示', '请先添加或扫描动态照片文件')
            return
        out_dir = self._resolve_out_dir(files)
        target = self._target_name()
        options = {'google_mp_suffix': self.mp_suffix_var.get()}
        self.progress.config(maximum=len(files), value=0)
        self.progress_label.config(text=f'0/{len(files)}')
        self.runner = QueueRunner(
            files, target, out_dir, options, self.logger.log,
            on_item=lambda *a: self.after(0, lambda: None),
            on_progress=lambda done, total: self.after(
                0, lambda: self._on_progress(done, total)),
            on_all_done=lambda ok, fail, cancelled: self.after(
                0, lambda: self._on_all_done(ok, fail, cancelled)))
        self.runner.start()
        self.start_btn.config(state=tk.DISABLED)
        self.stop_btn.config(state=tk.NORMAL)
        self.status_var.set(f'正在转换 → {self.target_combo.get()} ...')
        self.logger.info(f'队列启动：{len(files)} 个文件 → {self.target_combo.get()}，输出到 {out_dir}', '队列')

    def _stop(self):
        if self.runner:
            self.runner.stop()
            self.status_var.set('正在停止...')
            self.logger.warning('用户请求停止队列', '队列')

    def _on_progress(self, done, total):
        self.progress.config(value=done)
        self.progress_label.config(text=f'{done}/{total}')

    def _on_all_done(self, ok, fail, cancelled):
        self.start_btn.config(state=tk.NORMAL)
        self.stop_btn.config(state=tk.DISABLED)
        text = f'{"已取消。" if cancelled else "完成。"} 成功 {ok} 个，失败 {fail} 个'
        self.status_var.set(text)
        self.logger.info(text, '队列')
        if fail == 0 and not cancelled:
            messagebox.showinfo('完成', f'全部 {ok} 个文件转换成功！')

    def _clear_memory(self):
        if messagebox.askyesno('清除记忆', '确定要删除保存的窗口位置与设置吗？'):
            _delete_config()
            self.logger.info('配置记忆已清除')

    def _on_close(self):
        self.cfg['geometry'] = self.geometry()
        self.cfg['target'] = self._target_name()
        self.cfg['out_dir'] = self.out_var.get()
        # 非 Google 目标时变量被强制为 False，需保存用户真实意图值
        self.cfg['google_mp_suffix'] = (self.mp_suffix_var.get()
                                        if self._target_name() == 'google'
                                        else self._mp_suffix_saved)
        _save_config(self.cfg)
        if self.runner:
            self.runner.stop()
        self.destroy()


# ---------------------------------------------------------------- CLI

def run_cli(argv):
    parser = argparse.ArgumentParser(description='动态照片格式转换（CLI）')
    parser.add_argument('--cli', action='store_true', help='CLI 模式')
    parser.add_argument('--to', required=True, choices=[n for n, _ in TARGETS],
                        help='目标格式')
    parser.add_argument('--out', default='', help='输出目录')
    parser.add_argument('--no-mp-suffix', action='store_true',
                        help='Google 输出不追加 _MP 命名')
    parser.add_argument('files', nargs='+', help='输入文件（JPG）')
    args = parser.parse_args(argv)

    def log(level, msg, source=''):
        print(f'[{level}][{source}] {msg}')

    from core.convert import convert_file, ConvertError
    ok = fail = 0
    for path in args.files:
        out_dir = args.out or str(Path(path).parent / 'converted')
        try:
            outputs = convert_file(path, args.to, out_dir, log,
                                   {'google_mp_suffix': not args.no_mp_suffix})
            ok += 1
            for o in outputs:
                print(f'  -> {o}')
        except ConvertError as e:
            fail += 1
            log('error', f'{path}: {e}', 'CLI')
    print(f'完成：成功 {ok}，失败 {fail}')
    return 0 if fail == 0 else 1


if __name__ == '__main__':
    if '--cli' in sys.argv:
        sys.exit(run_cli([a for a in sys.argv[1:] if a != '--cli']))
    App().mainloop()
