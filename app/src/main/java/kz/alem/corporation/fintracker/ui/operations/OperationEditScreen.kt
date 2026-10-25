package kz.alem.corporation.fintracker.ui.operations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.VerticalDivider
import kz.alem.corporation.fintracker.ui.common.FormTextField
import kz.alem.corporation.fintracker.ui.common.ShakeBox
import kz.alem.corporation.fintracker.ui.common.InsetDivider
import kz.alem.corporation.fintracker.ui.common.CheckboxRow
import kz.alem.corporation.fintracker.ui.common.PickerField
import kz.alem.corporation.fintracker.ui.common.EditorCard
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kz.alem.corporation.fintracker.AppContainer
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.data.calculateSum
import kz.alem.corporation.fintracker.data.model.Account
import kz.alem.corporation.fintracker.data.model.Category
import kz.alem.corporation.fintracker.data.model.EditForm
import kz.alem.corporation.fintracker.data.model.EditType
import kz.alem.corporation.fintracker.data.model.Operation
import kz.alem.corporation.fintracker.data.model.CategoryType
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.data.model.categoryType
import kz.alem.corporation.fintracker.data.model.title
import kz.alem.corporation.fintracker.data.startOfDay
import kz.alem.corporation.fintracker.ui.common.AccountPickerSheet
import kz.alem.corporation.fintracker.ui.common.AmountField
import kz.alem.corporation.fintracker.ui.common.CategoryPickerSheet
import kz.alem.corporation.fintracker.ui.common.DatePickerModal
import kz.alem.corporation.fintracker.ui.common.EditScaffold
import kz.alem.corporation.fintracker.ui.common.Formats
import kz.alem.corporation.fintracker.ui.common.LocalAppContainer
import kz.alem.corporation.fintracker.ui.common.appLocale
import kz.alem.corporation.fintracker.ui.common.appViewModel
import kz.alem.corporation.fintracker.ui.common.color
import kz.alem.corporation.fintracker.ui.common.utcDayToLocal
import kz.alem.corporation.fintracker.ui.navigation.AccountEditRoute
import kz.alem.corporation.fintracker.ui.navigation.CategoryEditRoute
import kz.alem.corporation.fintracker.ui.navigation.LocalNavigator
import kz.alem.corporation.fintracker.ui.navigation.NavResultEffect
import kz.alem.corporation.fintracker.ui.navigation.NavResults
import kz.alem.corporation.fintracker.ui.navigation.OperationEditRoute
import java.util.Calendar

