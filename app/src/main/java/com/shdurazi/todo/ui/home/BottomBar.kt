package com.shdurazi.todo.ui.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shdurazi.todo.R
import com.shdurazi.todo.core.AppData
import com.shdurazi.todo.core.Project
import com.shdurazi.todo.core.Queries
import com.shdurazi.todo.ui.Res
import com.shdurazi.todo.ui.Sheet
import com.shdurazi.todo.ui.UiState
import com.shdurazi.todo.ui.components.Badge
import com.shdurazi.todo.ui.components.Glyph
import com.shdurazi.todo.ui.components.pressable
import com.shdurazi.todo.ui.theme.Hue
import com.shdurazi.todo.ui.theme.Hues
import com.shdurazi.todo.ui.theme.Ink
import com.shdurazi.todo.ui.theme.Txt
import kotlin.math.abs
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

val BOTTOM_BAR_H = 80.dp
private val SIDE = 20.dp

/**
 * The strip under the cards. It shows the project switcher; while a to-do is being typed it turns into the
 * toolbar above the keyboard, and while a to-do is being dragged it becomes the delete zone.
 */
@Composable
fun BottomBar(ui: UiState, data: AppData, now: Long, modifier: Modifier = Modifier) {
    val mode = when {
        ui.drag.active -> 2
        ui.quick.focused -> 1
        else -> 0
    }
    Box(
        modifier = modifier.fillMaxWidth().height(BOTTOM_BAR_H),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = mode, animationSpec = tween(170), label = "bar") { m ->
            when (m) {
                2 -> DeleteZone(ui)
                1 -> QuickToolbar(ui, data, now)
                else -> ProjectSwitcher(ui, data)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- projects

private fun startIndex(state: LazyListState): Int {
    val items = state.layoutInfo.visibleItemsInfo
    if (items.isEmpty()) return -1
    return items.minByOrNull { abs(it.offset) }?.index ?: -1
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectSwitcher(ui: UiState, data: AppData) {
    val projects = data.projects
    val selectedId = data.settings.selectedProjectId
    val selectedIndex = projects.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Start)
    val latestProjects by rememberUpdatedState(projects)
    val latestSelected by rememberUpdatedState(selectedId)
    val width = LocalConfiguration.current.screenWidthDp.dp

    // bring the selected project to the start whenever it changes from elsewhere (tap, toolbar, new project)
    LaunchedEffect(selectedIndex, projects.size) {
        if (projects.isNotEmpty() && startIndex(state) != selectedIndex) {
            state.animateScrollToItem(selectedIndex)
        }
    }
    // after the user has swiped, the project that came to rest at the start becomes the selected one
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .drop(1)
            .filter { !it }
            .collect {
                val p = latestProjects.getOrNull(startIndex(state))
                if (p != null && p.id != latestSelected) ui.repo.selectProject(p.id)
            }
    }

    LazyRow(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(start = SIDE + 2.dp, end = width - 150.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(projects, key = { _, p -> p.id }) { _, p ->
            ProjectChip(
                project = p,
                selected = p.id == selectedId,
                onTap = { ui.repo.selectProject(p.id) },
                onLong = { ui.sheet = Sheet.EditProject(p.id) }
            )
        }
        item(key = "new-project") {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .pressable(pressedScale = 0.88f, label = "New project") {
                        ui.sheet = Sheet.EditProject(null)
                    },
                contentAlignment = Alignment.Center
            ) {
                Glyph(R.drawable.ic_ui_plus_circle, 28.dp, Color(0xFF3A3A3C))
            }
        }
    }
}

@Composable
private fun ProjectChip(project: Project, selected: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val hue = Hues.of(project.colorKey)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .pressable(pressedScale = 0.96f, label = project.name, onLongPress = onLong, onTap = onTap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Badge(iconKey = project.iconKey, hue = hue, size = 34.dp)
            Spacer(modifier = Modifier.width(10.dp))
        }
        Txt(
            text = project.name,
            size = 26.sp,
            weight = FontWeight.Bold,
            display = true,
            tracking = (-0.6).sp,
            color = if (selected) Ink.White else Color(0xFF48484A),
            modifier = Modifier.widthIn(max = 230.dp)
        )
    }
}

// ---------------------------------------------------------------------------------------------- toolbar

@Composable
private fun QuickToolbar(ui: UiState, data: AppData, now: Long) {
    val q = ui.quick
    val focus = LocalFocusManager.current
    val project = Queries.project(data, data.settings.selectedProjectId) ?: data.projects.firstOrNull()
    val hue = Hues.of(project?.colorKey)
    val parsed = remember(q.text.text, data.projects, now / 60_000L) { QuickAdd.parse(q.text.text, data, now) }
    val hasDay = q.day != null || parsed.day != null
    val hasTime = q.minute != null || parsed.minute != null
    val priority = maxOf(parsed.priority, q.priority)
    val canCommit = q.text.text.isNotBlank()
    val dim = Color(0xFF8E8E93)

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!heard.isNullOrBlank()) {
                val current = q.text.text
                val joined = if (current.isBlank()) heard else "$current $heard"
                q.text = TextFieldValue(joined, TextRange(joined.length))
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(CircleShape)
                .background(Ink.Card),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton(Res.glyph(project?.iconKey), hue.accent, "Next project") {
                val list = data.projects
                if (list.size > 1) {
                    val i = list.indexOfFirst { it.id == project?.id }
                    ui.repo.selectProject(list[(i + 1) % list.size].id)
                }
            }
            ToolButton(
                icon = if (hasTime) R.drawable.ic_ui_bell else R.drawable.ic_ui_calendar,
                tint = if (hasDay || hasTime) hue.accent else dim,
                label = "Date and time"
            ) {
                focus.clearFocus()
                ui.sheet = Sheet.WhenPicker(
                    day = q.day ?: parsed.day,
                    minute = q.minute ?: parsed.minute,
                    onResult = { d, m ->
                        q.day = d
                        q.minute = m
                        try {
                            q.focusRequester.requestFocus()
                        } catch (e: IllegalStateException) {
                            // the field is not on screen
                        }
                    }
                )
            }
            ToolButton(R.drawable.ic_ui_circle, if (q.later) hue.accent else dim, "Nice to do") {
                q.later = !q.later
            }
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 52.dp)
                    .pressable(pressedScale = 0.86f, label = "Priority") { q.priority = (q.priority + 1) % 4 },
                contentAlignment = Alignment.Center
            ) {
                if (priority == 0) {
                    Glyph(R.drawable.ic_ui_bang, 22.dp, dim)
                } else {
                    Txt(
                        text = "!".repeat(priority),
                        size = 20.sp,
                        weight = FontWeight.Bold,
                        color = hue.accent
                    )
                }
            }
            ToolButton(R.drawable.ic_ui_lines, dim, "Add details") {
                val added = QuickAdd.commit(ui, data, now)
                if (added != null) {
                    focus.clearFocus()
                    ui.sheet = Sheet.EditTask(added.id)
                } else {
                    ui.showToast("Type a title first")
                }
            }
            ToolButton(R.drawable.ic_ui_mic, hue.accent, "Voice input") {
                try {
                    speech.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "New To Do")
                    )
                } catch (e: ActivityNotFoundException) {
                    ui.showToast("Voice input isn't available on this device")
                }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        CommitButton(canCommit = canCommit, hue = hue) {
            if (canCommit) QuickAdd.commit(ui, data, now)
            focus.clearFocus()
        }
    }
}

