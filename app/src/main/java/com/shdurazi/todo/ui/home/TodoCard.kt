package com.shdurazi.todo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shdurazi.todo.R
import com.shdurazi.todo.core.AppData
import com.shdurazi.todo.core.Fmt
import com.shdurazi.todo.core.Planner
import com.shdurazi.todo.core.Queries
import com.shdurazi.todo.core.Task
import com.shdurazi.todo.ui.Sheet
import com.shdurazi.todo.ui.UiState
import com.shdurazi.todo.ui.components.CheckRing
import com.shdurazi.todo.ui.components.Glyph
import com.shdurazi.todo.ui.components.pressable
import com.shdurazi.todo.ui.theme.Fonts
import com.shdurazi.todo.ui.theme.Hue
import com.shdurazi.todo.ui.theme.Hues
import com.shdurazi.todo.ui.theme.Ink
import com.shdurazi.todo.ui.theme.Txt
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ROW_H = 42.dp
private val CARD_RADIUS = 30.dp

/**
 * The dark card with the open to-dos of the selected project. It sits at the bottom of the area above the
 * project bar and grows upwards (under the white timeline card); when there is more than fits it scrolls.
 */
@Composable
fun TodoCard(
    ui: UiState,
    data: AppData,
    now: Long,
    h24: Boolean,
    timelinePx: () -> Float,
    collapsedTopPx: Float,
    overlapPx: Float,
    onContentHeight: (Float) -> Unit,
    onDrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repo = ui.repo
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val zone = remember { ZoneId.systemDefault() }
    val today = Planner.dayOf(now, zone)
    val nowMinute = Planner.minuteOf(now, zone)

    val project = Queries.project(data, data.settings.selectedProjectId) ?: data.projects.firstOrNull()
    val hue = Hues.of(project?.colorKey)
    val accentKey = project?.colorKey ?: "green"
    val projectId = project?.id

    val open = remember(data, projectId) { Queries.openTasks(data, projectId) }
    val done = remember(data, projectId) { Queries.doneTasks(data, projectId) }
    val todo = remember(open) { open.filter { !it.later } }
    val later = remember(open) { open.filter { it.later } }

    // rows whose ring was just ticked: they look done for a moment before they really move
    val pending = remember { mutableStateListOf<String>() }
    val scroll = rememberScrollState()

    fun toggle(t: Task) {
        if (t.done) {
            repo.toggleDone(t.id)
            return
        }
        if (pending.contains(t.id)) return
        pending.add(t.id)
        scope.launch {
            delay(380)
            val nowDone = repo.toggleDone(t.id)
            pending.remove(t.id)
            if (nowDone) {
                val d = repo.state.value
                if (d.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (d.settings.confetti && Queries.openTasks(d, t.projectId).isEmpty()) ui.burst()
                ui.showToast("Completed", "Undo") { repo.toggleDone(t.id) }
            }
        }
    }

    fun submit() {
        QuickAdd.commit(ui, repo.state.value, now)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val topLimit = maxOf(timelinePx() - overlapPx, collapsedTopPx)
                    val maxH = (constraints.maxHeight - topLimit).coerceAtLeast(0f).roundToInt()
                    val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = maxH))
                    layout(p.width, p.height) { p.place(0, 0) }
                }
                .clip(RoundedCornerShape(CARD_RADIUS))
                .background(Ink.Card)
                .verticalScroll(scroll, reverseScrolling = true)
                .onSizeChanged { onContentHeight(it.height.toFloat()) }
                .padding(top = 14.dp, bottom = 10.dp)
        ) {
            if (done.isNotEmpty()) {
                CompletedHeader(count = done.size, expanded = ui.showCompleted) {
                    ui.showCompleted = !ui.showCompleted
                }
                if (ui.showCompleted) {
                    for (t in done) {
                        androidx.compose.runtime.key(t.id) {
                            TaskRow(ui, t, hue, accentKey, true, Fmt.dueMeta(t, today, nowMinute, h24), { toggle(t) }, onDrop)
                        }
                    }
                }
            }
            if (todo.isNotEmpty()) {
                SectionHeader("To Do")
                for (t in todo) {
                    androidx.compose.runtime.key(t.id) {
                        TaskRow(
                            ui, t, hue, accentKey, pending.contains(t.id), Fmt.dueMeta(t, today, nowMinute, h24),
                            { toggle(t) }, onDrop
                        )
                    }
                }
            }
            if (later.isNotEmpty()) {
                SectionHeader("Nice to do")
                for (t in later) {
                    androidx.compose.runtime.key(t.id) {
                        TaskRow(
                            ui, t, hue, accentKey, pending.contains(t.id), Fmt.dueMeta(t, today, nowMinute, h24),
                            { toggle(t) }, onDrop
                        )
                    }
                }
            }
            NewTodoRow(ui, data, hue) { submit() }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Txt(text = title, color = Ink.TextDim, size = 13.sp, weight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
                .height(1.dp)
                .background(Ink.Hairline)
        )
    }
}

