package com.secount.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import okio.Path.Companion.toPath

/** In-app update check + crash-log helpers (extracted from App.kt, no behavior change). */

internal const val UPDATE_CHECK_KEY = "secount_update_checked_at"
internal const val APP_VERSION = "1.1.1"
internal const val RELEASES_URL = "https://github.com/TheKIAR/Secount/releases"

@Composable
internal fun AppStartupChecks(
    onCrashReport: (String) -> Unit,
    onUpdateAvailable: (Pair<String, String>) -> Unit
) {
    LaunchedEffect(Unit) {
        try {
            val f = okio.FileSystem.SYSTEM
            val p = (com.secount.app.logic.platformDataDir() + "/crash_log.txt").toPath()
            if (f.exists(p)) {
                val txt = f.read(p) { readUtf8() }
                if (txt.isNotBlank()) onCrashReport(txt.take(4000))
            }
        } catch (ignored: Exception) {
        }
        try {
            val last = com.secount.app.logic.prefsGet(UPDATE_CHECK_KEY)?.toLongOrNull() ?: 0L
            if (com.secount.app.logic.nowSec() - last > 86400) {
                com.secount.app.logic.prefsPut(UPDATE_CHECK_KEY, com.secount.app.logic.nowSec().toString())
                val json = httpGetSafe("https://api.github.com/TheKIAR/Secount/releases/latest")
                if (json != null) {
                    val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
                    val url = Regex("\"html_url\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
                    if (tag != null && url != null && isNewerVersion(APP_VERSION, tag)) {
                        onUpdateAvailable(tag to url)
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }
}

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
