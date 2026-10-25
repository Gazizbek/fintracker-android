package kz.alem.corporation.fintracker.ui.database

import android.content.Context
import androidx.annotation.StringRes
import kz.alem.corporation.fintracker.AppContainer
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.data.ImportColumn
import kz.alem.corporation.fintracker.data.calculateSum
import kz.alem.corporation.fintracker.data.model.Account
import kz.alem.corporation.fintracker.data.model.AccountIcons
import kz.alem.corporation.fintracker.data.model.AccountType
import kz.alem.corporation.fintracker.data.model.Category
import kz.alem.corporation.fintracker.data.model.CategoryGroup
import kz.alem.corporation.fintracker.data.model.CategoryGroupIcons
import kz.alem.corporation.fintracker.data.model.CategoryIcons
import kz.alem.corporation.fintracker.data.model.CategoryType
import kz.alem.corporation.fintracker.data.model.Currency
import kz.alem.corporation.fintracker.data.model.Operation
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.data.roundCents
import kz.alem.corporation.fintracker.sync.apiCall
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

enum class Separator(val char: Char) { COMMA(','), SEMICOLON(';') }

enum class ImportTemplate(@get:StringRes val title: Int) {
    ALL_OPERATIONS(R.string.allOperations),
    EXPENSES_INCOMES(R.string.expensesIncomes),
    TRANSFERS(R.string.transfers),
    LAST_USED(R.string.lastUsed),
}

/** Import column templates; ids are shared between templates (iOS ImportColumn). */
object ImportColumns {
    val allOperations = listOf(
        ImportColumn(1, "date"), ImportColumn(2, "type"), ImportColumn(3, "categoryGroup"),
        ImportColumn(4, "category"), ImportColumn(5, "account1Name"), ImportColumn(6, "account1Currency"),
        ImportColumn(7, "account2Name"), ImportColumn(8, "account2Currency"), ImportColumn(9, "amount1"),
        ImportColumn(10, "rate"), ImportColumn(11, "amount2"), ImportColumn(12, "quantity"),
        ImportColumn(13, "total"), ImportColumn(14, "comment"),
    )
    val expensesAndIncomes = listOf(
        ImportColumn(1, "date"), ImportColumn(2, "type"), ImportColumn(5, "account"),
        ImportColumn(3, "categoryGroup"), ImportColumn(4, "category"), ImportColumn(6, "currency"),
        ImportColumn(9, "amount"), ImportColumn(12, "quantity"), ImportColumn(13, "total"), ImportColumn(14, "comment"),
    )
    val transfers = listOf(
        ImportColumn(1, "date"), ImportColumn(2, "type"), ImportColumn(5, "accountFrom"),
        ImportColumn(7, "accountTo"), ImportColumn(6, "account1Currency"), ImportColumn(8, "account2Currency"),
        ImportColumn(9, "amount1"), ImportColumn(11, "amount2"), ImportColumn(10, "rate"), ImportColumn(14, "comment"),
    )

    @StringRes
    fun title(name: String): Int = when (name) {
        "date" -> R.string.date
        "type" -> R.string.type
        "categoryGroup" -> R.string.categoryGroup
        "category" -> R.string.category
        "account1Name" -> R.string.account1Name
        "account1Currency" -> R.string.account1Currency
        "account2Name" -> R.string.account2Name
        "account2Currency" -> R.string.account2Currency
        "amount1" -> R.string.amount1
        "rate" -> R.string.rate
        "amount2" -> R.string.amount2
        "quantity" -> R.string.quantity
        "total" -> R.string.total
        "comment" -> R.string.comment
        "account" -> R.string.account
        "currency" -> R.string.currency
        "amount" -> R.string.amount
        "accountFrom" -> R.string.accountFrom
        "accountTo" -> R.string.accountTo
        else -> R.string.other
    }
}

private const val DATE_PATTERN = "dd.MM.yy HH:mm"

private fun randomColor(): Int = (0xFF shl 24) or Random.nextInt(0x1000000)

/** Swift-style Double text ("1500.0") used by the CSV format both apps share. */
private fun Double.csv(): String =
    if (this == Math.rint(this) && abs(this) < 1e15) "${toLong()}.0" else BigDecimal(toString()).toPlainString()

private fun String.toCsvDouble(): Double? = trim().replace(" ", "").replace(',', '.').toDoubleOrNull()

