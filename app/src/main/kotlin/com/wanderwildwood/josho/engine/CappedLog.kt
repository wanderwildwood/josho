package com.wanderwildwood.josho.engine

import java.io.Reader

/**
 * LilyPond's output as it comes, kept within bounds: the first [headChars] in full, then only
 * the last [tailLines] lines, with a count of those left out between. A score whose own Scheme
 * prints without end costs a fixed amount of memory, and the log still shows how it began and
 * how it ended.
 */
internal class CappedLog(private val headChars: Int = HEAD_CHARS, private val tailLines: Int = TAIL_LINES) {
    private val head = StringBuilder()
    private var headFull = false
    private val tail = ArrayDeque<String>()

    /** Lines between the head and the tail that are not kept. */
    var skipped = 0
        private set

    fun add(line: String) {
        if (!headFull && head.length + line.length + 1 <= headChars) {
            head.append(line).append('\n')
            return
        }
        headFull = true
        tail.addLast(line)
        if (tail.size > tailLines) {
            tail.removeFirst()
            skipped++
        }
    }

    /** The log as kept; [leftOut] words the line that stands for the lines not kept. */
    fun text(leftOut: (Int) -> String): String = buildString {
        append(head)
        if (skipped > 0) append(leftOut(skipped)).append('\n')
        tail.forEach { append(it).append('\n') }
    }

    companion object {
        const val HEAD_CHARS = 64 * 1024
        const val TAIL_LINES = 200
    }
}

/** The longest line kept whole; the rest of a longer one is read and dropped. */
internal const val MAX_LOG_LINE = 2000

/**
 * The next line of [reader] without its line ending, at most [max] characters of it (the rest
 * of a longer line is read past), or null at the end. Unlike readLine, a line with no end in
 * sight never piles up in memory.
 */
internal fun readLineAtMost(reader: Reader, max: Int = MAX_LOG_LINE): String? {
    val line = StringBuilder()
    var any = false
    while (true) {
        val c = reader.read()
        if (c < 0) return if (any) line.toString() else null
        any = true
        when (c.toChar()) {
            '\n' -> return line.toString()
            '\r' -> {}
            else -> if (line.length < max) line.append(c.toChar())
        }
    }
}