@Composable
private fun CompletedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .pressable(pressedScale = 0.99f, label = "Completed tasks") { onToggle() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Txt(text = "Completed", color = Ink.TextDim, size = 13.sp, weight = FontWeight.SemiBold)
        Txt(
            text = "  $count",
            color = Ink.TextFaint,
            size = 13.sp,
            weight = FontWeight.SemiBold
        )
        Glyph(
            res = R.drawable.ic_ui_chevron_down,
            size = 16.dp,
            tint = Ink.TextDim,
            modifier = Modifier.padding(start = 4.dp).rotate(if (expanded) 180f else 0f)
        )
    }
}

@Composable
private fun TaskRow(
    ui: UiState,
    task: Task,
    hue: Hue,
    accentKey: String,
    checked: Boolean,
    meta: Fmt.Meta?,
    onRing: () -> Unit,
    onDrop: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val suppressTap = remember { booleanArrayOf(false) }
    val latestDrop by rememberUpdatedState(onDrop)
    val latestTask by rememberUpdatedState(task)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_H)
            .graphicsLayer { alpha = if (ui.drag.active && ui.drag.taskId == task.id) 0.25f else 1f }
            .onGloballyPositioned { coords[0] = it }
            .pointerInput(task.id) {
                detectTapGestures(
                    onPress = {
                        suppressTap[0] = false
                        tryAwaitRelease()
                    },
                    onTap = {
                        if (!suppressTap[0]) ui.sheet = Sheet.EditTask(latestTask.id)
                    }
                )
            }
            .pointerInput(task.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val c = coords[0]
                        if (c != null && c.isAttached) {
                            suppressTap[0] = true
                            if (ui.repo.state.value.settings.haptics) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            ui.drag.begin(
                                id = latestTask.id,
                                text = latestTask.title,
                                colorKey = accentKey,
                                start = c.localToRoot(offset),
                                widthPx = c.size.width.toFloat(),
                                grabOffset = offset
                            )
                        }
                    },
                    onDrag = { change, _ ->
                        val c = coords[0]
                        if (c != null && c.isAttached) ui.drag.moveTo(c.localToRoot(change.position))
                    },
                    onDragEnd = { latestDrop() },
                    onDragCancel = { ui.drag.end() }
                )
            }
            .padding(start = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(ROW_H)
                .pressable(pressedScale = 0.86f, label = "Complete ${task.title}") { onRing() },
            contentAlignment = Alignment.Center
        ) {
            CheckRing(checked = checked, accent = hue.accent, size = 21.dp)
        }
        Txt(
            text = task.title,
            modifier = Modifier.weight(1f),
            color = if (checked) Ink.TextDim else Ink.Text,
            size = 17.sp,
            strike = checked
        )
        if (meta != null) {
            val bottom = meta.bottom
            Column(
                modifier = Modifier.padding(start = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Txt(
                    text = meta.top,
                    size = 11.sp,
                    color = if (meta.overdue && !checked) Ink.Danger else Ink.TextDim,
                    weight = FontWeight.Medium
                )
                if (bottom != null) {
                    Txt(text = bottom, size = 11.sp, color = Ink.TextFaint, weight = FontWeight.Medium)
                }
            }
        }
        if (task.priority > 0) {
            Txt(
                text = Fmt.priorityMark(task.priority),
                size = 16.sp,
                weight = FontWeight.Bold,
                color = hue.accent,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun NewTodoRow(ui: UiState, data: AppData, hue: Hue, onCommit: () -> Unit) {
    val q = ui.quick
    val highlight = remember(data.projects, hue) { QuickHighlight(data.projects, hue.accent) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_H + 2.dp)
            .padding(start = 6.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(ROW_H)
                .pointerInput(Unit) { detectTapGestures { q.focusRequester.requestFocus() } },
            contentAlignment = Alignment.Center
        ) {
            if (q.focused) {
                CheckRing(checked = false, accent = hue.accent, size = 21.dp, ringColor = hue.accent)
            } else {
                Glyph(R.drawable.ic_ui_plus_circle, 21.dp, hue.accent)
            }
        }
        BasicTextField(
            value = q.text,
            onValueChange = { q.text = it },
            modifier = Modifier
                .weight(1f)
                .focusRequester(q.focusRequester)
                .onFocusChanged { q.focused = it.isFocused },
            textStyle = TextStyle(
                color = Ink.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = Fonts.Inter,
                letterSpacing = (-0.2).sp
            ),
            singleLine = true,
            cursorBrush = SolidColor(hue.accent),
            visualTransformation = highlight,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (q.text.text.isEmpty()) {
                        Txt(text = "New To Do", color = hue.accent, size = 17.sp)
                    }
                    inner()
                }
            }
        )
    }
}
