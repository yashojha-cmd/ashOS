package app.ash.presentation.onboarding

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.MarkunreadMailbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import app.ash.R
import app.ash.data.repository.SettingsRepository
import app.ash.designsystem.AshRow
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.AshTheme
import app.ash.designsystem.GroupedSection
import app.ash.designsystem.PrimaryButton
import app.ash.domain.repository.SourceRepository
import app.ash.notification.AshNotificationListener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionSetupViewModel @Inject constructor(
    private val sources: SourceRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val enabledSources = sources.observeEnabledCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun listenerEnabled() = sources.isListenerEnabled()

    init {
        viewModelScope.launch { sources.discoverInstalledKnownApps() }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            onDone()
        }
    }
}

fun notificationAccessIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, AshNotificationListener::class.java).flattenToString())
    } else {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

fun hasPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Progressive, honest permission setup (§35). Each row says why ASH asks, and everything can be
 * skipped; ASH still works with manual entries and documents.
 */
@Composable
fun PermissionSetupScreen(onChooseSources: () -> Unit, onFinished: () -> Unit, onBack: () -> Unit) {
    val vm: PermissionSetupViewModel = hiltViewModel()
    val context = LocalContext.current
    val enabledSources by vm.enabledSources.collectAsStateWithLifecycle()
    var listener by remember { mutableStateOf(vm.listenerEnabled()) }
    var notify by remember { mutableStateOf(hasPostNotifications(context)) }
    LifecycleResumeEffect(Unit) {
        listener = vm.listenerEnabled()
        notify = hasPostNotifications(context)
        onPauseOrDispose { }
    }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notify = it }
    val status = AshTheme.status

    AshScaffold(title = stringResource(R.string.perm_title), onBack = onBack) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Text(
                    stringResource(R.string.perm_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            item {
                GroupedSection(header = stringResource(R.string.perm_notifications_header), footer = stringResource(R.string.perm_notifications_footer)) {
                    AshRow(
                        title = stringResource(R.string.perm_notification_access),
                        subtitle = stringResource(if (listener) R.string.perm_on else R.string.perm_notification_access_desc),
                        icon = Icons.Rounded.MarkunreadMailbox,
                        iconTint = if (listener) status.ok else MaterialTheme.colorScheme.primary,
                        iconBackground = if (listener) status.okContainer else MaterialTheme.colorScheme.primaryContainer,
                        onClick = { context.startActivity(notificationAccessIntent(context)) },
                        trailing = { TextButton(onClick = { context.startActivity(notificationAccessIntent(context)) }) { Text(stringResource(if (listener) R.string.action_manage else R.string.action_turn_on)) } },
                    )
                    AshRow(
                        title = stringResource(R.string.perm_sources),
                        subtitle = stringResource(R.string.perm_sources_desc, enabledSources),
                        icon = Icons.Rounded.Apps,
                        showDivider = false,
                        showChevron = true,
                        onClick = onChooseSources,
                    )
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.perm_reminders_header), footer = stringResource(R.string.perm_reminders_footer)) {
                    AshRow(
                        title = stringResource(R.string.perm_post_notifications),
                        subtitle = stringResource(if (notify) R.string.perm_on else R.string.perm_post_notifications_desc),
                        icon = Icons.Rounded.NotificationsActive,
                        iconTint = if (notify) status.ok else MaterialTheme.colorScheme.primary,
                        iconBackground = if (notify) status.okContainer else MaterialTheme.colorScheme.primaryContainer,
                        showDivider = false,
                        trailing = {
                            if (!notify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                TextButton(onClick = { notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.action_allow)) }
                            }
                        },
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.perm_camera_later),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(text = stringResource(R.string.action_finish_setup), onClick = { vm.finish(onFinished) }, modifier = Modifier.padding(horizontal = 16.dp))
                TextButton(onClick = { vm.finish(onFinished) }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(stringResource(R.string.action_skip_for_now))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
