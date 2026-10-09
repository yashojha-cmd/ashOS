package app.ash.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ash.R
import app.ash.designsystem.AshScaffold
import app.ash.designsystem.EmptyState
import app.ash.designsystem.PrimaryButton
import app.ash.security.AppLockManager
import app.ash.security.AuthResult
import app.ash.security.BiometricGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensitiveGateViewModel @Inject constructor(private val lock: AppLockManager) : ViewModel() {
    val unlocked = MutableStateFlow(lock.recentlyAuthenticated())

    fun markUnlocked() {
        lock.markAuthenticated()
        unlocked.value = true
    }
}

/**
 * Requires fingerprint or device PIN before showing [content], unless the user authenticated
 * in the last two minutes. Used for sensitive documents and money, wherever they are opened from.
 */
@Composable
fun SensitiveGate(required: Boolean, title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    if (!required) {
        content()
        return
    }
    val vm: SensitiveGateViewModel = hiltViewModel()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    if (unlocked) {
        content()
        return
    }
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    val prompt = stringResource(R.string.vault_prompt_title)
    fun unlock() = scope.launch {
        when (BiometricGate.authenticate(activity, prompt, null)) {
            AuthResult.Success, AuthResult.Unavailable -> vm.markUnlocked()
            else -> Unit
        }
    }
    LaunchedEffect(Unit) { unlock() }
    AshScaffold(title = title, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            EmptyState(Icons.Rounded.Lock, stringResource(R.string.gate_locked_title), stringResource(R.string.vault_locked_body))
            PrimaryButton(stringResource(R.string.action_unlock), onClick = { unlock() }, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}
