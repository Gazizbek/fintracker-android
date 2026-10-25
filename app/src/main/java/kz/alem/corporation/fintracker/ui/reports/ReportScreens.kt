package kz.alem.corporation.fintracker.ui.reports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.ui.common.BackButton
import kz.alem.corporation.fintracker.ui.common.FilterDialog
import kz.alem.corporation.fintracker.ui.common.Formats
import kz.alem.corporation.fintracker.ui.common.ReorderableCheckboxList
import kz.alem.corporation.fintracker.ui.common.appLocale
import kz.alem.corporation.fintracker.ui.common.appViewModel
import kz.alem.corporation.fintracker.ui.common.shareCsv
import kz.alem.corporation.fintracker.ui.navigation.LocalNavigator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Top bar and dialogs shared by both report screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportScaffold(title: String, viewModel: ReportViewModel, content: @Composable (Modifier) -> Unit) {
    val context = LocalContext.current
    val locale = appLocale
    val filter by viewModel.filter.collectAsState()
    val measures by viewModel.measures.collectAsState()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(locale) { viewModel.setLocale(locale) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { dialog = "filter" }) { Icon(Icons.Filled.FilterList, stringResource(R.string.filter)) }
                    IconButton(onClick = { dialog = "measures" }) { Icon(Icons.Filled.Tune, stringResource(R.string.measures)) }
                    IconButton(onClick = { shareCsv(context, "operations.csv", viewModel.csv(context)) }) {
                        Icon(Icons.Filled.Share, stringResource(R.string.share))
                    }
                },
            )
        },
    ) { padding -> content(Modifier.padding(padding)) }

    when (dialog) {
        "filter" -> FilterDialog(
            initial = filter,
            onApply = { viewModel.filter.value = it },
            onDismiss = { dialog = null },
            excludeTransferType = true,
        )
        "measures" -> MeasuresDialog(measures, onApply = { viewModel.measures.value = it }, onDismiss = { dialog = null })
    }
}

// region Linear report

@Composable
fun LinearReportScreen() {
    val viewModel = appViewModel { container, _ -> ReportViewModel(container) }
    val navigator = LocalNavigator.current
    val state by viewModel.state.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val expanded = remember { mutableStateListOf<String>() }

    ReportScaffold(stringResource(R.string.linearReport), viewModel) { modifier ->
        if (state.isLoading) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = modifier) {
                reportRows(state.items, expanded) { item -> navigator.showOperations(item.drillDownFilter(filter)) }
            }
        }
    }
}

/** Expandable tree rows; tapping a leaf opens its operations. */
private fun LazyListScope.reportRows(items: List<ReportItem>, expanded: MutableList<String>, onLeaf: (ReportItem) -> Unit) {
    items.forEach { item ->
        item(key = item.id) {
            val isExpanded = item.id in expanded
            ReportRow(
                item = item,
                expandable = item.subItems.isNotEmpty(),
                isExpanded = isExpanded,
                modifier = Modifier.padding(start = (item.level * 16).dp),
                onClick = {
                    if (item.subItems.isEmpty()) onLeaf(item)
                    else if (isExpanded) expanded.removeAll { it == item.id || it.startsWith(item.id + "/") } else expanded.add(item.id)
                },
            )
        }
        if (item.id in expanded) reportRows(item.subItems, expanded, onLeaf)
    }
}

/** Row color and readable content color. Months cycle through the theme accent roles. */
@Composable
private fun reportColors(item: ReportItem): Pair<Color, Color> {
    item.color?.let { return Color(it) to Color.White }
    val scheme = MaterialTheme.colorScheme
    val month = (item.key as? ReportKey.MonthKey)?.key ?: 0
    return when (month % 3) {
        0 -> scheme.primary to scheme.onPrimary
        1 -> scheme.tertiary to scheme.onTertiary
        else -> scheme.secondary to scheme.onSecondary
    }
}