class OperationEditViewModel(
    container: AppContainer,
    val editType: EditType,
    val type: OperationType,
    id: Long,
) : ViewModel() {
    private val store = container.store
    private val useOnlyDate = container.prefs.useOnlyDate.value
    private var original: Operation? = null

    var date by mutableLongStateOf(if (useOnlyDate) startOfDay(System.currentTimeMillis()) else System.currentTimeMillis())
    var account by mutableStateOf<Account?>(null)
    var addAccount by mutableStateOf<Account?>(null)
    var category by mutableStateOf<Category?>(null)
    var quantity by mutableDoubleStateOf(1.0)
    var amount by mutableDoubleStateOf(0.0)
    var mainRate by mutableIntStateOf(1)
    var rate by mutableDoubleStateOf(1.0)
    var note by mutableStateOf("")
    var confirmed by mutableStateOf(true)
    var hasError by mutableStateOf(false)

    /** Bumped on every failed save so the missing fields shake. */
    var shakeTrigger by mutableIntStateOf(0)
        private set

    /** Which account slot a newly created account (from a picker's +) should fill. */
    var pendingAccountSlotIsDestination = false

    init {
        viewModelScope.launch {
            if (id != 0L) {
                val operation = withContext(Dispatchers.IO) {
                    store.getOperation(id)?.also {
                        // Resolve relations off the main thread.
                        it.account.target
                        it.addAccount.target
                        it.category.target
                    }
                } ?: return@launch
                original = operation
                date = operation.date
                account = operation.account.target
                addAccount = operation.addAccount.target
                category = operation.category.target
                quantity = operation.quantity
                amount = operation.amount
                mainRate = operation.mainRate
                rate = operation.rate
                note = operation.note.orEmpty()
                confirmed = operation.confirmed
            } else {
                val defaultAccount = withContext(Dispatchers.IO) { store.getDefaultAccount(null) }
                account = defaultAccount
                if (type == OperationType.TRANSFER) {
                    addAccount = withContext(Dispatchers.IO) { store.getDefaultAccount(defaultAccount?.id) }
                } else {
                    category = withContext(Dispatchers.IO) { store.getDefaultCategory(type) }
                }
            }
        }
    }

    val isModified: Boolean
        get() = original?.let {
            // An empty relation has targetId 0, never null.
            date != it.date || (account?.id ?: 0L) != it.account.targetId ||
                (addAccount?.id ?: 0L) != it.addAccount.targetId ||
                (category?.id ?: 0L) != it.category.targetId || quantity != it.quantity || amount != it.amount ||
                mainRate != it.mainRate || rate != it.rate || note != it.note.orEmpty() || confirmed != it.confirmed
        } ?: (amount != 0.0 || quantity != 1.0 || note.isNotEmpty())

    val sourceMissing get() = if (type == OperationType.INCOME) category == null else account == null
    val destinationMissing
        get() = when (type) {
            OperationType.EXPENSE -> category == null
            OperationType.INCOME -> account == null
            OperationType.TRANSFER -> addAccount == null
        }

    /** Transfers between different currencies need an exchange rate. */
    val needsRate: Boolean
        get() {
            val c1 = account?.currency?.targetId ?: 0L
            val c2 = addAccount?.currency?.targetId ?: 0L
            return type == OperationType.TRANSFER && c1 != 0L && c2 != 0L && c1 != c2
        }

    val total: Double get() = calculateSum(amount * quantity, rate, mainRate)

    val totalSymbol: String
        get() = addAccount?.currency?.target?.symbol ?: account?.currency?.target?.symbol.orEmpty()

    fun resetDate() {
        date = if (useOnlyDate) startOfDay(System.currentTimeMillis()) else System.currentTimeMillis()
    }

    fun selectAccount(selected: Account) {
        account = selected
        // A transfer can't go to the same account.
        if (type == OperationType.TRANSFER && addAccount?.id == selected.id) {
            viewModelScope.launch { addAccount = withContext(Dispatchers.IO) { store.getDefaultAccount(selected.id) } }
        }
    }

    fun onAccountCreated(id: Long) = viewModelScope.launch {
        val created = withContext(Dispatchers.IO) { store.getAccount(id) } ?: return@launch
        if (pendingAccountSlotIsDestination) addAccount = created else selectAccount(created)
    }

    fun onCategoryCreated(id: Long) = viewModelScope.launch {
        val created = withContext(Dispatchers.IO) { store.getCategory(id) } ?: return@launch
        // The type may have been switched in the editor; don't select a category that doesn't fit this operation.
        if (created.typeEnum == CategoryType.MIXED || created.typeEnum == type.categoryType) category = created
    }

    fun save(onSaved: () -> Unit) {
        hasError = sourceMissing || destinationMissing || quantity == 0.0 || amount == 0.0
        if (hasError) {
            shakeTrigger++
            return
        }
        val prev = original
        if (editType == EditType.UPDATE && prev == null) return // still loading
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val operation = Operation(
                    id = if (editType == EditType.UPDATE) prev!!.id else 0,
                    date = date,
                    quantity = quantity,
                    amount = amount,
                    mainRate = mainRate,
                    rate = rate,
                    note = note.ifEmpty { null },
                    sum = total,
                    confirmed = confirmed,
                    remoteId = if (editType == EditType.UPDATE) prev!!.remoteId else "",
                    createdAt = if (editType == EditType.UPDATE) prev!!.createdAt else System.currentTimeMillis(),
                )
                operation.typeEnum = type
                operation.account.target = account
                operation.addAccount.target = if (type == OperationType.TRANSFER) addAccount else null
                operation.category.target = if (type == OperationType.TRANSFER) null else category

                store.runInTx {
                    if (editType == EditType.UPDATE) {
                        store.updateOperation(operation)
                        val balance = if (confirmed) amount * quantity else 0.0
                        store.recalcAccountsBalance(prev!!, operation, balance)
                    } else {
                        val newId = store.createOperation(operation)
                        if (confirmed) store.recalcAccountBalance(newId, amount * quantity)
                    }
                }
            }
            onSaved()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationEditScreen(route: OperationEditRoute) {
    val viewModel = appViewModel { container, _ -> OperationEditViewModel(container, route.editType, route.type, route.id) }
    val navigator = LocalNavigator.current
    val locale = appLocale
    val useOnlyDate by LocalAppContainer.current.prefs.useOnlyDate.collectAsState()
    val type = route.type
    var picker by rememberSaveable { mutableStateOf<String?>(null) }

    NavResultEffect(NavResults.CREATED_ACCOUNT) { viewModel.onAccountCreated(it) }
    NavResultEffect(NavResults.CREATED_CATEGORY) { viewModel.onCategoryCreated(it) }

    EditScaffold(
        title = stringResource(route.editType.title(EditForm.OPERATION)),
        headerColor = type.color,
        isModified = viewModel.isModified,
        onClose = { navigator.back() },
        onSave = { viewModel.save { navigator.back() } },
    ) {
        EditorCard(padded = false) {
            ListItem(
                headlineContent = {
                    Row {
                        TextButton(onClick = { picker = "date" }) { Text(Formats.mediumDate(viewModel.date, locale)) }
                        if (!useOnlyDate) {
                            TextButton(onClick = { picker = "time" }) { Text(Formats.time(viewModel.date, locale)) }
                        }
                    }
                },
                trailingContent = {
                    IconButton(onClick = { viewModel.resetDate() }) {
                        Icon(Icons.Filled.Today, contentDescription = stringResource(R.string.today), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        EditorCard(padded = false) {
            val addAccount = { destination: Boolean ->
                viewModel.pendingAccountSlotIsDestination = destination
                navigator.navigate(AccountEditRoute(EditType.CREATE))
            }
            val addCategory = { navigator.navigate(CategoryEditRoute(EditType.CREATE, type = type.categoryType)) }
            val sourceError = viewModel.hasError && viewModel.sourceMissing
            val destinationError = viewModel.hasError && viewModel.destinationMissing
            ShakeBox(viewModel.shakeTrigger, active = viewModel.sourceMissing) {
                if (type == OperationType.INCOME) {
                    CategoryField(stringResource(R.string.source), viewModel.category, sourceError, { picker = "category" }, addCategory)
                } else {
                    AccountField(stringResource(R.string.source), viewModel.account, sourceError, { picker = "account" }) { addAccount(false) }
                }
            }
            InsetDivider()
            ShakeBox(viewModel.shakeTrigger, active = viewModel.destinationMissing) {
                when (type) {
                    OperationType.INCOME ->
                        AccountField(stringResource(R.string.destination), viewModel.account, destinationError, { picker = "account" }) { addAccount(false) }
                    OperationType.EXPENSE ->
                        CategoryField(stringResource(R.string.destination), viewModel.category, destinationError, { picker = "category" }, addCategory)
                    else ->
                        AccountField(stringResource(R.string.destination), viewModel.addAccount, destinationError, { picker = "addAccount" }) { addAccount(true) }
                }
            }
        }
        EditorCard(padded = false) {
            // Quantity (1/3) and amount (2/3) on one line, the total right-aligned under them.
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.weight(1f)) {
                    ShakeBox(viewModel.shakeTrigger, active = viewModel.quantity == 0.0) {
                        AmountField(
                            value = viewModel.quantity,
                            onValueChange = { viewModel.quantity = it },
                            label = stringResource(R.string.quantity),
                            borderless = true,
                            isError = viewModel.hasError && viewModel.quantity == 0.0,
                        )
                    }
                }
                VerticalDivider(Modifier.padding(vertical = 8.dp))
                Box(Modifier.weight(2f)) {
                    ShakeBox(viewModel.shakeTrigger, active = viewModel.amount == 0.0) {
                        AmountField(
                            value = viewModel.amount,
                            onValueChange = { viewModel.amount = it },
                            label = stringResource(R.string.amount),
                            suffix = viewModel.account?.currency?.target?.symbol,
                            borderless = true,
                            isError = viewModel.hasError && viewModel.amount == 0.0,
                            showZero = true,
                        )
                    }
                }
            }
            InsetDivider()
            Text(
                text = "${stringResource(R.string.total)}: ${Formats.money(viewModel.total, viewModel.totalSymbol, locale)}",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
            if (viewModel.needsRate) {
                val c1 = viewModel.account?.currency?.target?.symbol.orEmpty()
                val c2 = viewModel.addAccount?.currency?.target?.symbol.orEmpty()
                val (from, to) = if (viewModel.mainRate == 1) c1 to c2 else c2 to c1
                InsetDivider()
                PickerField(
                    label = stringResource(R.string.exchangeRate),
                    value = "1 $from = ${Formats.number(viewModel.rate, locale)} $to",
                    onClick = { picker = "rate" },
                    borderless = true,
                )
            }
        }
        EditorCard(padded = false) {
            FormTextField(
                value = viewModel.note,
                onValueChange = { viewModel.note = it },
                borderless = true,
                label = { Text(stringResource(R.string.comment)) },
                singleLine = false,
                minLines = 3,
                maxLines = 8,
            )
        }
        EditorCard(padded = false) {
            CheckboxRow(stringResource(R.string.confirmed), viewModel.confirmed, { viewModel.confirmed = it })
        }
    }

    when (picker) {
        // With time in use, OK becomes "Next" and the time picker follows the date picker.
        "date" -> DatePickerModal(
            viewModel.date,
            onPick = {
                viewModel.date = utcDayToLocal(it, keepTimeOf = viewModel.date)
                if (!useOnlyDate) picker = "time"
            },
            onDismiss = { picker = null },
            confirmLabel = if (useOnlyDate) null else stringResource(R.string.next),
        )
        "time" -> TimePickerModal(viewModel.date, onPick = { viewModel.date = it }, onDismiss = { picker = null })
        "account" -> AccountPickerSheet(
            onDismiss = { picker = null },
            selectedIds = setOfNotNull(viewModel.account?.id),
            onSelect = { viewModel.selectAccount(it) },
            onAdd = {
                viewModel.pendingAccountSlotIsDestination = false
                navigator.navigate(AccountEditRoute(EditType.CREATE))
            },
        )
        "addAccount" -> AccountPickerSheet(
            onDismiss = { picker = null },
            exceptId = viewModel.account?.id,
            selectedIds = setOfNotNull(viewModel.addAccount?.id),
            onSelect = { viewModel.addAccount = it },
            onAdd = {
                viewModel.pendingAccountSlotIsDestination = true
                navigator.navigate(AccountEditRoute(EditType.CREATE))
            },
        )
        "category" -> CategoryPickerSheet(
            onDismiss = { picker = null },
            operationType = type,
            selectedIds = setOfNotNull(viewModel.category?.id),
            onSelect = { viewModel.category = it },
            onAdd = { navigator.navigate(CategoryEditRoute(EditType.CREATE, type = type.categoryType)) },
        )
        "rate" -> RateDialog(
            currency1 = viewModel.account?.currency?.target?.symbol.orEmpty(),
            currency2 = viewModel.addAccount?.currency?.target?.symbol.orEmpty(),
            mainRate = viewModel.mainRate,
            rate = viewModel.rate,
            onApply = { main, value ->
                viewModel.mainRate = main
                viewModel.rate = value
            },
            onDismiss = { picker = null },
        )
    }
}

@Composable
private fun AccountField(label: String, account: Account?, isError: Boolean, onClick: () -> Unit, onAdd: () -> Unit) {
    val locale = appLocale
    PickerField(
        label = label,
        value = account?.name.orEmpty(),
        onClick = onClick,
        isError = isError,
        borderless = true,
        // Current balance on the right of the account name, on the same line.
        suffix = account?.let { Formats.money(it.balance, it.currency.target?.symbol.orEmpty(), locale) },
        onAdd = onAdd,
    )
}

@Composable
private fun CategoryField(label: String, category: Category?, isError: Boolean, onClick: () -> Unit, onAdd: () -> Unit) {
    PickerField(
        label = label,
        value = category?.name.orEmpty(),
        onClick = onClick,
        isError = isError,
        borderless = true,
        onAdd = onAdd,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerModal(initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initial }
    val state = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE),
        is24Hour = android.text.format.DateFormat.is24HourFormat(LocalAppContainer.current.context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                calendar.set(Calendar.HOUR_OF_DAY, state.hour)
                calendar.set(Calendar.MINUTE, state.minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                onPick(calendar.timeInMillis)
                onDismiss()
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Exchange rate editor (iOS RateScreen): pick which currency is the "1" side, then the
 * rate. mainRate 1 = 1 [currency1] equals rate [currency2]; 2 = the reverse.
 */
@Composable
private fun RateDialog(
    currency1: String,
    currency2: String,
    mainRate: Int,
    rate: Double,
    onApply: (Int, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = appLocale
    var currentMain by rememberSaveable { mutableIntStateOf(mainRate) }
    var currentRate by rememberSaveable { mutableDoubleStateOf(rate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exchangeRate)) },
        text = {
            Column {
                listOf(1 to currency1, 2 to currency2).forEach { (value, symbol) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = currentMain == value, onClick = { currentMain = value }),
                    ) {
                        RadioButton(selected = currentMain == value, onClick = { currentMain = value })
                        Text("1 $symbol")
                    }
                }
                val target = if (currentMain == 1) currency2 else currency1
                AmountField(
                    value = currentRate,
                    onValueChange = { currentRate = it },
                    label = stringResource(R.string.rate),
                    suffix = target,
                    contentPadding = PaddingValues(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(currentMain, currentRate)
                onDismiss()
            }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
