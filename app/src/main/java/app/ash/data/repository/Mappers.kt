package app.ash.data.repository

import app.ash.data.local.dao.BillRow
import app.ash.data.local.dao.DocumentWithTags
import app.ash.data.local.dao.TransactionRow
import app.ash.data.local.entities.AppSourceEntity
import app.ash.data.local.entities.DocumentEntity
import app.ash.data.local.entities.InsurancePolicyEntity
import app.ash.data.local.entities.ProfileEntity
import app.ash.data.local.entities.RecordEntity
import app.ash.data.local.entities.ReminderEntity
import app.ash.data.local.entities.SubscriptionEntity
import app.ash.data.local.entities.VehicleEntity
import app.ash.data.local.entities.WarrantyEntity
import app.ash.data.parser.dictionary.KnownApps
import app.ash.domain.model.AppSource
import app.ash.domain.model.BillItem
import app.ash.domain.model.Category
import app.ash.domain.model.Document
import app.ash.domain.model.InsurancePolicy
import app.ash.domain.model.Profile
import app.ash.domain.model.Record
import app.ash.domain.model.RecordExtras
import app.ash.domain.model.Reminder
import app.ash.domain.model.Subscription
import app.ash.domain.model.TransactionItem
import app.ash.domain.model.Vehicle
import app.ash.domain.model.Warranty
import kotlinx.serialization.json.Json

internal val AshJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}

internal fun RecordExtras.toJson(): String? = if (this == RecordExtras()) null else AshJson.encodeToString(RecordExtras.serializer(), this)

internal fun String?.toExtras(): RecordExtras =
    this?.let { runCatching { AshJson.decodeFromString(RecordExtras.serializer(), it) }.getOrNull() } ?: RecordExtras()

fun RecordEntity.toDomain() = Record(
    id = id, type = type, category = Category.fromKey(categoryKey), title = title, summary = summary,
    merchant = merchantName, amountPaise = amountPaise, dueAt = dueAt, eventAt = eventAt, expiresAt = expiresAt,
    reference = reference, importance = importance, status = status, confidence = confidence, source = source,
    sourcePackage = sourcePackage, sourceLabel = sourceLabel, notificationId = notificationId, profileId = profileId,
    extras = extrasJson.toExtras(), materialized = materialized, createdAt = createdAt, updatedAt = updatedAt,
)

fun Record.toEntity(now: Long) = RecordEntity(
    id = id, type = type, categoryKey = category.name, title = title, summary = summary, merchantName = merchant,
    amountPaise = amountPaise, dueAt = dueAt, eventAt = eventAt, expiresAt = expiresAt, reference = reference,
    importance = importance, status = status, confidence = confidence, source = source, sourcePackage = sourcePackage,
    sourceLabel = sourceLabel, notificationId = notificationId, profileId = profileId, extrasJson = extras.toJson(),
    materialized = materialized, createdAt = if (createdAt == 0L) now else createdAt, updatedAt = now,
)

fun TransactionRow.toDomain() = TransactionItem(
    recordId = recordId, amountPaise = amountPaise, direction = direction, merchant = merchant, accountRef = accountRef,
    occurredAt = occurredAt, category = Category.fromKey(categoryKey), confidence = confidence,
    userCorrected = userCorrected, vehicleId = vehicleId, sourceLabel = sourceLabel,
)

fun BillRow.toDomain() = BillItem(recordId, biller, amountPaise, dueAt, paidAt, recurrence, status)

fun SubscriptionEntity.toDomain() = Subscription(id, name, amountPaise, period, nextRenewalAt, autoDetected, active, profileId)

fun DocumentEntity.toDomain(tags: List<String> = emptyList()) = Document(
    id = id, title = title, docType = docType, fileName = fileName, mimeType = mimeType, sizeBytes = sizeBytes,
    sha256 = sha256, expiresAt = expiresAt, issuedAt = issuedAt, documentNumber = documentNumber, ocrText = ocrText,
    notes = notes, sensitive = sensitive, needsReview = needsReview, vehicleId = vehicleId, policyId = policyId, profileId = profileId,
    tags = tags, createdAt = createdAt,
)

fun DocumentWithTags.toDomain() = document.toDomain(tags.map { it.name })

fun VehicleEntity.toDomain() = Vehicle(
    id, name, brand, model, regNumber, fuelType, purchasedAt, pucExpiresAt, lastServiceAt, nextServiceAt,
    warrantyExpiresAt, loanEmiPaise, emiDayOfMonth, profileId, notes,
)

fun InsurancePolicyEntity.toDomain() = InsurancePolicy(id, insurer, policyNumber, kind, startAt, expiresAt, premiumPaise, vehicleId, profileId, notes)

fun WarrantyEntity.toDomain() = Warranty(id, product, purchasedAt, expiresAt, documentId, vehicleId, profileId)

fun ReminderEntity.toDomain() = Reminder(id, title, body, fireAt, target, targetId, leadDays, state, auto)

fun ProfileEntity.toDomain() = Profile(id, name, relation)

fun AppSourceEntity.toDomain() = AppSource(
    packageName = packageName, label = label, kind = kind, enabled = enabled,
    suggested = KnownApps.find(packageName) != null, notificationCount = notificationCount, lastSeenAt = lastSeenAt,
)
