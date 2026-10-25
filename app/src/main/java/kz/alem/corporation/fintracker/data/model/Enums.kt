package kz.alem.corporation.fintracker.data.model

import androidx.annotation.Keep
import androidx.annotation.StringRes
import kz.alem.corporation.fintracker.R

// Raw values are persisted locally and must stay identical to the iOS app (they are
// also what the CSV export writes), so never rename them.

// Kept: used as a navigation route argument (serialized by name).
@Keep
enum class OperationType(val raw: String, val serverValue: Int, @get:StringRes val title: Int) {
    EXPENSE("expense", 0, R.string.expense),
    INCOME("income", 1, R.string.income),
    TRANSFER("transfer", 2, R.string.transfer);

    companion object {
        fun fromRaw(raw: String?): OperationType = entries.firstOrNull { it.raw == raw } ?: EXPENSE
        fun fromServer(value: Int): OperationType = entries.firstOrNull { it.serverValue == value } ?: EXPENSE
    }
}

/** The category type an operation of this type uses (mixed categories fit either). */
val OperationType.categoryType: CategoryType
    get() = if (this == OperationType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME

enum class CategoryType(val raw: String, val serverValue: Int, @get:StringRes val title: Int) {
    EXPENSE("expenseCategory", 0, R.string.expenseCategory),
    INCOME("incomeCategory", 1, R.string.incomeCategory),
    MIXED("mixedCategory", 2, R.string.mixedCategory);

    companion object {
        fun fromRaw(raw: String?): CategoryType = entries.firstOrNull { it.raw == raw } ?: EXPENSE
        fun fromServer(value: Int): CategoryType = entries.firstOrNull { it.serverValue == value } ?: EXPENSE
    }
}

enum class AccountType(val raw: String, @get:StringRes val title: Int) {
    DEBIT_CARD("debitCard", R.string.debitCard),
    CREDIT_CARD("creditCard", R.string.creditCard),
    DEPOSIT("deposit", R.string.deposit),
    CREDIT("credit", R.string.credit),
    CASH("cash", R.string.cash),
    SAVINGS("savings", R.string.savings),
    DEBT("debt", R.string.debt);

    // The server only knows three buckets, so this mapping is lossy (same as iOS).
    val serverValue: Int
        get() = when (this) {
            CREDIT_CARD, DEBIT_CARD, CREDIT, CASH -> 0
            DEPOSIT, SAVINGS -> 1
            DEBT -> 2
        }

    companion object {
        fun fromRaw(raw: String?): AccountType = entries.firstOrNull { it.raw == raw } ?: CREDIT_CARD
        fun fromServer(value: Int): AccountType = when (value) {
            1 -> DEPOSIT
            2 -> DEBT
            else -> CREDIT_CARD
        }
    }
}

enum class Period(val raw: String, @get:StringRes val title: Int) {
    ALL_TIME("allTime", R.string.allTime),
    CURRENT_WEEK("currentWeek", R.string.currentWeek),
    LAST_WEEK("lastWeek", R.string.lastWeek),
    CURRENT_MONTH("currentMonth", R.string.currentMonth),
    LAST_MONTH("lastMonth", R.string.lastMonth),
    CURRENT_YEAR("currentYear", R.string.currentYear),
    LAST_YEAR("lastYear", R.string.lastYear),
    ARBITRARY("arbitraryPeriod", R.string.arbitraryPeriod),
}

enum class CompareType(val raw: String, @get:StringRes val title: Int) {
    EQUAL("equalTo", R.string.equalTo),
    MORE("moreThan", R.string.moreThan),
    LESS("lessThan", R.string.lessThan),
}

enum class ContainType(val raw: String, @get:StringRes val title: Int) {
    CONTAINS("doesContain", R.string.doesContain),
    NOT_CONTAINS("doesNotContain", R.string.doesNotContain),
}

@Keep
enum class EditType { CREATE, UPDATE, COPY }

enum class EditForm { OPERATION, ACCOUNT, CATEGORY, CATEGORY_GROUP, CURRENCY, PLAN }

@StringRes
fun EditType.title(form: EditForm): Int = when (form) {
    EditForm.ACCOUNT -> when (this) {
        EditType.CREATE -> R.string.creatingAccount
        EditType.UPDATE -> R.string.updatingAccount
        EditType.COPY -> R.string.copyingAccount
    }
    EditForm.CATEGORY -> when (this) {
        EditType.CREATE -> R.string.creatingCategory
        EditType.UPDATE -> R.string.updatingCategory
        EditType.COPY -> R.string.copyingCategory
    }
    EditForm.CATEGORY_GROUP -> when (this) {
        EditType.CREATE -> R.string.creatingCategoryGroup
        EditType.UPDATE -> R.string.updatingCategoryGroup
        EditType.COPY -> R.string.copyingCategoryGroup
    }
    EditForm.CURRENCY -> when (this) {
        EditType.CREATE -> R.string.creatingCurrency
        EditType.UPDATE -> R.string.updatingCurrency
        EditType.COPY -> R.string.copyingCurrency
    }
    EditForm.OPERATION -> when (this) {
        EditType.CREATE -> R.string.creatingOperation
        EditType.UPDATE -> R.string.updatingOperation
        EditType.COPY -> R.string.copyingOperation
    }
    EditForm.PLAN -> when (this) {
        EditType.CREATE -> R.string.creatingPlan
        EditType.UPDATE -> R.string.updatingPlan
        EditType.COPY -> R.string.copyingPlan
    }
}

/** Errors raised when deleting a record that other records still reference. */
enum class DatabaseError(@get:StringRes val message: Int) {
    CURRENCY_IS_USED(R.string.currencyIsUsed),
    CATEGORY_GROUP_IS_USED(R.string.categoryGroupIsUsed),
    CATEGORY_IS_USED(R.string.categoryIsUsed),
    ACCOUNT_IS_USED(R.string.accountIsUsed),
}

class DatabaseException(val error: DatabaseError) : Exception(error.name)
