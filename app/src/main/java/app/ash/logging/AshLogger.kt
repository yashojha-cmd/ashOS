package app.ash.logging

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Single logging entry point for ASH. Nothing in the app calls android.util.Log directly.
 *
 * Every message is redacted unless [detailedParserLogging] is on, and only [sensitive] calls
 * honour that flag. Logs never leave the device unless the user taps Export in Diagnostics.
 */
object AshLogger {
    enum class Level(val code: Char) { DEBUG('D'), INFO('I'), WARN('W'), ERROR('E') }

    data class Entry(
        val timeMillis: Long,
        val level: Level,
        val tag: String,
        val thread: String,
        val message: String,
        val throwable: Throwable?,
    ) {
        fun format(): String {
            val time = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(timeMillis), java.time.ZoneId.systemDefault())
            val base = "${FORMATTER.format(time)} | ${level.code} | $tag | $thread | $message"
            return if (throwable == null) base else base + "\n" + throwable.stackTraceToString().let(Redactor::redact)
        }
    }

    fun interface Sink {
        fun write(entry: Entry)
    }

    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    @Volatile
    private var sinks: List<Sink> = emptyList()

    @Volatile
    var minLevel: Level = Level.DEBUG

    /** Toggled from Diagnostics; auto-expires (see SettingsRepository). */
    @Volatile
    var detailedParserLogging: Boolean = false

    fun install(vararg newSinks: Sink) {
        sinks = newSinks.toList()
    }

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message, null)
    fun w(tag: String, message: String, t: Throwable? = null) = log(Level.WARN, tag, message, t)
    fun e(tag: String, message: String, t: Throwable? = null) = log(Level.ERROR, tag, message, t)

    /** For raw notification/OCR text. Logged verbatim only while detailed parser logging is on. */
    fun sensitive(tag: String, message: String) {
        if (detailedParserLogging) write(Level.DEBUG, tag, "[detailed] $message", null)
        else log(Level.DEBUG, tag, message, null)
    }

    private fun log(level: Level, tag: String, message: String, t: Throwable?) =
        write(level, tag, Redactor.redact(message), t)

    private fun write(level: Level, tag: String, message: String, t: Throwable?) {
        if (level.ordinal < minLevel.ordinal) return
        val entry = Entry(System.currentTimeMillis(), level, tag, Thread.currentThread().name, message, t)
        for (sink in sinks) {
            try {
                sink.write(entry)
            } catch (_: Throwable) {
                // A broken sink must never crash the app.
            }
        }
    }
}
