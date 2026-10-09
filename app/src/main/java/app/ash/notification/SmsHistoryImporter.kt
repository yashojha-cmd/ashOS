package app.ash.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import app.ash.common.IoDispatcher
import app.ash.common.TimeProvider
import app.ash.logging.AshLogger
import app.ash.workspace.WorkspaceGraphs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time import of past SMS from the phone's inbox (needs READ_SMS, asked only when the user
 * taps Import). Android keeps no notification history for apps, but it does keep SMS, so this is
 * how ashOS can fill in previous months.
 *
 * Privacy rules:
 *  - Only business senders are read (alphanumeric headers like "VM-HDFCBK", or 5–6 digit short
 *    codes). Messages from personal phone numbers are skipped without being parsed or stored.
 *  - OTPs are dropped by the normal pipeline. Nothing leaves the phone: ashOS has no internet access.
 *  - Results always go to the real account, never the demo.
 */
@Singleton
class SmsHistoryImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val graphs: WorkspaceGraphs,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    data class Result(val scanned: Int, val businessMessages: Int, val created: Int, val skippedPersonal: Int)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    suspend fun import(days: Int = DEFAULT_DAYS, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Result = withContext(io) {
        check(hasPermission()) { "READ_SMS not granted" }
        val since = time.nowMillis() - days * DAY
        val graph = graphs.real
        // SMS share the Messages source, so the allow-list and duplicate checks line up with live SMS notifications.
        graph.sources.recordSeen(SMS_SOURCE)
        graph.sources.setEnabled(SMS_SOURCE, true)

        data class Sms(val id: Long, val address: String, val body: String, val date: Long)
        val messages = mutableListOf<Sms>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                messages += Sms(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getLong(3))
            }
        }

        var business = 0
        var created = 0
        var personal = 0
        messages.forEachIndexed { index, sms ->
            if (!isBusinessSender(sms.address)) {
                personal++
            } else if (sms.body.isNotBlank()) {
                business++
                val result = graph.ingestor.ingest(SMS_SOURCE, "sms-${sms.id}", senderLabel(sms.address), sms.body, sms.date, historical = true)
                if (result == NotificationIngestor.Result.CREATED) created++
            }
            if (index % 20 == 0) onProgress(index, messages.size)
        }
        onProgress(messages.size, messages.size)
        graph.life.detectSubscriptions()
        AshLogger.i(TAG, "SMS import: scanned=${messages.size} business=$business created=$created personalSkipped=$personal days=$days")
        Result(messages.size, business, created, personal)
    }

    companion object {
        private const val TAG = "SmsImport"
        private const val DAY = 24L * 60 * 60 * 1000
        const val DEFAULT_DAYS = 90
        const val SMS_SOURCE = "com.google.android.apps.messaging"

        private val shortCode = Regex("""^\d{5,6}$""")

        /** Indian business SMS use DLT headers ("VM-HDFCBK", "AX-SBIINB-S") or short codes, never 10-digit numbers. */
        fun isBusinessSender(address: String): Boolean {
            val a = address.trim()
            if (a.isEmpty()) return false
            if (a.any { it.isLetter() }) return true
            return shortCode.matches(a)
        }

        /** "VM-HDFCBK-S" → "HDFCBK". */
        fun senderLabel(address: String): String {
            val parts = address.trim().uppercase().split('-').filter { it.isNotEmpty() }
            return when {
                parts.size >= 2 && parts[0].length == 2 -> parts[1]
                else -> parts.firstOrNull() ?: address
            }
        }
    }
}
