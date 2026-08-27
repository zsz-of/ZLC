package com.zsz.zlivephoto.ui.picker

import androidx.compose.runtime.mutableStateListOf
import com.zsz.zlivephoto.core.QuickClassify
import com.zsz.zlivephoto.core.formats.FormatRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * 相册扫描引擎：多线程识别动态照片。
 *
 * 会话化扫描策略（用户需求）：
 * - 不主动监听媒体库变化；仅在打开内置选择器时开启新会话（newSession 清空全部状态）
 * - 每个相册一次会话内只扫一次：扫完（completed）后切走再切回不重扫
 * - 未扫完就切走：立即中断；切回时清空旧进度从头重扫
 * - 转到新相册：立即开始该相册的扫描
 *
 * 无磁盘持久化：扫描结果仅存内存，不产生任何本地存储。
 */
class AlbumScanner(private val repo: MediaRepo) {

    class ScanState {
        /** 已识别为动态照片的列表（Compose 状态，UI 直接观察） */
        val results = mutableStateListOf<MediaItem>()
        /** 当前相册已知全部项 */
        val known = ConcurrentHashMap<String, MediaItem>()
        /** 待扫描队列 */
        val pending = ConcurrentLinkedQueue<MediaItem>()
        /** 已完成扫描的 key 集合 */
        val scanned = ConcurrentHashMap.newKeySet<String>()
        /** 已扫描数（原子，UI 轮询显示） */
        val doneCount = AtomicInteger(0)
        @Volatile var total = 0
        @Volatile var running = false
        /** 本次会话内已完成全量扫描（关闭选择器前不再触发重扫） */
        @Volatile var completed = false
        /**
         * 本相册唯一的扫描 job：覆盖 reset → diff → workers 全流程。
         * workers 作为它的子协程启动，cancel 它即可连带取消所有扫描线程，
         * 不会产生孤儿协程（旧版 enterJob/job 双轨模型在 leave 时可能漏取消）。
         */
        var job: Job? = null

        /**
         * 中断后的相册切回时使用：清空旧进度，从头重扫。
         * 注意：results 是 SnapshotStateList，clear 必须在 Main 线程执行——
         * 与主线程 LazyVerticalGrid 的迭代并发会抛 ConcurrentModificationException。
         */
        fun resetProgress() {
            results.clear()
            known.clear()
            pending.clear()
            scanned.clear()
            doneCount.set(0)
            total = 0
        }
    }

    private val states = ConcurrentHashMap<Long, ScanState>()
    @Volatile private var activeBucket: Long? = null

    fun stateOf(bucketId: Long): ScanState = states.getOrPut(bucketId) { ScanState() }

    /** 打开内置选择器时开启新会话：中断并清空全部相册的扫描状态 */
    fun newSession() {
        states.values.forEach { it.job?.cancel() }
        states.clear()
        activeBucket = null
    }

    /**
     * 进入相册：
     * - 已扫完（completed）→ 直接展示已有结果，不再扫描
     * - 从未扫过 / 上次中断 → 清空旧进度，从头全量扫描
     *
     * 全流程（reset → diff → workers）在单一 job 内完成，
     * leave/newSession/再次 enter 取消该 job 即可干净终止所有扫描线程。
     */
    fun enter(bucketId: Long, scope: CoroutineScope) {
        activeBucket = bucketId
        val st = stateOf(bucketId)
        if (st.completed) return
        st.job?.cancel()
        st.job = scope.launch(Dispatchers.IO) {
            // SnapshotStateList 的 clear 必须在 Main 线程（与网格迭代并发会 CME 崩溃）；
            // diffRefresh 的 MediaStore 查询仍在 IO 线程执行
            withContext(Dispatchers.Main) { st.resetProgress() }
            if (!isActive) return@launch
            if (diffRefresh(st, bucketId)) {
                st.running = true
                // workers 作为本 job 的子协程启动，随父协程取消而终止
                val workers = (1..SCAN_THREADS).map {
                    launch { scanWorker(st) }
                }
                workers.forEach { it.join() }
                if (st.pending.isEmpty()) {
                    st.running = false
                    st.completed = true
                }
            }
        }
    }

    /** 离开相册：立即中断扫描（进度保留在内存，切回时由 enter 重扫） */
    fun leave(bucketId: Long) {
        states[bucketId]?.job?.cancel()
        if (activeBucket == bucketId) activeBucket = null
    }

    /** 单个扫描线程：粗筛 → 精判，扫到一张即在 Main 线程追加显示 */
    private suspend fun scanWorker(st: ScanState) {
        while (currentCoroutineContext().isActive) {
            val item = st.pending.poll() ?: break
            if (!st.known.containsKey(item.key)) continue // 队列残留已被删除的项
            if (QuickClassify.sniff(item.path)) {
                try {
                    val (plugin, score) = FormatRegistry.detectBest(item.path)
                    if (plugin != null && score >= 50) {
                        val done = item.copy(
                            formatName = plugin.name,
                            formatDisplay = plugin.display
                        )
                        withContext(Dispatchers.Main) {
                            if (st.known.containsKey(done.key) &&
                                st.results.none { it.id == done.id }
                            ) {
                                st.results.add(done)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            st.scanned.add(item.key)
            st.doneCount.set(st.scanned.size)
        }
    }

    /**
     * 查询相册最新文件列表，填充待扫队列。
     * 返回是否有待扫项。
     */
    private fun diffRefresh(st: ScanState, bucketId: Long): Boolean {
        val current = repo.queryAlbumImages(bucketId)
        for (item in current) {
            st.known[item.key] = item
            st.pending.add(item)
        }
        st.total = current.size
        return st.pending.isNotEmpty()
    }

    companion object {
        private const val SCAN_THREADS = 4
    }
}
