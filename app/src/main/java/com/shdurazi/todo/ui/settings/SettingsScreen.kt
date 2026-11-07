package com.shdurazi.todo.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.shdurazi.todo.R
import com.shdurazi.todo.core.AppData
import com.shdurazi.todo.core.Codec
import com.shdurazi.todo.core.Fmt
import com.shdurazi.todo.core.Queries
import com.shdurazi.todo.notify.Notifier
import com.shdurazi.todo.notify.ReminderEngine
import com.shdurazi.todo.ui.UiState
import com.shdurazi.todo.ui.components.Glyph
import com.shdurazi.todo.ui.components.pressable
import com.shdurazi.todo.ui.sheets.ChipRow
import com.shdurazi.todo.ui.sheets.ChoiceChip
import com.shdurazi.todo.ui.sheets.FormGroup
import com.shdurazi.todo.ui.sheets.GroupDivider
import com.shdurazi.todo.ui.sheets.SectionLabel
import com.shdurazi.todo.ui.sheets.SwitchRow
import com.shdurazi.todo.ui.sheets.TimeWheels
import com.shdurazi.todo.ui.sheets.ValueRow
import com.shdurazi.todo.ui.theme.Hues
import com.shdurazi.todo.ui.theme.Ink
import com.shdurazi.todo.ui.theme.Txt
import java.time.LocalDate

private fun openNotificationSettings(ctx: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        // no settings app to handle it
    }
}

/** Android 16: the system page where Live Updates are switched on or off for this app (falls back to the app's notification page). */
private fun openLiveUpdateSettings(ctx: Context) {
    try {
        val action = Settings::class.java.getField("ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS").get(null) as String
        ctx.startActivity(Intent(action, Uri.parse("package:" + ctx.packageName)))
    } catch (e: Exception) {
        openNotificationSettings(ctx)
    }
}

private fun openExactAlarmSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT < 31) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))
    try {
        ctx.startActivity(intent)
    } catch (e: Exception) {
        // not available on this device
    }
}

