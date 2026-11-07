package com.shdurazi.todo.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.input.TextFieldValue
import com.shdurazi.todo.core.FocusSession
import com.shdurazi.todo.core.Repository

enum class Screen { Home, Focus, Settings }

/** How much of the screen the white timeline card takes. */
enum class TimelineMode { Collapsed, Half, Full }

sealed interface Sheet {
    data class EditTask(val taskId: String) : Sheet
    data class EditBlock(val blockId: String) : Sheet
    data class NewBlock(val day: Long, val startMin: Int, val taskId: String?) : Sheet
    data class EditProject(val projectId: String?) : Sheet
    data class WhenPicker(val day: Long?, val minute: Int?, val onResult: (Long?, Int?) -> Unit) : Sheet
    object DayPicker : Sheet
}

class ToastMsg(val id: Long, val text: String, val actionLabel: String?, val action: (() -> Unit)?)

/** Drag-and-drop of a task from the to-do card onto the timeline (block / empty space) or the bin. */
@Stable
class DragState {
    var active by mutableStateOf(false)
    var taskId by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var accentKey by mutableStateOf("green")

    /** Finger position in root coordinates. */
    var pointer by mutableStateOf(Offset.Zero)
    var ghostWidthPx by mutableFloatStateOf(0f)

    /** Where inside the row the finger took hold, so the ghost does not jump. */
    var grab by mutableStateOf(Offset.Zero)
    var hoverBlock by mutableStateOf<String?>(null)
    var overDelete by mutableStateOf(false)
    var overTimeline by mutableStateOf(false)

    // layout registries, not state: they are only read when the finger moves or lifts
    val blockCoords = HashMap<String, LayoutCoordinates>()
    var timelineCoords: LayoutCoordinates? = null
    var deleteCoords: LayoutCoordinates? = null

    /** Converts a root-space position inside the timeline into a minute of the day (set by the timeline). */
    var minuteAt: ((Offset) -> Int)? = null

    /** Called while dragging near the top / bottom edge of the timeline: -1, 0 or +1 with speed factor. */
    var autoScroll by mutableFloatStateOf(0f)

    fun begin(id: String, text: String, colorKey: String, start: Offset, widthPx: Float, grabOffset: Offset) {
        taskId = id
        title = text
        accentKey = colorKey
        pointer = start
        ghostWidthPx = widthPx
        grab = grabOffset
        active = true
        refresh()
    }

    fun moveTo(p: Offset) {
        pointer = p
        refresh()
    }

    fun refresh() {
        val p = pointer
        val tl = timelineCoords
        val inTimeline = tl != null && tl.isAttached && tl.boundsInRoot().contains(p)
        var hit: String? = null
        if (inTimeline) {
            for ((id, c) in blockCoords) {
                if (c.isAttached && c.boundsInRoot().contains(p)) {
                    hit = id
                    break
                }
            }
        }
        val del = deleteCoords
        overDelete = del != null && del.isAttached && del.boundsInRoot().contains(p)
        overTimeline = inTimeline
        hoverBlock = hit
        // edge auto-scroll
        autoScroll = 0f
        if (inTimeline && tl != null) {
            val b = tl.boundsInRoot()
            val zone = 90f
            if (p.y < b.top + zone) autoScroll = -((b.top + zone - p.y) / zone).coerceIn(0f, 1f)
            else if (p.y > b.bottom - zone) autoScroll = ((p.y - (b.bottom - zone)) / zone).coerceIn(0f, 1f)
        }
    }

    fun end() {
        active = false
        taskId = null
        hoverBlock = null
        overDelete = false
        overTimeline = false
        autoScroll = 0f
    }
}

/** State of the "New To Do" field and its toolbar. */
@Stable
class QuickAddState {
    var text by mutableStateOf(TextFieldValue(""))
    var focused by mutableStateOf(false)
    var projectId by mutableStateOf<String?>(null)
    var day by mutableStateOf<Long?>(null)
    var minute by mutableStateOf<Int?>(null)
    var priority by mutableIntStateOf(0)
    var later by mutableStateOf(false)
    val focusRequester = FocusRequester()

    fun reset() {
        text = TextFieldValue("")
        projectId = null
        day = null
        minute = null
        priority = 0
        later = false
    }
}

@Stable
class UiState(val repo: Repository) {
    var screen by mutableStateOf(Screen.Home)

    /** Epoch day shown on the timeline. */
    var day by mutableLongStateOf(0L)
    var timeline by mutableStateOf(TimelineMode.Half)
    var sheet by mutableStateOf<Sheet?>(null)
    var toast by mutableStateOf<ToastMsg?>(null)
    var confetti by mutableIntStateOf(0)
    var quickAddRequest by mutableIntStateOf(0)
    var showCompleted by mutableStateOf(false)
    var lastFocus by mutableStateOf<FocusSession?>(null)

    val drag = DragState()
    val quick = QuickAddState()

    private var toastSeq = 0L

    fun showToast(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        toastSeq += 1
        toast = ToastMsg(toastSeq, text, actionLabel, action)
    }

    fun burst() {
        confetti += 1
    }
}
