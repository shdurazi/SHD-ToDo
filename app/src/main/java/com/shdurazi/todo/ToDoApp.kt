package com.shdurazi.todo

import android.app.Application
import android.content.Context
import com.shdurazi.todo.core.AppData
import com.shdurazi.todo.core.Repository
import com.shdurazi.todo.notify.Channels
import com.shdurazi.todo.notify.Sync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class ToDoApp : Application() {

    lateinit var repo: Repository
        private set

    /** True while an activity of ours is visible (used to avoid redundant heads-up notifications). */
    @Volatile
    var foreground: Boolean = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        Channels.ensure(this)
        repo = Repository(
            file = File(filesDir, "todo-data.json"),
            scope = scope,
            onPersisted = { data: AppData -> Sync.afterPersist(this, data) }
        )
        repo.load()
    }

    companion object {
        /** Long-lived scope for persistence and widget refreshes (never cancelled). */
        val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Volatile
        private var instance: ToDoApp? = null

        fun of(context: Context): ToDoApp = (context.applicationContext as ToDoApp)

        fun repo(context: Context): Repository = of(context).repo
    }
}
