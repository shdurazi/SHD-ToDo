package com.shdurazi.todo.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shdurazi.todo.R
import com.shdurazi.todo.core.AppData
import com.shdurazi.todo.core.Block
import com.shdurazi.todo.core.Fmt
import com.shdurazi.todo.core.Keys
import com.shdurazi.todo.core.Planner
import com.shdurazi.todo.core.Queries
import com.shdurazi.todo.ui.Sheet
import com.shdurazi.todo.ui.UiState
import com.shdurazi.todo.ui.components.Glyph
import com.shdurazi.todo.ui.components.MonthCalendar
import com.shdurazi.todo.ui.components.pressable
import com.shdurazi.todo.ui.theme.Hues
import com.shdurazi.todo.ui.theme.Ink
import com.shdurazi.todo.ui.theme.Txt
import java.time.ZoneId

private fun accentOf(data: AppData) =
    Hues.of(Queries.project(data, data.settings.selectedProjectId)?.colorKey).accent

// ------------------------------------------------------------------------------------------------ task

/** Edits apply immediately (the title only while it is not empty); "Done" just closes the sheet. */
@Composable
fun TaskEditorSheet(ui: UiState, data: AppData, taskId: String, now: Long, h24: Boolean) {
    val repo = ui.repo
    val task = data.tasks.firstOrNull { it.id == taskId }
    if (task == null) {
        LaunchedEffect(taskId) { ui.sheet = null }
        return
    }
    val zone = remember { ZoneId.systemDefault() }
    val today = Planner.dayOf(now, zone)
    val project = Queries.project(data, task.projectId)
    val hue = Hues.of(project?.colorKey)
    var title by remember(taskId) { mutableStateOf(task.title) }
    var notes by remember(taskId) { mutableStateOf(task.notes) }
    var showWhen by remember(taskId) { mutableStateOf(false) }

    val dueDay = task.dueDay
    val dueMinute = task.dueMinute
    val whenLabel = if (dueDay == null) {
        "No date"
    } else {
        Fmt.dayLabel(dueDay, today) + (if (dueMinute != null) "  " + Fmt.clock(dueMinute, h24) else "")
    }
    val block = task.blockId?.let { id -> data.blocks.firstOrNull { it.id == id } }

    SheetTitle("To-do")
    FormGroup {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            SheetTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) repo.updateTask(taskId) { t -> t.copy(title = it.trim()) }
                },
                placeholder = "Title",
                accent = hue.accent,
                size = 21.sp
            )
        }
        GroupDivider()
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 44.dp)) {
            SheetTextField(
                value = notes,
                onValueChange = {
                    notes = it
                    repo.updateTask(taskId) { t -> t.copy(notes = it) }
                },
                placeholder = "Notes",
                accent = hue.accent,
                size = 16.sp,
                weight = FontWeight.Medium,
                color = Ink.TextDim,
                singleLine = false
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    FormGroup {
        ValueRow(
            label = "When",
            value = whenLabel,
            valueColor = if (dueDay != null) hue.accent else Ink.TextDim,
            onTap = { showWhen = !showWhen }
        )
        if (showWhen) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                WhenPickerContent(
                    day = dueDay,
                    minute = dueMinute,
                    today = today,
                    accent = hue.accent,
                    h24 = h24,
                    onChange = { d, m ->
                        repo.updateTask(taskId) { t -> t.copy(dueDay = d, dueMinute = if (d != null) m else null) }
                    }
                )
            }
        }
        if (dueDay != null && dueMinute != null) {
            GroupDivider()
            SwitchRow("Remind me", task.remind, hue.accent) { on ->
                repo.updateTask(taskId) { t -> t.copy(remind = on) }
            }
        }
        GroupDivider()
        SwitchRow("Nice to do", task.later, hue.accent) { on ->
            repo.updateTask(taskId) { t -> t.copy(later = on) }
        }
        if (block != null) {
            GroupDivider()
            ValueRow(
                label = "On the timeline",
                value = Fmt.clock(block.startMin, h24) + " · " + block.title,
                onTap = { repo.scheduleTask(taskId, null) }
            )
        }
    }

    SectionLabel("Priority")
    ChipRow {
        val levels = listOf("None" to 0, "!" to 1, "!!" to 2, "!!!" to 3)
        for ((label, level) in levels) {
            ChoiceChip(label, task.priority == level, hue.accent) {
                repo.updateTask(taskId) { t -> t.copy(priority = level) }
            }
        }
    }

    if (data.projects.size > 1) {
        SectionLabel("Project")
        ChipRow {
            for (p in data.projects) {
                ChoiceChip(p.name, p.id == task.projectId, Hues.of(p.colorKey).accent) {
                    repo.updateTask(taskId) { t -> t.copy(projectId = p.id) }
                }
            }
        }
    }

    ButtonRow {
        SheetButton(
            text = "Delete",
            fill = Ink.DangerFill,
            textColor = Ink.Danger,
            modifier = Modifier.weight(1f)
        ) {
            val removed = task
            repo.deleteTask(taskId)
            ui.sheet = null
            ui.showToast("Deleted", "Undo") { repo.restoreTask(removed) }
        }
        SheetButton(
            text = "Done",
            fill = hue.accent,
            textColor = Color.Black,
            modifier = Modifier.weight(1f)
        ) { ui.sheet = null }
    }
}

