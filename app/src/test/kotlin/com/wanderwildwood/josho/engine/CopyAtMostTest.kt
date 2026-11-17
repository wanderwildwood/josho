package com.wanderwildwood.josho.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class CopyAtMostTest {
    @Test fun aFileWithinTheLimitIsCopiedWhole() {
        val out = ByteArrayOutputStream()
        assertEquals(5L, copyAtMost(ByteArrayInputStream(ByteArray(5) { 1 }), out, 5))
        assertEquals(5, out.size())
    }

    @Test fun aFilePastTheLimitSaysSo() {
        val out = ByteArrayOutputStream()
        val read = copyAtMost(ByteArrayInputStream(ByteArray(200_000)), out, 100_000)
        assertTrue(read > 100_000)
        assertTrue(out.size() <= 100_000)
    }
}
