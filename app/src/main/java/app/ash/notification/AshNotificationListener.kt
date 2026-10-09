package app.ash.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.ash.common.ApplicationScope
import app.ash.logging.AshLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Bound by Android only after the user grants notification access in system settings.
 * Event-driven: no polling and no foreground service. Work is handed to a background scope.
 */
@AndroidEntryPoint
class AshNotificationListener : NotificationListenerService() {

    @Inject lateinit var graphs: app.ash.workspace.WorkspaceGraphs

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected.value = true
        AshLogger.i(TAG, "Notification listener connected")
        // Pick up anything still in the shade when access was granted; history before that is not available.
        val active = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
        active.forEach(::handle)
    }

    override fun onListenerDisconnected() {
        connected.value = false
        AshLogger.w(TAG, "Notification listener disconnected")
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (sbn.isOngoing) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category == Notification.CATEGORY_CALL || n.category == Notification.CATEGORY_TRANSPORT ||
            n.category == Notification.CATEGORY_PROGRESS || n.category == Notification.CATEGORY_SERVICE
        ) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = buildString {
            val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            append(big ?: body.orEmpty())
            if (big == null) {
                extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { lines ->
                    if (lines.isNotEmpty()) append(" ").append(lines.joinToString(" "))
                }
            }
        }
        val posted = sbn.postTime
        val key = sbn.key
        // Real notifications always go to the real account, even while the demo account is open.
        scope.launch { graphs.real.ingestor.ingest(sbn.packageName, key, title, text, posted) }
    }

    companion object {
        private const val TAG = "Listener"
        private val connected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = connected
    }
}