// ------------------------------------------------------------------------------------------------ block

@Composable
private fun StepButton(glyph: Int, label: String, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .pressable(pressedScale = 0.88f, label = label) { onTap() }
            .clip(CircleShape)
            .background(Ink.CardLift),
        contentAlignment = Alignment.Center
    ) {
        Glyph(glyph, 20.dp, Ink.Text)
    }
}

/** Creates a timeline block (from a long-press or a drop) or edits an existing one. */
@Composable
fun BlockEditorSheet(
    ui: UiState,
    data: AppData,
    sheet: Sheet,
    now: Long,
    h24: Boolean,
    onStartFocus: (Block) -> Unit
) {
    val repo = ui.repo
    val existing = (sheet as? Sheet.EditBlock)?.let { s -> data.blocks.firstOrNull { it.id == s.blockId } }
    if (sheet is Sheet.EditBlock && existing == null) {
        LaunchedEffect(sheet) { ui.sheet = null }
        return
    }
    val fresh = sheet as? Sheet.NewBlock
    val zone = remember { ZoneId.systemDefault() }
    val today = Planner.dayOf(now, zone)
    val linked = fresh?.taskId?.let { id -> data.tasks.firstOrNull { it.id == id } }
    val project = Queries.project(data, linked?.projectId ?: data.settings.selectedProjectId)
        ?: data.projects.firstOrNull()

    var title by remember(sheet) { mutableStateOf(existing?.title ?: linked?.title ?: "") }
    var iconKey by remember(sheet) { mutableStateOf(existing?.iconKey ?: project?.iconKey ?: "bulb") }
    var colorKey by remember(sheet) { mutableStateOf(existing?.colorKey ?: project?.colorKey ?: "blue") }
    var day by remember(sheet) { mutableLongStateOf(existing?.day ?: fresh?.day ?: today) }
    var startMin by remember(sheet) { mutableIntStateOf(existing?.startMin ?: fresh?.startMin ?: (9 * 60)) }
    var duration by remember(sheet) { mutableIntStateOf(existing?.durationMin ?: data.settings.defaultBlockMin) }
    var remind by remember(sheet) { mutableStateOf(existing?.remind ?: true) }
    val hue = Hues.of(colorKey)

    fun draft(id: String): Block = Block(
        id = id,
        projectId = existing?.projectId ?: project?.id ?: data.projects.firstOrNull()?.id ?: "",
        title = title.trim().ifEmpty { "Focus" },
        iconKey = iconKey,
        colorKey = colorKey,
        day = day,
        startMin = startMin,
        durationMin = duration,
        remind = remind
    )

    fun save(): Block {
        return if (existing == null) {
            val created = repo.addBlock(draft(""))
            if (linked != null) repo.scheduleTask(linked.id, created.id)
            created
        } else {
            repo.updateBlock(existing.id) { old ->
                old.copy(
                    title = title.trim().ifEmpty { "Focus" },
                    iconKey = iconKey,
                    colorKey = colorKey,
                    day = day,
                    startMin = startMin,
                    durationMin = duration,
                    remind = remind
                )
            }
            repo.state.value.blocks.firstOrNull { it.id == existing.id } ?: existing
        }
    }

    SheetTitle(if (existing == null) "New block" else "Edit block")
    FormGroup {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            SheetTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = "Deep work",
                accent = hue.accent,
                size = 21.sp,
                autoFocus = existing == null
            )
        }
    }

    SectionLabel("Time")
    FormGroup {
        Row(
            modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).pressable(pressedScale = 0.88f, label = "Previous day") { day -= 1L },
                contentAlignment = Alignment.Center
            ) { Glyph(R.drawable.ic_ui_chevron_left, 22.dp, Ink.TextDim) }
            Txt(
                text = Fmt.dayLabel(day, today),
                size = 17.sp,
                weight = FontWeight.SemiBold,
                align = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier.size(44.dp).pressable(pressedScale = 0.88f, label = "Next day") { day += 1L },
                contentAlignment = Alignment.Center
            ) { Glyph(R.drawable.ic_ui_chevron_right, 22.dp, Ink.TextDim) }
        }
        GroupDivider()
        TimeWheels(
            minute = startMin,
            h24 = h24,
            onChange = { startMin = it },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }

    SectionLabel("Length")
    ChipRow {
        for (m in listOf(15, 30, 45, 60, 90, 120, 180)) {
            ChoiceChip(Fmt.duration(m), duration == m, hue.accent) { duration = m }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    FormGroup {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Txt(text = "Exactly", size = 17.sp, modifier = Modifier.weight(1f))
            StepButton(R.drawable.ic_ui_minus, "Shorter") { duration = (duration - 5).coerceAtLeast(5) }
            Txt(
                text = Fmt.duration(duration),
                size = 17.sp,
                weight = FontWeight.SemiBold,
                align = TextAlign.Center,
                modifier = Modifier.width(88.dp)
            )
            StepButton(R.drawable.ic_ui_plus, "Longer") { duration = (duration + 5).coerceAtMost(12 * 60) }
        }
    }

    SectionLabel("Colour")
    ColorDots(selected = colorKey, onPick = { colorKey = it })
    SectionLabel("Icon")
    IconGrid(selected = iconKey, accent = hue.accent, onPick = { iconKey = it })

    Spacer(modifier = Modifier.height(18.dp))
    FormGroup {
        SwitchRow("Alert before it starts", remind, hue.accent) { remind = it }
    }

    if (existing != null) {
        val tasks = Queries.tasksOfBlock(data, existing.id)
        SectionLabel(if (tasks.isEmpty()) "To-dos" else "To-dos (${tasks.size})")
        FormGroup {
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Txt(text = "Drag a to-do onto this block to schedule it", size = 15.sp, color = Ink.TextDim)
                }
            } else {
                for ((i, t) in tasks.withIndex()) {
                    if (i > 0) GroupDivider()
                    ValueRow(
                        label = t.title,
                        value = "Remove",
                        valueColor = Ink.TextDim,
                        chevron = false,
                        onTap = { repo.scheduleTask(t.id, null) }
                    )
                }
            }
        }
    }

    ButtonRow {
        if (existing != null) {
            SheetButton(
                text = "Delete",
                fill = Ink.DangerFill,
                textColor = Ink.Danger,
                modifier = Modifier.weight(1f)
            ) {
                repo.deleteBlock(existing.id)
                ui.sheet = null
                ui.showToast("Block deleted")
            }
            SheetButton(
                text = "Focus",
                fill = Ink.Button,
                textColor = Ink.Text,
                modifier = Modifier.weight(1f)
            ) {
                val saved = save()
                ui.sheet = null
                onStartFocus(saved)
            }
        } else {
            SheetButton(
                text = "Cancel",
                fill = Ink.Button,
                textColor = Ink.Text,
                modifier = Modifier.weight(1f)
            ) { ui.sheet = null }
        }
        SheetButton(
            text = if (existing == null) "Add" else "Save",
            fill = hue.accent,
            textColor = Color.Black,
            modifier = Modifier.weight(1f)
        ) {
            save()
            ui.sheet = null
        }
    }
}

