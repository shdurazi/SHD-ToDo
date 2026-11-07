package com.shdurazi.todo.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.IconCompat
import com.shdurazi.todo.MainActivity
import com.shdurazi.todo.R
import com.shdurazi.todo.ToDoApp
import com.shdurazi.todo.core.AppData
import com.shdurazi.todo.core.Block
import com.shdurazi.todo.core.FocusSession
import com.shdurazi.todo.core.Fmt
import com.shdurazi.todo.core.Planner
import com.shdurazi.todo.core.Queries
import com.shdurazi.todo.core.Settings
import com.shdurazi.todo.core.Task
import com.shdurazi.todo.ui.Res
import com.shdurazi.todo.ui.theme.Hues
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Builds and posts every notification of the app. */
object Notifier {
    private const val ID_SUMMARY = 101
    private const val ID_NEXT_UP = 102
    const val ID_FOCUS = 103
    private const val ID_FOCUS_DONE = 104

    const val ACTION_DONE = "com.shdurazi.todo.action.DONE"
    const val ACTION_SNOOZE = "com.shdurazi.todo.action.SNOOZE"
    const val ACTION_STOP_FOCUS = "com.shdurazi.todo.action.STOP_FOCUS"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_MINUTES = "minutes"

    private fun taskNotifId(id: String): Int = 1_000 + (id.hashCode() and 0x7FFFFFFF) % 900_000
    private fun blockNotifId(id: String): Int = 1_000_000 + (id.hashCode() and 0x7FFFFFFF) % 900_000

    fun enabled(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun notify(context: Context, id: Int, n: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted: nothing to do
        }
    }

    fun cancelTask(context: Context, taskId: String) {
        NotificationManagerCompat.from(context).cancel(taskNotifId(taskId))
    }

