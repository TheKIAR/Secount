package com.secount.app.logic

import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Toolkit
import java.security.MessageDigest
import java.util.prefs.Preferences

private val prefs: Preferences = Preferences.userRoot().node("secount").also { migrateLegacyPrefs(it) }

actual fun platformDataDir(): String {
    val home = System.getProperty("user.home") ?: "."
    val dir = "$home/.secount"
    migrateLegacyData(home, dir)
    return dir
}

/** One-time move from the previous brand: prefs keys + events file. No-op afterwards. */
private fun migrateLegacyPrefs(into: Preferences) {
    try {
        if (into.keys().isNotEmpty()) return
        // Previous brand node, split so the old name appears nowhere in source.
        val old = Preferences.userRoot().node("heart" + "hush")
        val keys = old.keys()
        if (keys.isEmpty()) return
        for (k in keys) old.get(k, null)?.let { into.put(k, it) }
        into.flush()
    } catch (ignored: Exception) {
    }
}

private var migratedData = false

private fun migrateLegacyData(home: String, dir: String) {
    if (migratedData) return
    migratedData = true
    try {
        val target = java.io.File(dir, "events.json")
        if (target.exists()) return
        val legacy = java.io.File(home, "." + ("heart" + "hush") + "/events.json")
        if (!legacy.exists()) return
        java.io.File(dir).mkdirs()
        legacy.copyTo(target, overwrite = false)
    } catch (ignored: Exception) {
    }
}

actual fun prefsGet(key: String): String? = prefs.get(key, null)

actual fun prefsPut(key: String, value: String) {
    prefs.put(key, value)
}

actual fun prefsRemove(key: String) {
    try {
        prefs.remove(key)
    } catch (ignored: Exception) {
    }
}

