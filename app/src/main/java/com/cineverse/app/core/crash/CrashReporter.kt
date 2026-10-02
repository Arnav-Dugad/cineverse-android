package com.cineverse.app.core.crash

import android.content.Context
import android.os.Build
import com.cineverse.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What happened, the last time the app died.
 *
 * Deliberately NOT Crashlytics. CineVerse is sideloaded to a handful of friends
 * from a GitHub release; wiring in a telemetry SDK would mean shipping their
 * device identifiers to a third party so that one person can read a stack trace.
 * This writes the trace to the phone's own storage, shows it in Settings, and
 * lets whoever hit it decide whether to send it. Nothing leaves the device
 * unless somebody taps a button.
 *
 * It is also honest about what it can do. An uncaught-exception handler runs
 * inside a process that is already failing, so it does exactly one thing — write
 * a file, synchronously — and then hands straight back to the handler it
 * replaced, so the system still gets its chance to report and to kill the
 * process properly. Anything cleverer (a network call, a coroutine, a dialog)
 * is a second crash inside the first.
 */
class CrashReporter(private val context: Context) {

    private val folder: File by lazy {
        File(context.filesDir, "crashes").apply { mkdirs() }
    }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(thread, error) }
            // Always hand on. Swallowing this leaves a zombie process with no
            // window, which is a far worse experience than a clean crash.
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(thread: Thread, error: Throwable) {
        val stamp = System.currentTimeMillis()
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val report = buildString {
            appendLine("CineVerse ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine(FORMAT.format(Date(stamp)))
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("thread: ${thread.name}")
            appendLine()
            append(trace)
        }
        File(folder, "$stamp.txt").writeText(report)
        prune()
    }

    /** The five newest, and no more: a crash loop must not fill the phone. */
    private fun prune() {
        val files = folder.listFiles()?.sortedByDescending { it.name } ?: return
        files.drop(5).forEach { runCatching { it.delete() } }
    }

    /** Newest first. */
    fun reports(): List<CrashReport> =
        folder.listFiles()
            ?.sortedByDescending { it.name }
            ?.mapNotNull { file ->
                val at = file.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
                val text = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
                CrashReport(
                    at = at,
                    // The first line of a stack trace is the only line most
                    // people will read, so it is what the list shows.
                    summary = text.lineSequence()
                        .firstOrNull { it.startsWith("java.") || it.startsWith("kotlin.") || it.contains("Exception") }
                        ?.trim()
                        ?.take(140)
                        .orEmpty()
                        .ifBlank { "Crash" },
                    text = text,
                )
            }
            .orEmpty()

    fun clear() {
        folder.listFiles()?.forEach { runCatching { it.delete() } }
    }

    /**
     * A GitHub issue, prefilled.
     *
     * Capped at 6000 characters because the whole thing goes in a URL, and a
     * truncated trace that opens beats a complete one that 414s.
     */
    fun issueUrl(report: CrashReport): String {
        val title = "Crash: ${report.summary.take(80)}"
        val body = buildString {
            appendLine("**What I was doing**")
            appendLine()
            appendLine("<!-- a line or two is plenty -->")
            appendLine()
            appendLine("**Report**")
            appendLine()
            appendLine("```")
            appendLine(report.text.take(6_000))
            appendLine("```")
        }
        return "https://github.com/$REPO/issues/new" +
            "?title=" + java.net.URLEncoder.encode(title, "UTF-8") +
            "&body=" + java.net.URLEncoder.encode(body, "UTF-8")
    }

    private companion object {
        const val REPO = "Arnav-Dugad/cineverse-android"
        val FORMAT = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
    }
}

data class CrashReport(val at: Long, val summary: String, val text: String)
