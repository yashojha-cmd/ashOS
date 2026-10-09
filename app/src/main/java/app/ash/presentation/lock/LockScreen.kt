package app.ash.presentation.lock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import app.ash.R
import app.ash.designsystem.PrimaryButton
import app.ash.designsystem.SecureScreen
import app.ash.logging.AshLogger
import app.ash.security.AuthResult
import app.ash.security.BiometricGate
import kotlinx.coroutines.launch

@Composable
fun LockScreen(onUnlocked: () -> Unit, onUnavailable: () -> Unit) {
    SecureScreen()
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    val unavailable = stringResource(R.string.lock_unavailable)

    fun authenticate() {
        scope.launch {
            when (val result = BiometricGate.authenticate(activity, title, subtitle)) {
                AuthResult.Success -> onUnlocked()
                AuthResult.Cancelled -> message = null
                is AuthResult.Error -> {
                    message = result.message
                    AshLogger.w("Lock", "Unlock failed")
                }
                AuthResult.Unavailable -> {
                    message = unavailable
                    onUnavailable()
                }
            }
        }
    }
    LaunchedEffect(Unit) { authenticate() }
    // Back must never reveal or drive the app hidden behind the lock.
    androidx.activity.compose.BackHandler { activity.moveTaskToBack(true) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow touches so nothing behind the lock can be used.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.safeDrawingPadding().padding(32.dp),
        ) {
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(app.ash.designsystem.BrandGraphite).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = stringResource(R.string.cd_ash_logo), modifier = Modifier.size(96.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                message ?: stringResource(R.string.lock_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            PrimaryButton(stringResource(R.string.action_unlock), onClick = ::authenticate)
        }
    }
}
