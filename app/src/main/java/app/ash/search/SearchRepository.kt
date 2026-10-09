package app.ash.search

import app.ash.common.IoDispatcher
import app.ash.common.TimeProvider
import app.ash.data.local.dao.DocumentDao
import app.ash.data.local.dao.PolicyDao
import app.ash.data.local.dao.ProfileDao
import app.ash.data.local.dao.RecordDao
import app.ash.data.local.dao.SubscriptionDao
import app.ash.data.local.dao.VehicleDao
import app.ash.data.local.dao.WarrantyDao
import app.ash.data.local.entities.RecordEntity
import app.ash.data.parser.ParseContext
import app.ash.data.repository.toDomain
import app.ash.domain.model.Category
import app.ash.domain.model.Document
import app.ash.domain.model.InsuranceKind
import app.ash.domain.model.InsurancePolicy
import app.ash.domain.model.Record
import app.ash.domain.model.Subscription
import app.ash.domain.model.Vehicle
import app.ash.domain.model.Warranty
import app.ash.intelligence.IntelligenceEngine
import app.ash.intelligence.SearchQueryParser
import app.ash.intelligence.StructuredQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SearchResults(
    val query: StructuredQuery,
    val records: List<Record> = emptyList(),
    val documents: List<Document> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val policies: List<InsurancePolicy> = emptyList(),
    val warranties: List<Warranty> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
) {
    val isEmpty: Boolean
        get() = records.isEmpty() && documents.isEmpty() && vehicles.isEmpty() && policies.isEmpty() && warranties.isEmpty() && subscriptions.isEmpty()
    val total: Int get() = records.size + documents.size + vehicles.size + policies.size + warranties.size + subscriptions.size
}

/**
 * Local search across everything ASH stores (§14). Records and documents use Room FTS4;
 * the small entity tables are matched in memory with category synonyms.
 */
