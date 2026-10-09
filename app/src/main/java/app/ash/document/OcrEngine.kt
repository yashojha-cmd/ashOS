package app.ash.document

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** OCR abstraction so the engine can be swapped (e.g. Tesseract) without touching callers. */
interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): String
}

/**
 * ML Kit text recognition using the bundled (on-device) Latin and Devanagari models.
 * The models ship inside the APK; with no INTERNET permission ML Kit cannot send anything out.
 */
@Singleton
class MlKitOcrEngine @Inject constructor() : OcrEngine {
    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val devanagari by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }

    override suspend fun recognize(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val latinText = latin.process(image).await().text
        // Devanagari pass catches Hindi on Aadhaar/PAN; skip it when the Latin pass was already rich.
        val hindiText = if (latinText.length < 400) runCatching { devanagari.process(image).await().text }.getOrDefault("") else ""
        val seen = latinText.lines().map { it.trim() }.toMutableSet()
        val extra = hindiText.lines().map { it.trim() }.filter { it.isNotEmpty() && seen.add(it) }
        return (latinText.lines() + extra).joinToString("\n").trim()
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
