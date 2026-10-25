package kz.alem.corporation.fintracker.data

import android.content.Context
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.query.QueryBuilder.StringOrder
import io.objectbox.query.QueryCondition
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kz.alem.corporation.fintracker.data.model.Account
import kz.alem.corporation.fintracker.data.model.Account_
import kz.alem.corporation.fintracker.data.model.Category
import kz.alem.corporation.fintracker.data.model.CategoryGroup
import kz.alem.corporation.fintracker.data.model.CategoryType
import kz.alem.corporation.fintracker.data.model.categoryType
import kz.alem.corporation.fintracker.data.model.Category_
import kz.alem.corporation.fintracker.data.model.Collections
import kz.alem.corporation.fintracker.data.model.CompareType
import kz.alem.corporation.fintracker.data.model.ContainType
import kz.alem.corporation.fintracker.data.model.Currency
import kz.alem.corporation.fintracker.data.model.CategoryGroup_
import kz.alem.corporation.fintracker.data.model.Currency_
import kz.alem.corporation.fintracker.data.model.DatabaseError
import kz.alem.corporation.fintracker.data.model.DatabaseException
import kz.alem.corporation.fintracker.data.model.MyObjectBox
import kz.alem.corporation.fintracker.data.model.Operation
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.data.model.Operation_
import kz.alem.corporation.fintracker.data.model.OperationsFilter
import kz.alem.corporation.fintracker.data.model.Period
import kz.alem.corporation.fintracker.data.model.Plan
import kz.alem.corporation.fintracker.data.model.Plan_
import kz.alem.corporation.fintracker.data.model.SyncEntity
import kz.alem.corporation.fintracker.data.model.Tombstone
import java.util.UUID

/**
 * Local database, a port of the iOS ObjectBoxStore. All functions block, so call them
 * off the main thread.
 *
 * Every local write stamps the sync metadata and fires [localChanges] (which schedules a
 * debounced sync). Writes that come from the server go through the sync helpers in
 * FinanceStoreSync.kt instead, which never stamp. [changes] ticks on both so screens can
 * reload.
 */
class FinanceStore(context: Context) {

    val boxStore: BoxStore = MyObjectBox.builder()
        .androidContext(context.applicationContext)
        .name("FinTracker")
        .build()

    val currencyBox: Box<Currency> = boxStore.boxFor()
    val categoryGroupBox: Box<CategoryGroup> = boxStore.boxFor()
    val categoryBox: Box<Category> = boxStore.boxFor()
    val accountBox: Box<Account> = boxStore.boxFor()
    val operationBox: Box<Operation> = boxStore.boxFor()
    val planBox: Box<Plan> = boxStore.boxFor()
    val tombstoneBox: Box<Tombstone> = boxStore.boxFor()

    private val _changes = MutableStateFlow(0L)
    /** Ticks after any data change (local or from sync). */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private val _localChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    /** Emits after local user edits only; drives the debounced sync. */
    val localChanges: SharedFlow<Unit> = _localChanges.asSharedFlow()

    // region Change tracking

    /** Notifications raised inside [runInTx] on this thread, held until the commit. */
    private class PendingTx(var depth: Int = 0, var changed: Boolean = false, var local: Boolean = false)

    private val pendingTx = ThreadLocal.withInitial { PendingTx() }

    /**
     * Runs [block] in one transaction and fires change notifications only after it
     * commits. Notifying from inside the transaction let collectors on other threads
     * re-read the old data and then never reload, so use this instead of
     * boxStore.runInTx.
     */
    fun runInTx(block: () -> Unit) {
        val pending = pendingTx.get()
        pending.depth++
        var committed = false
        try {
            boxStore.runInTx { block() }
            committed = true
        } finally {
            pending.depth--
        }
        if (pending.depth > 0) return
        // A rolled-back transaction changed nothing, so drop what it queued.
        val changed = pending.changed && committed
        val local = pending.local
        pending.changed = false
        pending.local = false
        if (changed) {
            _changes.update { it + 1 }
            if (local) _localChanges.tryEmit(Unit)
        }
    }

