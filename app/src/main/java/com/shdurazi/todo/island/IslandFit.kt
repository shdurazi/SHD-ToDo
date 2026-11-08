package com.shdurazi.todo.island

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.RectF
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import kotlin.math.min

/**
 * How much of the island fits beside the camera.
 *
 * The island lives in a window below the status bar, so the status bar's own icons are painted over it. The app cannot
 * look at those icons (that takes an accessibility service or notification access, and Google Play Protect refuses to
 * install such an app from a browser), so it goes by what the phone tells any app: an alarm is set, headphones are
 * connected, Wi-Fi is on... Each of those is an icon in the right half of the bar, close to the camera.
 *
 * Three sizes, from wide to small:
 *  - [WIDE]: the ring left of the camera and the time right of it, as in the Anchor picture;
 *  - [LEFT]: the ring and the time both left of the camera, the right half pulled in;
 *  - [RING]: the ring alone, left of the camera.
 */
object IslandFit {
    const val WIDE = 0
    const val LEFT = 1
    const val RING = 2

    /** What the person can pick in the settings. Anything but [AUTO] is a size they chose themselves. */
    const val AUTO = 0
    const val CHOICE_WIDE = 1
    const val CHOICE_LEFT = 2
    const val CHOICE_RING = 3

    // Distances in dp.
    private const val POD = 62f // each half of the wide pill, from the camera's edge to the end of the pill
    private const val END = 8f // the end of the pill to the ring
    private const val GAP = 6f // the ring to the time
    private const val SLOT = 46f // the room the time gets when it sits left of the camera
    private const val NEAR = 8f // the camera to the nearest thing beside it
    private const val TIGHT = 8f // the right half when it is pulled in: a little black around the camera

    fun name(mode: Int): String = when (mode) {
        WIDE -> "wide"
        LEFT -> "left"
        else -> "ring"
    }

    /** Where the pieces of the compact island sit in one size. Widths are pixels, x values are screen coordinates. */
    class Lay {
        var leftEar = 0f
        var rightEar = 0f
        var ringX = 0f
        var timeX = 0f
        var timeRoom = 0f
        var timeA = 0f

        fun set(o: Lay) {
            leftEar = o.leftEar
            rightEar = o.rightEar
            ringX = o.ringX
            timeX = o.timeX
            timeRoom = o.timeRoom
            timeA = o.timeA
        }
    }

    fun ringD(pillH: Float, d: Float): Float = min(pillH - 12f * d, 24f * d)

    /** How far the pill reaches left of the camera's left edge in this size. */
    fun leftEar(mode: Int, ringD: Float, d: Float): Float = when (mode) {
        WIDE -> POD * d
        LEFT -> END * d + ringD + GAP * d + SLOT * d + NEAR * d
        else -> END * d + ringD + NEAR * d
    }

    /** How far the pill reaches right of the camera's right edge in this size. */
    fun rightEar(mode: Int, d: Float): Float = if (mode == WIDE) POD * d else TIGHT * d

    /** Fills [out] with where everything goes in [mode], around the camera hole [hole] (screen pixels). */
    fun fill(mode: Int, hole: RectF, pillH: Float, d: Float, out: Lay) {
        val rd = ringD(pillH, d)
        out.leftEar = leftEar(mode, rd, d)
        out.rightEar = rightEar(mode, d)
        val slotX = hole.left - NEAR * d - SLOT * d / 2f
        when (mode) {
            WIDE -> {
                out.ringX = hole.left - POD * d / 2f
                out.timeX = hole.right + POD * d / 2f
                out.timeRoom = (POD - 12f) * d
                out.timeA = 1f
            }
            LEFT -> {
                out.ringX = hole.left - out.leftEar + END * d + rd / 2f
                out.timeX = slotX
                out.timeRoom = SLOT * d
                out.timeA = 1f
            }
            else -> {
                out.ringX = hole.left - NEAR * d - rd / 2f
                // the time's place of the LEFT size, so that going between the two only fades it
                out.timeX = slotX
                out.timeRoom = SLOT * d
                out.timeA = 0f
            }
        }
    }

