package app.ash.presentation.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MarkunreadMailbox
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ash.BuildConfig
import app.ash.R
import app.ash.data.repository.AppSettings
import app.ash.data.repository.SettingsRepository
import app.ash.data.repository.ThemeMode
import app.ash.designsystem.AshRow
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.OptionField
import app.ash.designsystem.SwitchRow
import app.ash.domain.repository.ReminderRepository
import app.ash.navigation.BackupRoute
import app.ash.navigation.DiagnosticsRoute
import app.ash.navigation.PrivacyRoute
import app.ash.navigation.ProfilesRoute
import app.ash.navigation.SourcesRoute
import app.ash.presentation.onboarding.notificationAccessIntent
import app.ash.reminder.ReminderScheduler
import app.ash.security.AppLockManager
import app.ash.security.AuthResult
import app.ash.security.BiometricGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val reminders: ReminderRepository,
    val scheduler: ReminderScheduler,
    private val lock: AppLockManager,
    private val demo: app.ash.demo.DemoDataSeeder,
    private val workspaces: app.ash.workspace.WorkspaceManager,
    private val backup: app.ash.backup.BackupRepository,
    private val sms: app.ash.notification.SmsHistoryImporter,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val workspace: app.ash.workspace.Workspace get() = workspaces.active
    val supportsDemo: Boolean get() = workspaces.supportsDemo

    /** Null when idle; otherwise the step being loaded. */
    val demoProgress = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Null when idle; otherwise (done, total) messages read. */
    val smsProgress = kotlinx.coroutines.flow.MutableStateFlow<Pair<Int, Int>?>(null)

    fun switchAccount() = workspaces.signOut()

    /** Demo account only: wipe it and fill it again with fresh sample data. */
    fun resetDemo(done: (app.ash.demo.DemoDataSeeder.Summary?) -> Unit) = viewModelScope.launch {
        if (workspaces.active != app.ash.workspace.Workspace.DEMO) return@launch
        demoProgress.value = "…"
        val summary = runCatching {
            backup.deleteAll()
            demo.seed { step -> demoProgress.value = step }
        }.onFailure { app.ash.logging.AshLogger.e("Demo", "Reset demo data failed", it) }.getOrNull()
        demoProgress.value = null
        done(summary)
    }

    fun hasSmsPermission() = sms.hasPermission()

    fun importSms(done: (app.ash.notification.SmsHistoryImporter.Result?) -> Unit) = viewModelScope.launch {
        smsProgress.value = 0 to 0
        val result = runCatching { sms.import { d, t -> smsProgress.value = d to t } }
            .onFailure { app.ash.logging.AshLogger.e("SmsImport", "SMS import failed", it) }
            .getOrNull()
        smsProgress.value = null
        done(result)
    }

    fun setAppLock(enabled: Boolean) = viewModelScope.launch {
        repo.setAppLock(enabled)
        if (enabled) lock.unlock()
    }
    fun setTimeout(seconds: Int) = viewModelScope.launch { repo.setAutoLockSeconds(seconds) }
    fun setExact(enabled: Boolean) = viewModelScope.launch {
        repo.setExactReminders(enabled)
        reminders.rescheduleAll()
    }
    fun setHideAmounts(v: Boolean) = viewModelScope.launch { repo.setHideAmounts(v) }
    fun setStoreText(v: Boolean) = viewModelScope.launch { repo.setStoreOriginalText(v) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setSecureAll(v: Boolean) = viewModelScope.launch { repo.setSecureAllScreens(v) }
}

private val timeouts = listOf(0, 30, 60, 300, 900)

