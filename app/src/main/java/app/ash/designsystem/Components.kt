package app.ash.designsystem

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ash.R

/** Subtle iOS-style press feedback: the element sinks slightly with a spring. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, AshMotion.pressSpring, label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Screen scaffold with an iOS-style large title that collapses into the top bar on scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AshScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { snackbarHost?.let { SnackbarHost(it) } },
        content = content,
    )
}

/** Inset grouped list section (iOS Settings style). */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (header != null) {
            Text(
                header.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp).semantics { heading() },
            )
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth().animateContentSize(),
        ) {
            Column(content = content)
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
    }
}

/**
 * Grouped-list styling for lazy lists: each row is its own lazy item (only visible rows are
 * composed), drawn with rounded corners on the first and last row so it still looks like one
 * inset group. Use this instead of GroupedSection for lists that can grow long.
 */
fun <T> androidx.compose.foundation.lazy.LazyListScope.groupedItems(
    items: List<T>,
    key: (T) -> Any,
    header: String? = null,
    row: @Composable (item: T, isLast: Boolean) -> Unit,
) {
    if (items.isEmpty()) return
    if (header != null) {
        item(key = "header-$header") {
            Text(
                header.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, top = 8.dp, bottom = 6.dp).semantics { heading() },
            )
        }
    }
    val lastIndex = items.lastIndex
    items(items.size, key = { key(items[it]) }) { index ->
        val radius = 22.dp
        val shape = RoundedCornerShape(
            topStart = if (index == 0) radius else 0.dp,
            topEnd = if (index == 0) radius else 0.dp,
            bottomStart = if (index == lastIndex) radius else 0.dp,
            bottomEnd = if (index == lastIndex) radius else 0.dp,
        )
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = if (index == lastIndex) 8.dp else 0.dp),
        ) { row(items[index], index == lastIndex) }
    }
}

@Composable
fun AshRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.colorScheme.primaryContainer,
    showDivider: Boolean = true,
    showChevron: Boolean = false,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable(interaction, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (icon != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(iconBackground),
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (showChevron) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = AshTheme.status.separator,
                modifier = Modifier.padding(start = if (icon != null) 64.dp else 16.dp),
            )
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    showDivider: Boolean = true,
    enabled: Boolean = true,
) {
    AshRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        showDivider = showDivider,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        trailing = { Switch(checked = checked, onCheckedChange = if (enabled) onCheckedChange else null, enabled = enabled) },
    )
}

enum class Tone { URGENT, SOON, OK, INFO }

@Composable
fun toneColors(tone: Tone): Pair<Color, Color> {
    val s = AshTheme.status
    return when (tone) {
        Tone.URGENT -> s.urgent to s.urgentContainer
        Tone.SOON -> s.soon to s.soonContainer
        Tone.OK -> s.ok to s.okContainer
        Tone.INFO -> s.info to s.infoContainer
    }
}

fun toneIcon(tone: Tone): ImageVector = when (tone) {
    Tone.URGENT -> AshIcons.Urgent
    Tone.SOON -> AshIcons.Upcoming
    Tone.OK -> AshIcons.Completed
    Tone.INFO -> AshIcons.Information
}

/** Status shown by icon + text + colour together, so it never relies on colour alone. */
@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val (fg, bg) = toneColors(tone)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(toneIcon(tone), contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** The Home card: tinted by urgency, with category icon, title, detail line and a View action. */
@Composable
fun AttentionCard(
    title: String,
    detail: String,
    label: String,
    tone: Tone,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val (fg, bg) = toneColors(tone)
    val interaction = remember { MutableInteractionSource() }
    val viewLabel = stringResource(R.string.action_view)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                role = Role.Button,
                onClickLabel = viewLabel,
                onLongClickLabel = stringResource(R.string.action_why_here),
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$label. $title. $detail" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg)) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                StatusPill(label, tone)
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onClick) { Text(viewLabel) }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(34.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(4.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Inline banner for errors and important states (§38): never fail silently. */
@Composable
fun MessageBanner(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val (fg, bg) = toneColors(tone)
    Surface(shape = MaterialTheme.shapes.medium, color = bg, modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)) {
            Icon(toneIcon(tone), contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel, color = fg) }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Blocks screenshots and screen recording while this screen is visible (§20, §34). */
@Composable
fun SecureScreen(alwaysSecure: Boolean = false) {
    val context = LocalContext.current
    DisposableEffect(alwaysSecure) {
        val window = (context as? Activity)?.window
        // Reference-counted so one screen leaving never unblocks screenshots another still needs.
        SecureFlag.holders++
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            SecureFlag.holders--
            if (SecureFlag.holders <= 0) {
                SecureFlag.holders = 0
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

private object SecureFlag {
    var holders = 0
}

@Composable
fun CenteredLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}