/** CSV splitting that tolerates quoted separators and line breaks. */
object CsvParser {
    /** Splits a CSV file into rows of cells, starting at the 1-based [firstRow]. */
    fun parse(text: String, separator: Separator, firstRow: Int): List<List<String>> {
        // Line breaks inside quoted cells would split a record, so flatten them first.
        val cleaned = buildString(text.length) {
            var inQuotes = false
            for (char in text) {
                if (char == '"') inQuotes = !inQuotes
                append(if (inQuotes && (char == '\r' || char == '\n')) ' ' else char)
            }
        }
        return cleaned.split('\n')
            .drop((firstRow - 1).coerceAtLeast(0))
            .map { it.trimEnd('\r') }
            .filter { it.isNotEmpty() }
            .map { row -> splitRow(row, separator.char) }
    }

    private fun splitRow(row: String, separator: Char): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        for (char in row) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == separator && !inQuotes -> {
                    cells += cell.toString()
                    cell.clear()
                }
                else -> cell.append(char)
            }
        }
        cells += cell.toString()
        return cells
    }
}

/** CSV import / export and the full data reset (port of the iOS DatabaseViewModel). */
class DatabaseService(private val container: AppContainer) {

    private val store = container.store

    // region Import

    private val currencies = mutableListOf<Currency>()
    private val accounts = mutableListOf<Account>()
    private val groups = mutableListOf<CategoryGroup>()
    private val categories = mutableListOf<Category>()

    /**
     * Imports parsed rows mapped by [columns] (enabled ones, in file order). Names that
     * don't exist yet become new currencies / accounts / groups / categories. With
     * [clean], local data (and the cloud copy, for subscribers) is wiped first.
     */
    suspend fun importData(rows: List<List<String>>, clean: Boolean, columns: List<ImportColumn>, typeNames: Map<OperationType, String>) {
        if (clean) {
            store.clearAll()
            store.clearTombstones()
            deleteCloudDataIfSubscribed()
            container.syncEngine.resetState()
        }

        currencies.clear(); currencies += store.readCurrencies()
        accounts.clear(); accounts += store.readAccounts(onlyVisible = false)
        groups.clear(); groups += store.readCategoryGroups()
        categories.clear(); categories += store.readCategories(onlyVisible = false)
        val operations = mutableListOf<Operation>()
        val dateFormat = SimpleDateFormat(DATE_PATTERN, Locale.ROOT)
        fun index(id: Int) = columns.indexOfFirst { it.id == id }.takeIf { it >= 0 }

        // Files list newest first; import oldest first.
        for (row in rows.asReversed()) {
            if (row.size < columns.size) continue
            fun cell(id: Int): String? = index(id)?.let { row.getOrNull(it) }

            val date = cell(1)?.let { runCatching { dateFormat.parse(it) }.getOrNull()?.time } ?: System.currentTimeMillis()
            val type = typeByName(cell(2), typeNames)
            val account = accountByNameOrCreate(cell(5), cell(6)) ?: continue
            var addAccount: Account? = null
            var category: Category? = null
            if (type == OperationType.TRANSFER) {
                addAccount = accountByNameOrCreate(cell(7), cell(8))
            } else {
                category = categoryByNameOrCreate(cell(3), cell(4))
            }

            val quantity = cell(12)?.toCsvDouble()?.let { abs(it) } ?: 1.0
            val amount = cell(9)?.toCsvDouble()?.let { abs(it) } ?: 1.0
            val note = cell(14)
            var mainRate = 1
            var rate = 1.0
            if (type == OperationType.TRANSFER) {
                val finalAmount = cell(11)?.toCsvDouble()
                if (finalAmount != null) {
                    rate = cell(10)?.toCsvDouble() ?: 1.0
                    if (amount > finalAmount && rate >= 1) mainRate = 2
                }
            }

            val operation = Operation(
                date = date,
                quantity = quantity,
                amount = amount,
                mainRate = mainRate,
                rate = rate,
                note = note,
                sum = calculateSum(amount * quantity, rate, mainRate),
                confirmed = true,
            )
            operation.typeEnum = type
            operation.account.target = account
            operation.addAccount.target = addAccount
            operation.category.target = category
            operations += operation
            applyBalance(operation, amount * quantity)
        }

        // Only new records (id 0) are stamped for sync; existing ones only had their
        // derived balance changed.
        store.runInTx {
            store.createCurrencies(currencies.filter { it.id == 0L })
            store.createCategoryGroups(groups.filter { it.id == 0L })
            store.createCategories(categories.filter { it.id == 0L })
            val (newAccounts, existingAccounts) = accounts.partition { it.id == 0L }
            store.createAccounts(newAccounts)
            store.accountBox.put(existingAccounts)
            store.createOperations(operations)
        }
    }

