package com.shdurazi.todo.island

import android.content.Context
import android.graphics.Color
import com.shdurazi.todo.ui.theme.Hues

/** Colours of the island. Green is the mint of the Anchor picture, the other hues are the app's own. */
object IslandStyle {
    fun accent(colorKey: String?): Int =
        if (colorKey == "green") 0xFF0CD585.toInt() else Hues.of(colorKey).accentArgb

    /** The accent at a given opacity (0..1). */
    fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24)

    /** The accent turned down to a quarter on black: the colour of the blocks that are not running. */
    fun dim(color: Int): Int = Color.rgb(
        (Color.red(color) * 0.245f).toInt(),
        (Color.green(color) * 0.245f).toInt(),
        (Color.blue(color) * 0.245f).toInt()
    )

    /** The accent mixed with white (title and countdown text). */
    fun lighten(color: Int, k: Float): Int = Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * k).toInt(),
        (Color.green(color) + (255 - Color.green(color)) * k).toInt(),
        (Color.blue(color) + (255 - Color.blue(color)) * k).toInt()
    )
}

/** Island settings that live on the device and not in the backup. */
object IslandPrefs {
    private const val FILE = "island"
    private const val KEY_ON = "enabled"
    private const val KEY_INTRO = "intro_for"
    private const val KEY_FAILED = "failed"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The switch in the settings. */
    fun enabled(context: Context): Boolean = try {
        prefs(context).getBoolean(KEY_ON, true)
    } catch (e: Exception) {
        true
    }

    fun setEnabled(context: Context, on: Boolean) {
        try {
            val e = prefs(context).edit().putBoolean(KEY_ON, on)
            if (on) e.putBoolean(KEY_FAILED, false)
            e.apply()
        } catch (e: Exception) {
            // not worth failing over
        }
    }

    /** True after the drawing code threw: the island then stays off until the switch is turned on again. */
    fun failed(context: Context): Boolean = try {
        prefs(context).getBoolean(KEY_FAILED, false)
    } catch (e: Exception) {
        false
    }

    fun setFailed(context: Context, failed: Boolean) {
        try {
            prefs(context).edit().putBoolean(KEY_FAILED, failed).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    /** Switch on and not stopped by an error. */
    fun usable(context: Context): Boolean = enabled(context) && !failed(context)

    /** The session (its start time) whose opening animation has already been played. */
    fun introFor(context: Context): Long = try {
        prefs(context).getLong(KEY_INTRO, 0L)
    } catch (e: Exception) {
        0L
    }

    fun setIntroFor(context: Context, key: Long) {
        try {
            prefs(context).edit().putLong(KEY_INTRO, key).apply()
        } catch (e: Exception) {
            // ignore
        }
    }
}
