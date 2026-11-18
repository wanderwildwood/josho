package com.wanderwildwood.josho.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class ReadAtMostTest {
    @Test fun aScoreWithinTheLimitIsReadWhole() {
        val text = "\\relative { c'4 d e f }\n"
        assertEquals(text, readAtMost(ByteArrayInputStream(text.toByteArray()), 100))
    }

    @Test fun aScorePastTheLimitIsRefused() {
        assertThrows(TooLarge::class.java) { readAtMost(ByteArrayInputStream(ByteArray(200_000)), 100_000) }
    }

    @Test fun theAppsOwnFolderRefusesAScorePastTwoMegabytes() {
        val dir = java.nio.file.Files.createTempDirectory("josho-read").toFile()
        try {
            val folder = LocalFolder(dir)
            val small = folder.create("small.ly", "{ c' }")!!
            assertEquals("{ c' }", folder.read(small))
            File(dir, "large.ly").writeBytes(ByteArray((MAX_SOURCE + 1).toInt()) { 'a'.code.toByte() })
            val large = folder.scores().first { it.name == "large.ly" }
            assertThrows(TooLarge::class.java) { folder.read(large) }
        } finally {
            dir.deleteRecursively()
        }
    }
}
