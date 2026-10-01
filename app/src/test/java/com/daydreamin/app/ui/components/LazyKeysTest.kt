package com.daydreamin.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LazyKeysTest {
    @Test fun `a repeated song still gets a key of its own`() {
        val keys = listOf("a", "b", "a", "a").uniqueKeys { it }
        assertEquals(listOf("a", "b", "a#2", "a#3"), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun `lists without repeats keep their plain keys`() {
        assertEquals(listOf("x", "y"), listOf("x", "y").uniqueKeys { it })
    }
}
