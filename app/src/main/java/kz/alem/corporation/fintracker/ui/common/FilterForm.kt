package kz.alem.corporation.fintracker.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.data.endOfDay
import kz.alem.corporation.fintracker.data.model.CompareType
import kz.alem.corporation.fintracker.data.model.ContainType
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.data.model.OperationsFilter
import kz.alem.corporation.fintracker.data.model.Period
import kz.alem.corporation.fintracker.data.range
import kz.alem.corporation.fintracker.data.startOfDay
import java.util.Calendar
import java.util.TimeZone

// region Dates

/** Material date pickers work in UTC midnights; convert to/from local days. */
fun localToUtcDay(millis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = millis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

fun utcDayToLocal(utcMillis: Long, keepTimeOf: Long? = null): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val local = Calendar.getInstance()
    if (keepTimeOf != null) local.timeInMillis = keepTimeOf else local.timeInMillis = startOfDay(System.currentTimeMillis())
    local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    return local.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit, confirmLabel: String? = null) {
    val state = rememberDatePickerState(initialSelectedDateMillis = localToUtcDay(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            // onDismiss runs first so onPick can open a follow-up dialog (date → time).
            TextButton(onClick = {
                onDismiss()
                state.selectedDateMillis?.let(onPick)
            }) { Text(confirmLabel ?: stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state = state) }
}

/**
 * Period dropdown with the begin/end dates it resolves to. Dates are editable only for
 * the arbitrary period (iOS PeriodPicker).
 */
@Composable
fun PeriodPicker(
    period: Period,
    beginDate: Long,
    endDate: Long,
    onChange: (period: Period, begin: Long, end: Long) -> Unit,
    periods: List<Period> = Period.entries,
    borderless: Boolean = false,
) {
    val locale = appLocale
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    DropdownField(
        label = stringResource(R.string.period),
        options = periods,
        selected = period,
        optionLabel = { stringResource(it.title) },
        onSelect = { selected ->
            val range = selected.range()
            if (range != null) onChange(selected, range.first, range.second) else onChange(selected, beginDate, endDate)
        },
        borderless = borderless,
    )
    if (period != Period.ALL_TIME) {
        val editable = period == Period.ARBITRARY
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            TextButton(onClick = { editing = "begin" }, enabled = editable) { Text(Formats.mediumDate(beginDate, locale)) }
            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { editing = "end" }, enabled = editable) { Text(Formats.mediumDate(endDate, locale)) }
        }
    }
    when (editing) {
        "begin" -> DatePickerModal(beginDate, onPick = { picked ->
            val begin = startOfDay(utcDayToLocal(picked))
            onChange(period, begin, maxOf(endDate, endOfDay(begin)))
        }, onDismiss = { editing = null })
        "end" -> DatePickerModal(endDate, onPick = { picked ->
            val end = endOfDay(utcDayToLocal(picked))
            onChange(period, minOf(beginDate, startOfDay(end)), end)
        }, onDismiss = { editing = null })
    }
}

// endregion

/** Joined names of the chosen items, or "All". */
@Composable
private fun namesOrAll(names: List<String>): String =
    if (names.isEmpty()) stringResource(R.string.all) else names.joinToString(", ")

