package com.secount.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.secount.app.logic.BackupCrypto
import com.secount.app.logic.EventItem
import com.secount.app.logic.EventStore
import com.secount.app.logic.PairStore
import com.secount.app.logic.PhotoLockGuard
import com.secount.app.logic.PinLock
import com.secount.app.logic.SyncEngine
import com.secount.app.logic.alarmBeep
import com.secount.app.logic.alarmStop
import com.secount.app.logic.biometricAuthenticate
import com.secount.app.logic.biometricAvailable
import com.secount.app.logic.copyToClipboard
import com.secount.app.logic.copyFromJson
import com.secount.app.logic.deletePhotoFile
import com.secount.app.logic.getClipboardText
import com.secount.app.logic.loadPhotoBitmap
import com.secount.app.logic.notifySecret
import com.secount.app.logic.nowSec
import com.secount.app.logic.openUrl
import com.secount.app.logic.photoToB64
import com.secount.app.logic.pickPhotoFile
import com.secount.app.logic.platformDataDir
import com.secount.app.logic.prefsGet
import com.secount.app.logic.prefsPut
import com.secount.app.logic.systemLanguage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath

private const val NEED_LOCK_KEY = "secount_need_lock"
private const val MUTED_KEY = "secount_muted"
private const val LANG_KEY = "secount_lang"

private val LANGS = listOf("System", "en", "de", "fr", "es")

private fun langDisplay(code: String): String = when (code) {
    "en" -> "English"
    "de" -> "Deutsch"
    "fr" -> "Français"
    "es" -> "Español"
    else -> "System"
}

/** Pairing payload for QR / copy-paste. */
internal fun pairingText(myCode: String, accountId: String): String = "SECOUNT1:$myCode:$accountId"

/** Accepts a raw 6-letter code or a SECOUNT1:... payload; returns the code or null. */
internal fun parsePairCode(input: String): String? {
    val t = input.trim().uppercase()
    if (PairStore.looksLikeCode(t)) return t
    if (t.startsWith("SECOUNT1:")) {
        val parts = t.split(":")
        if (parts.size >= 2 && PairStore.looksLikeCode(parts[1])) return parts[1]
    }
    // Be liberal: find any 6-char token that looks like a code.
    for (tok in t.split(Regex("[^A-Z0-9]+"))) {
        if (PairStore.looksLikeCode(tok)) return tok
    }
    return null
}

/** Pokes the Android home widget (no-op on desktop). */
private object SecountWidgetPush {
    fun refresh(store: EventStore) {
        try {
            com.secount.app.logic.widgetRefresh(store.exportJson())
        } catch (ignored: Exception) {
        }
    }
}



private val FILTERS = listOf("All", "Today", "Next 7 days", "Featured", "With secret", "Past", "To partner")
private val SORTS = listOf("Happening next", "Name A–Z", "Biggest countdown", "Newest first")
internal val REPEATS = listOf("One-time", "Yearly", "Monthly", "Weekly")
internal val SOUNDS = listOf("Chime", "Soft", "Silent")
private val TEXT_SIZES = listOf("Standard", "Large", "Extra large")
private const val TEXT_SIZE_KEY = "secount_textsize"

private fun textScale(pref: String): Float = when (pref) {
    "Large" -> 1.15f
    "Extra large" -> 1.3f
    else -> 1f
}

private fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Good night"
}

