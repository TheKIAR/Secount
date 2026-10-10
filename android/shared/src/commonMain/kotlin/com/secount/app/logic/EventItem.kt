package com.secount.app.logic

import androidx.compose.ui.graphics.Color
import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Countdown model. Same JSON schema everywhere, so events.json files are
 * interchangeable between Android and desktop.
 */
class EventItem {
    var id: String = UUID.randomUUID().toString().replace("-", "")
    var title: String = ""
    var date: LocalDate = LocalDate.now().plusDays(7)
    var hour: Int = 9
    var minute: Int = 0
    var message: String = ""
    var secretMessage: String = ""
    var secretEnabled: Boolean = false
    var featured: Boolean = false
    var repeatYearly: Boolean = true
    /** once | yearly | monthly | weekly. Empty = legacy (use repeatYearly). */
    var repeatMode: String = ""
    var soundEnabled: Boolean = true
    /** Chime | Soft | Silent */
    var soundName: String = "Chime"
    var remind1d: Boolean = true
    var remind7d: Boolean = false
    /** Full reply conversation, lines of "epochSec|who|text". replyMessage keeps latest for compat. */
    var replyThread: String = ""
    /** Attached photo filename (app photos dir). Empty = none. */
    var photoUri: String = ""
    var createdAt: LocalDateTime = LocalDateTime.now()

    /** Emoji / symbol shown on the card. */
    var icon: String = "❤"

    /** Accent color as #RRGGBB. Empty = use app brand color. */
    var accentHex: String = ""

    /** Free-form category: Birthday, Exam, Wedding, App Release, Holiday, Work, ... */
    var category: String = "Countdown"

    /** Account id of the creator. Empty = created before pairing existed. */
    var senderId: String = ""

    /** True = addressed to the partner (they reveal it at zero). */
    var forPartner: Boolean = false

    /** True once the partner has opened it at zero (receipt). */
    var delivered: Boolean = false

    /** Epoch sec when delivered receipt arrived (0 = unknown). */
    var deliveredAtSec: Long = 0L

    /** Epoch sec when partner opened/read the message (seen receipt). */
    var seenAtSec: Long = 0L

    /** Last edit epoch sec — prevents stale overwrites on synced edit. */
    var updatedAtSec: Long = 0L

    /** Reply thread on a shared secret (receiver answers the sender). */
    var replyMessage: String = ""

    fun effectiveRepeat(): String {
        if (repeatMode == "once" || repeatMode == "yearly" || repeatMode == "monthly" || repeatMode == "weekly") return repeatMode
        return if (repeatYearly) "yearly" else "once"
    }

    fun setRepeat(mode: String) {
        repeatMode = mode
        repeatYearly = mode != "once"
    }

    fun nextOccurrence(today: LocalDate): LocalDate {
        when (effectiveRepeat()) {
            "once" -> return date
            "weekly" -> {
                if (!today.isAfter(date)) return date
                var d = date
                var guard = 0
                while (d.isBefore(today) && guard < 520) {
                    d = d.plusWeeks(1)
                    guard++
                }
                return d
            }
            "monthly" -> {
                if (!today.isAfter(date)) return date
                var y = today.year
                var m = today.monthValue
                repeat(25) {
                    val len = LocalDate.of(y, m, 1).lengthOfMonth()
                    val cand = LocalDate.of(y, m, minOf(date.dayOfMonth, len))
                    if (!cand.isBefore(today) && !cand.isBefore(date)) return cand
                    m++
                    if (m > 12) {
                        m = 1
                        y++
                    }
                }
                return date
            }
            else -> {
                val day = minOf(
                    date.dayOfMonth,
                    LocalDate.of(today.year, date.month, 1).lengthOfMonth()
                )
                val candidate: LocalDate = try {
                    LocalDate.of(today.year, date.month, day)
                } catch (e: DateTimeException) {
                    LocalDate.of(today.year, date.month, 1)
                        .withDayOfMonth(LocalDate.of(today.year, date.month, 1).lengthOfMonth())
                }
                return if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
            }
        }
    }