    private fun notifyDataChanged() {
        val pending = pendingTx.get()
        if (pending.depth > 0) {
            pending.changed = true
            pending.local = true
            return
        }
        _changes.update { it + 1 }
        _localChanges.tryEmit(Unit)
    }

    /** Refreshes screens without scheduling an upload (sync results, full resets). */
    fun notifyExternalChange() {
        val pending = pendingTx.get()
        if (pending.depth > 0) {
            pending.changed = true
            return
        }
        _changes.update { it + 1 }
    }

    private fun stampLocalWrite(entity: SyncEntity) {
        if (entity.remoteId.isEmpty()) entity.remoteId = UUID.randomUUID().toString()
        entity.updatedAt = System.currentTimeMillis()
        entity.synced = false
    }

    private fun addTombstone(remoteId: String, collectionName: String) {
        if (remoteId.isEmpty()) return
        tombstoneBox.put(Tombstone(remoteId = remoteId, collectionName = collectionName))
    }

    // endregion

    // region Tombstones

    fun readTombstones(): List<Tombstone> = tombstoneBox.all

    fun deleteTombstones(tombstones: List<Tombstone>) = tombstoneBox.remove(tombstones)

    fun clearTombstones() = tombstoneBox.removeAll()

    // endregion

    // region Currencies

    fun createCurrency(currency: Currency): Long {
        stampLocalWrite(currency)
        val id = currencyBox.put(currency)
        notifyDataChanged()
        return id
    }

    fun createCurrencies(currencies: List<Currency>) {
        currencies.forEach(::stampLocalWrite)
        currencyBox.put(currencies)
        notifyDataChanged()
    }

    fun cleanCurrencyDefaults() {
        val results = currencyBox.query(Currency_.useAsDefault.equal(true)).build().use { it.find() }
        results.forEach {
            it.useAsDefault = false
            stampLocalWrite(it)
        }
        currencyBox.put(results)
        notifyDataChanged()
    }

    fun readCurrencies(): List<Currency> = currencyBox.all.sortedBy { it.name }

    fun updateCurrency(currency: Currency): Long = createCurrency(currency)

    fun deleteCurrency(id: Long) {
        val count = accountBox.query(Account_.currencyId.equal(id)).build().use { it.count() }
        if (count > 0) throw DatabaseException(DatabaseError.CURRENCY_IS_USED)
        currencyBox.get(id)?.let { addTombstone(it.remoteId, Collections.CURRENCIES) }
        currencyBox.remove(id)
        notifyDataChanged()
    }

    fun getCurrency(id: Long?): Currency? = id?.takeIf { it != 0L }?.let { currencyBox.get(it) }

    fun getDefaultCurrency(): Currency? =
        currencyBox.query().orderDesc(Currency_.useAsDefault).build().use { it.findFirst() }

    // endregion

    // region Category groups

    fun createCategoryGroup(group: CategoryGroup): Long {
        stampLocalWrite(group)
        val id = categoryGroupBox.put(group)
        notifyDataChanged()
        return id
    }

    fun createCategoryGroups(groups: List<CategoryGroup>) {
        groups.forEach(::stampLocalWrite)
        categoryGroupBox.put(groups)
        notifyDataChanged()
    }

    fun cleanCategoryGroupDefaults() {
        val results = categoryGroupBox.query(CategoryGroup_.useAsDefault.equal(true)).build().use { it.find() }
        results.forEach {
            it.useAsDefault = false
            stampLocalWrite(it)
        }
        categoryGroupBox.put(results)
        notifyDataChanged()
    }

    fun readCategoryGroups(): List<CategoryGroup> = categoryGroupBox.all.sortedBy { it.name }

    fun updateCategoryGroup(group: CategoryGroup): Long = createCategoryGroup(group)

    fun deleteCategoryGroup(id: Long) {
        val count = categoryBox.query(Category_.categoryGroupId.equal(id)).build().use { it.count() }
        if (count > 0) throw DatabaseException(DatabaseError.CATEGORY_GROUP_IS_USED)
        categoryGroupBox.get(id)?.let { addTombstone(it.remoteId, Collections.CATEGORY_GROUPS) }
        categoryGroupBox.remove(id)
        notifyDataChanged()
    }

