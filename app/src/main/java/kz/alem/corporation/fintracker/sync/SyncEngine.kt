package kz.alem.corporation.fintracker.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kz.alem.corporation.fintracker.AppContainer
import kz.alem.corporation.fintracker.data.FinanceStore
import kz.alem.corporation.fintracker.data.deleteAccountByRemoteId
import kz.alem.corporation.fintracker.data.deleteCategoryByRemoteId
import kz.alem.corporation.fintracker.data.deleteCategoryGroupByRemoteId
import kz.alem.corporation.fintracker.data.deleteCurrencyByRemoteId
import kz.alem.corporation.fintracker.data.deleteOperationByRemoteId
import kz.alem.corporation.fintracker.data.deletePlanByRemoteId
import kz.alem.corporation.fintracker.data.findAccountByRemoteId
import kz.alem.corporation.fintracker.data.findCategoryByRemoteId
import kz.alem.corporation.fintracker.data.findCategoryGroupByRemoteId
import kz.alem.corporation.fintracker.data.findCurrencyByRemoteId
import kz.alem.corporation.fintracker.data.findOperationByRemoteId
import kz.alem.corporation.fintracker.data.findPlanByRemoteId
import kz.alem.corporation.fintracker.data.hasUnsyncedEntities
import kz.alem.corporation.fintracker.data.markAllUnsynced
import kz.alem.corporation.fintracker.data.markSynced
import kz.alem.corporation.fintracker.data.model.Account
import kz.alem.corporation.fintracker.data.model.Category
import kz.alem.corporation.fintracker.data.model.CategoryGroup
import kz.alem.corporation.fintracker.data.model.Collections
import kz.alem.corporation.fintracker.data.model.Currency
import kz.alem.corporation.fintracker.data.model.Operation
import kz.alem.corporation.fintracker.data.model.Plan
import kz.alem.corporation.fintracker.data.recalculateAllAccountBalances
import kz.alem.corporation.fintracker.data.unsyncedAccounts
import kz.alem.corporation.fintracker.data.unsyncedCategories
import kz.alem.corporation.fintracker.data.unsyncedCategoryGroups
import kz.alem.corporation.fintracker.data.unsyncedCurrencies
import kz.alem.corporation.fintracker.data.unsyncedOperations
import kz.alem.corporation.fintracker.data.unsyncedPlans
import java.util.concurrent.atomic.AtomicBoolean

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Error(val message: String) : SyncState
}

/**
 * Upload / pull reconciliation with the backend (port of the iOS SyncEngine).
 *
 * Only one sync runs at a time; triggers that arrive meanwhile set a "run again" flag.
 * After a successful cycle the engine drains: it re-runs while unsynced rows remain.
 * Sync silently no-ops unless it is enabled, the user is signed in, no account switch is
 * pending and the user has a subscription.
 */
class SyncEngine(private val container: AppContainer) {

    private val store: FinanceStore get() = container.store
    private val prefs get() = container.prefs
    private val api get() = container.syncApi

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow(prefs.lastSyncAt.takeIf { it > 0 })
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    private val _isSyncEnabled = MutableStateFlow(prefs.isSyncEnabled)
    /** Manual pause switch, independent of sign-in and subscription (opt-out). */
    val isSyncEnabled: StateFlow<Boolean> = _isSyncEnabled.asStateFlow()

    private val isSyncing = AtomicBoolean(false)
    private val pendingAfterCurrent = AtomicBoolean(false)
    private var debounceJob: Job? = null

    fun setSyncEnabled(enabled: Boolean) {
        prefs.isSyncEnabled = enabled
        _isSyncEnabled.value = enabled
        if (enabled) scheduleImmediate()
    }

    fun resetState() {
        debounceJob?.cancel()
        prefs.lastSyncAt = 0
        _lastSyncedAt.value = null
        _state.value = SyncState.Idle
        pendingAfterCurrent.set(false)
    }

    fun scheduleImmediate() {
        if (isSyncing.get()) {
            pendingAfterCurrent.set(true)
        } else {
            container.appScope.launch { sync() }
        }
    }

    /** Waits 2 s to coalesce rapid local saves into one sync. */
    fun scheduleDebounced() {
        debounceJob?.cancel()
        debounceJob = container.appScope.launch {
            delay(DEBOUNCE_MS)
            scheduleImmediate()
        }
    }

