package com.wanderwildwood.josho.engine

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PrefixSealTest {
    private lateinit var base: File
    private lateinit var usr: File
    private lateinit var stamp: File

    @Before fun setUp() {
        base = Files.createTempDirectory("seal").toFile()
        usr = File(base, "usr").apply { mkdirs() }
        stamp = File(usr, ".josho-version")
    }

    @After fun tearDown() {
        base.walkTopDown().forEach { it.setWritable(true, true) }
        base.deleteRecursively()
    }

    private fun unpack(): PrefixSeal {
        val seal = PrefixSeal(base, File(base, "prefix-sha256.txt"))
        File(usr, "share/lily").mkdirs()
        seal.take("(define x 1)".byteInputStream(), File(usr, "share/lily/lily.scm"))
        seal.take("font".byteInputStream(), File(usr, "share/font.otf"))
        val link = File(usr, "share/lily/link.scm")
        Files.createSymbolicLink(link.toPath(), File("lily.scm").toPath())
        seal.link(link, "lily.scm")
        seal.seal()
        stamp.writeText("1")
        return seal
    }

    @Test fun anUntouchedPrefixPasses() {
        val seal = unpack()
        assertFalse(File(usr, "share/lily/lily.scm").canWrite())
        assertTrue(seal.check(listOf(usr), ignore = setOf(stamp)))
    }

    @Test fun aChangedFileFails() {
        val seal = unpack()
        val scm = File(usr, "share/lily/lily.scm")
        scm.setWritable(true, true)
        scm.writeText("(define x 2)")
        assertFalse(seal.check(listOf(usr), ignore = setOf(stamp)))
        // Made read-only again, it still fails on its bytes.
        scm.setWritable(false, false)
        assertFalse(seal.check(listOf(usr), ignore = setOf(stamp)))
    }

    @Test fun anAddedFileFails() {
        val seal = unpack()
        File(usr, "share/lily/extra.scm").writeText("(display 1)")
        assertFalse(seal.check(listOf(usr), ignore = setOf(stamp)))
    }

    @Test fun aMovedLinkFails() {
        val seal = unpack()
        val link = File(usr, "share/lily/link.scm")
        link.delete()
        Files.createSymbolicLink(link.toPath(), File("../font.otf").toPath())
        assertFalse(seal.check(listOf(usr), ignore = setOf(stamp)))
    }

    @Test fun aFolderLeftOutMayFillUp() {
        val seal = unpack()
        val cache = File(usr, "var/cache/fontconfig").apply { mkdirs() }
        File(cache, "fonts.cache").writeText("cache")
        assertTrue(seal.check(listOf(usr), ignore = setOf(stamp, cache)))
        assertFalse(seal.check(listOf(usr), ignore = setOf(stamp)))
    }

    @Test fun noRecordFails() {
        assertFalse(PrefixSeal(base, File(base, "missing.txt")).check(listOf(usr)))
    }
}
