package com.shdurazi.todo.core

/** Colour keys shared by projects and timeline blocks. The UI maps them to real colours. */
object Keys {
    val colors = listOf("green", "blue", "teal", "yellow", "orange", "pink", "purple", "red")
    val icons = listOf(
        "briefcase", "mail", "pencil", "person", "bulb", "fork", "hammer",
        "phone", "check", "star", "book", "code", "heart", "cart", "plane"
    )
}

data class Project(
    val id: String,
    val name: String,
    val colorKey: String = "green",
    val iconKey: String = "briefcase"
)

data class Task(
    val id: String,
    val projectId: String,
    val title: String,
    val notes: String = "",
    /** 0 = none, 1 = "!", 2 = "!!", 3 = "!!!" */
    val priority: Int = 0,
    /** true = shown in the "Nice to do" section */
    val later: Boolean = false,
    /** epoch day of the due date */
    val dueDay: Long? = null,
    /** minute of day of the due time (only meaningful when [dueDay] is set) */
    val dueMinute: Int? = null,
    /** user preference: remind at the due time */
    val remind: Boolean = true,
    /** pending reminder (epoch millis); cleared once it has fired */
    val remindAt: Long? = null,
    /** timeline block this task has been scheduled into */
    val blockId: String? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val createdAt: Long = 0L,
    val order: Long = 0L
)

data class Block(
    val id: String,
    val projectId: String,
    val title: String,
    val iconKey: String = "bulb",
    val colorKey: String = "blue",
    /** epoch day */
    val day: Long,
    /** minutes from midnight */
    val startMin: Int,
    val durationMin: Int,
    /** alert shortly before the block starts */
    val remind: Boolean = true,
    /** the start alert has already fired (or is no longer relevant) */
    val alerted: Boolean = false
) {
    val endMin: Int get() = startMin + durationMin
}

data class FocusSession(
    val blockId: String?,
    val title: String,
    val iconKey: String,
    val colorKey: String,
    val startedAt: Long,
    val endsAt: Long
)

data class Settings(
    /** 0 = follow system, 1 = 24 hour, 2 = 12 hour */
    val clockMode: Int = 0,
    /** minutes before a block starts to alert; 0 = off */
    val blockLeadMin: Int = 5,
    val dailySummary: Boolean = true,
    val summaryMinute: Int = 8 * 60,
    val lastSummaryDay: Long = -1L,
    /** keep an ongoing "next up" notification in the shade / lock screen */
    val nextUpOngoing: Boolean = false,
    /** show task titles on the lock screen */
    val lockScreenDetails: Boolean = false,
    val defaultBlockMin: Int = 45,
    val confetti: Boolean = true,
    val haptics: Boolean = true,
    val selectedProjectId: String? = null
)

data class AppData(
    val projects: List<Project> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val blocks: List<Block> = emptyList(),
    val focus: FocusSession? = null,
    val settings: Settings = Settings()
)
