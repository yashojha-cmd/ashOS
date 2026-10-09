package app.ash.presentation.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ash.R
import app.ash.designsystem.AshTheme
import app.ash.designsystem.BrandGraphite
import app.ash.designsystem.pressScale
import app.ash.workspace.Workspace

/**
 * Asked on every cold start: which account to open. Each account is a separate encrypted data
 * space; real notifications always go to Yash's account.
 */
@Composable
fun AccountPickerScreen(lastUsed: Workspace, onPick: (Workspace) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(BrandGraphite)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = stringResource(R.string.cd_ash_logo), modifier = Modifier.size(96.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.account_title), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.account_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        AccountCard(
            title = stringResource(R.string.account_real_title),
            body = stringResource(R.string.account_real_body),
            icon = Icons.Rounded.Person,
            accent = MaterialTheme.colorScheme.primary,
            accentBackground = MaterialTheme.colorScheme.primaryContainer,
            lastUsed = lastUsed == Workspace.REAL,
            onClick = { onPick(Workspace.REAL) },
        )
        Spacer(Modifier.height(14.dp))
        AccountCard(
            title = stringResource(R.string.account_demo_title),
            body = stringResource(R.string.account_demo_body),
            icon = Icons.Rounded.Science,
            accent = AshTheme.status.info,
            accentBackground = AshTheme.status.infoContainer,
            lastUsed = lastUsed == Workspace.DEMO,
            onClick = { onPick(Workspace.DEMO) },
        )
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.account_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AccountCard(
    title: String,
    body: String,
    icon: ImageVector,
    accent: Color,
    accentBackground: Color,
    lastUsed: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .border(if (lastUsed) 2.dp else 1.dp, if (lastUsed) accent else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .semantics { role = Role.Button },
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(accentBackground), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (lastUsed) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.account_last_used),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(accentBackground).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Shown while the demo account fills itself with sample data the first time. */
@Composable
fun PreparingDemoScreen(step: String?) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.account_preparing), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (step != null) {
            Spacer(Modifier.height(6.dp))
            Text(step, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
