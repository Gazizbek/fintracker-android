package kz.alem.corporation.fintracker.ui.accounts

import kz.alem.corporation.fintracker.ui.common.InsetDivider
import androidx.compose.material3.ListItem
import kz.alem.corporation.fintracker.ui.common.SwipeableListItem
import kz.alem.corporation.fintracker.ui.common.PickerField
import kz.alem.corporation.fintracker.ui.common.EditorCard
import kz.alem.corporation.fintracker.ui.common.EditScaffold
import kz.alem.corporation.fintracker.ui.common.RequiredField
import kz.alem.corporation.fintracker.ui.common.ShakeBox
import kz.alem.corporation.fintracker.ui.common.DropdownField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.foundation.layout.padding
import kz.alem.corporation.fintracker.ui.common.CheckboxRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import kz.alem.corporation.fintracker.ui.common.AppTopBar
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kz.alem.corporation.fintracker.AppContainer
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.data.model.Account
import kz.alem.corporation.fintracker.data.model.AccountIcons
import kz.alem.corporation.fintracker.data.model.AccountType
import kz.alem.corporation.fintracker.data.model.Currency
import kz.alem.corporation.fintracker.data.model.DEFAULT_COLOR
import kz.alem.corporation.fintracker.data.model.DatabaseError
import kz.alem.corporation.fintracker.data.model.DatabaseException
import kz.alem.corporation.fintracker.data.model.EditForm
import kz.alem.corporation.fintracker.data.model.EditType
import kz.alem.corporation.fintracker.data.model.OperationsFilter
import kz.alem.corporation.fintracker.data.model.title
import kz.alem.corporation.fintracker.ui.common.AmountField
import kz.alem.corporation.fintracker.ui.common.ColorRow
import kz.alem.corporation.fintracker.ui.common.CurrencyPickerSheet
import kz.alem.corporation.fintracker.ui.common.Formats
import kz.alem.corporation.fintracker.ui.common.IconChip
import kz.alem.corporation.fintracker.ui.common.IconPickerSheet
import kz.alem.corporation.fintracker.ui.common.ItemDivider
import kz.alem.corporation.fintracker.ui.common.LocalAppContainer
import kz.alem.corporation.fintracker.ui.common.MessageDialog
import kz.alem.corporation.fintracker.ui.common.SearchField
import kz.alem.corporation.fintracker.ui.common.SectionHeader
import kz.alem.corporation.fintracker.ui.common.SyncedIcon
import kz.alem.corporation.fintracker.ui.common.appLocale
import kz.alem.corporation.fintracker.ui.common.AddFab
import kz.alem.corporation.fintracker.ui.common.FabClearance
import kz.alem.corporation.fintracker.ui.common.appViewModel
import kz.alem.corporation.fintracker.ui.common.imageVector
import kz.alem.corporation.fintracker.ui.navigation.AccountEditRoute
import kz.alem.corporation.fintracker.ui.navigation.AccountsGraph
import kz.alem.corporation.fintracker.ui.navigation.CurrencyEditRoute
import kz.alem.corporation.fintracker.ui.navigation.LocalNavigator
import kz.alem.corporation.fintracker.ui.navigation.NavResultEffect
import kz.alem.corporation.fintracker.ui.navigation.NavResults
import kz.alem.corporation.fintracker.ui.navigation.ScrollToTopOnReselect

// region List

data class AccountSection(val currency: Currency?, val total: Double, val accounts: List<Account>)

class AccountsViewModel(container: AppContainer) : ViewModel() {
    private val store = container.store
    val showAll = MutableStateFlow(false)
    val query = MutableStateFlow("")
    val error = MutableStateFlow<DatabaseError?>(null)

