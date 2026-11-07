package com.secount.app.logic

import java.time.LocalDate
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Loads/saves the countdown list as JSON. Same schema on Android and
 * desktop, so an events.json file can be copied between the two.
 */
class EventStore(dir: String, name: String = "events.json") {
    private val file = "$dir/$name".toPath()
    private val tempFile = "$file.tmp".toPath()
    private val backupFile = "$file.bak".toPath()
    private val fs = FileSystem.SYSTEM
    private val backingItems = mutableListOf<EventItem>()
    private var recoverySource: Path? = null

    @Synchronized
    fun items(): MutableList<EventItem> = backingItems

    @Synchronized
    fun load() {
        backingItems.clear()
        val pending = readItems(tempFile)
        val current = readItems(file)
        // The backup is a recovery fallback, so avoid parsing it during normal
        // launches when either the pending or primary snapshot is usable.
        val backup = if (pending == null && current == null) readItems(backupFile) else null
        val (source, items) = when {
            // A complete temporary snapshot means the previous save got as
            // far as writing its contents, even if the process stopped before
            // it could replace events.json.
            pending != null -> tempFile to pending
            current != null -> file to current
            backup != null -> backupFile to backup
            else -> null to emptyList<EventItem>()
        }
        backingItems.addAll(items)
        recoverySource = if (source != null && source != file) {
            if (restoreSnapshot(source, current != null)) null else source
        } else {
            null
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
    fun save(): Boolean {
        // Do not overwrite a recovered snapshot until it has been restored to
        // the primary file. A failed restoration leaves the recovery copy
        // untouched so the next launch can try again.
        val pendingRecovery = recoverySource
        if (pendingRecovery != null) {
            if (!restoreSnapshot(pendingRecovery, readItems(file) != null)) return false
            recoverySource = null
        }
        try {
            file.parent?.let { if (!fs.exists(it)) fs.createDirectories(it) }
            val sb = StringBuilder("[\n")
            for (i in backingItems.indices) {
                sb.append("  ").append(backingItems[i].toJson())
                if (i + 1 < backingItems.size) sb.append(",")
                sb.append("\n")
            }
            sb.append("]\n")
            fs.write(tempFile) { writeUtf8(sb.toString()) }

            if (fs.exists(file)) {
                // atomicMove may not replace an existing destination on every
                // supported filesystem. Rotate the old snapshot to a backup
                // first, then move into an absent destination. If power is
                // lost in between, load() can restore the temp or backup.
                if (fs.exists(backupFile)) fs.delete(backupFile)
                fs.atomicMove(file, backupFile)
            }

            fs.atomicMove(tempFile, file)
            recoverySource = null
            return true
        } catch (ignored: Exception) {
            // Leave a complete temp/backup in place. load() validates both and
            // recovers the newest complete snapshot on the next launch.
            recoverySource = if (readItems(tempFile) != null) tempFile else null
            return false
        }
    }

    /** Parse one complete event-array snapshot, or return null if unusable. */
    private fun readItems(path: Path): List<EventItem>? {
        return try {
            if (!fs.exists(path)) return null
            parseEventArray(fs.read(path) { readUtf8() })
        } catch (ignored: Exception) {
            null
        }
    }

    /** Restore a validated candidate without overwriting the only good copy. */
    private fun restoreSnapshot(source: Path, primaryIsValid: Boolean): Boolean {
        if (source == file) return true
        return try {
            if (fs.exists(file)) {
                if (primaryIsValid) {
                    if (fs.exists(backupFile)) fs.delete(backupFile)
                    fs.atomicMove(file, backupFile)
                } else {
                    fs.delete(file)
                }
            }
            fs.atomicMove(source, file)
            true
        } catch (ignored: Exception) {
            false
        }
    }

    @Synchronized
    fun addOrUpdate(item: EventItem): Boolean {
        for ((index, e) in backingItems.withIndex()) {
            if (e.id == item.id) {
                val original = e.copyFromJson()
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
                if (save()) return true
                backingItems[index] = original
                return false
            }
        }
        if (item.repeatMode.isEmpty()) item.repeatMode = if (item.repeatYearly) "yearly" else "once"
        backingItems.add(item)
        if (save()) return true
        backingItems.removeAll { it.id == item.id }
        return false
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
            val parsed = parseEventArray(json) ?: return 0
            for (e in parsed) {
                try {
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
