package kz.alem.corporation.fintracker.ui.planning

import androidx.compose.runtime.mutableIntStateOf
import kz.alem.corporation.fintracker.ui.common.ShakeBox
import kz.alem.corporation.fintracker.ui.common.InsetDivider
import androidx.compose.material3.ListItem
import kz.alem.corporation.fintracker.ui.common.SwipeableListItem
import kz.alem.corporation.fintracker.ui.common.PickerField
import kz.alem.corporation.fintracker.ui.common.EditorCard
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import kz.alem.corporation.fintracker.ui.common.AppTopBar
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import kz.alem.corporation.fintracker.data.FinanceStore
import kz.alem.corporation.fintracker.data.inferPeriod
import kz.alem.corporation.fintracker.data.model.Category
import kz.alem.corporation.fintracker.data.model.CategoryGroup
import kz.alem.corporation.fintracker.data.model.Currency
import kz.alem.corporation.fintracker.data.model.EditForm
import kz.alem.corporation.fintracker.data.model.EditType
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.data.model.OperationsFilter
import kz.alem.corporation.fintracker.data.model.Period
import kz.alem.corporation.fintracker.data.model.Plan
import kz.alem.corporation.fintracker.data.model.title
import kz.alem.corporation.fintracker.data.range
import kz.alem.corporation.fintracker.data.roundCents
import kz.alem.corporation.fintracker.ui.common.AmountField
import kz.alem.corporation.fintracker.ui.common.CategoryGroupPickerSheet
import kz.alem.corporation.fintracker.ui.common.CategoryPickerSheet
import kz.alem.corporation.fintracker.ui.common.CurrencyPickerSheet
import kz.alem.corporation.fintracker.ui.common.EditScaffold
import kz.alem.corporation.fintracker.ui.common.EmptyState
import kz.alem.corporation.fintracker.ui.common.Formats
import kz.alem.corporation.fintracker.ui.common.IconChip
import kz.alem.corporation.fintracker.ui.common.ItemDivider
import kz.alem.corporation.fintracker.ui.common.PeriodPicker
import kz.alem.corporation.fintracker.ui.common.RequiredField
import kz.alem.corporation.fintracker.ui.common.SyncedIcon
import kz.alem.corporation.fintracker.ui.common.appLocale
import kz.alem.corporation.fintracker.ui.common.AddFab
import kz.alem.corporation.fintracker.ui.common.FabClearance
import kz.alem.corporation.fintracker.ui.common.appViewModel
import kz.alem.corporation.fintracker.ui.common.color
import kz.alem.corporation.fintracker.ui.common.icon
import kz.alem.corporation.fintracker.ui.navigation.LocalNavigator
import kz.alem.corporation.fintracker.ui.navigation.PlanEditRoute
import kz.alem.corporation.fintracker.ui.theme.TypeColors

data class PlanProgress(
    val spent: Double = 0.0,
    val target: Double = 0.0,
    /** Clamped to 0..1 for the progress bar. */
    val percent: Float = 0f,
    /** Unclamped, e.g. 1.2 for 120 %. */
    val rawPercent: Double = 0.0,
    val isOverLimit: Boolean = false,
)

/**
 * Sums matching operations over the plan's frozen date window, through the same filter
 * path the operations list and reports use.
 */
fun FinanceStore.computeProgress(plan: Plan): PlanProgress {
    val categories = plan.categories.toList()
    val groups = plan.categoryGroups.toList()
    val filter = OperationsFilter(
        period = Period.ARBITRARY,
        beginDate = plan.beginDate,
        endDate = plan.endDate,
        operationTypes = listOf(plan.typeEnum),
        currencies = listOfNotNull(plan.currency.target),
        categories = categories,
        categoryGroups = if (categories.isEmpty()) groups else emptyList(),
    )
    val spent = readOperations(filter).sumOf { it.amount * it.quantity }.roundCents()
    val target = plan.amount
    val raw = if (target > 0) spent / target else 0.0
    return PlanProgress(spent, target, raw.coerceIn(0.0, 1.0).toFloat(), raw, target > 0 && spent > target)
}

data class PlanItem(val plan: Plan, val progress: PlanProgress, val scopeNames: List<String>)