    fun getCategoryGroup(id: Long?): CategoryGroup? = id?.takeIf { it != 0L }?.let { categoryGroupBox.get(it) }

    fun getDefaultCategoryGroup(): CategoryGroup? =
        categoryGroupBox.query().orderDesc(CategoryGroup_.useAsDefault).build().use { it.findFirst() }

    // endregion

    // region Categories

    fun createCategory(category: Category): Long {
        stampLocalWrite(category)
        val id = categoryBox.put(category)
        notifyDataChanged()
        return id
    }

    fun createCategories(categories: List<Category>) {
        categories.forEach(::stampLocalWrite)
        categoryBox.put(categories)
        notifyDataChanged()
    }

    fun cleanCategoryDefaults() {
        val results = categoryBox.query(Category_.useAsDefault.equal(true)).build().use { it.find() }
        results.forEach {
            it.useAsDefault = false
            stampLocalWrite(it)
        }
        categoryBox.put(results)
        notifyDataChanged()
    }

    fun readCategories(onlyVisible: Boolean): List<Category> {
        val categories = if (onlyVisible) {
            categoryBox.query(Category_.visible.equal(true)).build().use { it.find() }
        } else {
            categoryBox.all
        }
        return categories.sortedBy { it.name }
    }

    /** Visible categories usable for [operationType] (mixed ones always qualify). */
    fun readActiveCategoriesByOperationType(operationType: OperationType?): List<Category> {
        val condition = if (operationType == null) {
            Category_.visible.equal(true)
        } else {
            Category_.visible.equal(true).and(
                Category_.type.equal(CategoryType.MIXED.raw, StringOrder.CASE_SENSITIVE)
                    .or(Category_.type.equal(operationType.categoryType.raw, StringOrder.CASE_SENSITIVE))
            )
        }
        return categoryBox.query(condition).build().use { it.find() }.sortedBy { it.name }
    }

    fun updateCategory(category: Category): Long = createCategory(category)

    fun deleteCategory(id: Long) {
        // iOS checked the category-group column here by mistake; the real dependency of a
        // category is the operations that use it.
        val count = operationBox.query(Operation_.categoryId.equal(id)).build().use { it.count() }
        if (count > 0) throw DatabaseException(DatabaseError.CATEGORY_IS_USED)
        categoryBox.get(id)?.let { addTombstone(it.remoteId, Collections.CATEGORIES) }
        categoryBox.remove(id)
        notifyDataChanged()
    }

    fun getCategory(id: Long?): Category? = id?.takeIf { it != 0L }?.let { categoryBox.get(it) }

    fun getDefaultCategory(operationType: OperationType): Category? {
        val condition = Category_.visible.equal(true).and(
            Category_.type.equal(CategoryType.MIXED.raw, StringOrder.CASE_SENSITIVE)
                .or(Category_.type.equal(operationType.categoryType.raw, StringOrder.CASE_SENSITIVE))
        )
        return categoryBox.query(condition).orderDesc(Category_.useAsDefault).build().use { it.findFirst() }
    }

    // endregion

    // region Accounts

    fun createAccount(account: Account): Long {
        stampLocalWrite(account)
        val id = accountBox.put(account)
        notifyDataChanged()
        return id
    }

    fun createAccounts(accounts: List<Account>) {
        accounts.forEach(::stampLocalWrite)
        accountBox.put(accounts)
        notifyDataChanged()
    }

    fun cleanAccountDefaults() {
        val results = accountBox.query(Account_.useAsDefault.equal(true)).build().use { it.find() }
        results.forEach {
            it.useAsDefault = false
            stampLocalWrite(it)
        }
        accountBox.put(results)
        notifyDataChanged()
    }

    fun readAccounts(onlyVisible: Boolean, exceptId: Long? = null): List<Account> {
        var condition: QueryCondition<Account> = Account_.id.notEqual(exceptId ?: 0L)
        if (onlyVisible) condition = condition.and(Account_.visible.equal(true))
        return accountBox.query(condition).build().use { it.find() }.sortedBy { it.name }
    }

