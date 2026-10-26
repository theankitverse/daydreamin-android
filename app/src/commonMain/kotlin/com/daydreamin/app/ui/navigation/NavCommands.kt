package com.daydreamin.app.ui.navigation

/**
 * What the desktop window's keyboard shortcuts can ask the app to do (Esc, Cmd/Ctrl+F). Set by
 * [AppNavHost] while it's on screen; null otherwise.
 */
object NavCommands {
    /** Goes back one page; false when there's nowhere to go back to. */
    var goBack: (() -> Boolean)? = null
    var openSearch: (() -> Unit)? = null
}
