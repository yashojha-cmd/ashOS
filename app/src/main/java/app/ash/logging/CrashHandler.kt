package app.ash.logging

/**
 * Records uncaught exceptions to the local log before the process dies, then defers to the
 * platform handler. Nothing is uploaded; there is no crash-reporting SDK in ASH.
 */
class CrashHandler(
    private val sink: FileLogSink,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            sink.writeBlocking(
                AshLogger.Entry(
                    timeMillis = System.currentTimeMillis(),
                    level = AshLogger.Level.ERROR,
                    tag = "Crash",
                    thread = thread.name,
                    message = "FATAL " + Redactor.redact(throwable.toString()),
                    throwable = throwable,
                ),
            )
        } catch (_: Throwable) {
        }
        previous?.uncaughtException(thread, throwable)
    }

    companion object {
        fun install(sink: FileLogSink) {
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(sink, prev))
        }
    }
}
