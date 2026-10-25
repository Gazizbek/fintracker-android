package kz.alem.corporation.fintracker.ui.reports

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kz.alem.corporation.fintracker.AppContainer
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.data.firstDayOfCurrentMonth
import kz.alem.corporation.fintracker.data.formatNumber
import kz.alem.corporation.fintracker.data.lastDayOfCurrentMonth
import kz.alem.corporation.fintracker.data.model.Account
import kz.alem.corporation.fintracker.data.model.Category
import kz.alem.corporation.fintracker.data.model.CategoryGroup
import kz.alem.corporation.fintracker.data.model.Currency
import kz.alem.corporation.fintracker.data.model.DEFAULT_COLOR
import kz.alem.corporation.fintracker.data.model.Operation
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.data.model.OperationsFilter
import kz.alem.corporation.fintracker.data.model.Period
import kz.alem.corporation.fintracker.data.monthKey
import kz.alem.corporation.fintracker.data.monthKeyStart
import kz.alem.corporation.fintracker.ui.common.Formats
import kz.alem.corporation.fintracker.ui.theme.TypeColors
import androidx.compose.ui.graphics.toArgb
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** A report dimension. Ids match iOS (1 month, 2 type, 3 category, 4 group, 5 account). */
data class Measure(val id: Int, val enabled: Boolean) {
    @get:StringRes
    val title: Int
        get() = when (id) {
            1 -> R.string.month
            2 -> R.string.operationType
            3 -> R.string.category
            4 -> R.string.categoryGroup
            else -> R.string.account
        }

    companion object {
        val defaults = listOf(Measure(1, true), Measure(2, true), Measure(3, true), Measure(4, false), Measure(5, false))
    }
}

/** What a report row stands for; used to build the drill-through filter. */
sealed interface ReportKey {
    data class CurrencyKey(val currency: Currency) : ReportKey
    data class MonthKey(val key: Int) : ReportKey
    data class TypeKey(val type: OperationType) : ReportKey
    data class CategoryKey(val category: Category?) : ReportKey
    data class GroupKey(val group: CategoryGroup?) : ReportKey
    data class AccountKey(val account: Account?) : ReportKey
}

class ReportItem(
    val id: String,
    val title: String,
    val value: Double,
    /** Record color; null means "use a theme color" (month rows). */
    val color: Int?,
    val symbol: String,
    val key: ReportKey,
    val parents: List<ReportKey>,
    val level: Int,
    val subItems: List<ReportItem>,
    val percent: Double = 0.0,
) {
    fun withPercent(percent: Double) = ReportItem(id, title, value, color, symbol, key, parents, level, subItems, percent)
}

/** Signed, cent-rounded amount: expenses count negative. */
private fun Operation.signedAmount(): Double {
    val sign = if (typeEnum == OperationType.EXPENSE) -1 else 1
    return (sign * amount * quantity * 100).roundToLong() / 100.0
}

/** Builds the report tree (port of the iOS ReportViewModel). */
class ReportBuilder(private val context: Context, private val locale: Locale) {

    fun build(operations: List<Operation>, measures: List<Measure>): List<ReportItem> =
        operations.groupBy { it.account.target?.currency?.target?.id }
            .mapNotNull { (_, list) ->
                val currency = list.first().account.target?.currency?.target ?: return@mapNotNull null
                val key = ReportKey.CurrencyKey(currency)
                ReportItem(
                    id = "c${currency.id}",
                    title = currency.name,
                    value = list.sumOf { it.signedAmount() },
                    color = currency.color,
                    symbol = currency.symbol,
                    key = key,
                    parents = emptyList(),
                    level = 0,
                    subItems = subItems(key, 0, list, currency.symbol, measures, emptyList(), "c${currency.id}"),
                )
            }

    private fun subItems(
        parent: ReportKey,
        index: Int,
        list: List<Operation>,
        symbol: String,
        measures: List<Measure>,
        prevParents: List<ReportKey>,
        idPrefix: String,
    ): List<ReportItem> {
        if (index >= measures.size) return emptyList()
        val measure = measures[index].id
        val parents = prevParents + parent

        val groups: Map<ReportKey, List<Operation>> = list.groupBy { operation ->
            when (measure) {
                1 -> ReportKey.MonthKey(monthKey(operation.date))
                2 -> ReportKey.TypeKey(operation.typeEnum)
                3 -> ReportKey.CategoryKey(operation.category.target)
                4 -> ReportKey.GroupKey(operation.category.target?.categoryGroup?.target)
                else -> ReportKey.AccountKey(operation.account.target)
            }
        }
        // Entities have identity equality, so regroup by id to merge equal records.
        val merged = groups.entries.groupBy { keyId(it.key) }.map { (_, entries) ->
            entries.first().key to entries.flatMap { it.value }
        }

        var items = merged.map { (key, operations) ->
            val (title, color) = describe(key)
            val id = "$idPrefix/${keyId(key)}"
            ReportItem(
                id = id,
                title = title,
                value = operations.sumOf { it.signedAmount() },
                color = color,
                symbol = symbol,
                key = key,
                parents = parents,
                level = index + 1,
                subItems = subItems(key, index + 1, operations, symbol, measures, parents, id),
            )
        }

        items = when (measure) {
            1 -> items.sortedByDescending { (it.key as ReportKey.MonthKey).key }
            2 -> items.sortedBy { it.title }
            else -> {
                val underMonth = measures.take(index).any { it.id == 1 }
                if (underMonth) items.sortedBy { abs(it.value) } else items.sortedBy { it.value }
            }
        }

        val total = items.sumOf { abs(it.value) }
        return items.map { item ->
            val percent = if (total == 0.0) 0.0 else (abs(item.value) * 100 / total * 10).roundToLong() / 10.0
            item.withPercent(percent)
        }
    }

