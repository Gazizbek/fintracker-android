package kz.alem.corporation.fintracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kz.alem.corporation.fintracker.R
import kz.alem.corporation.fintracker.data.model.OperationType

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = QuickAddWidget()
}

/** Three buttons that open a new expense, income or transfer. */
class QuickAddWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetTheme {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = widgetRoot().padding(6.dp),
                ) {
                    OperationType.entries.forEachIndexed { index, type ->
                        if (index > 0) Spacer(GlanceModifier.width(6.dp))
                        AddButton(context, type)
                    }
                }
            }
        }
    }

    @Composable
    private fun RowScope.AddButton(context: Context, type: OperationType) {
        val (icon, label) = when (type) {
            OperationType.EXPENSE -> R.drawable.ic_widget_expense to R.string.expense
            OperationType.INCOME -> R.drawable.ic_widget_income to R.string.income
            OperationType.TRANSFER -> R.drawable.ic_widget_transfer to R.string.widgetTransfer
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .cornerRadius(12.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .clickable(actionStartActivity(WidgetLaunch.AddOperation(type).intent(context))),
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(type.widgetColor),
                modifier = GlanceModifier.size(24.dp),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                context.getString(label),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
