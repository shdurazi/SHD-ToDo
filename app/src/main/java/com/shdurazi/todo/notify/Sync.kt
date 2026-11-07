package com.shdurazi.todo.notify

import android.content.Context
import com.shdurazi.todo.core.AppData
import com.shdurazi.todo.widget.WidgetUpdater

/** Everything that has to follow a change of the data: alarm, ongoing notifications, widgets. */
object Sync {
    fun afterPersist(context: Context, data: AppData) {
        val app = context.applicationContext
        try {
            ReminderEngine.schedule(app, data)
        } catch (e: Exception) {
            // never let a scheduling problem take the process down
        }
        try {
            Notifier.syncOngoing(app, data)
        } catch (e: Exception) {
        }
        try {
            WidgetUpdater.requestUpdate(app)
        } catch (e: Exception) {
        }
    }
}
