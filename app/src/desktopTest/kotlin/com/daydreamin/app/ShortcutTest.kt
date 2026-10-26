package com.daydreamin.app

import androidx.compose.ui.input.key.KeyEvent
import com.daydreamin.app.platform.DesktopBack
import com.daydreamin.app.ui.navigation.NavCommands
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShortcutTest {
    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun press(key: Key, meta: Boolean = false, ctrl: Boolean = false, char: Char? = null) =
        KeyEvent(key = key, type = KeyEventType.KeyDown, codePoint = char?.code ?: 0, isMetaPressed = meta, isCtrlPressed = ctrl)

    @AfterTest fun reset() { NavCommands.goBack = null; NavCommands.openSearch = null }

    @Test fun escClosesTheInnermostOpenThingFirst() {
        val calls = mutableListOf<String>()
        NavCommands.goBack = { calls += "page"; true }
        val playlist = DesktopBack.Entry().apply { enabled = true; onBack = { calls += "playlist" } }
        val lyrics = DesktopBack.Entry().apply { enabled = true; onBack = { calls += "lyrics" } }
        val disabled = DesktopBack.Entry().apply { enabled = false; onBack = { calls += "disabled" } }
        listOf(playlist, lyrics, disabled).forEach(DesktopBack::add)
        try {
            assertTrue(handleShortcut(press(Key.Escape)))
            lyrics.enabled = false
            assertTrue(handleShortcut(press(Key.Escape)))
            playlist.enabled = false
            assertTrue(handleShortcut(press(Key.Escape)))
            assertEquals(listOf("lyrics", "playlist", "page"), calls)
        } finally {
            listOf(playlist, lyrics, disabled).forEach(DesktopBack::remove)
        }
    }

    @Test fun escOnTheFirstPageIsLeftAlone() {
        NavCommands.goBack = { false }
        assertFalse(handleShortcut(press(Key.Escape)))
    }

    @Test fun commandFOpensSearch() {
        var opened = 0
        NavCommands.openSearch = { opened++ }
        assertTrue(handleShortcut(press(Key.F, meta = true)))  // Cmd on a Mac
        assertTrue(handleShortcut(press(Key.F, ctrl = true)))  // Ctrl elsewhere
        assertEquals(2, opened)
    }

    @Test fun plainKeysAreNotTaken() {
        // Letters, and arrows without a modifier, belong to whatever has focus (lists, text).
        assertFalse(handleShortcut(press(Key.F, char = 'f')))
        assertFalse(handleShortcut(press(Key.DirectionRight)))
        assertFalse(handleShortcut(press(Key.DirectionLeft)))
    }
}
