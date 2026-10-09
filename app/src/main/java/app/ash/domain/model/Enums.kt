package app.ash.domain.model

/** Core categories (§9). Stored by name; the categories table allows user-defined additions later. */
enum class Category {
    FINANCE, BILLS, SHOPPING, TRAVEL, VEHICLE, INSURANCE, DOCUMENTS, SUBSCRIPTIONS,
    HEALTHCARE, HOME, WORK, EDUCATION, DELIVERIES, GOVERNMENT, REMINDERS, OTHER;

    companion object {
        fun fromKey(key: String?): Category = entries.firstOrNull { it.name == key } ?: OTHER
    }
}

enum class RecordStatus { UNREAD, REVIEW, ACTION_REQUIRED, HANDLED, ARCHIVED }

enum class RecordType {
    TRANSACTION, BILL, DELIVERY, TRIP, INSURANCE, SUBSCRIPTION, VEHICLE, GOVERNMENT, APPOINTMENT, GENERIC
}

enum class RecordSource { NOTIFICATION, OCR, MANUAL }

enum class Importance(val weight: Int) { LOW(0), MEDIUM(8), HIGH(14), CRITICAL(20) }

enum class TxnDirection { INCOME, EXPENSE, REFUND, TRANSFER, UNKNOWN }

enum class RecurrencePeriod(val approxDays: Int, val perMonth: Double) {
    WEEKLY(7, 52.0 / 12), MONTHLY(30, 1.0), QUARTERLY(91, 1.0 / 3), YEARLY(365, 1.0 / 12)
}

enum class DocType {
    AADHAAR, PAN, DRIVING_LICENCE, VEHICLE_RC, INSURANCE, PASSPORT, TICKET, RECEIPT,
    WARRANTY, BILL, CERTIFICATE, MEDICAL, OTHER
}

enum class ReminderState { SCHEDULED, FIRED, DONE, SNOOZED, CANCELLED }

enum class ReminderTarget { NONE, RECORD, DOCUMENT, POLICY, WARRANTY, VEHICLE_PUC, VEHICLE_SERVICE, SUBSCRIPTION }

enum class InsuranceKind { VEHICLE, HEALTH, LIFE, HOME, TRAVEL, OTHER }

enum class ProfileRelation { SELF, PARENT, SPOUSE, CHILD, SIBLING, HOME, OTHER }

enum class TripMode { TRAIN, FLIGHT, BUS, OTHER }

enum class DeliveryState { ORDERED, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, RETURNED }

enum class SourceKind { BANK, UPI, SMS, SHOPPING, FOOD, TRAVEL, TELECOM, UTILITY, INSURANCE, OTT, GOVERNMENT, OTHER }
