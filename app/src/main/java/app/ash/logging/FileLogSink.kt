package app.ash.logging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.Writer

/**
 * Writes log lines to app-private storage: filesDir/logs/ash-<timestamp>.log.
 * Files rotate at [maxFileBytes] and only the newest [maxFiles] are kept (5 x 1 MB by default).
 * Writing happens on a single background coroutine so callers never block on disk I/O.
 */
class FileLogSink(
    private val dir: File,
    private val header: String,
    private val maxFileBytes: Long = 1_000_000,
    private val maxFiles: Int = 5,
) : AshLogger.Sink {

    private val channel = Channel<AshLogger.Entry>(capacity = 2048, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val lock = Any()
    private var current: File? = null
    private var writer: Writer? = null

    init {
        dir.mkdirs()
        scope.launch {
            for (entry in channel) {
                synchronized(lock) {
                    writeLine(entry.format())
                    // Drain whatever queued up meanwhile, then flush once per burst.
                    var next = channel.tryReceive().getOrNull()
                    while (next != null) {
                        writeLine(next.format())
                        next = channel.tryReceive().getOrNull()
                    }
                    writer?.flush()
                }
            }
        }
    }

    override fun write(entry: AshLogger.Entry) {
        channel.trySend(entry)
    }

    /** Synchronous write used by the crash handler, where the process is about to die. */
    fun writeBlocking(entry: AshLogger.Entry) = synchronized(lock) {
        writeLine(entry.format())
        writer?.flush()
    }

    fun logFiles(): List<File> =
        dir.listFiles { f -> f.name.endsWith(".log") }?.sortedBy { it.lastModified() } ?: emptyList()

    fun clear() = synchronized(lock) {
        writer?.close()
        writer = null
        current = null
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun writeLine(line: String) {
        val w = ensureWriter()
        w.write(line)
        w.write("\n")
        if ((current?.length() ?: 0) > maxFileBytes) {
            w.flush()
            w.close()
            writer = null
            current = null
            prune()
        }
    }

    private fun ensureWriter(): Writer {
        writer?.let { return it }
        val file = File(dir, "ash-${System.currentTimeMillis()}.log")
        val w = FileWriter(file, true).buffered()
        w.write(header)
        w.write("\n")
        current = file
        writer = w
        prune()
        return w
    }

    private fun prune() {
        val files = dir.listFiles { f -> f.name.startsWith("ash-") && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() } ?: return
        files.drop(maxFiles).forEach { it.delete() }
    }
}
