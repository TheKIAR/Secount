package com.secount.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secount.app.logic.EventItem
import com.secount.app.logic.loadPhotoBitmap
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/** Countdown cards (extracted from App.kt, no behavior change). */

@Composable
internal fun LetterReveal(
    text: String,
    key: String,
    charDelayMs: Long = 70L
) {
    var shown by remember(key) { mutableStateOf(0) }
    var replayTick by remember(key) { mutableStateOf(0) }
    @Suppress("UNUSED_EXPRESSION")
    replayTick
    LaunchedEffect(key, replayTick) {
        shown = 0
        // Small pause before the letter starts writing.
        delay(400)
        while (shown < text.length) {
            delay(charDelayMs)
            shown++
        }
    }
    val done = shown >= text.length
    Column {
        Text(
            if (shown <= 0) "✒…" else text.take(shown) + if (done) "" else "▍",
            fontSize = 15.sp
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = { replayTick++ },
                enabled = done
            ) { Text(if (done) "↻ Replay" else "$shown/${text.length}") }
        }
    }
}

@Composable
internal fun SecretInboxCard(
    e: EventItem,
    fontScale: Float = 1f,
    onOpen: () -> Unit
) {
    val accent = e.accentColor(Brand)
    val replies = try { e.threadEntries().size } catch (ignored: Exception) { 0 }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(7.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.14f)) {
                    Text("🎁", fontSize = scaled(26.sp, fontScale), modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (e.title.isNotBlank()) e.title else "You have a new secret message — open it",
                        fontWeight = FontWeight.Bold,
                        fontSize = scaled(17.sp, fontScale)
                    )
                    Spacer(Modifier.height(2.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.14f)) {
                        Text(
                            "🎁 FOR YOU • ${e.shortCountdown(LocalDate.now()).uppercase()}",
                            color = accent, fontSize = scaled(11.sp, fontScale), fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Your partner's surprise arrived — open it when you're ready. Nothing was visible before today.",
                fontSize = scaled(14.sp, fontScale),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(e.dateLabel(), fontSize = scaled(12.sp, fontScale), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (replies > 0) {
                Spacer(Modifier.height(6.dp))
                AssistChip(
                    onClick = onOpen,
                    label = { Text("💬 $replies repl${if (replies == 1) "y" else "ies"} — tap to read", fontSize = scaled(12.sp, fontScale)) }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onOpen,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text(Lang.t("openMsg"), fontSize = scaled(14.sp, fontScale)) }
            }
            }
        }
    }
}