data class PlanningState(val hasPlans: Boolean = false, val items: List<PlanItem> = emptyList())

class PlanningViewModel(container: AppContainer) : ViewModel() {
    private val store = container.store
    val showAll = MutableStateFlow(false)

    val state: StateFlow<PlanningState> = combine(store.changes, showAll) { _, all ->
        val now = System.currentTimeMillis()
        val plans = store.readPlans()
        val displayed = if (all) plans else plans.filter { now in it.beginDate..it.endDate }
        PlanningState(
            hasPlans = plans.isNotEmpty(),
            items = displayed.map { plan ->
                val names = plan.categories.map { it.name }.ifEmpty { plan.categoryGroups.map { it.name } }
                PlanItem(plan, store.computeProgress(plan), names)
            },
        )
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanningState())

    fun delete(id: Long) = viewModelScope.launch(Dispatchers.IO) { store.deletePlan(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningScreen() {
    val viewModel = appViewModel { container, _ -> PlanningViewModel(container) }
    val navigator = LocalNavigator.current
    val state by viewModel.state.collectAsState()
    val showAll by viewModel.showAll.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = { AddFab(onClick = { navigator.navigate(PlanEditRoute(EditType.CREATE)) }) },
        topBar = {
            AppTopBar(
                title = stringResource(R.string.planning),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { viewModel.showAll.value = !showAll }) {
                        Icon(
                            if (showAll) Icons.Filled.AllInclusive else Icons.Filled.CalendarMonth,
                            contentDescription = stringResource(if (showAll) R.string.showAllPlans else R.string.showActivePlans),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.hasPlans -> EmptyState(
                Icons.Filled.TrackChanges,
                stringResource(R.string.noPlansYet),
                stringResource(R.string.noPlansYetDescription),
                Modifier.padding(padding),
            )
            state.items.isEmpty() -> EmptyState(
                Icons.Filled.CalendarMonth,
                stringResource(R.string.noActivePlans),
                stringResource(R.string.noActivePlansDescription),
                Modifier.padding(padding),
            )
            else -> LazyColumn(modifier = Modifier.padding(padding), contentPadding = FabClearance) {
                items(state.items, key = { it.plan.id }) { item ->
                    PlanRow(
                        item = item,
                        onClick = { navigator.navigate(PlanEditRoute(EditType.UPDATE, item.plan.id)) },
                        onDelete = { viewModel.delete(item.plan.id) },
                    )
                    ItemDivider()
                }
            }
        }
    }

}

@Composable
private fun PlanRow(item: PlanItem, onClick: () -> Unit, onDelete: () -> Unit) {
    val locale = appLocale
    val plan = item.plan
    val progress = item.progress
    val type = plan.typeEnum
    val symbol = plan.currency.target?.symbol.orEmpty()
    val scope = item.scopeNames.joinToString(", ").ifEmpty {
        stringResource(if (type == OperationType.EXPENSE) R.string.allExpenses else R.string.allIncome)
    }
    val warnColor = MaterialTheme.colorScheme.error
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    SwipeableListItem(
        onEdit = onClick,
        onDelete = onDelete,
        onTap = onClick,
        modifier = Modifier.alpha(if (plan.endDate < System.currentTimeMillis()) 0.5f else 1f),
    ) {
        ListItem(
            headlineContent = { Text(plan.name) },
            supportingContent = {
            Column {
                Text(scope, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Formats.mediumDate(plan.beginDate, locale)} – ${Formats.mediumDate(plan.endDate, locale)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { progress.percent },
                    color = if (progress.isOverLimit || type == OperationType.EXPENSE) TypeColors.expense else TypeColors.income,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${Formats.number(progress.spent, locale)} / ${Formats.number(progress.target, locale)} $symbol",
                        color = if (progress.isOverLimit) warnColor else secondary,
                    )
                    if (progress.isOverLimit) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = warnColor, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${Math.round(progress.rawPercent * 100)}%",
                        color = if (progress.isOverLimit) warnColor else secondary,
                    )
                }
            }
        },
            leadingContent = { IconChip(color = type.color, icon = type.icon) },
            trailingContent = { SyncedIcon(plan.synced) },
        )
    }
}

// region Editor

enum class PlanScope { ALL, CATEGORY, GROUP }

class PlanEditViewModel(container: AppContainer, val editType: EditType, id: Long) : ViewModel() {
    private val store = container.store
    private var original: Plan? = null
    private var originalCategoryIds: Set<Long> = emptySet()
    private var originalGroupIds: Set<Long> = emptySet()

    private val defaultRange = Period.CURRENT_MONTH.range()!!
    var name by mutableStateOf("")
    var type by mutableStateOf(OperationType.EXPENSE)
    var period by mutableStateOf(Period.CURRENT_MONTH)
    var beginDate by mutableLongStateOf(defaultRange.first)
    var endDate by mutableLongStateOf(defaultRange.second)
    var amount by mutableDoubleStateOf(0.0)
    var currency by mutableStateOf<Currency?>(null)
    var categories by mutableStateOf<List<Category>>(emptyList())
    var groups by mutableStateOf<List<CategoryGroup>>(emptyList())
    var scope by mutableStateOf(PlanScope.ALL)
    var hasError by mutableStateOf(false)

    /** Bumped on every failed save so the missing fields shake. */
    var shakeTrigger by mutableIntStateOf(0)
        private set

    init {
        viewModelScope.launch {
            if (id != 0L) {
                val loaded = withContext(Dispatchers.IO) {
                    store.getPlan(id)?.let { Triple(it, it.categories.toList(), it.categoryGroups.toList()) }
                } ?: return@launch
                val (plan, planCategories, planGroups) = loaded
                original = plan
                originalCategoryIds = planCategories.map { it.id }.toSet()
                originalGroupIds = planGroups.map { it.id }.toSet()
                name = plan.name
                type = plan.typeEnum
                period = inferPeriod(plan.beginDate, plan.endDate)
                beginDate = plan.beginDate
                endDate = plan.endDate
                amount = plan.amount
                currency = withContext(Dispatchers.IO) { plan.currency.target }
                categories = planCategories
                groups = planGroups
                scope = when {
                    planCategories.isNotEmpty() -> PlanScope.CATEGORY
                    planGroups.isNotEmpty() -> PlanScope.GROUP
                    else -> PlanScope.ALL
                }
            } else {
                currency = withContext(Dispatchers.IO) { store.getDefaultCurrency() }
            }
        }
    }

    // The auto-filled default currency does not count as a change for new plans.
    val isModified: Boolean
        get() = original?.let {
            name != it.name || type != it.typeEnum || beginDate != it.beginDate || endDate != it.endDate ||
                amount != it.amount || (currency?.id ?: 0L) != it.currency.targetId ||
                categories.map { c -> c.id }.toSet() != originalCategoryIds ||
                groups.map { g -> g.id }.toSet() != originalGroupIds
        } ?: (name.isNotEmpty() || amount != 0.0 || categories.isNotEmpty() || groups.isNotEmpty())

    fun changeScope(value: PlanScope) {
        scope = value
        when (value) {
            PlanScope.ALL -> {
                categories = emptyList()
                groups = emptyList()
            }
            PlanScope.CATEGORY -> groups = emptyList()
            PlanScope.GROUP -> categories = emptyList()
        }
    }

    fun save(onSaved: () -> Unit) {
        hasError = name.isEmpty() || amount <= 0 || currency == null
        if (hasError) {
            shakeTrigger++
            return
        }
        if (editType == EditType.UPDATE && original == null) return // still loading
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val plan = if (editType == EditType.UPDATE) original!! else Plan()
                plan.name = name
                plan.typeEnum = type
                plan.beginDate = beginDate
                plan.endDate = endDate
                plan.amount = amount
                plan.currency.target = currency
                plan.categories.clear()
                plan.categories.addAll(categories)
                plan.categoryGroups.clear()
                plan.categoryGroups.addAll(groups)
                if (editType == EditType.UPDATE) store.updatePlan(plan) else store.createPlan(plan)
            }
            onSaved()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditScreen(route: PlanEditRoute) {
    val viewModel = appViewModel { container, _ -> PlanEditViewModel(container, route.editType, route.id) }
    val navigator = LocalNavigator.current
    val locale = appLocale
    var picker by rememberSaveable { mutableStateOf<String?>(null) }

    EditScaffold(
        title = stringResource(route.editType.title(EditForm.PLAN)),
        headerColor = viewModel.type.color,
        isModified = viewModel.isModified,
        onClose = { navigator.back() },
        onSave = { viewModel.save { navigator.back() } },
    ) {
        EditorCard(padded = false) {
            ShakeBox(viewModel.shakeTrigger, active = viewModel.name.isEmpty()) {
                RequiredField(viewModel.name, { viewModel.name = it }, stringResource(R.string.planNameExample),
                    error = null, borderless = true, isError = viewModel.hasError && viewModel.name.isEmpty())
            }
            InsetDivider()
            SegmentedChoice(
                options = listOf(OperationType.EXPENSE, OperationType.INCOME),
                selected = viewModel.type,
                label = { stringResource(it.title) },
                onSelect = { viewModel.type = it },
            )
        }
        EditorCard(padded = false) {
            PeriodPicker(
                period = viewModel.period,
                beginDate = viewModel.beginDate,
                endDate = viewModel.endDate,
                onChange = { p, b, e ->
                    viewModel.period = p
                    viewModel.beginDate = b
                    viewModel.endDate = e
                },
                // A plan needs a bounded window.
                periods = Period.entries - Period.ALL_TIME,
                borderless = true,
            )
        }
        EditorCard {
            Text(
                stringResource(R.string.scope),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            SegmentedChoice(
                options = PlanScope.entries,
                selected = viewModel.scope,
                label = {
                    stringResource(
                        when (it) {
                            PlanScope.ALL -> R.string.allOperations
                            PlanScope.CATEGORY -> R.string.byCategory
                            PlanScope.GROUP -> R.string.byCategoryGroup
                        }
                    )
                },
                onSelect = { viewModel.changeScope(it) },
            )
            when (viewModel.scope) {
                PlanScope.CATEGORY -> {
                    InsetDivider()
                    PickerField(
                        label = stringResource(R.string.byCategory),
                        value = viewModel.categories.joinToString(", ") { it.name },
                        onClick = { picker = "categories" },
                        borderless = true,
                    )
                }
                PlanScope.GROUP -> {
                    InsetDivider()
                    PickerField(
                        label = stringResource(R.string.byCategoryGroup),
                        value = viewModel.groups.joinToString(", ") { it.name },
                        onClick = { picker = "groups" },
                        borderless = true,
                    )
                }
                PlanScope.ALL -> Unit
            }
        }
        EditorCard(padded = false) {
            ShakeBox(viewModel.shakeTrigger, active = viewModel.amount <= 0) {
                AmountField(
                    value = viewModel.amount,
                    onValueChange = { viewModel.amount = it },
                    label = stringResource(R.string.amount),
                    suffix = viewModel.currency?.symbol,
                    borderless = true,
                    showZero = true,
                    isError = viewModel.hasError && viewModel.amount <= 0,
                )
            }
            InsetDivider()
            ShakeBox(viewModel.shakeTrigger, active = viewModel.currency == null) {
                PickerField(
                    label = stringResource(R.string.currency),
                    value = viewModel.currency?.let { "${it.name} (${it.code})" }.orEmpty(),
                    onClick = { picker = "currency" },
                    borderless = true,
                    isError = viewModel.hasError && viewModel.currency == null,
                )
            }
        }
    }

    when (picker) {
        "currency" -> CurrencyPickerSheet(
            onDismiss = { picker = null },
            selectedIds = setOfNotNull(viewModel.currency?.id),
            onSelect = { viewModel.currency = it },
        )
        "categories" -> CategoryPickerSheet(
            onDismiss = {
                picker = null
                if (viewModel.categories.isEmpty()) viewModel.changeScope(PlanScope.ALL)
            },
            operationType = viewModel.type,
            multi = true,
            selectedIds = viewModel.categories.map { it.id }.toSet(),
            onChoose = { viewModel.categories = it },
        )
        "groups" -> CategoryGroupPickerSheet(
            onDismiss = {
                picker = null
                if (viewModel.groups.isEmpty()) viewModel.changeScope(PlanScope.ALL)
            },
            multi = true,
            selectedIds = viewModel.groups.map { it.id }.toSet(),
            onChoose = { viewModel.groups = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedChoice(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                // No checkmark: the selected segment is already shown by its fill.
                icon = {},
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

// endregion
