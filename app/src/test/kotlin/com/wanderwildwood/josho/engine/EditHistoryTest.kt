package com.wanderwildwood.josho.engine

import com.wanderwildwood.josho.engine.EditHistory.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {
    private var clock = 0L
    private var text = ""

    private fun history(depth: Int = 100): EditHistory {
        clock = 0L
        text = ""
        return EditHistory(depth = depth, pauseMillis = 2000).apply { reset(Snapshot("")) }
    }

    /** Types [letters] at the end of the text, one key every [every] ms. */
    private fun EditHistory.type(letters: String, every: Long = 100) {
        for (c in letters) {
            clock += every
            text += c
            record(Snapshot(text, text.length), clock)
        }
    }

    /** Erases [count] characters from the end, one key every 100 ms. */
    private fun EditHistory.erase(count: Int) {
        repeat(count) {
            clock += 100
            text = text.dropLast(1)
            record(Snapshot(text, text.length), clock)
        }
    }

    private fun EditHistory.back(): String? = undo()?.also { text = it.text }?.text

    @Test fun nothingToTakeBackAtFirst() {
        val h = history()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertNull(h.undo())
        assertNull(h.redo())
    }

    @Test fun aWordIsOneStep() {
        val h = history()
        h.type("c4")
        assertTrue(h.canUndo)
        assertEquals("", h.back())
        assertFalse(h.canUndo)
    }

    @Test fun eachWordIsItsOwnStepAndKeepsItsSpace() {
        val h = history()
        h.type("c4 d4 e4")
        assertEquals("c4 d4 ", h.back())
        assertEquals("c4 ", h.back())
        assertEquals("", h.back())
        assertNull(h.back())
    }

    @Test fun aNewLineStartsAStep() {
        val h = history()
        h.type("c4\nd4")
        assertEquals("c4\n", h.back())
        assertEquals("c4", h.back())
        assertEquals("", h.back())
    }

    @Test fun aPauseStartsAStep() {
        val h = history()
        h.type("c")
        h.type("4", every = 2500)
        assertEquals("c", h.back())
        assertEquals("", h.back())
    }

    @Test fun erasingJoinsButNotWithTyping() {
        val h = history()
        h.type("cis4")
        h.erase(3)
        assertEquals("c", text)
        assertEquals("cis4", h.back())
        assertEquals("", h.back())
    }

    @Test fun aPasteIsItsOwnStep() {
        val h = history()
        h.type("c4")
        clock += 100
        text += " d4 e4"
        h.record(Snapshot(text, text.length), clock)
        h.type("f")
        assertEquals("c4 d4 e4", h.back())
        assertEquals("c4", h.back())
    }

    @Test fun movingTheCursorStartsAStepAndIsRemembered() {
        val h = history()
        h.type("c4")
        h.record(Snapshot("c4", 0), clock + 50)
        clock += 100
        h.record(Snapshot("ac4", 1), clock)
        val back = h.undo()
        assertEquals(Snapshot("c4", 0), back)
        assertEquals(Snapshot("", 0), h.undo())
    }

    @Test fun redoPutsBackWhatWasTakenBack() {
        val h = history()
        h.type("c4 d4")
        assertEquals("c4 ", h.back())
        assertTrue(h.canRedo)
        assertEquals(Snapshot("c4 d4", 5), h.redo())
        assertFalse(h.canRedo)
        assertEquals(Snapshot("c4 ", 3), h.undo())
    }

    @Test fun typingAfterUndoDropsRedo() {
        val h = history()
        h.type("c4 d4")
        h.back()
        h.type("e")
        assertFalse(h.canRedo)
        assertNull(h.redo())
        assertEquals("c4 ", h.back())
    }

    @Test fun theOldestStepsGoPastTheDepth() {
        val h = history(depth = 3)
        h.type("a b c d e")
        assertEquals("a b c d ", h.back())
        assertEquals("a b c ", h.back())
        assertEquals("a b ", h.back())
        assertNull(h.back())
    }

    @Test fun anotherFileStartsEmpty() {
        val h = history()
        h.type("c4 d4")
        h.back()
        h.reset(Snapshot("other"))
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        h.record(Snapshot("others", 6), 10_000)
        assertEquals(Snapshot("other", 0), h.undo())
    }

    @Test fun theSameTextTwiceIsNoStep() {
        val h = history()
        h.type("c")
        h.record(Snapshot("c", 1), clock + 10)
        assertEquals("", h.back())
        assertFalse(h.canUndo)
    }
}
