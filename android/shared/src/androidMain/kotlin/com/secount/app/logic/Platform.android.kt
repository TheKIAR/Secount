package com.secount.app.logic

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.ui.graphics.asImageBitmap
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AppCtx {
    var app: Context? = null
    /** Set by MainActivity: used to launch the gallery picker. */
    var photoLauncher: ((String) -> Unit)? = null
    /** Foreground activity for framework BiometricPrompt (set in onResume). */
    var activity: android.app.Activity? = null
}

private fun photosDir(): java.io.File {
    val ctx = AppCtx.app ?: error("AppCtx not initialized")
    val d = java.io.File(ctx.filesDir, "photos")
    try {
        if (!d.exists()) d.mkdirs()
    } catch (ignored: Exception) {
    }
    return d
}

actual fun copyToClipboard(text: String) {
    try {
        val ctx = AppCtx.app ?: return
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Secount", text))
    } catch (ignored: Exception) {
    }
}

actual fun getClipboardText(): String? {
    return try {
        val ctx = AppCtx.app ?: return null
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        clip.getItemAt(0).coerceToText(ctx)?.toString()
    } catch (e: Exception) {
        null
    }
}

actual fun pickPhotoFile(onResult: (String?) -> Unit) {
    try {
        val launch = AppCtx.photoLauncher
        if (launch == null) {
            onResult(null)
            return
        }
        PhotoPick.pending = onResult
        launch("image/*")
    } catch (e: Exception) {
        onResult(null)
    }
}

object PhotoPick {
    var pending: ((String?) -> Unit)? = null

    fun onPicked(uri: android.net.Uri?): String? {
        return try {
            val ctx = AppCtx.app ?: return null
            if (uri == null) return null
            val name = "p" + System.currentTimeMillis() + ".jpg"
            val out = java.io.File(photosDir(), name)
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                out.outputStream().use { outs -> ins.copyTo(outs) }
            }
            name
        } catch (e: Exception) {
            null
        }
    }

    fun deliver(uri: android.net.Uri?) {
        val cb = pending
        pending = null
        try {
            cb?.invoke(onPicked(uri))
        } catch (ignored: Exception) {
            try {
                cb?.invoke(null)
            } catch (ignored2: Exception) {
            }
        }
    }
}

actual fun loadPhotoBitmap(name: String): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        if (name.isBlank()) return null
        if (name.contains("/") || name.contains("\\")) return null
        val f = java.io.File(photosDir(), name)
        if (!f.exists()) return null
        val opts = android.graphics.BitmapFactory.Options()
        opts.inSampleSize = 4
        val bmp = android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts) ?: return null
        bmp.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

actual fun deletePhotoFile(name: String) {
    try {
        if (name.isBlank() || name.contains("/") || name.contains("\\")) return
        java.io.File(photosDir(), name).delete()
    } catch (ignored: Exception) {
    }
}

actual fun systemLanguage(): String {
    return try {
        val ctx = AppCtx.app ?: return "en"
        (ctx.resources.configuration.locales.get(0)?.language ?: "en").lowercase()
    } catch (e: Exception) {
        "en"
    }
}

actual fun openUrl(url: String) {
    try {
        val ctx = AppCtx.app ?: return
        val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    } catch (ignored: Exception) {
    }
}

actual fun widgetRefresh(eventsJson: String) {
    try {
        val ctx = AppCtx.app ?: return
        val (title, sub) = nextUp(eventsJson)
        ctx.getSharedPreferences("secount_widget", Context.MODE_PRIVATE).edit()
            .putString("title", title).putString("sub", sub).apply()
    } catch (ignored: Exception) {
    }
}

private fun nextUp(json: String): Pair<String, String> {
    return try {
        val titles = Regex("\"title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(json).map {
            it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
        }.toList()
        val dates = Regex("\"date\"\\s*:\\s*\"([0-9]{4}-[0-9]{2}-[0-9]{2})\"").findAll(json).map {
            it.groupValues[1]
        }.toList()
        if (titles.isEmpty()) return "Secount" to "No countdowns yet"
        val today = java.time.LocalDate.now()
        var bestT = ""
        var bestD = Long.MAX_VALUE
        for (i in titles.indices) {
            val ds = dates.getOrNull(i) ?: continue
            val d = try {
                java.time.LocalDate.parse(ds)
            } catch (e: Exception) {
                continue
            }
            if (d.isBefore(today)) continue
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, d)
            if (days < bestD) {
                bestD = days
                bestT = titles[i]
            }
        }
        if (bestT.isEmpty()) "Secount" to "All done — add one!"
        else bestT to when (bestD) {
            0L -> "Today! ♥"
            1L -> "Tomorrow"
            else -> "$bestD days left"
        }
    } catch (e: Exception) {
        "Secount" to "Open the app"
    }
}

