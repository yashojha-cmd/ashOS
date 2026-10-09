package app.ash.workspace

import android.app.Activity
import android.content.Context
import android.content.Intent
import app.ash.BuildConfig
import app.ash.logging.AshLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

/**
 * A workspace is a fully separate data space: its own encrypted database, database key and
 * vault folder. Nothing is shared between workspaces except app settings (theme, lock).
 *
 *  - REAL ("Yash"): your real data. Live notifications and SMS import always land here.
 *  - DEMO: sample data for exploring the app. It never receives real notifications.
 */
enum class Workspace(
    val id: String,
    val dbName: String,
    val keyFile: String,
    val vaultDir: String,
    val selfProfileName: String,
) {
    REAL("yash", "ash.db", "db.key", "vault", "Yash"),
    DEMO("demo", "ash-demo.db", "db-demo.key", "vault-demo", "Me");

    companion object {
        fun fromId(id: String?): Workspace? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Chooses the workspace for this process. The choice is fixed for the process lifetime, so every
 * repository, ViewModel and Flow is bound to exactly one database; switching restarts the app.
 */
@Singleton
class WorkspaceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The workspace this process is running. Release builds without demo support always use REAL. */
    val active: Workspace =
        if (!BuildConfig.DEMO_DATA) Workspace.REAL
        else Workspace.fromId(prefs.getString(KEY_ACTIVE, null)) ?: Workspace.REAL

    /** True once the user picked an account in this launch (the sign-in screen asks on every cold start). */
    private val _signedIn = MutableStateFlow(!BuildConfig.DEMO_DATA || consumeSkipPicker())
    val signedIn: StateFlow<Boolean> = _signedIn

    val supportsDemo: Boolean get() = BuildConfig.DEMO_DATA

    /** Picks [workspace]. The same workspace continues immediately; a different one restarts the app into it. */
    fun signIn(workspace: Workspace, activity: Activity) {
        if (workspace == active) {
            _signedIn.value = true
            return
        }
        AshLogger.i(TAG, "Switching workspace ${active.id} -> ${workspace.id}")
        prefs.edit().putString(KEY_ACTIVE, workspace.id).putBoolean(KEY_SKIP_PICKER, true).commit()
        restart(activity)
    }

    /** Shows the sign-in screen again (Settings → Switch account). */
    fun signOut() {
        _signedIn.value = false
    }

    private fun consumeSkipPicker(): Boolean {
        val skip = prefs.getBoolean(KEY_SKIP_PICKER, false)
        if (skip) prefs.edit().remove(KEY_SKIP_PICKER).commit()
        return skip
    }

    private fun restart(activity: Activity) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finishAffinity()
        // A fresh process rebuilds every singleton against the newly chosen workspace.
        exitProcess(0)
    }

    private companion object {
        const val TAG = "Workspace"
        const val PREFS = "ash_workspace"
        const val KEY_ACTIVE = "active"
        const val KEY_SKIP_PICKER = "skip_picker_once"
    }
}
