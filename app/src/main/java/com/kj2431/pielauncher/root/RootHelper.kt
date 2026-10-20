package com.kj2431.pielauncher.root

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Holds ONE persistent `su` shell for the whole process lifetime and runs every
 * command inside it. This means the superuser manager prompts/grants root only
 * once (at [prewarm], i.e. launcher activation) instead of on every command —
 * no repeated "granted Superuser rights" popups.
 *
 * All shell access is serialized through [lock]; root work is already off the
 * main thread (see ActionRunner), so blocking reads here are safe. A command
 * that doesn't finish within [TIMEOUT_S] (or a root prompt left unanswered for
 * [GRANT_TIMEOUT_S]) kills the shell, so one hung script can't block every
 * later root action; the next call simply opens a fresh shell.
 */
object RootHelper {

    private const val TAG = "RootHelper"
    private const val TIMEOUT_S = 15L
    private const val GRANT_TIMEOUT_S = 30L

    private val lock = Any()
    private var process: Process? = null
    private var stdin: DataOutputStream? = null
    private var stdout: BufferedReader? = null

    private val watchdog = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "root-watchdog").apply { isDaemon = true }
    }

    @Volatile private var available: Boolean? = null

    /** Open the shell ahead of time (e.g. when the service starts). */
    fun prewarm() = thread(name = "root-prewarm") { isAvailable() }

    /** True if a root shell is (or can be) opened. Result cached after first call. */
    fun isAvailable(): Boolean {
        available?.let { if (it && alive()) return true }
        synchronized(lock) {
            val ok = ensureOpen()
            available = ok
            return ok
        }
    }

    /** Run one or more commands in the persistent shell. Returns last exit == 0. */
    fun exec(vararg commands: String): Boolean = run(commands) != null

    /** Like [exec], but returns the commands' output, or null if the last one failed. */
    fun query(vararg commands: String): String? = run(commands)

    fun pressKey(keyevent: Int) = exec("input keyevent $keyevent")

    /** Close the shell (e.g. on service destroy). */
    fun shutdown() = synchronized(lock) { closeLocked(); available = null }

    // ---- internals (must hold lock) ----------------------------------------
    private fun alive(): Boolean = process?.isAlive == true

    private fun run(commands: Array<out String>): String? = synchronized(lock) {
        if (!ensureOpen()) return null
        val os = stdin ?: return null
        val reader = stdout ?: return null
        val marker = "PIE_DONE_" + System.nanoTime()
        val timer = killAfter(TIMEOUT_S)
        return try {
            commands.forEach { os.writeBytes(it + "\n") }
            // The marker must start its own line even if the output didn't end with one.
            os.writeBytes("__r=$?; echo; echo $marker \$__r\n")
            os.flush()
            val out = StringBuilder()
            var success = false
            var finished = false
            while (true) {
                val line = reader.readLine() ?: break          // shell died or timed out
                if (line.startsWith(marker)) { success = line.trim().endsWith(" 0"); finished = true; break }
                out.append(line).append('\n')
            }
            if (!finished) { Log.w(TAG, "shell ended before the command finished"); closeLocked() }
            if (success) out.toString().trimEnd('\n') else null
        } catch (e: Exception) {
            Log.w(TAG, "exec failed; closing shell", e); closeLocked(); null
        } finally {
            timer.cancel(false)
        }
    }

    private fun ensureOpen(): Boolean {
        if (alive()) return true
        closeLocked()
        return try {
            val p = ProcessBuilder("su").redirectErrorStream(true).start()
            val os = DataOutputStream(p.outputStream)
            val rd = BufferedReader(InputStreamReader(p.inputStream))
            process = p; stdin = os; stdout = rd
            // Verify we truly have root via a marker round-trip.
            val marker = "PIE_READY_" + System.nanoTime()
            val timer = killAfter(GRANT_TIMEOUT_S)
            var granted = false
            try {
                os.writeBytes("echo $marker\n"); os.flush()
                while (true) {
                    val line = rd.readLine() ?: break
                    if (line.contains(marker)) { granted = true; break }
                }
            } finally {
                timer.cancel(false)
            }
            if (!granted) { closeLocked(); false } else true
        } catch (e: Exception) {
            Log.w(TAG, "could not open su shell", e); closeLocked(); false
        }
    }

    /** Destroys the current shell after [seconds] unless cancelled, which unblocks a pending read. */
    private fun killAfter(seconds: Long): ScheduledFuture<*> {
        val p = process
        return watchdog.schedule({
            Log.w(TAG, "root shell timed out after ${seconds}s; killing it")
            p?.destroy()
        }, seconds, TimeUnit.SECONDS)
    }

    private fun closeLocked() {
        runCatching { stdin?.writeBytes("exit\n"); stdin?.flush() }
        runCatching { stdin?.close() }
        runCatching { stdout?.close() }
        runCatching { process?.destroy() }
        stdin = null; stdout = null; process = null
    }
}