    fun updateAccount(account: Account): Long = createAccount(account)

    fun deleteAccount(id: Long) {
        val count = operationBox.query(Operation_.accountId.equal(id).or(Operation_.addAccountId.equal(id)))
            .build().use { it.count() }
        if (count > 0) throw DatabaseException(DatabaseError.ACCOUNT_IS_USED)
        accountBox.get(id)?.let { addTombstone(it.remoteId, Collections.ACCOUNTS) }
        accountBox.remove(id)
        notifyDataChanged()
    }

    fun getAccount(id: Long?): Account? = id?.takeIf { it != 0L }?.let { accountBox.get(it) }

    fun getDefaultAccount(exceptId: Long?): Account? =
        accountBox.query(Account_.visible.equal(true).and(Account_.id.notEqual(exceptId ?: 0L)))
            .orderDesc(Account_.useAsDefault)
            .build().use { it.findFirst() }

    /** Applies a newly created (or, with a negative [balance], deleted) operation to its accounts. */
    fun recalcAccountBalance(operationId: Long, balance: Double) {
        val operation = operationBox.get(operationId) ?: return
        setAccountBalance(
            balance = if (operation.typeEnum == OperationType.INCOME) -balance else balance,
            id = operation.account.targetId,
        )
        if (operation.typeEnum == OperationType.TRANSFER && operation.addAccount.targetId != 0L) {
            setAccountBalance(
                balance = -calculateSum(balance, operation.rate, operation.mainRate),
                id = operation.addAccount.targetId,
            )
        }
    }

    /** Moves the balance effect of an edited operation from its old state to the new one. */
    fun recalcAccountsBalance(prevOperation: Operation, newOperation: Operation, balance: Double) {
        val prevBalance = if (prevOperation.confirmed) prevOperation.amount * prevOperation.quantity else 0.0
        if (!prevOperation.confirmed && !newOperation.confirmed) return
        val isIncome = newOperation.typeEnum == OperationType.INCOME

        if (prevOperation.account.targetId == newOperation.account.targetId) {
            setAccountBalance(
                balance = if (isIncome) prevBalance - balance else -(prevBalance - balance),
                id = newOperation.account.targetId,
            )
        } else {
            setAccountBalance(if (isIncome) prevBalance else -prevBalance, prevOperation.account.targetId)
            setAccountBalance(if (isIncome) -balance else balance, newOperation.account.targetId)
        }

        val addAccountId = newOperation.addAccount.targetId
        val prevAddAccountId = prevOperation.addAccount.targetId
        if (newOperation.typeEnum == OperationType.TRANSFER && addAccountId != 0L && prevAddAccountId != 0L) {
            val balanceRated = calculateSum(balance, newOperation.rate, newOperation.mainRate)
            val prevBalanceRated = calculateSum(prevBalance, prevOperation.rate, prevOperation.mainRate)
            if (prevAddAccountId == addAccountId) {
                setAccountBalance(prevBalanceRated - balanceRated, addAccountId)
            } else {
                setAccountBalance(prevBalanceRated, prevAddAccountId)
                setAccountBalance(-balanceRated, addAccountId)
            }
        }
    }

    /** Subtracts [balance] from the account's current balance. */
    fun setAccountBalance(balance: Double, id: Long) {
        val account = accountBox.get(id) ?: return
        account.balance = (account.balance - balance).roundCents()
        stampLocalWrite(account)
        accountBox.put(account)
    }

    // endregion

    // region Operations

    fun createOperation(operation: Operation): Long {
        stampLocalWrite(operation)
        val id = operationBox.put(operation)
        notifyDataChanged()
        return id
    }

    fun createOperations(operations: List<Operation>) {
        operations.forEach(::stampLocalWrite)
        operationBox.put(operations)
        notifyDataChanged()
    }

