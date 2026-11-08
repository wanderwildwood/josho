package com.wanderwildwood.josho.engine

/**
 * One line of LilyPond's output. Lines that point into a file ("score.ly:12:5: error: …") carry
 * where, so the log can take the reader there.
 */
data class LogLine(
    val text: String,
    val file: String? = null,
    val line: Int = 0,
    val column: Int = 0,
    val kind: Kind = Kind.Plain,
) {
    enum class Kind { Plain, Error, Warning }
}

private val located = Regex("""^(.+?):(\d+):(\d+): (error|warning|programming error|fatal error): ?(.*)$""")

/** LilyPond's output as lines; the "Processing / Parsing / Drawing…" chatter stays, the blanks go. */
fun parseLog(output: String): List<LogLine> = output.lines().filter { it.isNotBlank() }.map { raw ->
    val m = located.matchEntire(raw.trimEnd())
    if (m == null) {
        LogLine(raw.trimEnd(), kind = if (raw.startsWith("fatal error") || raw.startsWith("error")) LogLine.Kind.Error else LogLine.Kind.Plain)
    } else {
        val (file, line, column, kind, _) = m.destructured
        LogLine(
            raw.trimEnd(), file, line.toInt(), column.toInt(),
            if (kind == "warning") LogLine.Kind.Warning else LogLine.Kind.Error,
        )
    }
}

/** The character offset where 1-based [line] and [column] fall in [text], kept inside it. */
fun offsetOf(text: String, line: Int, column: Int): Int {
    var offset = 0
    var current = 1
    while (current < line) {
        val next = text.indexOf('\n', offset)
        if (next < 0) return text.length
        offset = next + 1
        current++
    }
    val end = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
    // LilyPond counts columns from 1 here; a tab counts as one.
    return (offset + (column - 1).coerceAtLeast(0)).coerceAtMost(end)
}
