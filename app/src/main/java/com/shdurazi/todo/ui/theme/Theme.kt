package com.shdurazi.todo.ui.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.shdurazi.todo.R

object Ink {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Card = Color(0xFF1B1B1D)
    val CardLift = Color(0xFF2A2A2D)
    val Button = Color(0xFF262628)
    val Hairline = Color(0xFF2C2C2E)
    val Text = Color(0xFFF5F5F7)
    val TextDim = Color(0xFF8E8E93)
    val TextFaint = Color(0xFF5A5A5E)
    val Ring = Color(0xFF7C7C80)
    val Danger = Color(0xFFFF453A)
    val DangerFill = Color(0xFF1F0709)

    // light surfaces (timeline card)
    val Paper = Color(0xFFFFFFFF)
    val PaperText = Color(0xFF0B0B0C)
    val PaperDim = Color(0xFF9A9AA0)
    val PaperLine = Color(0xFFF0F0F2)
    val TodayDot = Color(0xFFFF4D5F)
}

object Fonts {
    val Inter = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold)
    )
    val Display = FontFamily(
        Font(R.font.inter_display_bold, FontWeight.Bold),
        Font(R.font.inter_display_extrabold, FontWeight.ExtraBold)
    )
}

/** Small text helper: one place that decides font, tracking and defaults. */
@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.Text,
    size: TextUnit = 17.sp,
    weight: FontWeight = FontWeight.Medium,
    display: Boolean = false,
    maxLines: Int = 1,
    align: TextAlign? = null,
    strike: Boolean = false,
    tracking: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = if (display) Fonts.Display else Fonts.Inter,
            letterSpacing = if (tracking != TextUnit.Unspecified) tracking else (-0.2).sp,
            lineHeight = lineHeight,
            textAlign = align ?: TextAlign.Unspecified,
            textDecoration = if (strike) TextDecoration.LineThrough else null
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun TxtAnnotated(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Ink.Text,
    size: TextUnit = 17.sp,
    weight: FontWeight = FontWeight.Medium,
    maxLines: Int = 1
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            fontFamily = Fonts.Inter,
            letterSpacing = (-0.2).sp
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
