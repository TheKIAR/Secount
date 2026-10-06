package com.secount.app.logic

import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Online pairing + delivery over plain HTTPS (ntfy.sh topics, no accounts).
 *
 * Two devices pair by exchanging 6-letter codes (told to each other
 * out-of-band). After both sides enter each other's code the devices are
 * linked. The link can only be severed when BOTH sides agree.
 */
object PairNet {
    /** Overridable for tests. Production uses the public ntfy.sh service. */
    var BASE = "https://ntfy.sh"

    fun inboxTopic(code: String): String = "secount-in-${code.lowercase()}"

    fun pairTopic(a: String, b: String): String {
        val x = a.lowercase()
        val y = b.lowercase()
        return if (x < y) "secount-p-$x-$y" else "secount-p-$y-$x"
    }
}

data class IncomingReq(val code: String, val accountId: String, val at: Long)

data class SyncResult(
    var justPaired: Boolean = false,
    var severAsked: Boolean = false,
    var severDeclined: Boolean = false,
    var severed: Boolean = false,
    var changed: Boolean = false,
    var offline: Boolean = false,
    var replyReceived: Boolean = false,
    /** Id of the countdown that got a reply (to auto-open it), or null. */
    var replyId: String? = null,
    var deleteId: String? = null,
    var seenId: String? = null
)

class PairStore(ns: String = "") {
    private val p: String = if (ns.isEmpty()) "" else ns + "_"
    internal fun k(name: String): String = p + name

    val accountId: String
    val myCode: String

    init {
        var id = prefsGet(k("acct_id"))
        if (id.isNullOrEmpty()) {
            id = UUID.randomUUID().toString().replace("-", "")
            prefsPut(k("acct_id"), id)
        }
        accountId = id
        var code = prefsGet(k("my_code"))
        if (code.isNullOrEmpty()) {
            code = newCode()
            prefsPut(k("my_code"), code)
        }
        myCode = code
    }

    fun isPaired(): Boolean = prefsGet(k("partner_id"))?.isNotEmpty() == true

    fun partnerId(): String = prefsGet(k("partner_id")) ?: ""
    fun partnerCode(): String = prefsGet(k("partner_code")) ?: ""
    fun pairedAt(): Long = prefsGet(k("paired_at"))?.toLongOrNull() ?: 0L

    fun pendingCode(): String {
        val c = prefsGet(k("pending_code")) ?: ""
        if (c.isEmpty()) return ""
        // Outgoing requests expire: a week-old unanswered code is stale
        // (user can simply re-enter it). Missing timestamp = legacy, keep.
        val at = pendingAt()
        if (at > 0 && nowSec() - at > PENDING_TTL_SEC) {
            setPending("")
            return ""
        }
        return c
    }
    fun setPending(code: String) {
        prefsPut(k("pending_code"), code)
        if (code.isEmpty()) {
            prefsPut(k("pending_at"), "")
        } else {
            prefsPut(k("pending_at"), nowSec().toString())
            // allow an immediate resend on next sync
            prefsPut(k("pending_resend_at"), "")
        }
    }
    fun pendingAt(): Long = prefsGet(k("pending_at"))?.toLongOrNull() ?: 0L
    fun lastResendAt(): Long = prefsGet(k("pending_resend_at"))?.toLongOrNull() ?: 0L
    fun setLastResendAt(v: Long) = prefsPut(k("pending_resend_at"), v.toString())

    fun wantSever(): Boolean = prefsGet(k("want_sever")) == "1"
    fun setWantSever(v: Boolean) = prefsPut(k("want_sever"), if (v) "1" else "")

    fun lastUnpairReqAt(): Long = prefsGet(k("unpair_req_at"))?.toLongOrNull() ?: 0L
    fun setLastUnpairReqAt(v: Long) = prefsPut(k("unpair_req_at"), v.toString())

    fun lastSeverTopic(): String = prefsGet(k("last_sever_topic")) ?: ""
    fun lastSeverPartnerCode(): String = prefsGet(k("last_sever_pcode")) ?: ""
    fun lastSeverAt(): Long = prefsGet(k("last_sever_at"))?.toLongOrNull() ?: 0L

    fun pairTopic(): String? {
        if (!isPaired()) return null
        return PairNet.pairTopic(myCode, partnerCode())
    }

    fun completePairing(partnerId: String, partnerCode: String) {
        prefsPut(k("partner_id"), partnerId)
        prefsPut(k("partner_code"), partnerCode)
        prefsPut(k("paired_at"), nowSec().toString())
        setPending("")
        removeIncoming(partnerCode)
        setWantSever(false)
    }