actual fun platformDataDir(): String =
    (AppCtx.app ?: error("AppCtx not initialized")).filesDir.absolutePath

actual fun prefsGet(key: String): String? =
    (AppCtx.app ?: error("AppCtx not initialized"))
        .getSharedPreferences("secount", Context.MODE_PRIVATE)
        .getString(key, null)

actual fun prefsPut(key: String, value: String) {
    (AppCtx.app ?: error("AppCtx not initialized"))
        .getSharedPreferences("secount", Context.MODE_PRIVATE)
        .edit().putString(key, value).apply()
}

actual fun prefsRemove(key: String) {
    try {
        (AppCtx.app ?: error("AppCtx not initialized"))
            .getSharedPreferences("secount", Context.MODE_PRIVATE)
            .edit().remove(key).apply()
    } catch (ignored: Exception) {
    }
}

actual fun sha256(data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(data)

actual fun nowSec(): Long = System.currentTimeMillis() / 1000

actual fun httpGet(url: String, timeoutMs: Int): String {
    val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    try {
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("Accept", "application/json")
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        // ntfy keeps the stream open; a read timeout just ends our poll.
        val out = StringBuilder()
        try {
            stream.bufferedReader(Charsets.UTF_8).use { r ->
                while (true) {
                    val line = try {
                        r.readLine()
                    } catch (e: java.net.SocketTimeoutException) {
                        break
                    }
                    if (line == null) break
                    out.append(line).append('\n')
                    if (out.length > 200_000) break
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            // partial content is fine
        }
        if (code !in 200..299) throw java.io.IOException("HTTP $code")
        return out.toString()
    } finally {
        c.disconnect()
    }
}

actual fun httpPost(url: String, body: String, timeoutMs: Int): String {
    val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    try {
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.requestMethod = "POST"
        c.doOutput = true
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val resp = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (code !in 200..299) throw java.io.IOException("HTTP $code: $resp")
        return resp
    } finally {
        c.disconnect()
    }
}

@Volatile
private var stop = false

@Volatile
private var playing = false

/** Soft music-box chime (E–G#–B–E). Replaces the old harsh alarm tone. */
actual fun alarmBeep() {
    synchronized(SoundLock) {
        if (playing) return
        playing = true
    }
    stop = false
    Thread({
        var player: android.media.MediaPlayer? = null
        try {
            // Pretty bundled chime first.
            try {
                val ctx = AppCtx.app
                if (ctx != null) {
                    val resId = ctx.resources.getIdentifier(
                        "secount_chime", "raw", ctx.packageName
                    )
                    if (resId != 0) {
                        val afd = ctx.resources.openRawResourceFd(resId)
                        if (afd != null) {
                            player = android.media.MediaPlayer().apply {
                                setDataSource(
                                    afd.fileDescriptor, afd.startOffset, afd.length
                                )
                                afd.close()
                                setAudioAttributes(
                                    android.media.AudioAttributes.Builder()
                                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                        .build()
                                )
                                prepare()
                            }
                            for (i in 0 until 2) {
                                if (stop) break
                                try {
                                    if (player?.isPlaying != true) player?.start()
                                    Thread.sleep(2400)
                                } catch (e: InterruptedException) {
                                    Thread.currentThread().interrupt()
                                    break
                                }
                            }
                            return@Thread
                        }
                    }
                }
            } catch (ignored: Exception) {
            }
            // Fallback: soft two-tone (much gentler than the old guard tone).
            var gen: ToneGenerator? = null
            try {
                gen = try {
                    ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
                } catch (e: Exception) {
                    null
                }
                for (i in 0 until 2) {
                    if (stop) break
                    try {
                        gen?.startTone(ToneGenerator.TONE_PROP_BEEP, 600)
                    } catch (ignored: Exception) {
                    }
                    try {
                        Thread.sleep(1100)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            } finally {
                try {
                    gen?.release()
                } catch (ignored: Exception) {
                }
            }
        } finally {
            try {
                player?.release()
            } catch (ignored: Exception) {
            }
            playing = false
        }
    }, "alarm-beep").apply { isDaemon = true }.start()
}

actual fun alarmStop() {
    stop = true
}

actual fun isSystemDark(): Boolean {
    return try {
        val ctx = AppCtx.app ?: return false
        val mask = ctx.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        mask == android.content.res.Configuration.UI_MODE_NIGHT_YES
    } catch (e: Exception) {
        false
    }
}

actual fun notifySecret(title: String, text: String) {
    try {
        val ctx = AppCtx.app ?: return
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "secount_secret"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            var channel = try {
                mgr.getNotificationChannel(channelId)
            } catch (e: Exception) {
                null
            }
            if (channel == null) {
                channel = android.app.NotificationChannel(
                    channelId, "Secret messages",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                )
                try {
                    val resId = ctx.resources.getIdentifier(
                        "secount_chime", "raw", ctx.packageName
                    )
                    if (resId != 0) {
                        val soundUri = android.net.Uri.parse(
                            "${android.content.ContentResolver.SCHEME_ANDROID_RESOURCE}://${ctx.packageName}/$resId"
                        )
                        val attrs = android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        channel.setSound(soundUri, attrs)
                    }
                } catch (ignored: Exception) {
                }
                try {
                    mgr.createNotificationChannel(channel)
                } catch (ignored: Exception) {
                }
            }
        }
        val intent = try {
            ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        } catch (e: Exception) {
            null
        }
        val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
            (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
                android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = try {
            if (intent != null) android.app.PendingIntent.getActivity(ctx, 0, intent, flags)
            else null
        } catch (e: Exception) {
            null
        }
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.Notification.Builder(ctx, channelId)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(ctx)
        }
        val smallIcon = try {
            val rid = ctx.resources.getIdentifier("ic_stat_secount", "drawable", ctx.packageName)
            if (rid != 0) rid else android.R.drawable.ic_dialog_info
        } catch (e: Exception) {
            android.R.drawable.ic_dialog_info
        }
        builder.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(smallIcon)
            .setAutoCancel(true)
        if (pending != null) builder.setContentIntent(pending)
        try {
            mgr.notify(1001, builder.build())
        } catch (e: SecurityException) {
            // Notification permission not granted — in-app card still shows.
        }
    } catch (ignored: Exception) {
    }
}

private const val BIOMETRIC_KEY_ALIAS = "secount_biometric_unlock"
private const val BIOMETRIC_PROOF_PREF = "secount_biometric_unlock_proof"
private val BIOMETRIC_PROOF = "SECOUNT_BIOMETRIC_UNLOCK_V1".encodeToByteArray()

private data class BiometricRequest(val cipher: Cipher, val encryptedProof: ByteArray?, val enrollProof: Boolean)

private fun createBiometricKey(): SecretKey {
    val builder = KeyGenParameterSpec.Builder(
        BIOMETRIC_KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
    if (android.os.Build.VERSION.SDK_INT >= 30) {
        builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
    } else {
        builder.setUserAuthenticationValidityDurationSeconds(-1)
    }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(builder.build())
        generateKey()
    }
}

private fun biometricRequest(): BiometricRequest {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    var key = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
    var payload = prefsGet(BIOMETRIC_PROOF_PREF)?.let {
        try { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) } catch (ignored: Exception) { null }
    }
    if (key == null) {
        key = createBiometricKey()
        payload = null
    }

    fun request(key: SecretKey, proof: ByteArray?): BiometricRequest {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (proof == null || proof.size <= 12) {
            cipher.init(Cipher.ENCRYPT_MODE, key)
            return BiometricRequest(cipher, null, true)
        }
        val nonce = proof.copyOfRange(0, 12)
        val encryptedProof = proof.copyOfRange(12, proof.size)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        return BiometricRequest(cipher, encryptedProof, false)
    }

    return try {
        request(key, payload)
    } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
        keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
        prefsRemove(BIOMETRIC_PROOF_PREF)
        key = createBiometricKey()
        request(key, null)
    }
}

internal fun encryptBiometricProof(cipher: Cipher?): ByteArray? {
    if (cipher == null) return null
    return try {
        val encrypted = cipher.doFinal(BIOMETRIC_PROOF)
        cipher.iv + encrypted
    } catch (ignored: Exception) {
        null
    }
}

internal fun authenticateBiometricProof(cipher: Cipher?, encryptedProof: ByteArray?): Boolean {
    if (cipher == null || encryptedProof == null) return false
    return try {
        MessageDigest.isEqual(BIOMETRIC_PROOF, cipher.doFinal(encryptedProof))
    } catch (ignored: Exception) {
        false
    }
}

actual fun biometricAvailable(): Boolean {
    return try {
        if (android.os.Build.VERSION.SDK_INT < 28) return false
        val ctx = AppCtx.app ?: return false
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (!km.isDeviceSecure) return false
        val bm = ctx.getSystemService("biometric") ?: return true
        try {
            val m = bm.javaClass.getMethod("canAuthenticate")
            val r = (m.invoke(bm) as? Int) ?: return true
            // BIOMETRIC_SUCCESS == 0
            r == 0
        } catch (e: Exception) {
            true
        }
    } catch (e: Exception) {
        false
    }
}

actual fun biometricAuthenticate(onResult: (Boolean) -> Unit) {
    try {
        if (android.os.Build.VERSION.SDK_INT < 28) {
            onResult(false)
            return
        }
        val act = AppCtx.activity ?: run { onResult(false); return }
        val request = biometricRequest()
        val exec = act.mainExecutor
        val prompt = android.hardware.biometrics.BiometricPrompt.Builder(act)
            .setTitle("Unlock Secount")
            .setSubtitle("Use fingerprint / face")
            .setNegativeButton("Use PIN", exec, android.content.DialogInterface.OnClickListener { _, _ ->
                try { onResult(false) } catch (ignored: Exception) { }
            })
            .build()
        val cancel = android.os.CancellationSignal()
        prompt.authenticate(
            android.hardware.biometrics.BiometricPrompt.CryptoObject(request.cipher), cancel, exec,
            object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                    try {
                        val authenticatedCipher = result?.cryptoObject?.cipher
                        if (request.enrollProof) {
                            val proof = encryptBiometricProof(authenticatedCipher)
                            if (proof == null) {
                                onResult(false)
                            } else {
                                prefsPut(BIOMETRIC_PROOF_PREF, android.util.Base64.encodeToString(proof, android.util.Base64.NO_WRAP))
                                onResult(true)
                            }
                        } else {
                            onResult(authenticateBiometricProof(authenticatedCipher, request.encryptedProof))
                        }
                    } catch (ignored: Exception) {
                        onResult(false)
                    }
                }
                override fun onAuthenticationFailed() {
                    // stay open; user can retry or use PIN
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    try { onResult(false) } catch (ignored: Exception) { }
                }
            }
        )
    } catch (e: Exception) {
        try { onResult(false) } catch (ignored: Exception) { }
    }
}