private fun filterEmoji(id: String): String = when (id) {
    "Today" -> "● "
    "Next 7 days" -> "◐ "
    "Featured" -> "★ "
    "With secret" -> "🎁 "
    "Past" -> "✓ "
    "To partner" -> "✉ "
    else -> ""
}
internal val ACCENTS = listOf(
    "Auto" to "",
    "Pink" to "#FF5D97",
    "Violet" to "#7C6CFF",
    "Teal" to "#22C4A8",
    "Amber" to "#FFB020",
    "Sky" to "#38BDF8",
    "Rose" to "#F472B6",
    "Green" to "#4ADE80"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val store = remember { EventStore(platformDataDir()).also { it.load() } }
    val pin = remember { PinLock() }
    val pair = remember { PairStore() }
    val engine = remember { SyncEngine(store, pair) }
    val scope = rememberCoroutineScope()

    var secTick by remember { mutableStateOf(0) }
    var storeVer by remember { mutableStateOf(0) }
    var unlocked by remember { mutableStateOf(pin.isUnlocked()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FILTERS[0]) }
    var sort by remember { mutableStateOf(SORTS[0]) }
    var viewMode by remember { mutableStateOf("List") }
    var calMonth by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var calDay by remember { mutableStateOf<LocalDate?>(null) }
    var editing by remember { mutableStateOf<EventItem?>(null) }
    var editIsNew by remember { mutableStateOf(false) }
    var showConnect by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var secretOf by remember { mutableStateOf<EventItem?>(null) }
    var alarmOf by remember { mutableStateOf<EventItem?>(null) }
    var confirmDelete by remember { mutableStateOf<EventItem?>(null) }
    var undoItem by remember { mutableStateOf<EventItem?>(null) }
    var severPrompt by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var themeName by remember { mutableStateOf(prefsGet("secount_theme") ?: THEMES[0].name) }
    var darkMode by remember { mutableStateOf(prefsGet("secount_darkmode") ?: "System") }
    var textSizePref by remember { mutableStateOf(prefsGet(TEXT_SIZE_KEY) ?: TEXT_SIZES[0]) }
    val fontScale = textScale(textSizePref)
    var muted by remember { mutableStateOf(prefsGet(MUTED_KEY) == "1") }
    var langPref by remember { mutableStateOf(prefsGet(LANG_KEY) ?: "System") }
    var crashReport by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Mine = countdowns I created. Inbox = partner surprises (D-day only, vanish after).
    var mainTab by remember { mutableStateOf("Mine") }
    val shownSecrets = remember { mutableSetOf<String>() }

    val effLang = if (langPref == "System") systemLanguage() else langPref
    Lang.code = effLang

    fun refresh() {
        storeVer++
        try {
            if (platformDataDir().isNotEmpty()) SecountWidgetPush.refresh(store)
        } catch (ignored: Exception) {
        }
    }

    fun isMuted(): Boolean = muted

    // Crash report + update check, once per launch.
    LaunchedEffect(Unit) {
        try {
            val f = okio.FileSystem.SYSTEM
            val p = (platformDataDir() + "/crash_log.txt").toPath()
            if (f.exists(p)) {
                val txt = f.read(p) { readUtf8() }
                if (txt.isNotBlank()) crashReport = txt.take(4000)
            }
        } catch (ignored: Exception) {
        }
        try {
            val last = prefsGet(UPDATE_CHECK_KEY)?.toLongOrNull() ?: 0L
            if (nowSec() - last > 86400) {
                prefsPut(UPDATE_CHECK_KEY, nowSec().toString())
                val json = httpGetSafe("https://api.github.com/TheKIAR/Secount/releases/latest")
                if (json != null) {
                    val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
                    val url = Regex("\"html_url\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
                    if (tag != null && url != null && isNewerVersion(APP_VERSION, tag)) {
                        updateInfo = tag to url
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    fun doSync() {
        if (syncing) return
        syncing = true
        scope.launch {
            try {
                val res = engine.syncNow()
                if (res.justPaired) notice = "Connected! You can now send countdowns to each other."
                if (res.severAsked) severPrompt = true
                if (res.severDeclined) notice = "Your partner declined to disconnect. Still connected."
                if (res.severed) notice = "Connection severed by mutual agreement."
                if (res.replyReceived) {
                    val target = res.replyId?.let { store.byId(it) }
                    val title = target?.title?.takeIf { it.isNotBlank() } ?: "a secret message"
                    // Auto-open the conversation so the reply is visible right away.
                    // Only when the item is visible to this device (sender always;
                    // receiver only at zero) — otherwise keep it for D-day.
                    if (target != null) {
                        val t = LocalDate.now()
                        // isForMe items hidden until due; sender items always visible.
                        val syncMyId = try { pair.accountId } catch (e: Exception) { "" }
                        val canShow = if (target.isForMe(syncMyId)) target.isDueToday(t) else true
                        if (canShow) {
                            secretOf = target
                        }
                    }
                    notice = "💬 Partner replied to '$title'. Open it to read & reply."
                    if (!isMuted()) {
                        try {
                            notifySecret(
                                "Secount reply 💬",
                                "Partner replied to '$title'. Tap to open Secount and read it."
                            )
                        } catch (ignored: Exception) {
                        }
                    }
                }
                if (res.deleteId != null) {
                    // Partner deleted a shared countdown — close it if open.
                    try {
                        if (secretOf?.id == res.deleteId) secretOf = null
                        if (alarmOf?.id == res.deleteId) alarmOf = null
                        if (editing?.id == res.deleteId) editing = null
                    } catch (ignored: Exception) {
                    }
                }
                if (res.seenId != null) {
                    // Partner saw my message — receipts display updates on refresh.
                }
                if (res.offline) notice = "Offline — will retry automatically."
            } finally {
                syncing = false
                refresh()
            }
        }
    }

    // 1s ticker drives only the live timer text; list ordering uses storeVer
    // so the whole list is not resorted/recomposed every second.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            secTick++
            // Lock only when the OS sent us Home/background (native sets the
            // flag). No timer auto-lock while you stay in the app.
            // While the photo picker is open the OS also backgrounds us —
            // that must NOT lock, or unsaved editor text is lost.
            try {
                if (prefsGet(NEED_LOCK_KEY) == "1") {
                    if (PhotoLockGuard.picking) {
                        // Consume the flag, stay unlocked during picking.
                        prefsPut(NEED_LOCK_KEY, "")
                    } else {
                        prefsPut(NEED_LOCK_KEY, "")
                        // Only locks when a PIN is set; otherwise stays open.
                        pin.lockOnHome()
                        unlocked = pin.isUnlocked()
                    }
                }
            } catch (ignored: Exception) {
            }
            // Instant sync on resume (set by MainActivity.onResume).
            try {
                if (prefsGet("secount_need_sync") == "1") {
                    prefsPut("secount_need_sync", "")
                    doSync()
                }
            } catch (ignored: Exception) {
            }
        }
    }
    // online sync every 15s when linked (reply push reliability),
    // every 8s while waiting to pair
    LaunchedEffect(Unit) {
        doSync()
        while (true) {
            delay(if (pair.isPaired()) 15_000 else 8_000)
            doSync()
        }
    }

    // No early return here on purpose: the main UI stays composed under the
    // PIN overlay so unsaved editor text / photo state survives a lock.
    // When locked, PinGate is drawn full-screen on top at the end of this
    // composable (see bottom of SecountTheme block).
    val today = LocalDate.now()
    @Suppress("UNUSED_EXPRESSION")
    secTick
    val now = LocalDateTime.now()
    val myId = pair.accountId
    @Suppress("UNUSED_EXPRESSION")
    storeVer
    // Inbox count: partner surprises due today (vanish after the day).
    val inboxList = remember(storeVer) {
        val t = LocalDate.now()
        store.sortedByNext(t).filter { e -> e.isForMe(myId) && e.isDueToday(t) }
    }
    val shown = remember(storeVer, query, filter, sort, mainTab) {
        val t = LocalDate.now()
        var list = store.sortedByNext(t).filter { e ->
            if (mainTab == "Inbox") {
                // Separate inbox: only partner messages due today.
                // Main screen never shows them; after the day they vanish.
                if (!e.isForMe(myId) || !e.isDueToday(t)) return@filter false
            } else {
                // Mine tab: only my countdowns (sent + personal).
                // Partner-created items never appear here.
                if (e.isForMe(myId)) return@filter false
                if (e.isForMe(myId) && !e.isDueToday(t)) return@filter false
            }
            if (calDay != null && viewMode == "Calendar") {
                val match = if (e.effectiveRepeat() == "once") e.date == calDay
                else e.nextOccurrence(t) == calDay || e.isDueToday(calDay!!)
                if (!match) return@filter false
            }
            (query.isBlank() || (e.title + " " + e.message + " " + e.displayCategory())
                .contains(query.trim(), ignoreCase = true)) &&
                when (filter) {
                    "Today" -> e.isDueToday(t)
                    "Next 7 days" -> !e.isPast(t) && e.daysUntil(t) <= 7
                    "Featured" -> e.featured
                    "With secret" -> e.hasSecret() || e.threadEntries().isNotEmpty()
                    "Past" -> e.isPast(t)
                    "To partner" -> e.forPartner && e.isMine(myId)
                    else -> true
                }
        }
        list = when (sort) {
            "Name A–Z" -> list.sortedBy { it.title.lowercase() }
            "Biggest countdown" -> list.sortedByDescending { it.daysUntil(t) }
            "Newest first" -> list.sortedByDescending { it.createdAt }
            else -> list
        }
        list
    }
    var todayN = 0
    var weekN = 0
    for (e in store.items()) {
        if (e.isForMe(myId) && !e.isDueToday(today)) continue
        if (e.isDueToday(today)) todayN++
        else if (!e.isPast(today) && e.daysUntil(today) <= 7) weekN++
    }

    // due-today reveals: personal items and partner-sent items open here;
    // items I sent are revealed on the partner's device instead.
    // Partner items also fire the "You Have a Secret Message" notification.
    // Plus 1-day / 7-day pre-reminders (once per day per event).
    // Never auto-open secrets while the PIN overlay is up.
    LaunchedEffect(secTick, unlocked) {
        if (!unlocked) return@LaunchedEffect
        if (secTick % 5 != 0) return@LaunchedEffect
        val t = LocalDate.now()
        for (e in store.items()) {
            if (e.isForMe(myId) && !e.isDueToday(t)) continue
            val mine = e.isMine(myId)
            val forMe = e.isForMe(myId)
            if (e.isDueToday(t) && !shownSecrets.contains(e.id)) {
                if (!mine && !forMe) continue
                if (mine && e.forPartner) continue
                shownSecrets.add(e.id)
                if (!isMuted() && e.soundEnabled && e.soundName != "Silent") {
                    try {
                        alarmBeep()
                    } catch (ignored: Exception) {
                    }
                }
                if (forMe && !isMuted()) {
                    try {
                        notifySecret(
                            "You Have a Secret Message Open it",
                            "Open Secount to read your new secret message."
                        )
                    } catch (ignored: Exception) {
                    }
                }
                if (e.hasSecret() || forMe) secretOf = e else alarmOf = e
                if (forMe) {
                    scope.launch { engine.sendDelivered(e.id) }
                }
            } else if (!e.isPast(t) && !isMuted()) {
                val d = e.daysUntil(t)
                val keyDay = t.toString()
                if (d == 1L && e.remind1d) {
                    val k = "reminded_${e.id}_1_$keyDay"
                    if (prefsGet(k) == null) {
                        prefsPut(k, "1")
                        try {
                            notifySecret("Tomorrow: ${e.title}", "${e.shortCountdown(t)} • ${e.timeLabel()}")
                        } catch (ignored: Exception) {
                        }
                    }
                }
                if (d == 7L && e.remind7d) {
                    val k = "reminded_${e.id}_7_$keyDay"
                    if (prefsGet(k) == null) {
                        prefsPut(k, "1")
                        try {
                            notifySecret("In a week: ${e.title}", "${e.shortCountdown(t)} • ${e.dateLabel()}")
                        } catch (ignored: Exception) {
                        }
                    }
                }
            }
        }
    }

    SecountTheme(themeName, darkMode) {
      Box(Modifier.fillMaxSize()) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        fun closeDrawer() {
            scope.launch { try {
                drawerState.close()
            } catch (ignored: Exception) {
            } }
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(
                        Modifier.verticalScroll(rememberScrollState())
                    ) {
                        // Modern profile header with theme gradient.
                        Box(
                            Modifier.fillMaxWidth()
                                .background(heroGradient())
                                .padding(horizontal = 20.dp, vertical = 22.dp)
                        ) {
                            Column {
                                Text("♥ Secount", fontWeight = FontWeight.Bold, fontSize = scaled(24.sp, fontScale), color = Color.White)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    themeByName(themeName).tagline,
                                    fontSize = scaled(12.sp, fontScale), color = Color.White.copy(alpha = 0.9f)
                                )
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.White.copy(alpha = 0.2f)
                                ) {
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
                            onClick = {
                                val item = EventItem()
                                item.date = LocalDate.now().plusDays(7)
                                item.senderId = myId
                                item.forPartner = pair.isPaired()
                                editing = item
                                editIsNew = true
                                closeDrawer()
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        DrawerSection(Lang.t("secConnection"))
                        NavigationDrawerItem(
                            label = { Text(if (syncing) Lang.t("syncing") else Lang.t("syncNow"), fontSize = scaled(14.sp, fontScale)) },
                            selected = false,
                            icon = { Text("⟳") },
                            onClick = { doSync(); closeDrawer() }
                        )
                        NavigationDrawerItem(
                            label = { Text(if (pair.isPaired()) "✉ ${pair.partnerCode()}" else Lang.t("connectPartner"), fontSize = scaled(14.sp, fontScale)) },
                            selected = false,
                            icon = { Text("✉") },
                            onClick = { showConnect = true; closeDrawer() }
                        )
                        NavigationDrawerItem(
                            label = { Text(Lang.t("appPin"), fontSize = scaled(14.sp, fontScale)) },
                            selected = false,
                            icon = { Text("🔒") },
                            onClick = { showPin = true; closeDrawer() }
                        )
                        NavigationDrawerItem(
                            label = { Text(if (muted) Lang.t("unmute") else Lang.t("mute"), fontSize = scaled(14.sp, fontScale)) },
                            selected = false,
                            icon = { Text(if (muted) "🔇" else "🔔") },
                            onClick = {
                                muted = !muted
                                try {
                                    prefsPut(MUTED_KEY, if (muted) "1" else "")
                                } catch (ignored: Exception) {
                                }
                                if (muted) {
                                    try {
                                        alarmStop()
                                    } catch (ignored: Exception) {
                                    }
                                }
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        DrawerSection(Lang.t("secBackup"))
                        NavigationDrawerItem(
                            label = { Text(Lang.t("exportBackup"), fontSize = scaled(14.sp, fontScale)) },
                            selected = false,
                            icon = { Text("⤴") },
                            onClick = { showExport = true; closeDrawer() }
                        )
                        NavigationDrawerItem(
                            label = { Text(Lang.t("importBackup"), fontSize = scaled(14.sp, fontScale)) },
                            selected = false,
                            icon = { Text("⤵") },
                            onClick = { showImport = true; closeDrawer() }
                        )
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        DrawerSection("READABILITY")
                        for (s in TEXT_SIZES) {
                            NavigationDrawerItem(
                                label = { Text((if (s == textSizePref) "● " else "○ ") + s, fontSize = scaled(14.sp, fontScale)) },
                                selected = s == textSizePref,
                                icon = { Text(if (s == TEXT_SIZES[0]) "A" else if (s == TEXT_SIZES[1]) "A＋" else "A＋＋") },
                                onClick = {
                                    textSizePref = s
                                    try {
                                        prefsPut(TEXT_SIZE_KEY, s)
                                    } catch (ignored: Exception) {
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        DrawerSection(Lang.t("secLanguage"))
                        for (l in LANGS) {
                            NavigationDrawerItem(
                                label = { Text((if (l == langPref) "● " else "○ ") + langDisplay(l), fontSize = scaled(14.sp, fontScale)) },
                                selected = l == langPref,
                                onClick = {
                                    langPref = l
                                    try {
                                        prefsPut(LANG_KEY, l)
                                    } catch (ignored: Exception) {
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        DrawerSection(Lang.t("secAppearance"))
                        for (m in listOf("System", "Light", "Dark")) {
                            NavigationDrawerItem(
                                label = { Text((if (m == darkMode) "● " else "○ ") + m, fontSize = scaled(14.sp, fontScale)) },
                                selected = m == darkMode,
                                icon = { Text(if (m == "Light") "☀" else if (m == "Dark") "☾" else "◐") },
                                onClick = {
                                    darkMode = m
                                    try {
                                        prefsPut("secount_darkmode", m)
                                    } catch (ignored: Exception) {
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        DrawerSection(Lang.t("secTheme"))
                        for (t in THEMES) {
                            NavigationDrawerItem(
                                label = {
                                    Column {
                                        Text((if (t.name == themeName) "● " else "○ ") + t.name, fontSize = scaled(14.sp, fontScale))
                                        Text(t.tagline, fontSize = scaled(11.sp, fontScale), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                selected = t.name == themeName,
                                icon = { ThemeDot(t.name) },
                                onClick = {
                                    themeName = t.name
                                    try {
                                        prefsPut("secount_theme", t.name)
                                    } catch (ignored: Exception) {
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                Modifier.fillMaxSize()
            ) {
                // ── Smart ultra-modern hero header ──
                Box(
                    Modifier.fillMaxWidth()
                        .background(heroGradient())
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                scope.launch { try {
                                    drawerState.open()
                                } catch (ignored: Exception) {
                                } }
                            }) { Text("☰", fontSize = scaled(24.sp, fontScale), color = Color.White) }
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
                                    color = Color.White, fontSize = scaled(12.sp, fontScale), fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        // Approachable stat pills: glanceable, high contrast.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HeroStat("${store.items().size}", "saved", Modifier.weight(1f), fontScale)
                            HeroStat("$todayN", "today", Modifier.weight(1f), fontScale)
                            HeroStat("$weekN", "this week", Modifier.weight(1f), fontScale)
                            HeroStat("${inboxList.size}", "inbox", Modifier.weight(1f), fontScale)
                        }
                        if (mainTab == "Mine" && shown.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            val next = shown[0]
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (next.isForMe(myId)) secretOf = next
                                    else { editing = next.copyFromJson(); editIsNew = false }
                                }
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
                                            fontSize = scaled(10.sp, fontScale), fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(next.title, fontWeight = FontWeight.Bold, fontSize = scaled(16.sp, fontScale))
                                        Text(
                                            "${next.shortCountdown(today)} • ${next.dateLabel()}",
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
                // ── Friendly tab switcher: big 52dp targets, clear selected state ──
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmartTab(
                        selected = mainTab == "Mine",
                        title = "♥ Mine",
                        subtitle = "${store.items().count { !it.isForMe(myId) }}",
                        onClick = { mainTab = "Mine" },
                        modifier = Modifier.weight(1f),
                        fontScale = fontScale
                    )
                    SmartTab(
                        selected = mainTab == "Inbox",
                        title = if (inboxList.isNotEmpty()) "💌 Inbox (${inboxList.size})" else "💌 Inbox",
                        subtitle = if (inboxList.isNotEmpty()) "new!" else "D-day only",
                        onClick = { mainTab = "Inbox" },
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
                        onClick = { viewMode = "List"; calDay = null },
                        modifier = Modifier.weight(1f),
                        fontScale = fontScale,
                        compact = true
                    )
                    SmartTab(
                        selected = viewMode == "Calendar",
                        title = "📅 ${Lang.t("viewCalendar")}",
                        subtitle = "by date",
                        onClick = { viewMode = "Calendar" },
                        modifier = Modifier.weight(1f),
                        fontScale = fontScale,
                        compact = true
                    )
                }
                OutlinedTextField(
                    query, { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    placeholder = { Text(Lang.t("search"), fontSize = scaled(14.sp, fontScale)) },
                    leadingIcon = { Text("🔍", fontSize = scaled(18.sp, fontScale)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("✕") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp)
                )
                // Smart filter chips: one-tap, no hidden menus. Sort stays a dropdown.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (f in FILTERS) {
                        val sel = filter == f
                        FilterChip(
                            selected = sel,
                            onClick = { filter = f },
                            label = { Text(filterEmoji(f) + Lang.filterLabel(f), fontSize = scaled(13.sp, fontScale), fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) },
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
                        if (shown.isEmpty()) "No results"
                        else if (shown.size == 1) "1 countdown"
                        else "${shown.size} countdowns",
                        fontSize = scaled(12.sp, fontScale),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    MappedDropDown(
                        Lang.t("sort"), SORTS, sort, { sort = it },
                        { Lang.sortLabel(it) }, Modifier.weight(1f)
                    )
                }
                if (viewMode == "Calendar") {
                    CalendarView(
                        month = calMonth,
                        today = today,
                        store = store,
                        myId = myId,
                        selected = calDay,
                        fontScale = fontScale,
                        onMonth = { calMonth = it },
                        onDay = { calDay = it; refresh() }
                    )
                }
                undoItem?.let { u ->
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${Lang.t("deleted")} '${u.title}'", fontSize = scaled(13.sp, fontScale), modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            store.addOrUpdate(u)
                            undoItem = null
                            refresh()
                        }) { Text(Lang.t("undo")) }
                        TextButton(onClick = { undoItem = null }) { Text(Lang.t("dismiss")) }
                    }
                    }
                }
                if (shown.isEmpty()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (mainTab == "Inbox") {
                            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text("💌", fontSize = scaled(52.sp, fontScale), modifier = Modifier.padding(20.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "All quiet — for now",
                                fontSize = scaled(20.sp, fontScale), fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Partner surprises appear here on D-day, then vanish after the day ends. Yearly surprises return each year.",
                                fontSize = scaled(14.sp, fontScale),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text("♥", fontSize = scaled(52.sp, fontScale), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(20.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Start your first countdown",
                                fontSize = scaled(20.sp, fontScale), fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (query.isNotBlank() || filter != "All") Lang.t("emptyNomatch")
                                else "Three easy steps: 1) Name it  2) Pick a date  3) Add a secret or photo if you like.",
                                fontSize = scaled(14.sp, fontScale),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    val item = EventItem()
                                    item.date = LocalDate.now().plusDays(7)
                                    item.senderId = myId
                                    item.forPartner = pair.isPaired()
                                    editing = item
                                    editIsNew = true
                                },
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
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        items(shown, key = { it.id }) { e ->
                            if (e.isForMe(myId)) {
                                SecretInboxCard(
                                    e,
                                    fontScale = fontScale,
                                    onOpen = { secretOf = e }
                                )
                            } else {
                                EventCard(
                                    e, now, myId,
                                    fontScale = fontScale,
                                    onEdit = { editing = e.copyFromJson(); editIsNew = false },
                                    onDuplicate = {
                                        val copy = e.copyFromJson()
                                        copy.id = UUID.randomUUID().toString().replace("-", "")
                                        copy.title = e.title + " (copy)"
                                        copy.createdAt = LocalDateTime.now()
                                        copy.senderId = myId
                                        store.addOrUpdate(copy)
                                        if (copy.forPartner) scope.launch { engine.sendCountdown(copy) }
                                        refresh()
                                    },
                                    onRing = {
                                        if (!isMuted() && e.soundEnabled && e.soundName != "Silent") {
                                            try {
                                                alarmBeep()
                                            } catch (ignored: Exception) {
                                            }
                                        }
                                        val hasThread = e.threadEntries().isNotEmpty()
                                        if (e.hasSecret() || hasThread || e.forPartner) secretOf = e else alarmOf = e
                                    },
                                    onMessages = { secretOf = e },
                                    onDelete = { confirmDelete = e }
                                )
                            }
                        }
                    }
                }
            }
            // Approachable extended FAB: label + icon, always visible above content.
            ExtendedFloatingActionButton(
                onClick = {
                    val item = EventItem()
                    item.date = LocalDate.now().plusDays(7)
                    item.senderId = myId
                    item.forPartner = pair.isPaired()
                    editing = item
                    editIsNew = true
                },
                text = { Text(Lang.t("newBtn"), fontSize = scaled(14.sp, fontScale)) },
                icon = { Text("+", fontSize = scaled(22.sp, fontScale), fontWeight = FontWeight.Bold) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
            }
        }

        editing?.let { item ->
            // Safety net: partner-sent items are never editable, even if an
            // old code path tries to open the editor for them.
            if (item.isForMe(myId)) {
                LaunchedEffect(item.id) {
                    secretOf = store.byId(item.id) ?: item
                    editing = null
                }
            } else {
                EditDialog(
                    item, editIsNew, pair,
                    onSave = { saved, send ->
                        try {
                            saved.touchUpdated(nowSec())
                        } catch (ignored: Exception) {
                        }
                        store.addOrUpdate(saved)
                        if (send) scope.launch {
                            engine.sendCountdown(saved)
                            // Photo sync: queue compressed thumbnail chunks.
                            try {
                                if (saved.photoUri.isNotEmpty()) {
                                    val b64 = try { photoToB64(saved.photoUri) } catch (e: Exception) { null }
                                    if (b64 != null && b64.isNotEmpty()) {
                                        engine.queuePhoto(saved.id, b64)
                                        // Trigger immediate chunk send via normal sync path.
                                        try { engine.sendCountdown(saved) } catch (ignored: Exception) { }
                                    }
                                }
                            } catch (ignored: Exception) {
                            }
                        }
                        editing = null
                        refresh()
                    },
                    onDelete = { del ->
                        val wasShared = del.forPartner && del.isMine(myId)
                        if (del.photoUri.isNotEmpty()) {
                            try { deletePhotoFile(del.photoUri) } catch (ignored: Exception) { }
                        }
                        store.delete(del.id)
                        if (wasShared) scope.launch { try { engine.sendDelete(del.id) } catch (ignored: Exception) { } }
                        editing = null
                        refresh()
                    },
                    onCancel = { editing = null }
                )
            }
        }
        if (showConnect) {
            ConnectDialog(
                pair, engine,
                onClose = { showConnect = false; refresh() },
                onNotice = { notice = it; refresh() },
                onSeverPrompt = { severPrompt = true }
            )
        }
        if (showPin) {
            PinDialog(pin, onClose = { showPin = false; unlocked = pin.isUnlocked(); refresh() })
        }
        if (severPrompt && pair.isPaired()) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Partner wants to disconnect") },
                text = { Text("Your partner asked to sever the connection. It only ends if you also agree. Agree?") },
                confirmButton = {
                    TextButton(onClick = {
                        severPrompt = false
                        scope.launch {
                            engine.agreeSever()
                            notice = "Connection severed by mutual agreement."
                            refresh()
                        }
                    }) { Text(Lang.t("agree")) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        severPrompt = false
                        scope.launch {
                            engine.declineSever()
                            refresh()
                        }
                    }) { Text(Lang.t("keepConn")) }
                }
            )
        }
        notice?.let { msg ->
            AlertDialog(
                onDismissRequest = { notice = null },
                title = { Text("Secount") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = { notice = null }) { Text(Lang.t("ok")) }
                }
            )
        }
        crashReport?.let { report ->
            AlertDialog(
                onDismissRequest = { },
                title = { Text(Lang.t("crashTitle")) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(report.take(1200), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        try {
                            copyToClipboard(report)
                        } catch (ignored: Exception) {
                        }
                        clearCrashLog()
                        crashReport = null
                    }) { Text(Lang.t("copyReport")) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        clearCrashLog()
                        crashReport = null
                    }) { Text(Lang.t("discard")) }
                }
            )
        }
        updateInfo?.let { (tag, url) ->
            AlertDialog(
                onDismissRequest = { updateInfo = null },
                title = { Text(Lang.t("updateTitle") + " ($tag)") },
                text = { Text(RELEASES_URL, fontSize = 12.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        try {
                            openUrl(url)
                        } catch (ignored: Exception) {
                        }
                        updateInfo = null
                    }) { Text(Lang.t("download")) }
                },
                dismissButton = {
                    TextButton(onClick = { updateInfo = null }) { Text(Lang.t("updateLater")) }
                }
            )
        }
        if (showExport) {
            val json = remember(showExport, storeVer) { store.exportJson() }
            var encPass by remember { mutableStateOf("") }
            val outText = remember(json, encPass) {
                if (encPass.isNotEmpty()) {
                    try { BackupCrypto.encrypt(encPass, json) } catch (e: Exception) { json }
                } else json
            }
            AlertDialog(
                onDismissRequest = { showExport = false },
                title = { Text(Lang.t("exportBackup")) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        OutlinedTextField(
                            encPass, { encPass = it },
                            label = { Text("Password (optional — encrypts backup)") },
                            singleLine = true
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (encPass.isNotEmpty()) "🔒 Encrypted — only readable with this password."
                            else "No password — plain JSON (secrets readable).",
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(outText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        try {
                            copyToClipboard(outText)
                        } catch (ignored: Exception) {
                        }
                        showExport = false
                    }) { Text(Lang.t("copy")) }
                },
                dismissButton = {
                    TextButton(onClick = { showExport = false }) { Text(Lang.t("close")) }
                }
            )
        }
        if (showImport) {
            var pasted by remember { mutableStateOf("") }
            var decPass by remember { mutableStateOf("") }
            var imported by remember { mutableStateOf<Int?>(null) }
            var importErr by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { showImport = false; refresh() },
                title = { Text("Import backup") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("Paste a backup JSON array (or 🔒 encrypted backup + password).", fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(pasted, { pasted = it; imported = null; importErr = null }, label = { Text("Backup JSON / encrypted") })
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(decPass, { decPass = it }, label = { Text("Password (if encrypted)") }, singleLine = true)
                        if (imported != null) Text("Imported $imported countdown(s).", fontSize = 12.sp)
                        if (importErr != null) Text(importErr!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        try {
                            var txt = pasted.trim()
                            if (txt.startsWith("ENC1.")) {
                                if (decPass.isEmpty()) {
                                    importErr = "Encrypted backup needs its password."
                                    return@TextButton
                                }
                                val dec = BackupCrypto.decrypt(decPass, txt)
                                if (dec == null) {
                                    importErr = "Wrong password or corrupt backup."
                                    return@TextButton
                                }
                                txt = dec
                            }
                            imported = store.importJson(txt)
                            importErr = null
                        } catch (e: Exception) {
                            importErr = "Import failed."
                        }
                        refresh()
                    }) { Text(Lang.t("importBackup")) }
                },
                dismissButton = {
                    TextButton(onClick = { showImport = false; refresh() }) { Text(Lang.t("close")) }
                }
            )
        }
        secretOf?.let { item ->
            val live = store.byId(item.id) ?: item
            val initialThread = (store.byId(item.id)?.threadEntries() ?: item.threadEntries())
            // If a conversation already exists, show it immediately — no extra OPEN tap
            // needed to discover the partner's reply.
            var opened by remember(item.id) { mutableStateOf(initialThread.isNotEmpty()) }
            var reply by remember(item.id) { mutableStateOf("") }
            var sending by remember(item.id) { mutableStateOf(false) }
            val forMe = live.isForMe(myId)
            // Seen receipt: when the receiver opens, tell the sender.
            LaunchedEffect(opened, live.id) {
                if (opened && forMe) {
                    try {
                        scope.launch { try { engine.sendSeen(live.id) } catch (ignored: Exception) { } }
                    } catch (ignored: Exception) {
                    }
                }
            }
            AlertDialog(
                onDismissRequest = { secretOf = null },
                title = {
                    Text(
                        if (forMe) "🎁 ${live.title.ifEmpty { "You have a secret message" }}"
                        else "💌 ${live.title.ifEmpty { "You have a message" }}"
                    )
                },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("${live.displayCategory()} • ${live.dateLabel()}", fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        if (!opened) {
                            Text(
                                if (forMe) "Your partner sent you a surprise. It arrived at zero — open it when you're ready."
                                else "The countdown reached zero. Open your message when you're ready."
                            )
                            Spacer(Modifier.height(8.dp))
                            val pendingThread = (store.byId(live.id)?.threadEntries() ?: live.threadEntries())
                            if (pendingThread.isNotEmpty()) {
                                Text("💬 ${pendingThread.size} repl${if (pendingThread.size == 1) "y" else "ies"} — open to read.", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.height(4.dp))
                            }
                        } else {
                            // Normal message section (always shown, labelled).
                            if (live.message.isNotBlank()) {
                                Text("✉ Message:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(live.message)
                                Spacer(Modifier.height(8.dp))
                            }
                            // Secret section — revealed word by word like a letter
                            // being written. Shown even if secretEnabled flag is off,
                            // as long as text exists (prevents "only normal visible" bug).
                            if (live.secretMessage.isNotBlank()) {
                                Text("🎁 Secret message:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                LetterReveal(
                                    text = live.secretMessage,
                                    key = live.id + live.secretMessage + opened.toString()
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            if (live.message.isBlank() && live.secretMessage.isBlank()) {
                                Text("The day has arrived! ♥")
                                Spacer(Modifier.height(8.dp))
                            }
                            if (live.photoUri.isNotEmpty()) {
                                val bmp = remember(live.photoUri) {
                                    try {
                                        loadPhotoBitmap(live.photoUri)
                                    } catch (ignored: Exception) {
                                        null
                                    }
                                }
                                if (bmp != null) {
                                    Image(
                                        bmp, contentDescription = Lang.t("photo"),
                                        modifier = Modifier.fillMaxWidth().height(160.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                            val thread = (store.byId(live.id)?.threadEntries() ?: live.threadEntries())
                            if (thread.isNotEmpty()) {
                                Text("💬 Conversation (${thread.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                for ((ts, who, text) in thread) {
                                    val whenS = if (ts > 0) {
                                        try {
                                            java.time.Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().take(16).replace("T", " ")
                                        } catch (e: Exception) {
                                            ""
                                        }
                                    } else ""
                                    // Normalize: own messages -> You, partner's -> Partner.
                                    val whoLabel = when (who) {
                                        "me", "sender" -> "You"
                                        "partner" -> "Partner"
                                        "" -> if (forMe) "Partner" else "You"
                                        else -> who
                                    }
                                    Text(
                                        "$whoLabel: $text" + (if (whenS.isNotEmpty()) "  ($whenS)" else ""),
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            if (pair.isPaired() && (forMe || live.forPartner)) {
                                OutlinedTextField(
                                    reply, { reply = it },
                                    label = { Text(if (thread.isEmpty()) "Write a reply…" else "Reply…") }
                                )
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        val text = reply.trim()
                                        if (text.isEmpty() || sending) return@Button
                                        sending = true
                                        scope.launch {
                                            try {
                                                val cur = store.byId(live.id)
                                                if (cur != null) {
                                                    cur.appendReply(if (forMe) "me" else "sender", text, nowSec())
                                                    store.addOrUpdate(cur)
                                                }
                                                engine.sendReply(live.id, text)
                                                reply = ""
                                                refresh()
                                            } finally {
                                                sending = false
                                            }
                                        }
                                    },
                                    enabled = reply.trim().isNotEmpty() && !sending
                                ) { Text(if (sending) "SENDING…" else "SEND REPLY") }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { opened = true }, enabled = !opened) {
                        Text(if (opened) Lang.t("opened") else Lang.t("openMsg"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { secretOf = null }) { Text(Lang.t("close")) }
                }
            )
        }
        alarmOf?.let { item ->
            val msg = if (item.message.trim().isEmpty()) "The day has arrived!" else item.message
            AlertDialog(
                onDismissRequest = { alarmOf = null },
                title = { Text("${item.displayIcon()} It's time — ${item.title}") },
                text = { Text("${item.displayCategory()} • ${item.dateLabel()}\n\n$msg") },
                confirmButton = {
                    TextButton(onClick = { alarmStop(); alarmOf = null }) { Text(Lang.t("stop")) }
                },
                dismissButton = {
                    TextButton(onClick = { alarmStop(); alarmOf = null }) { Text(Lang.t("snooze")) }
                }
            )
        }
        confirmDelete?.let { item ->
            val wasShared = item.forPartner && item.isMine(myId)
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text(Lang.t("delete")) },
                text = { Text("Delete '${item.title}'?" + if (wasShared) "\n(Partner's copy is removed too on next sync.)" else "") },
                confirmButton = {
                    TextButton(onClick = {
                        undoItem = item.copyFromJson()
                        if (item.photoUri.isNotEmpty()) {
                            try {
                                deletePhotoFile(item.photoUri)
                            } catch (ignored: Exception) {
                            }
                        }
                        store.delete(item.id)
                        if (wasShared) scope.launch { try { engine.sendDelete(item.id) } catch (ignored: Exception) { } }
                        confirmDelete = null
                        refresh()
                    }) { Text(Lang.t("yes")) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = null }) { Text(Lang.t("no")) }
                }
            )
        }
        // PIN overlay on top: keeps editor/dialog state composed underneath,
        // so a lock never clears unsaved text or photo choice.
        // No PIN set → no lock screen at all; lock appears only after a PIN is added.
        if (!unlocked && pin.hasPin()) {
            PinGate(pin, themeName, darkMode, onUnlock = { unlocked = pin.isUnlocked(); refresh() })
        }
      }
    }
}
