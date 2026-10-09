package app.ash.logging

import android.util.Log

/** Mirrors logs to logcat. Installed in debug builds only. */
class LogcatSink : AshLogger.Sink {
    override fun write(entry: AshLogger.Entry) {
        val tag = "ASH/${entry.tag}".take(23)
        when (entry.level) {
            AshLogger.Level.DEBUG -> Log.d(tag, entry.message, entry.throwable)
            AshLogger.Level.INFO -> Log.i(tag, entry.message, entry.throwable)
            AshLogger.Level.WARN -> Log.w(tag, entry.message, entry.throwable)
            AshLogger.Level.ERROR -> Log.e(tag, entry.message, entry.throwable)
        }
    }
}
