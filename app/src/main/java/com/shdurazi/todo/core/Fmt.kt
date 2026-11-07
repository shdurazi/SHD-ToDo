package com.shdurazi.todo.core

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Locale-independent (always English / ASCII digits) formatting helpers. */
object Fmt {
    private fun two(n: Int): String = n.toString().padStart(2, '0')

    /** "09:30" (24 h) or "9:30 AM" (12 h) */
    fun clock(minute: Int, h24: Boolean): String {
        val m = ((minute % 1440) + 1440) % 1440
        val h = m / 60
        val mm = m % 60
        if (h24) return two(h) + ":" + two(mm)
        val h12 = if (h % 12 == 0) 12 else h % 12
        return h12.toString() + ":" + two(mm) + " " + (if (h < 12) "AM" else "PM")
    }

    /** Label on the timeline's hour rail: "09:00" or "9 AM". */
    fun hourLabel(hour: Int, h24: Boolean): String {
        if (h24) return two(hour) + ":00"
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return h12.toString() + " " + (if (hour < 12) "AM" else "PM")
    }

    /** "45m", "1h", "1h 30m" */
    fun duration(min: Int): String {
        val h = min / 60
        val m = min % 60
        return if (h == 0) "${m}m" else if (m == 0) "${h}h" else "${h}h ${m}m"
    }

    /** Big focus countdown: "47min" while at least a minute is left, otherwise "42s". */
    fun focusRemaining(ms: Long): String {
        if (ms <= 0L) return "0s"
        return if (ms >= 60_000L) "${(ms + 59_999L) / 60_000L}min" else "${(ms + 999L) / 1000L}s"
    }

    fun weekdayShort(day: Long): String =
        LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    fun monthFull(day: Long): String =
        LocalDate.ofEpochDay(day).month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    fun monthShort(day: Long): String =
        LocalDate.ofEpochDay(day).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    fun dayOfMonth(day: Long): Int = LocalDate.ofEpochDay(day).dayOfMonth

    /** "Today", "Tomorrow", "Yesterday" or "Mon 12 Oct". */
    fun dayLabel(day: Long, today: Long): String {
        val diff = day - today
        return if (diff == 0L) "Today"
        else if (diff == 1L) "Tomorrow"
        else if (diff == -1L) "Yesterday"
        else weekdayShort(day) + " " + dayOfMonth(day) + " " + monthShort(day)
    }

    /** Small two-line label shown at the right of a to-do row. */
    data class Meta(val top: String, val bottom: String?, val overdue: Boolean)

    fun dueMeta(t: Task, today: Long, nowMinute: Int, h24: Boolean): Meta? {
        val d = t.dueDay ?: return null
        val min = t.dueMinute
        val overdue = d < today || (d == today && min != null && min < nowMinute)
        return Meta(dayLabel(d, today), if (min != null) clock(min, h24) else null, overdue)
    }

    /** "!" / "!!" / "!!!" */
    fun priorityMark(p: Int): String = "!".repeat(p.coerceIn(0, 3))

    /** "1 scheduled task" / "3 scheduled tasks" */
    fun scheduledTasks(n: Int): String = if (n == 1) "1 scheduled task" else "$n scheduled tasks"
}
