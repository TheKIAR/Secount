package com.secount.app.logic

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * JSON serialization for [EventItem]. Same schema on Android and desktop,
 * so events.json files are interchangeable between the two.
 * (Extracted from EventItem.kt, no behavior change.)
 */

fun EventItem.toJson(): String {
    return "{\"id\":" + jsonQ(id) +
        ",\"title\":" + jsonQ(title) +
        ",\"date\":" + jsonQ(date.toString()) +
        ",\"hour\":" + hour.coerceIn(0, 23) +
        ",\"minute\":" + minute.coerceIn(0, 59) +
        ",\"message\":" + jsonQ(message) +
        ",\"secretMessage\":" + jsonQ(secretMessage) +
        ",\"secretEnabled\":" + secretEnabled +
        ",\"featured\":" + featured +
        ",\"repeatYearly\":" + repeatYearly +
        ",\"repeatMode\":" + jsonQ(effectiveRepeat()) +
        ",\"soundEnabled\":" + soundEnabled +
        ",\"soundName\":" + jsonQ(soundName) +
        ",\"remind1d\":" + remind1d +
        ",\"remind7d\":" + remind7d +
        ",\"icon\":" + jsonQ(icon) +
        ",\"accentHex\":" + jsonQ(accentHex) +
        ",\"category\":" + jsonQ(category) +
        ",\"senderId\":" + jsonQ(senderId) +
        ",\"forPartner\":" + forPartner +
        ",\"delivered\":" + delivered +
        ",\"deliveredAtSec\":" + deliveredAtSec +
        ",\"seenAtSec\":" + seenAtSec +
        ",\"updatedAtSec\":" + updatedAtSec +
        ",\"replyMessage\":" + jsonQ(replyMessage) +
        ",\"replyThread\":" + jsonQ(replyThread) +
        ",\"photoUri\":" + jsonQ(photoUri) +
        ",\"createdAt\":" + jsonQ(createdAt.toString()) + "}"
}

fun EventItem.copyFromJson(): EventItem = EventItem.fromJson(toJson())

fun EventItem.Companion.fromJson(obj: String): EventItem {
    val e = EventItem()
    for (part in JsonUtil.splitTopLevel(obj.trim())) {
        val colon = part.indexOf(':')
        if (colon < 0) continue
        val key = JsonUtil.unquote(part.substring(0, colon).trim())
        val `val` = part.substring(colon + 1).trim()
        try {
            when (key) {
                "id" -> e.id = JsonUtil.unquote(`val`)
                "title" -> e.title = JsonUtil.unquote(`val`)
                "date" -> e.date = LocalDate.parse(JsonUtil.unquote(`val`))
                "hour" -> e.hour = JsonUtil.unquote(`val`).toIntOrNull() ?: (`val`.toIntOrNull() ?: 9)
                "minute" -> e.minute = JsonUtil.unquote(`val`).toIntOrNull() ?: (`val`.toIntOrNull() ?: 0)
                "message" -> e.message = JsonUtil.unquote(`val`)
                "secretMessage" -> e.secretMessage = JsonUtil.unquote(`val`)
                "secretEnabled" -> e.secretEnabled = `val`.toBoolean()
                "featured" -> e.featured = `val`.toBoolean()
                "repeatYearly" -> e.repeatYearly = `val`.toBoolean()
                "repeatMode" -> e.repeatMode = JsonUtil.unquote(`val`)
                "repeat" -> e.repeatMode = JsonUtil.unquote(`val`)
                "soundEnabled" -> e.soundEnabled = `val`.toBoolean()
                "soundName" -> e.soundName = JsonUtil.unquote(`val`)
                "remind1d" -> e.remind1d = `val`.toBoolean()
                "remind7d" -> e.remind7d = `val`.toBoolean()
                "icon" -> e.icon = JsonUtil.unquote(`val`)
                "accentHex" -> e.accentHex = JsonUtil.unquote(`val`)
                "color" -> e.accentHex = JsonUtil.unquote(`val`)
                "category" -> e.category = JsonUtil.unquote(`val`)
                "kind" -> e.category = JsonUtil.unquote(`val`)
                "senderId" -> e.senderId = JsonUtil.unquote(`val`)
                "forPartner" -> e.forPartner = `val`.toBoolean()
                "delivered" -> e.delivered = `val`.toBoolean()
                "deliveredAtSec" -> e.deliveredAtSec = JsonUtil.unquote(`val`).toLongOrNull() ?: (`val`.toLongOrNull() ?: 0L)
                "seenAtSec" -> e.seenAtSec = JsonUtil.unquote(`val`).toLongOrNull() ?: (`val`.toLongOrNull() ?: 0L)
                "updatedAtSec" -> e.updatedAtSec = JsonUtil.unquote(`val`).toLongOrNull() ?: (`val`.toLongOrNull() ?: 0L)
                "replyMessage" -> e.replyMessage = JsonUtil.unquote(`val`)
                "reply" -> e.replyMessage = JsonUtil.unquote(`val`)
                "replyThread" -> e.replyThread = JsonUtil.unquote(`val`)
                "photoUri" -> e.photoUri = JsonUtil.unquote(`val`)
                "photo" -> e.photoUri = JsonUtil.unquote(`val`)
                "createdAt" -> e.createdAt = LocalDateTime.parse(JsonUtil.unquote(`val`))
                else -> {}
            }
        } catch (ignored: Exception) {
        }
    }
    if (e.icon.isEmpty()) e.icon = "📅"
    if (e.category.isEmpty()) e.category = "Countdown"
    if (e.repeatMode.isEmpty()) e.repeatMode = if (e.repeatYearly) "yearly" else "once"
    e.repeatYearly = e.repeatMode != "once"
    e.hour = e.hour.coerceIn(0, 23)
    e.minute = e.minute.coerceIn(0, 59)
    if (e.soundName.isEmpty()) e.soundName = "Chime"
    if (e.replyThread.isEmpty() && e.replyMessage.isNotEmpty()) {
        e.replyThread = "0|partner|" + e.replyMessage.replace("\n", " ")
    }
    return e
}

private fun jsonQ(s: String?): String {
    return "\"" + JsonUtil.escape(s ?: "") + "\""
}
