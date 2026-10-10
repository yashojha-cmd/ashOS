package app.ash.domain.usecase

import app.ash.domain.model.DeliveryState
import app.ash.domain.model.InsuranceKind
import app.ash.domain.model.Record
import app.ash.domain.model.RecordType
import app.ash.domain.model.RecurrencePeriod
import app.ash.domain.model.TripMode
import app.ash.domain.model.TxnDirection

/**
 * What a record *means*, as opposed to the message it came from. Every list in ashOS shows a
 * subject, one meaningful headline and a few facts; the original message stays in the detail view.
 */
sealed interface Headline {
    data class Due(val amountPaise: Long?, val dueAt: Long) : Headline
    data class Expires(val kind: InsuranceKind?, val expiresAt: Long) : Headline
    data class Paid(val amountPaise: Long) : Headline
    data class Received(val amountPaise: Long, val salary: Boolean) : Headline
    data class Refund(val amountPaise: Long) : Headline
    data class Trip(val mode: TripMode, val from: String?, val to: String?, val at: Long?) : Headline
    data class Delivery(val state: DeliveryState, val at: Long?) : Headline
    data class Renews(val amountPaise: Long?, val period: RecurrencePeriod?, val at: Long?) : Headline
    data class Event(val at: Long) : Headline
    /** Nothing structured was extracted; show a short clean line from the message. */
    data class Note(val text: String) : Headline
}

enum class FactKind { ACCOUNT, PNR, POLICY, ORDER, VEHICLE, REFERENCE, SOURCE }

data class Fact(val kind: FactKind, val value: String)

data class RecordSummary(val subject: String, val headline: Headline, val facts: List<Fact>)

object RecordSummarizer {

    fun summarize(r: Record): RecordSummary {
        val subject = (r.merchant ?: r.title).trim().ifEmpty { r.sourceLabel ?: "" }
        val headline = headline(r)
        val facts = buildList {
            r.reference?.takeIf { it.isNotBlank() }?.let { ref ->
                add(
                    Fact(
                        when (r.type) {
                            RecordType.TRIP -> FactKind.PNR
                            RecordType.INSURANCE -> FactKind.POLICY
                            RecordType.DELIVERY -> FactKind.ORDER
                            RecordType.VEHICLE -> FactKind.VEHICLE
                            else -> FactKind.REFERENCE
                        },
                        ref,
                    ),
                )
            }
            r.extras.vehicleReg?.takeIf { reg -> none { it.value.equals(reg, ignoreCase = true) } }?.let { add(Fact(FactKind.VEHICLE, it)) }
            r.extras.accountRef?.let { add(Fact(FactKind.ACCOUNT, it)) }
            // The app it came from, only when it adds information (not "ACKO" under "ACKO").
            r.sourceLabel?.takeIf { label -> !subject.contains(label, ignoreCase = true) && !label.contains(subject, ignoreCase = true) }
                ?.let { add(Fact(FactKind.SOURCE, it)) }
        }.take(3)
        return RecordSummary(subject, headline, facts)
    }

    private fun headline(r: Record): Headline {
        val amount = r.amountPaise
        return when (r.type) {
            RecordType.TRANSACTION -> when (r.extras.direction) {
                TxnDirection.INCOME -> amount?.let { Headline.Received(it, r.extras.salary == true) }
                TxnDirection.REFUND -> amount?.let { Headline.Refund(it) }
                TxnDirection.EXPENSE, TxnDirection.TRANSFER, TxnDirection.UNKNOWN, null -> amount?.let { Headline.Paid(it) }
            } ?: note(r)
            RecordType.BILL -> r.dueAt?.let { Headline.Due(amount, it) } ?: amount?.let { Headline.Paid(it) } ?: note(r)
            RecordType.INSURANCE -> r.expiresAt?.let { Headline.Expires(r.extras.insuranceKind, it) } ?: r.dueAt?.let { Headline.Due(amount, it) } ?: note(r)
            RecordType.TRIP -> Headline.Trip(r.extras.tripMode ?: TripMode.OTHER, r.extras.tripFrom, r.extras.tripTo, r.eventAt)
            RecordType.DELIVERY -> Headline.Delivery(r.extras.deliveryState ?: DeliveryState.ORDERED, r.eventAt)
            RecordType.SUBSCRIPTION -> Headline.Renews(amount, r.extras.recurrence, r.dueAt)
            RecordType.VEHICLE -> (r.dueAt ?: r.expiresAt)?.let { Headline.Due(amount, it) } ?: note(r)
            RecordType.GOVERNMENT, RecordType.APPOINTMENT, RecordType.GENERIC ->
                r.dueAt?.let { if (amount != null) Headline.Due(amount, it) else Headline.Event(it) } ?: note(r)
        }
    }

    /** First sentence of the summary, without the sender prefix banks often add ("BESCOM: ..."). */
    private fun note(r: Record): Headline {
        val text = r.summary.substringAfter(": ", r.summary).split(Regex("""(?<=[.!?])\s""")).firstOrNull().orEmpty().trim()
        return Headline.Note(text.ifEmpty { r.title }.take(90))
    }
}
