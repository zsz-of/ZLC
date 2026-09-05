package com.zsz.zlivephoto.ui.picker

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/** 相册（按 MediaStore bucket 分组） */
data class AlbumInfo(
    val bucketId: Long,
    val name: String,
    val count: Int,
    /** 最新一张图片的 id（相册封面） */
    val coverId: Long,
    val latestTaken: Long,
    /** 合成模式：封面是否为视频（决定封面缩略图用的 collection） */
    val coverIsVideo: Boolean = false
)

/** 相册内一张图片（导入时按 path 直读原文件，完整保留 EXIF） */
data class MediaItem(
    val id: Long,
    val path: String,
    val name: String,
    val bucketId: Long,
    /** 拍摄时间（ms）；部分文件为 0，排序时回退用 dateModified */
    val dateTaken: Long,
    /** 修改时间（秒） */
    val dateModified: Long,
    val size: Long,
    /** 识别出的动态照片格式（plugin.name），未识别为 null */
    val formatName: String? = null,
    val formatDisplay: String? = null,
    /** 合成模式：视频项标记（照片为 false） */
    val isVideo: Boolean = false,
    /** 合成模式：视频时长（ms），照片为 0 */
    val durationMs: Long = 0
) {
    val uri: Uri
        get() = ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

    /** 校验 key：id+size+modified（视频加类型前缀避免与同 id 图片混淆）；文件被编辑（大小/时间变化）也会被视为新项触发重扫 */
    val key: String get() = "${if (isVideo) "v" else "i"}$id:$size:$dateModified"

    /** 排序用的有效时间 */
    val sortTime: Long get() = if (dateTaken > 0) dateTaken else dateModified * 1000L
}

/** MediaStore 图片库查询（内置选择器数据源） */
class MediaRepo(private val resolver: ContentResolver) {

    /** 查询全部图片并按相册（bucket）分组；DCIM/Camera 置顶，其余按最新照片时间降序。
     *  includeVideos=true（合成模式）：视频统计并入同名相册，图片优先作封面。 */
    fun queryAlbums(includeVideos: Boolean = false): List<AlbumInfo> {
        val albums = HashMap<Long, AlbumBuilder>()
        try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.BUCKET_ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_MODIFIED
                ),
                null, null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val iPath = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val iBucketName = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val iModified = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                while (c.moveToNext()) {
                    val bucketId = c.getLong(iBucket)
                    if (bucketId == 0L) continue
                    val taken = c.getLong(iTaken)
                    val modified = c.getLong(iModified)
                    val effTaken = if (taken > 0) taken else modified * 1000L
                    val b = albums.getOrPut(bucketId) {
                        AlbumBuilder(bucketId, c.getString(iBucketName) ?: "未命名")
                    }
                    b.count++
                    // 记录路径样本用于 DCIM/Camera 置顶判断
                    if (b.samplePath == null) b.samplePath = c.getString(iPath) ?: ""
                    // 游标按时间降序，首个即最新
                    if (b.coverId == 0L) { b.coverId = c.getLong(iId); b.latestTaken = effTaken }
                }
            }
            // 合成模式：视频并入相册统计（图片封面优先，视频-only 相册用视频作封面）
            if (includeVideos) {
                resolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DATA,
                        MediaStore.Video.Media.BUCKET_ID,
                        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                        MediaStore.Video.Media.DATE_TAKEN,
                        MediaStore.Video.Media.DATE_MODIFIED
                    ),
                    null, null,
                    MediaStore.Video.Media.DATE_TAKEN + " DESC"
                )?.use { c ->
                    val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val iBucket = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                    val iBucketName = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    val iTaken = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
                    val iModified = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                    while (c.moveToNext()) {
                        val bucketId = c.getLong(iBucket)
                        if (bucketId == 0L) continue
                        val taken = c.getLong(iTaken)
                        val modified = c.getLong(iModified)
                        val effTaken = if (taken > 0) taken else modified * 1000L
                        val b = albums.getOrPut(bucketId) {
                            AlbumBuilder(bucketId, c.getString(iBucketName) ?: "未命名")
                        }
                        b.count++
                        if (b.coverId == 0L) {
                            b.coverId = c.getLong(iId)
                            b.latestTaken = effTaken
                            b.coverIsVideo = true
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return albums.values
            .filter { it.coverId != 0L }
            .map { AlbumInfo(it.bucketId, it.name, it.count, it.coverId, it.latestTaken, it.coverIsVideo) }
            .sortedWith(
                compareByDescending<AlbumInfo> { it.isCameraAlbum(albums[it.bucketId]?.samplePath) }
                    .thenByDescending { it.latestTaken }
            )
    }

    /** 是否为 DCIM/Camera 相册（路径含 /DCIM/Camera/，OEM 命名各异，按路径判断最稳） */
    private fun AlbumInfo.isCameraAlbum(samplePath: String?): Boolean {
        val p = samplePath ?: return false
        return p.contains("/DCIM/Camera/", ignoreCase = true) ||
               p.contains("/DCIM/相册/", ignoreCase = true) ||
               p.contains("/DCIM/Camera", ignoreCase = true)
    }

    /** 查询指定相册的全部图片，按拍摄时间降序。
     *  includeVideos=true（合成模式）：并入该相册的视频（含时长），统一按时间降序。 */
    fun queryAlbumImages(bucketId: Long, includeVideos: Boolean = false): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.BUCKET_ID,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.SIZE
                ),
                MediaStore.Images.Media.BUCKET_ID + "=?",
                arrayOf(bucketId.toString()),
                MediaStore.Images.Media.DATE_TAKEN + " DESC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val iPath = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val iModified = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                while (c.moveToNext()) {
                    val path = c.getString(iPath) ?: continue
                    val name = c.getString(iName) ?: path.substringAfterLast('/')
                    result.add(MediaItem(
                        id = c.getLong(iId),
                        path = path,
                        name = name,
                        bucketId = c.getLong(iBucket),
                        dateTaken = c.getLong(iTaken),
                        dateModified = c.getLong(iModified),
                        size = c.getLong(iSize)
                    ))
                }
            }
            if (includeVideos) {
                resolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DATA,
                        MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.BUCKET_ID,
                        MediaStore.Video.Media.DATE_TAKEN,
                        MediaStore.Video.Media.DATE_MODIFIED,
                        MediaStore.Video.Media.SIZE,
                        MediaStore.Video.Media.DURATION
                    ),
                    MediaStore.Video.Media.BUCKET_ID + "=?",
                    arrayOf(bucketId.toString()),
                    MediaStore.Video.Media.DATE_TAKEN + " DESC"
                )?.use { c ->
                    val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val iPath = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val iBucket = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                    val iTaken = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
                    val iModified = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                    val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    while (c.moveToNext()) {
                        val path = c.getString(iPath) ?: continue
                        val name = c.getString(iName) ?: path.substringAfterLast('/')
                        result.add(MediaItem(
                            id = c.getLong(iId),
                            path = path,
                            name = name,
                            bucketId = c.getLong(iBucket),
                            dateTaken = c.getLong(iTaken),
                            dateModified = c.getLong(iModified),
                            size = c.getLong(iSize),
                            isVideo = true,
                            durationMs = c.getLong(iDur)
                        ))
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result.sortedByDescending { it.sortTime }
    }

    private class AlbumBuilder(
        val bucketId: Long,
        val name: String,
        var count: Int = 0,
        var coverId: Long = 0L,
        var latestTaken: Long = 0L,
        var samplePath: String? = null,
        var coverIsVideo: Boolean = false
    )
}
