package com.shdurazi.todo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shdurazi.todo.R
import com.shdurazi.todo.ui.theme.Ink
import com.shdurazi.todo.ui.theme.Txt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Month grid used for picking a day. Weeks start on the locale's first day of the week. */
@Composable
fun MonthCalendar(
    selected: Long?,
    today: Long,
    accent: Color,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var month by remember(selected) {
        mutableStateOf(YearMonth.from(LocalDate.ofEpochDay(selected ?: today)))
    }
    val first: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val lead = ((month.atDay(1).dayOfWeek.value - first.value) + 7) % 7
    val days = month.lengthOfMonth()
    val rows = (lead + days + 6) / 7

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Txt(
                text = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year,
                size = 17.sp,
                weight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier.size(40.dp).pressable { month = month.minusMonths(1) },
                contentAlignment = Alignment.Center
            ) { Glyph(R.drawable.ic_ui_chevron_left, 22.dp, Ink.TextDim) }
            Box(
                modifier = Modifier.size(40.dp).pressable { month = month.plusMonths(1) },
                contentAlignment = Alignment.Center
            ) { Glyph(R.drawable.ic_ui_chevron_right, 22.dp, Ink.TextDim) }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val dow = first.plus(i.toLong())
                Box(modifier = Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                    Txt(
                        text = dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2),
                        size = 12.sp,
                        color = Ink.TextDim,
                        weight = FontWeight.Medium
                    )
                }
            }
        }
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNumber = r * 7 + c - lead + 1
                    if (dayNumber < 1 || dayNumber > days) {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1.15f))
                    } else {
                        val epoch = month.atDay(dayNumber).toEpochDay()
                        val isSel = selected != null && epoch == selected
                        val isToday = epoch == today
                        Box(
                            modifier = Modifier.weight(1f).aspectRatio(1.15f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .pressable(pressedScale = 0.9f) { onPick(epoch) }
                                    .clip(CircleShape)
                                    .background(if (isSel) accent else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Txt(
                                    text = dayNumber.toString(),
                                    size = 16.sp,
                                    weight = if (isSel || isToday) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.Black else if (isToday) accent else Ink.Text,
                                    align = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
