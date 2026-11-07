package com.shdurazi.todo.core

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** Result of parsing one line typed into the quick-add field. */
data class QuickParse(
    val title: String,
    /** epoch day, if the text contained a date */
    val day: Long?,
    /** minute of day, if the text contained a time */
    val minute: Int?,
    /** 0..3 */
    val priority: Int,
    /** project referenced with #name (only when it matches an existing project) */
    val projectId: String?,
    /** ranges of the original text that were understood (used for highlighting) */
    val spans: List<IntRange>
)

/**
 * Natural-language parser for lines such as `Team sync today 9am !!` or `Call mum tomorrow 6pm #personal`.
 * Understood: priority (`!`, `!!`, `!!!`), `#project`, today / tonight / tomorrow, weekdays (`friday`, `next mon`),
 * `next week`, `in 2 hours` / `in 3 days`, `oct 5` / `5 oct` / `2026-12-01`, and times (`9am`, `9:30pm`, `21:00`, `at 9`, `noon`).
 */
object QuickParser {
    private val IC = setOf(RegexOption.IGNORE_CASE)
    private const val NB = "(?<![\\p{L}\\p{N}])"
    private const val NA = "(?![\\p{L}\\p{N}])"
    private const val MONTHS =
        "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"

    private val PRIORITY = Regex("(?<!\\S)(!{1,3})(?!\\S)")
    private val PROJECT = Regex("(?<!\\S)#([\\p{L}\\p{N}_-]+)")
    private val RELATIVE =
        Regex("${NB}in\\s+(\\d{1,3})\\s*(minutes?|mins?|hours?|hrs?|days?|weeks?|[mhdw])$NA", IC)
    private val ISO_DATE = Regex("${NB}(\\d{4})-(\\d{2})-(\\d{2})$NA")
    private val MONTH_DAY = Regex(
        "${NB}(?:(?:on|by)\\s+)?($MONTHS)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?(?![\\p{L}\\p{N}:])",
        IC
    )
    private val DAY_MONTH = Regex(
        "${NB}(?:(?:on|by)\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?($MONTHS)\\.?(?:,?\\s+(\\d{4}))?$NA",
        IC
    )
    private val NEXT_WEEK = Regex("${NB}next\\s+week$NA", IC)
    private val WEEKDAY = Regex(
        "${NB}(?:(next|this|on|by)\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tues|tue|wed|thurs|thur|thu|fri|sat|sun)$NA",
        IC
    )
    private val TODAY_TMR = Regex("${NB}(?:(?:on|by)\\s+)?(today|tonight|tomorrow|tmrw|tmr)$NA", IC)
    private val TIME_MERIDIEM = Regex(
        "(?<![\\p{L}\\p{N}:.])(?:(?:at|@)\\s*)?(\\d{1,2})(?:[.:](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)$NA",
        IC
    )
    private val TIME_24 = Regex(
        "(?<![\\p{L}\\p{N}:.])(?:(?:at|@)\\s*)?([01]?\\d|2[0-3]):([0-5]\\d)(?![\\p{L}\\p{N}:])",
        IC
    )
    private val TIME_AT = Regex("${NB}(?:at|@)\\s*(\\d{1,2})(?:[.:]([0-5]\\d))?(?![\\p{L}\\p{N}:])", IC)
    private val NOON = Regex("${NB}(noon|midday)$NA", IC)
    private val DAYPART = Regex("${NB}(morning|afternoon|evening|night)$NA", IC)

    private val FULL_DAYS = setOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

