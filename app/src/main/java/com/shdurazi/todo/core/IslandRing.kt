package com.shdurazi.todo.core

import java.time.ZoneId

/**
 * What the dial in the island shows: the running focus session and the blocks around it as arcs on a ring.
 * Angles are degrees clockwise from the top. "Now" is always at [NOW_DEG], left of the top, like the notch of the
 * Anchor dial: a block that has just started begins there and reaches to the right, and as time passes the whole dial
 * keeps turning to the left.
 * Plain Kotlin (no Android) so that it can be tested.
 */
object IslandRing {
    /** Degrees of the dial per minute for a normal block (one full turn is a little over four and a half hours). */
    const val DEG_PER_MIN = 1.33f

    /** Where "now" sits on the dial. */
    const val NOW_DEG = -35f

    /** The running block never takes more than this many degrees, so that its rounded ends stay in view. */
    const val MAX_ACTIVE_DEG = 70f

    /** Blocks further away from "now" than this many minutes (either side) are off the visible part of the ring. */
    const val WINDOW_MIN = 100

    data class Seg(
        val id: String,
        val startMs: Long,
        val endMs: Long,
        val colorKey: String,
        val iconKey: String,
        val active: Boolean
    )

    /** Dial degrees per minute for a session of this length: long sessions are drawn slower so they still fit. */
    fun degPerMin(sessionMs: Long): Float {
        val minutes = (sessionMs / 60_000f).coerceAtLeast(1f)
        return minOf(DEG_PER_MIN, MAX_ACTIVE_DEG / minutes)
    }

    /** Position on the dial of a point in time (later = further right; "now" is at [NOW_DEG]). */
    fun angle(ms: Long, now: Long, degPerMin: Float): Float = NOW_DEG + (ms - now) / 60_000f * degPerMin

    /** The running session first, then the blocks around it (past and future) that do not overlap it. */
    fun segments(d: AppData, f: FocusSession, now: Long, zone: ZoneId): List<Seg> {
        val out = ArrayList<Seg>()
        out.add(Seg("focus", f.startedAt, f.endsAt, f.colorKey, f.iconKey, true))
        val window = WINDOW_MIN * 60_000L
        val today = Planner.dayOf(now, zone)
        val candidates = d.blocks
            .filter { it.id != f.blockId && it.day >= today - 1 && it.day <= today + 1 }
            .map { Seg(it.id, Planner.blockStart(it, zone), Planner.blockEnd(it, zone), it.colorKey, it.iconKey, false) }
            .filter { it.endMs > now - window && it.startMs < now + window }
            .filter { it.startMs >= f.endsAt || it.endMs <= f.startedAt }
            .sortedBy { it.startMs }
        var lastEnd = Long.MIN_VALUE
        for (c in candidates) {
            if (c.startMs < lastEnd) continue
            out.add(c)
            lastEnd = c.endMs
        }
        return out
    }

    /** "30 Min", "1h 05m", "<1 Min", "Done" */
    fun remainingLabel(msLeft: Long): String {
        if (msLeft <= 0L) return "Done"
        if (msLeft < 60_000L) return "<1 Min"
        val m = ((msLeft + 59_999L) / 60_000L).toInt()
        return if (m < 60) "$m Min" else "${m / 60}h " + (m % 60).toString().padStart(2, '0') + "m"
    }

    /** "29:41", "1:05:09" */
    fun countdown(msLeft: Long): String {
        val s = ((msLeft.coerceAtLeast(0L) + 999L) / 1000L).toInt()
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        val mm = m.toString().padStart(2, '0')
        val ss = sec.toString().padStart(2, '0')
        return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
    }

    /** How much of the session is still ahead, 1 at the start and 0 at the end. */
    fun fractionLeft(startedAt: Long, endsAt: Long, now: Long): Float {
        val total = (endsAt - startedAt).coerceAtLeast(1L)
        return ((endsAt - now).toFloat() / total).coerceIn(0f, 1f)
    }
}
