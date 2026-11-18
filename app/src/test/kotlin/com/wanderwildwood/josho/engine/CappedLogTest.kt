package com.wanderwildwood.josho.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class CappedLogTest {
    private fun note(n: Int) = "($n left out)"

    @Test fun aShortLogIsKeptWhole() {
        val log = CappedLog(headChars = 100, tailLines = 3)
        log.add("Processing `score.ly'")
        log.add("Parsing...")
        assertEquals("Processing `score.ly'\nParsing...\n", log.text(::note))
        assertEquals(0, log.skipped)
    }

    @Test fun aRunawayLogKeepsItsStartAndItsEnd() {
        val log = CappedLog(headChars = 12, tailLines = 2)
        log.add("first")
        log.add("second")
        repeat(10_000) { log.add("x$it") }
        val text = log.text(::note)
        assertTrue(text.startsWith("first\n"))
        assertTrue(text.endsWith("x9998\nx9999\n"))
        assertTrue(text.contains("(9999 left out)"))
    }

    @Test fun linesAreReadOneByOneAndALongOneIsCut() {
        val reader = StringReader("a\r\n" + "b".repeat(50) + "\nlast")
        assertEquals("a", readLineAtMost(reader, 10))
        assertEquals("b".repeat(10), readLineAtMost(reader, 10))
        assertEquals("last", readLineAtMost(reader, 10))
        assertNull(readLineAtMost(reader, 10))
    }
}
