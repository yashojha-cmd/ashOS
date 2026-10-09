package app.ash.navigation

import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data object PermissionsRoute
@Serializable data class SourcesRoute(val fromOnboarding: Boolean = false)

// Tabs
@Serializable data object HomeRoute
@Serializable data object InboxRoute
@Serializable data object VaultRoute
@Serializable data object LifeRoute
@Serializable data object SearchRoute

@Serializable data class RecordRoute(val id: Long)
@Serializable data class RecordEditRoute(val id: Long = 0, val type: String = "BILL")
@Serializable data class DocumentRoute(val id: Long)
@Serializable data object CameraRoute
@Serializable data object RemindersRoute
@Serializable data class ReminderEditRoute(val id: Long = 0, val title: String = "")
@Serializable data object MoneyRoute
@Serializable data class SubscriptionEditRoute(val id: Long = 0)
@Serializable data object VehiclesRoute
@Serializable data class VehicleRoute(val id: Long)
@Serializable data class VehicleEditRoute(val id: Long = 0)
@Serializable data object PoliciesRoute
@Serializable data class PolicyEditRoute(val id: Long = 0, val vehicleId: Long = 0)
@Serializable data object WarrantiesRoute
@Serializable data class WarrantyEditRoute(val id: Long = 0)
@Serializable data object ProfilesRoute
@Serializable data object SettingsRoute
@Serializable data object PrivacyRoute
@Serializable data object DiagnosticsRoute
@Serializable data object LogsRoute
@Serializable data object BackupRoute