    private fun applyBalance(operation: Operation, balance: Double) {
        fun subtract(account: Account?, value: Double) {
            account ?: return
            account.balance = (account.balance - value.roundCents()).roundCents()
        }
        subtract(operation.account.target, if (operation.typeEnum == OperationType.INCOME) -balance else balance)
        if (operation.typeEnum == OperationType.TRANSFER) {
            subtract(operation.addAccount.target, -calculateSum(balance, operation.rate, operation.mainRate))
        }
    }

    private fun typeByName(value: String?, typeNames: Map<OperationType, String>): OperationType {
        val name = value?.lowercase() ?: return OperationType.TRANSFER
        typeNames.entries.firstOrNull { it.value.lowercase() == name }?.let { return it.key }
        return when (name) {
            "expense", "шығын", "расход" -> OperationType.EXPENSE
            "income", "кіріс", "доход" -> OperationType.INCOME
            else -> OperationType.TRANSFER
        }
    }

    private fun accountByNameOrCreate(name: String?, currencySymbol: String?): Account? {
        if (name == null || currencySymbol == null) return null
        val currency = currencies.firstOrNull { it.symbol == currencySymbol }
            ?: Currency(name = currencySymbol, code = currencySymbol, symbol = currencySymbol, color = randomColor())
                .also { currencies += it }
        return accounts.firstOrNull { it.name == name && it.currency.target?.symbol == currency.symbol }
            ?: Account(name = name, color = randomColor()).also {
                it.typeEnum = AccountType.CREDIT_CARD
                it.iconEnum = AccountIcons.creditcard
                it.currency.target = currency
                accounts += it
            }
    }

    private fun categoryByNameOrCreate(groupName: String?, categoryName: String?): Category? {
        if (groupName == null || categoryName == null) return null
        val group = groups.firstOrNull { it.name == groupName }
            ?: CategoryGroup(name = groupName, color = randomColor(), icon = CategoryGroupIcons.store.name).also { groups += it }
        return categories.firstOrNull { it.name == categoryName && it.categoryGroup.target?.name == group.name }
            ?: Category(name = categoryName, color = randomColor(), icon = CategoryIcons.basket.name).also {
                it.typeEnum = CategoryType.MIXED
                it.categoryGroup.target = group
                categories += it
            }
    }

    // endregion

    // region Export

    fun exportCsv(context: Context, operations: List<Operation>, separator: Separator): String = buildString {
        val sep = separator.char
        val headers = listOf(
            R.string.date, R.string.type, R.string.categoryGroup, R.string.category,
            R.string.account1Name, R.string.account1Currency, R.string.account2Name, R.string.account2Currency,
            R.string.amount1, R.string.rate, R.string.amount2, R.string.quantity, R.string.total, R.string.comment,
        )
        headers.forEach { append('"').append(context.getString(it)).append('"').append(sep) }
        append('\n')

        val dateFormat = SimpleDateFormat(DATE_PATTERN, Locale.ROOT)
        fun quoted(value: String?) = "\"${value.orEmpty()}\""
        for (operation in operations) {
            val account = operation.account.target
            val addAccount = operation.addAccount.target
            val category = operation.category.target
            append(dateFormat.format(Date(operation.date))).append(sep)
            append(quoted(operation.type)).append(sep)
            append(quoted(category?.categoryGroup?.target?.name)).append(sep)
            append(quoted(category?.name)).append(sep)
            append(quoted(account?.name)).append(sep)
            append(quoted(account?.currency?.target?.symbol)).append(sep)
            append(quoted(addAccount?.name)).append(sep)
            append(quoted(addAccount?.currency?.target?.symbol)).append(sep)
            append(operation.amount.csv()).append(sep)
            append(operation.rate.csv()).append(sep)
            val amount2 = if (operation.typeEnum == OperationType.TRANSFER) {
                calculateSum(operation.amount * operation.quantity, operation.rate, operation.mainRate).csv()
            } else "\"\""
            append(amount2).append(sep)
            append(operation.quantity.csv()).append(sep)
            append(operation.sum.csv()).append(sep)
            append(quoted(operation.note))
            append('\n')
        }
    }

    // endregion

    /** Wipes local data, plus the cloud copy for subscribers (best effort). */
    suspend fun clearAll() {
        store.clearAll()
        store.clearTombstones()
        deleteCloudDataIfSubscribed()
        container.syncEngine.resetState()
    }

    private suspend fun deleteCloudDataIfSubscribed() {
        if (!container.authManager.isSignedIn || !container.subscriptionManager.hasSubscription.value) return
        runCatching { apiCall { container.syncApi.deleteCloudData() } }
    }
}
