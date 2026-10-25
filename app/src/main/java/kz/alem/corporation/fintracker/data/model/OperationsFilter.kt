package kz.alem.corporation.fintracker.data.model

/** Operation filter shared by the operations list, reports, export and plan progress. */
data class OperationsFilter(
    val period: Period = Period.ALL_TIME,
    val beginDate: Long = System.currentTimeMillis(),
    val endDate: Long = System.currentTimeMillis(),
    val currencies: List<Currency> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val operationTypes: List<OperationType> = emptyList(),
    val categoryGroups: List<CategoryGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val compareType: CompareType = CompareType.EQUAL,
    val sum: Double = 0.0,
    val containType: ContainType = ContainType.CONTAINS,
    val note: String = "",
) {
    val isInitial: Boolean
        get() = period == Period.ALL_TIME &&
            currencies.isEmpty() &&
            accounts.isEmpty() &&
            operationTypes.isEmpty() &&
            categoryGroups.isEmpty() &&
            categories.isEmpty() &&
            sum == 0.0 &&
            note.isEmpty()
}