    suspend fun sync() {
        if (!canSync()) return
        if (!isSyncing.compareAndSet(false, true)) {
            pendingAfterCurrent.set(true)
            return
        }
        var succeeded = false
        try {
            _state.value = SyncState.Syncing
            withContext(Dispatchers.IO) {
                performUpload()
                performPull()
            }
            _state.value = SyncState.Idle
            prefs.lastSyncAt.takeIf { it > 0 }?.let { _lastSyncedAt.value = it }
            succeeded = true
        } catch (e: Exception) {
            _state.value = SyncState.Error(errorMessage(e))
        } finally {
            isSyncing.set(false)
            store.notifyExternalChange()
        }

        if (!succeeded) return
        // Drain loop: rows edited during the sync (or a large backlog) go up right away.
        val hasUnsynced = withContext(Dispatchers.IO) { store.hasUnsyncedEntities() }
        if (hasUnsynced || pendingAfterCurrent.getAndSet(false)) scheduleImmediate()
    }

    private fun canSync(): Boolean {
        val auth = container.authManager
        return _isSyncEnabled.value &&
            auth.isSignedIn &&
            // Firebase already switched accounts while the user decides; syncing now would mix them.
            auth.pendingAccountSwitch.value == null &&
            container.subscriptionManager.hasSubscription.value
    }

    private fun errorMessage(e: Exception): String {
        val res = (e as? SyncApiException)?.messageRes
        return if (res != null) container.context.getString(res) else e.localizedMessage ?: e.toString()
    }

    /** Replaces all local data with the cloud snapshot. */
    suspend fun restoreFromCloud() {
        val response = apiCall { api.download() }
        withContext(Dispatchers.IO) {
            store.clearAll()
            store.clearTombstones()
            // The database is empty, so skip the remoteId lookups and batch-insert.
            store.runInTx { batchInsert(response) }
            store.recalculateAllAccountBalances()
        }
        prefs.lastSyncAt = response.syncedAt
        _lastSyncedAt.value = response.syncedAt
        store.notifyExternalChange()
    }

    /** Wipes the cloud copy and forces a full re-upload of local data. */
    suspend fun deleteCloudData() {
        apiCall { api.deleteCloudData() }
        withContext(Dispatchers.IO) { store.markAllUnsynced() }
        resetState()
        scheduleImmediate()
    }

    // region Upload

    private suspend fun performUpload() {
        val currencies = store.unsyncedCurrencies()
        val accounts = store.unsyncedAccounts()
        val groups = store.unsyncedCategoryGroups()
        val categories = store.unsyncedCategories()
        val operations = store.unsyncedOperations()
        val plans = store.unsyncedPlans()
        val tombstones = store.readTombstones()

        if (currencies.isEmpty() && accounts.isEmpty() && groups.isEmpty() && categories.isEmpty() &&
            operations.isEmpty() && plans.isEmpty() && tombstones.isEmpty()
        ) return

        // On the very first sync use "now", which lets the server skip the conflict scan.
        val clientSyncAt = prefs.lastSyncAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        val deviceToken = container.pushManager.fcmToken
        var offset = 0
        var isFirst = true

        // Everything but operations is small and goes in the first request; operations
        // are chunked. Runs at least once so the other entities are flushed.
        do {
            val slice = operations.subList(offset, minOf(offset + CHUNK_SIZE, operations.size))
            offset += slice.size

            val payload = UploadPayload(
                currencies = if (isFirst) currencies.map(SyncMapper::toDto) else emptyList(),
                accounts = if (isFirst) accounts.map(SyncMapper::toDto) else emptyList(),
                categoryGroups = if (isFirst) groups.map(SyncMapper::toDto) else emptyList(),
                categories = if (isFirst) categories.map(SyncMapper::toDto) else emptyList(),
                operations = slice.map(SyncMapper::toDto),
                plans = if (isFirst) plans.map(SyncMapper::toDto) else emptyList(),
                tombstones = if (isFirst) tombstones.map(SyncMapper::toDto) else emptyList(),
                clientSyncAt = clientSyncAt,
                deviceToken = deviceToken,
            )

            val response = apiCall { api.upload(payload) }
            applyConflicts(response.conflicts)

            if (isFirst) {
                store.markSynced(currencies)
                store.markSynced(accounts)
                store.markSynced(groups)
                store.markSynced(categories)
                store.markSynced(plans)
                store.deleteTombstones(tombstones)
                // Refresh sync icons now instead of after every operation chunk.
                store.notifyExternalChange()
            }
            store.markSynced(slice)

            // Only advance the cursor once every chunk is up.
            if (offset >= operations.size) prefs.lastSyncAt = response.syncedAt
            isFirst = false
        } while (offset < operations.size)
    }

