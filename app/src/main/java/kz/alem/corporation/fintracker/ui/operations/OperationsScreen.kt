package kz.alem.corporation.fintracker.ui.operations

import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ListItem
import kz.alem.corporation.fintracker.ui.common.SwipeableListItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import kz.alem.corporation.fintracker.ui.common.FabClearance
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import kz.alem.corporation.fintracker.ui.common.AppTopBar
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
import kz.alem.corporation.fintracker.AppContainer
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.data.calculateSum
import kz.alem.corporation.fintracker.data.model.EditType
import kz.alem.corporation.fintracker.data.model.Operation
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.data.model.OperationsFilter
import kz.alem.corporation.fintracker.data.monthKey
import kz.alem.corporation.fintracker.ui.common.FilterDialog
import kz.alem.corporation.fintracker.ui.common.Formats
import kz.alem.corporation.fintracker.ui.common.IconChip
import kz.alem.corporation.fintracker.ui.common.ItemDivider
import kz.alem.corporation.fintracker.ui.common.SectionHeader
import kz.alem.corporation.fintracker.ui.common.SyncedIcon
import kz.alem.corporation.fintracker.ui.common.appLocale
import kz.alem.corporation.fintracker.ui.common.color
import kz.alem.corporation.fintracker.ui.common.icon
import kz.alem.corporation.fintracker.ui.navigation.LocalNavigator
import kz.alem.corporation.fintracker.ui.navigation.OperationEditRoute
import kz.alem.corporation.fintracker.ui.navigation.OperationsGraph
import kz.alem.corporation.fintracker.ui.navigation.ScrollToTopOnReselect

/** Operations for one month, or the unconfirmed ones (monthKey == null). */
data class OperationSection(val monthKey: Int?, val operations: List<Operation>)

/**
 * Operations list state. Activity-scoped so catalog screens and reports can open this
 * tab with a filter (iOS shares one OperationViewModel the same way).
 */
class OperationsViewModel(container: AppContainer) : ViewModel() {
    private val store = container.store

    val filter = MutableStateFlow<OperationsFilter?>(null)
    val isRefreshing = MutableStateFlow(false)

    val sections: StateFlow<List<OperationSection>> = combine(store.changes, filter) { _, f ->
        val operations = store.readOperations(f)
        val unconfirmed = operations.filter { !it.confirmed }
        val byMonth = operations.filter { it.confirmed }
            .groupBy { monthKey(it.date) }
            .toSortedMap(compareByDescending { it })
            .map { (key, list) -> OperationSection(key, list) }
        if (unconfirmed.isEmpty()) byMonth else listOf(OperationSection(null, unconfirmed)) + byMonth
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(operation: Operation) = viewModelScope.launch(Dispatchers.IO) {
        val current = store.getOperation(operation.id) ?: return@launch
        store.runInTx {
            if (current.confirmed) {
                val balance = current.amount * current.quantity
                if (balance > 0) store.recalcAccountBalance(current.id, -balance)
            }
            store.deleteOperation(current.id)
        }
    }

    fun refresh() = viewModelScope.launch {
        isRefreshing.value = true
        store.notifyExternalChange()
        isRefreshing.value = false
    }
}

/** Amount in the destination currency, as shown on iOS rows. */
fun Operation.displayTotal(): Double = calculateSum(amount * quantity, rate, mainRate)

fun Operation.displaySymbol(): String =
    addAccount.target?.currency?.target?.symbol ?: account.target?.currency?.target?.symbol.orEmpty()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OperationsScreen(viewModel: OperationsViewModel) {
    val navigator = LocalNavigator.current
    val locale = appLocale
    val sections by viewModel.sections.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showFilter by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    ScrollToTopOnReselect(OperationsGraph, listState)
    var fabExpanded by rememberSaveable { mutableStateOf(false) }

    // The FAB lives outside the Scaffold so the scrim can sit between it and the screen.
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                AppTopBar(
                    title = stringResource(R.string.operations),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = { showFilter = true }) {
                            Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.filter))
                        }
                        if (filter?.isInitial == false) {
                            IconButton(onClick = { viewModel.filter.value = null }) {
                                Icon(Icons.Filled.FilterListOff, contentDescription = stringResource(R.string.clearFilter))
                            }
                        }
                    },
                )
            },
        ) { padding ->
            PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refresh() }, modifier = Modifier.padding(padding)) {
                LazyColumn(state = listState, contentPadding = FabClearance) {
                    sections.forEach { section ->
                        stickyHeader(key = "section-${section.monthKey}", contentType = "header") {
                            SectionHeader(
                                if (section.monthKey == null) stringResource(R.string.unconfirmed)
                                else Formats.monthYear(kz.alem.corporation.fintracker.data.monthKeyStart(section.monthKey), locale)
                            )
                        }
                        items(section.operations, key = { it.id }, contentType = { "operation" }) { operation ->
                            OperationRow(
                                operation = operation,
                                onClick = { navigator.navigate(OperationEditRoute(EditType.UPDATE, operation.typeEnum, operation.id)) },
                                onCopy = { navigator.navigate(OperationEditRoute(EditType.COPY, operation.typeEnum, operation.id)) },
                                onDelete = { viewModel.delete(operation) },
                            )
                            ItemDivider()
                        }
                    }
                }
            }
        }

        // Dimmed backdrop while the FAB menu is open; tapping it collapses the menu.
        AnimatedVisibility(visible = fabExpanded, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = stringResource(R.string.close),
                    ) { fabExpanded = false },
            )
        }
        AddOperationFabMenu(
            expanded = fabExpanded,
            onExpandedChange = { fabExpanded = it },
            onSelect = { type -> navigator.navigate(OperationEditRoute(EditType.CREATE, type)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                .padding(16.dp),
        )
    }

    if (showFilter) {
        FilterDialog(initial = filter, onApply = { viewModel.filter.value = it }, onDismiss = { showFilter = false })
    }
}

