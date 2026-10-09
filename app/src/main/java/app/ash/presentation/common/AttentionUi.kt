package app.ash.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.ash.R
import app.ash.common.Money
import app.ash.designsystem.Tone
import app.ash.designsystem.dueText
import app.ash.designsystem.expiresText
import app.ash.designsystem.relativeDay
import app.ash.domain.usecase.AttentionItem
import app.ash.domain.usecase.AttentionKind
import app.ash.domain.usecase.AttentionTarget
import app.ash.intelligence.PriorityEngine
import app.ash.navigation.DocumentRoute
import app.ash.navigation.PolicyEditRoute
import app.ash.navigation.RecordRoute
import app.ash.navigation.SubscriptionEditRoute
import app.ash.navigation.VehicleRoute
import app.ash.navigation.WarrantyEditRoute

fun AttentionTarget.route(): Any = when (this) {
    is AttentionTarget.RecordTarget -> RecordRoute(id)
    is AttentionTarget.PolicyTarget -> PolicyEditRoute(id)
    is AttentionTarget.DocumentTarget -> DocumentRoute(id)
    is AttentionTarget.WarrantyTarget -> WarrantyEditRoute(id)
    is AttentionTarget.VehicleTarget -> VehicleRoute(id)
    is AttentionTarget.SubscriptionTarget -> SubscriptionEditRoute(id)
}

fun PriorityEngine.Bucket.tone(): Tone = when (this) {
    PriorityEngine.Bucket.NEEDS_ATTENTION -> Tone.URGENT
    PriorityEngine.Bucket.COMING_UP -> Tone.SOON
    PriorityEngine.Bucket.RECENTLY_HANDLED -> Tone.OK
    else -> Tone.INFO
}

@Composable
fun bucketLabel(bucket: PriorityEngine.Bucket): String = stringResource(
    when (bucket) {
        PriorityEngine.Bucket.NEEDS_ATTENTION -> R.string.bucket_needs_attention
        PriorityEngine.Bucket.COMING_UP -> R.string.bucket_coming_up
        PriorityEngine.Bucket.RECENTLY_HANDLED -> R.string.bucket_handled
        else -> R.string.bucket_info
    },
)

/** One-line detail: "₹2,140 · Due today", "PUC · Expires in 23 days". */
@Composable
fun attentionDetail(item: AttentionItem): String {
    val prefix = when (item.kind) {
        AttentionKind.PUC -> stringResource(R.string.kind_puc)
        AttentionKind.SERVICE -> stringResource(R.string.kind_service)
        AttentionKind.RENEWAL -> stringResource(R.string.kind_renewal)
        else -> null
    }
    val whenText = item.deadlineAt?.let {
        when (item.deadlineKind) {
            PriorityEngine.DeadlineKind.DUE -> dueText(it)
            PriorityEngine.DeadlineKind.EXPIRES -> expiresText(it)
            PriorityEngine.DeadlineKind.EVENT -> if (item.kind == AttentionKind.DELIVERY) stringResource(R.string.expected_on, relativeDay(it)) else relativeDay(it)
            PriorityEngine.DeadlineKind.NONE -> null
        }
    }
    return listOfNotNull(item.amountPaise?.let { Money.format(it) }, prefix, whenText).joinToString(" · ").ifEmpty { stringResource(R.string.bucket_info) }
}

@Composable
fun scorePartLabel(part: PriorityEngine.Part): String = stringResource(
    when (part) {
        PriorityEngine.Part.URGENCY -> R.string.score_urgency
        PriorityEngine.Part.PROXIMITY -> R.string.score_proximity
        PriorityEngine.Part.IMPORTANCE -> R.string.score_importance
        PriorityEngine.Part.OVERDUE -> R.string.score_overdue
        PriorityEngine.Part.ACTION -> R.string.score_action
    },
)
