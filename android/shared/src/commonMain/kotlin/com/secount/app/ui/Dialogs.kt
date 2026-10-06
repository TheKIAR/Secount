package com.secount.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secount.app.logic.PairStore
import com.secount.app.logic.PinLock
import com.secount.app.logic.SyncEngine
import com.secount.app.logic.biometricAuthenticate
import com.secount.app.logic.biometricAvailable
import com.secount.app.logic.copyToClipboard
import com.secount.app.logic.getClipboardText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** PIN lock screens (extracted from App.kt, no behavior change). */

@Composable
internal fun PinGate(pin: PinLock, themeName: String, darkMode: String, onUnlock: () -> Unit) {
    var entry by remember { mutableStateOf("") }
    var denied by remember { mutableStateOf(false) }
    var tickLock by remember { mutableStateOf(0) }
    fun tryUnlock() {
        if (!pin.canAttempt()) {
            denied = true
            return
        }
        if (pin.unlock(entry)) {
            entry = ""
            denied = false
            onUnlock()
        } else denied = true
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tickLock++
        }
    }
    @Suppress("UNUSED_EXPRESSION")
    tickLock
    val lockedSecs = pin.lockoutRemainingSec()
    SecountTheme(themeName, darkMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("♥", fontSize = 48.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("Secount", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (pin.isDefaultPin()) Lang.t("firstPin")
                    else Lang.t("enterPin"),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    entry, { entry = it.filter { c -> c.isDigit() }.take(8); denied = false },
                    placeholder = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { tryUnlock() }),
                    enabled = lockedSecs <= 0
                )
                if (lockedSecs > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("${Lang.t("waitLock")} ${lockedSecs}s.", color = MaterialTheme.colorScheme.error)
                } else {
                    if (denied) {
                        val left = pin.attemptsLeft()
                        Text(
                            if (left > 0) "${Lang.t("wrongPin")} $left"
                            else Lang.t("wrongPin"),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { tryUnlock() }, enabled = lockedSecs <= 0) { Text(Lang.t("unlock")) }
                if (lockedSecs <= 0 && biometricAvailable()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        try {
                            biometricAuthenticate { ok ->
                                if (ok) {
                                    try {
                                        pin.unlockViaBiometric()
                                        onUnlock()
                                    } catch (ignored: Exception) {
                                    }
                                }
                            }
                        } catch (ignored: Exception) {
                        }
                    }) { Text("Use fingerprint / face") }
                }
            }
        }
    }
}

