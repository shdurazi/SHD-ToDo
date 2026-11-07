package com.shdurazi.todo.ui

import com.shdurazi.todo.R

/** Maps the string keys stored in the data file to drawable resources. */
object Res {
    fun glyph(key: String?): Int = when (key) {
        "briefcase" -> R.drawable.ic_glyph_briefcase
        "mail" -> R.drawable.ic_glyph_mail
        "pencil" -> R.drawable.ic_glyph_pencil
        "person" -> R.drawable.ic_glyph_person
        "bulb" -> R.drawable.ic_glyph_bulb
        "fork" -> R.drawable.ic_glyph_fork
        "hammer" -> R.drawable.ic_glyph_hammer
        "phone" -> R.drawable.ic_glyph_phone
        "check" -> R.drawable.ic_glyph_check
        "star" -> R.drawable.ic_glyph_star
        "book" -> R.drawable.ic_glyph_book
        "code" -> R.drawable.ic_glyph_code
        "heart" -> R.drawable.ic_glyph_heart
        "cart" -> R.drawable.ic_glyph_cart
        "plane" -> R.drawable.ic_glyph_plane
        else -> R.drawable.ic_glyph_bulb
    }

    /** Solid circle behind a glyph in widgets. */
    fun badge(colorKey: String?): Int = when (colorKey) {
        "green" -> R.drawable.w_badge_green
        "blue" -> R.drawable.w_badge_blue
        "teal" -> R.drawable.w_badge_teal
        "yellow" -> R.drawable.w_badge_yellow
        "orange" -> R.drawable.w_badge_orange
        "pink" -> R.drawable.w_badge_pink
        "purple" -> R.drawable.w_badge_purple
        "red" -> R.drawable.w_badge_red
        else -> R.drawable.w_badge_blue
    }

    /** Pastel rounded rectangle behind a block in widgets. */
    fun blockBg(colorKey: String?): Int = when (colorKey) {
        "green" -> R.drawable.w_block_green
        "blue" -> R.drawable.w_block_blue
        "teal" -> R.drawable.w_block_teal
        "yellow" -> R.drawable.w_block_yellow
        "orange" -> R.drawable.w_block_orange
        "pink" -> R.drawable.w_block_pink
        "purple" -> R.drawable.w_block_purple
        "red" -> R.drawable.w_block_red
        else -> R.drawable.w_block_blue
    }

    fun pillBg(colorKey: String?): Int = when (colorKey) {
        "green" -> R.drawable.w_pill_green
        "blue" -> R.drawable.w_pill_blue
        "teal" -> R.drawable.w_pill_teal
        "yellow" -> R.drawable.w_pill_yellow
        "orange" -> R.drawable.w_pill_orange
        "pink" -> R.drawable.w_pill_pink
        "purple" -> R.drawable.w_pill_purple
        "red" -> R.drawable.w_pill_red
        else -> R.drawable.w_pill_blue
    }
}
