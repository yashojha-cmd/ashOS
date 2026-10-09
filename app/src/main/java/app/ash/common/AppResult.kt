package app.ash.common

/** Explicit success/failure so errors are surfaced to the UI instead of being swallowed. */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError, val cause: Throwable? = null) : AppResult<Nothing>
}

enum class AppError {
    NOTIFICATION_ACCESS_DISABLED,
    OCR_LOW_CONFIDENCE,
    OCR_FAILED,
    FILE_READ_FAILED,
    FILE_TOO_LARGE,
    UNSUPPORTED_FILE,
    ENCRYPTION_FAILED,
    WRONG_PASSWORD,
    CORRUPT_BACKUP,
    STORAGE_FULL,
    UNKNOWN,
}

inline fun <T> runCatchingApp(error: AppError = AppError.UNKNOWN, block: () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(error, e)
    }