    fun matchProject(projects: List<Project>, query: String): Project? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        return projects.firstOrNull { it.name.lowercase() == q }
            ?: projects.firstOrNull { it.name.lowercase().startsWith(q) }
            ?: projects.firstOrNull { it.name.lowercase().replace(" ", "").startsWith(q) }
    }

    fun parse(input: String, now: LocalDateTime, projects: List<Project> = emptyList()): QuickParse {
        val taken = ArrayList<IntRange>()
        fun free(r: IntRange): Boolean = taken.none { it.first <= r.last && r.first <= it.last }
        fun firstFree(rx: Regex, accept: (MatchResult) -> Boolean = { true }): MatchResult? =
            rx.findAll(input).firstOrNull { free(it.range) && accept(it) }

        val today = now.toLocalDate()
        val nowMin = now.hour * 60 + now.minute

        // ---- priority ("!", "!!", "!!!" as standalone words)
        var priority = 0
        for (m in PRIORITY.findAll(input)) {
            if (!free(m.range)) continue
            priority = maxOf(priority, m.groupValues[1].length)
            taken.add(m.range)
        }

        // ---- #project (only if it names an existing project)
        var projectId: String? = null
        val pm = firstFree(PROJECT) { matchProject(projects, it.groupValues[1]) != null }
        if (pm != null) {
            projectId = matchProject(projects, pm.groupValues[1])?.id
            taken.add(pm.range)
        }

        var day: LocalDate? = null
        var minute: Int? = null
        var tonight = false
        var relativeTime = false
        var dateMatch: MatchResult? = null

        // ---- "in 2 hours", "in 3 days"
        val rel = firstFree(RELATIVE)
        if (rel != null) {
            val n = rel.groupValues[1].toInt()
            val unit = rel.groupValues[2].lowercase()[0]
            when (unit) {
                'm', 'h' -> {
                    val target = now.plusMinutes(if (unit == 'h') n * 60L else n.toLong())
                    day = target.toLocalDate()
                    minute = target.hour * 60 + target.minute
                    relativeTime = true
                }
                'd' -> day = today.plusDays(n.toLong())
                else -> day = today.plusWeeks(n.toLong())
            }
            taken.add(rel.range)
        }

        // ---- explicit dates
        if (day == null) {
            val m = firstFree(ISO_DATE)
            if (m != null) {
                val d = safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                if (d != null) {
                    day = d
                    dateMatch = m
                    taken.add(m.range)
                }
            }
        }
        if (day == null) {
            val m = firstFree(MONTH_DAY)
            if (m != null) {
                val d = resolveMonthDay(today, monthOf(m.groupValues[1]), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull())
                if (d != null) {
                    day = d
                    dateMatch = m
                    taken.add(m.range)
                }
            }
        }
        if (day == null) {
            val m = firstFree(DAY_MONTH)
            if (m != null) {
                val d = resolveMonthDay(today, monthOf(m.groupValues[2]), m.groupValues[1].toInt(), m.groupValues[3].toIntOrNull())
                if (d != null) {
                    day = d
                    dateMatch = m
                    taken.add(m.range)
                }
            }
        }
        if (day == null) {
            val m = firstFree(NEXT_WEEK)
            if (m != null) {
                day = today.plusDays((8 - today.dayOfWeek.value).toLong())
                dateMatch = m
                taken.add(m.range)
            }
        }
        if (day == null) {
            val m = firstFree(WEEKDAY) { r ->
                FULL_DAYS.contains(r.groupValues[2].lowercase()) || r.groupValues[1].isNotEmpty()
            }
            if (m != null) {
                val prefix = m.groupValues[1].lowercase()
                val target = dayOfWeekOf(m.groupValues[2])
                var delta = (target.value - today.dayOfWeek.value + 7) % 7
                if (delta == 0) delta = 7
                if (prefix == "next" && today.dayOfWeek.value + delta <= 7) delta += 7
                day = today.plusDays(delta.toLong())
                dateMatch = m
                taken.add(m.range)
            }
        }
        if (day == null) {
            val m = firstFree(TODAY_TMR)
            if (m != null) {
                val w = m.groupValues[1].lowercase()
                if (w == "today" || w == "tonight") {
                    day = today
                    tonight = w == "tonight"
                } else {
                    day = today.plusDays(1)
                }
                dateMatch = m
                taken.add(m.range)
            }
        }

        // ---- times
        var ambiguousHour: Int? = null
        var ambiguousMinute = 0
        if (!relativeTime) {
            val mm = firstFree(TIME_MERIDIEM)
            if (mm != null) {
                val h = mm.groupValues[1].toInt()
                val mi = mm.groupValues[2].ifEmpty { "0" }.toInt()
                val pmFlag = mm.groupValues[3].lowercase().startsWith("p")
                if (h in 1..12 && mi in 0..59) {
                    minute = (h % 12 + (if (pmFlag) 12 else 0)) * 60 + mi
                    taken.add(mm.range)
                }
            }
            if (minute == null) {
                val m = firstFree(TIME_24)
                if (m != null) {
                    minute = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
                    taken.add(m.range)
                }
            }
            if (minute == null) {
                val m = firstFree(TIME_AT)
                if (m != null) {
                    val h = m.groupValues[1].toInt()
                    val mi = m.groupValues[2].ifEmpty { "0" }.toInt()
                    if (h == 0 || h in 13..23) {
                        minute = h * 60 + mi
                        taken.add(m.range)
                    } else if (h in 1..12) {
                        ambiguousHour = h
                        ambiguousMinute = mi
                        taken.add(m.range)
                    }
                }
            }
            if (minute == null && ambiguousHour == null) {
                val m = firstFree(NOON)
                if (m != null) {
                    minute = 12 * 60
                    taken.add(m.range)
                }
            }
            if (minute == null && ambiguousHour == null) {
                val dm = dateMatch
                val m = firstFree(DAYPART) { r ->
                    dm != null && r.range.first > dm.range.last &&
                        input.substring(dm.range.last + 1, r.range.first).isBlank()
                }
                if (m != null) {
                    minute = when (m.groupValues[1].lowercase()) {
                        "morning" -> 9 * 60
                        "afternoon" -> 15 * 60
                        "evening" -> 18 * 60
                        else -> 21 * 60
                    }
                    taken.add(m.range)
                }
            }
            if (minute == null && ambiguousHour == null && tonight) minute = 20 * 60
        }

        // ---- resolve ambiguous "at 9"
        var resolvedDay: LocalDate? = day
        var resolvedMinute: Int? = minute
        val ah = ambiguousHour
        if (ah != null && resolvedMinute == null) {
            if (ah == 12) {
                resolvedMinute = 12 * 60 + ambiguousMinute
            } else {
                val am = ah * 60 + ambiguousMinute
                val pmv = (ah + 12) * 60 + ambiguousMinute
                resolvedMinute = if (resolvedDay == null || resolvedDay == today) {
                    if (am > nowMin) am else if (pmv > nowMin) pmv else am
                } else {
                    if (ah in 7..11) am else pmv
                }
            }
        }
        // a time without a date: today if it is still ahead, otherwise tomorrow
        val rm = resolvedMinute
        if (resolvedDay == null && rm != null) {
            resolvedDay = if (rm > nowMin) today else today.plusDays(1)
        }

        // ---- title = input without the understood tokens
        val sorted = taken.sortedBy { it.first }
        val sb = StringBuilder()
        for (i in input.indices) {
            if (sorted.none { i in it }) sb.append(input[i])
        }
        var title = sb.toString().replace(Regex("\\s+"), " ").trim { c ->
            c.isWhitespace() || c == ',' || c == ';' || c == '-' || c == ':'
        }
        if (title.isEmpty()) title = input.trim()

        return QuickParse(
            title = title,
            day = resolvedDay?.toEpochDay(),
            minute = resolvedMinute,
            priority = priority,
            projectId = projectId,
            spans = sorted
        )
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        try {
            LocalDate.of(y, m, d)
        } catch (e: DateTimeException) {
            null
        }

    private fun resolveMonthDay(today: LocalDate, month: Int, dom: Int, year: Int?): LocalDate? {
        if (year != null) return safeDate(year, month, dom)
        val d = safeDate(today.year, month, dom) ?: return null
        return if (d.isBefore(today)) safeDate(today.year + 1, month, dom) else d
    }

    private fun monthOf(word: String): Int =
        when (word.lowercase().take(3)) {
            "jan" -> 1
            "feb" -> 2
            "mar" -> 3
            "apr" -> 4
            "may" -> 5
            "jun" -> 6
            "jul" -> 7
            "aug" -> 8
            "sep" -> 9
            "oct" -> 10
            "nov" -> 11
            else -> 12
        }

    private fun dayOfWeekOf(word: String): DayOfWeek =
        when (word.lowercase().take(3)) {
            "mon" -> DayOfWeek.MONDAY
            "tue" -> DayOfWeek.TUESDAY
            "wed" -> DayOfWeek.WEDNESDAY
            "thu" -> DayOfWeek.THURSDAY
            "fri" -> DayOfWeek.FRIDAY
            "sat" -> DayOfWeek.SATURDAY
            else -> DayOfWeek.SUNDAY
        }
}
