package kz.alem.corporation.fintracker.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.data.model.Account
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.ui.common.AppTopBar
import kz.alem.corporation.fintracker.ui.common.EmptyState
import kz.alem.corporation.fintracker.ui.common.Formats
import kz.alem.corporation.fintracker.ui.common.IconChip
import kz.alem.corporation.fintracker.ui.common.ItemDivider
import kz.alem.corporation.fintracker.ui.common.LocalAppContainer
import kz.alem.corporation.fintracker.ui.common.SectionHeader
import kz.alem.corporation.fintracker.ui.common.appLocale
import kz.alem.corporation.fintracker.ui.common.color
import kz.alem.corporation.fintracker.ui.common.icon
import kz.alem.corporation.fintracker.ui.common.imageVector
import kz.alem.corporation.fintracker.ui.planning.PlanItem
import kz.alem.corporation.fintracker.ui.planning.computeProgress
import kz.alem.corporation.fintracker.ui.theme.FinTrackerTheme
import kz.alem.corporation.fintracker.ui.theme.TypeColors

/**
 * Base of the widget setup screens. The launcher opens it when a widget is added (and, on
 * Android 12+, to reconfigure it); the widget itself opens it while it has nothing to show.
 */
abstract class WidgetConfigActivity : AppCompatActivity() {

    protected var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        private set

    protected abstract val widget: GlanceAppWidget

    @Composable
    protected abstract fun Content(saved: Preferences?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Backing out must not add the widget.
        setResult(RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val container = appContainer
        setContent {
            val theme by container.prefs.theme.collectAsState()
            val saved by produceState<Preferences?>(null) {
                val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                value = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                FinTrackerTheme(theme) { Content(saved) }
            }
        }
    }

    /** Stores the choice in the widget's state, redraws it and closes. */
    protected fun save(edit: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { edit(it) }
            widget.update(this@WidgetConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

class AccountsWidgetConfigActivity : WidgetConfigActivity() {
    override val widget = AccountsWidget()

    @Composable
    override fun Content(saved: Preferences?) {
        val savedIds = saved?.let { AccountsWidget.parseIds(it[AccountsWidget.KEY_ACCOUNT_IDS]) }
        AccountsConfigScreen(
            savedIds = savedIds,
            loaded = saved != null,
            onClose = ::finish,
            onSave = { ids -> save { it[AccountsWidget.KEY_ACCOUNT_IDS] = ids.joinToString(",") } },
        )
    }
}

class PlanWidgetConfigActivity : WidgetConfigActivity() {
    override val widget = PlanWidget()

    @Composable
    override fun Content(saved: Preferences?) {
        PlanConfigScreen(
            selectedId = saved?.get(PlanWidget.KEY_PLAN_ID),
            onClose = ::finish,
            onSelect = { id -> save { it[PlanWidget.KEY_PLAN_ID] = id } },
        )
    }
}

private data class AccountGroup(val title: String, val accounts: List<Account>)

/** Multi-select list of all accounts grouped by currency; saves them in list order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountsConfigScreen(
    savedIds: List<Long>?,
    loaded: Boolean,
    onClose: () -> Unit,
    onSave: (List<Long>) -> Unit,
) {
    val store = LocalAppContainer.current.store
    val locale = appLocale
    val groups by produceState(emptyList<AccountGroup>()) {
        value = withContext(Dispatchers.IO) {
            store.readAccounts(onlyVisible = false)
                .groupBy { it.currency.targetId }
                .values
                .map { accounts -> accounts.first().currency.target to accounts }
                .sortedBy { it.first?.name.orEmpty() }
                .map { (currency, accounts) -> AccountGroup(currency?.name.orEmpty(), accounts) }
        }
    }
    // Starts from the saved choice once the widget state has been read.
    var selection by remember(loaded) { mutableStateOf(savedIds.orEmpty().toSet()) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.chooseAccounts),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
                },
                actions = {
                    TextButton(
                        enabled = selection.isNotEmpty(),
                        onClick = { onSave(groups.flatMap { it.accounts }.map { it.id }.filter { it in selection }) },
                    ) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            groups.forEachIndexed { index, group ->
                item(key = "header-$index") { SectionHeader(group.title) }
                items(group.accounts, key = { it.id }) { account ->
                    val checked = account.id in selection
                    ListItem(
                        headlineContent = { Text(account.name) },
                        supportingContent = {
                            Text(Formats.money(account.balance, account.currency.target?.symbol.orEmpty(), locale))
                        },
                        leadingContent = { IconChip(color = Color(account.color), icon = account.iconEnum.imageVector) },
                        trailingContent = {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.alpha(if (checked) 1f else 0f),
                            )
                        },
                        modifier = Modifier.clickable {
                            selection = if (checked) selection - account.id else selection + account.id
                        },
                    )
                    ItemDivider()
                }
            }
        }
    }
}

/** Single-select list of plans; choosing one saves immediately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanConfigScreen(selectedId: Long?, onClose: () -> Unit, onSelect: (Long) -> Unit) {
    val store = LocalAppContainer.current.store
    val locale = appLocale
    val items by produceState<List<PlanItem>?>(null) {
        value = withContext(Dispatchers.IO) {
            store.readPlans().map { PlanItem(it, store.computeProgress(it), emptyList()) }
        }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.choosePlan),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
                },
            )
        },
    ) { padding ->
        val plans = items ?: return@Scaffold
        if (plans.isEmpty()) {
            EmptyState(
                Icons.Filled.TrackChanges,
                stringResource(R.string.noPlansYet),
                stringResource(R.string.noPlansYetDescription),
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(plans, key = { it.plan.id }) { item ->
                val plan = item.plan
                val progress = item.progress
                val type = plan.typeEnum
                val symbol = plan.currency.target?.symbol.orEmpty()
                ListItem(
                    headlineContent = { Text(plan.name) },
                    supportingContent = {
                        Column {
                            Text("${Formats.mediumDate(plan.beginDate, locale)} – ${Formats.mediumDate(plan.endDate, locale)}")
                            LinearProgressIndicator(
                                progress = { progress.percent },
                                color = if (progress.isOverLimit || type == OperationType.EXPENSE) TypeColors.expense else TypeColors.income,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                // One continuous bar: no gap before the track and no end dot (Material 3 defaults).
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                            Text("${Formats.number(progress.spent, locale)} / ${Formats.number(progress.target, locale)} $symbol")
                        }
                    },
                    leadingContent = { IconChip(color = type.color, icon = type.icon) },
                    trailingContent = {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.alpha(if (plan.id == selectedId) 1f else 0f),
                        )
                    },
                    modifier = Modifier.clickable { onSelect(plan.id) },
                )
                ItemDivider()
            }
        }
    }
}