    /** Accounts grouped by currency, each with the total of the accounts included in it. */
    val sections: StateFlow<List<AccountSection>> = combine(store.changes, showAll, query) { _, all, q ->
        val needle = q.lowercase()
        store.readAccounts(onlyVisible = !all)
            .groupBy { it.currency.targetId }
            .map { (_, accounts) ->
                AccountSection(
                    currency = accounts.first().currency.target,
                    total = accounts.filter { it.inSummary }.sumOf { it.balance },
                    accounts = accounts.filter { needle.isEmpty() || it.name.lowercase().contains(needle) },
                )
            }
            .filter { it.accounts.isNotEmpty() }
            .sortedBy { it.currency?.name.orEmpty() }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        try {
            store.deleteAccount(id)
        } catch (e: DatabaseException) {
            error.value = e.error
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AccountsScreen() {
    val viewModel = appViewModel { container, _ -> AccountsViewModel(container) }
    val navigator = LocalNavigator.current
    val locale = appLocale
    val openFiltered by LocalAppContainer.current.prefs.openFilteredOperation.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val showAll by viewModel.showAll.collectAsState()
    val query by viewModel.query.collectAsState()
    val error by viewModel.error.collectAsState()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    ScrollToTopOnReselect(AccountsGraph, listState)
    val totalLabel = stringResource(R.string.total)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = { AddFab(onClick = { navigator.navigate(AccountEditRoute(EditType.CREATE)) }) },
        topBar = {
            AppTopBar(
                title = stringResource(R.string.accounts),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { viewModel.showAll.value = !showAll }) {
                        Icon(
                            if (showAll) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (showAll) R.string.hideHidden else R.string.showHidden),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.padding(padding), contentPadding = FabClearance) {
            item { SearchField(query, { viewModel.query.value = it }) }
            sections.forEach { section ->
                val symbol = section.currency?.symbol.orEmpty()
                stickyHeader(key = "currency-${section.currency?.id}") {
                    SectionHeader("$totalLabel: ${Formats.money(section.total, symbol, locale)}")
                }
                items(section.accounts, key = { it.id }) { account ->
                    SwipeableListItem(
                        onEdit = { navigator.navigate(AccountEditRoute(EditType.UPDATE, account.id)) },
                        onDelete = { viewModel.delete(account.id) },
                        onTap = {
                            if (openFiltered) {
                                navigator.showOperations(OperationsFilter(accounts = listOf(account)))
                            } else {
                                navigator.navigate(AccountEditRoute(EditType.UPDATE, account.id))
                            }
                        },
                    ) {
                        ListItem(
                            headlineContent = { Text(account.name) },
                            supportingContent = { Text(Formats.money(account.balance, symbol, locale)) },
                            leadingContent = { IconChip(color = Color(account.color), icon = account.iconEnum.imageVector, isDefault = account.useAsDefault) },
                            trailingContent = { SyncedIcon(account.synced) },
                        )
                    }
                    ItemDivider()
                }
            }
        }
    }
    error?.let { MessageDialog(stringResource(R.string.error), stringResource(it.message)) { viewModel.error.value = null } }
}

// endregion

// region Editor

class AccountEditViewModel(container: AppContainer, val editType: EditType, id: Long) : ViewModel() {
    private val store = container.store
    private var original: Account? = null

    var name by mutableStateOf("")
    var type by mutableStateOf(AccountType.CREDIT_CARD)
    var mainBalance by mutableDoubleStateOf(0.0)
    var currency by mutableStateOf<Currency?>(null)
    var color by mutableIntStateOf(DEFAULT_COLOR)
    var icon by mutableStateOf(AccountIcons.creditcard)
    var visible by mutableStateOf(true)
    var useAsDefault by mutableStateOf(false)
    var inSummary by mutableStateOf(true)
    var hasError by mutableStateOf(false)

    /** Bumped on every failed save so the missing fields shake. */
    var shakeTrigger by mutableIntStateOf(0)
        private set

    init {
        viewModelScope.launch {
            if (id != 0L) {
                val account = withContext(Dispatchers.IO) { store.getAccount(id) } ?: return@launch
                original = account
                name = account.name
                type = account.typeEnum
                mainBalance = account.mainBalance
                currency = account.currency.target
                color = account.color
                icon = account.iconEnum
                visible = account.visible
                useAsDefault = account.useAsDefault
                inSummary = account.inSummary
            } else {
                currency = withContext(Dispatchers.IO) { store.getDefaultCurrency() }
            }
        }
    }

    val isModified: Boolean
        get() = original?.let {
            (currency?.id ?: 0L) != it.currency.targetId || name != it.name || type != it.typeEnum || icon != it.iconEnum ||
                color != it.color || mainBalance != it.mainBalance || visible != it.visible ||
                useAsDefault != it.useAsDefault || inSummary != it.inSummary
        } ?: (name.isNotEmpty() || type != AccountType.CREDIT_CARD || icon != AccountIcons.creditcard || color != DEFAULT_COLOR)

    fun onCurrencyCreated(id: Long) = viewModelScope.launch {
        currency = withContext(Dispatchers.IO) { store.getCurrency(id) }
    }

    fun save(onSaved: (Long) -> Unit) {
        hasError = name.isEmpty() || currency == null
        if (hasError) {
            shakeTrigger++
            return
        }
        if (editType == EditType.UPDATE && original == null) return // still loading
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                if (useAsDefault) store.cleanAccountDefaults()
                val existing = original
                val target = if (editType == EditType.UPDATE && existing != null) {
                    // Shift the derived balance by the change of the starting balance.
                    existing.balance = existing.balance - existing.mainBalance + mainBalance
                    existing
                } else {
                    Account(balance = mainBalance)
                }
                target.name = name
                target.typeEnum = type
                target.mainBalance = mainBalance
                target.currency.target = currency
                target.color = color
                target.iconEnum = icon
                target.visible = visible
                target.useAsDefault = useAsDefault
                target.inSummary = inSummary
                if (editType == EditType.UPDATE) store.updateAccount(target) else store.createAccount(target)
            }
            onSaved(id)
        }
    }
}

@Composable
fun AccountEditScreen(route: AccountEditRoute) {
    val viewModel = appViewModel { container, _ -> AccountEditViewModel(container, route.editType, route.id) }
    val navigator = LocalNavigator.current
    var iconPicker by rememberSaveable { mutableStateOf(false) }
    var currencyPicker by rememberSaveable { mutableStateOf(false) }

    NavResultEffect(NavResults.CREATED_CURRENCY) { viewModel.onCurrencyCreated(it) }

    EditScaffold(
        title = stringResource(route.editType.title(EditForm.ACCOUNT)),
        headerColor = Color(viewModel.color),
        isModified = viewModel.isModified,
        onClose = { navigator.back() },
        onSave = {
            viewModel.save { id ->
                if (route.editType != EditType.UPDATE) navigator.setResult(NavResults.CREATED_ACCOUNT, id)
                navigator.back()
            }
        },
        header = {
            IconChip(color = Color(viewModel.color), icon = viewModel.icon.imageVector, size = 80.dp, onClick = { iconPicker = true })
        },
    ) {
        EditorCard(padded = false) {
            // Required fields: no message underneath; a missing one turns red and shakes (iOS).
            ShakeBox(viewModel.shakeTrigger, active = viewModel.name.isEmpty()) {
                RequiredField(viewModel.name, { viewModel.name = it }, stringResource(R.string.accountNameExample),
                    error = null, borderless = true, isError = viewModel.hasError && viewModel.name.isEmpty())
            }
            InsetDivider()
            DropdownField(
                label = stringResource(R.string.accountType),
                options = AccountType.entries,
                selected = viewModel.type,
                optionLabel = { stringResource(it.title) },
                onSelect = { viewModel.type = it },
                borderless = true,
            )
            InsetDivider()
            AmountField(
                value = viewModel.mainBalance,
                onValueChange = { viewModel.mainBalance = it },
                label = stringResource(R.string.initialAmount),
                suffix = viewModel.currency?.symbol,
                borderless = true,
                showZero = true,
            )
            InsetDivider()
            ShakeBox(viewModel.shakeTrigger, active = viewModel.currency == null) {
                PickerField(
                    label = stringResource(R.string.currency),
                    value = viewModel.currency?.let { "${it.name} (${it.code})" }.orEmpty(),
                    onClick = { currencyPicker = true },
                    onAdd = { navigator.navigate(CurrencyEditRoute(EditType.CREATE)) },
                    borderless = true,
                    isError = viewModel.hasError && viewModel.currency == null,
                )
            }
        }
        EditorCard(padded = false) {
            CheckboxRow(stringResource(R.string.inTotalByCurrency), viewModel.inSummary, { viewModel.inSummary = it })
            InsetDivider()
            CheckboxRow(stringResource(R.string.useAsDefault), viewModel.useAsDefault, { viewModel.useAsDefault = it })
            InsetDivider()
            CheckboxRow(stringResource(R.string.visible), viewModel.visible, { viewModel.visible = it })
        }
        EditorCard(padded = false) { ColorRow(viewModel.color) { viewModel.color = it } }
    }

    if (iconPicker) {
        IconPickerSheet(AccountIcons.entries, viewModel.icon, { it.imageVector }, { viewModel.icon = it }, { iconPicker = false })
    }
    if (currencyPicker) {
        CurrencyPickerSheet(
            onDismiss = { currencyPicker = false },
            selectedIds = setOfNotNull(viewModel.currency?.id),
            onSelect = { viewModel.currency = it },
        )
    }
}

// endregion
