package com.shdurazi.todo.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shdurazi.todo.R
import com.shdurazi.todo.core.Keys
import com.shdurazi.todo.ui.Res
import com.shdurazi.todo.ui.components.Glyph
import com.shdurazi.todo.ui.components.ToggleSwitch
import com.shdurazi.todo.ui.components.pressable
import com.shdurazi.todo.ui.theme.Fonts
import com.shdurazi.todo.ui.theme.Hues
import com.shdurazi.todo.ui.theme.Ink
import com.shdurazi.todo.ui.theme.Txt
import kotlinx.coroutines.delay

/** Shared building blocks of the editor sheets (they all live on the dark card surface). */

@Composable
fun SheetTitle(text: String) {
    Txt(
        text = text,
        size = 24.sp,
        weight = FontWeight.Bold,
        display = true,
        tracking = (-0.6).sp,
        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
    )
}

@Composable
fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    accent: Color,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp,
    weight: FontWeight = FontWeight.SemiBold,
    color: Color = Ink.Text,
    singleLine: Boolean = true,
    autoFocus: Boolean = false,
    onDone: (() -> Unit)? = null
) {
    val requester = remember { FocusRequester() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().focusRequester(requester),
        textStyle = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = Fonts.Inter,
            letterSpacing = (-0.2).sp
        ),
        singleLine = singleLine,
        cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Txt(text = placeholder, color = Ink.TextFaint, size = size, weight = weight)
                }
                inner()
            }
        }
    )
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(280)
            try {
                requester.requestFocus()
            } catch (e: IllegalStateException) {
                // not attached yet
            }
        }
    }
}

/** Rounded block that groups form rows. */
@Composable
fun FormGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.Button),
        content = content
    )
}

@Composable
fun GroupDivider() {
    Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(Ink.Hairline))
}

@Composable
fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Ink.TextDim,
    chevron: Boolean = true,
    onTap: (() -> Unit)? = null
) {
    val base = modifier.fillMaxWidth().heightIn(min = 52.dp)
    Row(
        modifier = (if (onTap != null) base.pressable(pressedScale = 0.99f, label = label) { onTap() } else base)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Txt(text = label, size = 17.sp, modifier = Modifier.weight(1f))
        Txt(text = value, size = 16.sp, color = valueColor, modifier = Modifier.weight(1f, fill = false))
        if (chevron && onTap != null) {
            Glyph(R.drawable.ic_ui_chevron_right, 18.dp, Ink.TextFaint, Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Txt(text = label, size = 17.sp, modifier = Modifier.weight(1f))
        ToggleSwitch(checked = checked, onChange = onChange, accent = accent)
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, accent: Color, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .pressable(pressedScale = 0.94f, label = text) { onTap() }
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent else Ink.CardLift)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Txt(
            text = text,
            size = 15.sp,
            weight = FontWeight.SemiBold,
            color = if (selected) Color.Black else Ink.Text
        )
    }
}

/** One horizontally scrolling row of chips (projects, quick dates, durations ...). */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
fun SheetButton(
    text: String,
    fill: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onTap: () -> Unit
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .pressable(pressedScale = 0.97f, label = text) { onTap() }
            .clip(RoundedCornerShape(27.dp))
            .background(fill),
        contentAlignment = Alignment.Center
    ) {
        Txt(text = text, color = textColor, size = 17.sp, weight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionLabel(text: String) {
    Txt(
        text = text,
        size = 13.sp,
        color = Ink.TextDim,
        weight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
fun ColorDots(selected: String, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (key in Keys.colors) {
            val isSelected = key == selected
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .pressable(pressedScale = 0.88f, label = key) { onPick(key) }
                    .clip(CircleShape)
                    .then(if (isSelected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                    .padding(if (isSelected) 5.dp else 0.dp)
                    .clip(CircleShape)
                    .background(Hues.of(key).accent)
            )
        }
    }
}

@Composable
fun IconGrid(selected: String, accent: Color, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (rowKeys in Keys.icons.chunked(5)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (key in rowKeys) {
                    val isSelected = key == selected
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .pressable(pressedScale = 0.9f, label = key) { onPick(key) }
                            .clip(CircleShape)
                            .background(if (isSelected) accent else Ink.Button),
                        contentAlignment = Alignment.Center
                    ) {
                        Glyph(Res.glyph(key), 26.dp, if (isSelected) Color.White else Ink.TextDim)
                    }
                }
                // keep the last row aligned with the others
                repeat(5 - rowKeys.size) { Spacer(modifier = Modifier.width(52.dp)) }
            }
        }
    }
}

@Composable
fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
