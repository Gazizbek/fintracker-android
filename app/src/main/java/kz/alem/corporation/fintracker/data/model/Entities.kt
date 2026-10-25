package kz.alem.corporation.fintracker.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne

// ObjectBox entities, mirroring the iOS models. Differences from iOS:
// - colors are stored as ARGB ints (the sync wire format) instead of hex strings;
// - dates are stored as epoch milliseconds.
// Every entity carries the sync metadata: remoteId (client-generated UUID), updatedAt
// (ms, stamped on every local write) and synced (local-only upload bookkeeping).
// Equality is intentionally left as identity so StateFlows re-emit freshly read rows.

@Entity
class Currency(
    @Id var id: Long = 0,
    var name: String = "",
    var code: String = "",
    var symbol: String = "",
    var color: Int = DEFAULT_COLOR,
    var useAsDefault: Boolean = false,
    override var synced: Boolean = false,
    @Index override var remoteId: String = "",
    override var updatedAt: Long = 0,
    var createdAt: Long = System.currentTimeMillis(),
) : SyncEntity

@Entity
class CategoryGroup(
    @Id var id: Long = 0,
    var name: String = "",
    var color: Int = DEFAULT_COLOR,
    var icon: String = CategoryGroupIcons.basket.name,
    var useAsDefault: Boolean = false,
    override var synced: Boolean = false,
    @Index override var remoteId: String = "",
    override var updatedAt: Long = 0,
    var createdAt: Long = System.currentTimeMillis(),
) : SyncEntity {
    var iconEnum: CategoryGroupIcons
        get() = CategoryGroupIcons.fromRaw(icon)
        set(value) { icon = value.name }
}

@Entity
class Category(
    @Id var id: Long = 0,
    var name: String = "",
    var type: String = CategoryType.EXPENSE.raw,
    var color: Int = DEFAULT_COLOR,
    var icon: String = CategoryIcons.basket.name,
    var visible: Boolean = true,
    var useAsDefault: Boolean = false,
    override var synced: Boolean = false,
    @Index override var remoteId: String = "",
    override var updatedAt: Long = 0,
    var createdAt: Long = System.currentTimeMillis(),
) : SyncEntity {
    lateinit var categoryGroup: ToOne<CategoryGroup>

    var typeEnum: CategoryType
        get() = CategoryType.fromRaw(type)
        set(value) { type = value.raw }

    var iconEnum: CategoryIcons
        get() = CategoryIcons.fromRaw(icon)
        set(value) { icon = value.name }
}

@Entity
class Account(
    @Id var id: Long = 0,
    var name: String = "",
    var type: String = AccountType.CREDIT_CARD.raw,
    /** Manually entered starting balance; this is what syncs. */
    var mainBalance: Double = 0.0,
    /** Derived: mainBalance plus all confirmed operations. Never synced. */
    var balance: Double = 0.0,
    var color: Int = DEFAULT_COLOR,
    var icon: String = AccountIcons.creditcard.name,
    var visible: Boolean = true,
    var useAsDefault: Boolean = false,
    var inSummary: Boolean = true,
    override var synced: Boolean = false,
    @Index override var remoteId: String = "",
    override var updatedAt: Long = 0,
    var createdAt: Long = System.currentTimeMillis(),
) : SyncEntity {
    lateinit var currency: ToOne<Currency>

    var typeEnum: AccountType
        get() = AccountType.fromRaw(type)
        set(value) { type = value.raw }

    var iconEnum: AccountIcons
        get() = AccountIcons.fromRaw(icon)
        set(value) { icon = value.name }
}

@Entity
class Operation(
    @Id var id: Long = 0,
    @Index var type: String = OperationType.EXPENSE.raw,
    @Index var date: Long = System.currentTimeMillis(),
    var quantity: Double = 1.0,
    var amount: Double = 0.0,
    /** 1: 1 source = rate destination; 2: 1 destination = rate source. */
    var mainRate: Int = 1,
    var rate: Double = 1.0,
    var note: String? = null,
    /** amount * quantity converted with the rate; only used by the amount filter. */
    var sum: Double = 0.0,
    var confirmed: Boolean = true,
    override var synced: Boolean = false,
    @Index override var remoteId: String = "",
    override var updatedAt: Long = 0,
    var createdAt: Long = System.currentTimeMillis(),
) : SyncEntity {
    lateinit var account: ToOne<Account>
    /** Transfer destination. */
    lateinit var addAccount: ToOne<Account>
    lateinit var category: ToOne<Category>

    var typeEnum: OperationType
        get() = OperationType.fromRaw(type)
        set(value) { type = value.raw }
}

@Entity
class Plan(
    @Id var id: Long = 0,
    var name: String = "",
    var type: String = OperationType.EXPENSE.raw,
    // The tracked window, frozen at creation/edit time. No period type is stored; the
    // edit form infers which period it matches purely as a UI convenience.
    var beginDate: Long = 0,
    var endDate: Long = 0,
    var amount: Double = 0.0,
    override var synced: Boolean = false,
    @Index override var remoteId: String = "",
    override var updatedAt: Long = 0,
    var createdAt: Long = System.currentTimeMillis(),
) : SyncEntity {
    lateinit var currency: ToOne<Currency>

    // Optional scope, mutually exclusive in the UI; both empty = all income/expenses.
    lateinit var categories: ToMany<Category>
    lateinit var categoryGroups: ToMany<CategoryGroup>

    var typeEnum: OperationType
        get() = OperationType.fromRaw(type)
        set(value) { type = value.raw }
}

/** Records a local deletion until it has been uploaded. */
@Entity
class Tombstone(
    @Id var id: Long = 0,
    var remoteId: String = "",
    var collectionName: String = "",
    var deletedAt: Long = System.currentTimeMillis(),
)

/** Sync metadata shared by every synced entity. */
interface SyncEntity {
    var remoteId: String
    var updatedAt: Long
    var synced: Boolean
}

object Collections {
    const val CURRENCIES = "currencies"
    const val ACCOUNTS = "accounts"
    const val CATEGORY_GROUPS = "categoryGroups"
    const val CATEGORIES = "categories"
    const val OPERATIONS = "operations"
    const val PLANS = "plans"
}

/** iOS default accent color (systemBlue). */
const val DEFAULT_COLOR: Int = 0xFF007AFF.toInt()
