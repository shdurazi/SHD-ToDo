package com.shdurazi.todo.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shdurazi.todo.R
import com.shdurazi.todo.ui.Res
import com.shdurazi.todo.ui.theme.Hue
import com.shdurazi.todo.ui.theme.Ink

/**
 * Tap (and optional long-press) without a ripple; the element shrinks slightly while it is held,
 * which is the feedback the original design uses.
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    label: String? = null,
    onLongPress: (() -> Unit)? = null,
    onTap: () -> Unit
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "press"
    )
    val click by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    val hasLong = onLongPress != null
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .semantics(mergeDescendants = true) {
            role = Role.Button
            if (label != null) contentDescription = label
            onClick(label = null) {
                click()
                true
            }
        }
        .pointerInput(enabled, hasLong) {
            if (enabled) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onLongPress = if (hasLong) {
                        { _: Offset -> long?.invoke() }
                    } else {
                        null
                    },
                    onTap = { click() }
                )
            }
        }
}

/** A tintable vector from res/drawable. */
@Composable
fun Glyph(res: Int, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint)
    )
}

/** Solid coloured circle with a white glyph, as on the timeline blocks. */
@Composable
fun Badge(iconKey: String, hue: Hue, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(hue.badge),
        contentAlignment = Alignment.Center
    ) {
        Glyph(Res.glyph(iconKey), size * 0.62f, Color.White)
    }
}

/** Round checkbox: a ring that fills with the accent colour and a tick when checked. */
@Composable
fun CheckRing(
    checked: Boolean,
    accent: Color,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
    ringColor: Color = Ink.Ring
) {
    val fill by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "ring"
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 1.8.dp.toPx()
            val r = (this.size.minDimension - stroke) / 2f
            if (fill > 0f) {
                drawCircle(color = accent.copy(alpha = fill), radius = r + stroke / 2f)
            }
            drawCircle(
                color = ringColor.copy(alpha = 1f - fill),
                radius = r,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        if (fill > 0.01f) {
            Glyph(R.drawable.ic_ui_check, size * 0.72f, Color.White.copy(alpha = fill))
        }
    }
}

/** Compact on/off switch in the app's style. */
@Composable
fun ToggleSwitch(checked: Boolean, onChange: (Boolean) -> Unit, accent: Color, modifier: Modifier = Modifier) {
    val knob by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = tween(durationMillis = 140),
        label = "knob"
    )
    Box(
        modifier = modifier
            .size(width = 50.dp, height = 30.dp)
            .pressable(pressedScale = 0.95f) { onChange(!checked) }
            .clip(CircleShape)
            .background(if (checked) accent else Color(0xFF3A3A3C))
    ) {
        Box(
            modifier = Modifier
                .offset(x = knob, y = 2.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** Thin horizontal separator for the dark cards. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier, color: Color = Ink.Hairline) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(color))
}
