package com.shdurazi.todo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.shdurazi.todo.R
import com.shdurazi.todo.ToDoApp
import com.shdurazi.todo.core.AppData
import com.shdurazi.todo.core.Fmt
import com.shdurazi.todo.core.Planner
import com.shdurazi.todo.core.Queries
import com.shdurazi.todo.notify.ClockPref
import com.shdurazi.todo.ui.theme.Hues
import java.time.ZoneId

/** Dark "To Do" list: the most urgent open tasks, tick them off from the home or lock screen. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = ToDoApp.repo(context).data
        val now = System.currentTimeMillis()
        provideContent { TodayContent(data, now) }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

@Composable
private fun TodayContent(d: AppData, now: Long) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = Planner.dayOf(now, zone)
    val nowMin = Planner.minuteOf(now, zone)
    val h24 = ClockPref.is24(context, d.settings)
    val hue = Hues.of(Queries.project(d, d.settings.selectedProjectId)?.colorKey)
    val accent = ColorProvider(hue.accent)
    val size = LocalSize.current

    val rowHeight = 36.dp
    val chrome = 28.dp + 30.dp + 36.dp
    val rows = ((size.height - chrome).value / rowHeight.value).toInt().coerceIn(1, 8)
    val tasks = Queries.focusTasks(d, today, rows)
    val totalOpen = d.tasks.count { !it.done }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_dark))
            .padding(all = 14.dp)
            .clickable(onClick = WidgetIntents.openApp(context))
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(30.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_ui_check),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(accent)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "To Do",
                style = TextStyle(color = WColors.white, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = if (totalOpen == 0) "" else totalOpen.toString(),
                style = TextStyle(color = WColors.dim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            )
        }

        if (tasks.isEmpty()) {
            Column(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "All clear",
                    style = TextStyle(color = WColors.white, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "Nothing left to do right now",
                    style = TextStyle(color = WColors.dim, fontSize = 12.sp)
                )
            }
        } else {
            Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                for (t in tasks) {
                    val label = dueLabel(t, today, nowMin, h24)
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().height(rowHeight),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_ui_ring),
                            contentDescription = "Mark done",
                            modifier = GlanceModifier
                                .size(24.dp)
                                .clickable(
                                    onClick = actionRunCallback<ToggleDoneAction>(
                                        actionParametersOf(ToggleDoneAction.TaskIdKey to t.id)
                                    )
                                ),
                            colorFilter = ColorFilter.tint(WColors.ring)
                        )
                        Spacer(modifier = GlanceModifier.width(10.dp))
                        Text(
                            text = if (t.priority > 0) t.title + "  " + Fmt.priorityMark(t.priority) else t.title,
                            style = TextStyle(color = WColors.white, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        if (label.first.isNotEmpty()) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Text(
                                text = label.first,
                                style = TextStyle(
                                    color = if (label.second) WColors.danger else WColors.dim,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(34.dp)
                .clickable(onClick = WidgetIntents.quickAdd(context)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_ui_plus_circle),
                contentDescription = null,
                modifier = GlanceModifier.size(22.dp),
                colorFilter = ColorFilter.tint(accent)
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Text(
                text = "New To Do",
                style = TextStyle(color = accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            )
        }
    }
}
