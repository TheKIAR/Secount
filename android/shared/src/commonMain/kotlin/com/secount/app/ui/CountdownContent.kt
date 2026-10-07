package com.secount.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secount.app.logic.EventItem
import com.secount.app.logic.EventStore
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
internal fun CountdownContent(
    shown: List<EventItem>,
    mainTab: String,
    viewMode: String,
    modifier: Modifier,
    query: String,
    filter: String,
    now: LocalDateTime,
    today: LocalDate,
    store: EventStore,
    myId: String,
    month: LocalDate,
    selectedDay: LocalDate?,
    undoItem: EventItem?,
    fontScale: Float,
    onMonth: (LocalDate) -> Unit,
    onDay: (LocalDate?) -> Unit,
    onUndo: (EventItem) -> Unit,
    onDismissUndo: () -> Unit,
    onCreate: () -> Unit,
    onOpenSecret: (EventItem) -> Unit,
    onEdit: (EventItem) -> Unit,
    onDuplicate: (EventItem) -> Unit,
    onRing: (EventItem) -> Unit,
    onMessages: (EventItem) -> Unit,
    onDelete: (EventItem) -> Unit
) {
    if (viewMode == "Calendar") {
        CalendarView(
            month = month,
            today = today,
            store = store,
            myId = myId,
            selected = selectedDay,
            fontScale = fontScale,
            onMonth = onMonth,
            onDay = onDay
        )
    }
    undoItem?.let { deleted ->
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${Lang.t("deleted")} '${deleted.title}'",
                    fontSize = scaled(13.sp, fontScale),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onUndo(deleted) }) { Text(Lang.t("undo")) }
                TextButton(onClick = onDismissUndo) { Text(Lang.t("dismiss")) }
            }
        }
    }
    if (shown.isEmpty()) {
        Column(
            modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (mainTab == "Inbox") {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text("💌", fontSize = scaled(52.sp, fontScale), modifier = Modifier.padding(20.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("All quiet — for now", fontSize = scaled(20.sp, fontScale), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Partner surprises appear here on D-day, then vanish after the day ends. Yearly surprises return each year.",
                    fontSize = scaled(14.sp, fontScale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        "♥",
                        fontSize = scaled(52.sp, fontScale),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(20.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Start your first countdown", fontSize = scaled(20.sp, fontScale), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (query.isNotBlank() || filter != "All") Lang.t("emptyNomatch")
                    else "Three easy steps: 1) Name it  2) Pick a date  3) Add a secret or photo if you like.",
                    fontSize = scaled(14.sp, fontScale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onCreate,
                    modifier = Modifier.heightIn(min = 52.dp),
                    shape = RoundedCornerShape(18.dp)
                ) { Text(Lang.t("newBtn"), fontSize = scaled(15.sp, fontScale)) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tip: use the search and chips above to find anything fast.",
                    fontSize = scaled(12.sp, fontScale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(shown, key = { it.id }) { item ->
                if (item.isForMe(myId)) {
                    SecretInboxCard(item, fontScale = fontScale, onOpen = { onOpenSecret(item) })
                } else {
                    EventCard(
                        item,
                        now,
                        myId,
                        fontScale = fontScale,
                        onEdit = { onEdit(item) },
                        onDuplicate = { onDuplicate(item) },
                        onRing = { onRing(item) },
                        onMessages = { onMessages(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }
    }
}