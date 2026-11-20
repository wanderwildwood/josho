package com.wanderwildwood.josho.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class IndentTest {
    private fun re(vararg lines: String) = Indent.reindent(lines.joinToString("\n")).text
    private fun lines(vararg lines: String) = lines.joinToString("\n")

    @Test fun bracesAddALevelEach() {
        assertEquals(
            lines("\\score {", "  \\new Staff {", "    c d e f", "  }", "}"),
            re("\\score {", "\\new Staff {", "c d e f", "}", "}"),
        )
    }

    @Test fun alreadyIndentedIsLeftAlone() {
        val text = lines("\\score {", "  \\new Staff {", "    c d e f", "  }", "}")
        assertEquals(text, Indent.reindent(text).text)
    }

    @Test fun simultaneousBracketsIndent() {
        assertEquals(
            lines("<<", "  \\new Staff { c }", ">>"),
            re("<<", "     \\new Staff { c }", ">>"),
        )
    }

    @Test fun aLineCarryingOnLinesUpAfterTheBracket() {
        assertEquals(
            lines("music = { c4 d", "          e f }"),
            re("music = { c4 d", "e f }"),
        )
    }

    @Test fun aCommentAfterTheBracketIndentsInstead() {
        assertEquals(lines("{ % voice", "  c", "}"), re("{ % voice", "c", "}"))
    }

    @Test fun schemeDefineIndentsTheEmacsWay() {
        assertEquals(lines("#(define (f x)", "   (+ x 1))"), re("#(define (f x)", "(+ x 1))"))
    }

    @Test fun schemeCallLinesUpWithItsFirstArgument() {
        assertEquals(lines("#(list 1", "       2)"), re("#(list 1", "2)"))
    }

    @Test fun lilyPondInsideScheme() {
        assertEquals(
            lines("#(define m #{", "  c d", "   #})"),
            re("#(define m #{", "c d", "#})"),
        )
    }

    @Test fun aSchemeValueEndsBeforeTheBracket() {
        assertEquals(
            lines("\\override A.b = #'c {", "  d", "}"),
            re("\\override A.b = #'c {", "d", "}"),
        )
    }

    @Test fun bracketsInStringsAndCommentsDoNotCount() {
        assertEquals(
            lines("{", "  c^\"{\" % {", "  d", "}"),
            re("{", "c^\"{\" % {", "d", "}"),
        )
    }

    @Test fun aStringOverSeveralLinesKeepsItsLines() {
        assertEquals(
            lines("\\markup {", "  \"one", "   two\"", "}"),
            re("\\markup {", "\"one", "   two\"", "}"),
        )
    }

    @Test fun aBlockCommentKeepsItsLinesButItsEndIsIndented() {
        assertEquals(
            lines("{", "  %{ a", "     b", "  %}", "  c", "}"),
            re("{", "%{ a", "     b", "%}", "c", "}"),
        )
    }

    @Test fun blankLinesAreNotMadeLonger() {
        assertEquals(lines("{", "", "  ", "  c", "}"), re("{", "", "     ", "c", "}"))
    }

    @Test fun windowsLineEndsStay() {
        assertEquals("{\r\n  c\r\n}", Indent.reindent("{\r\nc\r\n}").text)
    }

    @Test fun tabsBecomeSpaces() {
        assertEquals(lines("{", "  c", "}"), re("{", "\t\tc", "}"))
    }

    @Test fun theCursorKeepsItsPlaceInTheLine() {
        val r = Indent.reindent("{\nc d\n}")
        assertEquals("{\n  c d\n}", r.text)
        assertEquals(4, r.moved(2)) // before c
        assertEquals(6, r.moved(4)) // before d
        assertEquals(9, r.moved(7)) // the end
        assertEquals(0, r.moved(0))
    }

    @Test fun aCursorInsideTheOldIndentGoesNoFurtherThanTheNew() {
        val r = Indent.reindent("{\n      c\n}")
        assertEquals("{\n  c\n}", r.text)
        assertEquals(4, r.moved(7)) // was in the middle of six spaces, now at the end of two
    }

    @Test fun theBundledScoresAreAlreadyInOrder() {
        for (name in listOf("new.ly", "sample.ly")) {
            val text = java.io.File("src/main/assets/$name").readText()
            assertEquals(name, text, Indent.reindent(text).text)
        }
    }

    @Test fun emptyText() {
        assertEquals("", Indent.reindent("").text)
    }
}