    fun sever(store: EventStore) {
        // Remember the old link so we can re-announce the sever for a few
        // minutes afterwards. Otherwise the partner can stay "connected"
        // forever if it missed the single unpair-done post (offline, cursor
        // already moved, app closed) — the reported one-sided disconnect.
        try {
            val topic = pairTopic()
            val pcode = partnerCode()
            if (topic != null && pcode.isNotEmpty()) {
                prefsPut(k("last_sever_topic"), topic)
                prefsPut(k("last_sever_pcode"), pcode)
                prefsPut(k("last_sever_at"), nowSec().toString())
            }
        } catch (ignored: Exception) {
        }
        val exPartner = partnerId()
        prefsPut(k("partner_id"), "")
        prefsPut(k("partner_code"), "")
        prefsPut(k("paired_at"), "")
        setPending("")
        setWantSever(false)
        prefsPut(k("incoming_reqs"), "[]")
        // remove everything shared with the ex-partner; keep personal items
        synchronized(store) {
            val kill = store.items().filter { e ->
                e.forPartner || (e.senderId.isNotEmpty() && e.senderId != accountId && e.senderId == exPartner)
            }.map { it.id }
            for (id in kill) store.delete(id)
        }
    }

    fun incoming(): List<IncomingReq> {
        val all = readIncoming()
        if (all.isEmpty()) return all
        val now = nowSec()
        val fresh = all.filter { it.at <= 0 || now - it.at <= INCOMING_TTL_SEC }
        if (fresh.size < all.size) saveIncoming(fresh)
        return fresh
    }

    private fun readIncoming(): List<IncomingReq> {
        val raw = prefsGet(k("incoming_reqs")) ?: return emptyList()
        val out = mutableListOf<IncomingReq>()
        try {
            val t = raw.trim()
            if (!t.startsWith("[") || !t.endsWith("]")) return out
            val inside = t.substring(1, t.length - 1).trim()
            if (inside.isEmpty()) return out
            // Split array into object elements by scanning (do NOT use
            // splitTopLevel here: it strips a single {...} and would return
            // fields instead of one element).
            var depth = 0
            var inStr = false
            var start = -1
            var i = 0
            while (i < inside.length) {
                val c = inside[i]
                if (inStr) {
                    if (c == '\\' && i + 1 < inside.length) i++
                    else if (c == '"') inStr = false
                } else {
                    if (c == '"') inStr = true
                    else if (c == '{') {
                        if (depth == 0) start = i
                        depth++
                    } else if (c == '}') {
                        depth--
                        if (depth == 0 && start >= 0) {
                            val obj = inside.substring(start, i + 1)
                            val map = flatMap(obj)
                            val code = map["code"] ?: ""
                            if (code.isNotEmpty()) {
                                out.add(IncomingReq(code, map["id"] ?: "", map["at"]?.toLongOrNull() ?: 0L))
                            }
                            start = -1
                        }
                    }
                }
                i++
            }
        } catch (ignored: Exception) {
        }
        return out
    }

    fun addIncoming(code: String, accountId: String) {
        val clean = code.trim().uppercase()
        if (clean.isEmpty() || clean == myCode) return
        if (isPaired() && clean == partnerCode()) return
        val cur = incoming().toMutableList()
        if (cur.any { it.code == clean }) return
        cur.add(IncomingReq(clean, accountId, nowSec()))
        saveIncoming(cur)
    }

    fun removeIncoming(code: String) {
        val cur = incoming().filter { it.code != code.trim().uppercase() }
        saveIncoming(cur)
    }

    private fun saveIncoming(list: List<IncomingReq>) {
        // Bound the list: drop week-old requests, keep the 20 newest.
        val now = nowSec()
        val kept = list.filter { it.at <= 0 || now - it.at <= INCOMING_TTL_SEC }
            .sortedByDescending { it.at }.take(MAX_INCOMING)
        val sb = StringBuilder("[")
        for ((i, r) in kept.withIndex()) {
            if (i > 0) sb.append(",")
            sb.append("{\"code\":").append(q(r.code))
                .append(",\"id\":").append(q(r.accountId))
                .append(",\"at\":").append(r.at).append("}")
        }
        sb.append("]")
        prefsPut(k("incoming_reqs"), sb.toString())
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        /** Pairing requests (incoming + outgoing pending) expire after 7 days. */
        private const val INCOMING_TTL_SEC = 7L * 24 * 3600
        private const val PENDING_TTL_SEC = 7L * 24 * 3600
        private const val MAX_INCOMING = 20

        fun newCode(): String {
            val sb = StringBuilder()
            repeat(6) { sb.append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
            return sb.toString()
        }

        fun looksLikeCode(s: String): Boolean {
            val t = s.trim().uppercase()
            if (t.length != 6) return false
            return t.all { ALPHABET.contains(it) }
        }

        internal fun flatMap(obj: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            for (part in JsonUtil.splitTopLevel(obj.trim())) {
                val colon = part.indexOf(':')
                if (colon < 0) continue
                val key = JsonUtil.unquote(part.substring(0, colon).trim())
                val v = part.substring(colon + 1).trim()
                map[key] = if (v.startsWith("\"")) JsonUtil.unquote(v) else v
            }
            return map
        }

        private fun q(s: String): String = "\"" + JsonUtil.escape(s) + "\""
    }
}

class SyncEngine(private val store: EventStore, private val pair: PairStore) {