@Composable
fun SettingsScreen(onBack: () -> Unit, navigate: (Any) -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val lockTitle = stringResource(R.string.lock_prompt_title)
    val noLockText = stringResource(R.string.lock_unavailable)

    AshScaffold(title = stringResource(R.string.settings_title), onBack = onBack, snackbarHost = snackbar) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item {
                val progress by vm.demoProgress.collectAsStateWithLifecycle()
                val smsProgress by vm.smsProgress.collectAsStateWithLifecycle()
                val doneTemplate = stringResource(R.string.demo_done)
                val smsDoneTemplate = stringResource(R.string.sms_import_done)
                val smsDenied = stringResource(R.string.sms_permission_denied)
                val failText = stringResource(R.string.error_unknown)
                val smsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (!granted) scope.launch { snackbar.showSnackbar(smsDenied) }
                    else vm.importSms { r -> scope.launch { snackbar.showSnackbar(r?.let { String.format(smsDoneTemplate, it.businessMessages, it.created, it.skippedPersonal) } ?: failText) } }
                }
                val accountName = if (vm.workspace == app.ash.workspace.Workspace.REAL) stringResource(R.string.account_real_title) else stringResource(R.string.account_demo_title)
                GroupedSection(header = stringResource(R.string.account_header)) {
                    AshRow(
                        title = stringResource(R.string.account_signed_in, accountName),
                        icon = Icons.Rounded.Group,
                        showDivider = true,
                    )
                    if (vm.supportsDemo) {
                        AshRow(stringResource(R.string.account_switch), icon = Icons.Rounded.Apps, showChevron = true, onClick = vm::switchAccount)
                    }
                    if (vm.workspace == app.ash.workspace.Workspace.DEMO) {
                        AshRow(
                            title = stringResource(R.string.demo_reset),
                            subtitle = progress?.let { stringResource(R.string.demo_loading, it) } ?: stringResource(R.string.demo_reset_desc),
                            icon = Icons.Rounded.BugReport,
                            showDivider = false,
                            onClick = if (progress == null) ({
                                vm.resetDemo { summary ->
                                    scope.launch { snackbar.showSnackbar(summary?.let { String.format(doneTemplate, it.created, it.review, it.documents) } ?: failText) }
                                }
                            }) else null,
                            trailing = { if (progress != null) androidx.compose.material3.CircularProgressIndicator(modifier = androidx.compose.ui.Modifier.size(22.dp), strokeWidth = 2.dp) },
                        )
                    } else {
                        AshRow(
                            title = stringResource(R.string.sms_import),
                            subtitle = smsProgress?.let { (done, total) -> stringResource(R.string.sms_importing, done, total) }
                                ?: stringResource(R.string.sms_import_desc, app.ash.notification.SmsHistoryImporter.DEFAULT_DAYS),
                            icon = Icons.Rounded.MarkunreadMailbox,
                            showDivider = false,
                            onClick = if (smsProgress == null) ({
                                if (vm.hasSmsPermission()) {
                                    vm.importSms { r -> scope.launch { snackbar.showSnackbar(r?.let { String.format(smsDoneTemplate, it.businessMessages, it.created, it.skippedPersonal) } ?: failText) } }
                                } else {
                                    smsLauncher.launch(android.Manifest.permission.READ_SMS)
                                }
                            }) else null,
                            trailing = { if (smsProgress != null) androidx.compose.material3.CircularProgressIndicator(modifier = androidx.compose.ui.Modifier.size(22.dp), strokeWidth = 2.dp) },
                        )
                    }
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.settings_privacy)) {
                    AshRow(stringResource(R.string.privacy_title), subtitle = stringResource(R.string.privacy_promise_short), icon = Icons.Rounded.PrivacyTip, showChevron = true, onClick = { navigate(PrivacyRoute) })
                    SwitchRow(stringResource(R.string.settings_store_text), s.storeOriginalText, vm::setStoreText, subtitle = stringResource(R.string.settings_store_text_desc), icon = Icons.Rounded.Save, showDivider = false)
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.settings_security)) {
                    SwitchRow(stringResource(R.string.settings_app_lock), s.appLockEnabled, { enable ->
                        scope.launch {
                            if (!enable) { vm.setAppLock(false); return@launch }
                            when (BiometricGate.authenticate(activity, lockTitle, null)) {
                                AuthResult.Success -> vm.setAppLock(true)
                                AuthResult.Unavailable -> snackbar.showSnackbar(noLockText)
                                else -> Unit
                            }
                        }
                    }, subtitle = stringResource(R.string.settings_app_lock_desc), icon = Icons.Rounded.Fingerprint)
                    if (s.appLockEnabled) {
                        OptionField(stringResource(R.string.settings_auto_lock), timeouts, s.autoLockSeconds.takeIf { it in timeouts } ?: 60, { timeoutLabel(it) }, vm::setTimeout)
                    }
                    SwitchRow(stringResource(R.string.settings_secure_all), s.secureAllScreens, vm::setSecureAll, subtitle = stringResource(R.string.settings_secure_all_desc), icon = Icons.Rounded.ScreenLockPortrait)
                    SwitchRow(stringResource(R.string.settings_hide_amounts), s.hideAmountsInNotifications, vm::setHideAmounts, subtitle = stringResource(R.string.settings_hide_amounts_desc), icon = Icons.Rounded.VisibilityOff, showDivider = false)
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.settings_notifications)) {
                    AshRow(stringResource(R.string.perm_notification_access), icon = Icons.Rounded.MarkunreadMailbox, showChevron = true, onClick = { context.startActivity(notificationAccessIntent(context)) })
                    AshRow(stringResource(R.string.perm_sources), icon = Icons.Rounded.Apps, showChevron = true, onClick = { navigate(SourcesRoute()) })
                    SwitchRow(stringResource(R.string.settings_exact), s.exactReminders, { enable ->
                        if (enable && !vm.scheduler.canUseExactAlarms() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                        }
                        vm.setExact(enable)
                    }, subtitle = stringResource(R.string.settings_exact_desc), icon = Icons.Rounded.Alarm, showDivider = false)
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.settings_general)) {
                    AshRow(stringResource(R.string.profiles_title), icon = Icons.Rounded.Group, showChevron = true, onClick = { navigate(ProfilesRoute) })
                    OptionField(stringResource(R.string.settings_theme), ThemeMode.entries, s.themeMode, { themeLabel(it) }, vm::setTheme)
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.settings_data)) {
                    AshRow(stringResource(R.string.backup_title), subtitle = stringResource(R.string.backup_desc), icon = Icons.Rounded.Save, showChevron = true, onClick = { navigate(BackupRoute) })
                    AshRow(stringResource(R.string.diagnostics_title), subtitle = stringResource(R.string.diagnostics_desc), icon = Icons.Rounded.BugReport, showChevron = true, showDivider = false, onClick = { navigate(DiagnosticsRoute) })
                }
            }
            item {
                GroupedSection(footer = stringResource(R.string.about_footer)) {
                    AshRow(stringResource(R.string.about_version), showDivider = false, trailing = { Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium) })
                }
            }
        }
    }
}

@Composable
private fun timeoutLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.timeout_immediately)
    30 -> stringResource(R.string.timeout_30s)
    60 -> stringResource(R.string.timeout_1m)
    300 -> stringResource(R.string.timeout_5m)
    else -> stringResource(R.string.timeout_15m)
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Suppress("unused")
private val keepIcons = listOf(Icons.Rounded.DarkMode, Icons.Rounded.Timer)
