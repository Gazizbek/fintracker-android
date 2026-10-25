package kz.alem.corporation.fintracker.ui.currencies

import kz.alem.corporation.fintracker.ui.common.ShakeBox
import kz.alem.corporation.fintracker.ui.common.InsetDivider
import kz.alem.corporation.fintracker.ui.common.CheckboxRow
import androidx.compose.material3.ListItem
import kz.alem.corporation.fintracker.ui.common.SwipeableListItem
import kz.alem.corporation.fintracker.ui.common.EditorCard
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kz.alem.corporation.fintracker.AppContainer
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.data.model.Currency
import kz.alem.corporation.fintracker.data.model.DEFAULT_COLOR
import kz.alem.corporation.fintracker.data.model.DatabaseError
import kz.alem.corporation.fintracker.data.model.DatabaseException
import kz.alem.corporation.fintracker.data.model.EditForm
import kz.alem.corporation.fintracker.data.model.EditType
import kz.alem.corporation.fintracker.data.model.OperationsFilter
import kz.alem.corporation.fintracker.data.model.title
import kz.alem.corporation.fintracker.ui.common.ColorRow
import kz.alem.corporation.fintracker.ui.common.EditScaffold
import kz.alem.corporation.fintracker.ui.common.IconChip
import kz.alem.corporation.fintracker.ui.common.ItemDivider
import kz.alem.corporation.fintracker.ui.common.LocalAppContainer
import kz.alem.corporation.fintracker.ui.common.MessageDialog
import kz.alem.corporation.fintracker.ui.common.SyncedIcon
import kz.alem.corporation.fintracker.ui.common.BackButton
import kz.alem.corporation.fintracker.ui.common.RequiredField
import kz.alem.corporation.fintracker.ui.common.AddFab
import kz.alem.corporation.fintracker.ui.common.FabClearance
import kz.alem.corporation.fintracker.ui.common.appViewModel
import kz.alem.corporation.fintracker.ui.navigation.CurrencyEditRoute
import kz.alem.corporation.fintracker.ui.navigation.LocalNavigator
import kz.alem.corporation.fintracker.ui.navigation.NavResults

// region List

class CurrenciesViewModel(private val container: AppContainer) : ViewModel() {
    private val store = container.store

    val currencies: StateFlow<List<Currency>> = store.changes
        .map { store.readCurrencies() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val error = MutableStateFlow<DatabaseError?>(null)

    fun delete(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        try {
            store.deleteCurrency(id)
        } catch (e: DatabaseException) {
            error.value = e.error
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrenciesScreen() {
    val viewModel = appViewModel { container, _ -> CurrenciesViewModel(container) }
    val navigator = LocalNavigator.current
    val prefs = LocalAppContainer.current.prefs
    val openFiltered by prefs.openFilteredOperation.collectAsState()
    val currencies by viewModel.currencies.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        floatingActionButton = { AddFab(onClick = { navigator.navigate(CurrencyEditRoute(EditType.CREATE)) }) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.currencies)) },
                navigationIcon = { BackButton() },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding), contentPadding = FabClearance) {
            items(currencies, key = { it.id }) { currency ->
                SwipeableListItem(
                    onEdit = { navigator.navigate(CurrencyEditRoute(EditType.UPDATE, currency.id)) },
                    onDelete = { viewModel.delete(currency.id) },
                    onTap = {
                        if (openFiltered) {
                            navigator.showOperations(OperationsFilter(currencies = listOf(currency)))
                        } else {
                            navigator.navigate(CurrencyEditRoute(EditType.UPDATE, currency.id))
                        }
                    },
                ) {
                    ListItem(
                        headlineContent = { Text(currency.name) },
                        supportingContent = { Text(currency.code) },
                        leadingContent = { IconChip(color = Color(currency.color), text = currency.symbol, isDefault = currency.useAsDefault) },
                        trailingContent = { SyncedIcon(currency.synced) },
                    )
                }
                ItemDivider()
            }
        }
    }
    error?.let { MessageDialog(stringResource(R.string.error), stringResource(it.message)) { viewModel.error.value = null } }
}

// endregion

// region Editor

private val currencySymbols = listOf(
    "₸", "₽", "$", "€", "£", "¥", "₿", "₣", "Br", "₡", "₱", "₹",
    "₪", "₩", "₭", "₮", "ƒ", "₦", "﷼", "лв", "₴", "฿", "د.إ", "₫",
)

class CurrencyEditViewModel(container: AppContainer, val editType: EditType, id: Long) : ViewModel() {
    private val store = container.store
    private var original: Currency? = null

