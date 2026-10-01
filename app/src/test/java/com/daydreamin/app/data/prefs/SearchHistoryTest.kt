package com.daydreamin.app.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryTest {
    @Test fun `newest first, no repeats in any case, tidy spacing`() {
        val h = withSearch(listOf("arijit singh", "coldplay"), "  Coldplay   Yellow ")
        assertEquals(listOf("Coldplay Yellow", "arijit singh", "coldplay"), h)
        assertEquals(listOf("Arijit Singh", "Coldplay Yellow", "coldplay"), withSearch(h, "Arijit Singh"))
    }

    @Test fun `blank searches are ignored and the list is capped`() {
        assertEquals(listOf("a"), withSearch(listOf("a"), "   "))
        assertEquals(15, (1..30).fold(emptyList<String>()) { acc, n -> withSearch(acc, "q$n") }.size)
    }
}