actual fun photoToB64(name: String): String? {
    return try {
        if (name.isBlank()) return null
        val ctx = AppCtx.app ?: return null
        if (name.contains("/") || name.contains("\\")) return null
        val f = java.io.File(photosDir(), name)
        if (!f.exists()) return null
        val opts = android.graphics.BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)
        var sample = 1
        while ((opts.outWidth / sample) > 600 || (opts.outHeight / sample) > 600) sample *= 2
        val o2 = android.graphics.BitmapFactory.Options()
        o2.inSampleSize = sample
        var bmp = android.graphics.BitmapFactory.decodeFile(f.absolutePath, o2) ?: return null
        // Scale precisely to max 600px.
        val w = bmp.width
        val h = bmp.height
        val scale = minOf(1f, 600f / maxOf(w, h).toFloat())
        if (scale < 1f) {
            val nw = (w * scale).toInt().coerceAtLeast(1)
            val nh = (h * scale).toInt().coerceAtLeast(1)
            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, nw, nh, true)
            if (scaled != bmp) {
                try { bmp.recycle() } catch (ignored: Exception) { }
                bmp = scaled
            }
        }
        val baos = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos)
        try { bmp.recycle() } catch (ignored: Exception) { }
        val bytes = baos.toByteArray()
        if (bytes.size > 120000) return null
        java.util.Base64.getEncoder().encodeToString(bytes)
    } catch (e: Exception) {
        null
    }
}

actual fun savePhotoB64(b64: String): String? {
    return try {
        if (b64.isBlank() || b64.length > 400000) return null
        val bytes = java.util.Base64.getDecoder().decode(b64)
        if (bytes.isEmpty() || bytes.size > 300000) return null
        // Validate it's an image.
        val opts = android.graphics.BitmapFactory.Options()
        opts.inJustDecodeBounds = true
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        val name = "p" + System.currentTimeMillis() + ".jpg"
        val out = java.io.File(photosDir(), name)
        out.outputStream().use { it.write(bytes) }
        name
    } catch (e: Exception) {
        null
    }
}

private object SoundLock
