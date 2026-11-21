package com.zsz.zlivephoto.sponsor

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.zsz.zlivephoto.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** 赞助收款链接（二维码由这些链接在程序内即时生成，不打包图片）。 */
object SponsorLinks {
    /** 微信收款码（用户提供，微信扫一扫 / 长按识别均可）。 */
    const val WECHAT = "wxp://f2f0jxTxKPAA_gbxBfWEiVO5BkK-275g2hUBU4cdDJNiwrZ5EIsM0hhZ2SA5GK2RXYn7"

    /** 支付宝收款链接：二维码内容即此链接，「直接跳转」也用它唤起支付宝。 */
    const val ALIPAY = "https://qr.alipay.com/fkx11041w0pjbbnivcyli91"
}

/** 赞助入口 / 启动提醒的本地状态（SharedPreferences，卸载即清除）。 */
object SponsorState {
    private const val PREFS = "sponsor"
    private const val KEY_LAUNCHES = "launches"
    private const val KEY_SUPPRESSED = "suppressed"

    /** 启动间隔：每 N 次冷启动提醒一次（第 1 次启动不提醒）。 */
    private const val REMIND_EVERY = 5

    /** 本次启动是否该弹提醒。只在 [consumePending] 里读取一次，避免旋转屏幕重复弹。 */
    @Volatile
    private var pending = false

    fun onLaunch(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val launches = p.getInt(KEY_LAUNCHES, 0) + 1
        p.edit().putInt(KEY_LAUNCHES, launches).apply()
        pending = launches > 1 && launches % REMIND_EVERY == 0 && !p.getBoolean(KEY_SUPPRESSED, false)
    }

    /** 取走「本次启动需要提醒」标记：返回 true 后标记即清除，同一次启动不会再弹。 */
    fun consumePending(): Boolean {
        val v = pending
        pending = false
        return v
    }

    fun suppress(context: Context) {
        pending = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SUPPRESSED, true).apply()
    }
}

/**
 * 官方版本校验：APK 签名证书 SHA-256 与构建机 keystore 一致才认为「官方版本」。
 *
 * 期望值由 build.gradle.kts 在构建时从本地 signing.properties 指向的 keystore 读出并注入
 * [BuildConfig.SPONSOR_CERT_SHA256]；keystore 与 signing.properties 均不入库，
 * 因此别人用自己证书打的包、debug 包都会判定为非官方版本，赞助入口只做提示。
 */
object OfficialBuild {
    private val expected: String = BuildConfig.SPONSOR_CERT_SHA256.trim()

    fun isOfficial(context: Context): Boolean {
        if (expected.isEmpty()) return false
        val actual = runCatching { currentCertSha256(context) }.getOrDefault("")
        return actual.isNotEmpty() && actual.equals(expected, ignoreCase = true)
    }

    private fun currentCertSha256(context: Context): String {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val sig = signatures?.firstOrNull() ?: return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
        return digest.joinToString("") { "%02X".format(it) }
    }
}

/** 二维码生成（zxing，纯离线；返回带白边留白的方形位图）。 */
object QrCodes {
    fun encode(content: String, sizePx: Int = 720): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val row = y * sizePx
            for (x in 0 until sizePx) {
                pixels[row + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }

    /** 保存到系统相册（Pictures/赞助二维码）。返回是否成功。 */
    fun saveToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean = runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/赞助二维码")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            // Android 9-：写入公共 Pictures 目录后通知相册刷新
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "赞助二维码"
            ).apply { mkdirs() }
            val file = File(dir, "$fileName.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        }
        true
    }.getOrDefault(false)
}
