package com.wanderwildwood.josho.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The log lines are LilyPond 2.26's own, copied from a compile of test/errors.ly on the emulator. */
class LogLinesTest {
    private val output = """
        Processing `score.ly'
        Parsing...
        score.ly:3:30: error: unknown command: `\undefinedthing'
        \relative c' { c4 d e f | g1
                                     \undefinedthing }
        score.ly:19:29: warning: bar check failed at: 1/2
        parts/motif.ily:2:5: error: syntax error, unexpected '}'

        fatal error: failed files: "score.ly"
    """.trimIndent()

    @Test fun locatedLinesCarryFileLineAndColumn() {
        val lines = parseLog(output)
        val first = lines.first { it.file != null }
        assertEquals("score.ly", first.file)
        assertEquals(3, first.line)
        assertEquals(30, first.column)
        assertEquals(LogLine.Kind.Error, first.kind)
        assertEquals(LogLine.Kind.Warning, lines.first { it.line == 19 }.kind)
        assertEquals("parts/motif.ily", lines.first { it.line == 2 }.file)
    }

    @Test fun otherLinesArePlainAndBlanksGo() {
        val lines = parseLog(output)
        assertNull(lines.first().file)
        assertEquals(LogLine.Kind.Plain, lines.first().kind)
        assertEquals(LogLine.Kind.Error, lines.last().kind)
        assertEquals(0, lines.count { it.text.isBlank() })
    }

    @Test fun offsetsFollowLinesAndColumns() {
        val text = "ab\ncdef\n\ngh"
        assertEquals(0, offsetOf(text, 1, 1))
        assertEquals(4, offsetOf(text, 2, 2))
        assertEquals(7, offsetOf(text, 2, 99))   // past the end of its line: the line's end
        assertEquals(8, offsetOf(text, 3, 1))
        assertEquals(text.length, offsetOf(text, 40, 1))
    }
}
