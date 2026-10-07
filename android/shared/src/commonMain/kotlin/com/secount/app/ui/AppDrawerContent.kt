package com.secount.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secount.app.logic.PairStore

@Composable
internal fun AppDrawerContent(
    pair: PairStore,
    syncing: Boolean,
    themeName: String,
    darkMode: String,
    muted: Boolean,
    textSizePref: String,
    langPref: String,
    fontScale: Float,
    onNewCountdown: () -> Unit,
    onSync: () -> Unit,
    onConnect: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onTextSize: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onAppearance: (String) -> Unit,
    onTheme: (String) -> Unit
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth()
                .background(heroGradient())
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(Modifier.size(32.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Secount", fontWeight = FontWeight.Bold, fontSize = scaled(24.sp, fontScale), color = Color.White)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    themeByName(themeName).tagline,
                    fontSize = scaled(12.sp, fontScale),
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.2f)) {
                    Text(
                        if (pair.isPaired()) "✉ Connected • ${pair.partnerCode()}"
                        else if (syncing) "○ Syncing…"
                        else "○ Not connected — tap Connect",
                        fontSize = scaled(12.sp, fontScale),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            DrawerSection(Lang.t("secCountdowns"))
            NavigationDrawerItem(
                label = { Text(Lang.t("newCountdown"), fontSize = scaled(14.sp, fontScale)) },
                selected = false,
                icon = { Text("＋", fontWeight = FontWeight.Bold) },
                onClick = onNewCountdown
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            DrawerSection(Lang.t("secConnection"))
            NavigationDrawerItem(
                label = { Text(if (syncing) Lang.t("syncing") else Lang.t("syncNow"), fontSize = scaled(14.sp, fontScale)) },
                selected = false,
                icon = { Text("⟳") },
                onClick = onSync
            )
            NavigationDrawerItem(
                label = { Text(if (pair.isPaired()) "✉ ${pair.partnerCode()}" else Lang.t("connectPartner"), fontSize = scaled(14.sp, fontScale)) },
                selected = false,
                icon = { Text("✉") },
                onClick = onConnect
            )
            NavigationDrawerItem(
                label = { Text(Lang.t("appPin"), fontSize = scaled(14.sp, fontScale)) },
                selected = false,
                icon = { Text("🔒") },
                onClick = onPin
            )
            NavigationDrawerItem(
                label = { Text(if (muted) Lang.t("unmute") else Lang.t("mute"), fontSize = scaled(14.sp, fontScale)) },
                selected = false,
                icon = { Text(if (muted) "🔇" else "🔔") },
                onClick = onMute
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            DrawerSection(Lang.t("secBackup"))
            NavigationDrawerItem(
                label = { Text(Lang.t("exportBackup"), fontSize = scaled(14.sp, fontScale)) },
                selected = false,
                icon = { Text("⤴") },
                onClick = onExport
            )
            NavigationDrawerItem(
                label = { Text(Lang.t("importBackup"), fontSize = scaled(14.sp, fontScale)) },
                selected = false,
                icon = { Text("⤵") },
                onClick = onImport
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            DrawerSection("READABILITY")
            for (size in TEXT_SIZES) {
                NavigationDrawerItem(
                    label = { Text((if (size == textSizePref) "● " else "○ ") + size, fontSize = scaled(14.sp, fontScale)) },
                    selected = size == textSizePref,
                    icon = { Text(if (size == TEXT_SIZES[0]) "A" else if (size == TEXT_SIZES[1]) "A＋" else "A＋＋") },
                    onClick = { onTextSize(size) }
                )
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            DrawerSection(Lang.t("secLanguage"))
            for (language in LANGS) {
                NavigationDrawerItem(
                    label = { Text((if (language == langPref) "● " else "○ ") + langDisplay(language), fontSize = scaled(14.sp, fontScale)) },
                    selected = language == langPref,
                    onClick = { onLanguage(language) }
                )
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            DrawerSection(Lang.t("secAppearance"))
            for (mode in listOf("System", "Light", "Dark")) {
                NavigationDrawerItem(
                    label = { Text((if (mode == darkMode) "● " else "○ ") + mode, fontSize = scaled(14.sp, fontScale)) },
                    selected = mode == darkMode,
                    icon = { Text(if (mode == "Light") "☀" else if (mode == "Dark") "☾" else "◐") },
                    onClick = { onAppearance(mode) }
                )
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            DrawerSection(Lang.t("secTheme"))
            for (theme in THEMES) {
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text((if (theme.name == themeName) "● " else "○ ") + theme.name, fontSize = scaled(14.sp, fontScale))
                            Text(theme.tagline, fontSize = scaled(11.sp, fontScale), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    selected = theme.name == themeName,
                    icon = { ThemeDot(theme.name) },
                    onClick = { onTheme(theme.name) }
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}