// ------------------------------------------------------------------------------------------------ project

@Composable
fun ProjectEditorSheet(ui: UiState, data: AppData, projectId: String?) {
    val repo = ui.repo
    val existing = projectId?.let { id -> data.projects.firstOrNull { it.id == id } }
    if (projectId != null && existing == null) {
        LaunchedEffect(projectId) { ui.sheet = null }
        return
    }
    var name by remember(projectId) { mutableStateOf(existing?.name ?: "") }
    var colorKey by remember(projectId) {
        mutableStateOf(existing?.colorKey ?: Keys.colors[data.projects.size % Keys.colors.size])
    }
    var iconKey by remember(projectId) { mutableStateOf(existing?.iconKey ?: "briefcase") }
    var confirmDelete by remember(projectId) { mutableStateOf(false) }
    val hue = Hues.of(colorKey)

    fun save() {
        val clean = name.trim().ifEmpty { "Project" }
        if (existing == null) {
            val created = repo.addProject(clean, colorKey, iconKey)
            repo.selectProject(created.id)
        } else {
            repo.updateProject(existing.id) { it.copy(name = clean, colorKey = colorKey, iconKey = iconKey) }
        }
        ui.sheet = null
    }

    SheetTitle(if (existing == null) "New project" else "Edit project")
    FormGroup {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            SheetTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Project name",
                accent = hue.accent,
                size = 21.sp,
                autoFocus = existing == null,
                onDone = { save() }
            )
        }
    }
    SectionLabel("Colour")
    ColorDots(selected = colorKey, onPick = { colorKey = it })
    SectionLabel("Icon")
    IconGrid(selected = iconKey, accent = hue.accent, onPick = { iconKey = it })

    ButtonRow {
        if (existing != null && data.projects.size > 1) {
            val count = data.tasks.count { it.projectId == existing.id }
            SheetButton(
                text = if (confirmDelete) "Tap again" else "Delete",
                fill = if (confirmDelete) Ink.Danger else Ink.DangerFill,
                textColor = if (confirmDelete) Color.White else Ink.Danger,
                modifier = Modifier.weight(1f)
            ) {
                if (!confirmDelete) {
                    confirmDelete = true
                } else {
                    repo.deleteProject(existing.id)
                    ui.sheet = null
                    ui.showToast(
                        if (count == 0) "Project deleted" else "Project and $count to-dos deleted"
                    )
                }
            }
        }
        SheetButton(
            text = if (existing == null) "Add" else "Save",
            fill = hue.accent,
            textColor = Color.Black,
            modifier = Modifier.weight(1f)
        ) { save() }
    }
    if (confirmDelete && existing != null) {
        val count = data.tasks.count { it.projectId == existing.id }
        Txt(
            text = if (count == 0) {
                "This removes the project and its timeline blocks."
            } else {
                "This removes the project, its $count to-dos and its timeline blocks."
            },
            size = 13.sp,
            color = Ink.TextDim,
            maxLines = 3,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp)
        )
    }
}

// ------------------------------------------------------------------------------------------------ day

@Composable
fun DayPickerSheet(ui: UiState, data: AppData, now: Long) {
    val zone = remember { ZoneId.systemDefault() }
    val today = Planner.dayOf(now, zone)
    val accent = accentOf(data)
    SheetTitle("Go to date")
    MonthCalendar(
        selected = ui.day,
        today = today,
        accent = accent,
        onPick = {
            ui.day = it
            ui.sheet = null
        }
    )
    ButtonRow {
        SheetButton(
            text = "Today",
            fill = accent,
            textColor = Color.Black,
            modifier = Modifier.weight(1f)
        ) {
            ui.day = today
            ui.sheet = null
        }
    }
}
