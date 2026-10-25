package kz.alem.corporation.fintracker.ui.database

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kz.alem.corporation.fintracker.AppContainer
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.data.ImportColumn
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.data.model.OperationsFilter
import kz.alem.corporation.fintracker.ui.common.DropdownField
import kz.alem.corporation.fintracker.ui.common.FilterForm
import kz.alem.corporation.fintracker.ui.common.FormDivider
import kz.alem.corporation.fintracker.ui.common.FormScaffold
import kz.alem.corporation.fintracker.ui.common.FormSection
import kz.alem.corporation.fintracker.ui.common.FullScreenDialog
import kz.alem.corporation.fintracker.ui.common.LocalAppContainer
import kz.alem.corporation.fintracker.ui.common.MessageDialog
import kz.alem.corporation.fintracker.ui.common.SwitchRow
import kz.alem.corporation.fintracker.ui.common.ValueRow
import kz.alem.corporation.fintracker.ui.common.appViewModel
import kz.alem.corporation.fintracker.ui.common.shareCsv
import kz.alem.corporation.fintracker.ui.navigation.ExportRoute
import kz.alem.corporation.fintracker.ui.navigation.ImportRoute
import kz.alem.corporation.fintracker.ui.navigation.LocalNavigator
import kz.alem.corporation.fintracker.ui.common.ReorderableSwitchList

