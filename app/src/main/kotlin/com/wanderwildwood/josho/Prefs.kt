package com.wanderwildwood.josho

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri

/**
 * Which way up the screen is held. The Kompakt never turns by itself, so three of these are an
 * orientation asked for by name, and DEVICE watches the accelerometer ([TurnWithPhone]).
 * UPRIGHT is the default; ACROSS is the way to write with a keyboard; AROUND the same turned half
 * a turn, for a keyboard case with the hinge on the other side.
 */
enum class Turn(val activityInfo: Int) {
    UPRIGHT(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    ACROSS(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    AROUND(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
    DEVICE(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
}

/** The two things Engraver remembers: the folder, and how large the editor's text is. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("engraver", Context.MODE_PRIVATE)

    var folder: Uri?
        get() = p.getString("folder", null)?.let(Uri::parse)
        set(v) = p.edit().putString("folder", v?.toString()).apply()

    /** An index into [TEXT_SIZES]. */
    var textSize: Int
        get() = p.getInt("textSize", 2).coerceIn(TEXT_SIZES.indices)
        set(v) = p.edit().putInt("textSize", v).apply()

    var turn: Turn
        get() = runCatching { Turn.valueOf(p.getString("turn", null) ?: "") }.getOrDefault(Turn.UPRIGHT)
        set(v) = p.edit().putString("turn", v.name).apply()

    /** The score last open, by its path in the folder, so the app opens where it was left. */
    var lastScore: String?
        get() = p.getString("lastScore", null)
        set(v) = p.edit().putString("lastScore", v).apply()

    companion object {
        /**
         * Editor text sizes in sp. The house floor is 16 (STYLE.md); the editor goes under it on
         * purpose, as a choice the reader makes: a LilyPond score runs to hundreds of lines and
         * its writer asked for the smallest workable type. Like the calendar's month grid, this
         * is the exception, and only for the source text; the rest of the app stays at 16.
         */
        val TEXT_SIZES = listOf(11, 13, 16, 19)
    }
}