/** Filter fields shared by the filter dialog and the export screen. */
@Composable
fun ColumnScope.FilterForm(
    filter: OperationsFilter,
    onChange: (OperationsFilter) -> Unit,
    excludeTransferType: Boolean = false,
    showContainType: Boolean = true,
) {
    val locale = appLocale
    var picker by rememberSaveable { mutableStateOf<String?>(null) }

    FormSection {
        PeriodPicker(filter.period, filter.beginDate, filter.endDate, onChange = { p, b, e ->
            onChange(filter.copy(period = p, beginDate = b, endDate = e))
        }, borderless = true)
    }
    FormSection {
        PickerField(
            label = stringResource(R.string.operationTypes),
            value = namesOrAll(filter.operationTypes.map { stringResource(it.title) }),
            onClick = { picker = "types" },
            borderless = true,
        )
        InsetDivider()
        PickerField(
            label = stringResource(R.string.currencies),
            value = namesOrAll(filter.currencies.map { it.name }),
            onClick = { picker = "currencies" },
            borderless = true,
        )
        InsetDivider()
        PickerField(
            label = stringResource(R.string.accounts),
            value = namesOrAll(filter.accounts.map { it.name }),
            onClick = { picker = "accounts" },
            borderless = true,
        )
        InsetDivider()
        PickerField(
            label = stringResource(R.string.categoryGroups),
            value = namesOrAll(filter.categoryGroups.map { it.name }),
            onClick = { picker = "groups" },
            borderless = true,
        )
        InsetDivider()
        PickerField(
            label = stringResource(R.string.categories),
            value = namesOrAll(filter.categories.map { it.name }),
            onClick = { picker = "categories" },
            borderless = true,
        )
    }
    FormSection {
        DropdownField(
            label = stringResource(R.string.amount),
            options = CompareType.entries,
            selected = filter.compareType,
            optionLabel = { stringResource(it.title) },
            onSelect = { onChange(filter.copy(compareType = it)) },
            borderless = true,
        )
        InsetDivider()
        AmountField(
            value = filter.sum,
            onValueChange = { onChange(filter.copy(sum = it)) },
            label = stringResource(R.string.enterSum),
            placeholder = "",
            borderless = true,
        )
    }
    FormSection {
        if (showContainType) {
            DropdownField(
                label = stringResource(R.string.comment),
                options = ContainType.entries,
                selected = filter.containType,
                optionLabel = { stringResource(it.title) },
                onSelect = { onChange(filter.copy(containType = it)) },
                borderless = true,
            )
            InsetDivider()
        }
        FormTextField(
            value = filter.note,
            onValueChange = { onChange(filter.copy(note = it)) },
            borderless = true,
            label = { Text(stringResource(if (showContainType) R.string.commentText else R.string.comment)) },
            singleLine = false,
            minLines = 3,
            maxLines = 8,
        )
    }
    TextButton(
        onClick = { onChange(OperationsFilter()) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) { Text(stringResource(R.string.reset)) }

    when (picker) {
        "types" -> OperationTypePickerSheet(
            onDismiss = { picker = null },
            types = if (excludeTransferType) listOf(OperationType.EXPENSE, OperationType.INCOME) else OperationType.entries,
            selected = filter.operationTypes,
            onChoose = { onChange(filter.copy(operationTypes = it)) },
        )
        "currencies" -> CurrencyPickerSheet(
            onDismiss = { picker = null },
            multi = true,
            selectedIds = filter.currencies.map { it.id }.toSet(),
            onChoose = { onChange(filter.copy(currencies = it)) },
        )
        "accounts" -> AccountPickerSheet(
            onDismiss = { picker = null },
            multi = true,
            selectedIds = filter.accounts.map { it.id }.toSet(),
            onChoose = { onChange(filter.copy(accounts = it)) },
        )
        "groups" -> CategoryGroupPickerSheet(
            onDismiss = { picker = null },
            multi = true,
            selectedIds = filter.categoryGroups.map { it.id }.toSet(),
            onChoose = { onChange(filter.copy(categoryGroups = it)) },
        )
        "categories" -> CategoryPickerSheet(
            onDismiss = { picker = null },
            multi = true,
            selectedIds = filter.categories.map { it.id }.toSet(),
            onChoose = { onChange(filter.copy(categories = it)) },
        )
    }
}

/** Full-screen dialog with a close button, a title and one confirm action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel)) }
                    },
                    actions = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = formBackground,
                        scrolledContainerColor = formBackground,
                    ),
                )
            },
            containerColor = formBackground,
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(vertical = 8.dp),
                content = content,
            )
        }
    }
}

/** Operations filter editor (iOS OperationsFilterScreen). */
@Composable
fun FilterDialog(
    initial: OperationsFilter?,
    onApply: (OperationsFilter) -> Unit,
    onDismiss: () -> Unit,
    excludeTransferType: Boolean = false,
) {
    var filter by androidx.compose.runtime.remember { mutableStateOf(initial ?: OperationsFilter()) }
    FullScreenDialog(
        title = stringResource(R.string.filter),
        confirmLabel = stringResource(R.string.apply),
        onConfirm = {
            val applied = if (excludeTransferType && filter.operationTypes.isEmpty()) {
                filter.copy(operationTypes = listOf(OperationType.EXPENSE, OperationType.INCOME))
            } else filter
            onApply(applied)
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        FilterForm(filter = filter, onChange = { filter = it }, excludeTransferType = excludeTransferType)
    }
}
