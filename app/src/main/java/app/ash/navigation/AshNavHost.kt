package app.ash.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.ash.R
import app.ash.designsystem.AshIcons
import app.ash.designsystem.AshMotion
import app.ash.designsystem.AshTheme
import app.ash.designsystem.SecureScreen
import app.ash.presentation.home.HomeScreen
import app.ash.presentation.inbox.InboxScreen
import app.ash.presentation.inbox.RecordDetailScreen
import app.ash.presentation.inbox.RecordEditScreen
import app.ash.presentation.life.LifeScreen
import app.ash.presentation.life.MoneyScreen
import app.ash.presentation.life.PoliciesScreen
import app.ash.presentation.life.PolicyEditScreen
import app.ash.presentation.life.ProfilesScreen
import app.ash.presentation.life.SubscriptionEditScreen
import app.ash.presentation.life.VehicleDetailScreen
import app.ash.presentation.life.VehicleEditScreen
import app.ash.presentation.life.VehiclesScreen
import app.ash.presentation.life.WarrantiesScreen
import app.ash.presentation.life.WarrantyEditScreen
import app.ash.presentation.lock.LockScreen
import app.ash.presentation.onboarding.OnboardingScreen
import app.ash.presentation.onboarding.PermissionSetupScreen
import app.ash.presentation.onboarding.SourcesScreen
import app.ash.presentation.reminders.ReminderEditScreen
import app.ash.presentation.reminders.RemindersScreen
import app.ash.presentation.search.SearchScreen
import app.ash.presentation.settings.BackupScreen
import app.ash.presentation.settings.DiagnosticsScreen
import app.ash.presentation.settings.LogsScreen
import app.ash.presentation.settings.PrivacyCenterScreen
import app.ash.presentation.settings.SettingsScreen
import app.ash.presentation.vault.CameraScreen
import app.ash.presentation.vault.DocumentDetailScreen
import app.ash.presentation.vault.VaultScreen
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun AshRoot(openReminders: MutableStateFlow<Boolean>) {
    val vm: AppViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    AshTheme(state.themeMode) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (!state.loaded) return@Box
            if (state.secureAll) SecureScreen(alwaysSecure = true)
            val activity = androidx.activity.compose.LocalActivity.current!!
            androidx.compose.animation.AnimatedContent(
                targetState = when {
                    !state.signedIn -> RootStage.PICK_ACCOUNT
                    state.preparingDemo -> RootStage.PREPARING
                    else -> RootStage.APP
                },
                transitionSpec = {
                    androidx.compose.animation.fadeIn(app.ash.designsystem.AshMotion.fadeSpring) togetherWith
                        androidx.compose.animation.fadeOut(app.ash.designsystem.AshMotion.fadeSpring)
                },
                label = "root",
            ) { stage ->
                when (stage) {
                    RootStage.PICK_ACCOUNT -> app.ash.presentation.account.AccountPickerScreen(lastUsed = state.workspace) { ws -> vm.signIn(ws, activity) }
                    RootStage.PREPARING -> app.ash.presentation.account.PreparingDemoScreen(state.demoStep)
                    RootStage.APP -> {
                        val nav = rememberNavController()
                        val pendingReminders by openReminders.collectAsStateWithLifecycle()
                        LaunchedEffect(pendingReminders, state.onboardingDone) {
                            if (pendingReminders && state.onboardingDone) {
                                nav.navigate(RemindersRoute)
                                openReminders.value = false
                            }
                        }
                        AshNavHost(nav, startOnboarding = !state.onboardingDone, reviewCount = state.reviewCount)
                    }
                }
            }
            if (state.locked) {
                LockScreen(onUnlocked = vm::unlocked, onUnavailable = vm::disableLockBecauseUnavailable)
            }
        }
    }
}

private data class Tab(val route: Any, val icon: ImageVector, val selectedIcon: ImageVector, val label: Int)

private val tabs = listOf(
    Tab(HomeRoute, Icons.Outlined.WbSunny, Icons.Rounded.WbSunny, R.string.tab_home),
    Tab(BalanceRoute, Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet, R.string.tab_balance),
    Tab(VaultRoute, Icons.Outlined.Lock, Icons.Rounded.Lock, R.string.tab_vault),
    Tab(LifeRoute, Icons.Outlined.Category, Icons.Rounded.Category, R.string.tab_life),
)