// region Database menu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen() {
    val navigator = LocalNavigator.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var resetDone by rememberSaveable { mutableStateOf(false) }

    FormScaffold(stringResource(R.string.database)) { padding ->
        Column(Modifier.padding(padding)) {
            FormSection {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.importData)) },
                    supportingContent = { Text(stringResource(R.string.importDataFromCSV)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { navigator.navigate(ImportRoute) },
                )
                FormDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.exportData)) },
                    supportingContent = { Text(stringResource(R.string.exportDataToCSV)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { navigator.navigate(ExportRoute) },
                )
                FormDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.resetData)) },
                    supportingContent = { Text(stringResource(R.string.clearAllData)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { confirmReset = true },
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = { Text(stringResource(R.string.confirmRemovingAll)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        withContext(Dispatchers.IO) { DatabaseService(container).clearAll() }
                        resetDone = true
                    }
                }) { Text(stringResource(R.string.yes), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (resetDone) MessageDialog(null, stringResource(R.string.allDataSuccessfullyRemoved)) { resetDone = false }
}

// endregion

// region Import

class ImportViewModel(private val container: AppContainer) : ViewModel() {
    private val service = DatabaseService(container)

    var separator by mutableStateOf(Separator.COMMA)
    var firstRow by mutableStateOf("2")
    var columns by mutableStateOf(container.prefs.importColumns ?: ImportColumns.allOperations)
    var typeNames by mutableStateOf(
        mapOf(OperationType.EXPENSE to "Expense", OperationType.INCOME to "Income", OperationType.TRANSFER to "Transfer")
    )
    var deleteOldData by mutableStateOf(false)
    var file by mutableStateOf<Uri?>(null)
    var fileName by mutableStateOf<String?>(null)

    var rows by mutableStateOf<List<List<String>>>(emptyList())
        private set
    var isImporting by mutableStateOf(false)
        private set
    var imported by mutableStateOf(false)
    var readError by mutableStateOf(false)

    val enabledColumns: List<ImportColumn> get() = columns.filter { it.isEnabled }

    fun saveColumns(value: List<ImportColumn>) {
        columns = value
        container.prefs.importColumns = value
    }

    fun loadPreview() = viewModelScope.launch {
        val uri = file ?: return@launch
        rows = withContext(Dispatchers.IO) {
            runCatching {
                container.context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()?.let { CsvParser.parse(it.removePrefix("\uFEFF"), separator, firstRow.toIntOrNull() ?: 2) }
        } ?: run {
            readError = true
            emptyList()
        }
    }

    fun import() = viewModelScope.launch {
        isImporting = true
        withContext(Dispatchers.IO) { service.importData(rows, deleteOldData, enabledColumns, typeNames) }
        isImporting = false
        imported = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen() {
    val viewModel = appViewModel { container, _ -> ImportViewModel(container) }
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.file = uri
            viewModel.fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }
    }

    FormScaffold(stringResource(R.string.importData)) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            FormSection {
                DropdownField(
                    label = stringResource(R.string.delimiter),
                    options = Separator.entries,
                    selected = viewModel.separator,
                    optionLabel = { it.char.toString() },
                    onSelect = { viewModel.separator = it },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.initialRow)) },
                    trailingContent = {
                        OutlinedTextField(
                            value = viewModel.firstRow,
                            onValueChange = { value -> viewModel.firstRow = value.filter { it.isDigit() }.take(4) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(88.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                FormDivider()
                ValueRow(stringResource(R.string.fieldOrder), "", onClick = { dialog = "columns" })
                FormDivider()
                ValueRow(stringResource(R.string.settingOperationTypes), "", onClick = { dialog = "types" })
                FormDivider()
                SwitchRow(stringResource(R.string.deleteOldData), viewModel.deleteOldData, { viewModel.deleteOldData = it })
                FormDivider()
                ValueRow(
                    stringResource(R.string.fileCSV),
                    viewModel.fileName ?: stringResource(R.string.choose).lowercase(),
                    onClick = { launcher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) },
                )
            }
            Button(
                onClick = {
                    viewModel.loadPreview()
                    dialog = "preview"
                },
                enabled = viewModel.file != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) { Text(stringResource(R.string.preview)) }
        }
    }

    when (dialog) {
        "columns" -> ColumnsDialog(viewModel.columns, onApply = viewModel::saveColumns, onDismiss = { dialog = null })
        "types" -> TypeNamesDialog(viewModel.typeNames, onApply = { viewModel.typeNames = it }, onDismiss = { dialog = null })
        "preview" -> PreviewDialog(viewModel, onDismiss = { dialog = null })
    }
    if (viewModel.imported) {
        MessageDialog(null, stringResource(R.string.dataLoadedSuccessfully)) {
            viewModel.imported = false
            dialog = null
            navigator.back()
        }
    }
    if (viewModel.readError) {
        MessageDialog(stringResource(R.string.error), stringResource(R.string.readingFileErrorChooseFile)) {
            viewModel.readError = false
            dialog = null
        }
    }
}

/** Column mapping with templates (iOS DataColumnsScreen). */
@Composable
private fun ColumnsDialog(initial: List<ImportColumn>, onApply: (List<ImportColumn>) -> Unit, onDismiss: () -> Unit) {
    val columns = remember { mutableStateListOf(*initial.toTypedArray()) }
    var template by remember {
        mutableStateOf(if (initial == ImportColumns.allOperations) ImportTemplate.ALL_OPERATIONS else ImportTemplate.LAST_USED)
    }
    val lastUsed = LocalAppContainer.current.prefs.importColumns
    FullScreenDialog(
        title = stringResource(R.string.fieldSetting),
        confirmLabel = stringResource(R.string.apply),
        onConfirm = {
            onApply(columns.toList())
            onDismiss()
        },
        onDismiss = onDismiss,
        scrollable = false,
    ) {
        ReorderableSwitchList(
            items = columns,
            key = { it.id },
            label = { stringResource(ImportColumns.title(it.name)) },
            checked = { it.isEnabled },
            onCheckedChange = { index, value ->
                columns[index] = columns[index].copy(isEnabled = value)
                template = ImportTemplate.LAST_USED
            },
            onMoved = { template = ImportTemplate.LAST_USED },
            header = {
                item(key = "template") {
                    FormSection {
                        DropdownField(
                            label = stringResource(R.string.template),
                            options = ImportTemplate.entries,
                            selected = template,
                            optionLabel = { stringResource(it.title) },
                            onSelect = { selected ->
                                template = selected
                                val replacement = when (selected) {
                                    ImportTemplate.ALL_OPERATIONS -> ImportColumns.allOperations
                                    ImportTemplate.EXPENSES_INCOMES -> ImportColumns.expensesAndIncomes
                                    ImportTemplate.TRANSFERS -> ImportColumns.transfers
                                    ImportTemplate.LAST_USED -> lastUsed
                                }
                                if (replacement != null) {
                                    columns.clear()
                                    columns.addAll(replacement)
                                }
                            },
                        )
                    }
                }
            },
        )
    }
}

/** The names the file uses for each operation type. */
@Composable
private fun TypeNamesDialog(initial: Map<OperationType, String>, onApply: (Map<OperationType, String>) -> Unit, onDismiss: () -> Unit) {
    var names by remember { mutableStateOf(initial) }
    FullScreenDialog(
        title = stringResource(R.string.settingOperationTypes),
        confirmLabel = stringResource(R.string.apply),
        onConfirm = {
            onApply(names)
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        FormSection(footer = stringResource(R.string.settingOperationTypesDescription)) {
            OperationType.entries.forEach { type ->
                OutlinedTextField(
                    value = names[type].orEmpty(),
                    onValueChange = { names = names + (type to it) },
                    label = { Text(stringResource(type.title)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** First rows of the parsed file under the chosen columns, then the import itself. */
@Composable
private fun PreviewDialog(viewModel: ImportViewModel, onDismiss: () -> Unit) {
    val columns = viewModel.enabledColumns
    FullScreenDialog(
        title = stringResource(R.string.preview),
        confirmLabel = stringResource(R.string.importData),
        onConfirm = { if (viewModel.rows.isNotEmpty() && !viewModel.isImporting) viewModel.import() },
        onDismiss = onDismiss,
    ) {
        Text(
            stringResource(R.string.previewSlicedInfo, PREVIEW_ROWS, viewModel.rows.size),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        )
        if (viewModel.isImporting) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
            ) {
                Row {
                    columns.forEach { column ->
                        Text(stringResource(ImportColumns.title(column.name)), fontWeight = FontWeight.Bold, modifier = Modifier.width(CELL_WIDTH).padding(4.dp))
                    }
                }
                HorizontalDivider()
                viewModel.rows.take(PREVIEW_ROWS).forEach { row ->
                    Row {
                        columns.indices.forEach { i ->
                            Text(row.getOrNull(i).orEmpty(), maxLines = 2, modifier = Modifier.width(CELL_WIDTH).padding(4.dp))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private const val PREVIEW_ROWS = 20
private val CELL_WIDTH = 120.dp

// endregion

// region Export

class ExportViewModel(private val container: AppContainer) : ViewModel() {
    var separator by mutableStateOf(Separator.COMMA)
    var filter by mutableStateOf(OperationsFilter())

    fun export(onReady: (String) -> Unit) = viewModelScope.launch {
        val csv = withContext(Dispatchers.IO) {
            DatabaseService(container).exportCsv(container.context, container.store.readOperations(filter), separator)
        }
        onReady(csv)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen() {
    val viewModel = appViewModel { container, _ -> ExportViewModel(container) }
    val context = LocalContext.current

    FormScaffold(stringResource(R.string.exportData)) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            FormSection {
                DropdownField(
                    label = stringResource(R.string.delimiter),
                    options = Separator.entries,
                    selected = viewModel.separator,
                    optionLabel = { it.char.toString() },
                    onSelect = { viewModel.separator = it },
                    borderless = true,
                )
            }
            FilterForm(filter = viewModel.filter, onChange = { viewModel.filter = it }, showContainType = false)
            Button(
                onClick = { viewModel.export { csv -> shareCsv(context, "operations.csv", csv) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) { Text(stringResource(R.string.export)) }
        }
    }
}

// endregion
