package com.shdurazi.todo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shdurazi.todo.core.Planner
import com.shdurazi.todo.notify.FocusController
import com.shdurazi.todo.notify.ReminderEngine
import com.shdurazi.todo.ui.AppRoot
import com.shdurazi.todo.ui.Screen
import com.shdurazi.todo.ui.Sheet
import com.shdurazi.todo.ui.UiState
import java.time.ZoneId
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var ui: UiState

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        ui = UiState(ToDoApp.repo(this))
        ui.day = Planner.dayOf(System.currentTimeMillis(), ZoneId.systemDefault())
        if (savedInstanceState == null) handleIntent(intent)
        setContent { AppRoot(ui) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        ToDoApp.of(this).foreground = true
        // catches up on anything that came due while the app was not running and re-arms the alarm
        val app = applicationContext
        ToDoApp.scope.launch {
            try {
                ReminderEngine.fire(app)
            } catch (e: Exception) {
                // never block the UI on a reminder problem
            }
        }
    }

    override fun onStop() {
        super.onStop()
        ToDoApp.of(this).foreground = false
        ToDoApp.repo(this).flush()
    }

    /** Notification taps, widget taps, the launcher shortcut and the quick settings tile all land here. */
    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val repo = ToDoApp.repo(this)
        when (action) {
            ACTION_QUICK_ADD -> {
                ui.screen = Screen.Home
                ui.sheet = null
                ui.quickAddRequest += 1
            }
            ACTION_OPEN_TASK -> {
                val id = intent.getStringExtra(EXTRA_TASK_ID)
                if (id != null && repo.data.tasks.any { it.id == id }) {
                    ui.screen = Screen.Home
                    ui.sheet = Sheet.EditTask(id)
                }
            }
            ACTION_START_FOCUS -> {
                val id = intent.getStringExtra(EXTRA_BLOCK_ID)
                if (id != null && (repo.data.focus?.blockId == id || FocusController.start(this, id))) {
                    ui.sheet = null
                    ui.screen = Screen.Focus
                }
            }
            ACTION_OPEN_FOCUS -> {
                if (repo.data.focus != null) {
                    ui.sheet = null
                    ui.screen = Screen.Focus
                }
            }
        }
        // the same intent must not be replayed if the activity is recreated
        setIntent(Intent(Intent.ACTION_MAIN))
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.shdurazi.todo.action.QUICK_ADD"
        const val ACTION_OPEN_FOCUS = "com.shdurazi.todo.action.OPEN_FOCUS"
        const val ACTION_START_FOCUS = "com.shdurazi.todo.action.START_FOCUS"
        const val ACTION_OPEN_TASK = "com.shdurazi.todo.action.OPEN_TASK"
        const val EXTRA_BLOCK_ID = "block_id"
        const val EXTRA_TASK_ID = "task_id"
    }
}
