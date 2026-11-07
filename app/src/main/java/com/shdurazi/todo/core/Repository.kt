package com.shdurazi.todo.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId
import java.util.UUID

/**
 * Single source of truth. Keeps [AppData] in memory (as a [StateFlow]) and writes it to one JSON file,
 * debounced and atomically. [onPersisted] is called after every write (must not block).
 */
class Repository(
    private val file: File,
    private val scope: CoroutineScope,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val saveDelayMs: Long = 400L,
    private val onPersisted: (AppData) -> Unit = {}
) {
    private val flow = MutableStateFlow(AppData())
    val state: StateFlow<AppData> get() = flow
    val data: AppData get() = flow.value

    private val saveLock = Any()
    private val ioLock = Any()
    private var saveJob: Job? = null
    private var loaded = false

    // ------------------------------------------------------------ persistence

    /** Loads the data file (or creates the sample data on first run). Safe to call more than once. */
    fun load() {
        synchronized(ioLock) {
            if (loaded) return
            val read = readFromDisk()
            flow.value = read ?: Seed.create(clock(), zone())
            loaded = true
            if (read == null) {
                try {
                    writeToDisk(flow.value)
                } catch (e: Exception) {
                    // will be retried on the next change
                }
            }
        }
    }

    private fun readFromDisk(): AppData? {
        if (!file.exists()) return null
        return try {
            Codec.decode(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            try {
                file.renameTo(File(file.parentFile, file.name + ".corrupt-" + clock()))
            } catch (ignored: Exception) {
            }
            null
        }
    }

    private fun writeToDisk(d: AppData) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(Codec.encode(d), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun persist() {
        val snapshot = flow.value
        synchronized(ioLock) {
            try {
                writeToDisk(snapshot)
            } catch (e: Exception) {
                // disk full / read-only: keep going, the in-memory state is still correct
            }
        }
        onPersisted(snapshot)
    }

    private fun scheduleSave() {
        synchronized(saveLock) {
            saveJob?.cancel()
            saveJob = scope.launch(Dispatchers.IO) {
                delay(saveDelayMs)
                persist()
            }
        }
    }

    /** Writes the current state immediately (blocking). */
    fun flush() {
        synchronized(saveLock) {
            saveJob?.cancel()
            saveJob = null
        }
        persist()
    }

    fun update(transform: (AppData) -> AppData) {
        flow.update(transform)
        scheduleSave()
    }

    fun replaceAll(newData: AppData) {
        update { newData }
    }

    fun exportJson(): String = Codec.encode(flow.value)

    fun newId(): String = UUID.randomUUID().toString()

    // ------------------------------------------------------------ tasks

    private fun remindAtFor(t: Task, now: Long): Long? {
        if (t.done || !t.remind) return null
        val day = t.dueDay ?: return null
        val min = t.dueMinute ?: return null
        val at = Planner.millisOf(day, min, zone())
        return if (at > now) at else null
    }

    fun addTask(
        title: String,
        projectId: String,
        day: Long? = null,
        minute: Int? = null,
        priority: Int = 0,
        later: Boolean = false,
        notes: String = "",
        remind: Boolean = true,
        blockId: String? = null
    ): Task {
        val now = clock()
        val nextOrder = maxOf(now, (flow.value.tasks.maxOfOrNull { it.order } ?: 0L) + 1L)
        val base = Task(
            id = newId(),
            projectId = projectId,
            title = title.trim(),
            notes = notes,
            priority = priority.coerceIn(0, 3),
            later = later,
            dueDay = day,
            dueMinute = if (day != null) minute else null,
            remind = remind,
            blockId = blockId,
            createdAt = now,
            order = nextOrder
        )
        val task = base.copy(remindAt = remindAtFor(base, now))
        update { it.copy(tasks = it.tasks + task) }
        return task
    }

    fun updateTask(id: String, f: (Task) -> Task) {
        val now = clock()
        update { d ->
            d.copy(tasks = d.tasks.map { old ->
                if (old.id != id) {
                    old
                } else {
                    var n = f(old)
                    if (n.dueDay == null && n.dueMinute != null) n = n.copy(dueMinute = null)
                    if (n.dueDay != old.dueDay || n.dueMinute != old.dueMinute ||
                        n.remind != old.remind || n.done != old.done
                    ) {
                        n = n.copy(remindAt = remindAtFor(n, now))
                    }
                    n
                }
            })
        }
    }

    /** Flips the done state; returns the new state. */
    fun toggleDone(id: String): Boolean {
        val now = clock()
        val current = flow.value.tasks.firstOrNull { it.id == id } ?: return false
        val nowDone = !current.done
        updateTask(id) { t -> t.copy(done = nowDone, doneAt = if (nowDone) now else null) }
        return nowDone
    }

    fun deleteTask(id: String) {
        update { d -> d.copy(tasks = d.tasks.filter { it.id != id }) }
    }

    /** Puts a previously deleted task back (undo). */
    fun restoreTask(task: Task) {
        update { d ->
            if (d.tasks.any { it.id == task.id }) d else d.copy(tasks = d.tasks + task)
        }
    }

    fun scheduleTask(taskId: String, blockId: String?) {
        updateTask(taskId) { it.copy(blockId = blockId) }
    }

    fun snoozeTask(taskId: String, minutes: Int) {
        val at = clock() + minutes * 60_000L
        update { d ->
            d.copy(tasks = d.tasks.map { if (it.id == taskId && !it.done) it.copy(remindAt = at) else it })
        }
    }

    fun clearReminder(taskId: String) {
        update { d -> d.copy(tasks = d.tasks.map { if (it.id == taskId) it.copy(remindAt = null) else it }) }
    }

    fun clearCompleted() {
        update { d -> d.copy(tasks = d.tasks.filter { !it.done }) }
    }

    // ------------------------------------------------------------ blocks

    private fun alertPassed(b: Block, lead: Int, now: Long): Boolean {
        if (lead <= 0 || !b.remind) return false
        return Planner.blockStart(b, zone()) - lead * 60_000L <= now
    }

    fun addBlock(b: Block): Block {
        val now = clock()
        val clamped = clampBlock(b)
        val nb = clamped.copy(
            id = if (clamped.id.isEmpty()) newId() else clamped.id,
            alerted = alertPassed(clamped, flow.value.settings.blockLeadMin, now)
        )
        update { it.copy(blocks = it.blocks + nb) }
        return nb
    }

    fun updateBlock(id: String, f: (Block) -> Block) {
        val now = clock()
        update { d ->
            d.copy(blocks = d.blocks.map { old ->
                if (old.id != id) {
                    old
                } else {
                    var n = clampBlock(f(old))
                    if (n.day != old.day || n.startMin != old.startMin || n.remind != old.remind) {
                        n = n.copy(alerted = alertPassed(n, d.settings.blockLeadMin, now))
                    }
                    n
                }
            })
        }
    }

    private fun clampBlock(b: Block): Block {
        val start = b.startMin.coerceIn(0, 1435)
        val dur = b.durationMin.coerceIn(5, 1440 - start)
        return b.copy(startMin = start, durationMin = dur)
    }

    fun deleteBlock(id: String) {
        update { d ->
            d.copy(
                blocks = d.blocks.filter { it.id != id },
                tasks = d.tasks.map { if (it.blockId == id) it.copy(blockId = null) else it }
            )
        }
    }

    fun markBlockAlerted(id: String) {
        update { d -> d.copy(blocks = d.blocks.map { if (it.id == id) it.copy(alerted = true) else it }) }
    }

    // ------------------------------------------------------------ projects

    fun addProject(name: String, colorKey: String, iconKey: String): Project {
        val p = Project(newId(), name.trim().ifEmpty { "Project" }, colorKey, iconKey)
        update { it.copy(projects = it.projects + p) }
        return p
    }

    fun updateProject(id: String, f: (Project) -> Project) {
        update { d -> d.copy(projects = d.projects.map { if (it.id == id) f(it) else it }) }
    }

    /** Deletes a project together with its tasks and timeline blocks. */
    fun deleteProject(id: String) {
        update { d ->
            val remaining = d.projects.filter { it.id != id }
            val goneBlocks = d.blocks.filter { it.projectId == id }.map { it.id }.toSet()
            val sel = d.settings.selectedProjectId
            val focusBlock = d.focus?.blockId
            d.copy(
                projects = remaining,
                tasks = d.tasks.filter { it.projectId != id },
                blocks = d.blocks.filter { it.projectId != id },
                focus = if (focusBlock != null && goneBlocks.contains(focusBlock)) null else d.focus,
                settings = if (sel == id) d.settings.copy(selectedProjectId = remaining.firstOrNull()?.id) else d.settings
            )
        }
    }

    // ------------------------------------------------------------ settings & focus

    fun updateSettings(f: (Settings) -> Settings) {
        update { it.copy(settings = f(it.settings)) }
    }

    fun selectProject(id: String) {
        updateSettings { it.copy(selectedProjectId = id) }
    }

    fun startFocus(session: FocusSession) {
        update { it.copy(focus = session) }
    }

    fun endFocus() {
        update { it.copy(focus = null) }
    }
}
