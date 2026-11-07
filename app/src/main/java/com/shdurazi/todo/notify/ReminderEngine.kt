package com.shdurazi.todo.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shdurazi.todo.ToDoApp
import com.shdurazi.todo.core.AppData
import com.shdurazi.todo.core.Due
import com.shdurazi.todo.core.Planner
import java.time.ZoneId

/**
 * One chained alarm. [schedule] arms it for the earliest of "something is due" and "widgets need a redraw";
 * [fire] posts whatever is due, updates the data and arms the next one.
 */
object ReminderEngine {
    const val ACTION_ALARM = "com.shdurazi.todo.action.ALARM"
    private const val REQUEST_CODE = 7001

    private fun alarmIntent(context: Context): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java)
        i.setAction(ACTION_ALARM)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, data: AppData) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val nextDue = Planner.nextAt(data, now, zone)
        val refresh = Planner.nextRefreshAt(data, now, zone)
        val dueFirst = nextDue != null && nextDue <= refresh
        val at = if (dueFirst && nextDue != null) nextDue else refresh
        val pi = alarmIntent(context)
        try {
            if (dueFirst && canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Posts everything that is due, updates the data, then re-arms the alarm and refreshes ongoing UI. */
    fun fire(context: Context) {
        val repo = ToDoApp.repo(context)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val snapshot = repo.data
        var changed = false
        for (item in Planner.items(snapshot, now, zone)) {
            if (item.at > now) continue
            val due = item.due
            when (due) {
                is Due.TaskReminder -> {
                    val task = snapshot.tasks.firstOrNull { it.id == due.taskId }
                    if (task != null && !task.done) Notifier.postTaskReminder(context, snapshot, task, now)
                    repo.clearReminder(due.taskId)
                    changed = true
                }
                is Due.BlockAlert -> {
                    val block = snapshot.blocks.firstOrNull { it.id == due.blockId }
                    if (block != null) Notifier.postBlockAlert(context, snapshot, block, now)
                    repo.markBlockAlerted(due.blockId)
                    changed = true
                }
                is Due.DailySummary -> {
                    val today = Planner.dayOf(now, zone)
                    if (now - item.at <= Planner.SUMMARY_GRACE_MS) Notifier.postSummary(context, snapshot, today)
                    repo.updateSettings { it.copy(lastSummaryDay = today) }
                    changed = true
                }
                is Due.FocusEnd -> {
                    val f = snapshot.focus
                    if (f != null) Notifier.postFocusFinished(context, f)
                    repo.endFocus()
                    changed = true
                }
            }
        }
        if (changed) repo.flush() else Sync.afterPersist(context, repo.data)
    }
}
