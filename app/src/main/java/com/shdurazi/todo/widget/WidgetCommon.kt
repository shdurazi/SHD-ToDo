package com.shdurazi.todo.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.glance.unit.ColorProvider
import com.shdurazi.todo.MainActivity
import com.shdurazi.todo.ToDoApp
import com.shdurazi.todo.core.Fmt
import com.shdurazi.todo.core.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Colours used by all widgets. */
object WColors {
    val white = ColorProvider(Color(0xFFF5F5F7))
    val dim = ColorProvider(Color(0xFF8E8E93))
    val ring = ColorProvider(Color(0xFF7C7C80))
    val danger = ColorProvider(Color(0xFFFF453A))
    val paperText = ColorProvider(Color(0xFF0B0B0C))
    val paperDim = ColorProvider(Color(0xFF9A9AA0))
    val onBadge = ColorProvider(Color(0xFFFFFFFF))
}

object WidgetUpdater {
    /** Redraws every widget of the app (cheap, safe to call from any thread). */
    fun requestUpdate(context: Context) {
        val app = context.applicationContext
        ToDoApp.scope.launch {
            try {
                TodayWidget().updateAll(app)
            } catch (e: Exception) {
            }
            try {
                AgendaWidget().updateAll(app)
            } catch (e: Exception) {
            }
            try {
                NextUpWidget().updateAll(app)
            } catch (e: Exception) {
            }
        }
    }
}

object WidgetIntents {
    private fun base(context: Context): Intent {
        val i = Intent(context, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return i
    }

    fun openApp(context: Context): Action = actionStartActivity(base(context))

    fun quickAdd(context: Context): Action {
        val i = base(context)
        i.setAction(MainActivity.ACTION_QUICK_ADD)
        return actionStartActivity(i)
    }

    fun startFocus(context: Context, blockId: String): Action {
        val i = base(context)
        i.setAction(MainActivity.ACTION_START_FOCUS)
        i.putExtra(MainActivity.EXTRA_BLOCK_ID, blockId)
        return actionStartActivity(i)
    }
}

/** Ticks a task off straight from a widget. */
class ToggleDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        val repo = ToDoApp.repo(context)
        val t = repo.data.tasks.firstOrNull { it.id == id } ?: return
        if (!t.done) repo.toggleDone(id)
        withContext(Dispatchers.IO) { repo.flush() }
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<String>("task_id")
    }
}

/** Short label for the right side of a task row: "9:00 AM", "Tomorrow", "Mon 12 Oct 9:00". */
fun dueLabel(t: Task, today: Long, nowMinute: Int, h24: Boolean): Pair<String, Boolean> {
    val m = Fmt.dueMeta(t, today, nowMinute, h24) ?: return Pair("", false)
    val text = if (m.bottom == null) m.top else if (m.top == "Today") m.bottom else m.top + " " + m.bottom
    return Pair(text, m.overdue)
}
