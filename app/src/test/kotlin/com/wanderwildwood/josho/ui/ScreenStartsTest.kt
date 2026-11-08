package com.wanderwildwood.josho.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenStartsTest {
    @Test fun aPageThatFitsIsOneScreen() = assertEquals(listOf(0), screenStarts(500, 600))

    @Test fun aTallPageEndsFlushAndOverlaps() {
        val starts = screenStarts(1700, 600)
        assertEquals(0, starts.first())
        assertEquals(1100, starts.last())               // the last screen ends at the page's foot
        starts.zipWithNext().forEach { (a, b) -> assert(b - a <= 525) }   // an eighth repeats
    }
}