    fun isDueToday(today: LocalDate): Boolean {
        if (today.isBefore(date)) return false
        return when (effectiveRepeat()) {
            "once" -> date == today
            "weekly" -> date.dayOfWeek == today.dayOfWeek
            "monthly" -> {
                val want = minOf(date.dayOfMonth, today.lengthOfMonth())
                today.dayOfMonth == want
            }
            // Leap-day anniversaries fall back to Feb 28 in non-leap years,
            // matching nextOccurrence() so reminders fire on the right day.
            else -> nextOccurrence(today) == today
        }
    }

    fun isPast(today: LocalDate): Boolean {
        if (effectiveRepeat() != "once") return false
        return date.isBefore(today)
    }

    fun targetDateTime(today: LocalDate): LocalDateTime {
        val d = if (effectiveRepeat() == "once") date else nextOccurrence(today)
        return try {
            d.atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        } catch (e: Exception) {
            d.atStartOfDay()
        }
    }

    fun daysUntil(today: LocalDate): Long {
        val next = if (effectiveRepeat() != "once") nextOccurrence(today) else date
        return ChronoUnit.DAYS.between(today, next)
    }

    /** 0..1 progress from creation toward the target (for progress bars). */
    fun progress01(today: LocalDate): Float {
        try {
            val start = createdAt.toLocalDate()
            val end = if (effectiveRepeat() != "once") nextOccurrence(today) else date
            val total = ChronoUnit.DAYS.between(start, end)
            if (total <= 0) return if (isDueToday(today)) 1f else 0f
            val done = ChronoUnit.DAYS.between(start, today)
            var p = done.toFloat() / total.toFloat()
            if (p < 0f) p = 0f
            if (p > 1f) p = 1f
            return p
        } catch (e: Exception) {
            return 0f
        }
    }

    fun hasSecret(): Boolean {
        // Secret text alone is enough — the toggle is auto-armed on save,
        // but old items may have text with the flag off. Never hide those.
        return secretMessage.trim().isNotEmpty()
    }

    fun displayIcon(): String {
        return if (icon.trim().isEmpty()) "📅" else icon
    }

    fun displayCategory(): String {
        return if (category.trim().isEmpty()) "Countdown" else category
    }

    /** True if this device created the countdown. */
    fun isMine(myId: String): Boolean = senderId.isEmpty() || senderId == myId

    /** True if the partner sent it to me (I reveal it at zero). */
    fun isForMe(myId: String): Boolean = forPartner && senderId.isNotEmpty() && senderId != myId

    fun accentColor(fallback: Color): Color {
        return parseAccent(accentHex) ?: fallback
    }

    fun countdownText(now: LocalDateTime): String {
        val today = now.toLocalDate()
        if (isDueToday(today)) {
            val target = targetDateTime(today)
            if (!now.isBefore(target)) return "00d 00:00:00 • HERE"
            var s = Duration.between(now, target).seconds
            if (s < 0) s = 0
            return "00d ${pad2(s / 3600)}:${pad2((s % 3600) / 60)}:${pad2(s % 60)} • TODAY"
        }
        val next = targetDateTime(today)
        var s = Duration.between(now, next).seconds
        if (s < 0) s = 0
        return "${pad2(s / 86400)}d ${pad2((s % 86400) / 3600)}:${pad2((s % 3600) / 60)}:${pad2(s % 60)}"
    }

    fun shortCountdown(today: LocalDate): String {
        if (isDueToday(today)) return "Today!"
        val d = daysUntil(today)
        if (d < 0) return "${-d}d ago"
        if (d == 1L) return "Tomorrow"
        return "$d days left"
    }

    /** 12-hour AM/PM label, e.g. "3:05 PM". Internal storage stays 24h. */
    fun timeLabel(): String {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        val ampm = if (h < 12) "AM" else "PM"
        var h12 = h % 12
        if (h12 == 0) h12 = 12
        return "$h12:${if (m < 10) "0$m" else "$m"} $ampm"
    }

    /** Short epoch-sec -> "3:05 PM • 12 Sep" style for receipts. */
    fun receiptLabel(epochSec: Long): String {
        if (epochSec <= 0) return ""
        return try {
            val dt = java.time.Instant.ofEpochSecond(epochSec)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            val h = dt.hour
            val ampm = if (h < 12) "AM" else "PM"
            var h12 = h % 12
            if (h12 == 0) h12 = 12
            val mm = if (dt.minute < 10) "0${dt.minute}" else "${dt.minute}"
            "$h12:$mm $ampm • ${dt.dayOfMonth} ${dt.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"
        } catch (e: Exception) {
            ""
        }
    }

