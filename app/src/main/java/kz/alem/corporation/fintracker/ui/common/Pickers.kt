package kz.alem.corporation.fintracker.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.data.model.Account
import kz.alem.corporation.fintracker.data.model.Category
import kz.alem.corporation.fintracker.data.model.CategoryGroup
import kz.alem.corporation.fintracker.data.model.Currency
import kz.alem.corporation.fintracker.data.model.OperationType

data class PickerGroup<T>(val title: String?, val items: List<T>)

/**
 * Selection sheet shared by all entity pickers. Single mode selects on tap and closes;
 * multi mode collects a selection and returns an empty list when everything is checked
 * ("all", like iOS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectionSheet(
    title: String,
    groups: List<PickerGroup<T>>,
    itemId: (T) -> Long,
    headline: @Composable (T) -> String,
    onDismiss: () -> Unit,
    leading: (@Composable (T) -> Unit)? = null,
    supporting: ((T) -> String?)? = null,
    multi: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onSelect: (T) -> Unit = {},
    onChoose: (List<T>) -> Unit = {},
    onAdd: (() -> Unit)? = null,
) {
    var selection by remember { mutableStateOf(selectedIds) }
    val allItems = groups.flatMap { it.items }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (multi) {
                TextButton(onClick = { selection = emptySet() }) { Text(stringResource(R.string.reset)) }
                TextButton(onClick = {
                    val chosen = allItems.filter { itemId(it) in selection }
                    onChoose(if (chosen.size == allItems.size) emptyList() else chosen)
                    onDismiss()
                }) { Text(stringResource(R.string.choose)) }
            } else if (onAdd != null) {
                IconButton(onClick = {
                    onDismiss()
                    onAdd()
                }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add)) }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxHeight(0.85f).navigationBarsPadding()) {
            groups.forEachIndexed { index, group ->
                if (group.title != null) {
                    item(key = "header-$index") { SectionHeader(group.title) }
                }
                items(group.items, key = { itemId(it) }) { item ->
                    val id = itemId(item)
                    val checked = id in selection
                    ListItem(
                        headlineContent = { Text(headline(item)) },
                        supportingContent = supporting?.invoke(item)?.let { { Text(it) } },
                        leadingContent = leading?.let { { it(item) } },
                        trailingContent = {
                            // Always composed (hidden by alpha) so rows keep their height.
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.alpha(if (checked) 1f else 0f),
                            )
                        },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            if (multi) {
                                selection = if (checked) selection - id else selection + id
                            } else {
                                onSelect(item)
                                onDismiss()
                            }
                        },
                    )
                }
            }
        }
    }
}

// region Entity pickers

@Composable
fun AccountPickerSheet(
    onDismiss: () -> Unit,
    exceptId: Long? = null,
    selectedIds: Set<Long> = emptySet(),
    multi: Boolean = false,
    onSelect: (Account) -> Unit = {},
    onChoose: (List<Account>) -> Unit = {},
    onAdd: (() -> Unit)? = null,
) {
    val store = LocalAppContainer.current.store
    val locale = appLocale
    val totalLabel = stringResource(R.string.total)
    val groups by produceState(emptyList<PickerGroup<Account>>()) {
        value = withContext(Dispatchers.IO) {
            store.readAccounts(onlyVisible = true, exceptId = exceptId)
                .groupBy { it.currency.targetId }
                .values
                .map { accounts -> accounts.first().currency.target to accounts }
                .sortedBy { it.first?.name.orEmpty() }
                .map { (currency, accounts) ->
                    val total = accounts.filter { it.inSummary }.sumOf { it.balance }
                    PickerGroup("$totalLabel: ${Formats.money(total, currency?.symbol.orEmpty(), locale)}", accounts)
                }
        }
    }
    SelectionSheet(
        title = stringResource(if (multi) R.string.chooseAccounts else R.string.chooseAccount),
        groups = groups,
        itemId = { it.id },
        headline = { it.name },
        supporting = { Formats.money(it.balance, it.currency.target?.symbol.orEmpty(), locale) },
        leading = { IconChip(color = Color(it.color), icon = it.iconEnum.imageVector, isDefault = it.useAsDefault) },
        onDismiss = onDismiss,
        multi = multi,
        selectedIds = selectedIds,
        onSelect = onSelect,
        onChoose = onChoose,
        onAdd = onAdd,
    )
}

@Composable
fun CategoryPickerSheet(
    onDismiss: () -> Unit,
    operationType: OperationType? = null,
    selectedIds: Set<Long> = emptySet(),
    multi: Boolean = false,
    onSelect: (Category) -> Unit = {},
    onChoose: (List<Category>) -> Unit = {},
    onAdd: (() -> Unit)? = null,
) {
    val store = LocalAppContainer.current.store
    val groups by produceState(emptyList<PickerGroup<Category>>()) {
        value = withContext(Dispatchers.IO) {
            store.readActiveCategoriesByOperationType(operationType)
                .groupBy { it.categoryGroup.targetId }
                .values
                .map { categories -> categories.first().categoryGroup.target to categories }
                .sortedBy { it.first?.name.orEmpty() }
                .map { (group, categories) -> PickerGroup(group?.name.orEmpty(), categories) }
        }
    }
    SelectionSheet(
        title = stringResource(if (multi) R.string.chooseCategories else R.string.chooseCategory),
        groups = groups,
        itemId = { it.id },
        headline = { it.name },
        leading = { IconChip(color = Color(it.color), icon = it.iconEnum.imageVector, isDefault = it.useAsDefault) },
        onDismiss = onDismiss,
        multi = multi,
        selectedIds = selectedIds,
        onSelect = onSelect,
        onChoose = onChoose,
        onAdd = onAdd,
    )
}

@Composable
fun CategoryGroupPickerSheet(
    onDismiss: () -> Unit,
    selectedIds: Set<Long> = emptySet(),
    multi: Boolean = false,
    onSelect: (CategoryGroup) -> Unit = {},
    onChoose: (List<CategoryGroup>) -> Unit = {},
    onAdd: (() -> Unit)? = null,
) {
    val store = LocalAppContainer.current.store
    val groups by produceState(emptyList<PickerGroup<CategoryGroup>>()) {
        value = listOf(PickerGroup(null, withContext(Dispatchers.IO) { store.readCategoryGroups() }))
    }
    SelectionSheet(
        title = stringResource(if (multi) R.string.chooseGroups else R.string.chooseGroup),
        groups = groups,
        itemId = { it.id },
        headline = { it.name },
        leading = { IconChip(color = Color(it.color), icon = it.iconEnum.imageVector, isDefault = it.useAsDefault) },
        onDismiss = onDismiss,
        multi = multi,
        selectedIds = selectedIds,
        onSelect = onSelect,
        onChoose = onChoose,
        onAdd = onAdd,
    )
}

@Composable
fun CurrencyPickerSheet(
    onDismiss: () -> Unit,
    selectedIds: Set<Long> = emptySet(),
    multi: Boolean = false,
    onSelect: (Currency) -> Unit = {},
    onChoose: (List<Currency>) -> Unit = {},
    onAdd: (() -> Unit)? = null,
) {
    val store = LocalAppContainer.current.store
    val groups by produceState(emptyList<PickerGroup<Currency>>()) {
        value = listOf(PickerGroup(null, withContext(Dispatchers.IO) { store.readCurrencies() }))
    }
    SelectionSheet(
        title = stringResource(if (multi) R.string.chooseCurrencies else R.string.chooseCurrency),
        groups = groups,
        itemId = { it.id },
        headline = { it.name },
        supporting = { it.code },
        leading = { IconChip(color = Color(it.color), text = it.symbol, isDefault = it.useAsDefault) },
        onDismiss = onDismiss,
        multi = multi,
        selectedIds = selectedIds,
        onSelect = onSelect,
        onChoose = onChoose,
        onAdd = onAdd,
    )
}

@Composable
fun OperationTypePickerSheet(
    onDismiss: () -> Unit,
    types: List<OperationType>,
    selected: List<OperationType>,
    onChoose: (List<OperationType>) -> Unit,
) {
    SelectionSheet(
        title = stringResource(R.string.chooseOperationTypes),
        groups = listOf(PickerGroup(null, types)),
        itemId = { it.ordinal.toLong() },
        headline = { stringResource(it.title) },
        leading = { Icon(it.icon, contentDescription = null, tint = it.color) },
        onDismiss = onDismiss,
        multi = true,
        selectedIds = selected.map { it.ordinal.toLong() }.toSet(),
        onChoose = onChoose,
    )
}

// endregion
