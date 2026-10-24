package com.kj2431.pielauncher.service

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny on-device log for problems that are hard to reproduce: files/diag.log,
 * last [MAX_LINES] lines, read with root or `run-as`. Never throws.
 */
object DiagLog {

    private const val MAX_LINES = 300

    @Synchronized
    fun add(ctx: Context, message: String) {
        runCatching {
            val f = File(ctx.applicationContext.filesDir, "diag.log")
            val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())
            val lines = (if (f.exists()) f.readLines() else emptyList()) + "$time $message"
            f.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
        }
    }
}
