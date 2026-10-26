package kz.alem.corporation.fintracker.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
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
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.data.formatNumber
import kz.alem.corporation.fintracker.data.model.OperationType
import kz.alem.corporation.fintracker.ui.planning.computeProgress
import kz.alem.corporation.fintracker.ui.theme.TypeColors
import java.util.Formatter
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.roundToInt

class PlanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = PlanWidget()
}

/** 2x2 ring chart of the plan chosen in [PlanWidgetConfigActivity]. */
class PlanWidget : GlanceAppWidget() {

    private data class PlanStatus(
        val name: String,
        val period: String,
        /** Unclamped share of the target, e.g. 1.2 for 120 %; drawn as the arc. */
        val fraction: Float,
        val percent: Int,
        val amounts: String,
        val isOverLimit: Boolean,
        val type: OperationType,
    )

    /** Not configured yet, or the plan was deleted. */
    private data object Missing

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val configure = configureIntent(context, PlanWidgetConfigActivity::class.java, appWidgetId)
        val openPlanning = WidgetLaunch.Planning.intent(context)
        val initialId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[KEY_PLAN_ID]
        val initial = withContext(Dispatchers.IO) { load(context, initialId) }

        provideContent {
            val planId = currentState<Preferences>()[KEY_PLAN_ID]
            val status = rememberWidgetData(context, planId, initial) { load(context, it) }
            WidgetTheme {
                if (status is PlanStatus) {
                    PlanContent(status, GlanceModifier.clickable(actionStartActivity(openPlanning)))
                } else {
                    SetupPrompt(context.getString(R.string.widgetTapToChoosePlan), configure)
                }
            }
        }
    }

    @Composable
    private fun PlanContent(status: PlanStatus, modifier: GlanceModifier) {
        // Same colors as the planning list: red for expenses or an exceeded limit.
        val arcColor = when {
            status.isOverLimit || status.type == OperationType.EXPENSE -> TypeColors.expense
            else -> TypeColors.income
        }.toArgb()
        val warnColor = GlanceTheme.colors.error
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = widgetRoot().then(modifier).padding(12.dp),
        ) {
            Text(
                status.name,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                modifier = GlanceModifier.fillMaxWidth(),
            )
            Text(
                status.period,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                ),
                modifier = GlanceModifier.fillMaxWidth(),
            )
            Spacer(GlanceModifier.height(6.dp))
            Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                Image(
                    provider = ImageProvider(trackBitmap()),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.surfaceVariant),
                    modifier = GlanceModifier.fillMaxSize(),
                )
                if (status.fraction > 0f) {
                    Image(
                        provider = ImageProvider(arcBitmap(status.fraction, arcColor)),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = GlanceModifier.fillMaxSize(),
                    )
                }
                Text(
                    "${status.percent}%",
                    style = TextStyle(
                        color = if (status.isOverLimit) warnColor else GlanceTheme.colors.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(
                status.amounts,
                maxLines = 1,
                style = TextStyle(
                    color = if (status.isOverLimit) warnColor else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                ),
                modifier = GlanceModifier.fillMaxWidth(),
            )
        }
    }

    private fun load(context: Context, planId: Long?): Any {
        val store = context.appContainer.store
        val plan = store.getPlan(planId) ?: return Missing
        val progress = store.computeProgress(plan)
        val locale = context.widgetLocale
        val symbol = plan.currency.target?.symbol.orEmpty()
        return PlanStatus(
            name = plan.name,
            period = formatPeriod(context, plan.beginDate, plan.endDate),
            fraction = progress.rawPercent.toFloat(),
            percent = (progress.rawPercent * 100).roundToInt(),
            amounts = "${formatNumber(progress.spent, locale)} / ${formatNumber(progress.target, locale)} $symbol".trim(),
            isOverLimit = progress.isOverLimit,
            type = plan.typeEnum,
        )
    }

    companion object {
        val KEY_PLAN_ID = longPreferencesKey("planId")

        /** Compact localized range, e.g. "1–31 Oct"; the year shows only when it is not the current one. */
        private fun formatPeriod(context: Context, begin: Long, end: Long): String {
            val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
            return DateUtils.formatDateRange(context, Formatter(StringBuilder(), context.widgetLocale), begin, end, flags, null)
                .toString()
        }

        private const val RING_SIZE = 240
        private const val RING_STROKE = 44f

        private val ringBounds = (RING_STROKE / 2).let { RectF(it, it, RING_SIZE - it, RING_SIZE - it) }

        private fun strokePaint(color: Int, cap: Paint.Cap) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = RING_STROKE
            strokeCap = cap
            this.color = color
        }

        /** Full white ring behind the arc; tinted with the theme's track color by the caller. */
        private fun trackBitmap(): Bitmap {
            val bitmap = Bitmap.createBitmap(RING_SIZE, RING_SIZE, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawArc(ringBounds, 0f, 360f, false, strokePaint(Color.WHITE, Paint.Cap.BUTT))
            return bitmap
        }

        /**
         * Progress arc from 12 o'clock, like the Apple activity rings: past 100 % it keeps
         * going around over the full ring, and its rounded tip casts a shadow on the lap
         * beneath so the overrun stays visible. Drawn in its real [color] because a tint
         * would recolor the shadow too.
         */
        private fun arcBitmap(fraction: Float, color: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(RING_SIZE, RING_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            if (fraction <= 1f) {
                canvas.drawArc(ringBounds, -90f, 360f * fraction, false, strokePaint(color, Paint.Cap.ROUND))
                return bitmap
            }
            canvas.drawArc(ringBounds, 0f, 360f, false, strokePaint(color, Paint.Cap.BUTT))
            // Only the latest lap is drawn; a whole number of laps looks like 100 %.
            val sweep = 360f * (fraction - floor(fraction))
            if (sweep < 1f) return bitmap

            // Tip position, and the shadow it casts ahead of itself along the ring.
            val center = RING_SIZE / 2f
            val radius = ringBounds.width() / 2
            val tipAngle = Math.toRadians((-90f + sweep).toDouble())
            val tipX = center + radius * cos(tipAngle).toFloat()
            val tipY = center + radius * sin(tipAngle).toFloat()
            val shadowOffset = RING_STROKE * 0.3f
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                setShadowLayer(
                    RING_STROKE * 0.5f,
                    -sin(tipAngle).toFloat() * shadowOffset,
                    cos(tipAngle).toFloat() * shadowOffset,
                    Color.argb(190, 0, 0, 0),
                )
            }
            // Keep the shadow on the ring instead of spilling onto the widget background.
            val ring = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addCircle(center, center, radius + RING_STROKE / 2, Path.Direction.CW)
                addCircle(center, center, radius - RING_STROKE / 2, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(ring)
            canvas.drawCircle(tipX, tipY, RING_STROKE / 2, shadowPaint)
            canvas.restore()
            canvas.drawArc(ringBounds, -90f, sweep, false, strokePaint(color, Paint.Cap.ROUND))
            return bitmap
        }
    }
}