    var name by mutableStateOf("")
    var code by mutableStateOf("")
    var symbol by mutableStateOf("")
    var color by mutableIntStateOf(DEFAULT_COLOR)
    var useAsDefault by mutableStateOf(false)
    var hasError by mutableStateOf(false)

    /** Bumped on every failed save so the missing fields shake. */
    var shakeTrigger by mutableIntStateOf(0)
        private set

    init {
        if (id != 0L) viewModelScope.launch {
            val currency = withContext(Dispatchers.IO) { store.getCurrency(id) } ?: return@launch
            original = currency
            name = currency.name
            code = currency.code
            symbol = currency.symbol
            color = currency.color
            useAsDefault = currency.useAsDefault
        }
    }

    val isModified: Boolean
        get() = original?.let {
            name != it.name || code != it.code || symbol != it.symbol || color != it.color || useAsDefault != it.useAsDefault
        } ?: (name.isNotEmpty() || code.isNotEmpty() || symbol.isNotEmpty() || color != DEFAULT_COLOR || useAsDefault)

    private fun validate(): Boolean {
        hasError = name.isEmpty() || code.isEmpty() || symbol.isEmpty()
        if (hasError) shakeTrigger++
        return !hasError
    }

    /** Saves and reports the record id, or does nothing if validation fails. */
    fun save(onSaved: (Long) -> Unit) {
        if (!validate()) return
        if (editType == EditType.UPDATE && original == null) return // still loading
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                if (useAsDefault) store.cleanCurrencyDefaults()
                val target = if (editType == EditType.UPDATE) original ?: Currency() else Currency()
                target.name = name
                target.code = code
                target.symbol = symbol
                target.color = color
                target.useAsDefault = useAsDefault
                if (editType == EditType.UPDATE) store.updateCurrency(target) else store.createCurrency(target)
            }
            onSaved(id)
        }
    }
}

@Composable
fun CurrencyEditScreen(route: CurrencyEditRoute) {
    val viewModel = appViewModel { container, _ -> CurrencyEditViewModel(container, route.editType, route.id) }
    val navigator = LocalNavigator.current
    var symbolMenu by remember { mutableStateOf(false) }

    EditScaffold(
        title = stringResource(route.editType.title(EditForm.CURRENCY)),
        headerColor = Color(viewModel.color),
        isModified = viewModel.isModified,
        onClose = { navigator.back() },
        onSave = {
            viewModel.save { id ->
                if (route.editType != EditType.UPDATE) navigator.setResult(NavResults.CREATED_CURRENCY, id)
                navigator.back()
            }
        },
        header = {
            IconChip(color = Color(viewModel.color), text = viewModel.symbol, size = 80.dp)
        },
    ) {
        EditorCard(padded = false) {
            ShakeBox(viewModel.shakeTrigger, active = viewModel.name.isEmpty()) {
                RequiredField(viewModel.name, { viewModel.name = it }, stringResource(R.string.currencyNameExample),
                    error = null, borderless = true, isError = viewModel.hasError && viewModel.name.isEmpty())
            }
            InsetDivider()
            ShakeBox(viewModel.shakeTrigger, active = viewModel.code.isEmpty()) {
                RequiredField(viewModel.code, { viewModel.code = it }, stringResource(R.string.currencyCodeExample),
                    error = null, borderless = true, isError = viewModel.hasError && viewModel.code.isEmpty(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            }
            InsetDivider()
            ShakeBox(viewModel.shakeTrigger, active = viewModel.symbol.isEmpty()) {
                RequiredField(
                    value = viewModel.symbol,
                    onValueChange = { viewModel.symbol = it.take(3) },
                    label = stringResource(R.string.symbol),
                    error = null,
                    borderless = true,
                    isError = viewModel.hasError && viewModel.symbol.isEmpty(),
                    trailing = {
                        // The menu is anchored to the button, so it opens at the right edge.
                        Box {
                            IconButton(onClick = { symbolMenu = true }) { Icon(Icons.Filled.MoreHoriz, contentDescription = null) }
                            DropdownMenu(expanded = symbolMenu, onDismissRequest = { symbolMenu = false }) {
                                currencySymbols.forEach { s ->
                                    DropdownMenuItem(text = { Text(s) }, onClick = {
                                        viewModel.symbol = s
                                        symbolMenu = false
                                    })
                                }
                            }
                        }
                    },
                )
            }
        }
        EditorCard(padded = false) {
            CheckboxRow(stringResource(R.string.useAsDefault), viewModel.useAsDefault, { viewModel.useAsDefault = it })
        }
        EditorCard(padded = false) { ColorRow(viewModel.color) { viewModel.color = it } }
    }
}

// endregion
