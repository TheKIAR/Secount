package com.secount.app.ui

import okio.Path.Companion.toPath

/** In-app update check + crash-log helpers (extracted from App.kt, no behavior change). */

internal const val UPDATE_CHECK_KEY = "secount_update_checked_at"
internal const val APP_VERSION = "1.1.0"
internal const val RELEASES_URL = "https://github.com/TheKIAR/Secount/releases"

internal fun httpGetSafe(url: String): String? {
    return try {
        com.secount.app.logic.httpGet(url, 8000)
    } catch (e: Exception) {
        null
    }
}

internal fun clearCrashLog() {
    try {
        val f = okio.FileSystem.SYSTEM
        val p = (com.secount.app.logic.platformDataDir() + "/crash_log.txt").toPath()
        if (f.exists(p)) f.delete(p)
    } catch (ignored: Exception) {
    }
}

internal fun numVer(v: String): List<Int> {
    return v.trim().trimStart('v', 'V').split(Regex("[^0-9]+")).mapNotNull { it.toIntOrNull() }
}

internal fun isNewerVersion(current: String, tag: String): Boolean {
    val c = numVer(current)
    val n = numVer(tag)
    for (i in 0 until maxOf(c.size, n.size)) {
        val a = c.getOrElse(i) { 0 }
        val b = n.getOrElse(i) { 0 }
        if (b > a) return true
        if (b < a) return false
    }
    return false
}
