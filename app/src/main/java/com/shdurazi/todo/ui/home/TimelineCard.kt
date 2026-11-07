package com.shdurazi.todo.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shdurazi.todo.R
import com.shdurazi.todo.core.AppData
import com.shdurazi.todo.core.Block
import com.shdurazi.todo.core.Fmt
import com.shdurazi.todo.core.Planner
import com.shdurazi.todo.core.Queries
import com.shdurazi.todo.core.TimelineLayout
import com.shdurazi.todo.ui.Sheet
import com.shdurazi.todo.ui.UiState
import com.shdurazi.todo.ui.components.Badge
import com.shdurazi.todo.ui.components.Glyph
import com.shdurazi.todo.ui.components.pressable
import com.shdurazi.todo.ui.theme.Hues
import com.shdurazi.todo.ui.theme.Ink
import com.shdurazi.todo.ui.theme.Txt
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

const val HOUR_DP = 72
private val RAIL_W = 52.dp
private val RIGHT_PAD = 16.dp
val DATE_BAR_H = 96.dp

/** The white card at the top: scrolling timeline, settings button and the date bar. */
@Composable
fun TimelineCard(
    ui: UiState,
    data: AppData,
    now: Long,
    h24: Boolean,
    onResize: (Float) -> Unit,
    onResizeEnd: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartFocus: (Block) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val zone = remember { ZoneId.systemDefault() }
    val today = Planner.dayOf(now, zone)
    val scroll = androidx.compose.foundation.rememberScrollState()
    val swipePx = with(density) { 70.dp.toPx() }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(modifier = modifier.fillMaxSize().background(Ink.Paper)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { _, amount -> total += amount },
                        onDragEnd = {
                            if (abs(total) > swipePx) ui.day = ui.day + (if (total < 0f) 1L else -1L)
                        }
                    )
                }
        ) {
            Crossfade(targetState = ui.day, animationSpec = tween(150), label = "day") { d ->
                TimelineViewport(
                    ui = ui,
                    data = data,
                    day = d,
                    today = today,
                    nowMinute = Planner.minuteOf(now, zone),
                    h24 = h24,
                    scroll = scroll,
                    onStartFocus = onStartFocus
                )
            }
            // soft edges
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusTop + 30.dp)
                    .background(Brush.verticalGradient(listOf(Ink.Paper, Ink.Paper.copy(alpha = 0f))))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(Brush.verticalGradient(listOf(Ink.Paper.copy(alpha = 0f), Ink.Paper)))
            )
            // settings
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 6.dp, end = 14.dp)
                    .size(40.dp)
                    .pressable(pressedScale = 0.92f, label = "Settings") { onOpenSettings() }
                    .clip(CircleShape)
                    .background(Color(0xE6F2F2F7)),
                contentAlignment = Alignment.Center
            ) {
                Glyph(R.drawable.ic_ui_gear, 22.dp, Color(0xFF8E8E93))
            }
        }
        DateBar(
            day = ui.day,
            today = today,
            onResize = onResize,
            onResizeEnd = onResizeEnd,
            onSwipe = { delta -> ui.day = ui.day + delta },
            onPick = { ui.sheet = Sheet.DayPicker }
        )
    }
}

