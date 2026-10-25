package kz.alem.corporation.fintracker.data

import io.objectbox.Box
import io.objectbox.Property
import io.objectbox.query.QueryBuilder.StringOrder
import kz.alem.corporation.fintracker.data.model.Account
import kz.alem.corporation.fintracker.data.model.Account_
import kz.alem.corporation.fintracker.data.model.Category
import kz.alem.corporation.fintracker.data.model.CategoryGroup
import kz.alem.corporation.fintracker.data.model.CategoryGroup_
import kz.alem.corporation.fintracker.data.model.Category_
import kz.alem.corporation.fintracker.data.model.Currency
import kz.alem.corporation.fintracker.data.model.Currency_
import kz.alem.corporation.fintracker.data.model.Operation
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.data.model.Operation_
import kz.alem.corporation.fintracker.data.model.Plan
import kz.alem.corporation.fintracker.data.model.Plan_
import kz.alem.corporation.fintracker.data.model.SyncEntity

// Sync-only store helpers (port of ObjectBoxStore+Sync.swift). None of these stamp
// updatedAt or mark rows dirty.

private fun <T> Box<T>.unsynced(syncedProperty: Property<T>): List<T> =
    query(syncedProperty.equal(false)).build().use { it.find() }

private fun <T> Box<T>.byRemoteId(property: Property<T>, remoteId: String): T? =
    query(property.equal(remoteId, StringOrder.CASE_SENSITIVE)).build().use { it.findFirst() }

fun FinanceStore.unsyncedCurrencies(): List<Currency> = currencyBox.unsynced(Currency_.synced)
fun FinanceStore.unsyncedAccounts(): List<Account> = accountBox.unsynced(Account_.synced)
fun FinanceStore.unsyncedCategoryGroups(): List<CategoryGroup> = categoryGroupBox.unsynced(CategoryGroup_.synced)
fun FinanceStore.unsyncedCategories(): List<Category> = categoryBox.unsynced(Category_.synced)
fun FinanceStore.unsyncedOperations(): List<Operation> = operationBox.unsynced(Operation_.synced)
fun FinanceStore.unsyncedPlans(): List<Plan> = planBox.unsynced(Plan_.synced)

fun FinanceStore.hasUnsyncedEntities(): Boolean =
    unsyncedCurrencies().isNotEmpty() ||
        unsyncedAccounts().isNotEmpty() ||
        unsyncedCategoryGroups().isNotEmpty() ||
        unsyncedCategories().isNotEmpty() ||
        unsyncedOperations().isNotEmpty() ||
        unsyncedPlans().isNotEmpty()

private fun <T : SyncEntity> Box<T>.markAllUnsynced() {
    val entities = all
    entities.forEach { it.synced = false }
    put(entities)
}

/**
 * Marks uploaded rows synced. Re-reads each row and skips any that were edited while the
 * upload was in flight (their updatedAt moved on), so neither the edit nor its dirty flag
 * is lost.
 */
private fun <T : SyncEntity> Box<T>.markUploaded(uploaded: List<T>) {
    if (uploaded.isEmpty()) return
    val current = uploaded.mapNotNull { entity ->
        get(getId(entity))?.takeIf { it.updatedAt == entity.updatedAt }?.also { it.synced = true }
    }
    put(current)
}

/**
 * Forces a full re-upload. Needed after the cloud copy was wiped independently of local
 * data, otherwise the next upload finds nothing dirty and the cloud silently stays empty.
 */
fun FinanceStore.markAllUnsynced() {
    runInTx {
        currencyBox.markAllUnsynced()
        accountBox.markAllUnsynced()
        categoryGroupBox.markAllUnsynced()
        categoryBox.markAllUnsynced()
        operationBox.markAllUnsynced()
        planBox.markAllUnsynced()
    }
}

