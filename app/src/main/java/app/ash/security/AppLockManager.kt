package app.ash.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.ash.common.ApplicationScope
import app.ash.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App lock state. Locks on cold start and after the app has been in the background longer than
 * the auto-lock timeout. Sensitive areas (vault, money) additionally require a fresh unlock
 * within [SENSITIVE_WINDOW_MS].
 */
@Singleton
class AppLockManager @Inject constructor(
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked

    private var backgroundedAt: Long? = null
    private var lastUnlockAt: Long = 0
    private var lockEnabled = false
    private var timeoutMs = 60_000L

    fun start() {
        scope.launch {
            settings.settings.collect { s ->
                lockEnabled = s.appLockEnabled
                timeoutMs = s.autoLockSeconds * 1000L
                if (!lockEnabled) _locked.value = false
            }
        }
        scope.launch(kotlinx.coroutines.Dispatchers.Main) { ProcessLifecycleOwner.get().lifecycle.addObserver(this@AppLockManager) }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = backgroundedAt ?: return
        if (lockEnabled && System.currentTimeMillis() - since >= timeoutMs) _locked.value = true
    }

    fun unlock() {
        lastUnlockAt = System.currentTimeMillis()
        _locked.value = false
    }

    fun lockNow() {
        if (lockEnabled) _locked.value = true
    }

    /** True when a vault/money screen may open without asking again. */
    fun recentlyAuthenticated(): Boolean = System.currentTimeMillis() - lastUnlockAt < SENSITIVE_WINDOW_MS

    fun markAuthenticated() {
        lastUnlockAt = System.currentTimeMillis()
    }

    companion object {
        const val SENSITIVE_WINDOW_MS = 2 * 60 * 1000L
    }
}