    // endregion

    // region Pull

    private suspend fun performPull() {
        val response = apiCall { api.pull(prefs.lastSyncAt) }
        store.runInTx {
            upsert(response)
            response.deletedIds?.let { deleted ->
                deleted.currencies.forEach(store::deleteCurrencyByRemoteId)
                deleted.accounts.forEach(store::deleteAccountByRemoteId)
                deleted.categoryGroups.forEach(store::deleteCategoryGroupByRemoteId)
                deleted.categories.forEach(store::deleteCategoryByRemoteId)
                deleted.operations.forEach(store::deleteOperationByRemoteId)
                deleted.plans.forEach(store::deletePlanByRemoteId)
            }
        }
        val deleted = response.deletedIds
        val operationsChanged = response.operations.isNotEmpty() || deleted?.operations?.isNotEmpty() == true
        val accountsChanged = response.accounts.isNotEmpty() || deleted?.accounts?.isNotEmpty() == true
        if (operationsChanged || accountsChanged) store.recalculateAllAccountBalances()
        prefs.lastSyncAt = response.syncedAt
    }

    /** Upserts by remoteId; each type is pre-fetched into a map so it costs one query. */
    private fun upsert(response: PullResponse) {
        if (response.currencies.isNotEmpty()) {
            val existing = store.currencyBox.all.byRemoteId { it.remoteId }
            store.currencyBox.put(response.currencies.map { dto ->
                (existing[dto.remoteId] ?: Currency()).also { SyncMapper.apply(dto, it) }
            })
        }
        if (response.categoryGroups.isNotEmpty()) {
            val existing = store.categoryGroupBox.all.byRemoteId { it.remoteId }
            store.categoryGroupBox.put(response.categoryGroups.map { dto ->
                (existing[dto.remoteId] ?: CategoryGroup()).also { SyncMapper.apply(dto, it) }
            })
        }
        if (response.accounts.isNotEmpty()) {
            val currencies = store.currencyBox.all.byRemoteId { it.remoteId }
            val existing = store.accountBox.all.byRemoteId { it.remoteId }
            store.accountBox.put(response.accounts.map { dto ->
                (existing[dto.remoteId] ?: Account()).also { SyncMapper.apply(dto, it, currencies) }
            })
        }
        if (response.categories.isNotEmpty()) {
            val groups = store.categoryGroupBox.all.byRemoteId { it.remoteId }
            val existing = store.categoryBox.all.byRemoteId { it.remoteId }
            store.categoryBox.put(response.categories.map { dto ->
                (existing[dto.remoteId] ?: Category()).also { SyncMapper.apply(dto, it, groups) }
            })
        }
        if (response.operations.isNotEmpty()) {
            val accounts = store.accountBox.all.byRemoteId { it.remoteId }
            val categories = store.categoryBox.all.byRemoteId { it.remoteId }
            val existing = store.operationBox.all.byRemoteId { it.remoteId }
            store.operationBox.put(response.operations.map { dto ->
                (existing[dto.remoteId] ?: Operation()).also { SyncMapper.apply(dto, it, accounts, categories) }
            })
        }
        // Plans last so their currency / category / group references resolve.
        if (response.plans.isNotEmpty()) {
            val currencies = store.currencyBox.all.byRemoteId { it.remoteId }
            val categories = store.categoryBox.all.byRemoteId { it.remoteId }
            val groups = store.categoryGroupBox.all.byRemoteId { it.remoteId }
            val existing = store.planBox.all.byRemoteId { it.remoteId }
            store.planBox.put(response.plans.map { dto ->
                (existing[dto.remoteId] ?: Plan()).also { SyncMapper.apply(dto, it, currencies, categories, groups) }
            })
        }
    }