    // ---------------------------------------------------------------- what the status bar probably holds
    /** What the phone reports that would be an icon in the right half of the status bar. */
    class Guess(val icons: Int, val what: String)

    /** The size to use, and why. [auto] is false when the person picked it. */
    class Pick(val mode: Int, val auto: Boolean, val guess: Guess?)

    fun pick(context: Context): Pick {
        val choice = IslandPrefs.fit(context)
        if (choice != AUTO) return Pick(choice - 1, false, null)
        val g = guess(context)
        return Pick(if (g.icons == 0) WIDE else LEFT, true, g)
    }

    /** A sentence for the settings: what Auto goes by right now. */
    fun describe(context: Context): String {
        val g = guess(context)
        val size = if (g.icons == 0) "Wide" else "Left"
        return if (g.icons == 0) "Right now it sees nothing beside the camera, so Auto picks $size." else "Right now it sees ${g.what}, so Auto picks $size."
    }

    @Suppress("DEPRECATION") // allNetworks: the one call that also lists the Wi-Fi when the phone is using mobile data
    fun guess(context: Context): Guess {
        val test = debugIcons(context)
        if (test >= 0) return Guess(test, "$test test icons")
        val names = ArrayList<String>(5)
        try {
            if (context.getSystemService(AlarmManager::class.java)?.nextAlarmClock != null) names.add("an alarm")
        } catch (e: Throwable) {
            // not worth failing over
        }
        try {
            val am = context.getSystemService(AudioManager::class.java)
            if (am != null) {
                val out = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                if (out.any { headphones(it.type) }) names.add("headphones")
            }
        } catch (e: Throwable) {
            // ignore
        }
        try {
            // the status bar shows the Do Not Disturb icon or else the vibrate / silent one, never both
            val nm = context.getSystemService(NotificationManager::class.java)
            val zen = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            val dnd = zen == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                zen == NotificationManager.INTERRUPTION_FILTER_NONE ||
                zen == NotificationManager.INTERRUPTION_FILTER_ALARMS
            val am = context.getSystemService(AudioManager::class.java)
            val quiet = am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL
            if (dnd) names.add("Do Not Disturb") else if (quiet) names.add("silent mode")
        } catch (e: Throwable) {
            // ignore
        }
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            if (cm != null) {
                var wifi = false
                var vpn = false
                for (n in cm.allNetworks) {
                    val caps = cm.getNetworkCapabilities(n) ?: continue
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        vpn = true
                    } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        wifi = true
                    }
                }
                if (wifi) names.add("Wi-Fi")
                if (vpn) names.add("a VPN")
            }
        } catch (e: Throwable) {
            // ignore: without the permission this icon is simply not counted
        }
        return Guess(names.size, names.joinToString(", "))
    }

    private fun headphones(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        23 /* TYPE_HEARING_AID */, 26 /* TYPE_BLE_HEADSET */ -> true
        else -> false
    }

    // ---------------------------------------------------------------- test hooks (debug builds only)
    private fun debuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** `adb shell settings put global todo_island_fit auto|wide|left|ring`: the emulator test picks a size this way. */
    fun debugChoice(context: Context): Int {
        if (!debuggable(context)) return -1
        return try {
            when (Settings.Global.getString(context.contentResolver, "todo_island_fit")) {
                "auto" -> AUTO
                "wide" -> CHOICE_WIDE
                "left" -> CHOICE_LEFT
                "ring" -> CHOICE_RING
                else -> -1
            }
        } catch (e: Throwable) {
            -1
        }
    }

    /** `adb shell settings put global todo_island_icons <n>`: pretends that many icons are in the status bar (Auto only). */
    private fun debugIcons(context: Context): Int {
        if (!debuggable(context)) return -1
        return try {
            Settings.Global.getString(context.contentResolver, "todo_island_icons")?.toIntOrNull()?.coerceIn(0, 9) ?: -1
        } catch (e: Throwable) {
            -1
        }
    }
}
