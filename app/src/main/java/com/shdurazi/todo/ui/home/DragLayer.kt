package com.shdurazi.todo.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shdurazi.todo.ui.UiState
import com.shdurazi.todo.ui.components.CheckRing
import com.shdurazi.todo.ui.theme.Hues
import com.shdurazi.todo.ui.theme.Ink
import com.shdurazi.todo.ui.theme.Txt
import kotlin.math.roundToInt

/**
 * The lifted row that follows the finger. It is only drawn (no pointer handling): the drag itself is
 * driven by the row that was long-pressed, which keeps receiving the pointer events.
 */
@Composable
fun DragLayer(ui: UiState) {
    val d = ui.drag
    if (!d.active) return
    val density = LocalDensity.current
    val hue = Hues.of(d.accentKey)
    val shape = RoundedCornerShape(22.dp)
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .offset { IntOffset((d.pointer.x - d.grab.x).roundToInt(), (d.pointer.y - d.grab.y).roundToInt()) }
                .width(with(density) { d.ghostWidthPx.toDp() })
                .height(46.dp)
                .graphicsLayer {
                    scaleX = 1.03f
                    scaleY = 1.03f
                }
                .shadow(14.dp, shape, clip = false)
                .background(Color(0xFF111113), shape)
                .border(1.dp, Color(0xFF3A3A3C), shape)
                .padding(start = 6.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(42.dp), contentAlignment = Alignment.Center) {
                CheckRing(checked = false, accent = hue.accent, size = 21.dp)
            }
            Txt(text = d.title, color = Ink.White, size = 17.sp)
        }
    }
}
