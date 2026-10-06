package com.secount.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secount.app.logic.BackupCrypto
import com.secount.app.logic.EventStore
import com.secount.app.logic.copyToClipboard

/** Backup export/import dialogs (extracted from App.kt, no behavior change). */

@Composable
internal fun ExportDialog(store: EventStore, storeVer: Int, onClose: () -> Unit) {
    val json = remember(storeVer) { store.exportJson() }
    var encPass by remember { mutableStateOf("") }
    val outText = remember(json, encPass) {
        if (encPass.isNotEmpty()) {
            try { BackupCrypto.encrypt(encPass, json) } catch (e: Exception) { json }
        } else json
    }
    AlertDialog(
        onDismissRequest = onClose,
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
                onClose()
            }) { Text(Lang.t("copy")) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(Lang.t("close")) }
        }
    )
}

@Composable
internal fun ImportDialog(store: EventStore, onRefresh: () -> Unit, onClose: () -> Unit) {
    var pasted by remember { mutableStateOf("") }
    var decPass by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf<Int?>(null) }
    var importErr by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
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
                    if (txt.startsWith("ENC1.") || txt.startsWith("ENC2.")) {
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
                onRefresh()
            }) { Text(Lang.t("importBackup")) }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(Lang.t("close")) }
        }
    )
}
