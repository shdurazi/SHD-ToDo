package com.shdurazi.todo.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shdurazi.todo.core.Block
import com.shdurazi.todo.core.Planner
import com.shdurazi.todo.core.Queries
import com.shdurazi.todo.notify.ClockPref
import com.shdurazi.todo.notify.FocusController
import com.shdurazi.todo.ui.components.BottomSheet
import com.shdurazi.todo.ui.components.Confetti
import com.shdurazi.todo.ui.components.ToastPill
import com.shdurazi.todo.ui.focus.FocusScreen
import com.shdurazi.todo.ui.home.HomeScreen
import com.shdurazi.todo.ui.settings.SettingsScreen
import com.shdurazi.todo.ui.sheets.BlockEditorSheet
import com.shdurazi.todo.ui.sheets.DayPickerSheet
import com.shdurazi.todo.ui.sheets.ProjectEditorSheet
import com.shdurazi.todo.ui.sheets.TaskEditorSheet
import com.shdurazi.todo.ui.sheets.WhenPickerSheet
import com.shdurazi.todo.ui.theme.Hues
import com.shdurazi.todo.ui.theme.Ink
import java.time.ZoneId
import kotlinx.coroutines.delay

/** Wires the data, the screens, the sheets and the overlays together. */
@Composable
fun AppRoot(ui: UiState) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val data by ui.repo.state.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }

    // wall clock, refreshed at every full minute (due labels, "today", ...)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val t = System.currentTimeMillis()
            now = t
            delay(60_000L - (t % 60_000L) + 30L)
        }
    }
    // every time the app comes back it shows today again
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        now = System.currentTimeMillis()
        ui.day = Planner.dayOf(now, zone)
    }

    val h24 = ClockPref.is24(ctx, data.settings)
    val accent = Hues.of(Queries.project(data, data.settings.selectedProjectId)?.colorKey).accent

    // remember the last session so the focus screen can still show it once it has ended
    LaunchedEffect(data.focus) { data.focus?.let { ui.lastFocus = it } }

    // light status-bar icons over the dark surfaces, dark ones over the white timeline card
    val lightBars = ui.screen == Screen.Home &&
        (ui.drag.active || (!ui.quick.focused && ui.timeline != TimelineMode.Collapsed))
    SideEffect {
        val window = (ctx as? ComponentActivity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = lightBars
            controller.isAppearanceLightNavigationBars = false
        }
    }

    // ask for notifications once, shortly after the very first start (Android 13+)
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val prefs = ctx.getSharedPreferences("todo-prefs", Context.MODE_PRIVATE)
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted && !prefs.getBoolean("asked_notifications", false)) {
                delay(1200)
                prefs.edit().putBoolean("asked_notifications", true).apply()
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun startFocus(block: Block) {
        if (data.focus?.blockId != block.id) FocusController.start(ctx, block.id)
        ui.screen = Screen.Focus
    }

    Box(modifier = Modifier.fillMaxSize().background(Ink.Black)) {
        HomeScreen(
            ui = ui,
            data = data,
            now = now,
            h24 = h24,
            onOpenSettings = { ui.screen = Screen.Settings },
            onStartFocus = { block -> startFocus(block) }
        )

        AnimatedVisibility(
            visible = ui.screen == Screen.Focus,
            enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 14 },
            exit = fadeOut(tween(180))
        ) {
            Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                FocusScreen(ui = ui, data = data, onBack = { ui.screen = Screen.Home })
            }
        }
        AnimatedVisibility(
            visible = ui.screen == Screen.Settings,
            enter = slideInHorizontally(tween(280)) { it },
            exit = slideOutHorizontally(tween(240)) { it }
        ) {
            Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
                SettingsScreen(ui = ui, data = data, h24 = h24, onBack = { ui.screen = Screen.Home })
            }
        }
        // registered after the screens, so it wins over the home screen's own back handling
        BackHandler(enabled = ui.screen != Screen.Home) { ui.screen = Screen.Home }

        // sheets keep their last content while they slide away
        var lastSheet by remember { mutableStateOf<Sheet?>(null) }
        LaunchedEffect(ui.sheet) { ui.sheet?.let { lastSheet = it } }
        val sheetShown = ui.sheet ?: lastSheet
        BottomSheet(visible = ui.sheet != null, onDismiss = { ui.sheet = null }) {
            when (val s = sheetShown) {
                is Sheet.EditTask -> TaskEditorSheet(ui, data, s.taskId, now, h24)
                is Sheet.EditBlock -> BlockEditorSheet(ui, data, s, now, h24) { b -> startFocus(b) }
                is Sheet.NewBlock -> BlockEditorSheet(ui, data, s, now, h24) { b -> startFocus(b) }
                is Sheet.EditProject -> ProjectEditorSheet(ui, data, s.projectId)
                is Sheet.WhenPicker -> WhenPickerSheet(ui, s, now, h24)
                is Sheet.DayPicker -> DayPickerSheet(ui, data, now)
                null -> Unit
            }
        }

        // toast
        var lastToast by remember { mutableStateOf<ToastMsg?>(null) }
        LaunchedEffect(ui.toast?.id) {
            val t = ui.toast
            if (t != null) {
                lastToast = t
                delay(if (t.action != null) 4500L else 2600L)
                if (ui.toast?.id == t.id) ui.toast = null
            }
        }
        val toastShown = ui.toast ?: lastToast
        AnimatedVisibility(
            visible = ui.toast != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 92.dp),
            enter = fadeIn(tween(150)) + slideInVertically(tween(200)) { it / 2 },
            exit = fadeOut(tween(160))
        ) {
            if (toastShown != null) {
                ToastPill(
                    text = toastShown.text,
                    actionLabel = toastShown.actionLabel,
                    accent = accent,
                    onAction = {
                        toastShown.action?.invoke()
                        ui.toast = null
                    }
                )
            }
        }

        Confetti(trigger = ui.confetti)
    }
}
