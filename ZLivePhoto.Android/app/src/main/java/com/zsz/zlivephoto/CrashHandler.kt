package com.zsz.zlivephoto

import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃处理器：把崩溃堆栈写到 /sdcard/Z-LivePhoto-Crash.log
 */
object CrashHandler : Thread.UncaughtExceptionHandler {
    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun install() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            pw.println("=== Z-LivePhoto-Converter Crash ===")
            pw.println("Time: ${dateFormat.format(Date())}")
            pw.println("Thread: ${t.name}")
            pw.println()
            e.printStackTrace(pw)
            pw.println()
            pw.println("=== Device Info ===")
            pw.println("SDK: ${android.os.Build.VERSION.SDK_INT}")
            pw.println("Release: ${android.os.Build.VERSION.RELEASE}")
            pw.println("Model: ${android.os.Build.MODEL}")
            pw.println("Manufacturer: ${android.os.Build.MANUFACTURER}")
            pw.println()

            val logFile = File(
                Environment.getExternalStorageDirectory(),
                "Z-LivePhoto-Crash.log"
            )
            logFile.appendText(sw.toString())
        } catch (_: Exception) { }

        previousHandler?.uncaughtException(t, e)
    }
}
