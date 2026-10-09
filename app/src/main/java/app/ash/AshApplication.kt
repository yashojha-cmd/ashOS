package app.ash

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.ash.common.ApplicationScope
import app.ash.data.repository.SettingsRepository
import app.ash.document.VaultStorage
import app.ash.logging.AshLogger
import app.ash.logging.LogManager
import app.ash.reminder.MaintenanceWorker
import app.ash.reminder.NotificationChannels
import app.ash.security.AppLockManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AshApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var logManager: LogManager
    @Inject lateinit var appLock: AppLockManager
    @Inject lateinit var vault: VaultStorage
    @Inject lateinit var settings: SettingsRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.INFO else android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        logManager.initialise()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskWrites().detectNetwork().penaltyLog().build())
        }
        AshLogger.i(TAG, "ASH ${BuildConfig.VERSION_NAME} starting")
        NotificationChannels.create(this)
        appLock.start()
        scope.launch(Dispatchers.IO) {
            vault.clearViewCache()
            val s = settings.current()
            AshLogger.detailedParserLogging = s.detailedLogsUntil > System.currentTimeMillis()
            MaintenanceWorker.schedule(this@AshApplication)
        }
    }

    private companion object {
        const val TAG = "App"
    }
}
