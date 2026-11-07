package com.shdurazi.todo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shdurazi.todo.ui.theme.Ink
import com.shdurazi.todo.ui.theme.Txt
import kotlin.math.sin
import kotlin.random.Random

private class Piece(
    val x0: Float,
    val y0: Float,
    val vx: Float,
    val vy: Float,
    val sway: Float,
    val phase: Float,
    val rot0: Float,
    val rotSpeed: Float,
    val w: Float,
    val h: Float,
    val color: Color,
    val round: Boolean
)

private val confettiColors = listOf(
    Color(0xFF30D158), Color(0xFF5D74FE), Color(0xFF40C8E0), Color(0xFFFFD60A),
    Color(0xFFFF8A24), Color(0xFFFF375F), Color(0xFFBF5AF2)
)

/** Falls over everything for ~2.6 s each time [trigger] changes to a new non-zero value. */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier.fillMaxSize()) {
    if (trigger == 0) return
    val progress = remember(trigger) { Animatable(0f) }
    val pieces = remember(trigger) {
        val rnd = Random(trigger * 7919)
        List(110) {
            Piece(
                x0 = rnd.nextFloat(),
                y0 = -rnd.nextFloat() * 0.35f,
                vx = (rnd.nextFloat() - 0.5f) * 0.35f,
                vy = 0.12f + rnd.nextFloat() * 0.3f,
                sway = 0.01f + rnd.nextFloat() * 0.03f,
                phase = rnd.nextFloat() * 6.28f,
                rot0 = rnd.nextFloat() * 360f,
                rotSpeed = (rnd.nextFloat() - 0.5f) * 900f,
                w = 6f + rnd.nextFloat() * 6f,
                h = 9f + rnd.nextFloat() * 8f,
                color = confettiColors[rnd.nextInt(confettiColors.size)],
                round = rnd.nextInt(4) == 0
            )
        }
    }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 2600, easing = LinearEasing))
    }
    Canvas(modifier = modifier) {
        val p = progress.value
        val t = p * 2.6f
        val fade = if (p > 0.78f) 1f - (p - 0.78f) / 0.22f else 1f
        val d = density
        for (c in pieces) {
            val x = (c.x0 + c.vx * t + sin(t * 3.2f + c.phase) * c.sway) * size.width
            val y = (c.y0 + c.vy * t + 0.16f * t * t) * size.height
            if (y < -40f || y > size.height + 40f) continue
            val pw = c.w * d
            val ph = c.h * d
            val col = c.color.copy(alpha = fade.coerceIn(0f, 1f))
            rotate(degrees = c.rot0 + c.rotSpeed * t, pivot = Offset(x, y)) {
                if (c.round) {
                    drawCircle(color = col, radius = pw / 2f, center = Offset(x, y))
                } else {
                    drawRect(color = col, topLeft = Offset(x - pw / 2f, y - ph / 2f), size = Size(pw, ph))
                }
            }
        }
    }
}

/** Bottom pill with a message and an optional action ("Undo"). */
@Composable
fun ToastPill(text: String, actionLabel: String?, accent: Color, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .widthIn(max = 360.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF2C2C2E))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Txt(text = text, size = 15.sp, color = Ink.Text, weight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
        if (actionLabel != null) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .pressable(pressedScale = 0.92f) { onAction() }
            ) {
                Txt(text = actionLabel, size = 15.sp, color = accent, weight = FontWeight.Bold)
            }
        }
    }
}
