package com.shdurazi.todo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import com.shdurazi.todo.core.Block
import com.shdurazi.todo.core.Fmt
import com.shdurazi.todo.core.Planner
import com.shdurazi.todo.core.Queries
import com.shdurazi.todo.notify.ClockPref
import com.shdurazi.todo.ui.Res
import com.shdurazi.todo.ui.theme.Hues
import java.time.ZoneId

/** Light "timeline" widget: today's blocks as pastel pills, like the app's timeline card. */
class AgendaWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = ToDoApp.repo(context).data
        val now = System.currentTimeMillis()
        provideContent { AgendaContent(data, now) }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

@Composable
private fun AgendaContent(d: AppData, now: Long) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = Planner.dayOf(now, zone)
    val nowMin = Planner.minuteOf(now, zone)
    val h24 = ClockPref.is24(context, d.settings)
    val size = LocalSize.current

    val upcoming = Queries.blocksOn(d, today).filter { it.endMin > nowMin }
    val rowHeight = 44.dp
    val gap = 6.dp
    val chrome = 24.dp + 36.dp + 4.dp
    val fit = ((size.height - chrome).value / (rowHeight.value + gap.value)).toInt().coerceIn(1, 7)
    val shown = upcoming.take(fit)
    val hidden = upcoming.size - shown.size
    // the "· 2 tasks" suffix only fits on wide widgets; on narrow ones it would just be cut off
    val wide = size.width >= 300.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg_light))
            .padding(all = 12.dp)
            .clickable(onClick = WidgetIntents.openApp(context))
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Fmt.weekdayShort(today),
                style = TextStyle(color = WColors.paperText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Box(modifier = GlanceModifier.size(7.dp).background(ImageProvider(R.drawable.w_badge_red))) { }
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = Fmt.monthFull(today) + " " + Fmt.dayOfMonth(today),
                style = TextStyle(color = WColors.paperDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            )
        }
        Spacer(modifier = GlanceModifier.height(4.dp))

        if (shown.isEmpty()) {
            Column(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (Queries.blocksOn(d, today).isEmpty()) "Nothing planned" else "That's a wrap",
                    style = TextStyle(color = WColors.paperText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "Open ToDo to plan your day",
                    style = TextStyle(color = WColors.paperDim, fontSize = 12.sp)
                )
            }
        } else {
            for (b in shown) {
                BlockRow(b, h24, d, wide)
                Spacer(modifier = GlanceModifier.height(gap))
            }
            if (hidden > 0) {
                Text(
                    text = "+$hidden more",
                    style = TextStyle(color = WColors.paperDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}

@Composable
private fun BlockRow(b: Block, h24: Boolean, d: AppData, showCount: Boolean) {
    val hue = Hues.of(b.colorKey)
    val ink = ColorProvider(hue.ink)
    val soft = ColorProvider(hue.inkSoft)
    val pillText = ColorProvider(hue.pillText)
    val count = Queries.tasksOfBlock(d, b.id).count { !it.done }
    val sub = Fmt.clock(b.startMin, h24) + " – " + Fmt.clock(b.endMin, h24) +
        (if (showCount && count > 0) "  ·  " + (if (count == 1) "1 task" else "$count tasks") else "")

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(44.dp)
            .background(ImageProvider(Res.blockBg(b.colorKey)))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier.size(24.dp).background(ImageProvider(Res.badge(b.colorKey))),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(Res.glyph(b.iconKey)),
                contentDescription = null,
                modifier = GlanceModifier.size(14.dp),
                colorFilter = ColorFilter.tint(WColors.onBadge)
            )
        }
        Spacer(modifier = GlanceModifier.width(9.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = b.title,
                style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = sub,
                style = TextStyle(color = soft, fontSize = 11.sp),
                maxLines = 1
            )
        }
        Spacer(modifier = GlanceModifier.width(6.dp))
        Box(
            modifier = GlanceModifier
                .background(ImageProvider(Res.pillBg(b.colorKey)))
                .padding(horizontal = 7.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = Fmt.duration(b.durationMin),
                style = TextStyle(color = pillText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}
