package com.daydreamin.app.ui.components

/**
 * Keys for a lazy list that stay unique even when the list repeats an item. Compose crashes
 * ("Key … was already used") the moment a second copy of a key scrolls into view — and song
 * lists here come from YouTube mixes, charts and your own history, any of which can repeat a
 * song. The first copy keeps its plain key (so scroll position and animations stay stable);
 * later copies get "#2", "#3".
 */
fun <T> List<T>.uniqueKeys(keyOf: (T) -> String): List<String> {
    val seen = HashMap<String, Int>()
    return map { item ->
        val key = keyOf(item)
        val n = (seen[key] ?: 0) + 1
        seen[key] = n
        if (n == 1) key else "$key#$n"
    }
}
