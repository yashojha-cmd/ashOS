package app.ash.designsystem

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.House
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector
import app.ash.R
import app.ash.domain.model.Category

/**
 * Icon specification (§24): one Material Symbol per meaning, each with an accessibility label.
 * Icons are bundled vectors; nothing is fetched at runtime.
 */
object AshIcons {
    val Home = Icons.Rounded.Home
    val Inbox = Icons.Rounded.Inbox
    val Vault = Icons.Rounded.Lock
    val Life = Icons.Rounded.Category
    val Search = Icons.Rounded.Search

    val Urgent = Icons.Rounded.Error
    val Upcoming = Icons.Rounded.Schedule
    val Completed = Icons.Rounded.CheckCircle
    val Information = Icons.Rounded.Info

    fun category(category: Category): ImageVector = when (category) {
        Category.FINANCE -> Icons.Rounded.AccountBalanceWallet
        Category.BILLS -> Icons.Rounded.ReceiptLong
        Category.SHOPPING -> Icons.Rounded.ShoppingBag
        Category.TRAVEL -> Icons.Rounded.Flight
        Category.VEHICLE -> Icons.Rounded.DirectionsCar
        Category.INSURANCE -> Icons.Rounded.Shield
        Category.DOCUMENTS -> Icons.Rounded.Description
        Category.SUBSCRIPTIONS -> Icons.Rounded.Autorenew
        Category.HEALTHCARE -> Icons.Rounded.MedicalServices
        Category.HOME -> Icons.Rounded.House
        Category.WORK -> Icons.Rounded.Work
        Category.EDUCATION -> Icons.Rounded.School
        Category.DELIVERIES -> Icons.Rounded.Inventory2
        Category.GOVERNMENT -> Icons.Rounded.AccountBalance
        Category.REMINDERS -> Icons.Rounded.Notifications
        Category.OTHER -> Icons.Rounded.MoreHoriz
    }

    /** iOS system colour for a category (icon tiles, chart legends). */
    fun categoryColor(category: Category): androidx.compose.ui.graphics.Color = when (category) {
        Category.FINANCE -> TilePalette[1]
        Category.BILLS -> TilePalette[2]
        Category.SHOPPING -> TilePalette[6]
        Category.TRAVEL -> TilePalette[0]
        Category.VEHICLE -> TilePalette[4]
        Category.INSURANCE -> TilePalette[7]
        Category.DOCUMENTS -> TilePalette[0]
        Category.SUBSCRIPTIONS -> TilePalette[5]
        Category.HEALTHCARE -> TilePalette[3]
        Category.HOME -> TilePalette[8]
        Category.WORK -> TilePalette[9]
        Category.EDUCATION -> TilePalette[2]
        Category.DELIVERIES -> TilePalette[2]
        Category.GOVERNMENT -> TilePalette[4]
        Category.REMINDERS -> TilePalette[3]
        Category.OTHER -> TilePalette[9]
    }

    private val tileByIcon: Map<String, androidx.compose.ui.graphics.Color> by lazy {
        Category.entries.associate { category(it).name to categoryColor(it) }
    }

    /** Tile colour for any icon: the category colour when it is a category icon, otherwise a stable pick. */
    fun tileColor(icon: ImageVector): androidx.compose.ui.graphics.Color =
        tileByIcon[icon.name] ?: TilePalette[(icon.name.hashCode() and Int.MAX_VALUE) % TilePalette.size]

    @StringRes
    fun categoryLabel(category: Category): Int = when (category) {
        Category.FINANCE -> R.string.cat_finance
        Category.BILLS -> R.string.cat_bills
        Category.SHOPPING -> R.string.cat_shopping
        Category.TRAVEL -> R.string.cat_travel
        Category.VEHICLE -> R.string.cat_vehicle
        Category.INSURANCE -> R.string.cat_insurance
        Category.DOCUMENTS -> R.string.cat_documents
        Category.SUBSCRIPTIONS -> R.string.cat_subscriptions
        Category.HEALTHCARE -> R.string.cat_healthcare
        Category.HOME -> R.string.cat_home
        Category.WORK -> R.string.cat_work
        Category.EDUCATION -> R.string.cat_education
        Category.DELIVERIES -> R.string.cat_deliveries
        Category.GOVERNMENT -> R.string.cat_government
        Category.REMINDERS -> R.string.cat_reminders
        Category.OTHER -> R.string.cat_other
    }
}