@Composable
internal fun EventCard(
    e: EventItem,
    now: LocalDateTime,
    myId: String,
    fontScale: Float = 1f,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onRing: () -> Unit,
    onDelete: () -> Unit,
    onMessages: () -> Unit = onRing
) {
    val today = now.toLocalDate()
    val due = e.isDueToday(today)
    val accent = e.accentColor(MaterialTheme.colorScheme.primary)
    val mine = e.isMine(myId)
    val forMe = e.isForMe(myId)
    val daysLeft = try { e.daysUntil(today) } catch (ignored: Exception) { 0L }
    val urgency: Color = when {
        due -> Success
        daysLeft < 0 -> MaterialTheme.colorScheme.error
        daysLeft <= 1 -> MaterialTheme.colorScheme.primary
        daysLeft <= 7 -> MaterialTheme.colorScheme.tertiary
        else -> accent
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Accent rail: instant color identity, helps scanning.
            Box(Modifier.width(7.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.14f)) {
                    Text(e.displayIcon(), fontSize = scaled(26.sp, fontScale), modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (e.featured) "★ " else "") + e.title.ifBlank { "Untitled countdown" },
                        fontWeight = FontWeight.Bold,
                        fontSize = scaled(18.sp, fontScale)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${e.displayCategory().uppercase()} • ${e.shortCountdown(today).uppercase()}",
                        color = accent, fontSize = scaled(11.sp, fontScale), fontWeight = FontWeight.Bold
                    )
                }
                // Live timer block: big, mono, always readable.
                Surface(shape = RoundedCornerShape(14.dp), color = urgency.copy(alpha = 0.12f)) {
                    Column(
                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                    Text(
                        if (due) "♥ TODAY" else "♥ LIVE",
                        color = urgency,
                        fontSize = scaled(10.sp, fontScale),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        e.countdownText(now),
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        fontSize = scaled(15.sp, fontScale)
                    )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Status chips: secret / partner / replies / repeat — glanceable.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (e.hasSecret() && !forMe) StatusPill("🎁 Secret ready", accent, fontScale)
                if (forMe) StatusPill("🎁 For you", accent, fontScale)
                else if (mine && e.forPartner) {
                    val label = if (e.seenAtSec > 0) "👁 Seen" else if (e.delivered) "✉ Delivered" else "✉ To partner"
                    StatusPill(label, accent, fontScale)
                }
                val replyCount = e.threadEntries().size
                if (replyCount > 0) StatusPill("💬 $replyCount ${if (replyCount == 1) "reply" else "replies"}", accent, fontScale)
                if (e.effectiveRepeat() != "once") StatusPill("↻ ${e.repeatLabel()}", accent, fontScale)
                if (e.soundName == "Silent") StatusPill("🔇 Muted", accent, fontScale)
                if (due) StatusPill("♥ Day is here", Success, fontScale)
                else if (daysLeft in 1..7) StatusPill("⏳ $daysLeft day${if (daysLeft == 1L) "" else "s"} left", accent, fontScale)
            }
            Spacer(Modifier.height(6.dp))
            Text("📅 ${e.dateLabel()}" + if (e.photoUri.isNotEmpty()) " • 📷 Photo" else "", fontSize = scaled(12.sp, fontScale), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            if (forMe && !due) {
                Text(
                    "🎁 A surprise from your partner — the message arrives at zero.",
                    fontSize = scaled(14.sp, fontScale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val body = if (e.message.isEmpty()) "A special moment is waiting…" else e.message
                Text(body, fontSize = scaled(14.sp, fontScale))
            }
            if (e.photoUri.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val bmp = remember(e.photoUri) {
                    try {
                        loadPhotoBitmap(e.photoUri)
                    } catch (ignored: Exception) {
                        null
                    }
                }
                if (bmp != null) {
                    Image(
                        bmp, contentDescription = Lang.t("photo"),
                        modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(18.dp))
                    )
                }
            }
            val threadPreview = try { e.threadEntries() } catch (ignored: Exception) { emptyList() }
            if (threadPreview.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    val lastPreview = threadPreview.last()
                    Text(
                        "💬 ${threadPreview.size} ${if (threadPreview.size == 1) "reply" else "replies"} — \"${lastPreview.third.take(70)}\" — tap Messages to read & reply.",
                        fontSize = scaled(12.sp, fontScale), fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { e.progress01(today) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                color = urgency,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Spacer(Modifier.height(4.dp))
            // Big, labelled actions: approachable for everyone, 48dp targets.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End)) {
                TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 44.dp)) { Text(if (forMe && !mine) "View" else "✏ Edit", fontSize = scaled(13.sp, fontScale)) }
                if (mine) {
                    TextButton(onClick = onDuplicate, modifier = Modifier.heightIn(min = 44.dp)) { Text("⧉ Copy", fontSize = scaled(13.sp, fontScale)) }
                    TextButton(onClick = onRing, modifier = Modifier.heightIn(min = 44.dp)) { Text("🔔 Ring", fontSize = scaled(13.sp, fontScale)) }
                    TextButton(onClick = onMessages, modifier = Modifier.heightIn(min = 44.dp)) { Text("💬 Messages", fontSize = scaled(13.sp, fontScale)) }
                    TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 44.dp)) { Text("🗑 Delete", fontSize = scaled(13.sp, fontScale), color = MaterialTheme.colorScheme.error) }
                } else if (!mine && !forMe) {
                    TextButton(onClick = onMessages, modifier = Modifier.heightIn(min = 44.dp)) { Text("👁 View", fontSize = scaled(13.sp, fontScale)) }
                }
            }
            }
        }
    }
}
