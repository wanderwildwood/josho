package com.wanderwildwood.josho.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTest {
    @Test fun plainCodeIsTakenAsItIs() {
        assertEquals("{ c d e }\n", scoreFromShared("{ c d e }"))
    }

    @Test fun theLilyPondBlockOfAnAnswerIsTaken() {
        val answer = listOf(
            "Here is the excerpt in LilyPond:",
            "",
            "```lilypond",
            "\\version \"2.24.0\"",
            "{ c'4 d' e' f' }",
            "```",
            "",
            "Let me know if you want the bass line too.",
        ).joinToString("\n")
        assertEquals("\\version \"2.24.0\"\n{ c'4 d' e' f' }\n", scoreFromShared(answer))
    }

    @Test fun aLilyPondBlockIsPreferredOverAnEarlierOne() {
        val answer = "Run:\n```sh\nlilypond score.ly\n```\nThe score:\n```ly\n{ c }\n```\n"
        assertEquals("{ c }\n", scoreFromShared(answer))
    }

    @Test fun otherwiseTheFirstBlockIsTaken() {
        assertEquals("{ g }\n", scoreFromShared("Score:\n```\n{ g }\n```\nand\n```\n{ a }\n```"))
    }

    @Test fun anUnclosedFenceLeavesTheTextWhole() {
        assertEquals("```lilypond\n{ c }\n", scoreFromShared("```lilypond\n{ c }"))
    }

    @Test fun windowsLineEndsAroundTheFences() {
        assertEquals("{ c }\r\n{ d }\n", scoreFromShared("Score:\r\n```lilypond\r\n{ c }\r\n{ d }\r\n```\r\n"))
    }

    @Test fun indentInsideTheBlockStays() {
        assertEquals("\\score {\n  { c }\n}\n", scoreFromShared("```lilypond\n\\score {\n  { c }\n}\n```"))
    }

    @Test(expected = NotText::class) fun binaryIsRefused() {
        scoreFromShared("PNG\u0000\u0001")
    }

    @Test fun names() {
        assertEquals("shared.ly", sharedScoreName(null))
        assertEquals("shared.ly", sharedScoreName(""))
        assertEquals("bass.ly", sharedScoreName("bass.ly"))
        assertEquals("parts.ily", sharedScoreName("parts.ily"))
        assertEquals("answer.ly", sharedScoreName("answer.txt"))
        assertEquals("notes.ly", sharedScoreName("notes"))
        assertEquals("x.ly", sharedScoreName("../x.ly"))
    }

    @Test fun aTakenNameGetsANumber() {
        assertEquals("shared.ly", freeName("shared.ly", listOf("score.ly")))
        assertEquals("shared 2.ly", freeName("shared.ly", listOf("Shared.ly")))
        assertEquals("shared 3.ly", freeName("shared.ly", listOf("shared.ly", "shared 2.ly")))
    }
}