@Composable
private fun OperationRow(operation: Operation, onClick: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    val locale = appLocale
    val type = operation.typeEnum
    val title = if (type == OperationType.TRANSFER) operation.addAccount.target?.name else operation.category.target?.name
    SwipeableListItem(
        onEdit = onCopy,
        onDelete = onDelete,
        primaryIcon = Icons.Filled.ContentCopy,
        primaryContentDescription = stringResource(R.string.copy),
        onTap = onClick,
    ) {
        ListItem(
            headlineContent = { Text(title.orEmpty()) },
            // One Text instead of a Column: ListItem picks the three-line height from the
            // supporting content's first/last baselines, and a Column's merged baselines can go
            // stale when LazyColumn reuses a row, leaving an empty third line.
            supportingContent = {
            val note = operation.note?.takeIf { it.isNotEmpty() }
            val noteStyle = MaterialTheme.typography.bodySmall.toSpanStyle().copy(color = MaterialTheme.colorScheme.outline)
            Text(
                buildAnnotatedString {
                    append(operation.account.target?.name.orEmpty())
                    if (note != null) {
                        append('\n')
                        withStyle(noteStyle) { append(note) }
                    }
                }
            )
        },
            leadingContent = { IconChip(color = type.color, text = Formats.dayOfMonth(operation.date)) },
            trailingContent = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(
                    Formats.money(operation.displayTotal(), operation.displaySymbol(), locale),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                )
                SyncedIcon(operation.synced)
            }
        },
        )
    }
}

/**
 * Speed-dial FAB with one action per operation type (replaces the iOS "+" menu). Built
 * from stable components; the expressive FloatingActionButtonMenu is still experimental.
 */
@Composable
private fun AddOperationFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (OperationType) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "fabRotation")

    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OperationType.entries.reversed().forEach { type ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = 2.dp,
                        ) {
                            Text(
                                stringResource(type.title),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                onExpandedChange(false)
                                onSelect(type)
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(end = 4.dp),
                        ) { Icon(type.icon, contentDescription = stringResource(type.title), tint = type.color) }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { onExpandedChange(!expanded) }) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add), modifier = Modifier.rotate(rotation))
        }
    }
}
