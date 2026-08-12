# -*- coding: utf-8 -*-
"""后台队列执行器：顺序处理文件列表，支持取消。"""
import threading

from .convert import ConvertError, convert_file


class QueueRunner:
    """顺序转换队列。

    回调（均在工作线程触发，调用方负责调度到 UI 线程）：
    - on_item(index, path, ok, message, outputs)
    - on_progress(done, total)
    - on_all_done(success_count, fail_count, cancelled)
    """

    def __init__(self, files, target, out_dir, options, log,
                 on_item=None, on_progress=None, on_all_done=None):
        self.files = list(files)
        self.target = target
        self.out_dir = out_dir
        self.options = options or {}
        self.log = log
        self.on_item = on_item or (lambda *a: None)
        self.on_progress = on_progress or (lambda *a: None)
        self.on_all_done = on_all_done or (lambda *a: None)
        self._stop = threading.Event()
        self._thread = None

    def start(self):
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self):
        self._stop.set()

    def is_running(self) -> bool:
        return bool(self._thread and self._thread.is_alive())

    def _run(self):
        total = len(self.files)
        ok_count = fail_count = 0
        cancelled = False
        for idx, path in enumerate(self.files):
            if self._stop.is_set():
                cancelled = True
                self.log('warning', f'队列已取消，剩余 {total - idx} 个未处理', '队列')
                break
            self.log('info', f'[{idx + 1}/{total}] 处理：{path}', '队列')
            try:
                outputs = convert_file(path, self.target, self.out_dir,
                                       self.log, self.options)
                ok_count += 1
                self.on_item(idx, path, True, f'成功（{len(outputs)} 个文件）', outputs)
            except ConvertError as e:
                fail_count += 1
                self.log('error', f'[{idx + 1}/{total}] 失败：{e}', '队列')
                self.on_item(idx, path, False, str(e), [])
            except Exception as e:  # 未预期异常：记录并继续队列
                fail_count += 1
                self.log('critical', f'[{idx + 1}/{total}] 异常：{type(e).__name__}: {e}', '队列')
                self.on_item(idx, path, False, f'{type(e).__name__}: {e}', [])
            self.on_progress(idx + 1, total)
        self.on_all_done(ok_count, fail_count, cancelled)
