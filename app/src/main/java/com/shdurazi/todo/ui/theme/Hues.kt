package com.shdurazi.todo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Everything that is coloured by a colour key (core.Keys.colors).
 * [accent] is used on the black UI (checkboxes, "New To Do", the focus countdown),
 * the others on the light timeline blocks.
 */
class Hue(
    val accent: Color,
    val badge: Color,
    val block: Color,
    val blockHover: Color,
    val pill: Color,
    val pillText: Color,
    val ink: Color,
    val inkSoft: Color
) {
    val accentArgb: Int get() = accent.toArgb()
    val badgeArgb: Int get() = badge.toArgb()
    val blockArgb: Int get() = block.toArgb()
    val inkArgb: Int get() = ink.toArgb()
}

object Hues {
    private fun hue(
        accent: Long, badge: Long, block: Long, hover: Long, pill: Long, pillText: Long, ink: Long, inkSoft: Long
    ) = Hue(
        Color(accent), Color(badge), Color(block), Color(hover), Color(pill), Color(pillText), Color(ink), Color(inkSoft)
    )

    private val all: Map<String, Hue> = mapOf(
        "green" to hue(0xFF30D158, 0xFF2FB36A, 0xFFC8E4D5, 0xFFB8DDC8, 0xFFADDBBD, 0xFF3F9A63, 0xFF0B3B1E, 0xFF4C7A5E),
        "blue" to hue(0xFF5D74FE, 0xFF1181EF, 0xFFBEDCF4, 0xFFAFD3F1, 0xFF95C8F8, 0xFF1E7CB2, 0xFF0B2250, 0xFF436789),
        "teal" to hue(0xFF40C8E0, 0xFF31B3CA, 0xFFBEE3EB, 0xFFAEDBE5, 0xFF96D8E5, 0xFF199CA4, 0xFF00333B, 0xFF3F7C86),
        "yellow" to hue(0xFFFFD60A, 0xFFF7CE3F, 0xFFF5E9BF, 0xFFF2E0A0, 0xFFF7E196, 0xFFC9A21F, 0xFF564207, 0xFF8A7330),
        "orange" to hue(0xFFFF8A24, 0xFFF08833, 0xFFF6DAC4, 0xFFF9D2B3, 0xFFFBCB9E, 0xFFD9822A, 0xFF5A3210, 0xFF98704D),
        "pink" to hue(0xFFFF375F, 0xFFEE4678, 0xFFF6D3DD, 0xFFF2C3D0, 0xFFF3B7C8, 0xFFC72F63, 0xFF5C1230, 0xFF94566B),
        "purple" to hue(0xFFBF5AF2, 0xFF9D5CE0, 0xFFE3D6F5, 0xFFD8C6F0, 0xFFCFB9F0, 0xFF7B3FC4, 0xFF2F1356, 0xFF6B5088),
        "red" to hue(0xFFFF453A, 0xFFEE4B3F, 0xFFF6D1CD, 0xFFF2C0BA, 0xFFF1B5AF, 0xFFC8352B, 0xFF5B1410, 0xFF93514C)
    )

    private val fallback: Hue = all.getValue("blue")

    fun of(key: String?): Hue = if (key == null) fallback else (all[key] ?: fallback)
}