    fun cancelFocus(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_FOCUS)
    }

    // ------------------------------------------------------------ intents

    private fun openApp(
        context: Context,
        requestCode: Int,
        action: String? = null,
        data: String? = null,
        configure: (Intent) -> Unit = {}
    ): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (action != null) i.setAction(action)
        if (data != null) i.setData(Uri.parse(data))
        configure(i)
        return PendingIntent.getActivity(
            context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun broadcast(context: Context, action: String, key: String, configure: (Intent) -> Unit): PendingIntent {
        val i = Intent(context, ActionReceiver::class.java)
        i.setAction(action)
        i.setData(Uri.parse("todo://" + action + "/" + key))
        configure(i)
        return PendingIntent.getBroadcast(
            context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun applyPrivacy(context: Context, b: NotificationCompat.Builder, s: Settings, channel: String, publicText: String) {
        if (s.lockScreenDetails) {
            b.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        } else {
            b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            val pub = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_check)
                .setContentTitle("ToDo")
                .setContentText(publicText)
                .build()
            b.setPublicVersion(pub)
        }
    }

    // ------------------------------------------------------------ task reminder

    private fun dueText(t: Task, now: Long, h24: Boolean, zone: ZoneId): String {
        val today = Planner.dayOf(now, zone)
        val meta = Fmt.dueMeta(t, today, Planner.minuteOf(now, zone), h24) ?: return "Reminder"
        val core = if (meta.bottom != null) meta.top + " · " + meta.bottom else meta.top
        return if (meta.overdue) "Overdue · $core" else core
    }

    fun postTaskReminder(context: Context, d: AppData, t: Task, now: Long) {
        val zone = ZoneId.systemDefault()
        val s = d.settings
        val h24 = ClockPref.is24(context, s)
        val hue = Hues.of(Queries.project(d, t.projectId)?.colorKey)
        val title = if (t.priority > 0) Fmt.priorityMark(t.priority) + " " + t.title else t.title
        val b = NotificationCompat.Builder(context, Channels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setColor(hue.accentArgb)
            .setContentTitle(title)
            .setContentText(dueText(t, now, h24, zone))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(now)
            .setContentIntent(
                openApp(context, taskNotifId(t.id), MainActivity.ACTION_OPEN_TASK, "todo://open/" + t.id) {
                    it.putExtra(MainActivity.EXTRA_TASK_ID, t.id)
                }
            )
            .addAction(0, "Done", broadcast(context, ACTION_DONE, t.id) { it.putExtra(EXTRA_TASK_ID, t.id) })
            .addAction(0, "Snooze 10 min", broadcast(context, ACTION_SNOOZE, t.id) {
                it.putExtra(EXTRA_TASK_ID, t.id)
                it.putExtra(EXTRA_MINUTES, 10)
            })
        applyPrivacy(context, b, s, Channels.REMINDERS, "Reminder")
        notify(context, taskNotifId(t.id), b.build())
    }

    // ------------------------------------------------------------ block alert

    fun postBlockAlert(context: Context, d: AppData, block: Block, now: Long) {
        val zone = ZoneId.systemDefault()
        val start = Planner.blockStart(block, zone)
        if (Planner.blockEnd(block, zone) <= now) return
        val s = d.settings
        val h24 = ClockPref.is24(context, s)
        val hue = Hues.of(block.colorKey)
        val startText = Fmt.clock(block.startMin, h24)
        val text = if (start > now) {
            val mins = ((start - now) + 59_999L) / 60_000L
            "Starts in $mins min · $startText · " + Fmt.duration(block.durationMin)
        } else {
            "Started at $startText · " + Fmt.duration(block.durationMin)
        }
        val openFocus = openApp(context, blockNotifId(block.id), MainActivity.ACTION_START_FOCUS, "todo://focus/" + block.id) {
            it.putExtra(MainActivity.EXTRA_BLOCK_ID, block.id)
        }
        val b = NotificationCompat.Builder(context, Channels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setColor(hue.accentArgb)
            .setContentTitle(block.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(start)
            .setContentIntent(openApp(context, blockNotifId(block.id) + 1, null, "todo://open/block/" + block.id))
            .addAction(0, "Start focus", openFocus)
        applyPrivacy(context, b, s, Channels.REMINDERS, "Upcoming block")
        notify(context, blockNotifId(block.id), b.build())
    }

    // ------------------------------------------------------------ daily summary

    fun postSummary(context: Context, d: AppData, today: Long) {
        val s = d.settings
        val sum = Planner.summary(d, today)
        if (sum.blocks == 0 && sum.tasks == 0) return
        val h24 = ClockPref.is24(context, s)
        val parts = ArrayList<String>()
        if (sum.blocks > 0) parts.add(if (sum.blocks == 1) "1 block" else "${sum.blocks} blocks")
        if (sum.tasks > 0) parts.add(if (sum.tasks == 1) "1 task" else "${sum.tasks} tasks")
        val headline = parts.joinToString(" · ")
        val first = sum.firstBlock
        val detail = if (first != null) {
            headline + "\nFirst up: " + first.title + " at " + Fmt.clock(first.startMin, h24)
        } else {
            headline
        }
        val b = NotificationCompat.Builder(context, Channels.SUMMARY)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle("Your day")
            .setContentText(headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, ID_SUMMARY))
        applyPrivacy(context, b, s, Channels.SUMMARY, "Your daily summary is ready")
        notify(context, ID_SUMMARY, b.build())
    }

    // ------------------------------------------------------------ focus

    fun postFocusFinished(context: Context, f: FocusSession) {
        if (ToDoApp.of(context).foreground) return
        val hue = Hues.of(f.colorKey)
        val b = NotificationCompat.Builder(context, Channels.FOCUS_DONE)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setColor(hue.accentArgb)
            .setContentTitle("Focus finished")
            .setContentText(f.title)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, ID_FOCUS_DONE))
        notify(context, ID_FOCUS_DONE, b.build())
    }

    /**
     * The running focus session. On Android 16 this is a "Live Update": the countdown sits in a chip in the status bar
     * (on OnePlus it can live in the Live Alerts capsule around the camera) and the expanded card shows a progress
     * bar in the block's colour with the block's icon on it. Older versions get a normal ongoing notification.
     * The alarm in [ReminderEngine] redraws it every minute while the session runs.
     */
    private fun postFocus(context: Context, d: AppData, f: FocusSession) {
        val zone = ZoneId.systemDefault()
        val h24 = ClockPref.is24(context, d.settings)
        val hue = Hues.of(f.colorKey)
        val now = System.currentTimeMillis()
        val endClock = Fmt.clock(Planner.minuteOf(f.endsAt, zone), h24)
        // text for the chip / capsule (a few characters); redrawn every minute by the alarm
        val leftMs = f.endsAt - now
        val chipText = if (leftMs < 60_000L) "<1m" else Fmt.duration(((leftMs + 59_999L) / 60_000L).toInt())
        // the length of the session never changes, so this line never goes stale; the countdown itself is the chronometer
        val totalMin = ((f.endsAt - f.startedAt + 30_000L) / 60_000L).coerceAtLeast(1L).toInt()
        val b = NotificationCompat.Builder(context, Channels.FOCUS)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setColor(hue.accentArgb)
            .setContentTitle(f.title)
            .setContentText(Fmt.duration(totalMin) + " · until " + endClock)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(f.endsAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(openApp(context, ID_FOCUS, MainActivity.ACTION_OPEN_FOCUS))
            .addAction(0, "Stop", broadcast(context, ACTION_STOP_FOCUS, "focus") { })
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(chipText)
        if (Build.VERSION.SDK_INT >= 36) {
            val progress = focusProgress(context, f, now)
            if (progress != null) b.setStyle(progress)
        }
        applyPrivacy(context, b, d.settings, Channels.FOCUS, "Focus session running")
        notify(context, ID_FOCUS, b.build())
        if (Build.VERSION.SDK_INT >= 36) {
            logLiveStatus(context)
            probePromotable(context, f)
        }
    }

    private var probed = false

    /**
     * Debug builds only, once: builds (without posting) notifications that differ in one characteristic and logs
     * which of them the system calls promotable, to find out what keeps a notification from becoming a Live Update.
     * Read by the emulator test.
     */
    private fun probePromotable(context: Context, f: FocusSession) {
        if (probed) return
        probed = true
        try {
            if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
            fun promotable(b: NotificationCompat.Builder): String {
                val n = b.build()
                return (n.javaClass.getMethod("hasPromotableCharacteristics").invoke(n) as? Boolean).toString()
            }
            fun base() = NotificationCompat.Builder(context, Channels.FOCUS)
                .setSmallIcon(R.drawable.ic_stat_check)
                .setContentTitle("Title")
                .setContentText("Text")
                .setOngoing(true)
            Log.i("ToDoProbe", "fingerprint=" + Build.FINGERPRINT)
            val now = System.currentTimeMillis()
            fun probe(name: String, change: (NotificationCompat.Builder) -> Unit) {
                val b = base()
                change(b)
                Log.i("ToDoProbe", "$name -> " + promotable(b))
            }
            probe("ongoing+title") { }
            probe("+requestPromoted") { b -> b.setRequestPromotedOngoing(true) }
            probe("+progressStyle") { b ->
                b.setRequestPromotedOngoing(true)
                val p = focusProgress(context, f, now)
                if (p != null) b.setStyle(p)
            }
            probe("+chronometer") { b ->
                b.setRequestPromotedOngoing(true).setShowWhen(true).setWhen(f.endsAt)
                    .setUsesChronometer(true).setChronometerCountDown(true)
            }
            probe("+color") { b -> b.setRequestPromotedOngoing(true).setColor(Hues.of(f.colorKey).accentArgb) }
            probe("+publicVersion") { b ->
                val pub = NotificationCompat.Builder(context, Channels.FOCUS)
                    .setSmallIcon(R.drawable.ic_stat_check).setContentTitle("ToDo").build()
                b.setRequestPromotedOngoing(true).setPublicVersion(pub)
            }
            probe("+action") { b -> b.setRequestPromotedOngoing(true).addAction(0, "Stop", openApp(context, 9998)) }
            probe("+silent") { b -> b.setRequestPromotedOngoing(true).setSilent(true).setOnlyAlertOnce(true) }
            probe("+contentIntent") { b -> b.setRequestPromotedOngoing(true).setContentIntent(openApp(context, 9997)) }
            probe("+categoryProgress") { b ->
                b.setRequestPromotedOngoing(true).setCategory(NotificationCompat.CATEGORY_PROGRESS)
            }
            val notOngoing = NotificationCompat.Builder(context, Channels.FOCUS)
                .setSmallIcon(R.drawable.ic_stat_check).setContentTitle("Title").setRequestPromotedOngoing(true)
            Log.i("ToDoProbe", "control (not ongoing) -> " + promotable(notOngoing))
        } catch (e: Throwable) {
            Log.i("ToDoProbe", "probe failed: $e")
        }
    }

    /** Progress bar of the Live Update: one segment in the block's colour, the block's icon as the marker on it. */
    private fun focusProgress(context: Context, f: FocusSession, now: Long): NotificationCompat.ProgressStyle? {
        return try {
            val hue = Hues.of(f.colorKey)
            val total = ((f.endsAt - f.startedAt) / 1000L).coerceIn(1L, 7L * 86_400L).toInt()
            val elapsed = ((now - f.startedAt) / 1000L).coerceIn(0L, total.toLong()).toInt()
            val style = NotificationCompat.ProgressStyle()
                .setProgress(elapsed)
                .addProgressSegment(NotificationCompat.ProgressStyle.Segment(total).setColor(hue.accentArgb))
            val marker = markerIcon(context, f)
            if (marker != null) style.setProgressTrackerIcon(marker)
            style
        } catch (e: Throwable) {
            null
        }
    }

    /** The block's glyph in white on a disc in the block's colour. */
    private fun markerIcon(context: Context, f: FocusSession): IconCompat? {
        return try {
            val hue = Hues.of(f.colorKey)
            val size = 96
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = hue.accentArgb
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            val glyph = ContextCompat.getDrawable(context, Res.glyph(f.iconKey))?.mutate()
            if (glyph != null) {
                val tinted = DrawableCompat.wrap(glyph)
                DrawableCompat.setTint(tinted, Color.WHITE)
                val pad = (size * 0.24f).toInt()
                tinted.setBounds(pad, pad, size - pad, size - pad)
                tinted.draw(canvas)
            }
            IconCompat.createWithBitmap(bitmap)
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Android 16+: may the app show Live Updates (the user can switch it off per app)?
     * Null when it can't be asked. Looked up by name so that a missing API can never break the build or the app.
     */
    fun canPostLiveUpdates(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < 36) return null
        return try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return null
            nm.javaClass.getMethod("canPostPromotedNotifications").invoke(nm) as? Boolean
        } catch (e: Throwable) {
            null
        }
    }

    private var lastLiveLog = ""

    /** Writes one line to the log about how the system treats the focus notification (read by the emulator test). */
    private fun logLiveStatus(context: Context) {
        val app = context.applicationContext
        ToDoApp.scope.launch {
            delay(800)
            val line = try {
                val nm = app.getSystemService(NotificationManager::class.java)
                val n = nm?.activeNotifications?.firstOrNull { it.id == ID_FOCUS }?.notification
                val promotable = n?.let { it.javaClass.getMethod("hasPromotableCharacteristics").invoke(it) as? Boolean }
                val flag = android.app.Notification::class.java.getField("FLAG_PROMOTED_ONGOING").getInt(null)
                val promoted = n != null && (n.flags and flag) != 0
                "canPost=${canPostLiveUpdates(app)} promotable=$promotable promotedFlag=$promoted posted=${n != null}" +
                    " flags=0x" + Integer.toHexString(n?.flags ?: 0) +
                    " progress=" + (n?.extras?.getInt("android.progress", -1) ?: -1)
            } catch (e: Throwable) {
                "status unavailable: $e"
            }
            if (line != lastLiveLog) {
                lastLiveLog = line
                Log.i("ToDoLive", line)
            }
        }
    }

    // ------------------------------------------------------------ next up (ongoing)

    private fun postNextUp(context: Context, d: AppData, now: Long) {
        val zone = ZoneId.systemDefault()
        val nm = NotificationManagerCompat.from(context)
        val today = Planner.dayOf(now, zone)
        val block = Queries.currentOrNext(d, today, Planner.minuteOf(now, zone))
        if (block == null) {
            nm.cancel(ID_NEXT_UP)
            return
        }
        val h24 = ClockPref.is24(context, d.settings)
        val start = Planner.blockStart(block, zone)
        val end = Planner.blockEnd(block, zone)
        val running = now >= start && now < end
        val hue = Hues.of(block.colorKey)
        val count = Queries.tasksOfBlock(d, block.id).count { !it.done }
        val tasksText = if (count == 0) "" else " · " + (if (count == 1) "1 task" else "$count tasks")
        val text = if (running) {
            "Now · until " + Fmt.clock(block.endMin, h24) + tasksText
        } else {
            "Next · " + Fmt.clock(block.startMin, h24) + " · " + Fmt.duration(block.durationMin) + tasksText
        }
        val b = NotificationCompat.Builder(context, Channels.NEXT_UP)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setColor(hue.accentArgb)
            .setContentTitle(block.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(if (running) end else start)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(openApp(context, ID_NEXT_UP))
        applyPrivacy(context, b, d.settings, Channels.NEXT_UP, "Next up")
        notify(context, ID_NEXT_UP, b.build())
    }

    /** Keeps the focus and "next up" notifications in line with the data. Called after every change. */
    fun syncOngoing(context: Context, d: AppData) {
        val nm = NotificationManagerCompat.from(context)
        val now = System.currentTimeMillis()
        val f = d.focus
        if (f != null && f.endsAt > now) {
            postFocus(context, d, f)
            nm.cancel(ID_NEXT_UP)
            return
        }
        nm.cancel(ID_FOCUS)
        if (d.settings.nextUpOngoing) postNextUp(context, d, now) else nm.cancel(ID_NEXT_UP)
    }

    /** Used by the settings screen to open the system page of a channel. */
    fun manager(context: Context): NotificationManager? = context.getSystemService(NotificationManager::class.java)
}
