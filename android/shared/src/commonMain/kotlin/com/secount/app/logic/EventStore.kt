package com.secount.app.logic

import java.time.LocalDate
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Loads/saves the countdown list as JSON. Same schema on Android and
 * desktop, so an events.json file can be copied between the two.
 */
class EventStore(dir: String, name: String = "events.json") {
    private val file = "$dir/$name".toPath()
    private val fs = FileSystem.SYSTEM
    private val backingItems = mutableListOf<EventItem>()

    @Synchronized
    fun items(): MutableList<EventItem> = backingItems

    @Synchronized
    fun load() {
        backingItems.clear()
        try {
            if (fs.exists(file)) {
                val json = fs.read(file) { readUtf8() }.trim()
                if (json.startsWith("[") && json.endsWith("]")) {
                    for (part in JsonUtil.splitTopLevel(json.substring(1, json.length - 1))) {
                        val t = part.trim()
                        if (t.startsWith("{")) backingItems.add(EventItem.fromJson(t))
                    }
                }
            }
        } catch (e: Exception) {
            backingItems.clear()
        }
        var changed = false
        // No samples/seeds: start empty, keep what the user created.
        for (e in backingItems) {
            if (e.icon.trim().isEmpty()) {
                e.icon = guessIcon(e)
                changed = true
            }
            if (e.category.trim().isEmpty()) {
                e.category = "Countdown"
                changed = true
            }
        }
        if (changed) save()
    }

    @Synchronized
    fun save() {
        try {
            file.parent?.let { if (!fs.exists(it)) fs.createDirectories(it) }
            val sb = StringBuilder("[\n")
            for (i in backingItems.indices) {
                sb.append("  ").append(backingItems[i].toJson())
                if (i + 1 < backingItems.size) sb.append(",")
                sb.append("\n")
            }
            sb.append("]\n")
            val tmp = "$file.tmp".toPath()
            fs.write(tmp) { writeUtf8(sb.toString()) }
            if (fs.exists(file)) fs.delete(file)
            fs.atomicMove(tmp, file)
        } catch (ignored: Exception) {
            try {
                val tmp = "$file.tmp".toPath()
                if (fs.exists(tmp)) fs.delete(tmp)
            } catch (ignored2: Exception) {
            }
        }
    }

    @Synchronized
    fun addOrUpdate(item: EventItem) {
        for (e in backingItems) {
            if (e.id == item.id) {
                e.title = item.title
                e.date = item.date
                e.hour = item.hour
                e.minute = item.minute
                e.message = item.message
                e.secretMessage = item.secretMessage
                e.secretEnabled = item.secretEnabled
                e.featured = item.featured
                e.repeatYearly = item.repeatYearly
                e.repeatMode = item.repeatMode.ifEmpty { if (item.repeatYearly) "yearly" else "once" }
                e.soundEnabled = item.soundEnabled
                e.soundName = item.soundName
                e.remind1d = item.remind1d
                e.remind7d = item.remind7d
                e.icon = item.icon
                e.accentHex = item.accentHex
                e.category = item.category
                e.senderId = item.senderId
                e.forPartner = item.forPartner
                e.delivered = item.delivered
                if (item.deliveredAtSec > e.deliveredAtSec) e.deliveredAtSec = item.deliveredAtSec
                if (item.seenAtSec > e.seenAtSec) e.seenAtSec = item.seenAtSec
                if (item.updatedAtSec > e.updatedAtSec) e.updatedAtSec = item.updatedAtSec
                e.replyMessage = item.replyMessage
                // Preserve conversation on edit sync: merge instead of overwrite
                // when incoming thread is older/smaller.
                if (item.replyThread.length >= e.replyThread.length) {
                    e.replyThread = item.replyThread
                } else {
                    e.mergeThread(item.replyThread)
                }
                // Don't wipe a local photo with an empty incoming one (sender
                // without photo re-sending must not clear receiver's image).
                if (item.photoUri.isNotEmpty()) e.photoUri = item.photoUri
                save()
                return
            }
        }
        if (item.repeatMode.isEmpty()) item.repeatMode = if (item.repeatYearly) "yearly" else "once"
        backingItems.add(item)
        save()
    }

    fun exportJson(): String {
        val sb = StringBuilder("[\n")
        val list = synchronized(this) { ArrayList(backingItems) }
        for (i in list.indices) {
            sb.append("  ").append(list[i].toJson())
            if (i + 1 < list.size) sb.append(",")
            sb.append("\n")
        }
        sb.append("]\n")
        return sb.toString()
    }

    @Synchronized
    fun importJson(json: String): Int {
        var count = 0
        try {
            val t = json.trim()
            if (!t.startsWith("[") || !t.endsWith("]")) return 0
            val inside = t.substring(1, t.length - 1)
            for (p in extractObjects(inside)) {
                try {
                    val e = EventItem.fromJson(p)
                    if (e.title.isBlank()) continue
                    addOrUpdate(e)
                    count++
                } catch (ignored: Exception) {
                }
            }
        } catch (ignored: Exception) {
        }
        return count
    }

    /** Extract top-level {...} objects (splitTopLevel strips a lone object into fields). */
    private fun extractObjects(inside: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var inStr = false
        var start = -1
        var i = 0
        while (i < inside.length) {
            val c = inside[i]
            if (inStr) {
                if (c == '\\' && i + 1 < inside.length) i++
                else if (c == '"') inStr = false
            } else {
                if (c == '"') inStr = true
                else if (c == '{') {
                    if (depth == 0) start = i
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out.add(inside.substring(start, i + 1))
                        start = -1
                    }
                    if (depth < 0) depth = 0
                }
            }
            i++
        }
        return out
    }

    @Synchronized
    fun delete(id: String) {
        backingItems.removeAll { it.id == id }
        save()
    }

    @Synchronized
    fun byId(id: String): EventItem? {
        for (e in backingItems) if (e.id == id) return e
        return null
    }

    @Synchronized
    fun sortedByNext(today: LocalDate): List<EventItem> {
        val copy = ArrayList(backingItems)
        copy.sortWith(compareBy<EventItem> { !it.featured }.thenBy { it.nextOccurrence(today) })
        return copy
    }

    companion object {
        private fun guessIcon(e: EventItem): String {
            val t = e.title.lowercase() + " " + e.displayCategory().lowercase()
            if (t.contains("birthday") || t.contains("cake")) return "🎂"
            if (t.contains("exam") || t.contains("school") || t.contains("graduation")) return "🎓"
            if (t.contains("wedding") || t.contains("anniversary")) return "💖"
            if (t.contains("trip") || t.contains("travel") || t.contains("flight")) return "✈"
            if (t.contains("app") || t.contains("release") || t.contains("launch") || t.contains("android")) return "🚀"
            if (t.contains("game")) return "🎮"
            if (t.contains("music") || t.contains("concert")) return "🎵"
            if (t.contains("sport") || t.contains("match") || t.contains("football")) return "⚽"
            if (t.contains("work") || t.contains("meeting")) return "💻"
            return "❤"
        }
    }
}
