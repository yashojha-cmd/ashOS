package app.ash.logging

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import app.ash.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Owns the log files: setup at startup, reading for the viewer, export and clearing. */
@Singleton
class LogManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val logDir: File = File(context.filesDir, "logs")

    val fileSink: FileLogSink by lazy {
        FileLogSink(
            dir = logDir,
            header = "# ashOS ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE} | " +
                "${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        )
    }

    fun initialise() {
        if (BuildConfig.DEBUG) AshLogger.install(fileSink, LogcatSink()) else AshLogger.install(fileSink)
        AshLogger.minLevel = if (BuildConfig.DEBUG) AshLogger.Level.DEBUG else AshLogger.Level.INFO
        CrashHandler.install(fileSink)
    }

    /** Last [maxLines] lines across all log files, newest last. */
    fun readRecent(maxLines: Int = 500): List<String> {
        val lines = ArrayDeque<String>()
        for (file in fileSink.logFiles()) {
            file.forEachLine { line ->
                lines.addLast(line)
                if (lines.size > maxLines) lines.removeFirst()
            }
        }
        return lines.toList()
    }

    fun totalSizeBytes(): Long = fileSink.logFiles().sumOf { it.length() }

    fun clear() = fileSink.clear()

    /** Zips the logs into cache/share and returns a one-time share intent. User-initiated only. */
    fun buildExportIntent(): Intent {
        val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
        shareDir.listFiles()?.filter { it.name.startsWith("ash-logs-") }?.forEach { it.delete() }
        val zip = File(shareDir, "ash-logs-${System.currentTimeMillis()}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            for (file in fileSink.logFiles()) {
                out.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", zip)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