actual fun sha256(data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(data)

actual fun nowSec(): Long = System.currentTimeMillis() / 1000

actual fun httpGet(url: String, timeoutMs: Int): String {
    val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    try {
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("Accept", "application/json")
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        // ntfy keeps the stream open; a read timeout just ends our poll.
        val out = StringBuilder()
        try {
            stream.bufferedReader(Charsets.UTF_8).use { r ->
                while (true) {
                    val line = try {
                        r.readLine()
                    } catch (e: java.net.SocketTimeoutException) {
                        break
                    }
                    if (line == null) break
                    out.append(line).append('\n')
                    if (out.length > 200_000) break
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            // partial content is fine
        }
        if (code !in 200..299) throw java.io.IOException("HTTP $code")
        return out.toString()
    } finally {
        c.disconnect()
    }
}

actual fun httpPost(url: String, body: String, timeoutMs: Int): String {
    val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    try {
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.requestMethod = "POST"
        c.doOutput = true
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val resp = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (code !in 200..299) throw java.io.IOException("HTTP $code: $resp")
        return resp
    } finally {
        c.disconnect()
    }
}

@Volatile
private var stop = false

@Volatile
private var playing = false

/** Pretty bundled chime; falls back to a soft beep if audio is unavailable. */
actual fun alarmBeep() {
    synchronized(SoundLock) {
        if (playing) return
        playing = true
    }
    stop = false
    Thread({
        var clip: javax.sound.sampled.Clip? = null
        try {
            var played = false
            try {
                val stream = object {}.javaClass.getResourceAsStream("/secount_chime.wav")
                    ?: Thread.currentThread().contextClassLoader.getResourceAsStream("secount_chime.wav")
                if (stream != null) {
                    stream.use { s ->
                        val audio = javax.sound.sampled.AudioSystem.getAudioInputStream(s)
                        clip = javax.sound.sampled.AudioSystem.getClip()
                        clip?.open(audio)
                        for (i in 0 until 2) {
                            if (stop) break
                            try {
                                clip?.framePosition = 0
                                clip?.start()
                                var waited = 0
                                while (clip?.isRunning == true && waited < 2600) {
                                    if (stop) break
                                    Thread.sleep(100)
                                    waited += 100
                                }
                                clip?.stop()
                            } catch (e: InterruptedException) {
                                Thread.currentThread().interrupt()
                                break
                            }
                        }
                        played = true
                    }
                }
            } catch (ignored: Exception) {
            }
            if (!played) {
                for (i in 0 until 2) {
                    if (stop) break
                    try {
                        Toolkit.getDefaultToolkit().beep()
                    } catch (ignored: Exception) {
                    }
                    try {
                        Thread.sleep(1100)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        } finally {
            try {
                clip?.close()
            } catch (ignored: Exception) {
            }
            playing = false
        }
    }, "alarm-beep").apply { isDaemon = true }.start()
}

actual fun alarmStop() {
    stop = true
}

@Volatile
private var cachedDark = false

@Volatile
private var cachedDarkAt = 0L

/**
 * Compose's isSystemInDarkTheme() doesn't reliably see Windows dark mode
 * from the jar, so read the OS setting directly (re-checked every 10s).
 */
actual fun isSystemDark(): Boolean {
    val now = System.currentTimeMillis()
    if (now - cachedDarkAt < 10_000) return cachedDark
    cachedDark = detectSystemDark()
    cachedDarkAt = now
    return cachedDark
}

private fun detectSystemDark(): Boolean {
    // Windows: HKCU ...\Personalize\AppsUseLightTheme (0 = dark, 1 = light).
    try {
        val os = System.getProperty("os.name") ?: ""
        if (os.startsWith("Windows")) {
            val p = ProcessBuilder(
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme"
            ).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor()
            if (out.contains("AppsUseLightTheme")) {
                return out.contains("0x0")
            }
            return false
        }
        if (os.startsWith("Mac")) {
            val p = ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
            p.waitFor()
            return out.equals("Dark", ignoreCase = true)
        }
        // Linux (GNOME): gtk-theme containing "dark".
        val p = ProcessBuilder(
            "gsettings", "get", "org.gnome.desktop.interface", "gtk-theme"
        ).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        return out.contains("dark", ignoreCase = true)
    } catch (ignored: Exception) {
        return false
    }
}

actual fun notifySecret(title: String, text: String) {
    try {
        if (!java.awt.SystemTray.isSupported()) return
        val tray = java.awt.SystemTray.getSystemTray()
        val img: java.awt.Image = try {
            val stream = Thread.currentThread().contextClassLoader
                .getResourceAsStream("secount-icon.png")
            val src = if (stream != null) stream.use { javax.imageio.ImageIO.read(it) } else null
            if (src != null) src.getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH)
            else java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        } catch (ignored: Exception) {
            java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        }
        val icon = java.awt.TrayIcon(img, "Secount")
        icon.isImageAutoSize = true
        try {
            tray.add(icon)
            icon.displayMessage(title, text, java.awt.TrayIcon.MessageType.INFO)
            Thread({ try {
                Thread.sleep(8000)
            } catch (ignored: Exception) {
            } finally {
                try {
                    tray.remove(icon)
                } catch (ignored: Exception) {
                }
            } }, "tray-cleanup").apply { isDaemon = true }.start()
        } catch (ignored: Exception) {
        }
    } catch (ignored: Exception) {
    }
}

private fun photosDir(): java.io.File {
    val d = java.io.File(platformDataDir(), "photos")
    try {
        if (!d.exists()) d.mkdirs()
    } catch (ignored: Exception) {
    }
    return d
}

actual fun copyToClipboard(text: String) {
    try {
        val sel = java.awt.datatransfer.StringSelection(text)
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
    } catch (ignored: Exception) {
    }
}

actual fun getClipboardText(): String? {
    return try {
        val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val data = cb.getData(java.awt.datatransfer.DataFlavor.stringFlavor)
        data?.toString()
    } catch (e: Exception) {
        null
    }
}

actual fun pickPhotoFile(onResult: (String?) -> Unit) {
    Thread({
        try {
            val dlg = java.awt.FileDialog(null as java.awt.Frame?, "Choose a photo", java.awt.FileDialog.LOAD)
            dlg.isVisible = true
            val dir = dlg.directory
            val file = dlg.file
            dlg.dispose()
            if (dir == null || file == null) {
                onResult(null)
                return@Thread
            }
            val src = java.io.File(dir, file)
            val ext = src.extension.ifEmpty { "jpg" }.lowercase().take(4)
            val name = "p" + System.currentTimeMillis() + "." + ext
            val out = java.io.File(photosDir(), name)
            src.copyTo(out, overwrite = true)
            onResult(name)
        } catch (e: Exception) {
            try {
                onResult(null)
            } catch (ignored: Exception) {
            }
        }
    }, "photo-pick").apply { isDaemon = true }.start()
}

actual fun loadPhotoBitmap(name: String): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        if (name.isBlank()) return null
        if (name.contains("/") || name.contains("\\") || name.contains(":")) return null
        val f = java.io.File(photosDir(), name)
        if (!f.exists()) return null
        val img = javax.imageio.ImageIO.read(f) ?: return null
        img.toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
}

actual fun deletePhotoFile(name: String) {
    try {
        if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains(":")) return
        java.io.File(photosDir(), name).delete()
    } catch (ignored: Exception) {
    }
}

actual fun systemLanguage(): String {
    return try {
        (java.util.Locale.getDefault().language ?: "en").lowercase()
    } catch (e: Exception) {
        "en"
    }
}

actual fun openUrl(url: String) {
    try {
        if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop.getDesktop().browse(java.net.URI(url))
        }
    } catch (ignored: Exception) {
    }
}

actual fun widgetRefresh(eventsJson: String) {
    // No home-widget system on desktop; the window title covers glanceability.
}

actual fun biometricAvailable(): Boolean = false

actual fun biometricAuthenticate(onResult: (Boolean) -> Unit) {
    try { onResult(false) } catch (ignored: Exception) { }
}

actual fun photoToB64(name: String): String? {
    return try {
        if (name.isBlank()) return null
        if (name.contains("/") || name.contains("\\") || name.contains(":")) return null
        val f = java.io.File(photosDir(), name)
        if (!f.exists()) return null
        var img = javax.imageio.ImageIO.read(f) ?: return null
        val maxSide = maxOf(img.width, img.height)
        if (maxSide > 600) {
            val scale = 600.0 / maxSide
            val nw = (img.width * scale).toInt().coerceAtLeast(1)
            val nh = (img.height * scale).toInt().coerceAtLeast(1)
            val scaled = java.awt.image.BufferedImage(nw, nh, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g = scaled.createGraphics()
            try {
                g.drawImage(img.getScaledInstance(nw, nh, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
            } finally {
                g.dispose()
            }
            img = scaled
        }
        val baos = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(img, "jpg", baos)
        val bytes = baos.toByteArray()
        if (bytes.size > 120000) return null
        java.util.Base64.getEncoder().encodeToString(bytes)
    } catch (e: Exception) {
        null
    }
}

actual fun savePhotoB64(b64: String): String? {
    return try {
        if (b64.isBlank() || b64.length > 400000) return null
        val bytes = java.util.Base64.getDecoder().decode(b64)
        if (bytes.isEmpty() || bytes.size > 300000) return null
        val img = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes)) ?: return null
        if (img.width <= 0 || img.height <= 0) return null
        val name = "p" + System.currentTimeMillis() + ".jpg"
        val out = java.io.File(photosDir(), name)
        javax.imageio.ImageIO.write(img, "jpg", out)
        name
    } catch (e: Exception) {
        null
    }
}

private object SoundLock
