package app.ash.logging

import android.util.Log

/**
 * Mirrors ashOS logs to logcat (debug and staging builds only) so they can be read with
 * `adb logcat -s ashOS`. Uses Log.println, which R8's release rules do not strip.
 * Messages are already redacted by AshLogger.
 */
class LogcatSink : AshLogger.Sink {
    override fun write(entry: AshLogger.Entry) {
        val priority = when (entry.level) {
            AshLogger.Level.DEBUG -> Log.DEBUG
            AshLogger.Level.INFO -> Log.INFO
            AshLogger.Level.WARN -> Log.WARN
            AshLogger.Level.ERROR -> Log.ERROR
        }
        val body = entry.throwable?.let { "${entry.message}\n${Log.getStackTraceString(it)}" } ?: entry.message
        Log.println(priority, TAG, "[${entry.tag}] $body")
    }

    private companion object {
        const val TAG = "ashOS"
    }
}
