package app.ash.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ash.R
import app.ash.common.Money
import app.ash.designsystem.AshIcons
import app.ash.designsystem.dueText
import app.ash.designsystem.expiresText
import app.ash.designsystem.insuranceKindLabel
import app.ash.designsystem.periodLabel
import app.ash.designsystem.relativeDay
import app.ash.domain.model.DeliveryState
import app.ash.domain.model.Record
import app.ash.domain.model.TripMode
import app.ash.domain.usecase.Fact
import app.ash.domain.usecase.FactKind
import app.ash.domain.usecase.Headline
import app.ash.domain.usecase.RecordSummarizer

/** One plain sentence for what the record means: "₹2,140 · Due today", "Train MMCT → NDLS · In 15 days". */
@Composable
fun headlineText(h: Headline): String = when (h) {
    is Headline.Due -> listOfNotNull(h.amountPaise?.let { Money.format(it) }, dueText(h.dueAt)).joinToString(" · ")
    is Headline.Expires -> listOfNotNull(
        h.kind?.let { stringResource(R.string.headline_insurance, insuranceKindLabel(it)) },
        expiresText(h.expiresAt),
    ).joinToString(" · ")
    is Headline.Paid -> stringResource(R.string.headline_paid, Money.format(h.amountPaise))
    is Headline.Received -> stringResource(if (h.salary) R.string.headline_salary else R.string.headline_received, Money.format(h.amountPaise))
    is Headline.Refund -> stringResource(R.string.headline_refund, Money.format(h.amountPaise))
    is Headline.Trip -> {
        val mode = stringResource(
            when (h.mode) {
                TripMode.TRAIN -> R.string.trip_train
                TripMode.FLIGHT -> R.string.trip_flight
                TripMode.BUS -> R.string.trip_bus
                TripMode.OTHER -> R.string.trip_other
            },
        )
        val route = if (h.from != null && h.to != null) "${h.from} → ${h.to}" else null
        listOfNotNull(listOfNotNull(mode, route).joinToString(" "), h.at?.let { relativeDay(it) }).joinToString(" · ")
    }
    is Headline.Delivery -> when (h.state) {
        DeliveryState.ORDERED -> h.at?.let { stringResource(R.string.headline_ordered_eta, relativeDay(it)) } ?: stringResource(R.string.headline_ordered)
        DeliveryState.SHIPPED -> h.at?.let { stringResource(R.string.headline_arriving, relativeDay(it)) } ?: stringResource(R.string.headline_shipped)
        DeliveryState.OUT_FOR_DELIVERY -> stringResource(R.string.headline_out_for_delivery)
        DeliveryState.DELIVERED -> stringResource(R.string.headline_delivered)
        DeliveryState.CANCELLED -> stringResource(R.string.headline_cancelled)
        DeliveryState.RETURNED -> stringResource(R.string.headline_returned)
    }
    is Headline.Renews -> listOfNotNull(
        h.at?.let { stringResource(R.string.renews_on, relativeDay(it)) },
        h.amountPaise?.let { a -> h.period?.let { "${Money.format(a)} · ${periodLabel(it)}" } ?: Money.format(a) },
    ).joinToString(" · ")
    is Headline.Event -> relativeDay(h.at)
    is Headline.Note -> h.text
}

@Composable
fun factText(f: Fact): String = when (f.kind) {
    FactKind.PNR -> stringResource(R.string.fact_pnr, f.value)
    FactKind.POLICY -> stringResource(R.string.fact_policy, f.value)
    FactKind.ORDER -> stringResource(R.string.fact_order, f.value)
    FactKind.REFERENCE -> stringResource(R.string.fact_ref, f.value)
    FactKind.VEHICLE, FactKind.ACCOUNT, FactKind.SOURCE -> f.value
}

/** Small outlined chips for the key identifiers. */
@Composable
fun FactChips(facts: List<Fact>, modifier: Modifier = Modifier) {
    if (facts.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        facts.forEach { fact ->
            Text(
                factText(fact),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * The standard way to show a record anywhere in ashOS: subject, meaning, key facts.
 * [trailing] is for a status pill or time; the original message is never shown here.
 */
@Composable
fun RecordSummaryContent(record: Record, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val summary = RecordSummarizer.summarize(record)
    Row(modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(AshIcons.categoryColor(record.category)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AshIcons.category(record.category), null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(summary.subject, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                trailing()
            }
            Text(
                headlineText(summary.headline),
                style = MaterialTheme.typography.bodyLarge,
                color = if (summary.headline is Headline.Note) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FactChips(summary.facts, Modifier.padding(top = 2.dp))
        }
    }
}
