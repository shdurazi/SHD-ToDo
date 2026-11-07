package com.shdurazi.todo.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Channels {
    const val REMINDERS = "reminders"
    const val SUMMARY = "summary"
    const val NEXT_UP = "next_up"
    const val FOCUS = "focus"
    const val FOCUS_DONE = "focus_done"

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        fun make(id: String, name: String, importance: Int, description: String, badge: Boolean = false) {
            val ch = NotificationChannel(id, name, importance)
            ch.description = description
            ch.setShowBadge(badge)
            nm.createNotificationChannel(ch)
        }
        make(REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH, "Task reminders and alerts before a block starts", badge = true)
        make(SUMMARY, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT, "A short overview of your day")
        make(NEXT_UP, "Next up", NotificationManager.IMPORTANCE_LOW, "Keeps your current or next block in the notification shade")
        make(FOCUS, "Focus timer", NotificationManager.IMPORTANCE_LOW, "Shows the running focus session and its countdown")
        make(FOCUS_DONE, "Focus finished", NotificationManager.IMPORTANCE_HIGH, "Tells you when a focus session is over")
    }
}
