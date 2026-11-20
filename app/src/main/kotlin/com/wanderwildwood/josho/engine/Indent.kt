package com.wanderwildwood.josho.engine

/**
 * Re-indents LilyPond source the way Frescobaldi does.
 *
 * A port of python-ly's `ly/indent.py` (Wilbert Berendsen, GPL-3.0-or-later,
 * https://github.com/frescobaldi/python-ly), the indenter behind Frescobaldi's Re-indent, with just
 * enough of python-ly's lexer (`ly/lex`) to find the tokens it looks at: `{ }` and `<< >>` in
 * LilyPond, `( )`, `#(` and `#{ #}` in Scheme, strings and comments in both.
 *
 * Only the white space at the start of a line changes. Lines inside a string or a block comment
 * keep theirs, and an empty or blank line is never given a longer indent. One level is two spaces,
 * Frescobaldi's default; a line that carries on after an opening bracket lines up with what follows
 * the bracket, and Scheme lines up the way Emacs does (`define`, `let` and the like indent instead).
 */
object Indent {

    const val WIDTH = 2

    /** The re-indented text, and where a place in the old text ([moved]) is in the new one. */
    class Result internal constructor(
        val text: String,
        private val oldStarts: IntArray,
        private val newStarts: IntArray,
        private val oldIndent: IntArray,
        private val newIndent: IntArray,
    ) {
        /** Where [offset] in the old text lands: the same spot in its line, the indent counted anew. */
        fun moved(offset: Int): Int {
            if (oldStarts.isEmpty()) return offset
            var line = oldStarts.binarySearch(offset)
            if (line < 0) line = -line - 2
            line = line.coerceIn(0, oldStarts.size - 1)
            val col = offset - oldStarts[line]
            val newCol = if (col >= oldIndent[line]) col - oldIndent[line] + newIndent[line] else minOf(col, newIndent[line])
            return (newStarts[line] + newCol).coerceIn(0, text.length)
        }
    }

    fun reindent(source: String): Result {
        val raw = source.split('\n')
        // A file written on Windows keeps its line ends; the lexer never sees the \r.
        val ends = raw.map { if (it.endsWith('\r')) "\r" else "" }
        val bodies = raw.mapIndexed { i, s -> s.dropLast(ends[i].length) }
        val lines = Lexer().lines(bodies)

        val out = bodies.toMutableList()
        val newIndentOf = IntArray(lines.size)
        val oldIndentOf = IntArray(lines.size)
        val indents = mutableListOf("")
        var pline: Line? = null
        var prevIndent = ""
        for ((i, line) in lines.withIndex()) {
            val p = pline
            if (p != null) {
                if (p.indent != null) prevIndent = p.indent
                if (p.indenters.isNotEmpty()) {
                    val current = indents.last()
                    for ((align, indent) in p.indenters) {
                        var n = current
                        if (align != null && align != 0) n += " ".repeat(maxOf(0, align - prevIndent.length))
                        if (indent) n += " ".repeat(WIDTH)
                        indents.add(n)
                    }
                }
            }
            trim(indents, line.dedentersStart)
            val old = line.indent
            oldIndentOf[i] = old?.length ?: 0
            newIndentOf[i] = oldIndentOf[i]
            if (old != null) {
                val want = indents.last()
                val keep = line.isBlank && want.startsWith(old)
                if (!keep && old != want) {
                    out[i] = want + bodies[i].substring(old.length)
                    newIndentOf[i] = want.length
                }
            }
            trim(indents, line.dedentersEnd)
            pline = line
        }

        val text = StringBuilder()
        val oldStarts = IntArray(lines.size)
        val newStarts = IntArray(lines.size)
        var oldAt = 0
        for (i in out.indices) {
            oldStarts[i] = oldAt
            newStarts[i] = text.length
            text.append(out[i]).append(ends[i])
            if (i < out.size - 1) text.append('\n')
            oldAt += raw[i].length + 1
        }
        return Result(text.toString(), oldStarts, newStarts, oldIndentOf, newIndentOf)
    }

    /** python-ly's `del indents[max(1, len(indents) - n):]`. */
    private fun trim(indents: MutableList<String>, n: Int) {
        val from = maxOf(1, indents.size - n)
        while (indents.size > from) indents.removeAt(indents.size - 1)
    }

    // ---- What the indenter needs to know about one line (python-ly's Line) ----

    internal enum class Kind { SPACE, INDENT, DEDENT, COMMENT, BLOCK_BODY, BLOCK_END, WORD, OTHER }

    internal class Tok(val kind: Kind, val pos: Int, val text: String, val paren: Boolean = false) {
        val isComment get() = kind == Kind.COMMENT || kind == Kind.BLOCK_BODY || kind == Kind.BLOCK_END
    }

    internal enum class Start { CODE, STRING, BLOCK_COMMENT }

