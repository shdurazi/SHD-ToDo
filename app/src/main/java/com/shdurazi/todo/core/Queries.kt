package com.shdurazi.todo.core

/** Read-only helpers over [AppData] shared by the UI, widgets and notifications. */
object Queries {
    fun project(d: AppData, id: String?): Project? = d.projects.firstOrNull { it.id == id }

    fun blocksOn(d: AppData, day: Long): List<Block> =
        d.blocks.filter { it.day == day }.sortedWith(compareBy<Block>({ it.startMin }, { it.durationMin }))

    fun tasksOfBlock(d: AppData, blockId: String): List<Task> =
        d.tasks.filter { it.blockId == blockId }.sortedBy { it.order }

    fun openTasks(d: AppData, projectId: String?): List<Task> =
        d.tasks.filter { !it.done && (projectId == null || it.projectId == projectId) }.sortedBy { it.order }

    fun doneTasks(d: AppData, projectId: String?): List<Task> =
        d.tasks.filter { it.done && (projectId == null || it.projectId == projectId) }
            .sortedByDescending { it.doneAt ?: 0L }

    /** The block that contains [minute] on [day], otherwise the next one that starts after it. */
    fun currentOrNext(d: AppData, day: Long, minute: Int): Block? {
        val list = blocksOn(d, day)
        return list.firstOrNull { minute >= it.startMin && minute < it.endMin }
            ?: list.firstOrNull { it.startMin > minute }
    }

    private fun rank(t: Task, today: Long): Int {
        val due = t.dueDay
        return if (due != null && due < today) 0 else if (due != null && due == today) 1 else 2
    }

    /** Open tasks for widgets: overdue and due-today first (by time), then by priority and creation order. */
    fun focusTasks(d: AppData, today: Long, limit: Int): List<Task> {
        val open = d.tasks.filter { !it.done }
        val sorted = open.sortedWith(
            compareBy<Task>(
                { rank(it, today) },
                { if (rank(it, today) <= 1) (it.dueMinute ?: 1440) else 0 },
                { -it.priority },
                { it.order }
            )
        )
        return sorted.take(limit)
    }

    /** Number of open tasks that are due today / overdue or scheduled into one of today's blocks. */
    fun todayTaskCount(d: AppData, today: Long): Int {
        val todayBlocks = d.blocks.filter { it.day == today }.map { it.id }.toSet()
        return d.tasks.count { t ->
            if (t.done) false
            else {
                val due = t.dueDay
                (due != null && due <= today) || (t.blockId != null && todayBlocks.contains(t.blockId))
            }
        }
    }
}
