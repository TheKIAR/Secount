package com.secount.app.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.secount.app.ui.App
import java.awt.event.WindowFocusListener
import java.awt.event.WindowEvent

fun main() {
    // Crash log: capture uncaught exceptions; App.kt offers to copy/share it.
    try {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val home = System.getProperty("user.home") ?: "."
                val f = java.io.File("$home/.secount/crash_log.txt")
                f.parentFile?.mkdirs()
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                f.writeText("Secount crash @ ${java.util.Date()}\n$sw\n")
            } catch (ignored: Exception) {
            }
            prev?.uncaughtException(t, e)
        }
    } catch (ignored: Exception) {
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = nextUpTitle(),
            state = rememberWindowState(width = 480.dp, height = 860.dp)
        ) {
        // User rule: lock only when the window loses focus (Home/minimize/
        // Alt-Tab), never on a timer. App.kt polls this flag once per second.
        // File-dialog photo picking also drops focus — that must NOT lock.
        remember(window) {
            // Taskbar/dock icon: the Secount brand mark (shared desktop resource).
            try {
                val stream = Thread.currentThread().contextClassLoader
                    .getResourceAsStream("secount-icon.png")
                if (stream != null) {
                    stream.use {
                        val icon = javax.imageio.ImageIO.read(it)
                        if (icon != null) window.iconImage = icon
                    }
                }
            } catch (ignored: Exception) {
            }
            try {
                window.addWindowFocusListener(object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent?) {}
                    override fun windowLostFocus(e: WindowEvent?) {
                        try {
                            if (com.secount.app.logic.PhotoLockGuard.picking) return
                            java.util.prefs.Preferences.userRoot().node("secount")
                                .put("secount_need_lock", "1")
                        } catch (ignored: Exception) {
                        }
                    }
                })
            } catch (ignored: Exception) {
            }
            true
        }
        App()
        }
    }
}

/** Window title shows the next countdown so the desktop "widget" is glanceable. */
private fun nextUpTitle(): String {
    return try {
        val home = System.getProperty("user.home") ?: "."
        val f = java.io.File("$home/.secount/events.json")
        if (!f.exists()) return "Secount ♥ Countdowns"
        val txt = f.readText()
        // Minimal scan: find "title" values and "date" values in order.
        val titles = Regex("\"title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(txt).map {
            it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
        }.toList()
        val dates = Regex("\"date\"\\s*:\\s*\"([0-9]{4}-[0-9]{2}-[0-9]{2})\"").findAll(txt).map {
            it.groupValues[1]
        }.toList()
        if (titles.isEmpty()) return "Secount ♥ Countdowns"
        val today = java.time.LocalDate.now()
        var bestT = ""
        var bestD = Long.MAX_VALUE
        for (i in titles.indices) {
            val ds = dates.getOrNull(i) ?: continue
            val d = try {
                java.time.LocalDate.parse(ds)
            } catch (e: Exception) {
                continue
            }
            if (d.isBefore(today)) continue
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, d)
            if (days < bestD) {
                bestD = days
                bestT = titles[i]
            }
        }
        if (bestT.isEmpty()) "Secount ♥ Countdowns" else "Secount ♥ $bestT — ${if (bestD == 0L) "today!" else if (bestD == 1L) "tomorrow" else "$bestD days"}"
    } catch (e: Exception) {
        "Secount ♥ Countdowns"
    }
}
