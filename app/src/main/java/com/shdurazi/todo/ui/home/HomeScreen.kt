package com.shdurazi.todo.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.shdurazi.todo.core.AppData
import com.shdurazi.todo.core.Block
import com.shdurazi.todo.core.Planner
import com.shdurazi.todo.core.Queries
import com.shdurazi.todo.ui.Screen
import com.shdurazi.todo.ui.TimelineMode
import com.shdurazi.todo.ui.UiState
import com.shdurazi.todo.ui.theme.Ink
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Current height of the white timeline card in pixels; dragging follows the finger, settling animates. */
@Stable
class TimelineHeight {
    var px by mutableFloatStateOf(0f)
    var placed = false
    private var job: Job? = null

    fun snap(target: Float) {
        job?.cancel()
        px = target
        placed = true
    }

    fun dragTo(value: Float, min: Float, max: Float) {
        job?.cancel()
        px = value.coerceIn(min, maxOf(min, max))
    }

    fun settle(scope: CoroutineScope, target: Float) {
        job?.cancel()
        job = scope.launch {
            animate(
                initialValue = px,
                targetValue = target,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
            ) { value, _ -> px = value }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    ui: UiState,
    data: AppData,
    now: Long,
    h24: Boolean,
    onOpenSettings: () -> Unit,
    onStartFocus: (Block) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val zone = remember { ZoneId.systemDefault() }
    val today = Planner.dayOf(now, zone)

    val tl = remember { TimelineHeight() }
    var areaH by remember { mutableFloatStateOf(0f) }
    var todoH by remember { mutableFloatStateOf(0f) }

    val overlapPx = with(density) { 30.dp.toPx() }
    val statusTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val minDragPx = statusTopPx + with(density) { (DATE_BAR_H + 14.dp).toPx() }
    val typing = ui.quick.focused
    val collapsedTopPx = statusTopPx + with(density) { (if (typing) 12.dp else 66.dp).toPx() }

    // what the timeline card should be right now
    val mode = when {
        ui.drag.active -> TimelineMode.Full
        typing -> TimelineMode.Collapsed
        else -> ui.timeline
    }
    // read through the delegate everywhere, so long-lived gesture callbacks never see an old value
    val halfPx by remember(overlapPx) {
        derivedStateOf { (areaH - todoH + overlapPx).coerceIn(areaH * 0.34f, areaH * 0.58f) }
    }
    val targetPx = when (mode) {
        TimelineMode.Collapsed -> 0f
        TimelineMode.Half -> halfPx
        TimelineMode.Full -> areaH
    }
    LaunchedEffect(targetPx) {
        if (areaH > 0f) {
            if (!tl.placed) tl.snap(targetPx) else tl.settle(this, targetPx)
        }
    }

    // leaving the keyboard (back gesture) also leaves the "New To Do" field
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible) focus.clearFocus()
        imeWasVisible = imeVisible
    }

    // "New To Do" from the launcher shortcut / quick settings tile
    LaunchedEffect(ui.quickAddRequest) {
        if (ui.quickAddRequest > 0) {
            delay(250)
            try {
                ui.quick.focusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                // the field is not on screen (yet); nothing to do
            }
        }
    }

    BackHandler(
        enabled = ui.screen == Screen.Home && ui.timeline == TimelineMode.Full && !ui.drag.active && ui.sheet == null
    ) {
        ui.timeline = TimelineMode.Half
    }

    fun settleAfterDrag() {
        val px = tl.px
        val newMode = when {
            px < halfPx * 0.5f -> TimelineMode.Collapsed
            px > (halfPx + areaH) / 2f -> TimelineMode.Full
            else -> TimelineMode.Half
        }
        ui.timeline = newMode
        tl.settle(
            scope,
            when (newMode) {
                TimelineMode.Collapsed -> 0f
                TimelineMode.Half -> halfPx
                TimelineMode.Full -> areaH
            }
        )
    }

    fun handleDrop() {
        val d = ui.drag
        val id = d.taskId
        val overDelete = d.overDelete
        val hover = d.hoverBlock
        val overTimeline = d.overTimeline
        val pointer = d.pointer
        val toMinute = d.minuteAt
        d.end()
        if (id == null) return
        val repo = ui.repo
        val snapshot = repo.state.value
        val task = snapshot.tasks.firstOrNull { it.id == id } ?: return
        when {
            overDelete -> {
                repo.deleteTask(id)
                ui.showToast("Deleted", "Undo") { repo.restoreTask(task) }
            }
            hover != null -> {
                val block = snapshot.blocks.firstOrNull { it.id == hover }
                if (block != null && task.blockId != block.id) {
                    repo.scheduleTask(id, block.id)
                    ui.showToast("Scheduled in ${block.title}")
                }
            }
            overTimeline && toMinute != null -> {
                val project = Queries.project(snapshot, task.projectId)
                val created = repo.addBlock(
                    Block(
                        id = "",
                        projectId = task.projectId,
                        title = task.title,
                        iconKey = project?.iconKey ?: "bulb",
                        colorKey = project?.colorKey ?: "blue",
                        day = ui.day,
                        startMin = toMinute(pointer),
                        durationMin = snapshot.settings.defaultBlockMin
                    )
                )
                repo.scheduleTask(id, created.id)
                ui.showToast("Added to the timeline")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink.Black)
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { areaH = it.height.toFloat() }
            ) {
                TodoCard(
                    ui = ui,
                    data = data,
                    now = now,
                    h24 = h24,
                    timelinePx = { tl.px },
                    collapsedTopPx = collapsedTopPx,
                    overlapPx = overlapPx,
                    onContentHeight = { todoH = it },
                    onDrop = { handleDrop() }
                )

                val cardVisible by remember { derivedStateOf { tl.px > 0.5f } }
                if (cardVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val h = tl.px.roundToInt().coerceIn(0, constraints.maxHeight)
                                val p = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                                layout(p.width, h) { p.place(0, 0) }
                            }
                            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                    ) {
                        TimelineCard(
                            ui = ui,
                            data = data,
                            now = now,
                            h24 = h24,
                            onResize = { dy -> tl.dragTo(tl.px + dy, minDragPx, areaH) },
                            onResizeEnd = { settleAfterDrag() },
                            onOpenSettings = onOpenSettings,
                            onStartFocus = onStartFocus
                        )
                    }
                }

                AnimatedVisibility(
                    visible = ui.timeline == TimelineMode.Collapsed && !typing && !ui.drag.active,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 10.dp),
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(120))
                ) {
                    DatePill(
                        day = ui.day,
                        today = today,
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures { ui.timeline = TimelineMode.Half }
                            }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, dy ->
                                        change.consume()
                                        tl.dragTo(tl.px + dy, 0f, areaH)
                                    },
                                    onDragEnd = { settleAfterDrag() },
                                    onDragCancel = { settleAfterDrag() }
                                )
                            }
                    )
                }
            }
            BottomBar(ui = ui, data = data, now = now, modifier = Modifier.navigationBarsPadding())
        }
        DragLayer(ui)
    }
}
