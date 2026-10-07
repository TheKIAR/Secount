package com.secount.app.ui

/** App-wide constants, option lists, and pure display helpers (no behavior change). */

internal const val NEED_LOCK_KEY = "secount_need_lock"
internal const val MUTED_KEY = "secount_muted"
internal const val LANG_KEY = "secount_lang"

internal val LANGS = listOf("System", "en", "de", "fr", "es")

internal fun langDisplay(code: String): String = when (code) {
    "en" -> "English"
    "de" -> "Deutsch"
    "fr" -> "Français"
    "es" -> "Español"
    else -> "System"
}

internal val FILTERS = listOf("All", "Today", "Next 7 days", "Featured", "With secret", "Past", "To partner")
internal val SORTS = listOf("Happening next", "Name A–Z", "Biggest countdown", "Newest first")
internal val TEXT_SIZES = listOf("Standard", "Large", "Extra large")
internal const val TEXT_SIZE_KEY = "secount_textsize"

internal fun textScale(pref: String): Float = when (pref) {
    "Large" -> 1.15f
    "Extra large" -> 1.3f
    else -> 1f
}

internal fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Good night"
}

internal fun filterEmoji(id: String): String = when (id) {
    "Today" -> "● "
    "Next 7 days" -> "◐ "
    "Featured" -> "★ "
    "With secret" -> "🎁 "
    "Past" -> "✓ "
    "To partner" -> "✉ "
    else -> ""
}

internal fun pairingText(myCode: String, accountId: String): String = "SECOUNT1:$myCode:$accountId"

internal fun parsePairCode(input: String): String? {
    val normalized = input.trim().uppercase()
    if (com.secount.app.logic.PairStore.looksLikeCode(normalized)) return normalized
    if (normalized.startsWith("SECOUNT1:")) {
        val parts = normalized.split(":")
        if (parts.size >= 2 && com.secount.app.logic.PairStore.looksLikeCode(parts[1])) return parts[1]
    }
    return normalized.split(Regex("[^A-Z0-9]+"))
        .firstOrNull { com.secount.app.logic.PairStore.looksLikeCode(it) }
}