@Composable
private fun TimelineViewport(
    ui: UiState,
    data: AppData,
    day: Long,
    today: Long,
    nowMinute: Int,
    h24: Boolean,
    scroll: ScrollState,
    onStartFocus: (Block) -> Unit
) {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val padTop = statusTop + 34.dp
    val hourPx = with(density) { HOUR_DP.dp.toPx() }
    val padTopPx = with(density) { padTop.toPx() }
    val contentH = padTop + (HOUR_DP * 24).dp + 140.dp
    // the card always spans the window, so the window width is the card width
    val contentW = LocalConfiguration.current.screenWidthDp.dp - RAIL_W - RIGHT_PAD

    val blocks = remember(data.blocks, day) { Queries.blocksOn(data, day) }
    val placed = remember(blocks) { TimelineLayout.place(blocks) }
    val focus = data.focus

    // first view of a day: bring "now" (today) or the first block into view
    LaunchedEffect(day) {
        snapshotFlow { scroll.maxValue }.first { it > 0 }
        val firstHour = if (day == today) {
            (nowMinute / 60 - 1).coerceAtLeast(0)
        } else {
            val first = blocks.firstOrNull()
            if (first != null) (first.startMin / 60 - 1).coerceAtLeast(0) else 7
        }
        scroll.scrollTo((firstHour * hourPx).roundToInt())
    }

    // converts a finger position (root coordinates) into a minute of the day, for drops on empty space
    DisposableEffect(day, scroll) {
        val converter: (Offset) -> Int = { p ->
            val top = ui.drag.timelineCoords?.positionInRoot()?.y ?: 0f
            val contentY = p.y - top + scroll.value
            val minutes = ((contentY - padTopPx) / hourPx * 60f).roundToInt()
            ((minutes + 7) / 15 * 15).coerceIn(0, 1425)
        }
        ui.drag.minuteAt = converter
        onDispose {
            // while the day cross-fades the outgoing viewport is disposed after the new one registered
            if (ui.drag.minuteAt === converter) ui.drag.minuteAt = null
        }
    }

    // keep scrolling while a task is held near the top or bottom edge
    LaunchedEffect(ui.drag.active) {
        while (ui.drag.active) {
            val speed = ui.drag.autoScroll
            if (speed != 0f) {
                scroll.scrollBy(speed * 26f)
                ui.drag.refresh()
            }
            delay(16)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { ui.drag.timelineCoords = it }
    ) {
        Box(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentH)
                    .pointerInput(day) {
                        detectTapGestures(
                            onLongPress = { off ->
                                val m = (((off.y - padTopPx) / hourPx * 60f).roundToInt() + 7) / 15 * 15
                                ui.sheet = Sheet.NewBlock(day, m.coerceIn(0, 1380), null)
                            }
                        )
                    }
            ) {
                for (h in 0..23) {
                    Box(
                        modifier = Modifier
                            .offset(y = padTop + (h * HOUR_DP).dp - 9.dp)
                            .width(RAIL_W)
                            .padding(end = 8.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Txt(
                            text = Fmt.hourLabel(h, h24),
                            size = 12.sp,
                            color = Ink.PaperDim,
                            weight = FontWeight.Medium
                        )
                    }
                }
                for (p in placed) {
                    val b = p.block
                    val colW = contentW / p.columns
                    val x = RAIL_W + colW * p.column
                    val w = colW - (if (p.columns > 1) 4.dp else 0.dp)
                    val top = padTop + ((b.startMin / 60f) * HOUR_DP).dp + 2.dp
                    val height = (((b.durationMin / 60f) * HOUR_DP).dp - 4.dp).coerceAtLeast(26.dp)
                    BlockItem(
                        b = b,
                        data = data,
                        hovered = ui.drag.hoverBlock == b.id,
                        live = focus != null && focus.blockId == b.id,
                        ui = ui,
                        modifier = Modifier.offset(x = x, y = top).size(width = w, height = height),
                        tall = height >= 62.dp,
                        onTap = { onStartFocus(b) },
                        onLong = { ui.sheet = Sheet.EditBlock(b.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockItem(
    b: Block,
    data: AppData,
    hovered: Boolean,
    live: Boolean,
    ui: UiState,
    modifier: Modifier,
    tall: Boolean,
    onTap: () -> Unit,
    onLong: () -> Unit
) {
    val hue = Hues.of(b.colorKey)
    val count = remember(data.tasks, b.id) { Queries.tasksOfBlock(data, b.id).count { !it.done } }
    val shape = RoundedCornerShape(16.dp)
    DisposableEffect(b.id) {
        onDispose { ui.drag.blockCoords.remove(b.id) }
    }
    Box(
        modifier = modifier
            .onGloballyPositioned { ui.drag.blockCoords[b.id] = it }
            .pressable(pressedScale = 0.985f, label = b.title, onLongPress = onLong, onTap = onTap)
            .clip(shape)
            .background(if (hovered) hue.blockHover else hue.block)
            .then(if (hovered) Modifier.border(2.dp, hue.badge, shape) else Modifier)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Badge(iconKey = b.iconKey, hue = hue, size = 22.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Txt(
                        text = b.title,
                        color = hue.ink,
                        size = 16.sp,
                        weight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (live) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(hue.badge))
                    }
                    if (count > 0 && !tall) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Txt(text = count.toString(), color = hue.inkSoft, size = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(hue.pill)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Txt(
                        text = Fmt.duration(b.durationMin),
                        color = hue.pillText,
                        size = 12.sp,
                        weight = FontWeight.SemiBold
                    )
                }
            }
            if (tall && count > 0) {
                Txt(
                    text = Fmt.scheduledTasks(count),
                    color = hue.inkSoft,
                    size = 14.sp,
                    modifier = Modifier.padding(start = 30.dp, top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DateBar(
    day: Long,
    today: Long,
    onResize: (Float) -> Unit,
    onResizeEnd: () -> Unit,
    onSwipe: (Long) -> Unit,
    onPick: () -> Unit
) {
    val density = LocalDensity.current
    val swipePx = with(density) { 70.dp.toPx() }
    val resize by rememberUpdatedState(onResize)
    val resizeEnd by rememberUpdatedState(onResizeEnd)
    val swipe by rememberUpdatedState(onSwipe)
    val pick by rememberUpdatedState(onPick)

    Column(modifier = Modifier.fillMaxWidth().background(Ink.Paper)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Ink.PaperLine))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DATE_BAR_H - 1.dp)
                .pointerInput(Unit) { detectTapGestures { pick() } }
                .pointerInput(Unit) {
                    var horizontal: Boolean? = null
                    var totalX = 0f
                    detectDragGestures(
                        onDragStart = {
                            horizontal = null
                            totalX = 0f
                        },
                        onDrag = { _, amount ->
                            if (horizontal == null) horizontal = abs(amount.x) > abs(amount.y)
                            if (horizontal == true) totalX += amount.x else resize(amount.y)
                        },
                        onDragEnd = {
                            if (horizontal == true) {
                                if (abs(totalX) > swipePx) swipe(if (totalX < 0f) 1L else -1L)
                            } else {
                                resizeEnd()
                            }
                        },
                        onDragCancel = { if (horizontal != true) resizeEnd() }
                    )
                }
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(
                    text = Fmt.weekdayShort(day),
                    size = 36.sp,
                    weight = FontWeight.Bold,
                    color = Ink.PaperText,
                    display = true,
                    tracking = (-1).sp
                )
                if (day == today) {
                    Box(
                        modifier = Modifier
                            .padding(start = 3.dp, bottom = 9.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Ink.TodayDot)
                    )
                }
            }
            Txt(
                text = Fmt.monthFull(day),
                size = 16.sp,
                color = Ink.PaperDim,
                weight = FontWeight.SemiBold,
                align = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Txt(
                text = Fmt.dayOfMonth(day).toString(),
                size = 58.sp,
                weight = FontWeight.Bold,
                color = Ink.PaperText,
                display = true,
                tracking = (-2).sp
            )
        }
    }
}

/** White pill shown instead of the card when the timeline is collapsed. */
@Composable
fun DatePill(day: Long, today: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Ink.Paper)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Txt(text = Fmt.weekdayShort(day), size = 16.sp, weight = FontWeight.Bold, color = Ink.PaperText)
        Spacer(modifier = Modifier.width(7.dp))
        Txt(text = Fmt.monthFull(day), size = 16.sp, weight = FontWeight.Medium, color = Ink.PaperDim)
        Spacer(modifier = Modifier.width(7.dp))
        Txt(text = Fmt.dayOfMonth(day).toString(), size = 16.sp, weight = FontWeight.Bold, color = Ink.PaperText)
        if (day == today) {
            Box(
                modifier = Modifier
                    .padding(start = 7.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Ink.TodayDot)
            )
        }
    }
}
