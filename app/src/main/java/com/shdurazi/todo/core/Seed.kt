package com.shdurazi.todo.core

import java.time.ZoneId

/** Friendly sample content shown on the very first launch. */
object Seed {
    fun create(nowMs: Long, zone: ZoneId): AppData {
        val today = Planner.dayOf(nowMs, zone)
        val work = Project("p-work", "Work", "green", "briefcase")
        val personal = Project("p-personal", "Personal", "orange", "person")

        val blocks = listOf(
            Block("b-deep", work.id, "Deep work", "bulb", "blue", today, 10 * 60, 90, remind = true, alerted = true),
            Block("b-lunch", work.id, "Lunch", "fork", "yellow", today, 13 * 60, 45, remind = false, alerted = true),
            Block("b-wrap", work.id, "Wrap up", "check", "teal", today, 16 * 60, 30, remind = true, alerted = true)
        )

        var order = nowMs
        fun task(
            id: String,
            project: Project,
            title: String,
            later: Boolean = false,
            blockId: String? = null,
            priority: Int = 0
        ): Task {
            order += 1
            return Task(
                id = id,
                projectId = project.id,
                title = title,
                priority = priority,
                later = later,
                blockId = blockId,
                createdAt = nowMs,
                order = order
            )
        }

        val tasks = listOf(
            task("t-1", work, "Long-press a task and drag it onto a block", blockId = "b-deep"),
            task("t-2", work, "Tap a block to start a focus session", blockId = "b-deep"),
            task("t-3", work, "Try typing: Call Sam tomorrow 6pm !!"),
            task("t-4", work, "Drag a task to the bin to delete it", later = true),
            task("t-5", personal, "Plan the weekend"),
            task("t-6", personal, "Buy groceries", later = true)
        )

        return AppData(
            projects = listOf(work, personal),
            tasks = tasks,
            blocks = blocks,
            focus = null,
            settings = Settings(selectedProjectId = work.id)
        )
    }
}