    fun repeatLabel(): String = when (effectiveRepeat()) {
        "yearly" -> "yearly"
        "monthly" -> "monthly"
        "weekly" -> "weekly"
        else -> "one-time"
    }

    fun dateLabel(): String {
        val f = DateTimeFormatter.ofPattern("dd MMMM")
        val full = DateTimeFormatter.ofPattern("dd MMMM yyyy")
        val base = if (effectiveRepeat() == "once") date.format(full) else date.format(f)
        return "$base • ${timeLabel()} • ${repeatLabel()}"
    }

    fun threadEntries(): List<Triple<Long, String, String>> {
        val out = mutableListOf<Triple<Long, String, String>>()
        if (replyThread.isBlank()) {
            if (replyMessage.isNotBlank()) out.add(Triple(0L, "partner", replyMessage))
            return out
        }
        for (line in replyThread.lines()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val p1 = t.indexOf('|')
            if (p1 < 0) {
                out.add(Triple(0L, "", t))
                continue
            }
            val p2 = t.indexOf('|', p1 + 1)
            if (p2 < 0) {
                out.add(Triple(0L, "", t))
                continue
            }
            out.add(Triple(t.substring(0, p1).toLongOrNull() ?: 0L, t.substring(p1 + 1, p2), t.substring(p2 + 1)))
        }
        if (out.isEmpty() && replyMessage.isNotBlank()) out.add(Triple(0L, "partner", replyMessage))
        return out
    }

    fun appendReply(who: String, text: String, atSec: Long) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val safe = clean.replace("\n", " ")
        val entry = "$atSec|${who.replace("|", "")}|$safe"
        replyThread = if (replyThread.isBlank()) entry else replyThread + "\n" + entry
        replyMessage = clean
    }

    /** Merge another thread in (union, sorted by timestamp) — keeps replies on edit sync. */
    fun mergeThread(other: String) {
        if (other.isBlank()) return
        val seen = threadEntries().map { Triple(it.first, it.second, it.third) }.toMutableSet()
        for (line in other.lines()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val p1 = t.indexOf('|')
            if (p1 < 0) continue
            val p2 = t.indexOf('|', p1 + 1)
            if (p2 < 0) continue
            val ts = t.substring(0, p1).toLongOrNull() ?: 0L
            val who = t.substring(p1 + 1, p2)
            val txt = t.substring(p2 + 1)
            val triple = Triple(ts, who, txt)
            if (seen.add(triple)) {
                replyThread = if (replyThread.isBlank()) t else replyThread + "\n" + t
                if (txt.isNotBlank()) replyMessage = txt
            }
        }
        // Keep chronological order.
        val sorted = threadEntries().sortedBy { it.first }
        replyThread = sorted.joinToString("\n") { "${it.first}|${it.second}|${it.third}" }
        if (sorted.isNotEmpty()) replyMessage = sorted.last().third
    }

    fun touchUpdated(atSec: Long) {
        updatedAtSec = atSec
    }

    companion object {
        val CATEGORY_PRESETS = listOf(
            "Countdown", "Birthday", "Anniversary", "Wedding", "Holiday",
            "Exam", "Graduation", "App Release", "Game Launch", "Trip",
            "Work", "Health", "Custom"
        )

        val ICON_PRESETS = listOf(
            "❤", "🎂", "🎉", "⭐", "🚀",
            "🎓", "✈", "💖", "🏠", "🎵",
            "⚽", "💻", "🎮", "🌟", "⏰", "🔔"
        )

        fun parseAccent(hex: String?): Color? {
            try {
                if (hex == null) return null
                var h = hex.trim()
                if (h.isEmpty()) return null
                if (h.startsWith("#")) h = h.substring(1)
                if (h.length == 6) {
                    val r = h.substring(0, 2).toInt(16)
                    val g = h.substring(2, 4).toInt(16)
                    val b = h.substring(4, 6).toInt(16)
                    return Color(r, g, b)
                }
            } catch (ignored: Exception) {
            }
            return null
        }

        private fun pad2(v: Long): String = if (v < 10) "0$v" else "$v"
    }
}
