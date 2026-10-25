package kz.alem.corporation.fintracker.ui.categories

import kz.alem.corporation.fintracker.ui.common.ShakeBox
import kz.alem.corporation.fintracker.ui.common.InsetDivider
import kz.alem.corporation.fintracker.ui.common.CheckboxRow
import androidx.compose.material3.ListItem
import kz.alem.corporation.fintracker.ui.common.SwipeableListItem
import kz.alem.corporation.fintracker.ui.common.PickerField
import kz.alem.corporation.fintracker.ui.common.EditorCard
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import kz.alem.corporation.fintracker.data.model.Category
import kz.alem.corporation.fintracker.data.model.CategoryGroup
import kz.alem.corporation.fintracker.data.model.CategoryGroupIcons
import kz.alem.corporation.fintracker.data.model.CategoryIcons
import kz.alem.corporation.fintracker.data.model.CategoryType
import kz.alem.corporation.fintracker.data.model.DEFAULT_COLOR
import kz.alem.corporation.fintracker.data.model.DatabaseError
import kz.alem.corporation.fintracker.data.model.DatabaseException
import kz.alem.corporation.fintracker.data.model.EditForm
import kz.alem.corporation.fintracker.data.model.EditType
import kz.alem.corporation.fintracker.data.model.OperationsFilter
import kz.alem.corporation.fintracker.data.model.title
import kz.alem.corporation.fintracker.ui.common.BackButton
import kz.alem.corporation.fintracker.ui.common.CategoryGroupPickerSheet
import kz.alem.corporation.fintracker.ui.common.ColorRow
import kz.alem.corporation.fintracker.ui.common.DropdownField
import kz.alem.corporation.fintracker.ui.common.EditScaffold
import kz.alem.corporation.fintracker.ui.common.IconChip
import kz.alem.corporation.fintracker.ui.common.IconPickerSheet
import kz.alem.corporation.fintracker.ui.common.ItemDivider
import kz.alem.corporation.fintracker.ui.common.LocalAppContainer
import kz.alem.corporation.fintracker.ui.common.MessageDialog
import kz.alem.corporation.fintracker.ui.common.RequiredField
import kz.alem.corporation.fintracker.ui.common.SearchField
import kz.alem.corporation.fintracker.ui.common.SectionHeader
import kz.alem.corporation.fintracker.ui.common.SyncedIcon
import kz.alem.corporation.fintracker.ui.common.AddFab
import kz.alem.corporation.fintracker.ui.common.FabClearance
import kz.alem.corporation.fintracker.ui.common.appViewModel
import kz.alem.corporation.fintracker.ui.common.color
import kz.alem.corporation.fintracker.ui.common.imageVector
import kz.alem.corporation.fintracker.ui.navigation.CategoryEditRoute
import kz.alem.corporation.fintracker.ui.navigation.CategoryGroupEditRoute
import kz.alem.corporation.fintracker.ui.navigation.CategoryGroupsRoute
import kz.alem.corporation.fintracker.ui.navigation.LocalNavigator
import kz.alem.corporation.fintracker.ui.navigation.NavResultEffect
import kz.alem.corporation.fintracker.ui.navigation.NavResults

// region Categories list

data class CategorySection(val group: CategoryGroup?, val categories: List<Category>)

class CategoriesViewModel(container: AppContainer) : ViewModel() {
    private val store = container.store
    val showAll = MutableStateFlow(false)
    val query = MutableStateFlow("")
    val error = MutableStateFlow<DatabaseError?>(null)