fun FinanceStore.findCurrencyByRemoteId(id: String) = currencyBox.byRemoteId(Currency_.remoteId, id)
fun FinanceStore.findAccountByRemoteId(id: String) = accountBox.byRemoteId(Account_.remoteId, id)
fun FinanceStore.findCategoryGroupByRemoteId(id: String) = categoryGroupBox.byRemoteId(CategoryGroup_.remoteId, id)
fun FinanceStore.findCategoryByRemoteId(id: String) = categoryBox.byRemoteId(Category_.remoteId, id)
fun FinanceStore.findOperationByRemoteId(id: String) = operationBox.byRemoteId(Operation_.remoteId, id)
fun FinanceStore.findPlanByRemoteId(id: String) = planBox.byRemoteId(Plan_.remoteId, id)

@JvmName("markCurrenciesSynced")
fun FinanceStore.markSynced(entities: List<Currency>) = currencyBox.markUploaded(entities)
@JvmName("markAccountsSynced")
fun FinanceStore.markSynced(entities: List<Account>) = accountBox.markUploaded(entities)
@JvmName("markCategoryGroupsSynced")
fun FinanceStore.markSynced(entities: List<CategoryGroup>) = categoryGroupBox.markUploaded(entities)
@JvmName("markCategoriesSynced")
fun FinanceStore.markSynced(entities: List<Category>) = categoryBox.markUploaded(entities)
@JvmName("markOperationsSynced")
fun FinanceStore.markSynced(entities: List<Operation>) = operationBox.markUploaded(entities)
@JvmName("markPlansSynced")
fun FinanceStore.markSynced(entities: List<Plan>) = planBox.markUploaded(entities)

fun FinanceStore.deleteCurrencyByRemoteId(id: String) { findCurrencyByRemoteId(id)?.let { currencyBox.remove(it) } }
fun FinanceStore.deleteAccountByRemoteId(id: String) { findAccountByRemoteId(id)?.let { accountBox.remove(it) } }
fun FinanceStore.deleteCategoryGroupByRemoteId(id: String) { findCategoryGroupByRemoteId(id)?.let { categoryGroupBox.remove(it) } }
fun FinanceStore.deleteCategoryByRemoteId(id: String) { findCategoryByRemoteId(id)?.let { categoryBox.remove(it) } }
fun FinanceStore.deleteOperationByRemoteId(id: String) { findOperationByRemoteId(id)?.let { operationBox.remove(it) } }
fun FinanceStore.deletePlanByRemoteId(id: String) { findPlanByRemoteId(id)?.let { planBox.remove(it) } }

/**
 * Rebuilds every account's derived balance from its mainBalance plus all confirmed
 * operations. Does not stamp, so sync state is preserved.
 */
fun FinanceStore.recalculateAllAccountBalances() {
    val accounts = accountBox.all
    val balances = HashMap<Long, Double>(accounts.size)
    accounts.forEach { balances[it.id] = it.mainBalance }

    for (operation in operationBox.all) {
        val accountId = operation.account.targetId
        if (!operation.confirmed || accountId == 0L) continue
        val amount = operation.amount * operation.quantity
        when (operation.typeEnum) {
            OperationType.EXPENSE -> balances[accountId] = (balances[accountId] ?: 0.0) - amount
            OperationType.INCOME -> balances[accountId] = (balances[accountId] ?: 0.0) + amount
            OperationType.TRANSFER -> {
                balances[accountId] = (balances[accountId] ?: 0.0) - amount
                val addAccountId = operation.addAccount.targetId
                if (addAccountId != 0L) {
                    val received = calculateSum(amount, operation.rate, operation.mainRate)
                    balances[addAccountId] = (balances[addAccountId] ?: 0.0) + received
                }
            }
        }
    }

    accounts.forEach { it.balance = (balances[it.id] ?: it.mainBalance).roundCents() }
    accountBox.put(accounts)
}

/**
 * Repairs operations whose derived sum was never computed. Deterministic and a no-op
 * once data is correct, so it is safe to run on every launch.
 */
fun FinanceStore.backfillMissingOperationSums() {
    val broken = operationBox.all.filter { it.sum == 0.0 && it.amount != 0.0 && it.quantity != 0.0 }
    if (broken.isEmpty()) return
    broken.forEach { it.sum = calculateSum(it.amount * it.quantity, it.rate, it.mainRate) }
    operationBox.put(broken)
}
