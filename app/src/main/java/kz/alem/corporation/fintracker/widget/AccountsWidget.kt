package kz.alem.corporation.fintracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.data.formatNumber

class AccountsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = AccountsWidget()
}

/** Full-width list of the accounts chosen in [AccountsWidgetConfigActivity], with balances. */
class AccountsWidget : GlanceAppWidget() {

    private data class Line(val id: Long, val name: String, val color: Int, val amount: String)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val configure = configureIntent(context, AccountsWidgetConfigActivity::class.java, appWidgetId)
        val openAccounts = WidgetLaunch.Accounts.intent(context)
        val initialIds = parseIds(getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[KEY_ACCOUNT_IDS])
        val initial = withContext(Dispatchers.IO) { load(context, initialIds) }

        provideContent {
            val ids = parseIds(currentState<Preferences>()[KEY_ACCOUNT_IDS])
            val lines = rememberWidgetData(context, ids, initial) { load(context, it) }
            WidgetTheme {
                if (lines.isNullOrEmpty()) {
                    SetupPrompt(context.getString(R.string.widgetTapToChooseAccounts), configure)
                } else {
                    Column(modifier = widgetRoot().padding(vertical = 6.dp)) {
                        // Fixed title; only the accounts below it scroll.
                        Text(
                            context.getString(R.string.accounts),
                            maxLines = 1,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)
                                .clickable(actionStartActivity(openAccounts)),
                        )
                        LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                            items(lines, itemId = { it.id }) { line ->
                                AccountRow(line, GlanceModifier.clickable(actionStartActivity(openAccounts)))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AccountRow(line: Line, modifier: GlanceModifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_dot),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorProvider(line.color)),
                modifier = GlanceModifier.size(10.dp),
            )
            Spacer(GlanceModifier.width(12.dp))
            Text(
                line.name,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                line.amount,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
        }
    }

    /** Lines in the chosen order, skipping deleted accounts; null when not configured yet. */
    private fun load(context: Context, ids: List<Long>?): List<Line>? {
        ids ?: return null
        val store = context.appContainer.store
        val locale = context.widgetLocale
        return ids.mapNotNull { store.getAccount(it) }.map { account ->
            val symbol = account.currency.target?.symbol.orEmpty()
            Line(account.id, account.name, account.color, "${formatNumber(account.balance, locale)} $symbol".trim())
        }
    }

    companion object {
        /** Comma-separated ids of the chosen accounts, in display order. */
        val KEY_ACCOUNT_IDS = stringPreferencesKey("accountIds")

        fun parseIds(value: String?): List<Long>? =
            value?.split(',')?.mapNotNull { it.toLongOrNull() }
    }
}
