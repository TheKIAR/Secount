package com.secount.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CountdownBrowserControls(
    mainTab: String,
    viewMode: String,
    query: String,
    filter: String,
    sort: String,
    mineCount: Int,
    inboxCount: Int,
    shownCount: Int,
    fontScale: Float,
    onMainTab: (String) -> Unit,
    onViewMode: (String) -> Unit,
    onQuery: (String) -> Unit,
    onFilter: (String) -> Unit,
    onSort: (String) -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmartTab(
                selected = mainTab == "Mine",
                title = "♥ Mine",
                subtitle = mineCount.toString(),
                onClick = { onMainTab("Mine") },
                modifier = Modifier.weight(1f),
                fontScale = fontScale
            )
            SmartTab(
                selected = mainTab == "Inbox",
                title = if (inboxCount > 0) "💌 Inbox ($inboxCount)" else "💌 Inbox",
                subtitle = if (inboxCount > 0) "new!" else "D-day only",
                onClick = { onMainTab("Inbox") },
                modifier = Modifier.weight(1f),
                fontScale = fontScale
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmartTab(
                selected = viewMode == "List",
                title = "☰ ${Lang.t("viewList")}",
                subtitle = "in order",
                onClick = { onViewMode("List") },
                modifier = Modifier.weight(1f),
                fontScale = fontScale,
                compact = true
            )
            SmartTab(
                selected = viewMode == "Calendar",
                title = "📅 ${Lang.t("viewCalendar")}",
                subtitle = "by date",
                onClick = { onViewMode("Calendar") },
                modifier = Modifier.weight(1f),
                fontScale = fontScale,
                compact = true
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            placeholder = { Text(Lang.t("search"), fontSize = scaled(14.sp, fontScale)) },
            leadingIcon = { Text("🔍", fontSize = scaled(18.sp, fontScale)) },
            trailingIcon = {
                if (query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text("✕") }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp)
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (option in FILTERS) {
                val selected = filter == option
                FilterChip(
                    selected = selected,
                    onClick = { onFilter(option) },
                    label = {
                        Text(
                            filterEmoji(option) + Lang.filterLabel(option),
                            fontSize = scaled(13.sp, fontScale),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when (shownCount) {
                    0 -> "No results"
                    1 -> "1 countdown"
                    else -> "$shownCount countdowns"
                },
                fontSize = scaled(12.sp, fontScale),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            MappedDropDown(
                Lang.t("sort"), SORTS, sort, onSort,
                { Lang.sortLabel(it) }, Modifier.weight(1f)
            )
        }
    }
}