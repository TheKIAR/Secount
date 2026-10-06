package com.secount.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secount.app.logic.PinLock
import com.secount.app.logic.biometricAuthenticate
import com.secount.app.logic.biometricAvailable
import kotlinx.coroutines.delay

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
