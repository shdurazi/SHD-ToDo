package com.shdurazi.todo.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shdurazi.todo.ToDoApp
import com.shdurazi.todo.core.Planner
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the receiver alive. */
private fun BroadcastReceiver.async(block: () -> Unit) {
    val pending = goAsync()
    ToDoApp.scope.launch {
        try {
            block()
        } catch (e: Exception) {
            // ignore: a failed reminder must not crash the app
        } finally {
            pending.finish()
        }
    }
}

/** Fires when the chained alarm rings. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        async { ReminderEngine.fire(app) }
    }
}

/** Re-arms everything after a reboot, an app update, or a clock / time-zone / permission change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        async { ReminderEngine.fire(app) }
    }
}

/** Handles the buttons on notifications (Done, Snooze, Stop). */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val taskId = intent.getStringExtra(Notifier.EXTRA_TASK_ID)
        val minutes = intent.getIntExtra(Notifier.EXTRA_MINUTES, 10)
        async {
            val repo = ToDoApp.repo(app)
            when (action) {
                Notifier.ACTION_DONE -> if (taskId != null) {
                    val t = repo.data.tasks.firstOrNull { it.id == taskId }
                    if (t != null && !t.done) repo.toggleDone(taskId)
                    Notifier.cancelTask(app, taskId)
                }
                Notifier.ACTION_SNOOZE -> if (taskId != null) {
                    repo.snoozeTask(taskId, minutes)
                    Notifier.cancelTask(app, taskId)
                }
                Notifier.ACTION_STOP_FOCUS -> {
                    repo.endFocus()
                    Notifier.cancelFocus(app)
                }
            }
            repo.flush()
        }
    }
}

/** Small helper so the UI and notifications start focus sessions the same way. */
object FocusController {
    fun start(context: Context, blockId: String): Boolean {
        val repo = ToDoApp.repo(context)
        val block = repo.data.blocks.firstOrNull { it.id == blockId } ?: return false
        val zone = java.time.ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val bs = Planner.blockStart(block, zone)
        val be = Planner.blockEnd(block, zone)
        val endsAt = if (now in bs until be) be else now + block.durationMin * 60_000L
        repo.startFocus(
            com.shdurazi.todo.core.FocusSession(
                blockId = block.id,
                title = block.title,
                iconKey = block.iconKey,
                colorKey = block.colorKey,
                startedAt = now,
                endsAt = endsAt
            )
        )
        return true
    }

    fun stop(context: Context) {
        val repo = ToDoApp.repo(context)
        repo.endFocus()
        Notifier.cancelFocus(context)
    }
}
