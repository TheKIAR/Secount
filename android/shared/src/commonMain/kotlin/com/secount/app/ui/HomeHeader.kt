package com.secount.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secount.app.logic.EventItem
import com.secount.app.logic.PairStore
import java.time.LocalDateTime

@Composable
internal fun HomeHeader(
    now: LocalDateTime,
    mainTab: String,
    shown: List<EventItem>,
    pair: PairStore,
    syncing: Boolean,
    savedCount: Int,
    todayCount: Int,
    weekCount: Int,
    inboxCount: Int,
    myId: String,
    fontScale: Float,
    onOpenMenu: () -> Unit,
    onOpenNext: (EventItem) -> Unit
) {
    Box(
        Modifier.fillMaxWidth()
            .background(heroGradient())
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenMenu) {
                    Text("☰", fontSize = scaled(24.sp, fontScale), color = Color.White)
                }
                BrandMark(Modifier.size(34.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${greetingFor(now.hour)} ♥",
                        fontSize = scaled(13.sp, fontScale),
                        color = Color.White.copy(alpha = 0.92f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (mainTab == "Inbox") "Your surprises, right on time"
                        else if (shown.isNotEmpty()) "Next up: ${shown[0].title}"
                        else Lang.t("appTitle"),
                        fontSize = scaled(21.sp, fontScale),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.2f)) {
                    Text(
                        if (pair.isPaired()) "✉ ${pair.partnerCode()}" else if (syncing) "○ …" else "○ Offline",
                        color = Color.White,
                        fontSize = scaled(12.sp, fontScale),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroStat("$savedCount", "saved", Modifier.weight(1f), fontScale)
                HeroStat("$todayCount", "today", Modifier.weight(1f), fontScale)
                HeroStat("$weekCount", "this week", Modifier.weight(1f), fontScale)
                HeroStat("$inboxCount", "inbox", Modifier.weight(1f), fontScale)
            }
            if (mainTab == "Mine" && shown.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val next = shown[0]
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenNext(next) }
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(next.displayIcon(), fontSize = scaled(28.sp, fontScale))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "UP NEXT • ${next.displayCategory().uppercase()}",
                                fontSize = scaled(10.sp, fontScale),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(next.title, fontWeight = FontWeight.Bold, fontSize = scaled(16.sp, fontScale))
                            Text(
                                "${next.shortCountdown(now.toLocalDate())} • ${next.dateLabel()}",
                                fontSize = scaled(12.sp, fontScale),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                next.countdownText(now),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = scaled(14.sp, fontScale)
                            )
                            Text(
                                "Tap to open →",
                                fontSize = scaled(11.sp, fontScale),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}