/** Report line as a standard list row: color dot, title, share and amount. */
@Composable
private fun ReportRow(item: ReportItem, expandable: Boolean, isExpanded: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val locale = appLocale
    val (color, _) = reportColors(item)
    ListItem(
        headlineContent = { Text(item.title) },
        supportingContent = if (item.level > 0) ({ Text("${Formats.number(item.percent, locale)}%") }) else null,
        leadingContent = { Box(Modifier.size(16.dp).background(color, CircleShape)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Formats.money(item.value, item.symbol, locale), style = MaterialTheme.typography.bodyLarge)
                if (expandable) {
                    Icon(
                        if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}

// endregion

// region Pie chart

@Composable
fun PieChartScreen() {
    val viewModel = appViewModel { container, _ -> ReportViewModel(container) }
    val navigator = LocalNavigator.current
    val locale = appLocale
    val state by viewModel.state.collectAsState()
    val filter by viewModel.filter.collectAsState()
    // Drill-down path; the chart shows the last item's children (or the top level).
    val history = remember { mutableStateListOf<ReportItem>() }
    LaunchedEffect(state.items) { history.clear() }
    val current = history.lastOrNull()?.subItems ?: state.items

    val onPress: (ReportItem) -> Unit = { item ->
        if (item.subItems.isNotEmpty()) history.add(item) else navigator.showOperations(item.drillDownFilter(filter))
    }

    ReportScaffold(stringResource(R.string.circleDiagram), viewModel) { modifier ->
        Column(modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
            ) {
                TextButton(onClick = { history.clear() }) { Text("${stringResource(R.string.path)}:") }
                history.forEachIndexed { index, item ->
                    Text("/", color = MaterialTheme.colorScheme.outline)
                    TextButton(onClick = { while (history.size > index + 1) history.removeAt(history.lastIndex) }) { Text(item.title) }
                }
            }
            HorizontalDivider()
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                DonutChart(
                    items = current,
                    onPress = onPress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(horizontal = 32.dp, vertical = 12.dp),
                )
                LazyColumn {
                    items(current, key = { it.id }) { item ->
                        ReportRow(item = item, expandable = item.subItems.isNotEmpty(), isExpanded = false, onClick = { onPress(item) })
                    }
                }
            }
        }
    }
}

/**
 * Donut chart drawn on a Canvas. Top-level (currency) and operation-type slices get equal
 * weight since their signed totals aren't comparable; others are sized by |value|.
 */
@Composable
private fun DonutChart(items: List<ReportItem>, onPress: (ReportItem) -> Unit, modifier: Modifier = Modifier) {
    val locale = appLocale
    val textMeasurer = rememberTextMeasurer()
    val weights = items.map { if (it.level == 0 || it.key is ReportKey.TypeKey) 1.0 else abs(it.value) }
    val total = weights.sum()
    val gapDegrees = if (items.size > 1) 1.5f else 0f
    val colors = items.map { reportColors(it) }

    Canvas(
        modifier = modifier.pointerInput(items) {
            detectTapGestures { tap ->
                if (total <= 0) return@detectTapGestures
                val center = Offset(size.width / 2f, size.height / 2f)
                val outer = minOf(size.width, size.height) / 2f
                val distance = hypot(tap.x - center.x, tap.y - center.y)
                if (distance > outer || distance < outer * 0.5f) return@detectTapGestures
                // Angle from 12 o'clock, clockwise.
                var angle = Math.toDegrees(atan2((tap.y - center.y).toDouble(), (tap.x - center.x).toDouble())) + 90
                if (angle < 0) angle += 360
                var start = 0.0
                items.forEachIndexed { index, item ->
                    val sweep = weights[index] / total * 360
                    if (angle >= start && angle < start + sweep) {
                        onPress(item)
                        return@detectTapGestures
                    }
                    start += sweep
                }
            }
        },
    ) {
        if (total <= 0) return@Canvas
        val diameter = minOf(size.width, size.height)
        val strokeWidth = diameter * 0.2f
        val radius = (diameter - strokeWidth) / 2f
        val topLeft = Offset((size.width - diameter) / 2f + strokeWidth / 2f, (size.height - diameter) / 2f + strokeWidth / 2f)
        val arcSize = Size(diameter - strokeWidth, diameter - strokeWidth)
        var start = -90f
        items.forEachIndexed { index, item ->
            val sweep = (weights[index] / total * 360).toFloat()
            drawArc(
                color = colors[index].first,
                startAngle = start + gapDegrees / 2,
                sweepAngle = (sweep - gapDegrees).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth),
            )
            // Label slices big enough to fit one.
            if (sweep > 25f) {
                val mid = Math.toRadians((start + sweep / 2).toDouble())
                val labelCenter = Offset(
                    (size.width / 2f + radius * cos(mid)).toFloat(),
                    (size.height / 2f + radius * sin(mid)).toFloat(),
                )
                val layout = textMeasurer.measure(
                    "${item.title}\n${Formats.number(item.value, locale)}",
                    style = TextStyle(fontSize = 10.sp, color = colors[index].second, textAlign = TextAlign.Center),
                    maxLines = 2,
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = strokeWidth.toInt() * 2),
                )
                drawText(layout, topLeft = labelCenter - Offset(layout.size.width / 2f, layout.size.height / 2f))
            }
            start += sweep
        }
    }
}

// endregion

// region Measures

/** Enable and drag-order report measures. */
@Composable
private fun MeasuresDialog(initial: List<Measure>, onApply: (List<Measure>) -> Unit, onDismiss: () -> Unit) {
    val measures = remember { mutableStateListOf(*initial.toTypedArray()) }
    val containerColor = AlertDialogDefaults.containerColor
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.measures)) },
        text = {
            ReorderableCheckboxList(
                items = measures,
                key = { it.id },
                label = { stringResource(it.title) },
                checked = { it.enabled },
                onCheckedChange = { index, value -> measures[index] = measures[index].copy(enabled = value) },
                containerColor = containerColor,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(measures.toList())
                onDismiss()
            }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// endregion
