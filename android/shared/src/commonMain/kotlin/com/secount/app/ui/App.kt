package com.secount.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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

/** Pokes the Android home widget (no-op on desktop). */
private object SecountWidgetPush {
    fun refresh(store: EventStore) {
        try {
            com.secount.app.logic.widgetRefresh(store.exportJson())
        } catch (ignored: Exception) {
        }
    }
}



internal val REPEATS = listOf("One-time", "Yearly", "Monthly", "Weekly")
internal val SOUNDS = listOf("Chime", "Soft", "Silent")
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

    AppStartupChecks(
        onCrashReport = { crashReport = it },
        onUpdateAvailable = { updateInfo = it }
    )

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
                    AppDrawerContent(
                        pair = pair,
                        syncing = syncing,
                        themeName = themeName,
                        darkMode = darkMode,
                        muted = muted,
                        textSizePref = textSizePref,
                        langPref = langPref,
                        fontScale = fontScale,
                        onNewCountdown = {
                            val item = EventItem()
                            item.date = LocalDate.now().plusDays(7)
                            item.senderId = myId
                            item.forPartner = pair.isPaired()
                            editing = item
                            editIsNew = true
                            closeDrawer()
                        },
                        onSync = { doSync(); closeDrawer() },
                        onConnect = { showConnect = true; closeDrawer() },
                        onPin = { showPin = true; closeDrawer() },
                        onMute = {
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
                        },
                        onExport = { showExport = true; closeDrawer() },
                        onImport = { showImport = true; closeDrawer() },
                        onTextSize = {
                            textSizePref = it
                            try {
                                prefsPut(TEXT_SIZE_KEY, it)
                            } catch (ignored: Exception) {
                            }
                        },
                        onLanguage = {
                            langPref = it
                            try {
                                prefsPut(LANG_KEY, it)
                            } catch (ignored: Exception) {
                            }
                        },
                        onAppearance = {
                            darkMode = it
                            try {
                                prefsPut("secount_darkmode", it)
                            } catch (ignored: Exception) {
                            }
                        },
                        onTheme = {
                            themeName = it
                            try {
                                prefsPut("secount_theme", it)
                            } catch (ignored: Exception) {
                            }
                        }
                    )
                }
            }
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                Modifier.fillMaxSize()
            ) {
                HomeHeader(
                    now = now,
                    mainTab = mainTab,
                    shown = shown,
                    pair = pair,
                    syncing = syncing,
                    savedCount = store.items().size,
                    todayCount = todayN,
                    weekCount = weekN,
                    inboxCount = inboxList.size,
                    myId = myId,
                    fontScale = fontScale,
                    onOpenMenu = {
                        scope.launch { try {
                            drawerState.open()
                        } catch (ignored: Exception) {
                        } }
                    },
                    onOpenNext = { next ->
                        if (next.isForMe(myId)) secretOf = next
                        else { editing = next.copyFromJson(); editIsNew = false }
                    }
                )
                CountdownBrowserControls(
                    mainTab = mainTab,
                    viewMode = viewMode,
                    query = query,
                    filter = filter,
                    sort = sort,
                    mineCount = store.items().count { !it.isForMe(myId) },
                    inboxCount = inboxList.size,
                    shownCount = shown.size,
                    fontScale = fontScale,
                    onMainTab = { mainTab = it },
                    onViewMode = {
                        viewMode = it
                        if (it == "List") calDay = null
                    },
                    onQuery = { query = it },
                    onFilter = { filter = it },
                    onSort = { sort = it }
                )
                CountdownContent(
                    modifier = Modifier.weight(1f),
                    shown = shown,
                    mainTab = mainTab,
                    viewMode = viewMode,
                    query = query,
                    filter = filter,
                    now = now,
                    today = today,
                    store = store,
                    myId = myId,
                    month = calMonth,
                    selectedDay = calDay,
                    undoItem = undoItem,
                    fontScale = fontScale,
                    onMonth = { calMonth = it },
                    onDay = { calDay = it; refresh() },
                    onUndo = { deleted ->
                        store.addOrUpdate(deleted)
                        undoItem = null
                        refresh()
                    },
                    onDismissUndo = { undoItem = null },
                    onCreate = {
                        val item = EventItem()
                        item.date = LocalDate.now().plusDays(7)
                        item.senderId = myId
                        item.forPartner = pair.isPaired()
                        editing = item
                        editIsNew = true
                    },
                    onOpenSecret = { secretOf = it },
                    onEdit = { editing = it.copyFromJson(); editIsNew = false },
                    onDuplicate = { event ->
                        val copy = event.copyFromJson()
                        copy.id = UUID.randomUUID().toString().replace("-", "")
                        copy.title = event.title + " (copy)"
                        copy.createdAt = LocalDateTime.now()
                        copy.senderId = myId
                        store.addOrUpdate(copy)
                        if (copy.forPartner) scope.launch { engine.sendCountdown(copy) }
                        refresh()
                    },
                    onRing = { event ->
                        if (!isMuted() && event.soundEnabled && event.soundName != "Silent") {
                            try {
                                alarmBeep()
                            } catch (ignored: Exception) {
                            }
                        }
                        val hasThread = event.threadEntries().isNotEmpty()
                        if (event.hasSecret() || hasThread || event.forPartner) secretOf = event else alarmOf = event
                    },
                    onMessages = { secretOf = it },
                    onDelete = { confirmDelete = it }
                )
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
        if (showExport) ExportDialog(store, storeVer, onClose = { showExport = false })
        if (showImport) ImportDialog(store, onRefresh = { refresh() }, onClose = { showImport = false; refresh() })
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
