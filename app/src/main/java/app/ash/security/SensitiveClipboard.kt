package app.ash.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.core.content.getSystemService

/**
 * Copies identifiers (policy, PAN, PNR) marked as sensitive so Android 13+ hides the preview,
 * and clears the clipboard after 60 seconds if it still holds the copied value.
 */
object SensitiveClipboard {
    private const val CLEAR_AFTER_MS = 60_000L

    fun copy(context: Context, label: String, value: String) {
        val clipboard = context.getSystemService<ClipboardManager>() ?: return
        val clip = ClipData.newPlainText(label, value).apply {
            description.extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                else putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)
        Handler(Looper.getMainLooper()).postDelayed({
            val current = runCatching { clipboard.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
            if (current == value) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
            }
        }, CLEAR_AFTER_MS)
    }
}
