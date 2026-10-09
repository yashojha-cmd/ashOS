package app.ash.presentation

import app.ash.domain.model.BillItem
import app.ash.domain.model.Category
import app.ash.domain.model.Record
import app.ash.domain.model.RecordStatus
import app.ash.domain.model.RecordType
import app.ash.domain.model.TransactionItem
import app.ash.domain.model.TxnDirection
import app.ash.domain.repository.InboxFilter
import app.ash.domain.repository.MoneySummary
import app.ash.domain.repository.RecordRepository
import app.ash.presentation.inbox.InboxViewModel
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeRecords : RecordRepository {
        val items = MutableStateFlow(
            listOf(
                Record(id = 1, type = RecordType.BILL, category = Category.BILLS, title = "BESCOM", summary = "", status = RecordStatus.UNREAD),
                Record(id = 2, type = RecordType.TRANSACTION, category = Category.FINANCE, title = "Swiggy", summary = "", status = RecordStatus.REVIEW),
                Record(id = 3, type = RecordType.DELIVERY, category = Category.DELIVERIES, title = "Amazon", summary = "", status = RecordStatus.HANDLED),
            ),
        )
        val confirmed = mutableListOf<Long>()
        override fun observeForAttention() = items
        override fun observeInbox(filter: InboxFilter, category: Category?): Flow<List<Record>> =
            items.map { list -> list.filter { it.status in filter.statuses && (category == null || it.category == category) } }
        override fun observeCount(status: RecordStatus) = items.map { l -> l.count { it.status == status } }
        override fun observe(id: Long) = items.map { l -> l.firstOrNull { it.id == id } }
        override suspend fun get(id: Long) = items.value.firstOrNull { it.id == id }
        override suspend fun addManual(record: Record) = 0L
        override suspend fun update(record: Record) = Unit
        override suspend fun confirm(record: Record) { confirmed += record.id; setStatus(record.id, RecordStatus.HANDLED) }
        override suspend fun setStatus(id: Long, status: RecordStatus) { items.value = items.value.map { if (it.id == id) it.copy(status = status) else it } }
        override suspend fun delete(id: Long) = Unit
        override fun observeTransactions(limit: Int): Flow<List<TransactionItem>> = flowOf(emptyList())
        override fun observeMonthSummary(from: Long, to: Long) = flowOf(MoneySummary(0, 0, 0))
        override suspend fun correctTransaction(recordId: Long, amountPaise: Long, direction: TxnDirection, merchant: String?, category: Category) = Unit
        override fun observeBills(limit: Int): Flow<List<BillItem>> = flowOf(emptyList())
        override suspend fun setBillPaid(recordId: Long, paid: Boolean) = Unit
        override fun observeTransactionsSince(from: Long): Flow<List<TransactionItem>> = flowOf(emptyList())
        override suspend fun setSalary(recordId: Long, isSalary: Boolean) = Unit
        override fun observeIsSalary(recordId: Long): Flow<Boolean?> = flowOf(null)
    }

    @Test fun filtersAndActions() = runTest {
        val repo = FakeRecords()
        val vm = InboxViewModel(repo)
        vm.state.test {
            var s = awaitItem()
            while (s.loading) s = awaitItem()
            // Default view is "Needs you": the handled delivery is not shown.
            assertThat(s.items.map { it.title }).containsExactly("BESCOM", "Swiggy")

            vm.setFilter(InboxFilter.REVIEW)
            s = awaitItem()
            assertThat(s.items.map { it.title }).containsExactly("Swiggy")

            vm.confirm(s.items.single())
            assertThat(awaitItem().items).isEmpty()
            assertThat(repo.confirmed).containsExactly(2L)

            vm.setFilter(InboxFilter.ALL)
            vm.setCategory(Category.BILLS)
            s = expectMostRecentItem()
            assertThat(s.items.map { it.title }).containsExactly("BESCOM")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
