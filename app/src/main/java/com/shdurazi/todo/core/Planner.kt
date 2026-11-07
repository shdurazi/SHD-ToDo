package com.shdurazi.todo.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Something that should happen at a point in time (reminder, alert, summary, end of focus ...). */
sealed class Due {
    data class TaskReminder(val taskId: String) : Due()
    data class BlockAlert(val blockId: String) : Due()
    object DailySummary : Due()
    object FocusEnd : Due()
}

/**
 * Pure scheduling logic: decides what is due now and when the next alarm has to ring.
 * The Android layer only has to post notifications and set one alarm.
 */
object Planner {
    data class Item(val at: Long, val due: Due)

    const val SUMMARY_GRACE_MS = 3L * 60L * 60L * 1000L
    private const val MINUTE_MS = 60_000L

    fun millisOf(day: Long, minute: Int, zone: ZoneId): Long {
        val m = minute.coerceIn(0, 1439)
        val ldt = LocalDateTime.of(LocalDate.ofEpochDay(day), LocalTime.of(m / 60, m % 60))
        return ldt.atZone(zone).toInstant().toEpochMilli()
    }

    fun dayOf(millis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun minuteOf(millis: Long, zone: ZoneId): Int {
        val t = Instant.ofEpochMilli(millis).atZone(zone)
        return t.hour * 60 + t.minute
    }

    fun blockStart(b: Block, zone: ZoneId): Long = millisOf(b.day, b.startMin, zone)

    fun blockEnd(b: Block, zone: ZoneId): Long = blockStart(b, zone) + b.durationMin * MINUTE_MS

    /** When today's (or tomorrow's) daily summary should be delivered. */
    fun summaryTime(d: AppData, now: Long, zone: ZoneId): Long? {
        val s = d.settings
        if (!s.dailySummary) return null
        val today = dayOf(now, zone)
        val day = if (s.lastSummaryDay < today) today else today + 1
        return millisOf(day, s.summaryMinute, zone)
    }

    fun items(d: AppData, now: Long, zone: ZoneId): List<Item> {
        val out = ArrayList<Item>()
        for (t in d.tasks) {
            val at = t.remindAt
            if (!t.done && at != null) out.add(Item(at, Due.TaskReminder(t.id)))
        }
        val lead = d.settings.blockLeadMin
        if (lead > 0) {
            for (b in d.blocks) {
                if (!b.remind || b.alerted) continue
                if (blockEnd(b, zone) <= now) continue
                out.add(Item(blockStart(b, zone) - lead * MINUTE_MS, Due.BlockAlert(b.id)))
            }
        }
        val st = summaryTime(d, now, zone)
        if (st != null) out.add(Item(st, Due.DailySummary))
        val f = d.focus
        if (f != null) out.add(Item(f.endsAt, Due.FocusEnd))
        out.sortBy { it.at }
        return out
    }

    fun dueNow(d: AppData, now: Long, zone: ZoneId): List<Due> =
        items(d, now, zone).filter { it.at <= now }.map { it.due }

    /** Time of the earliest alarm strictly in the future. */
    fun nextAt(d: AppData, now: Long, zone: ZoneId): Long? =
        items(d, now, zone).firstOrNull { it.at > now }?.at

    /** When widgets / the "next up" notification need to be redrawn next (block boundaries, midnight, 30 min). */
    fun nextRefreshAt(d: AppData, now: Long, zone: ZoneId): Long {
        val today = dayOf(now, zone)
        var best = minOf(millisOf(today + 1, 0, zone), now + 30L * MINUTE_MS)
        for (b in d.blocks) {
            if (b.day != today) continue
            val s = blockStart(b, zone)
            val e = blockEnd(b, zone)
            if (s > now && s < best) best = s
            if (e > now && e < best) best = e
        }
        return maxOf(best, now + 1_000L)
    }

    data class Summary(val blocks: Int, val tasks: Int, val firstBlock: Block?)

    fun summary(d: AppData, today: Long): Summary {
        val blocks = Queries.blocksOn(d, today)
        return Summary(blocks.size, Queries.todayTaskCount(d, today), blocks.firstOrNull())
    }
}