    /** Poll inbox (+ pair topic when linked, or pending pair topic while waiting) and process everything. Never throws. */
    suspend fun syncNow(): SyncResult = withContext(Dispatchers.IO) {
        val res = SyncResult()
        try {
            pollTopic(PairNet.inboxTopic(pair.myCode), res)
            val linked = pair.pairTopic()
            if (linked != null) {
                pollTopic(linked, res)
                // Re-announce a fresh link for a few minutes so a late-polling
                // partner still sees the accept even if it missed the first one.
                reannounceAcceptIfFresh()
            } else {
                val pending = pair.pendingCode()
                if (pending.isNotEmpty()) {
                    // Listen for the accept even before we are linked.
                    try {
                        pollTopic(PairNet.pairTopic(pair.myCode, pending), res)
                    } catch (ignored: Exception) {
                    }
                    resendPendingIfDue()
                    // If the other side already asked before we entered their
                    // code, its original request is already consumed (since
                    // cursor moved). Re-check: mutual pending means linked.
                    // The resend above guarantees they will see us next sync,
                    // and their resend guarantees we will see them.
                }
            }
            sendUnsent()
            // While I wait for my partner to agree, keep re-posting my
            // unpair-request so they see it even if they missed the first one.
            if (pair.isPaired() && pair.wantSever()) resendUnpairRequestIfDue()
            // After I severed, keep re-announcing unpair-done for a few
            // minutes so a partner that missed the first post still drops.
            if (!pair.isPaired()) reannounceSeverIfFresh()
        } catch (e: Exception) {
            res.offline = true
        }
        res
    }

    /**
     * While I want out (requested, waiting for partner agreement) re-publish
     * unpair-request so the partner sees it even if they already consumed my
     * first post. Throttled to ~20s.
     */
    private fun resendUnpairRequestIfDue() {
        if (!pair.isPaired() || !pair.wantSever()) return
        val now = nowSec()
        if (now - pair.lastUnpairReqAt() < 20) return
        try {
            val topic = pair.pairTopic() ?: return
            httpPost(PairNet.BASE + "/" + topic, envelope("unpair-request", "{}"), 10000)
            pair.setLastUnpairReqAt(now)
        } catch (ignored: Exception) {
        }
    }

