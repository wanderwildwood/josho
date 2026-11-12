package com.wanderwildwood.josho.engine

/**
 * What was typed into the source, so it can be taken back and put again. Each step is the whole
 * text and where the cursor was. Typing joins into one step a word at a time: a letter after a
 * space, a new line, a pause, a move of the cursor, or anything that is not one character typed
 * or erased at the cursor (a paste, a symbol key over a selection) starts the next step.
 * At most [depth] steps are kept; the oldest go first.
 */
class EditHistory(private val depth: Int = 100, private val pauseMillis: Long = 2000) {

    /** The text, and the selection from [start] to [end] (the same place for a plain cursor). */
    data class Snapshot(val text: String, val start: Int = 0, val end: Int = start)

    private enum class Run { Typing, Erasing }

    private val before = ArrayDeque<Snapshot>()
    private val after = ArrayDeque<Snapshot>()
    private var now: Snapshot? = null
    private var run: Run? = null
    private var lastAt = 0L

    val canUndo: Boolean get() = before.isNotEmpty()
    val canRedo: Boolean get() = after.isNotEmpty()

    /** A different file is open: nothing to take back, starting from [snapshot]. */
    fun reset(snapshot: Snapshot) {
        before.clear()
        after.clear()
        now = snapshot
        run = null
    }

    /** The source is now [next], at [atMillis] on any clock that only goes forward. */
    fun record(next: Snapshot, atMillis: Long) {
        val was = now
        if (was == null) {
            now = next
            return
        }
        if (next.text == was.text) {
            // Only the cursor moved: the step taken back later returns to where it was left.
            if (next != was) {
                now = next
                run = null
            }
            return
        }
        val kind = runOf(was, next)
        val joins = kind != null && kind == run && atMillis - lastAt <= pauseMillis && !startsAfresh(was, next, kind)
        if (!joins) {
            before.addLast(was)
            if (before.size > depth) before.removeFirst()
        }
        after.clear()
        now = next
        run = kind
        lastAt = atMillis
    }

    /** The text as it was a step ago, or null with nothing to take back. */
    fun undo(): Snapshot? {
        val to = before.removeLastOrNull() ?: return null
        now?.let { after.addLast(it) }
        now = to
        run = null
        return to
    }

    /** The step last taken back, or null when there is none (or something was typed since). */
    fun redo(): Snapshot? {
        val to = after.removeLastOrNull() ?: return null
        now?.let { before.addLast(it) }
        now = to
        run = null
        return to
    }

    /** One character typed at the cursor, one erased before it, or neither (null). */
    private fun runOf(was: Snapshot, next: Snapshot): Run? {
        if (was.start != was.end || next.start != next.end) return null
        val a = was.text
        val b = next.text
        val at = was.start
        if (at < 0 || at > a.length) return null
        return when {
            b.length == a.length + 1 && next.start == at + 1 &&
                b.regionMatches(0, a, 0, at) && b.regionMatches(at + 1, a, at, a.length - at) -> Run.Typing
            b.length == a.length - 1 && at >= 1 && next.start == at - 1 &&
                a.regionMatches(0, b, 0, at - 1) && a.regionMatches(at, b, at - 1, a.length - at) -> Run.Erasing
            else -> null
        }
    }

    /** Whether this character, though it carries on from the last, begins a new step. */
    private fun startsAfresh(was: Snapshot, next: Snapshot, kind: Run): Boolean = when (kind) {
        Run.Typing -> {
            val typed = next.text[was.start]
            val last = was.text.getOrNull(was.start - 1)
            typed == '\n' || (last != null && last.isWhitespace() && !typed.isWhitespace())
        }
        Run.Erasing -> was.text[was.start - 1] == '\n'
    }
}
