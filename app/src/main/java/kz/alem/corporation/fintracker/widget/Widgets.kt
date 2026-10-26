package kz.alem.corporation.fintracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kz.alem.corporation.fintracker.AppContainer
import kz.alem.corporation.fintracker.FinTrackerApplication
import kz.alem.corporation.fintracker.MainActivity
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.ui.theme.DarkColors
import kz.alem.corporation.fintracker.ui.theme.LightColors
import kz.alem.corporation.fintracker.ui.theme.TypeColors
import java.util.Locale

internal val Context.appContainer: AppContainer
    get() = (applicationContext as FinTrackerApplication).container

/** Locale used to format widget numbers (the per-app language on Android 13+). */
internal val Context.widgetLocale: Locale
    get() = resources.configuration.locales[0] ?: Locale.getDefault()

/** Redraws every data widget; called after any data change. */
suspend fun updateDataWidgets(context: Context) {
    try {
        AccountsWidget().updateAll(context)
        PlanWidget().updateAll(context)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A widget refresh must never take the app down.
        Log.w("Widgets", "Widget update failed", e)
    }
}

/** What a widget tap asks the app to open. Encoded in the intent data URI. */
sealed interface WidgetLaunch {
    data class AddOperation(val type: OperationType) : WidgetLaunch
    data object Accounts : WidgetLaunch
    data object Planning : WidgetLaunch

    fun intent(context: Context): Intent {
        val path = when (this) {
            is AddOperation -> "add/${type.raw}"
            Accounts -> "accounts"
            Planning -> "planning"
        }
        // A distinct data URI per target keeps the PendingIntents from replacing each other.
        return Intent(context, MainActivity::class.java)
            .setData(Uri.parse("$SCHEME://$path"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    companion object {
        private const val SCHEME = "fintracker-widget"

        fun from(intent: Intent?): WidgetLaunch? {
            val uri = intent?.data ?: return null
            // Reopening the task from Recents replays the original intent.
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
            if (uri.scheme != SCHEME) return null
            return when (uri.host) {
                "add" -> uri.lastPathSegment?.let { raw ->
                    OperationType.entries.firstOrNull { it.raw == raw }?.let(::AddOperation)
                }
                "accounts" -> Accounts
                "planning" -> Planning
                else -> null
            }
        }
    }
}

/** Same palette as the app: wallpaper colors on Android 12+, the brand scheme below. */
@Composable
internal fun WidgetTheme(content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme(content = content)
    } else {
        GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors), content = content)
    }
}

/** Root modifier of every widget: themed rounded background. */
@Composable
internal fun widgetRoot(): GlanceModifier = GlanceModifier
    .fillMaxSize()
    .appWidgetBackground()
    .background(GlanceTheme.colors.widgetBackground)
    .cornerRadius(16.dp)

internal val OperationType.widgetColor: ColorProvider
    get() = ColorProvider(
        when (this) {
            OperationType.EXPENSE -> TypeColors.expense
            OperationType.INCOME -> TypeColors.income
            OperationType.TRANSFER -> TypeColors.transfer
        }
    )

internal fun colorProvider(argb: Int): ColorProvider = ColorProvider(Color(argb))

/**
 * Data shown by a widget, kept current while its session runs. [initial] is loaded before
 * the first frame so the widget never flashes empty; afterwards the data reloads off the
 * main thread (Glance composes on it) whenever [key] or the database changes.
 */
@Composable
internal fun <K, T> rememberWidgetData(context: Context, key: K, initial: T, load: (K) -> T): T {
    val tick by context.appContainer.store.changes.collectAsState()
    var data by remember { mutableStateOf(initial) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key, tick) {
        if (first) first = false else data = withContext(Dispatchers.IO) { load(key) }
    }
    return data
}

/** Shown until the widget is configured, or when its records were deleted; opens [configure]. */
@Composable
internal fun SetupPrompt(text: String, configure: Intent) {
    Box(
        modifier = widgetRoot().padding(12.dp).clickable(actionStartActivity(configure)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            maxLines = 3,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** Intent that opens [activity] to (re)configure the widget [appWidgetId]. */
internal fun configureIntent(context: Context, activity: Class<*>, appWidgetId: Int): Intent =
    Intent(context, activity)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Unique per widget so two widgets' PendingIntents do not merge.
        .setData(Uri.parse("fintracker-widget-config://$appWidgetId"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