    private fun keyId(key: ReportKey): String = when (key) {
        is ReportKey.CurrencyKey -> "c${key.currency.id}"
        is ReportKey.MonthKey -> "m${key.key}"
        is ReportKey.TypeKey -> "t${key.type.raw}"
        is ReportKey.CategoryKey -> "k${key.category?.id}"
        is ReportKey.GroupKey -> "g${key.group?.id}"
        is ReportKey.AccountKey -> "a${key.account?.id}"
    }

    private fun describe(key: ReportKey): Pair<String, Int?> = when (key) {
        is ReportKey.CurrencyKey -> key.currency.name to key.currency.color
        // Months have no color of their own; the UI picks a theme color.
        is ReportKey.MonthKey -> Formats.monthYear(monthKeyStart(key.key), locale) to null
        is ReportKey.TypeKey -> context.getString(key.type.title) to when (key.type) {
            OperationType.EXPENSE -> TypeColors.expense
            OperationType.INCOME -> TypeColors.income
            OperationType.TRANSFER -> TypeColors.transfer
        }.toArgb()
        is ReportKey.CategoryKey -> (key.category?.name.orEmpty()) to (key.category?.color ?: DEFAULT_COLOR)
        is ReportKey.GroupKey -> (key.group?.name.orEmpty()) to (key.group?.color ?: DEFAULT_COLOR)
        is ReportKey.AccountKey -> (key.account?.name.orEmpty()) to (key.account?.color ?: DEFAULT_COLOR)
    }

    /** Flattened CSV: one column per measure level, amount last. */
    fun csv(items: List<ReportItem>, measures: List<Measure>): String = buildString {
        append("Currency,")
        measures.forEach { append(context.getString(it.title)).append(',') }
        append("Amount\n")
        fun amount(item: ReportItem) = "\"${formatNumber(item.value, locale)} ${item.symbol}\""
        fun appendRows(subItems: List<ReportItem>, level: Int) {
            subItems.forEach { item ->
                repeat(level) { append(',') }
                append(item.title)
                repeat(measures.size - level + 1) { append(',') }
                append(amount(item)).append('\n')
                appendRows(item.subItems, level + 1)
            }
        }
        items.forEach { item ->
            append(item.title)
            repeat(measures.size + 1) { append(',') }
            append(amount(item)).append('\n')
            appendRows(item.subItems, 1)
        }
    }
}

/**
 * Filter that opens the operations behind a report row: the row's path plus the amount
 * and comment criteria of the report filter. Period and types fall back to the report's
 * own filter when the path doesn't pin them.
 */
fun ReportItem.drillDownFilter(current: OperationsFilter?): OperationsFilter {
    var filter = OperationsFilter(
        period = current?.period ?: Period.ALL_TIME,
        beginDate = current?.beginDate ?: System.currentTimeMillis(),
        endDate = current?.endDate ?: System.currentTimeMillis(),
        operationTypes = current?.operationTypes.orEmpty(),
        compareType = current?.compareType ?: OperationsFilter().compareType,
        sum = current?.sum ?: 0.0,
        containType = current?.containType ?: OperationsFilter().containType,
        note = current?.note.orEmpty(),
    )
    (parents + key).forEach { key ->
        filter = when (key) {
            is ReportKey.CurrencyKey -> filter.copy(currencies = listOf(key.currency))
            is ReportKey.MonthKey -> {
                val start = monthKeyStart(key.key)
                filter.copy(period = Period.ARBITRARY, beginDate = firstDayOfCurrentMonth(start), endDate = lastDayOfCurrentMonth(start))
            }
            is ReportKey.TypeKey -> filter.copy(operationTypes = listOf(key.type))
            is ReportKey.CategoryKey -> filter.copy(categories = listOfNotNull(key.category))
            is ReportKey.GroupKey -> filter.copy(categoryGroups = listOfNotNull(key.group))
            is ReportKey.AccountKey -> filter.copy(accounts = listOfNotNull(key.account))
        }
    }
    return filter
}

data class ReportState(val isLoading: Boolean = true, val items: List<ReportItem> = emptyList())

class ReportViewModel(private val container: AppContainer) : ViewModel() {
    private val store = container.store
    val filter = MutableStateFlow<OperationsFilter?>(
        OperationsFilter(operationTypes = listOf(OperationType.EXPENSE, OperationType.INCOME))
    )
    val measures = MutableStateFlow(Measure.defaults)
    private val locale = MutableStateFlow(Locale.getDefault())

    val state: StateFlow<ReportState> = combine(store.changes, filter, measures, locale) { _, f, m, l ->
        val builder = ReportBuilder(container.context, l)
        ReportState(isLoading = false, items = builder.build(store.readOperations(f), m.filter { it.enabled }))
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportState())

    fun setLocale(value: Locale) {
        locale.value = value
    }

    fun csv(context: Context): String =
        ReportBuilder(context, locale.value).csv(state.value.items, measures.value.filter { it.enabled })
}
