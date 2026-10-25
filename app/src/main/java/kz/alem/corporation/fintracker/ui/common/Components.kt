package kz.alem.corporation.fintracker.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.unit.sp
import kz.alem.corporation.fintracker.R

/** Colored circle with a white icon or text; a star marks the default record. */
@Composable
fun IconChip(
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String? = null,
    isDefault: Boolean = false,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
            } else if (text != null) {
                Text(
                    text,
                    color = Color.White,
                    fontSize = (size.value * 0.38f).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (isDefault) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = Color(0xFFFFA000),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(size * 0.4f),
            )
        }
    }
}

/** Sticky list header, aligned with ListItem headlines (16 + 40 + 16 dp). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.padding(start = 72.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (trailing != null) {
                    Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            HorizontalDivider()
        }
    }
}

/** Per-row sync status, visible only to subscribers (like iOS). */
@Composable
fun SyncedIcon(synced: Boolean) {
    val container = LocalAppContainer.current
    val hasSubscription by container.subscriptionManager.hasSubscription.collectAsState()
    if (!hasSubscription) return
    Icon(
        if (synced) Icons.Filled.CloudDone else Icons.Outlined.CloudOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(16.dp),
    )
}

@Composable
fun ItemDivider() = HorizontalDivider(modifier = Modifier.padding(start = 72.dp))

// region Forms

/**
 * Grouped settings-style card (Android 15/16 Settings): rows sit on a surfaceBright card
 * over the surfaceContainer screen background, with a primary-colored group title.
 * Use inside [FormScaffold] / [EditScaffold] so the background matches.
 */
@Composable
fun FormSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceBright,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
    }
}

/** Background of screens built from [FormSection]s. */
val formBackground: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainer

/**
 * Screen scaffold for settings-style screens. Uses the same pinned top app bar as the list
 * tabs (see [AppTopBar]) so every header behaves alike.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScaffold(
    title: String,
    showBack: Boolean = true,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        containerColor = formBackground,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        topBar = {
            AppTopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = { if (showBack) BackButton() },
                actions = actions,
                containerColor = formBackground,
            )
        },
        content = content,
    )
}

/**
 * The app's standard small top app bar: pinned, tinted with the scrolled container color once
 * content scrolls beneath it. Pair with `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`
 * on the screen's Scaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

private val formRowColors @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** Label on the left, value on the right; opens something on tap. */
@Composable
fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueWeight: FontWeight? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(label, color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified) },
        supportingContent = if (isError && errorText != null) {
            { Text(errorText, color = MaterialTheme.colorScheme.error) }
        } else null,
        trailingContent = {
            Text(
                value,
                color = valueColor,
                fontWeight = valueWeight,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        },
        colors = formRowColors,
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier
        ),
    )
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        colors = formRowColors,
        modifier = modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
    )
}

/** Divider between rows of a form card, inset to line up with the row labels. */
@Composable
fun InsetDivider() = HorizontalDivider(Modifier.padding(start = 16.dp))

/** Checkbox setting row: the whole row toggles, the checkbox trails like a switch would. */
@Composable
fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = formRowColors,
        modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
    )
}

/** Single choice inside a form: Material 3 exposed dropdown menu (read-only field + menu). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderless: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = if (borderless) modifier else modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        FormTextField(
            value = optionLabel(selected),
            onValueChange = {},
            borderless = borderless,
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            // Spacing is on the box above, so the menu lines up with the field.
            padded = false,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/**
 * Settings list preference, as in the system Settings app: the row shows the current
 * value; tapping opens a dialog of radio buttons that applies the choice immediately.
 */
@Composable
fun <T> ListPreferenceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(optionLabel(selected)) },
        colors = formRowColors,
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(Modifier.selectableGroup()) {
                    options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = option == selected, role = Role.RadioButton) {
                                    open = false
                                    onSelect(option)
                                }
                                .padding(vertical = 12.dp),
                        ) {
                            RadioButton(selected = option == selected, onClick = null)
                            Text(optionLabel(option), modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * Divider between rows of a [FormSection], starting at the row titles. Rows with a leading
 * icon (e.g. settings menu rows) pass [MenuRowTitleInset].
 */
@Composable
fun FormDivider(startInset: Dp = 16.dp) = HorizontalDivider(Modifier.padding(start = startInset))

/** Title start of a ListItem with a 24dp leading icon: 16dp padding + 24dp icon + 16dp gap. */
val MenuRowTitleInset = 56.dp

// endregion

// region Edit screens

/**
 * Editor layout (from the previous Android app): the record's color fills the top third
 * behind a transparent top bar (back + save) and a centered [header] (usually its icon);
 * the form follows as white cards ([EditorCard]) on the Settings background. Leaving
 * with unsaved changes asks to discard them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScaffold(
    title: String,
    isModified: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    headerColor: Color = MaterialTheme.colorScheme.primary,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val requestClose = rememberDiscardGuard(isModified, onClose)
    val onHeader = if (headerColor.luminance() > 0.5f) Color.Black else Color.White

    Box(
        Modifier
            .fillMaxSize()
            .background(formBackground),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.33f)
                .background(headerColor),
        )
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = requestClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = onSave) { Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = onHeader,
                        navigationIconContentColor = onHeader,
                        actionIconContentColor = onHeader,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (header != null) {
                    // Equal 24dp gaps above (16 + 8) and below (12 + 12), centering the
                    // header between the top bar and the first card.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = header,
                    )
                }
                content()
            }
        }
    }
}

/**
 * White card holding part of an editor form. [padded] adds vertical room for outlined
 * fields; leave it off for full-width rows (switches, color) so their ripple reaches the edges.
 */
@Composable
fun EditorCard(padded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    ) {
        Column(Modifier.padding(vertical = if (padded) 8.dp else 0.dp), content = content)
    }
}

/**
 * Asks to discard unsaved changes before leaving an editor (close button or system Back).
 * Returns the action the close button should call.
 */
@Composable
fun rememberDiscardGuard(isModified: Boolean, onClose: () -> Unit): () -> Unit {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestClose = { if (isModified) confirmDiscard = true else onClose() }
    BackHandler(onBack = requestClose)
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            text = { Text(stringResource(R.string.discardChanges) + "?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(stringResource(R.string.discardChanges), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keepEditing)) }
            },
        )
    }
    return requestClose
}

// endregion

// region Dialogs

@Composable
fun ConfirmDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.confirmRemoving)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun MessageDialog(title: String?, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}

/** Centered placeholder for empty lists (iOS ContentUnavailableView). */
@Composable
fun EmptyState(icon: ImageVector, title: String, description: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp), textAlign = TextAlign.Center)
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// endregion

/** Primary "create" action of a list screen. */
@Composable
fun AddFab(onClick: () -> Unit) {
    androidx.compose.material3.FloatingActionButton(onClick = onClick) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add))
    }
}

/** Bottom list padding so the last row can scroll clear of the FAB. */
val FabClearance = PaddingValues(bottom = 88.dp)
