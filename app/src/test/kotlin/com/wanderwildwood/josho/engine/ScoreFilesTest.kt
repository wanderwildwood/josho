package com.wanderwildwood.josho.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The file work behind Save as, Copy and Delete, on the app's own kind of folder in a temporary place. */
class ScoreFilesTest {
    private lateinit var dir: File
    private lateinit var folder: LocalFolder

    @Before fun setUp() {
        dir = Files.createTempDirectory("josho-scores").toFile()
        folder = LocalFolder(dir)
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    private fun score(name: String, text: String = "{ c4 }"): Doc = folder.create(name, text)!!

    private fun names(): List<String> = dir.list().orEmpty().sorted()

    @Test fun aNameGetsItsEnding() {
        assertEquals("song.ly", scoreName("song"))
        assertEquals("song.ly", scoreName("  song.ly "))
        assertEquals("song.ly", scoreName("song.LY"))
        assertEquals("song.pdf.ly", scoreName("song.pdf"))
    }

    @Test fun anEmptyNameIsRefused() {
        assertEquals(NameProblem.Empty, nameProblem("", emptyList()))
        assertEquals(NameProblem.Empty, nameProblem("   ", emptyList()))
        assertEquals(NameProblem.Empty, nameProblem(".ly", emptyList()))
    }

    @Test fun aTakenNameIsRefusedWhateverItsCase() {
        val taken = listOf("song.ly", "song.pdf")
        assertEquals(NameProblem.Taken, nameProblem("song", taken))
        assertEquals(NameProblem.Taken, nameProblem("Song.ly", taken))
        assertNull(nameProblem("song 2", taken))
        assertNull(nameProblem("song.pdf", taken))
    }

    @Test fun aNameThatIsAPathIsRefused() {
        assertEquals(NameProblem.Unusable, nameProblem("../song", emptyList()))
        assertEquals(NameProblem.Unusable, nameProblem("parts\\song", emptyList()))
        assertEquals(NameProblem.Unusable, nameProblem("so\nng", emptyList()))
    }

    @Test fun theNextFreeNameCountsUp() {
        assertEquals("song 2.ly", nextFreeName("song.ly", listOf("song.ly")))
        assertEquals("song 3.ly", nextFreeName("song.ly", listOf("song.ly", "Song 2.ly")))
        assertEquals("song 3.ly", nextFreeName("song 2.ly", listOf("song.ly", "song 2.ly")))
        assertEquals("opus 28 no 5.ly", nextFreeName("opus 28 no 4.ly", emptyList()))
        assertEquals("song 2.ly", nextFreeName("song", emptyList()))
    }

    @Test fun whatACompileLeaves() {
        assertEquals(listOf("my.song.pdf", "my.song.midi"), byProductNames("my.song.ly"))
    }

    @Test fun aCopyHoldsTheSameTextUnderTheNewName() {
        val original = score("song.ly", "{ c4 d4 }")
        val copy = copyScore(folder, original, "song 2")
        assertNotNull(copy)
        assertEquals("song 2.ly", copy!!.name)
        assertEquals("{ c4 d4 }", folder.read(copy))
        assertEquals("{ c4 d4 }", folder.read(original))
        assertEquals(listOf("song 2.ly", "song.ly"), names())
    }

    @Test fun saveAsWritesWhatIsInTheEditorAndLeavesTheOriginal() {
        val original = score("song.ly", "{ c4 }")
        val copy = copyScore(folder, original, "other.ly", text = "{ c4 e4 }")!!
        assertEquals("{ c4 e4 }", folder.read(copy))
        assertEquals("{ c4 }", folder.read(original))
    }

    @Test fun aCopyNeverReplacesAFile() {
        val original = score("song.ly", "{ c4 }")
        score("other.ly", "{ g4 }")
        val refused = runCatching { copyScore(folder, original, "Other") }
        assertTrue(refused.exceptionOrNull() is IllegalArgumentException)
        assertEquals("{ g4 }", File(dir, "other.ly").readText())
        assertTrue(runCatching { copyScore(folder, original, "") }.isFailure)
        assertTrue(runCatching { copyScore(folder, original, "../out") }.isFailure)
        assertEquals(listOf("other.ly", "song.ly"), names())
    }

    @Test fun deletingTakesThePdfAndMidiOfTheSameName() {
        val doomed = score("song.ly")
        score("song 2.ly")
        File(dir, "song.pdf").writeText("pdf")
        File(dir, "song.midi").writeText("midi")
        File(dir, "song 2.pdf").writeText("pdf")
        File(dir, "song.txt").writeText("notes")
        File(dir, "songbook.pdf").writeText("pdf")
        assertTrue(deleteScore(folder, doomed))
        assertEquals(listOf("song 2.ly", "song 2.pdf", "song.txt", "songbook.pdf"), names())
    }

    @Test fun deletingAScoreWithNothingBesideIt() {
        val doomed = score("song.ly")
        assertTrue(deleteScore(folder, doomed))
        assertEquals(emptyList<String>(), names())
    }

    @Test fun aScoreAlreadyGoneLeavesItsPdfAlone() {
        val gone = score("song.ly")
        File(dir, "song.pdf").writeText("pdf")
        File(dir, "song.ly").delete()
        assertFalse(deleteScore(folder, gone))
        assertEquals(listOf("song.pdf"), names())
    }

    @Test fun thePdfBesideAScore() {
        val doc = score("song.ly")
        assertNull(pdfBeside(folder, doc))
        File(dir, "song.pdf").writeText("pdf")
        assertEquals("song.pdf", pdfBeside(folder, doc)?.name)
    }

    @Test fun besideListsFilesNotFolders() {
        val doc = score("song.ly")
        File(dir, "parts").mkdir()
        File(dir, "motif.ily").writeText("")
        assertEquals(listOf("motif.ily", "song.ly"), folder.beside(doc).map { it.name }.sorted())
    }
}