@Composable
private fun AshNavHost(nav: NavHostController, startOnboarding: Boolean, reviewCount: Int) {
    val backStack by nav.currentBackStackEntryAsState()
    // Screen names only (never content), so a problem can be retraced from the log.
    androidx.compose.runtime.DisposableEffect(nav) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
            app.ash.logging.AshLogger.d("Nav", "Screen ${destination.route?.substringBefore("/")?.substringBefore("?")?.substringAfterLast(".")}")
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
    val destination = backStack?.destination
    val onTab = tabs.any { tab -> destination?.hasRoute(tab.route::class) == true }
    val navigate: (Any) -> Unit = { route -> nav.navigate(route) { launchSingleTop = true } }
    val back: () -> Unit = { nav.popBackStack() }
    val goHome: () -> Unit = {
        nav.navigate(HomeRoute) { popUpTo(nav.graph.id) { inclusive = true } }
    }

    // Decided once; later changes (finishing onboarding, delete-all) navigate explicitly
    // instead of rebuilding the whole graph underneath the user.
    val startDestination: Any = androidx.compose.runtime.remember { if (startOnboarding) OnboardingRoute else HomeRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTab) {
                IosTabBar(
                    selectedIndex = tabs.indexOfFirst { destination?.hasRoute(it.route::class) == true },
                    badgeOnFirst = reviewCount,
                    onSelect = { tab ->
                        nav.navigate(tab.route) {
                            // Today is the root of the tab stack even when the app started on onboarding.
                            popUpTo<HomeRoute> { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        NavHost(
            navController = nav,
            startDestination = startDestination,
            modifier = Modifier.padding(bottom).consumeWindowInsets(bottom),
            enterTransition = AshMotion.enter,
            exitTransition = AshMotion.exit,
            popEnterTransition = AshMotion.popEnter,
            popExitTransition = AshMotion.popExit,
        ) {
            composable<OnboardingRoute> { OnboardingScreen(onFinished = { nav.navigate(PermissionsRoute) }) }
            composable<PermissionsRoute> {
                PermissionSetupScreen(
                    onChooseSources = { nav.navigate(SourcesRoute(fromOnboarding = true)) },
                    onFinished = goHome,
                    onBack = back,
                )
            }
            composable<SourcesRoute> { entry ->
                val route = entry.toRoute<SourcesRoute>()
                SourcesScreen(onBack = back, onDone = if (route.fromOnboarding) goHome else back)
            }

            composable<HomeRoute>(enterTransition = AshMotion.tabEnter, exitTransition = AshMotion.tabExit, popEnterTransition = AshMotion.tabEnter) { HomeScreen(navigate) }
            composable<InboxRoute> { InboxScreen(navigate, onBack = back) }
            composable<VaultRoute>(enterTransition = AshMotion.tabEnter, exitTransition = AshMotion.tabExit, popEnterTransition = AshMotion.tabEnter) { VaultScreen(navigate) }
            composable<LifeRoute>(enterTransition = AshMotion.tabEnter, exitTransition = AshMotion.tabExit, popEnterTransition = AshMotion.tabEnter) { LifeScreen(navigate) }
            composable<SearchRoute> { SearchScreen(navigate, onBack = back) }
            composable<BalanceRoute>(enterTransition = AshMotion.tabEnter, exitTransition = AshMotion.tabExit, popEnterTransition = AshMotion.tabEnter) { app.ash.presentation.statement.StatementScreen(navigate) }

            composable<RecordRoute> { RecordDetailScreen(onBack = back, navigate = navigate) }
            composable<RecordEditRoute> { RecordEditScreen(onBack = back) }
            composable<DocumentRoute> { DocumentDetailScreen(onBack = back, navigate = navigate) }
            composable<CameraRoute> {
                CameraScreen(onBack = back, onSaved = { id ->
                    nav.popBackStack()
                    nav.navigate(DocumentRoute(id))
                })
            }
            composable<RemindersRoute> { RemindersScreen(onBack = back, navigate = navigate) }
            composable<ReminderEditRoute> { ReminderEditScreen(onBack = back) }
            composable<MoneyRoute> { MoneyScreen(onBack = back, navigate = navigate) }
            composable<SubscriptionEditRoute> { SubscriptionEditScreen(onBack = back) }
            composable<VehiclesRoute> { VehiclesScreen(onBack = back, navigate = navigate) }
            composable<VehicleRoute> { VehicleDetailScreen(onBack = back, navigate = navigate) }
            composable<VehicleEditRoute> { VehicleEditScreen(onBack = back) }
            composable<PoliciesRoute> { PoliciesScreen(onBack = back, navigate = navigate) }
            composable<PolicyEditRoute> { PolicyEditScreen(onBack = back) }
            composable<WarrantiesRoute> { WarrantiesScreen(onBack = back, navigate = navigate) }
            composable<WarrantyEditRoute> { WarrantyEditScreen(onBack = back) }
            composable<ProfilesRoute> { ProfilesScreen(onBack = back) }
            composable<SettingsRoute> { SettingsScreen(onBack = back, navigate = navigate) }
            composable<PrivacyRoute> { PrivacyCenterScreen(onBack = back, navigate = navigate) }
            composable<DiagnosticsRoute> { DiagnosticsScreen(onBack = back, navigate = navigate) }
            composable<LogsRoute> { LogsScreen(onBack = back) }
            composable<BackupRoute> {
                BackupScreen(onBack = back, onDeletedAll = {
                    nav.navigate(OnboardingRoute) { popUpTo(nav.graph.id) { inclusive = true } }
                })
            }
        }
    }
}

private enum class RootStage { PICK_ACCOUNT, PREPARING, APP }

/**
 * iOS-style tab bar: near-opaque background with a top hairline, filled icon and accent tint for
 * the selected tab, grey otherwise, small labels, no Material indicator pill, a light haptic tick.
 */
@Composable
private fun IosTabBar(selectedIndex: Int, badgeOnFirst: Int, onSelect: (Tab) -> Unit) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hairline = app.ash.designsystem.AshTheme.status.separator
    val accent = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .drawBehind { drawLine(hairline, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 0.5.dp.toPx()) }
            .navigationBarsPadding()
            .height(54.dp)
            .selectableGroup(),
    ) {
        tabs.forEachIndexed { i, tab ->
            val selected = i == selectedIndex
            val label = stringResource(tab.label)
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = selected,
                        role = androidx.compose.ui.semantics.Role.Tab,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (!selected) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onSelect(tab)
                        },
                    ),
            ) {
                val tint = if (selected) accent else idle
                if (i == 0 && badgeOnFirst > 0) {
                    BadgedBox(badge = { Badge { Text(badgeOnFirst.coerceAtMost(99).toString()) } }) {
                        Icon(if (selected) tab.selectedIcon else tab.icon, null, tint = tint, modifier = Modifier.size(25.dp))
                    }
                } else {
                    Icon(if (selected) tab.selectedIcon else tab.icon, null, tint = tint, modifier = Modifier.size(25.dp))
                }
                Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp), color = tint, maxLines = 1)
            }
        }
    }
}
