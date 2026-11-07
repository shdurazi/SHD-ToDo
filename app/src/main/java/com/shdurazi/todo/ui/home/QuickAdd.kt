package com.shdurazi.todo.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.shdurazi.todo.core.AppData
import com.shdurazi.todo.core.Project
import com.shdurazi.todo.core.QuickParse
import com.shdurazi.todo.core.QuickParser
import com.shdurazi.todo.core.Task
import com.shdurazi.todo.ui.UiState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Colours the parts of the typed text that were understood (date, time, priority, project). */
class QuickHighlight(
    private val projects: List<Project>,
    private val accent: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val spans = QuickParser.parse(raw, LocalDateTime.now(), projects).spans
        val styled = buildAnnotatedString {
            append(raw)
            for (r in spans) {
                if (r.first >= 0 && r.last < raw.length) {
                    addStyle(SpanStyle(color = accent), r.first, r.last + 1)
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

object QuickAdd {
    fun parse(text: String, data: AppData, nowMs: Long): QuickParse {
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault())
        return QuickParser.parse(text, ldt, data.projects)
    }

    /**
     * Adds the typed task, merging what the text says with what was chosen on the toolbar.
     * Returns the new task, or null when there is nothing to add.
     */
    fun commit(ui: UiState, data: AppData, nowMs: Long): Task? {
        val raw = ui.quick.text.text
        if (raw.isBlank()) return null
        val parsed = parse(raw, data, nowMs)
        val projectId = parsed.projectId
            ?: data.settings.selectedProjectId
            ?: data.projects.firstOrNull()?.id
            ?: return null
        // what was picked on the toolbar wins over what the text says
        val day = ui.quick.day ?: parsed.day
        val minute = ui.quick.minute ?: parsed.minute
        val priority = maxOf(parsed.priority, ui.quick.priority)
        val task = ui.repo.addTask(
            title = parsed.title,
            projectId = projectId,
            day = day,
            minute = minute,
            priority = priority,
            later = ui.quick.later
        )
        ui.quick.reset()
        return task
    }
}