    fun readOperations(filter: OperationsFilter?): List<Operation> {
        var condition: QueryCondition<Operation> = Operation_.amount.greater(0.0)
        var accountIds: Set<Long>? = null
        var categoryIds: Set<Long>? = null

        if (filter != null) {
            if (filter.period != Period.ALL_TIME) {
                condition = condition.and(Operation_.date.between(filter.beginDate, filter.endDate))
            }
            if (filter.operationTypes.isNotEmpty()) {
                condition = condition.and(
                    Operation_.type.oneOf(filter.operationTypes.map { it.raw }.toTypedArray(), StringOrder.CASE_SENSITIVE)
                )
            }
            if (filter.currencies.isNotEmpty()) {
                val ids = accountBox.query(Account_.currencyId.oneOf(filter.currencies.map { it.id }.toLongArray()))
                    .build().use { it.findIds() }.toSet()
                accountIds = ids
            }
            if (filter.accounts.isNotEmpty()) {
                val ids = filter.accounts.map { it.id }.toSet()
                accountIds = accountIds?.intersect(ids) ?: ids
            }
            if (filter.categoryGroups.isNotEmpty()) {
                val ids = categoryBox.query(Category_.categoryGroupId.oneOf(filter.categoryGroups.map { it.id }.toLongArray()))
                    .build().use { it.findIds() }.toSet()
                categoryIds = ids
            }
            if (filter.categories.isNotEmpty()) {
                val ids = filter.categories.map { it.id }.toSet()
                categoryIds = categoryIds?.intersect(ids) ?: ids
            }
            if (filter.sum != 0.0) {
                condition = condition.and(
                    when (filter.compareType) {
                        CompareType.EQUAL -> Operation_.sum.between(filter.sum - SUM_TOLERANCE, filter.sum + SUM_TOLERANCE)
                        CompareType.LESS -> Operation_.sum.less(filter.sum)
                        CompareType.MORE -> Operation_.sum.greater(filter.sum)
                    }
                )
            }
        }

        var operations = operationBox.query(condition).orderDesc(Operation_.date).build().use { it.find() }.asSequence()

        accountIds?.let { ids ->
            operations = operations.filter { it.account.targetId in ids || it.addAccount.targetId in ids }
        }
        categoryIds?.let { ids ->
            operations = operations.filter { it.category.targetId in ids }
        }
        // Note filtering happens in memory: ObjectBox's case-insensitive matching does not
        // handle Kazakh letters.
        if (filter != null && filter.note.isNotEmpty()) {
            val needle = filter.note.lowercase()
            operations = if (filter.containType == ContainType.NOT_CONTAINS) {
                operations.filter { !(it.note ?: "").lowercase().contains(needle) }
            } else {
                operations.filter { (it.note ?: "").lowercase().contains(needle) }
            }
        }
        return operations.toList()
    }

    fun updateOperation(operation: Operation): Long = createOperation(operation)

    fun deleteOperation(id: Long) {
        operationBox.get(id)?.let { addTombstone(it.remoteId, Collections.OPERATIONS) }
        operationBox.remove(id)
        notifyDataChanged()
    }

    fun getOperation(id: Long?): Operation? = id?.takeIf { it != 0L }?.let { operationBox.get(it) }

    // endregion

    // region Plans

    fun createPlan(plan: Plan): Long {
        stampLocalWrite(plan)
        // put() also persists pending ToMany changes (categories / categoryGroups).
        val id = planBox.put(plan)
        notifyDataChanged()
        return id
    }

    fun readPlans(): List<Plan> = planBox.query().orderDesc(Plan_.endDate).build().use { it.find() }

    fun updatePlan(plan: Plan): Long = createPlan(plan)

    fun deletePlan(id: Long) {
        planBox.get(id)?.let { addTombstone(it.remoteId, Collections.PLANS) }
        planBox.remove(id)
        notifyDataChanged()
    }

    fun getPlan(id: Long?): Plan? = id?.takeIf { it != 0L }?.let { planBox.get(it) }

    // endregion

    /** Wipes every entity box (not tombstones). */
    fun clearAll() {
        runInTx {
            planBox.removeAll()
            operationBox.removeAll()
            accountBox.removeAll()
            currencyBox.removeAll()
            categoryBox.removeAll()
            categoryGroupBox.removeAll()
        }
        notifyExternalChange()
    }

    private companion object {
        const val SUM_TOLERANCE = 0.005
    }
}
