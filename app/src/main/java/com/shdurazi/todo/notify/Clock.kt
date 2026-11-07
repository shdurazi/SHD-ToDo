package com.shdurazi.todo.notify

import android.content.Context
import android.text.format.DateFormat
import com.shdurazi.todo.core.Settings

/** Resolves the user's 12 / 24 hour preference. */
object ClockPref {
    fun is24(context: Context, settings: Settings): Boolean = when (settings.clockMode) {
        1 -> true
        2 -> false
        else -> DateFormat.is24HourFormat(context)
    }
}
