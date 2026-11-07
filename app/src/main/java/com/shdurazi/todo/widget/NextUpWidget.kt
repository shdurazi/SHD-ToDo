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
import com.shdurazi.todo.ui.Res
import com.shdurazi.todo.ui.theme.Hues
import java.time.ZoneId

/** Small square: the block you are in, or the next one. Made for the lock screen as well. */
class NextUpWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = ToDoApp.repo(context).data
        val now = System.currentTimeMillis()
        provideContent { NextUpContent(data, now) }
    }
}

class NextUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextUpWidget()
}

@Composable
private fun NextUpContent(d: AppData, now: Long) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = Planner.dayOf(now, zone)
    val nowMin = Planner.minuteOf(now, zone)
    val h24 = ClockPref.is24(context, d.settings)
    val block = Queries.currentOrNext(d, today, nowMin)

    if (block == null) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg_dark))
                .padding(all = 14.dp)
                .clickable(onClick = WidgetIntents.quickAdd(context)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Free",
                style = TextStyle(color = WColors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Nothing else planned today",
                style = TextStyle(color = WColors.dim, fontSize = 12.sp)
            )
        }
        return
    }

    val hue = Hues.of(block.colorKey)
    val ink = ColorProvider(hue.ink)
    val soft = ColorProvider(hue.inkSoft)
    val pillText = ColorProvider(hue.pillText)
    val running = nowMin >= block.startMin && nowMin < block.endMin
    val open = Queries.tasksOfBlock(d, block.id).count { !it.done }
    val range = Fmt.clock(block.startMin, h24) + " – " + Fmt.clock(block.endMin, h24)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(Res.blockBg(block.colorKey)))
            .padding(all = 12.dp)
            .clickable(onClick = WidgetIntents.openApp(context))
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (running) "NOW" else "NEXT",
                style = TextStyle(color = soft, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight()
            )
            Box(
                modifier = GlanceModifier.size(24.dp).background(ImageProvider(Res.badge(block.colorKey))),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(Res.glyph(block.iconKey)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(14.dp),
                    colorFilter = ColorFilter.tint(WColors.onBadge)
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = block.title,
            style = TextStyle(color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            maxLines = 2
        )
        Text(
            text = range,
            style = TextStyle(color = soft, fontSize = 12.sp)
        )
        if (open > 0) {
            Text(
                text = if (open == 1) "1 task" else "$open tasks",
                style = TextStyle(color = soft, fontSize = 12.sp)
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Box(
            modifier = GlanceModifier
                .background(ImageProvider(Res.pillBg(block.colorKey)))
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .clickable(onClick = WidgetIntents.startFocus(context, block.id)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Start focus",
                style = TextStyle(color = pillText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}
