package kz.alem.corporation.fintracker.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kz.alem.corporation.fintracker.R
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Switch rows reordered by dragging the handle (like the language list in system
 * Settings). Used by import column order. [header] items are shown above and can't be moved.
 */
@Composable
fun <T> ReorderableSwitchList(
    items: MutableList<T>,
    key: (T) -> Any,
    label: @Composable (T) -> String,
    checked: (T) -> Boolean,
    onCheckedChange: (index: Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onMoved: () -> Unit = {},
    header: (LazyListScope.() -> Unit)? = null,
) {
    ReorderableColumn(items, key, modifier.fillMaxSize(), MaterialTheme.colorScheme.surface, onMoved, header) { index, item ->
        ListItem(
            headlineContent = { Text(label(item)) },
            leadingContent = {
                IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                    Icon(Icons.Filled.DragHandle, contentDescription = stringResource(R.string.reorder))
                }
            },
            trailingContent = { Switch(checked = checked(item), onCheckedChange = { onCheckedChange(index, it) }) },
        )
    }
}

/**
 * Compact checkbox rows (checkbox, title, drag handle) for use inside an AlertDialog.
 * The list stretches over the dialog's [dialogPadding] so row ripples span the whole
 * dialog width, while the row content stays aligned with the title. Used by report measures.
 */
@Composable
fun <T> ReorderableCheckboxList(
    items: MutableList<T>,
    key: (T) -> Any,
    label: @Composable (T) -> String,
    checked: (T) -> Boolean,
    onCheckedChange: (index: Int, Boolean) -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    dialogPadding: Dp = 24.dp,
) {
    ReorderableColumn(items, key, modifier.fillMaxWidth().bleedHorizontally(dialogPadding), containerColor) { index, item ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = checked(item), role = Role.Checkbox) { onCheckedChange(index, it) }
                .padding(horizontal = dialogPadding),
        ) {
            // No callback: the whole row toggles, and the checkbox skips its 48dp touch padding.
            Checkbox(checked = checked(item), onCheckedChange = null)
            Text(label(item), modifier = Modifier.weight(1f).padding(horizontal = 16.dp))
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.draggableHandle().heightIn(min = 48.dp).padding(start = 16.dp),
            ) {
                Icon(Icons.Filled.DragHandle, contentDescription = stringResource(R.string.reorder))
            }
        }
    }
}

/** Lays the content out [bleed] wider on each side, over the parent's padding. */
private fun Modifier.bleedHorizontally(bleed: Dp) = layout { measurable, constraints ->
    val extra = (bleed * 2).roundToPx()
    if (!constraints.hasBoundedWidth) return@layout measurable.measure(constraints).let { layout(it.width, it.height) { it.place(0, 0) } }
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra / 2, 0) }
}

@Composable
private fun <T> ReorderableColumn(
    items: MutableList<T>,
    key: (T) -> Any,
    modifier: Modifier,
    containerColor: Color,
    onMoved: () -> Unit = {},
    header: (LazyListScope.() -> Unit)? = null,
    row: @Composable ReorderableCollectionItemScope.(index: Int, item: T) -> Unit,
) {
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = items.indexOfFirst { key(it) == from.key }
        val toIndex = items.indexOfFirst { key(it) == to.key }
        // Header items aren't in the list, so drops onto them are ignored.
        if (fromIndex >= 0 && toIndex >= 0) {
            items.add(toIndex, items.removeAt(fromIndex))
            onMoved()
        }
    }
    LazyColumn(state = listState, modifier = modifier) {
        header?.invoke(this)
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            ReorderableItem(reorderState, key = key(item)) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragElevation")
                Surface(color = containerColor, shadowElevation = elevation) {
                    row(index, item)
                }
            }
        }
    }
}
