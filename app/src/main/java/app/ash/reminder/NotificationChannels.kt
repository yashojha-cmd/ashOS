package app.ash.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import app.ash.R

object NotificationChannels {
    const val REMINDERS = "reminders"
    const val REVIEW = "review"

    fun create(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_reminders_desc)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(REVIEW, context.getString(R.string.channel_review), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_review_desc)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            ),
        )
    }
}