@Composable
fun SettingsScreen(ui: UiState, data: AppData, h24: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = ui.repo
    val s = data.settings
    val accent = Hues.of(Queries.project(data, s.selectedProjectId)?.colorKey).accent

    // permissions can change while the system settings are open: look again whenever we come back
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh += 1 }
    val notificationsOn = remember(refresh) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val exactOk = remember(refresh) { ReminderEngine.canScheduleExact(ctx) }
    val liveOn = remember(refresh) { Notifier.canPostLiveUpdates(ctx) }
    var showSummaryTime by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh += 1
        if (!granted) {
            ui.showToast("Turn notifications on in the system settings", "Open") { openNotificationSettings(ctx) }
        }
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val saved = try {
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(repo.exportJson().toByteArray(Charsets.UTF_8))
                } != null
            } catch (e: Exception) {
                false
            }
            ui.showToast(if (saved) "Backup saved" else "Couldn't save the backup")
        }
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = try {
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: Exception) {
                null
            }
            val restored = if (text == null) {
                null
            } else {
                try {
                    Codec.decode(text)
                } catch (e: Exception) {
                    null
                }
            }
            if (restored == null || restored.projects.isEmpty()) {
                ui.showToast("That file isn't a ToDo backup")
            } else {
                val before = repo.data
                repo.replaceAll(restored)
                ui.showToast("Backup restored", "Undo") { repo.replaceAll(before) }
            }
        }
    }
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .pressable(pressedScale = 0.9f, label = "Back") { onBack() }
                    .clip(CircleShape)
                    .background(Ink.Card),
                contentAlignment = Alignment.Center
            ) {
                Glyph(R.drawable.ic_ui_chevron_left, 24.dp, Ink.Text)
            }
            Txt(
                text = "Settings",
                size = 28.sp,
                weight = FontWeight.Bold,
                display = true,
                tracking = (-0.8).sp,
                modifier = Modifier.padding(start = 14.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 40.dp)
        ) {
            // ------------------------------------------------------------------ notifications
            SectionLabel("Notifications")
            FormGroup {
                ValueRow(
                    label = "Notifications",
                    value = if (notificationsOn) "On" else "Off — tap to allow",
                    valueColor = if (notificationsOn) Ink.TextDim else Color(0xFFFF9F0A),
                    onTap = {
                        if (!notificationsOn) {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openNotificationSettings(ctx)
                            }
                        } else {
                            openNotificationSettings(ctx)
                        }
                    }
                )
                if (!exactOk) {
                    GroupDivider()
                    ValueRow(
                        label = "Exact alarms",
                        value = "Off — tap to allow",
                        valueColor = Color(0xFFFF9F0A),
                        onTap = { openExactAlarmSettings(ctx) }
                    )
                }
                if (liveOn != null) {
                    GroupDivider()
                    ValueRow(
                        label = "Live updates",
                        value = if (liveOn) "On" else "Off — tap to allow",
                        valueColor = if (liveOn) Ink.TextDim else Color(0xFFFF9F0A),
                        onTap = { openLiveUpdateSettings(ctx) }
                    )
                }
            }
            Txt(
                text = "Reminders for to-dos with a time, alerts before blocks and the daily summary all use notifications." +
                    (if (liveOn != null) {
                        " A running focus session shows up as a Live Update in the status bar. " +
                            "On OnePlus phones also switch ToDo on under Notifications & Quick Settings › Live Alerts."
                    } else {
                        ""
                    }),
                size = 13.sp,
                color = Ink.TextDim,
                maxLines = 8,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)
            )

            SectionLabel("Alert before a block starts")
            ChipRow {
                val leads = listOf("Off" to 0, "5 min" to 5, "10 min" to 10, "15 min" to 15, "30 min" to 30)
                for ((label, minutes) in leads) {
                    ChoiceChip(label, s.blockLeadMin == minutes, accent) {
                        repo.updateSettings { it.copy(blockLeadMin = minutes) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            FormGroup {
                SwitchRow("Daily summary", s.dailySummary, accent) { on ->
                    repo.updateSettings { it.copy(dailySummary = on) }
                }
                if (s.dailySummary) {
                    GroupDivider()
                    ValueRow(
                        label = "Summary time",
                        value = Fmt.clock(s.summaryMinute, h24),
                        valueColor = accent,
                        onTap = { showSummaryTime = !showSummaryTime }
                    )
                    if (showSummaryTime) {
                        TimeWheels(
                            minute = s.summaryMinute,
                            h24 = h24,
                            onChange = { m -> repo.updateSettings { it.copy(summaryMinute = m) } },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                GroupDivider()
                SwitchRow("Show what's next", s.nextUpOngoing, accent) { on ->
                    repo.updateSettings { it.copy(nextUpOngoing = on) }
                }
                GroupDivider()
                SwitchRow("To-do titles on lock screen", s.lockScreenDetails, accent) { on ->
                    repo.updateSettings { it.copy(lockScreenDetails = on) }
                }
            }
            Txt(
                text = "\"Show what's next\" keeps a quiet notification with your current or next block.",
                size = 13.sp,
                color = Ink.TextDim,
                maxLines = 3,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)
            )

            // ------------------------------------------------------------------ timeline
            SectionLabel("New blocks last")
            ChipRow {
                for (m in listOf(15, 30, 45, 60, 90)) {
                    ChoiceChip(Fmt.duration(m), s.defaultBlockMin == m, accent) {
                        repo.updateSettings { it.copy(defaultBlockMin = m) }
                    }
                }
            }
            SectionLabel("Time format")
            ChipRow {
                val formats = listOf("System" to 0, "24-hour" to 1, "12-hour" to 2)
                for ((label, mode) in formats) {
                    ChoiceChip(label, s.clockMode == mode, accent) {
                        repo.updateSettings { it.copy(clockMode = mode) }
                    }
                }
            }

            // ------------------------------------------------------------------ feel
            SectionLabel("Feedback")
            FormGroup {
                SwitchRow("Confetti", s.confetti, accent) { on ->
                    repo.updateSettings { it.copy(confetti = on) }
                }
                GroupDivider()
                SwitchRow("Haptics", s.haptics, accent) { on ->
                    repo.updateSettings { it.copy(haptics = on) }
                }
            }

            // ------------------------------------------------------------------ widgets
            SectionLabel("Widgets")
            FormGroup {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Txt(
                        text = "Long-press your home screen, choose Widgets and look for ToDo: Tasks, Timeline and Next up. " +
                            "On devices that support lock-screen widgets they can be placed there too.",
                        size = 15.sp,
                        color = Ink.TextDim,
                        maxLines = 6,
                        lineHeight = 21.sp
                    )
                }
            }

            // ------------------------------------------------------------------ data
            SectionLabel("Your data")
            FormGroup {
                ValueRow(
                    label = "Export backup",
                    value = "JSON file",
                    onTap = { exportBackup.launch("todo-backup-" + LocalDate.now() + ".json") }
                )
                GroupDivider()
                ValueRow(
                    label = "Restore backup",
                    value = "Choose file",
                    onTap = { importBackup.launch(arrayOf("*/*")) }
                )
                GroupDivider()
                val doneCount = data.tasks.count { it.done }
                ValueRow(
                    label = "Clear completed",
                    value = if (doneCount == 0) "Nothing to clear" else "$doneCount to-dos",
                    chevron = false,
                    onTap = if (doneCount == 0) null else {
                        {
                            repo.clearCompleted()
                            ui.showToast("Cleared $doneCount completed")
                        }
                    }
                )
            }
            Txt(
                text = "Everything stays on this phone, in one file inside the app.",
                size = 13.sp,
                color = Ink.TextDim,
                maxLines = 2,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp)
            )

            // ------------------------------------------------------------------ about
            SectionLabel("About")
            FormGroup {
                ValueRow(label = "ToDo", value = if (version.isEmpty()) "" else "Version $version", chevron = false)
                GroupDivider()
                ValueRow(label = "Typeface", value = "Inter · SIL Open Font License", chevron = false)
            }
        }
    }
}