    /**
     * After severing locally, re-announce unpair-done for ~5 minutes so the
     * partner eventually drops too (fixes permanent one-sided disconnect).
     */
    private fun reannounceSeverIfFresh() {
        val topic = pair.lastSeverTopic()
        if (topic.isEmpty()) return
        val age = nowSec() - pair.lastSeverAt()
        if (age < 0 || age > 300) return
        try {
            val body = envelope("unpair-done", "{}")
            httpPost(PairNet.BASE + "/" + topic, body, 8000)
            val pcode = pair.lastSeverPartnerCode()
            if (pcode.isNotEmpty()) {
                try {
                    httpPost(PairNet.BASE + "/" + PairNet.inboxTopic(pcode), body, 8000)
                } catch (ignored: Exception) {
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private fun pairKey(): ByteArray? {
        return try {
            if (!pair.isPaired()) null
            else PairCrypto.deriveKey(pair.myCode, pair.partnerCode())
        } catch (e: Exception) {
            null
        }
    }

    private fun envelopeMaybeEnc(type: String, data: String): String {
        return try {
            if (type == "countdown" || type == "reply" || type == "delivered" ||
                type == "seen" || type == "delete" || type == "photo-chunk"
            ) {
                val k = pairKey()
                if (k != null) return envelope(type, PairCrypto.encryptToHex(k, data))
            }
            envelope(type, data)
        } catch (e: Exception) {
            envelope(type, data)
        }
    }

    private fun maybeDecrypt(dataRaw: String): String {
        return try {
            if (!dataRaw.startsWith("ENC2.") && !dataRaw.startsWith("ENC1.")) return dataRaw
            val k = pairKey() ?: return dataRaw
            PairCrypto.decryptHex(k, dataRaw) ?: return ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Retry publishing partner countdowns / replies / deletes / seens that failed while offline. */
    private fun sendUnsent() {
        if (!pair.isPaired()) return
        val topic = pair.pairTopic() ?: return
        for (e in store.items()) {
            if (!e.forPartner || !e.isMine(pair.accountId)) continue
            val sentVer = prefsGet(pair.k("sent_" + e.id))?.toLongOrNull()
            // Resend when never sent, or when edited after last send (versioned).
            if (sentVer != null && sentVer >= e.updatedAtSec) continue
            try {
                httpPost(PairNet.BASE + "/" + topic, envelopeMaybeEnc("countdown", e.toJson()), 12000)
                prefsPut(pair.k("sent_" + e.id), e.updatedAtSec.toString())
                // Piggyback photo chunks for this item when queued.
                try {
                    sendQueuedPhoto(e.id)
                } catch (ignored: Exception) {
                }
            } catch (ignored: Exception) {
            }
        }
        // Queued replies.
        try {
            val rq = prefsGet(pair.k("reply_queue")) ?: ""
            if (rq.isNotEmpty()) {
                val parts = rq.split("\n").filter { it.isNotBlank() }
                val remain = mutableListOf<String>()
                for (p in parts) {
                    val bar = p.indexOf('|')
                    if (bar < 0) continue
                    val id = p.substring(0, bar)
                    val txt = p.substring(bar + 1)
                    try {
                        httpPost(
                            PairNet.BASE + "/" + topic,
                            envelopeMaybeEnc("reply", "{\"id\":" + q(id) + ",\"reply\":" + q(txt) + "}"),
                            10000
                        )
                    } catch (e: Exception) {
                        remain.add(p)
                    }
                }
                prefsPut(pair.k("reply_queue"), remain.joinToString("\n"))
            }
        } catch (ignored: Exception) {
        }
        // Queued deletes.
        try {
            val dq = prefsGet(pair.k("delete_queue")) ?: ""
            if (dq.isNotEmpty()) {
                val remain = mutableListOf<String>()
                for (id in dq.split("\n").filter { it.isNotBlank() }) {
                    try {
                        httpPost(
                            PairNet.BASE + "/" + topic,
                            envelopeMaybeEnc("delete", "{\"id\":" + q(id) + "}"),
                            10000
                        )
                    } catch (e: Exception) {
                        remain.add(id)
                    }
                }
                prefsPut(pair.k("delete_queue"), remain.joinToString("\n"))
            }
        } catch (ignored: Exception) {
        }
        // Queued seen receipts.
        try {
            val sq = prefsGet(pair.k("seen_queue")) ?: ""
            if (sq.isNotEmpty()) {
                val remain = mutableListOf<String>()
                for (id in sq.split("\n").filter { it.isNotBlank() }) {
                    try {
                        httpPost(
                            PairNet.BASE + "/" + topic,
                            envelopeMaybeEnc("seen", "{\"id\":" + q(id) + ",\"at\":" + nowSec() + "}"),
                            10000
                        )
                    } catch (e: Exception) {
                        remain.add(id)
                    }
                }
                prefsPut(pair.k("seen_queue"), remain.joinToString("\n"))
            }
        } catch (ignored: Exception) {
        }
    }

    private fun queueReply(id: String, text: String) {
        try {
            val cur = prefsGet(pair.k("reply_queue")) ?: ""
            val clean = text.replace("\n", " ")
            prefsPut(pair.k("reply_queue"), (if (cur.isBlank()) "" else cur + "\n") + "$id|$clean")
        } catch (ignored: Exception) {
        }
    }

    private fun queueDelete(id: String) {
        try {
            val cur = prefsGet(pair.k("delete_queue")) ?: ""
            if (cur.lines().any { it.trim() == id }) return
            prefsPut(pair.k("delete_queue"), (if (cur.isBlank()) "" else cur + "\n") + id)
        } catch (ignored: Exception) {
        }
    }

    private fun queueSeen(id: String) {
        try {
            val cur = prefsGet(pair.k("seen_queue")) ?: ""
            if (cur.lines().any { it.trim() == id }) return
            prefsPut(pair.k("seen_queue"), (if (cur.isBlank()) "" else cur + "\n") + id)
        } catch (ignored: Exception) {
        }
    }

    /** Queue a photo for chunked sync (compressed base64 stored, sent on next sync). */
    fun queuePhoto(id: String, b64: String) {
        try {
            if (b64.isBlank() || b64.length > 400000) return
            prefsPut(pair.k("photoq_" + id), b64)
        } catch (ignored: Exception) {
        }
    }

    private fun sendQueuedPhoto(id: String) {
        val b64 = try { prefsGet(pair.k("photoq_" + id)) ?: return } catch (e: Exception) { return }
        if (b64.isBlank()) return
        val topic = pair.pairTopic() ?: return
        val chunkSize = 3000
        val total = (b64.length + chunkSize - 1) / chunkSize
        if (total <= 0 || total > 150) return
        for (i in 0 until total) {
            val part = b64.substring(i * chunkSize, minOf(b64.length, (i + 1) * chunkSize))
            val data = "{\"id\":" + q(id) + ",\"idx\":" + i + ",\"total\":" + total + ",\"chunk\":" + q(part) + "}"
            httpPost(PairNet.BASE + "/" + topic, envelopeMaybeEnc("photo-chunk", data), 12000)
        }
        try {
            prefsPut(pair.k("photoq_" + id), "")
        } catch (ignored: Exception) {
        }
    }

    fun markSent(id: String) = prefsPut(pair.k("sent_" + id), "1")

    /**
     * While waiting (pending set, not yet paired) re-publish our request so
     * the other side sees it even if it already consumed our first post
     * before entering our code. Throttled to ~20s to avoid spamming ntfy.
     */
    private fun resendPendingIfDue() {
        val pending = pair.pendingCode()
        if (pending.isEmpty() || pair.isPaired()) return
        val now = nowSec()
        if (now - pair.lastResendAt() < 20) return
        try {
            val data = "{\"code\":" + q(pair.myCode) + ",\"id\":" + q(pair.accountId) + "}"
            httpPost(PairNet.BASE + "/" + PairNet.inboxTopic(pending), envelope("pair-request", data), 10000)
            pair.setLastResendAt(now)
        } catch (ignored: Exception) {
        }
    }

    /**
     * For a few minutes after linking, re-announce the accept on every sync.
     * This covers the case where the partner's inbox cursor already moved
     * past our first accept post.
     */
    private fun reannounceAcceptIfFresh() {
        if (!pair.isPaired()) return
        val age = nowSec() - pair.pairedAt()
        if (age < 0 || age > 300) return
        try {
            val topic = pair.pairTopic() ?: return
            val body = envelope("pair-accept", "{\"code\":" + q(pair.myCode) + "}")
            httpPost(PairNet.BASE + "/" + topic, body, 8000)
            // Dual-channel: also drop it straight into their inbox.
            httpPost(PairNet.BASE + "/" + PairNet.inboxTopic(pair.partnerCode()), body, 8000)
        } catch (ignored: Exception) {
        }
    }

    /** Publish pair-accept on BOTH the shared pair topic AND the partner's inbox. */
    private fun sendAcceptDual(partnerCode: String) {
        val body = envelope("pair-accept", "{\"code\":" + q(pair.myCode) + "}")
        try {
            publishQuiet(PairNet.pairTopic(pair.myCode, partnerCode), body)
        } catch (ignored: Exception) {
        }
        try {
            publishQuiet(PairNet.inboxTopic(partnerCode), body)
        } catch (ignored: Exception) {
        }
    }

    suspend fun sendPairRequest(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val clean = code.trim().uppercase()
            if (clean == pair.myCode) return@withContext false
            if (!PairStore.looksLikeCode(clean)) return@withContext false
            val data = "{\"code\":" + q(pair.myCode) + ",\"id\":" + q(pair.accountId) + "}"
            httpPost(PairNet.BASE + "/" + PairNet.inboxTopic(clean), envelope("pair-request", data), 12000)
            pair.setPending(clean)
            pair.setLastResendAt(nowSec())
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendCountdown(item: EventItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val topic = pair.pairTopic() ?: return@withContext false
            try {
                item.touchUpdated(nowSec())
                store.addOrUpdate(item)
            } catch (ignored: Exception) {
            }
            httpPost(PairNet.BASE + "/" + topic, envelopeMaybeEnc("countdown", item.toJson()), 12000)
            prefsPut(pair.k("sent_" + item.id), item.updatedAtSec.toString())
            // Send queued photo chunks right away (same sync window).
            try {
                sendQueuedPhoto(item.id)
            } catch (ignored: Exception) {
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Synced delete: tell the partner to drop their copy too. Queued offline. */
    suspend fun sendDelete(itemId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val topic = pair.pairTopic() ?: return@withContext false
            val data = "{\"id\":" + q(itemId) + "}"
            try {
                httpPost(PairNet.BASE + "/" + topic, envelopeMaybeEnc("delete", data), 12000)
                true
            } catch (e: Exception) {
                queueDelete(itemId)
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendDelivered(itemId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val topic = pair.pairTopic() ?: return@withContext false
            val data = "{\"id\":" + q(itemId) + ",\"at\":" + nowSec() + "}"
            httpPost(PairNet.BASE + "/" + topic, envelopeMaybeEnc("delivered", data), 12000)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Seen / read receipt: receiver opened the message. Queued offline. */
    suspend fun sendSeen(itemId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val topic = pair.pairTopic() ?: return@withContext false
            val data = "{\"id\":" + q(itemId) + ",\"at\":" + nowSec() + "}"
            try {
                httpPost(PairNet.BASE + "/" + topic, envelopeMaybeEnc("seen", data), 12000)
                true
            } catch (e: Exception) {
                queueSeen(itemId)
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Send / update the reply thread on a shared secret countdown. Queued offline. */
    suspend fun sendReply(itemId: String, reply: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val topic = pair.pairTopic() ?: return@withContext false
            val data = "{\"id\":" + q(itemId) + ",\"reply\":" + q(reply) + "}"
            try {
                httpPost(PairNet.BASE + "/" + topic, envelopeMaybeEnc("reply", data), 12000)
                true
            } catch (e: Exception) {
                queueReply(itemId, reply)
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /** I want out: tell the partner, wait for their agreement (or instant if asked already). */
    suspend fun requestSever(): Boolean = withContext(Dispatchers.IO) {
        if (!pair.isPaired()) return@withContext false
        pair.setWantSever(true)
        pair.setLastUnpairReqAt(nowSec())
        return@withContext try {
            val topic = pair.pairTopic() ?: return@withContext true
            httpPost(PairNet.BASE + "/" + topic, envelope("unpair-request", "{}"), 12000)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Partner asked out and I agree: tell them it's done, then sever locally. */
    suspend fun agreeSever(): Boolean = withContext(Dispatchers.IO) {
        if (!pair.isPaired()) return@withContext false
        val topic = pair.pairTopic()
        if (topic != null) {
            try {
                httpPost(PairNet.BASE + "/" + topic, envelope("unpair-done", "{}"), 12000)
            } catch (ignored: Exception) {
            }
        }
        pair.sever(store)
        return@withContext true
    }

    suspend fun declineSever(): Boolean = withContext(Dispatchers.IO) {
        if (!pair.isPaired()) return@withContext false
        return@withContext try {
            val topic = pair.pairTopic() ?: return@withContext false
            httpPost(PairNet.BASE + "/" + topic, envelope("unpair-decline", "{}"), 12000)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------- internals
    private fun pollTopic(topic: String, res: SyncResult) {
        val sinceKey = pair.k("ntfy_since_") + topic
        val since = prefsGet(sinceKey) ?: ""
        // First poll replays the topic cache so nothing sent earlier is missed.
        val url = if (since.isEmpty()) PairNet.BASE + "/" + topic + "/json?since=all"
        else PairNet.BASE + "/" + topic + "/json?since=" + since
        val body = try {
            httpGet(url, 9000)
        } catch (e: Exception) {
            res.offline = true
            return
        }
        var maxId: String? = null
        for (line in body.lines()) {
            val t = line.trim()
            if (!t.startsWith("{")) continue
            val top = PairStore.flatMap(t)
            if (top["event"] != "message") continue
            val nid = top["id"]
            if (nid != null) maxId = nid
            val raw = extractRaw(t, "message") ?: continue
            handleEnvelope(raw, res)
        }
        if (maxId != null) prefsPut(sinceKey, maxId)
    }

    private fun handleEnvelope(raw: String, res: SyncResult) {
        val env = try {
            PairStore.flatMap(raw)
        } catch (e: Exception) {
            return
        }
        val type = env["type"] ?: return
        val from = env["from"] ?: ""
        if (from.isEmpty() || from == pair.accountId) return
        var dataRaw = extractRaw(raw, "data") ?: "{}"
        if (dataRaw.startsWith("ENC2.") || dataRaw.startsWith("ENC1.")) {
            dataRaw = maybeDecrypt(dataRaw)
            if (dataRaw.isEmpty()) return
        }
        when (type) {
            "pair-request" -> {
                val d = PairStore.flatMap(dataRaw)
                val code = (d["code"] ?: "").uppercase()
                if (code.isEmpty()) return
                if (pair.isPaired() && code == pair.partnerCode() && from == pair.partnerId()) {
                    // repair: re-confirm an existing link on both channels
                    sendAcceptDual(code)
                    return
                }
                if (pair.isPaired()) return
                pair.addIncoming(code, from)
                // mutual: I already entered their code -> we are linked
                if (pair.pendingCode() == code) {
                    pair.completePairing(from, code)
                    sendAcceptDual(code)
                    res.justPaired = true
                }
                res.changed = true
            }
            "pair-accept" -> {
                val d = PairStore.flatMap(dataRaw)
                val code = (d["code"] ?: "").uppercase()
                if (code.isEmpty()) return
                if (!pair.isPaired() && pair.pendingCode() == code) {
                    pair.completePairing(from, code)
                    // Confirm back so the other side stops re-announcing.
                    sendAcceptDual(code)
                    res.justPaired = true
                    res.changed = true
                } else if (pair.isPaired() && code == pair.partnerCode() && from == pair.partnerId()) {
                    // Late duplicate accept: harmless, ensures both stay linked.
                    res.changed = true
                }
            }
            "countdown" -> {
                if (!pair.isPaired() || from != pair.partnerId()) return
                try {
                    val item = EventItem.fromJson(dataRaw)
                    // The receiver must never be able to edit/overwrite: only
                    // accept items actually created by the sender. A receiver
                    // echoing back our own item has senderId != from -> drop.
                    if (item.senderId.isNotEmpty() && item.senderId != from) return
                    item.senderId = from
                    val existing = store.byId(item.id)
                    if (existing != null) {
                        // Stale edit protection: don't let an old resend wipe
                        // a newer local copy — merge conversation only.
                        if (item.updatedAtSec < existing.updatedAtSec) {
                            existing.mergeThread(item.replyThread)
                            store.addOrUpdate(existing)
                        } else {
                            // Preserve local conversation when incoming is older/smaller.
                            val keepThread = existing.replyThread
                            val keepMsg = existing.replyMessage
                            store.addOrUpdate(item)
                            val applied = store.byId(item.id)
                            if (applied != null && keepThread.length > (item.replyThread.length)) {
                                applied.mergeThread(keepThread)
                                if (keepMsg.isNotBlank() && applied.replyMessage.isBlank()) {
                                    applied.replyMessage = keepMsg
                                }
                                store.addOrUpdate(applied)
                            }
                        }
                        // A re-sent countdown clears a pending delete for it.
                        try {
                            val dq = prefsGet(pair.k("delete_queue")) ?: ""
                            if (dq.lines().any { it.trim() == item.id }) {
                                prefsPut(
                                    pair.k("delete_queue"),
                                    dq.lines().filter { it.trim() != item.id }.joinToString("\n")
                                )
                            }
                        } catch (ignored: Exception) {
                        }
                    } else {
                        // Don't resurrect an item the user already deleted via sync.
                        try {
                            val dq = prefsGet(pair.k("delete_queue")) ?: ""
                            if (dq.lines().any { it.trim() == item.id }) return
                        } catch (ignored: Exception) {
                        }
                        store.addOrUpdate(item)
                    }
                    // This counts as (re)sent — clear any photo queue marker only
                    // after chunks arrive (handled in photo-chunk branch).
                    res.changed = true
                } catch (ignored: Exception) {
                }
            }
            "reply" -> {
                if (!pair.isPaired() || from != pair.partnerId()) return
                try {
                    val d = PairStore.flatMap(dataRaw)
                    val id = d["id"] ?: return
                    val reply = d["reply"] ?: ""
                    if (reply.isBlank()) return
                    val item = store.byId(id) ?: return
                    // Only shared partner items carry a reply thread.
                    if (!item.forPartner && !item.isForMe(pair.accountId)) {
                        if (item.senderId != pair.accountId && item.senderId != from) return
                    }
                    val last = item.threadEntries().lastOrNull()?.third ?: ""
                    if (last != reply.trim()) {
                        item.appendReply("partner", reply, nowSec())
                        store.addOrUpdate(item)
                    }
                    res.replyReceived = true
                    res.replyId = id
                    res.changed = true
                } catch (ignored: Exception) {
                }
            }
            "delivered" -> {
                val d = PairStore.flatMap(dataRaw)
                val id = d["id"] ?: return
                val item = store.byId(id) ?: return
                if (!item.isMine(pair.accountId)) return
                val at = d["at"]?.toLongOrNull() ?: nowSec()
                var touched = false
                if (!item.delivered) {
                    item.delivered = true
                    touched = true
                }
                if (at > item.deliveredAtSec) {
                    item.deliveredAtSec = at
                    touched = true
                }
                if (touched) {
                    store.addOrUpdate(item)
                    res.changed = true
                }
            }
            "seen" -> {
                if (!pair.isPaired() || from != pair.partnerId()) return
                val d = PairStore.flatMap(dataRaw)
                val id = d["id"] ?: return
                val item = store.byId(id) ?: return
                if (!item.isMine(pair.accountId)) return
                val at = d["at"]?.toLongOrNull() ?: nowSec()
                if (at > item.seenAtSec) {
                    item.seenAtSec = at
                    if (!item.delivered) {
                        item.delivered = true
                        if (item.deliveredAtSec <= 0) item.deliveredAtSec = at
                    }
                    store.addOrUpdate(item)
                    res.changed = true
                    res.seenId = id
                }
            }
            "delete" -> {
                if (!pair.isPaired() || from != pair.partnerId()) return
                try {
                    val d = PairStore.flatMap(dataRaw)
                    val id = d["id"] ?: return
                    val item = store.byId(id) ?: return
                    // Only the creator can delete via sync.
                    if (item.senderId != from) return
                    store.delete(id)
                    try {
                        deletePhotoFile(item.photoUri)
                    } catch (ignored: Exception) {
                    }
                    res.deleteId = id
                    res.changed = true
                } catch (ignored: Exception) {
                }
            }
            "photo-chunk" -> {
                if (!pair.isPaired() || from != pair.partnerId()) return
                try {
                    val d = PairStore.flatMap(dataRaw)
                    val id = d["id"] ?: return
                    val idx = d["idx"]?.toIntOrNull() ?: return
                    val total = d["total"]?.toIntOrNull() ?: return
                    val chunk = d["chunk"] ?: return
                    if (total <= 0 || total > 150 || idx < 0 || idx >= total) return
                    if (chunk.length > 5000) return
                    val item = store.byId(id) ?: return
                    if (!item.isForMe(pair.accountId)) return
                    try {
                        prefsPut(pair.k("pchunk_${id}_$idx"), chunk)
                        prefsPut(pair.k("pchunk_total_$id"), total.toString())
                    } catch (ignored: Exception) {
                        return
                    }
                    // Check completeness.
                    var have = 0
                    for (i in 0 until total) {
                        try {
                            if ((prefsGet(pair.k("pchunk_${id}_$i") ) ?: "").isNotEmpty()) have++
                        } catch (ignored: Exception) {
                        }
                    }
                    if (have == total) {
                        val sb = StringBuilder()
                        for (i in 0 until total) {
                            sb.append(prefsGet(pair.k("pchunk_${id}_$i")) ?: "")
                        }
                        val b64 = sb.toString()
                        try {
                            val name = savePhotoB64(b64)
                            if (name != null && name.isNotEmpty()) {
                                // Drop old photo file if replaced.
                                val old = item.photoUri
                                item.photoUri = name
                                store.addOrUpdate(item)
                                if (old.isNotEmpty() && old != name) {
                                    try {
                                        deletePhotoFile(old)
                                    } catch (ignored: Exception) {
                                    }
                                }
                                res.changed = true
                            }
                        } catch (ignored: Exception) {
                        }
                        // Cleanup chunks.
                        try {
                            for (i in 0 until total) prefsPut(pair.k("pchunk_${id}_$i"), "")
                            prefsPut(pair.k("pchunk_total_$id"), "")
                        } catch (ignored: Exception) {
                        }
                    }
                } catch (ignored: Exception) {
                }
            }
            "unpair-request" -> {
                if (!pair.isPaired() || from != pair.partnerId()) return
                if (pair.wantSever()) {
                    // both agreed (I asked, they asked) -> sever now
                    pair.sever(store)
                    res.severed = true
                    res.changed = true
                } else {
                    res.severAsked = true
                }
            }
            "unpair-decline" -> {
                if (!pair.isPaired() || from != pair.partnerId()) return
                pair.setWantSever(false)
                res.severDeclined = true
            }
            "unpair-done" -> {
                // partner severed after agreement; if I also agreed, drop locally
                if (pair.isPaired() && from == pair.partnerId() && pair.wantSever()) {
                    pair.sever(store)
                    res.severed = true
                    res.changed = true
                }
            }
        }
    }

    private fun publishQuiet(topic: String, body: String) {
        try {
            httpPost(PairNet.BASE + "/" + topic, body, 10000)
        } catch (ignored: Exception) {
        }
    }

    private fun envelope(type: String, data: String): String {
        val id = UUID.randomUUID().toString().replace("-", "")
        return "{\"v\":1,\"type\":" + q(type) +
            ",\"from\":" + q(pair.accountId) +
            ",\"id\":" + q(id) +
            ",\"at\":" + nowSec() +
            ",\"data\":" + q(data) + "}"
    }

    private fun q(s: String): String = "\"" + JsonUtil.escape(s) + "\""

    /** Extract a JSON string value (unescaped) for key, or null. */
    internal fun extractRaw(obj: String, key: String): String? {
        val needle = "\"$key\":"
        var i = obj.indexOf(needle)
        if (i < 0) return null
        i += needle.length
        while (i < obj.length && (obj[i] == ' ' || obj[i] == '\t')) i++
        if (i >= obj.length || obj[i] != '"') return null
        i++
        val sb = StringBuilder()
        var esc = false
        while (i < obj.length) {
            val c = obj[i]
            if (esc) {
                sb.append('\\')
                sb.append(c)
                esc = false
            } else if (c == '\\') esc = true
            else if (c == '"') break
            else sb.append(c)
            i++
        }
        return JsonUtil.unquote("\"$sb\"")
    }
}