    val sections: StateFlow<List<CategorySection>> = combine(store.changes, showAll, query) { _, all, q ->
        val needle = q.lowercase()
        store.readCategories(onlyVisible = !all)
            .filter { needle.isEmpty() || it.name.lowercase().contains(needle) }
            .groupBy { it.categoryGroup.targetId }
            .map { (_, categories) -> CategorySection(categories.first().categoryGroup.target, categories) }
            .sortedBy { it.group?.name.orEmpty() }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        try {
            store.deleteCategory(id)
        } catch (e: DatabaseException) {
            error.value = e.error
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CategoriesScreen() {
    val viewModel = appViewModel { container, _ -> CategoriesViewModel(container) }
    val navigator = LocalNavigator.current
    val openFiltered by LocalAppContainer.current.prefs.openFilteredOperation.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val showAll by viewModel.showAll.collectAsState()
    val query by viewModel.query.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        floatingActionButton = { AddFab(onClick = { navigator.navigate(CategoryEditRoute(EditType.CREATE)) }) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { viewModel.showAll.value = !showAll }) {
                        Icon(
                            if (showAll) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (showAll) R.string.hideHidden else R.string.showHidden),
                        )
                    }
                    IconButton(onClick = { navigator.navigate(CategoryGroupsRoute) }) {
                        Icon(Icons.Filled.Folder, contentDescription = stringResource(R.string.categoryGroups))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding), contentPadding = FabClearance) {
            item { SearchField(query, { viewModel.query.value = it }) }
            sections.forEach { section ->
                stickyHeader(key = "group-${section.group?.id}") { SectionHeader(section.group?.name.orEmpty()) }
                items(section.categories, key = { it.id }) { category ->
                    SwipeableListItem(
                        onEdit = { navigator.navigate(CategoryEditRoute(EditType.UPDATE, category.id)) },
                        onDelete = { viewModel.delete(category.id) },
                        onTap = {
                            if (openFiltered) {
                                navigator.showOperations(OperationsFilter(categories = listOf(category)))
                            } else {
                                navigator.navigate(CategoryEditRoute(EditType.UPDATE, category.id))
                            }
                        },
                    ) {
                        ListItem(
                            headlineContent = { Text(category.name) },
                            leadingContent = {
                            IconChip(color = Color(category.color), icon = category.iconEnum.imageVector, isDefault = category.useAsDefault)
                        },
                            trailingContent = { SyncedIcon(category.synced) },
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

// region Category editor

class CategoryEditViewModel(container: AppContainer, val editType: EditType, id: Long, private val initialType: CategoryType) : ViewModel() {
    private val store = container.store
    private var original: Category? = null

    var group by mutableStateOf<CategoryGroup?>(null)
    var name by mutableStateOf("")
    var type by mutableStateOf(initialType)
    var color by mutableIntStateOf(DEFAULT_COLOR)
    var icon by mutableStateOf(CategoryIcons.basket)
    var useAsDefault by mutableStateOf(false)
    var visible by mutableStateOf(true)
    var hasError by mutableStateOf(false)

    /** Bumped on every failed save so the missing fields shake. */
    var shakeTrigger by mutableIntStateOf(0)
        private set

    init {
        viewModelScope.launch {
            if (id != 0L) {
                val category = withContext(Dispatchers.IO) { store.getCategory(id) } ?: return@launch
                original = category
                group = category.categoryGroup.target
                name = category.name
                type = category.typeEnum
                color = category.color
                icon = category.iconEnum
                useAsDefault = category.useAsDefault
                visible = category.visible
            } else {
                group = withContext(Dispatchers.IO) { store.getDefaultCategoryGroup() }
            }
        }
    }

    // The auto-filled default group is deliberately not counted as a change.
    val isModified: Boolean
        get() = original?.let {
            (group?.id ?: 0L) != it.categoryGroup.targetId || name != it.name || type != it.typeEnum ||
                icon != it.iconEnum || color != it.color || useAsDefault != it.useAsDefault || visible != it.visible
        } ?: (name.isNotEmpty() || type != initialType || icon != CategoryIcons.basket || color != DEFAULT_COLOR)

    fun onGroupCreated(id: Long) = viewModelScope.launch {
        group = withContext(Dispatchers.IO) { store.getCategoryGroup(id) }
    }

    fun save(onSaved: (Long) -> Unit) {
        hasError = group == null || name.isEmpty()
        if (hasError) {
            shakeTrigger++
            return
        }
        if (editType == EditType.UPDATE && original == null) return // still loading
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                if (useAsDefault) store.cleanCategoryDefaults()
                val target = if (editType == EditType.UPDATE) original ?: Category() else Category()
                target.categoryGroup.target = group
                target.name = name
                target.typeEnum = type
                target.color = color
                target.iconEnum = icon
                target.useAsDefault = useAsDefault
                target.visible = visible
                if (editType == EditType.UPDATE) store.updateCategory(target) else store.createCategory(target)
            }
            onSaved(id)
        }
    }
}

@Composable
fun CategoryEditScreen(route: CategoryEditRoute) {
    val viewModel = appViewModel { container, _ -> CategoryEditViewModel(container, route.editType, route.id, route.type) }
    val navigator = LocalNavigator.current
    var iconPicker by rememberSaveable { mutableStateOf(false) }
    var groupPicker by rememberSaveable { mutableStateOf(false) }

    NavResultEffect(NavResults.CREATED_CATEGORY_GROUP) { viewModel.onGroupCreated(it) }

    EditScaffold(
        title = stringResource(route.editType.title(EditForm.CATEGORY)),
        headerColor = Color(viewModel.color),
        isModified = viewModel.isModified,
        onClose = { navigator.back() },
        onSave = {
            viewModel.save { id ->
                if (route.editType != EditType.UPDATE) navigator.setResult(NavResults.CREATED_CATEGORY, id)
                navigator.back()
            }
        },
        header = {
            IconChip(color = Color(viewModel.color), icon = viewModel.icon.imageVector, size = 80.dp, onClick = { iconPicker = true })
        },
    ) {
        EditorCard(padded = false) {
            ShakeBox(viewModel.shakeTrigger, active = viewModel.name.isEmpty()) {
                RequiredField(viewModel.name, { viewModel.name = it }, stringResource(R.string.categoryNameExample),
                    error = null, borderless = true, isError = viewModel.hasError && viewModel.name.isEmpty())
            }
            InsetDivider()
            ShakeBox(viewModel.shakeTrigger, active = viewModel.group == null) {
                PickerField(
                    label = stringResource(R.string.categoryGroup),
                    value = viewModel.group?.name.orEmpty(),
                    onClick = { groupPicker = true },
                    onAdd = { navigator.navigate(CategoryGroupEditRoute(EditType.CREATE)) },
                    borderless = true,
                    isError = viewModel.hasError && viewModel.group == null,
                )
            }
            InsetDivider()
            DropdownField(
                label = stringResource(R.string.categoryType),
                options = CategoryType.entries,
                selected = viewModel.type,
                optionLabel = { stringResource(it.title) },
                onSelect = { viewModel.type = it },
                borderless = true,
            )
        }
        EditorCard(padded = false) {
            CheckboxRow(stringResource(R.string.useAsDefault), viewModel.useAsDefault, { viewModel.useAsDefault = it })
            InsetDivider()
            CheckboxRow(stringResource(R.string.visible), viewModel.visible, { viewModel.visible = it })
        }
        EditorCard(padded = false) { ColorRow(viewModel.color) { viewModel.color = it } }
    }

    if (iconPicker) {
        IconPickerSheet(CategoryIcons.entries, viewModel.icon, { it.imageVector }, { viewModel.icon = it }, { iconPicker = false })
    }
    if (groupPicker) {
        CategoryGroupPickerSheet(
            onDismiss = { groupPicker = false },
            selectedIds = setOfNotNull(viewModel.group?.id),
            onSelect = { viewModel.group = it },
            onAdd = { navigator.navigate(CategoryGroupEditRoute(EditType.CREATE)) },
        )
    }
}

// endregion

// region Category groups list

class CategoryGroupsViewModel(container: AppContainer) : ViewModel() {
    private val store = container.store
    val query = MutableStateFlow("")
    val error = MutableStateFlow<DatabaseError?>(null)

    val groups: StateFlow<List<CategoryGroup>> = combine(store.changes, query) { _, q ->
        val needle = q.lowercase()
        store.readCategoryGroups().filter { needle.isEmpty() || it.name.lowercase().contains(needle) }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        try {
            store.deleteCategoryGroup(id)
        } catch (e: DatabaseException) {
            error.value = e.error
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryGroupsScreen() {
    val viewModel = appViewModel { container, _ -> CategoryGroupsViewModel(container) }
    val navigator = LocalNavigator.current
    val openFiltered by LocalAppContainer.current.prefs.openFilteredOperation.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val query by viewModel.query.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        floatingActionButton = { AddFab(onClick = { navigator.navigate(CategoryGroupEditRoute(EditType.CREATE)) }) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categoryGroups)) },
                navigationIcon = { BackButton() },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding), contentPadding = FabClearance) {
            item { SearchField(query, { viewModel.query.value = it }) }
            items(groups, key = { it.id }) { group ->
                SwipeableListItem(
                    onEdit = { navigator.navigate(CategoryGroupEditRoute(EditType.UPDATE, group.id)) },
                    onDelete = { viewModel.delete(group.id) },
                    onTap = {
                        if (openFiltered) {
                            navigator.showOperations(OperationsFilter(categoryGroups = listOf(group)))
                        } else {
                            navigator.navigate(CategoryGroupEditRoute(EditType.UPDATE, group.id))
                        }
                    },
                ) {
                    ListItem(
                        headlineContent = { Text(group.name) },
                        leadingContent = { IconChip(color = Color(group.color), icon = group.iconEnum.imageVector, isDefault = group.useAsDefault) },
                        trailingContent = { SyncedIcon(group.synced) },
                    )
                }
                ItemDivider()
            }
        }
    }
    error?.let { MessageDialog(stringResource(R.string.error), stringResource(it.message)) { viewModel.error.value = null } }
}

// endregion

// region Category group editor

class CategoryGroupEditViewModel(container: AppContainer, val editType: EditType, id: Long) : ViewModel() {
    private val store = container.store
    private var original: CategoryGroup? = null

    var name by mutableStateOf("")
    var icon by mutableStateOf(CategoryGroupIcons.house)
    var color by mutableIntStateOf(DEFAULT_COLOR)
    var useAsDefault by mutableStateOf(false)
    var hasError by mutableStateOf(false)

    /** Bumped on every failed save so the missing fields shake. */
    var shakeTrigger by mutableIntStateOf(0)
        private set

    init {
        if (id != 0L) viewModelScope.launch {
            val group = withContext(Dispatchers.IO) { store.getCategoryGroup(id) } ?: return@launch
            original = group
            name = group.name
            icon = group.iconEnum
            color = group.color
            useAsDefault = group.useAsDefault
        }
    }

    val isModified: Boolean
        get() = original?.let {
            name != it.name || icon != it.iconEnum || color != it.color || useAsDefault != it.useAsDefault
        } ?: (name.isNotEmpty() || icon != CategoryGroupIcons.house || color != DEFAULT_COLOR || useAsDefault)

    fun save(onSaved: (Long) -> Unit) {
        hasError = name.isEmpty()
        if (hasError) {
            shakeTrigger++
            return
        }
        if (editType == EditType.UPDATE && original == null) return // still loading
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                if (useAsDefault) store.cleanCategoryGroupDefaults()
                val target = if (editType == EditType.UPDATE) original ?: CategoryGroup() else CategoryGroup()
                target.name = name
                target.iconEnum = icon
                target.color = color
                target.useAsDefault = useAsDefault
                if (editType == EditType.UPDATE) store.updateCategoryGroup(target) else store.createCategoryGroup(target)
            }
            onSaved(id)
        }
    }
}

@Composable
fun CategoryGroupEditScreen(route: CategoryGroupEditRoute) {
    val viewModel = appViewModel { container, _ -> CategoryGroupEditViewModel(container, route.editType, route.id) }
    val navigator = LocalNavigator.current
    var iconPicker by rememberSaveable { mutableStateOf(false) }

    EditScaffold(
        title = stringResource(route.editType.title(EditForm.CATEGORY_GROUP)),
        headerColor = Color(viewModel.color),
        isModified = viewModel.isModified,
        onClose = { navigator.back() },
        onSave = {
            viewModel.save { id ->
                if (route.editType != EditType.UPDATE) navigator.setResult(NavResults.CREATED_CATEGORY_GROUP, id)
                navigator.back()
            }
        },
        header = {
            IconChip(color = Color(viewModel.color), icon = viewModel.icon.imageVector, size = 80.dp, onClick = { iconPicker = true })
        },
    ) {
        EditorCard(padded = false) {
            ShakeBox(viewModel.shakeTrigger, active = viewModel.name.isEmpty()) {
                RequiredField(viewModel.name, { viewModel.name = it }, stringResource(R.string.categoryGroupNameExample),
                    error = null, borderless = true, isError = viewModel.hasError && viewModel.name.isEmpty())
            }
            InsetDivider()
            CheckboxRow(stringResource(R.string.useAsDefault), viewModel.useAsDefault, { viewModel.useAsDefault = it })
            InsetDivider()
            ColorRow(viewModel.color) { viewModel.color = it }
        }
    }

    if (iconPicker) {
        IconPickerSheet(CategoryGroupIcons.entries, viewModel.icon, { it.imageVector }, { viewModel.icon = it }, { iconPicker = false })
    }
}

// endregion
