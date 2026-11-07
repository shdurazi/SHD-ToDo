package com.shdurazi.todo.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shdurazi.todo.core.AppData
import com.shdurazi.todo.core.Fmt
import com.shdurazi.todo.core.Planner
import com.shdurazi.todo.core.Queries
import com.shdurazi.todo.core.Task
import com.shdurazi.todo.notify.FocusController
import com.shdurazi.todo.ui.UiState
import com.shdurazi.todo.ui.components.Badge
import com.shdurazi.todo.ui.components.CheckRing
import com.shdurazi.todo.ui.components.pressable
import com.shdurazi.todo.ui.theme.Hue
import com.shdurazi.todo.ui.theme.Hues
import com.shdurazi.todo.ui.theme.Ink
import com.shdurazi.todo.ui.theme.Txt
import java.time.ZoneId
import kotlinx.coroutines.delay

/** Full screen countdown for the running focus session, with the to-dos scheduled in that block. */
@Composable
fun FocusScreen(ui: UiState, data: AppData, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = ui.repo
    val zone = remember { ZoneId.systemDefault() }

    // a calm once-a-half-second tick; the countdown only shows whole seconds / minutes
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(500)
        }
    }

    val live = data.focus
    val session = live ?: ui.lastFocus
    val finished = live == null
    val hue = Hues.of(session?.colorKey)
    val today = Planner.dayOf(tick, zone)
    val blockId = session?.blockId

    val tasks = remember(data, blockId, today) {
        if (blockId != null) Queries.tasksOfBlock(data, blockId) else Queries.focusTasks(data, today, 6)
    }

    // the session ran out while this screen was open: celebrate once
    var wasLive by remember { mutableStateOf(live != null) }
    LaunchedEffect(finished) {
        if (finished && wasLive && repo.data.settings.confetti) ui.burst()
        wasLive = !finished
    }

    val big = when {
        live == null -> "Done"
        else -> Fmt.focusRemaining((live.endsAt - tick).coerceAtLeast(0L))
    }

    Box(modifier = Modifier.fillMaxSize().background(Ink.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (session != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(iconKey = session.iconKey, hue = hue, size = 26.dp)
                    Spacer(modifier = Modifier.width(9.dp))
                    Txt(
                        text = session.title,
                        size = 18.sp,
                        weight = FontWeight.SemiBold,
                        modifier = Modifier.widthIn(max = 280.dp)
                    )
                }
            }
            Txt(
                text = big,
                size = 84.sp,
                weight = FontWeight.Bold,
                display = true,
                tracking = (-2.5).sp,
                color = hue.accent,
                align = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 26.dp)
            )

            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Ink.Card)
                    .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 10.dp)
            ) {
                Txt(
                    text = "Tasks",
                    size = 13.sp,
                    color = Ink.TextDim,
                    weight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
                )
                Column(modifier = Modifier.heightIn(max = 290.dp).verticalScroll(rememberScrollState())) {
                    if (tasks.isEmpty()) {
                        Txt(
                            text = if (blockId != null) "Nothing scheduled in this block" else "Nothing to do right now",
                            size = 15.sp,
                            color = Ink.TextDim,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 12.dp)
                        )
                    } else {
                        for (t in tasks) {
                            androidx.compose.runtime.key(t.id) {
                                FocusTaskRow(t, hue) {
                                    val nowDone = repo.toggleDone(t.id)
                                    val d = repo.data
                                    val allDone = tasks.all { other ->
                                        d.tasks.firstOrNull { it.id == other.id }?.done == true
                                    }
                                    if (nowDone && allDone && d.settings.confetti) ui.burst()
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .pressable(pressedScale = 0.97f, label = "Back") { onBack() }
                        .clip(RoundedCornerShape(20.dp))
                        .background(Ink.Button),
                    contentAlignment = Alignment.Center
                ) {
                    Txt(text = "Back", size = 17.sp, weight = FontWeight.SemiBold)
                }
            }

            if (!finished) {
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .height(44.dp)
                        .pressable(pressedScale = 0.96f, label = "End focus session") {
                            FocusController.stop(ctx)
                            onBack()
                        }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Txt(text = "End session", size = 15.sp, color = Ink.TextDim, weight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun FocusTaskRow(task: Task, hue: Hue, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .pressable(pressedScale = 0.985f, label = task.title) { onToggle() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckRing(checked = task.done, accent = hue.accent, size = 21.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Txt(
            text = task.title,
            size = 17.sp,
            color = if (task.done) Ink.TextDim else Ink.Text,
            strike = task.done,
            modifier = Modifier.weight(1f)
        )
    }
}
