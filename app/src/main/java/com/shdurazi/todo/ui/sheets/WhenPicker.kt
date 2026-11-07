package com.shdurazi.todo.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shdurazi.todo.core.Planner
import com.shdurazi.todo.ui.Sheet
import com.shdurazi.todo.ui.UiState
import com.shdurazi.todo.ui.components.MonthCalendar
import com.shdurazi.todo.ui.components.WheelPicker
import com.shdurazi.todo.ui.theme.Hues
import com.shdurazi.todo.ui.theme.Ink
import com.shdurazi.todo.ui.theme.Txt
import java.time.LocalDate
import java.time.ZoneId

/** Hour / minute (/ AM-PM) drums for a time of day given as minutes after midnight. */
@Composable
fun TimeWheels(minute: Int, h24: Boolean, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val safe = ((minute % 1440) + 1440) % 1440
    val hour = safe / 60
    val min = safe % 60
    val hours24 = remember { (0..23).map { it.toString().padStart(2, '0') } }
    val hours12 = remember { (1..12).map { it.toString() } }
    val minutes = remember { (0..59).map { it.toString().padStart(2, '0') } }
    val meridiem = remember { listOf("AM", "PM") }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (h24) {
            WheelPicker(
                items = hours24,
                selectedIndex = hour,
                onSelected = { onChange(it * 60 + min) },
                modifier = Modifier.weight(1f),
                rowHeight = 40.dp,
                visibleRows = 3
            )
        } else {
            val h12 = if (hour % 12 == 0) 12 else hour % 12
            WheelPicker(
                items = hours12,
                selectedIndex = h12 - 1,
                onSelected = { idx ->
                    val picked = idx + 1
                    onChange((picked % 12 + (if (hour >= 12) 12 else 0)) * 60 + min)
                },
                modifier = Modifier.weight(1f),
                rowHeight = 40.dp,
                visibleRows = 3
            )
        }
        Txt(text = ":", size = 22.sp, weight = FontWeight.SemiBold, color = Ink.TextDim)
        WheelPicker(
            items = minutes,
            selectedIndex = min,
            onSelected = { onChange(hour * 60 + it) },
            modifier = Modifier.weight(1f),
            rowHeight = 40.dp,
            visibleRows = 3
        )
        if (!h24) {
            WheelPicker(
                items = meridiem,
                selectedIndex = if (hour >= 12) 1 else 0,
                onSelected = { idx -> onChange(((if (idx == 1) 12 else 0) + hour % 12) * 60 + min) },
                modifier = Modifier.weight(1f),
                rowHeight = 40.dp,
                visibleRows = 3
            )
        }
    }
}

/** Quick dates, a month calendar and an optional time: the "when" of a to-do. */
@Composable
fun WhenPickerContent(
    day: Long?,
    minute: Int?,
    today: Long,
    accent: Color,
    h24: Boolean,
    onChange: (Long?, Int?) -> Unit
) {
    val nextMonday = today + (8 - LocalDate.ofEpochDay(today).dayOfWeek.value)
    Column(modifier = Modifier.fillMaxWidth()) {
        ChipRow {
            ChoiceChip("Today", day == today, accent) { onChange(today, minute) }
            ChoiceChip("Tomorrow", day == today + 1, accent) { onChange(today + 1, minute) }
            ChoiceChip("Next week", day == nextMonday, accent) { onChange(nextMonday, minute) }
            ChoiceChip("No date", day == null, accent) { onChange(null, null) }
        }
        Spacer(modifier = Modifier.height(12.dp))
        MonthCalendar(
            selected = day,
            today = today,
            accent = accent,
            onPick = { onChange(it, minute) }
        )
        Spacer(modifier = Modifier.height(8.dp))
        FormGroup {
            SwitchRow(label = "Time", checked = minute != null, accent = accent) { on ->
                if (on) onChange(day ?: today, minute ?: (9 * 60)) else onChange(day, null)
            }
            if (minute != null) {
                TimeWheels(
                    minute = minute,
                    h24 = h24,
                    onChange = { onChange(day ?: today, it) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** Sheet opened from the toolbar of the quick-add field. */
@Composable
fun WhenPickerSheet(ui: UiState, sheet: Sheet.WhenPicker, now: Long, h24: Boolean) {
    val zone = remember { ZoneId.systemDefault() }
    val today = Planner.dayOf(now, zone)
    val hue = Hues.of(ui.repo.state.value.projects
        .firstOrNull { it.id == ui.repo.state.value.settings.selectedProjectId }?.colorKey)
    var day by remember(sheet) { mutableStateOf(sheet.day) }
    var minute by remember(sheet) { mutableStateOf(sheet.minute) }

    SheetTitle("When")
    WhenPickerContent(
        day = day,
        minute = minute,
        today = today,
        accent = hue.accent,
        h24 = h24,
        onChange = { d, m ->
            day = d
            minute = m
        }
    )
    ButtonRow {
        SheetButton(
            text = "Clear",
            fill = Ink.Button,
            textColor = Ink.Text,
            modifier = Modifier.weight(1f)
        ) {
            sheet.onResult(null, null)
            ui.sheet = null
        }
        SheetButton(
            text = "Done",
            fill = hue.accent,
            textColor = Color.Black,
            modifier = Modifier.weight(1f)
        ) {
            sheet.onResult(day, minute)
            ui.sheet = null
        }
    }
}
