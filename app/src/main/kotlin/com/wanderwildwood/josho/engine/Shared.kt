package com.wanderwildwood.josho.engine

/**
 * A score handed to Engraver through Android's Share menu: the text, and the name of the file it
 * came in when a file was shared (null for plain text, such as an answer in an AI app).
 */
data class Shared(val text: String, val fileName: String?)

/** Thrown when what was shared is not text: a picture, say, or some other file. */
class NotText : java.io.IOException("not text")

private val LANGUAGES = setOf("lilypond", "ly", "lily")
private val FENCE = Regex("""^ {0,3}(```+|~~~+)\s*([A-Za-z0-9_+-]*).*$""")

/**
 * The LilyPond in [text]. An answer from an AI app usually wraps its code in a fenced block
 * (```lilypond … ```) among sentences about it: the first block marked as LilyPond is taken, or
 * else the first block of any kind. Text without a whole block is taken as it is. Either way the
 * score ends in one line break.
 */
fun scoreFromShared(text: String): String {
    if (text.indexOf('\u0000') >= 0) throw NotText()
    val lines = text.split('\n')
    val blocks = mutableListOf<Pair<String, List<String>>>()
    var i = 0
    while (i < lines.size) {
        val open = FENCE.matchEntire(lines[i].trimEnd('\r'))
        if (open == null) { i++; continue }
        val fence = open.groupValues[1]
        val lang = open.groupValues[2].lowercase()
        val close = (i + 1 until lines.size).firstOrNull { j ->
            val t = lines[j].trimEnd('\r').trim()
            t.length >= fence.length && t.all { it == fence[0] }
        }
        if (close == null) break
        blocks += lang to lines.subList(i + 1, close)
        i = close + 1
    }
    val body = (blocks.firstOrNull { it.first in LANGUAGES } ?: blocks.firstOrNull())
        ?.second?.joinToString("\n")
        ?: text
    return body.trim('\n', '\r').trimEnd() + "\n"
}

/**
 * The name a shared score is saved under: a LilyPond file keeps its own name, any other file its
 * name with `.ly` for an ending, and plain text is `shared.ly`.
 */
fun sharedScoreName(fileName: String?): String {
    val name = fileName?.substringAfterLast('/')?.trim()?.takeIf { it.isNotEmpty() && it != "." && it != ".." }
        ?: return "shared.ly"
    val dot = name.lastIndexOf('.')
    val ending = if (dot > 0) name.substring(dot + 1).lowercase() else ""
    return when {
        ending in setOf("ly", "ily", "lyi") -> name
        dot > 0 -> name.substring(0, dot) + ".ly"
        else -> "$name.ly"
    }
}

/** [name], or the next free "name 2.ly" when the folder already has a file by that name. */
fun freeName(name: String, taken: Collection<String>): String =
    if (taken.none { it.equals(name, ignoreCase = true) }) name else nextFreeName(name, taken)