    /** Restore fast path: the database is empty, so references resolve from in-memory maps. */
    private fun batchInsert(response: PullResponse) {
        val currencies = response.currencies.map { dto -> Currency().also { SyncMapper.apply(dto, it) } }
        store.currencyBox.put(currencies)

        val groups = response.categoryGroups.map { dto -> CategoryGroup().also { SyncMapper.apply(dto, it) } }
        store.categoryGroupBox.put(groups)

        val currencyMap = currencies.byRemoteId { it.remoteId }
        val accounts = response.accounts.map { dto -> Account().also { SyncMapper.apply(dto, it, currencyMap) } }
        store.accountBox.put(accounts)

        val groupMap = groups.byRemoteId { it.remoteId }
        val categories = response.categories.map { dto -> Category().also { SyncMapper.apply(dto, it, groupMap) } }
        store.categoryBox.put(categories)

        val accountMap = accounts.byRemoteId { it.remoteId }
        val categoryMap = categories.byRemoteId { it.remoteId }
        store.operationBox.put(response.operations.map { dto ->
            Operation().also { SyncMapper.apply(dto, it, accountMap, categoryMap) }
        })

        store.planBox.put(response.plans.map { dto ->
            Plan().also { SyncMapper.apply(dto, it, currencyMap, categoryMap, groupMap) }
        })
    }

    // endregion

    // region Conflicts (server wins)

    private fun applyConflicts(conflicts: List<ConflictDto>) {
        if (conflicts.isEmpty()) return
        val currencyMap by lazy { store.currencyBox.all.byRemoteId { it.remoteId } }
        val groupMap by lazy { store.categoryGroupBox.all.byRemoteId { it.remoteId } }
        val accountMap by lazy { store.accountBox.all.byRemoteId { it.remoteId } }
        val categoryMap by lazy { store.categoryBox.all.byRemoteId { it.remoteId } }
        var balancesNeedRecalc = false

        for (conflict in conflicts) {
            val data = conflict.serverData ?: continue
            when (conflict.collectionName) {
                Collections.CURRENCIES -> store.findCurrencyByRemoteId(conflict.remoteId)?.let {
                    SyncMapper.apply(decode<CurrencyDto>(data) ?: return@let, it)
                    store.currencyBox.put(it)
                }
                Collections.ACCOUNTS -> store.findAccountByRemoteId(conflict.remoteId)?.let {
                    SyncMapper.apply(decode<AccountDto>(data) ?: return@let, it, currencyMap)
                    store.accountBox.put(it)
                    balancesNeedRecalc = true
                }
                Collections.CATEGORY_GROUPS -> store.findCategoryGroupByRemoteId(conflict.remoteId)?.let {
                    SyncMapper.apply(decode<CategoryGroupDto>(data) ?: return@let, it)
                    store.categoryGroupBox.put(it)
                }
                Collections.CATEGORIES -> store.findCategoryByRemoteId(conflict.remoteId)?.let {
                    SyncMapper.apply(decode<CategoryDto>(data) ?: return@let, it, groupMap)
                    store.categoryBox.put(it)
                }
                Collections.OPERATIONS -> store.findOperationByRemoteId(conflict.remoteId)?.let {
                    SyncMapper.apply(decode<OperationDto>(data) ?: return@let, it, accountMap, categoryMap)
                    store.operationBox.put(it)
                    balancesNeedRecalc = true
                }
                Collections.PLANS -> store.findPlanByRemoteId(conflict.remoteId)?.let {
                    SyncMapper.apply(decode<PlanDto>(data) ?: return@let, it, currencyMap, categoryMap, groupMap)
                    store.planBox.put(it)
                }
            }
        }
        if (balancesNeedRecalc) store.recalculateAllAccountBalances()
    }

    private inline fun <reified T> decode(element: JsonElement): T? =
        runCatching { syncJson.decodeFromJsonElement<T>(element) }.getOrNull()

    // endregion

    private inline fun <T> List<T>.byRemoteId(remoteId: (T) -> String): Map<String, T> =
        buildMap { this@byRemoteId.forEach { entity -> remoteId(entity).takeIf { it.isNotEmpty() }?.let { put(it, entity) } } }

    private companion object {
        const val CHUNK_SIZE = 200
        const val DEBOUNCE_MS = 2_000L
    }
}