@Composable
private fun ToolButton(icon: Int, tint: Color, label: String, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 52.dp)
            .pressable(pressedScale = 0.86f, label = label) { onTap() },
        contentAlignment = Alignment.Center
    ) {
        Glyph(icon, 22.dp, tint)
    }
}

@Composable
private fun CommitButton(canCommit: Boolean, hue: Hue, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .pressable(pressedScale = 0.9f, label = if (canCommit) "Add to-do" else "Hide keyboard") { onTap() }
            .clip(CircleShape)
            .background(if (canCommit) hue.accent else hue.accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (canCommit) {
            Glyph(R.drawable.ic_ui_check, 26.dp, Color.White)
        } else {
            Glyph(R.drawable.ic_ui_keyboard_down, 26.dp, hue.accent)
        }
    }
}

// ---------------------------------------------------------------------------------------------- delete zone

@Composable
private fun DeleteZone(ui: UiState) {
    val over = ui.drag.overDelete
    val scale by animateFloatAsState(
        targetValue = if (over) 1.03f else 1f,
        animationSpec = tween(120),
        label = "deleteZone"
    )
    DisposableEffect(Unit) {
        onDispose { ui.drag.deleteCoords = null }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SIDE)
            .height(64.dp)
            .onGloballyPositioned { ui.drag.deleteCoords = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .background(if (over) Color(0xFF3A0E12) else Ink.DangerFill)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = Ink.Danger.copy(alpha = if (over) 1f else 0.65f),
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 9.dp.toPx()), 0f)
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(R.drawable.ic_ui_trash, 18.dp, Ink.Danger)
            Spacer(modifier = Modifier.width(8.dp))
            Txt(text = "Drop here to delete", color = Ink.Danger, size = 15.sp, weight = FontWeight.Medium)
        }
    }
}
