package com.wanderwildwood.josho.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class SaveWithBackupTest {
    @Test fun theCopyIsNamedAfterTheScore() {
        assertEquals("Etude.ly.saving", backupName("Etude.ly"))
    }

    @Test fun aSaveWritesTheCopyThenTheScoreThenDropsTheCopy() {
        val steps = mutableListOf<String>()
        saveWithBackup({ steps += "copy" }, { steps += "score" }, { steps += "drop" })
        assertEquals(listOf("copy", "score", "drop"), steps)
    }

    @Test fun noCopyMeansTheScoreIsNotTouched() {
        val steps = mutableListOf<String>()
        assertThrows(IOException::class.java) {
            saveWithBackup({ throw IOException("full") }, { steps += "score" }, { steps += "drop" })
        }
        assertEquals(emptyList<String>(), steps)
    }

    @Test fun aScoreThatCannotBeWrittenKeepsTheCopy() {
        val steps = mutableListOf<String>()
        assertThrows(IOException::class.java) {
            saveWithBackup({ steps += "copy" }, { throw IOException("gone") }, { steps += "drop" })
        }
        assertEquals(listOf("copy"), steps)
    }

    @Test fun aCopyThatCannotBeDroppedStillCountsAsSaved() {
        saveWithBackup({}, {}, { throw IOException("busy") })
    }
}