    internal class Line(start: Start, tokens: List<Tok>) {
        /** The line's white space at the start, or null when it may not be changed. */
        val indent: String?
        val isBlank: Boolean
        var dedentersStart = 0
            private set
        var dedentersEnd = 0
            private set
        /** One (align column or null, add a level) per bracket left open on this line. */
        val indenters: List<Pair<Int?, Boolean>>

        init {
            when {
                start == Start.STRING -> { indent = null; isBlank = false }
                start == Start.BLOCK_COMMENT -> {
                    indent = when {
                        tokens.isNotEmpty() && tokens[0].kind == Kind.BLOCK_END -> ""
                        tokens.size > 1 && tokens[0].kind == Kind.BLOCK_BODY && tokens[1].kind == Kind.BLOCK_END &&
                            tokens[0].text.isBlank() -> tokens[0].text
                        else -> null
                    }
                    isBlank = false
                }
                tokens.isNotEmpty() && tokens[0].kind == Kind.SPACE -> { indent = tokens[0].text; isBlank = tokens.size == 1 }
                else -> { indent = ""; isBlank = tokens.isEmpty() }
            }

            var findDedenters = true
            val open = mutableListOf<MutableList<Tok>>()
            for (t in tokens) {
                when (t.kind) {
                    Kind.INDENT -> {
                        findDedenters = false
                        open.lastOrNull()?.add(t)
                        open.add(mutableListOf(t))
                    }
                    Kind.DEDENT -> {
                        if (findDedenters && !t.paren) {
                            dedentersStart++
                        } else {
                            findDedenters = false
                            if (open.isNotEmpty()) open.removeAt(open.size - 1) else dedentersEnd++
                        }
                    }
                    Kind.SPACE -> {}
                    else -> {
                        findDedenters = false
                        open.lastOrNull()?.add(t)
                    }
                }
            }
            indenters = open.map { group ->
                val token = group[0]
                val rest = group.subList(1, group.size)
                when {
                    token.paren -> when {
                        rest.size > 1 && !isSpecialSchemeKeyword(rest[0]) -> rest[1].pos to false
                        rest.size == 1 && !rest[0].isComment -> rest[0].pos to false
                        else -> token.pos to true
                    }
                    rest.isNotEmpty() && !rest[0].isComment -> rest[0].pos to false
                    else -> null to true
                }
            }
        }
    }

    /** Scheme words that indent instead of lining up (from Emacs' scheme.el, as python-ly has them). */
    private val SPECIAL = setOf(
        "begin", "case", "delay", "do", "lambda", "let", "let*", "letrec", "let-values", "let*-values",
        "sequence", "let-syntax", "letrec-syntax", "syntax-rules", "syntax-case", "library",
        "call-with-input-file", "with-input-from-file", "with-input-from-port", "call-with-output-file",
        "with-output-to-file", "with-output-to-port", "call-with-values", "dynamic-wind", "when", "unless",
        "letrec*", "parameterize", "define-values", "define-record-type", "define-library", "receive",
    )

    private fun isSpecialSchemeKeyword(t: Tok) =
        t.kind == Kind.WORD && (t.text.startsWith("def") || t.text in SPECIAL)

    // ---- The lexer: modes on a stack, carried from line to line ----

    private sealed class Mode {
        object Lily : Mode()
        /** A Scheme expression after `#` or `$`; [depth] is how many parentheses are open. */
        class Scheme(var depth: Int) : Mode()
        object LilyString : Mode()
        object SchemeString : Mode()
        object LilyBlock : Mode()
        object SchemeBlock : Mode()
    }

    internal class Lexer {
        private val stack = mutableListOf<Mode>(Mode.Lily)
        private val top get() = stack.last()

        fun lines(bodies: List<String>): List<Line> = bodies.map { body ->
            val start = when (top) {
                Mode.LilyString, Mode.SchemeString -> Start.STRING
                Mode.LilyBlock, Mode.SchemeBlock -> Start.BLOCK_COMMENT
                else -> Start.CODE
            }
            Line(start, tokens(body))
        }

        /** A Scheme expression has ended: back to the LilyPond it was in. */
        private fun expressionDone() {
            val s = top
            if (s is Mode.Scheme && s.depth <= 0) stack.removeAt(stack.size - 1)
        }

        private fun inEmbeddedLily() = top == Mode.Lily && stack.size >= 2 && stack[stack.size - 2] is Mode.Scheme

