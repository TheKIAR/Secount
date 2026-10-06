package com.secount.app.logic

import kotlin.random.Random

/**
 * Transport encryption for pairing relay traffic and password backups.
 * (Extracted from Pairing.kt, no behavior change.)
 */

/** Lightweight shared-secret obfuscation so partner countdowns/replies are not
 * readable to anyone casually watching the public relay topic. Key is derived
 * from both pairing codes (known to both devices only after mutual entry).
 * Not a substitute for full audited E2E, but far better than plaintext. */
object PairCrypto {
    fun deriveKey(codeA: String, codeB: String): ByteArray {
        val a = codeA.trim().lowercase()
        val b = codeB.trim().lowercase()
        val (x, y) = if (a < b) a to b else b to a
        return sha256("secount-pair|$x|$y".encodeToByteArray())
    }

    /**
     * Authenticated envelope for relay traffic.
     *
     * ENC2 adds an HMAC-SHA-256 tag over the nonce and ciphertext. This
     * prevents an attacker who can write to the public relay topic from
     * silently modifying a partner message. ENC1 remains readable for
     * backward compatibility with older local data.
     */
    fun encryptToHex(key: ByteArray, plain: String): String {
        val nonce = ByteArray(16)
        var v = nowSec() xor Random.nextLong()
        for (i in nonce.indices) {
            v = v * 6364136223846793005L + 1442695040888963407L
            nonce[i] = ((v ushr 33) and 0xFF).toByte()
        }
        val src = plain.encodeToByteArray()
        val out = xorStream(key, nonce, src)
        val tag = hmacSha256(key, nonce + out)
        return "ENC2." + hex(nonce) + "." + hex(out) + "." + hex(tag)
    }

    fun decryptHex(key: ByteArray, s: String): String? {
        return try {
            if (s.startsWith("ENC2.")) {
                val p = s.removePrefix("ENC2.").split('.')
                if (p.size != 3) return null
                val nonce = unhex(p[0]) ?: return null
                val cipher = unhex(p[1]) ?: return null
                val tag = unhex(p[2]) ?: return null
                if (nonce.size != 16 || tag.size != 32) return null
                val expected = hmacSha256(key, nonce + cipher)
                if (!constantEquals(expected, tag)) return null
                xorStream(key, nonce, cipher).decodeToString()
            } else if (s.startsWith("ENC1.")) {
                val p = s.removePrefix("ENC1.").split('.')
                if (p.size != 2) return null
                val nonce = unhex(p[0]) ?: return null
                val cipher = unhex(p[1]) ?: return null
                if (nonce.size != 8) return null
                xorStream(key, nonce, cipher).decodeToString()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun xorStream(key: ByteArray, nonce: ByteArray, src: ByteArray): ByteArray {
        val out = ByteArray(src.size)
        var counter = 0
        var pos = 0
        while (pos < src.size) {
            val ctr = ByteArray(4)
            ctr[0] = ((counter ushr 24) and 0xFF).toByte()
            ctr[1] = ((counter ushr 16) and 0xFF).toByte()
            ctr[2] = ((counter ushr 8) and 0xFF).toByte()
            ctr[3] = (counter and 0xFF).toByte()
            val stream = sha256(key + nonce + ctr)
            for (b in stream) {
                if (pos >= src.size) break
                out[pos] = (src[pos].toInt() xor b.toInt()).toByte()
                pos++
            }
            counter++
            if (counter > 100000) throw IllegalArgumentException("payload too large")
        }
        return out
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val block = 64
        val k = if (key.size > block) sha256(key) else key.copyOf()
        val kb = ByteArray(block)
        k.copyInto(kb)
        val inner = ByteArray(block) { (kb[it].toInt() xor 0x36).toByte() }
        val outer = ByteArray(block) { (kb[it].toInt() xor 0x5c).toByte() }
        return sha256(outer + sha256(inner + data))
    }

    private fun constantEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private fun hex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) {
            val v = x.toInt() and 0xFF
            if (v < 16) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString()
    }

    private fun unhex(s: String): ByteArray? {
        try {
            if (s.length % 2 != 0 || s.length > 200000) return null
            val out = ByteArray(s.length / 2)
            for (i in out.indices) {
                out[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return out
        } catch (e: Exception) {
            return null
        }
    }
}

/** Password-protected backup: ENC2 envelope with key from password. */
object BackupCrypto {
    fun encrypt(password: String, plain: String): String {
        val key = sha256(("secount-backup|" + password).encodeToByteArray())
        return PairCrypto.encryptToHex(key, plain)
    }

    fun decrypt(password: String, cipher: String): String? {
        return try {
            val t = cipher.trim()
            if (!t.startsWith("ENC2.") && !t.startsWith("ENC1.")) return null
            val key = sha256(("secount-backup|" + password).encodeToByteArray())
            PairCrypto.decryptHex(key, t)
        } catch (e: Exception) {
            null
        }
    }
}
