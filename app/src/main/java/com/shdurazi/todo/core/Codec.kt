package com.shdurazi.todo.core

/** Converts [AppData] to / from the JSON used for storage and backups. */
object Codec {
    const val VERSION = 1

    fun encode(d: AppData): String = Json.stringify(toTree(d))

    fun decode(text: String): AppData {
        val root = obj(Json.parse(text)) ?: throw IllegalArgumentException("Not a ToDo backup")
        return fromTree(root)
    }

    // ---------------------------------------------------------------- encode

    private fun tree(vararg pairs: Pair<String, Any?>): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in pairs) if (v != null) out[k] = v
        return out
    }

    fun toTree(d: AppData): Map<String, Any?> = tree(
        "app" to "todo",
        "version" to VERSION,
        "projects" to d.projects.map { p ->
            tree("id" to p.id, "name" to p.name, "color" to p.colorKey, "icon" to p.iconKey)
        },
        "tasks" to d.tasks.map { t ->
            tree(
                "id" to t.id,
                "project" to t.projectId,
                "title" to t.title,
                "notes" to t.notes,
                "priority" to t.priority,
                "later" to t.later,
                "dueDay" to t.dueDay,
                "dueMinute" to t.dueMinute,
                "remind" to t.remind,
                "remindAt" to t.remindAt,
                "block" to t.blockId,
                "done" to t.done,
                "doneAt" to t.doneAt,
                "createdAt" to t.createdAt,
                "order" to t.order
            )
        },
        "blocks" to d.blocks.map { b ->
            tree(
                "id" to b.id,
                "project" to b.projectId,
                "title" to b.title,
                "icon" to b.iconKey,
                "color" to b.colorKey,
                "day" to b.day,
                "start" to b.startMin,
                "duration" to b.durationMin,
                "remind" to b.remind,
                "alerted" to b.alerted
            )
        },
        "focus" to d.focus?.let { f ->
            tree(
                "block" to f.blockId,
                "title" to f.title,
                "icon" to f.iconKey,
                "color" to f.colorKey,
                "startedAt" to f.startedAt,
                "endsAt" to f.endsAt
            )
        },
        "settings" to d.settings.let { s ->
            tree(
                "clockMode" to s.clockMode,
                "blockLeadMin" to s.blockLeadMin,
                "dailySummary" to s.dailySummary,
                "summaryMinute" to s.summaryMinute,
                "lastSummaryDay" to s.lastSummaryDay,
                "nextUpOngoing" to s.nextUpOngoing,
                "lockScreenDetails" to s.lockScreenDetails,
                "defaultBlockMin" to s.defaultBlockMin,
                "confetti" to s.confetti,
                "haptics" to s.haptics,
                "selectedProjectId" to s.selectedProjectId
            )
        }
    )

    // ---------------------------------------------------------------- decode

    @Suppress("UNCHECKED_CAST")
    private fun obj(v: Any?): Map<String, Any?>? = if (v is Map<*, *>) v as Map<String, Any?> else null

    private fun list(v: Any?): List<Any?> = (v as? List<*>) ?: emptyList<Any?>()

    private fun Map<String, Any?>.str(k: String, d: String = ""): String = (this[k] as? String) ?: d
    private fun Map<String, Any?>.strOrNull(k: String): String? = this[k] as? String
    private fun Map<String, Any?>.int(k: String, d: Int = 0): Int = (this[k] as? Number)?.toInt() ?: d
    private fun Map<String, Any?>.long(k: String, d: Long = 0L): Long = (this[k] as? Number)?.toLong() ?: d
    private fun Map<String, Any?>.intOrNull(k: String): Int? = (this[k] as? Number)?.toInt()
    private fun Map<String, Any?>.longOrNull(k: String): Long? = (this[k] as? Number)?.toLong()
    private fun Map<String, Any?>.bool(k: String, d: Boolean = false): Boolean = (this[k] as? Boolean) ?: d

    fun fromTree(root: Map<String, Any?>): AppData {
        val projects = list(root["projects"]).mapNotNull { obj(it) }.mapNotNull { m ->
            val id = m.str("id")
            if (id.isEmpty()) null
            else Project(id, m.str("name", "Project"), m.str("color", "green"), m.str("icon", "briefcase"))
        }
        val tasks = list(root["tasks"]).mapNotNull { obj(it) }.mapNotNull { m ->
            val id = m.str("id")
            if (id.isEmpty()) null
            else Task(
                id = id,
                projectId = m.str("project"),
                title = m.str("title"),
                notes = m.str("notes"),
                priority = m.int("priority").coerceIn(0, 3),
                later = m.bool("later"),
                dueDay = m.longOrNull("dueDay"),
                dueMinute = m.intOrNull("dueMinute"),
                remind = m.bool("remind", true),
                remindAt = m.longOrNull("remindAt"),
                blockId = m.strOrNull("block"),
                done = m.bool("done"),
                doneAt = m.longOrNull("doneAt"),
                createdAt = m.long("createdAt"),
                order = m.long("order")
            )
        }
        val blocks = list(root["blocks"]).mapNotNull { obj(it) }.mapNotNull { m ->
            val id = m.str("id")
            if (id.isEmpty()) null
            else Block(
                id = id,
                projectId = m.str("project"),
                title = m.str("title"),
                iconKey = m.str("icon", "bulb"),
                colorKey = m.str("color", "blue"),
                day = m.long("day"),
                startMin = m.int("start").coerceIn(0, 1439),
                durationMin = m.int("duration", 45).coerceIn(5, 1440),
                remind = m.bool("remind", true),
                alerted = m.bool("alerted")
            )
        }
        val focus = obj(root["focus"])?.let { m ->
            val endsAt = m.long("endsAt")
            if (endsAt <= 0L) null
            else FocusSession(
                blockId = m.strOrNull("block"),
                title = m.str("title"),
                iconKey = m.str("icon", "bulb"),
                colorKey = m.str("color", "blue"),
                startedAt = m.long("startedAt"),
                endsAt = endsAt
            )
        }
        val sm = obj(root["settings"]) ?: emptyMap()
        val d = Settings()
        val settings = Settings(
            clockMode = sm.int("clockMode", d.clockMode).coerceIn(0, 2),
            blockLeadMin = sm.int("blockLeadMin", d.blockLeadMin).coerceIn(0, 120),
            dailySummary = sm.bool("dailySummary", d.dailySummary),
            summaryMinute = sm.int("summaryMinute", d.summaryMinute).coerceIn(0, 1439),
            lastSummaryDay = sm.long("lastSummaryDay", d.lastSummaryDay),
            nextUpOngoing = sm.bool("nextUpOngoing", d.nextUpOngoing),
            lockScreenDetails = sm.bool("lockScreenDetails", d.lockScreenDetails),
            defaultBlockMin = sm.int("defaultBlockMin", d.defaultBlockMin).coerceIn(5, 480),
            confetti = sm.bool("confetti", d.confetti),
            haptics = sm.bool("haptics", d.haptics),
            selectedProjectId = sm.strOrNull("selectedProjectId")
        )
        return AppData(projects, tasks, blocks, focus, settings)
    }
}