        fun tokens(s: String): List<Tok> {
            val out = mutableListOf<Tok>()
            var i = 0
            val n = s.length
            fun at(str: String) = s.startsWith(str, i)
            while (i < n) {
                val c = s[i]
                when (val m = top) {
                    Mode.LilyString, Mode.SchemeString -> {
                        val end = closingQuote(s, i)
                        val stop = if (end < 0) n else end + 1
                        out += Tok(Kind.OTHER, i, s.substring(i, stop))
                        i = stop
                        if (end >= 0) {
                            stack.removeAt(stack.size - 1)
                            if (m == Mode.SchemeString) expressionDone()
                        }
                    }
                    Mode.LilyBlock, Mode.SchemeBlock -> {
                        val close = if (m == Mode.LilyBlock) "%}" else "!#"
                        val end = s.indexOf(close, i)
                        val stop = if (end < 0) n else end
                        if (stop > i) out += Tok(Kind.BLOCK_BODY, i, s.substring(i, stop))
                        i = stop
                        if (end >= 0) {
                            out += Tok(Kind.BLOCK_END, i, close)
                            i += 2
                            stack.removeAt(stack.size - 1)
                        }
                    }
                    Mode.Lily -> when {
                        c.isWhitespace() -> i = space(s, i, out)
                        at("%{") -> { out += Tok(Kind.COMMENT, i, "%{"); i += 2; stack += Mode.LilyBlock }
                        c == '%' -> { out += Tok(Kind.COMMENT, i, s.substring(i)); i = n }
                        c == '"' -> { stack += Mode.LilyString; out += Tok(Kind.OTHER, i, "\""); i++ }
                        at("<<") -> { out += Tok(Kind.INDENT, i, "<<"); i += 2 }
                        at(">>") -> { out += Tok(Kind.DEDENT, i, ">>"); i += 2 }
                        c == '{' -> { out += Tok(Kind.INDENT, i, "{"); i++ }
                        c == '}' -> { out += Tok(Kind.DEDENT, i, "}"); i++ }
                        at("#}") && inEmbeddedLily() -> {
                            out += Tok(Kind.DEDENT, i, "#}"); i += 2
                            stack.removeAt(stack.size - 1)
                            expressionDone()
                        }
                        (c == '#' || c == '$') && s.getOrNull(i + 1).let { it != '{' && it != '}' } -> {
                            out += Tok(Kind.OTHER, i, c.toString()); i++
                            stack += Mode.Scheme(0)
                        }
                        c == '<' || c == '>' -> { out += Tok(Kind.OTHER, i, c.toString()); i++ }
                        else -> {
                            var j = i + 1
                            while (j < n && !s[j].isWhitespace() && s[j] !in LILY_STOPS) j++
                            out += Tok(Kind.OTHER, i, s.substring(i, j)); i = j
                        }
                    }
                    is Mode.Scheme -> when {
                        c.isWhitespace() -> i = space(s, i, out)
                        c == ';' -> { out += Tok(Kind.COMMENT, i, s.substring(i)); i = n }
                        at("#!") -> { out += Tok(Kind.COMMENT, i, "#!"); i += 2; stack += Mode.SchemeBlock }
                        c == '"' -> { stack += Mode.SchemeString; out += Tok(Kind.OTHER, i, "\""); i++ }
                        at("#(") -> { out += Tok(Kind.INDENT, i, "#(", paren = true); i += 2; m.depth++ }
                        c == '(' -> { out += Tok(Kind.INDENT, i, "(", paren = true); i++; m.depth++ }
                        c == ')' -> { out += Tok(Kind.DEDENT, i, ")", paren = true); i++; m.depth--; expressionDone() }
                        at("#{") -> { out += Tok(Kind.INDENT, i, "#{"); i += 2; stack += Mode.Lily }
                        c == '\'' || c == '`' || c == ',' -> {
                            val len = if (at(",@")) 2 else 1
                            out += Tok(Kind.OTHER, i, s.substring(i, i + len)); i += len
                        }
                        c == '{' || c == '}' -> {
                            // Not Scheme: the expression has ended, and LilyPond reads this.
                            stack.removeAt(stack.size - 1)
                        }
                        else -> {
                            var j = i + 1
                            if (at("#\\") && j + 1 < n) j += 2 // a character like #\( or #\space
                            while (j < n && !s[j].isWhitespace() && s[j] !in SCHEME_STOPS) j++
                            out += Tok(Kind.WORD, i, s.substring(i, j)); i = j
                            expressionDone()
                        }
                    }
                }
            }
            return out
        }

        private fun space(s: String, from: Int, out: MutableList<Tok>): Int {
            var j = from
            while (j < s.length && s[j].isWhitespace()) j++
            out += Tok(Kind.SPACE, from, s.substring(from, j))
            return j
        }

        /** The index of the quote that closes a string, or -1 when it carries on past this line. */
        private fun closingQuote(s: String, from: Int): Int {
            var j = from
            while (j < s.length) {
                when (s[j]) {
                    '\\' -> j += 2
                    '"' -> return j
                    else -> j++
                }
            }
            return -1
        }
    }

    private val LILY_STOPS = setOf('{', '}', '<', '>', '"', '%', '#')
    private val SCHEME_STOPS = setOf('(', ')', '"', '{', '}', ';')
}
