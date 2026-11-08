package com.wanderwildwood.josho

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.josho.ui.EngraverApp
import com.wanderwildwood.josho.ui.houseTypography
import com.wanderwildwood.josho.ui.monochrome

/**
 * The one screen stack: the folder's scores, a score open in the editor, settings.
 *
 * Test hook (emulator only, debug builds): `--ez compile true` compiles the open score once the
 * app is up, so a test can measure a compile without touching the screen; `--ez autocompile true`
 * with it has Guile compile LilyPond's Scheme into files/guile-cache (how that cache is made).
 */
class MainActivity : ComponentActivity() {
    private val turner = TurnWithPhone(this)
    private var turning = false

    /**
     * Hold the screen the way the reader chose (as Typewriter does). Three of the four are an
     * orientation asked for by name; "Turns with the phone" watches the accelerometer itself,
     * starting from the way the screen is now so that a phone lying on a desk stays as it was.
     */
    fun turn(turn: Turn) {
        if (turn == Turn.DEVICE) {
            TurnWithPhone.shown = when {
                resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE -> TurnWithPhone.PORTRAIT
                requestedOrientation == TurnWithPhone.REVERSE_LANDSCAPE -> TurnWithPhone.REVERSE_LANDSCAPE
                else -> TurnWithPhone.LANDSCAPE
            }
            turner.set(on = true)
            turning = true
        } else {
            // Stopped by holding the screen where it is rather than by switching off, which asks
            // for portrait on the way and would turn the panel twice.
            if (turning) turner.set(on = true, held = true)
            turning = false
            requestedOrientation = turn.activityInfo
        }
    }

    override fun onResume() {
        super.onResume()
        turner.resume()
    }

    override fun onPause() {
        turner.pause()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Asked for before the first frame, so the panel is drawn once, the right way round.
        turn(Prefs(this).turn)
        val autoCompile = BuildConfig.DEBUG && intent.getBooleanExtra("compile", false)
        // Debug only: compile with Guile's auto-compilation on, to make the shipped guile-cache/.
        val autoCompileScheme = BuildConfig.DEBUG && intent.getBooleanExtra("autocompile", false)
        setContent {
            // A steady cursor: a blinking one repaints the panel twice a second for as long as
            // the editor has focus.
            CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                ThemeMMD(colorScheme = monochrome, typography = houseTypography) {
                    EngraverApp(
                        autoCompile = autoCompile,
                        autoCompileScheme = autoCompileScheme,
                        onTurn = ::turn,
                        layout = if (BuildConfig.DEBUG) intent.getStringExtra("layout") else null,
                    )
                }
            }
        }
    }
}
