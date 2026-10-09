package app.ash.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.ash.R
import app.ash.common.Dates
import app.ash.domain.model.DocType
import app.ash.domain.model.InsuranceKind
import app.ash.domain.model.ProfileRelation
import app.ash.domain.model.RecordStatus
import app.ash.domain.model.RecordType
import app.ash.domain.model.RecurrencePeriod
import app.ash.domain.model.TxnDirection
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object AshFormat {
    fun date(millis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: java.util.Locale = java.util.Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(Instant.ofEpochMilli(millis).atZone(zone))

    fun dateTime(millis: Long, locale: java.util.Locale = java.util.Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun shortDate(millis: Long, locale: java.util.Locale = java.util.Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("d MMM", locale).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun daysFromToday(millis: Long): Long = Dates.daysUntil(millis, LocalDate.now(), ZoneId.systemDefault())
}

@Composable
fun formatDate(millis: Long): String = AshFormat.date(millis, locale = LocalConfiguration.current.locales[0])

/** "Due today", "Due in 3 days", "Overdue by 2 days". */
@Composable
fun dueText(millis: Long): String {
    val days = AshFormat.daysFromToday(millis)
    return when {
        days == 0L -> stringResource(R.string.due_today)
        days == 1L -> stringResource(R.string.due_tomorrow)
        days > 1 -> pluralStringResource(R.plurals.due_in_days, days.toInt(), days.toInt())
        else -> pluralStringResource(R.plurals.overdue_by_days, (-days).toInt(), (-days).toInt())
    }
}

/** "Expires today", "Expires in 23 days", "Expired 4 days ago". */
@Composable
fun expiresText(millis: Long): String {
    val days = AshFormat.daysFromToday(millis)
    return when {
        days == 0L -> stringResource(R.string.expires_today)
        days > 0 -> pluralStringResource(R.plurals.expires_in_days, days.toInt(), days.toInt())
        else -> pluralStringResource(R.plurals.expired_days_ago, (-days).toInt(), (-days).toInt())
    }
}

/** "Today", "Tomorrow", "In 3 days", "2 days ago". */
@Composable
fun relativeDay(millis: Long): String {
    val days = AshFormat.daysFromToday(millis)
    return when {
        days == 0L -> stringResource(R.string.today)
        days == 1L -> stringResource(R.string.tomorrow)
        days == -1L -> stringResource(R.string.yesterday)
        days > 1 -> pluralStringResource(R.plurals.in_days, days.toInt(), days.toInt())
        else -> pluralStringResource(R.plurals.days_ago, (-days).toInt(), (-days).toInt())
    }
}

@Composable
fun statusLabel(status: RecordStatus): String = stringResource(
    when (status) {
        RecordStatus.UNREAD -> R.string.status_unread
        RecordStatus.REVIEW -> R.string.status_review
        RecordStatus.ACTION_REQUIRED -> R.string.status_action
        RecordStatus.HANDLED -> R.string.status_handled
        RecordStatus.ARCHIVED -> R.string.status_archived
    },
)

@Composable
fun typeLabel(type: RecordType): String = stringResource(
    when (type) {
        RecordType.TRANSACTION -> R.string.type_transaction
        RecordType.BILL -> R.string.type_bill
        RecordType.DELIVERY -> R.string.type_delivery
        RecordType.TRIP -> R.string.type_trip
        RecordType.INSURANCE -> R.string.type_insurance
        RecordType.SUBSCRIPTION -> R.string.type_subscription
        RecordType.VEHICLE -> R.string.type_vehicle
        RecordType.GOVERNMENT -> R.string.type_government
        RecordType.APPOINTMENT -> R.string.type_appointment
        RecordType.GENERIC -> R.string.type_generic
    },
)

@Composable
fun directionLabel(direction: TxnDirection): String = stringResource(
    when (direction) {
        TxnDirection.INCOME -> R.string.dir_income
        TxnDirection.EXPENSE -> R.string.dir_expense
        TxnDirection.REFUND -> R.string.dir_refund
        TxnDirection.TRANSFER -> R.string.dir_transfer
        TxnDirection.UNKNOWN -> R.string.dir_unknown
    },
)

@Composable
fun periodLabel(period: RecurrencePeriod): String = stringResource(
    when (period) {
        RecurrencePeriod.WEEKLY -> R.string.period_weekly
        RecurrencePeriod.MONTHLY -> R.string.period_monthly
        RecurrencePeriod.QUARTERLY -> R.string.period_quarterly
        RecurrencePeriod.YEARLY -> R.string.period_yearly
    },
)

@Composable
fun docTypeLabel(type: DocType): String = stringResource(
    when (type) {
        DocType.AADHAAR -> R.string.doc_aadhaar
        DocType.PAN -> R.string.doc_pan
        DocType.DRIVING_LICENCE -> R.string.doc_dl
        DocType.VEHICLE_RC -> R.string.doc_rc
        DocType.INSURANCE -> R.string.doc_insurance
        DocType.PASSPORT -> R.string.doc_passport
        DocType.TICKET -> R.string.doc_ticket
        DocType.RECEIPT -> R.string.doc_receipt
        DocType.WARRANTY -> R.string.doc_warranty
        DocType.BILL -> R.string.doc_bill
        DocType.CERTIFICATE -> R.string.doc_certificate
        DocType.MEDICAL -> R.string.doc_medical
        DocType.OTHER -> R.string.doc_other
    },
)

@Composable
fun insuranceKindLabel(kind: InsuranceKind): String = stringResource(
    when (kind) {
        InsuranceKind.VEHICLE -> R.string.ins_vehicle
        InsuranceKind.HEALTH -> R.string.ins_health
        InsuranceKind.LIFE -> R.string.ins_life
        InsuranceKind.HOME -> R.string.ins_home
        InsuranceKind.TRAVEL -> R.string.ins_travel
        InsuranceKind.OTHER -> R.string.ins_other
    },
)

@Composable
fun relationLabel(relation: ProfileRelation): String = stringResource(
    when (relation) {
        ProfileRelation.SELF -> R.string.rel_self
        ProfileRelation.PARENT -> R.string.rel_parent
        ProfileRelation.SPOUSE -> R.string.rel_spouse
        ProfileRelation.CHILD -> R.string.rel_child
        ProfileRelation.SIBLING -> R.string.rel_sibling
        ProfileRelation.HOME -> R.string.rel_home
        ProfileRelation.OTHER -> R.string.rel_other
    },
)