@Composable
internal fun PinDialog(pin: PinLock, onClose: () -> Unit) {
    var cur by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    var hasPin by remember { mutableStateOf(pin.hasPin()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(Lang.t("pinTitle")) },
        text = {
            Column {
                Text(
                    if (hasPin) Lang.t("pinSet")
                    else Lang.t("pinNone"),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(6.dp))
                if (hasPin) {
                    OutlinedTextField(cur, { cur = it.filter { c -> c.isDigit() }.take(8) }, label = { Text(Lang.t("curPin")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedTextField(next, { next = it.filter { c -> c.isDigit() }.take(8) }, label = { Text(Lang.t("newPin")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(confirm, { confirm = it.filter { c -> c.isDigit() }.take(8) }, label = { Text(Lang.t("confirmPin")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                if (done != null) Text(done!!, color = Success, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (next.length < 4) {
                    err = Lang.t("newPin")
                    return@TextButton
                }
                if (next != confirm) {
                    err = Lang.t("confirmPin")
                    return@TextButton
                }
                val ok = if (hasPin) pin.changePin(cur, next) else pin.setPin(next)
                if (ok) {
                    err = null
                    done = Lang.t("pinSaved")
                    cur = ""
                    next = ""
                    confirm = ""
                    hasPin = pin.hasPin()
                } else err = Lang.t("wrongPin")
            }) { Text(if (hasPin) Lang.t("change") else Lang.t("setPin")) }
        },
        dismissButton = {
            Row {
                if (hasPin) {
                    TextButton(onClick = {
                        if (pin.removePin()) {
                            hasPin = false
                            err = null
                            done = Lang.t("pinRemoved")
                            cur = ""
                            next = ""
                            confirm = ""
                        }
                    }) { Text(Lang.t("removePin")) }
                    TextButton(onClick = { pin.lock(); onClose() }) { Text(Lang.t("lockNow")) }
                }
                TextButton(onClick = onClose) { Text(Lang.t("close")) }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectDialog(
    pair: PairStore,
    engine: SyncEngine,
    onClose: () -> Unit,
    onNotice: (String) -> Unit,
    onSeverPrompt: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var incoming by remember { mutableStateOf(pair.incoming()) }
    var pending by remember { mutableStateOf(pair.pendingCode()) }
    var paired by remember { mutableStateOf(pair.isPaired()) }
    var waiting by remember { mutableStateOf(pair.wantSever()) }

    fun reload() {
        incoming = pair.incoming()
        pending = pair.pendingCode()
        paired = pair.isPaired()
        waiting = pair.wantSever()
    }

    // Auto-sync every 5s while the dialog is open so the second device
    // pairs without having to press SYNC. Stops once linked (background
    // 25s loop takes over), but keeps watching for incoming requests.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            try {
                val res = engine.syncNow()
                reload()
                if (res.justPaired) onNotice("Connected! You can now send countdowns to each other.")
                if (res.severAsked) onSeverPrompt()
            } catch (ignored: Exception) {
            }
        }
    }

    var showQr by remember { mutableStateOf(false) }
    var copiedTick by remember { mutableStateOf(0) }
    val myPairText = remember(pair.myCode, pair.accountId) { pairingText(pair.myCode, pair.accountId) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(Lang.t("connTitle")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(Lang.t("yourCode"), fontWeight = FontWeight.Bold)
                Text(pair.myCode, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text(
                    Lang.t("connHint"),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        try {
                            copyToClipboard(myPairText)
                        } catch (ignored: Exception) {
                        }
                        copiedTick++
                    }) { Text(Lang.t("copy")) }
                    OutlinedButton(onClick = { showQr = !showQr }) {
                        Text(if (showQr) Lang.t("hideQr") else Lang.t("showQr"))
                    }
                }
                if (copiedTick > 0) Text(Lang.t("copied"), fontSize = 12.sp, color = Success)
                if (showQr) {
                    Spacer(Modifier.height(6.dp))
                    QrCode(myPairText)
                    Spacer(Modifier.height(4.dp))
                    Text(myPairText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(2.dp))
                    Text(Lang.t("scanHint"), fontSize = 12.sp)
                }
                if (paired) {
                    Spacer(Modifier.height(8.dp))
                    Text("✉ Connected to ${pair.partnerCode()}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (waiting) {
                        Text("Waiting for your partner to agree to disconnect…", fontSize = 12.sp)
                    } else {
                        OutlinedButton(onClick = {
                            busy = true
                            scope.launch {
                                val ok = engine.requestSever()
                                busy = false
                                reload()
                                onNotice(if (ok) "Disconnect requested. It ends only if your partner also agrees." else "Offline — request will be retried on next sync.")
                            }
                        }) { Text("DISCONNECT") }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        code, {
                            // Accept pasted SECOUNT1:... payloads or plain codes.
                            val parsed = parsePairCode(it)
                            code = if (parsed != null && it.contains(":")) parsed
                            else it.uppercase().filter { c -> c.isLetterOrDigit() || c == ':' }.take(32)
                            err = null
                        },
                        label = { Text(Lang.t("pairText")) },
                        placeholder = { Text(Lang.t("partnerCode")) },
                        singleLine = true
                    )
                    if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val parsed = parsePairCode(code)
                            if (parsed == null) {
                                err = Lang.t("partnerCode")
                                return@Button
                            }
                            if (parsed == pair.myCode) {
                                err = Lang.t("partnerCode")
                                return@Button
                            }
                            code = parsed
                            busy = true
                            scope.launch {
                                val ok = engine.sendPairRequest(parsed)
                                busy = false
                                reload()
                                onNotice(
                                    if (ok) "Request sent to $parsed. Ask them to enter YOUR code (${pair.myCode}) to complete."
                                    else "Offline — couldn't send. Try Sync later."
                                )
                            }
                        }) { Text(if (busy) "…" else Lang.t("sendReq")) }
                        OutlinedButton(onClick = {
                            try {
                                val clip = getClipboardText() ?: ""
                                val parsed = parsePairCode(clip)
                                if (parsed != null) {
                                    code = parsed
                                    err = null
                                } else {
                                    code = clip.uppercase().take(32)
                                }
                            } catch (ignored: Exception) {
                            }
                        }) { Text(Lang.t("paste")) }
                    }
                    if (pending.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Waiting on $pending… auto-retrying every few seconds. Keep this open.", fontSize = 12.sp)
                    }
                    if (incoming.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Wants to connect:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        for (req in incoming) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(req.code, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    code = req.code
                                }) { Text("→") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    engine.syncNow()
                    reload()
                    onClose()
                }
            }) { Text(Lang.t("syncClose")) }
        }
    )
}