@Singleton
class SearchRepository @Inject constructor(
    private val records: RecordDao,
    private val documents: DocumentDao,
    private val vehicles: VehicleDao,
    private val policies: PolicyDao,
    private val warranties: WarrantyDao,
    private val subscriptions: SubscriptionDao,
    private val profiles: ProfileDao,
    private val engine: IntelligenceEngine,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun search(raw: String): SearchResults = withContext(io) {
        val now = time.nowMillis()
        var query = engine.parseQuery(raw, ParseContext(time.today(), time.zone(), now))
        if (query.isEmpty) return@withContext SearchResults(query)

        // A term that names a profile ("dad") becomes a profile filter.
        val profile = profiles.getAll().firstOrNull { p -> query.terms.any { it.equals(p.name, ignoreCase = true) } }
        if (profile != null) query = query.copy(terms = query.terms.filterNot { it.equals(profile.name, ignoreCase = true) })
        val profileId = profile?.id
        val terms = query.terms
        val horizon = now + (if (query.dueSoon) 7L else 60L) * DAY

        // ---- Records ----
        val fts = SearchQueryParser.ftsExpression(terms)
        var candidates: List<RecordEntity> = when {
            fts != null -> runCatching { records.search(fts) }.getOrDefault(emptyList())
            query.amountPaise != null -> amountCandidates(query.amountPaise)
            query.fromMillis != null -> records.byDateRange(query.fromMillis, query.toMillis ?: now)
            query.expiring || query.dueSoon -> records.openWithDeadlineBefore(horizon)
            query.categories.isNotEmpty() -> records.byCategories(query.categories.map { it.name })
            profileId != null -> records.byCategories(Category.entries.map { it.name })
            else -> emptyList()
        }
        if (candidates.isEmpty() && fts != null && query.categories.isNotEmpty()) {
            candidates = records.byCategories(query.categories.map { it.name })
        }
        val recordResults = candidates.asSequence()
            .filter { r -> query.amountPaise == null || r.amountPaise?.let { near(it, query.amountPaise) } == true }
            .filter { r -> query.fromMillis == null || (r.anchor() >= query.fromMillis && r.anchor() < (query.toMillis ?: Long.MAX_VALUE)) }
            .filter { r -> query.categories.isEmpty() || Category.fromKey(r.categoryKey) in query.categories || fts != null && matchCount(r.text(), terms) > 0 }
            .filter { r -> !(query.expiring || query.dueSoon) || ((r.dueAt ?: r.expiresAt)?.let { it < horizon } == true) }
            .filter { r -> profileId == null || r.profileId == profileId }
            .sortedWith(compareByDescending<RecordEntity> { matchCount(it.text(), terms) }.thenByDescending { it.createdAt })
            .take(100)
            .map { it.toDomain() }
            .toList()

        // ---- Documents ----
        val docCandidates = buildMap {
            if (fts != null) runCatching { documents.search(fts) }.getOrDefault(emptyList()).forEach { put(it.id, it) }
            documents.getAllActive().forEach { d ->
                val text = "${d.title} ${d.docType.name.replace('_', ' ')} ${d.documentNumber.orEmpty()} ${d.notes.orEmpty()}".lowercase()
                val byTerms = terms.isNotEmpty() && matchCount(text, terms) > 0
                val byCategory = terms.isEmpty() && Category.DOCUMENTS in query.categories
                val byExpiry = query.expiring && d.expiresAt != null && d.expiresAt < now + 90 * DAY
                if (byTerms || byCategory || byExpiry) put(d.id, d)
            }
        }.values
        val documentResults = docCandidates
            .filter { d -> !query.expiring || (d.expiresAt != null && d.expiresAt < now + 90 * DAY) }
            .filter { d -> query.fromMillis == null || listOfNotNull(d.expiresAt, d.issuedAt, d.createdAt).any { it >= query.fromMillis && it < (query.toMillis ?: Long.MAX_VALUE) } }
            .filter { d -> profileId == null || d.profileId == profileId }
            .sortedBy { it.expiresAt ?: Long.MAX_VALUE }
            .map { d -> d.toDomain(documents.tagNames(d.id)) }

        // ---- Small entity tables (in memory) ----
        val vehicleResults = vehicles.getAll().map { it.toDomain() }.filter { v ->
            val text = "${v.name} ${v.brand.orEmpty()} ${v.model.orEmpty()} ${v.regNumber.orEmpty()} vehicle bike car".lowercase()
            (profileId == null || v.profileId == profileId) && when {
                query.expiring -> listOfNotNull(v.pucExpiresAt, v.nextServiceAt).any { it < horizon }
                terms.isNotEmpty() -> matchCount(text, terms.filterNot { it in GENERIC_VEHICLE_WORDS }) > 0 || terms.all { it in GENERIC_VEHICLE_WORDS } && Category.VEHICLE in query.categories
                else -> Category.VEHICLE in query.categories || profileId != null
            }
        }
        val policyResults = policies.getAll().map { it.toDomain() }.filter { p ->
            val text = "${p.insurer} ${p.policyNumber.orEmpty()} insurance policy ${kindWords(p.kind)}".lowercase()
            (profileId == null || p.profileId == profileId) &&
                (query.insuranceKind == null || p.kind == query.insuranceKind) &&
                (!query.expiring || (p.expiresAt != null && p.expiresAt < horizon)) &&
                (query.amountPaise == null || p.premiumPaise?.let { near(it, query.amountPaise) } == true) &&
                (terms.isEmpty() && (Category.INSURANCE in query.categories || query.expiring || profileId != null) || terms.isNotEmpty() && matchCount(text, terms) > 0)
        }
        val warrantyResults = warranties.getAll().map { it.toDomain() }.filter { w ->
            (profileId == null || w.profileId == profileId) &&
                (!query.expiring || w.expiresAt < horizon) &&
                (terms.isEmpty() && (query.expiring || profileId != null) || matchCount("${w.product} warranty".lowercase(), terms) > 0)
        }
        val subscriptionResults = subscriptionsSnapshot().filter { s ->
            (profileId == null || s.profileId == profileId) &&
                (query.amountPaise == null || near(s.amountPaise, query.amountPaise)) &&
                (terms.isEmpty() && (Category.SUBSCRIPTIONS in query.categories || query.amountPaise != null || profileId != null) ||
                    terms.isNotEmpty() && matchCount("${s.name} subscription".lowercase(), terms) > 0)
        }

        SearchResults(query, recordResults, documentResults, vehicleResults, policyResults, warrantyResults, subscriptionResults)
    }

    private suspend fun subscriptionsSnapshot(): List<Subscription> =
        subscriptions.observeAll().first().map { it.toDomain() }

    private suspend fun amountCandidates(amount: Long): List<RecordEntity> {
        val tolerance = maxOf(100L, amount / 100)
        return records.byAmount(amount - tolerance, amount + tolerance)
    }

    private fun near(value: Long, target: Long) = kotlin.math.abs(value - target) <= maxOf(100L, target / 100)

    private fun RecordEntity.anchor(): Long = eventAt ?: dueAt ?: expiresAt ?: createdAt

    private fun RecordEntity.text(): String =
        "$title $summary ${merchantName.orEmpty()} ${reference.orEmpty()} ${categoryKey.lowercase()} ${sourceLabel.orEmpty()}".lowercase()

    private fun matchCount(text: String, terms: List<String>): Int = terms.count { text.contains(it.lowercase()) }

    private fun kindWords(kind: InsuranceKind) = when (kind) {
        InsuranceKind.VEHICLE -> "vehicle bike car motor"
        InsuranceKind.HEALTH -> "health medical mediclaim"
        InsuranceKind.LIFE -> "life term"
        InsuranceKind.HOME -> "home house"
        InsuranceKind.TRAVEL -> "travel trip"
        InsuranceKind.OTHER -> ""
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        val GENERIC_VEHICLE_WORDS = setOf("vehicle", "bike", "car", "insurance", "policy")
    }
}
