package com.secount.app.logic

import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Full pairing + delivery + mutual-sever protocol test against an
 * embedded fake ntfy server (no internet needed).
 */
class SyncLiveTest {

    private data class Msg(val id: String, val topic: String, val body: String)

    private class FakeNtfy {
        private val server = ServerSocket(0)
        val port: Int = server.localPort
        private val messages = ConcurrentHashMap<String, CopyOnWriteArrayList<Msg>>()
        private val counter = AtomicInteger(0)
        @Volatile private var running = true
        val published = CopyOnWriteArrayList<Msg>()
        val pollQueries = CopyOnWriteArrayList<String>()
        val serverErrors = CopyOnWriteArrayList<String>()
        @Volatile var accepted = 0

        fun publish(topic: String, body: String): String {
            val id = "m" + counter.incrementAndGet()
            val msg = Msg(id, topic, body)
            messages.getOrPut(topic) { CopyOnWriteArrayList() }.add(msg)
            published.add(msg)
            return id
        }

        init {
            thread(name = "fake-ntfy", isDaemon = true) {
                while (running) {
                    try {
                        val s = server.accept()
                        accepted++
                        thread(isDaemon = true) { handle(s) }
                    } catch (e: Exception) {
                        if (running) serverErrors.add("accept: $e")
                    }
                }
            }
        }

        fun stop() {
            running = false
            try {
                server.close()
            } catch (ignored: Exception) {
            }
        }

        private fun handle(s: java.net.Socket) {
            try {
                s.soTimeout = 15000
                val input = s.getInputStream()
                val head = ByteArrayOutputStream()
                var prev = 0
                while (true) {
                    val b = input.read()
                    if (b < 0) break
                    head.write(b)
                    if (prev == '\r'.code && b == '\n'.code) {
                        // check for blank line
                        val h = head.toString(Charsets.UTF_8)
                        if (h.endsWith("\r\n\r\n")) break
                    }
                    prev = b
                    if (head.size() > 65536) break
                }
                val header = head.toString(Charsets.UTF_8)
                val requestLine = header.lineSequence().firstOrNull() ?: ""
                val parts = requestLine.split(" ")
                val method = parts.getOrElse(0) { "" }
                val target = parts.getOrElse(1) { "/" }
                var contentLength = 0
                for (line in header.lineSequence()) {
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }
                var body = ""
                if (contentLength > 0 && contentLength < 1_000_000) {
                    val buf = ByteArray(contentLength)
                    var off = 0
                    while (off < contentLength) {
                        val n = input.read(buf, off, contentLength - off)
                        if (n < 0) break
                        off += n
                    }
                    body = String(buf, 0, off, Charsets.UTF_8)
                }
                val path = target.substringBefore("?")
                val query = if (target.contains("?")) target.substringAfter("?") else ""
                var topic = path.trimStart('/')
                if (topic.endsWith("/json")) topic = topic.removeSuffix("/json")
                val since = query.split("&").firstOrNull { it.startsWith("since=") }
                    ?.substringAfter("=") ?: ""
                val responseBody: String = if (method == "POST") {
                    val id = publish(topic, body)
                    "{\"id\":\"$id\",\"time\":1}"
                } else {
                    pollQueries.add(query)
                    val list = messages[topic] ?: CopyOnWriteArrayList()
                    val sel = if (since == "all" || since.isEmpty()) list.toList()
                    else {
                        val idx = list.indexOfFirst { it.id == since }
                        if (idx < 0) list.toList() else list.drop(idx + 1)
                    }
                    sel.joinToString("\n") {
                        "{\"id\":\"${it.id}\",\"time\":1,\"event\":\"message\"," +
                            "\"topic\":\"${it.topic}\",\"message\":\"${JsonUtil.escape(it.body)}\"}"
                    }
                }
                val bytes = responseBody.toByteArray(Charsets.UTF_8)
                val out = s.getOutputStream()
                val headers = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                    "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                out.write(headers.toByteArray(Charsets.UTF_8))
                out.write(bytes)
                out.flush()
            } catch (e: Exception) {
                serverErrors.add("handle: $e")
            } finally {
                try {
                    s.close()
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private lateinit var fake: FakeNtfy
    private lateinit var dirA: String
    private lateinit var dirB: String
    private var runTag: String = ""

    @BeforeTest
    fun setUp() {
        fake = FakeNtfy()
        PairNet.BASE = "http://127.0.0.1:${fake.port}"
        runTag = System.nanoTime().toString()
        val base = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / ("secount-sync-$runTag")).toString()
        dirA = "$base/a"
        dirB = "$base/b"
        FileSystem.SYSTEM.createDirectories(dirA.toPath())
        FileSystem.SYSTEM.createDirectories(dirB.toPath())
    }

    @AfterTest
    fun tearDown() {
        PairNet.BASE = "https://ntfy.sh"
        fake.stop()
    }

    private fun party(ns: String, dir: String): Triple<PairStore, EventStore, SyncEngine> {
        val pair = PairStore(ns + "_" + runTag)
        val store = EventStore(dir)
        store.load()
        // start from seeds only; give unique readable names per party
        return Triple(pair, store, SyncEngine(store, pair))
    }

    @Test
    fun syncUsesImmediateIncrementalPolls() = runBlocking {
        val (pair, _, engine) = party("fast", dirA)
        val inbox = PairNet.inboxTopic(pair.myCode)

        engine.syncNow()
        assertEquals("since=all&poll=1", fake.pollQueries.last())

        val messageId = fake.publish(inbox, "{}")
        engine.syncNow()
        assertEquals("since=all&poll=1", fake.pollQueries.last())
        assertEquals(messageId, prefsGet(pair.k("ntfy_since_") + inbox))

        engine.syncNow()
        assertEquals("since=$messageId&poll=1", fake.pollQueries.last())
    }

    @Test
    fun pairRequestAndAcceptLinksBoth() = runBlocking {
        val (pairA, _, engineA) = party("pa", dirA)
        val (pairB, _, engineB) = party("pb", dirB)

        assertTrue(
            engineA.sendPairRequest(pairB.myCode),
            "POST failed; accepted=${fake.accepted} published=${fake.published.size} errors=${fake.serverErrors}"
        )
        assertEquals(pairB.myCode, pairA.pendingCode())

        assertTrue(engineB.sendPairRequest(pairA.myCode))
        val rB = engineB.syncNow()
        assertTrue(rB.justPaired, "B should pair on mutual request")
        assertTrue(pairB.isPaired())
        assertEquals(pairA.accountId, pairB.partnerId())

        val rA = engineA.syncNow()
        assertTrue(rA.justPaired || pairA.isPaired(), "A should pair too")
        assertTrue(pairA.isPaired())
        assertEquals(pairB.accountId, pairA.partnerId())
    }

    @Test
    fun staggeredPairLinksBoth() = runBlocking {
        // Real-world timing: A sends, B sees it BEFORE entering A's code,
        // then B sends, A pairs first, B must still pair via accept.
        val (pairA, _, engineA) = party("qa", dirA)
        val (pairB, _, engineB) = party("qb", dirB)

        assertTrue(engineA.sendPairRequest(pairB.myCode))
        // B syncs too early: only records incoming, cannot pair yet.
        engineB.syncNow()
        assertFalse(pairB.isPaired(), "B must not pair before entering A's code")
        assertTrue(pairB.incoming().any { it.code == pairA.myCode }, "B should see A's request")

        // Now B enters A's code and syncs (its cursor already consumed A's request).
        assertTrue(engineB.sendPairRequest(pairA.myCode))
        engineB.syncNow()
        assertFalse(pairB.isPaired(), "B still waiting until A pairs + accepts")

        // A syncs, sees B's request, pairs and broadcasts accept dual-channel.
        val rA = engineA.syncNow()
        assertTrue(pairA.isPaired(), "A pairs on mutual request")
        assertEquals(pairB.accountId, pairA.partnerId())

        // B's next sync must pick up the accept (inbox or pending pair-topic).
        val rB2 = engineB.syncNow()
        assertTrue(pairB.isPaired(), "B must pair via accept, justPaired=${rB2.justPaired}")
        assertEquals(pairA.accountId, pairB.partnerId())
    }

    @Test
    fun countdownDeliversToPartner() = runBlocking {
        val (pairA, storeA, engineA) = party("da", dirA)
        val (pairB, storeB, engineB) = party("db", dirB)
        engineA.sendPairRequest(pairB.myCode)
        engineB.sendPairRequest(pairA.myCode)
        engineB.syncNow()
        engineA.syncNow()
        assertTrue(pairA.isPaired() && pairB.isPaired())

        val item = EventItem()
        item.title = "Surprise"
        item.date = LocalDate.now().plusDays(3)
        item.message = "Hello partner"
        item.senderId = pairA.accountId
        item.forPartner = true
        storeA.addOrUpdate(item)
        assertTrue(engineA.sendCountdown(item))

        engineB.syncNow()
        val back = storeB.byId(item.id)
        assertNotNull(back, "partner receives the countdown")
        assertTrue(back.isForMe(pairB.accountId))
        assertEquals("Hello partner", back.message)

        // receipt flows back
        assertTrue(engineB.sendDelivered(item.id))
        engineA.syncNow()
        assertTrue(storeA.byId(item.id)!!.delivered)
    }

    @Test
    fun severNeedsBothSides() = runBlocking {
        val (pairA, _, engineA) = party("sa", dirA)
        val (pairB, _, engineB) = party("sb", dirB)
        engineA.sendPairRequest(pairB.myCode)
        engineB.sendPairRequest(pairA.myCode)
        engineB.syncNow()
        engineA.syncNow()
        assertTrue(pairA.isPaired() && pairB.isPaired())

        // A asks out; B hasn't agreed -> still linked
        assertTrue(engineA.requestSever())
        val rB = engineB.syncNow()
        assertTrue(rB.severAsked, "B is asked")
        assertTrue(pairA.isPaired() && pairB.isPaired(), "still linked until B agrees")

        // B declines -> link stays, A's flag cleared
        assertTrue(engineB.declineSever())
        val rA = engineA.syncNow()
        assertTrue(rA.severDeclined)
        assertTrue(pairA.isPaired() && pairB.isPaired())

        // A asks again, B agrees -> both sever
        assertTrue(engineA.requestSever())
        engineB.syncNow()
        assertTrue(engineB.agreeSever())
        assertFalse(pairB.isPaired())
        val rA2 = engineA.syncNow()
        assertTrue(rA2.severed)
        assertFalse(pairA.isPaired())
    }

    @Test
    fun severReannounceHealsMissedDone() = runBlocking {
        val (pairA, _, engineA) = party("ra", dirA)
        val (pairB, _, engineB) = party("rb", dirB)
        engineA.sendPairRequest(pairB.myCode)
        engineB.sendPairRequest(pairA.myCode)
        engineB.syncNow()
        engineA.syncNow()
        assertTrue(pairA.isPaired() && pairB.isPaired())

        assertTrue(engineA.requestSever())
        engineB.syncNow() // B sees the ask
        val before = fake.published.size
        assertTrue(engineB.agreeSever())
        assertFalse(pairB.isPaired())
        // Severed side keeps re-announcing done so a partner that missed the
        // first post still drops: an extra sync must publish again.
        engineB.syncNow()
        assertTrue(fake.published.size > before, "expected sever re-announce")
        val rA = engineA.syncNow()
        assertTrue(rA.severed)
        assertFalse(pairA.isPaired())
    }

    @Test
    fun receiverCannotOverwriteSenderItem() = runBlocking {
        val (pairA, storeA, engineA) = party("oa", dirA)
        val (pairB, storeB, engineB) = party("ob", dirB)
        engineA.sendPairRequest(pairB.myCode)
        engineB.sendPairRequest(pairA.myCode)
        engineB.syncNow()
        engineA.syncNow()
        assertTrue(pairA.isPaired() && pairB.isPaired())

        val item = EventItem()
        item.title = "Original"
        item.date = LocalDate.now().plusDays(3)
        item.message = "Secret plan"
        item.senderId = pairA.accountId
        item.forPartner = true
        storeA.addOrUpdate(item)
        assertTrue(engineA.sendCountdown(item))
        engineB.syncNow()
        assertNotNull(storeB.byId(item.id))

        // Receiver echoes back an edited copy (senderId still A's, from is B).
        // Must be dropped: only the creator's posts apply.
        val forged = EventItem.fromJson(storeB.byId(item.id)!!.toJson())
        forged.title = "Hacked"
        val env = "{\"v\":1,\"type\":\"countdown\",\"from\":\"" + pairB.accountId + "\"," +
            "\"id\":\"x2\",\"at\":1,\"data\":" + q(forged.toJson()) + "}"
        val topic = PairNet.pairTopic(pairA.myCode, pairB.myCode)
        val conn = java.net.URL("${PairNet.BASE}/$topic").openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.outputStream.use { it.write(env.toByteArray(Charsets.UTF_8)) }
            assertTrue(conn.responseCode in 200..299)
        } finally {
            conn.disconnect()
        }
        engineA.syncNow()
        assertEquals("Original", storeA.byId(item.id)!!.title)
    }

    @Test
    fun replyDeliversToSender() = runBlocking {
        val (pairA, storeA, engineA) = party("ya", dirA)
        val (pairB, storeB, engineB) = party("yb", dirB)
        engineA.sendPairRequest(pairB.myCode)
        engineB.sendPairRequest(pairA.myCode)
        engineB.syncNow()
        engineA.syncNow()
        assertTrue(pairA.isPaired() && pairB.isPaired())

        val item = EventItem()
        item.title = "Surprise"
        item.date = LocalDate.now().plusDays(1)
        item.message = "Hi"
        item.senderId = pairA.accountId
        item.forPartner = true
        storeA.addOrUpdate(item)
        assertTrue(engineA.sendCountdown(item))
        engineB.syncNow()
        assertNotNull(storeB.byId(item.id))

        assertTrue(engineB.sendReply(item.id, "Thank you!"))
        val rA = engineA.syncNow()
        assertTrue(rA.replyReceived)
        assertEquals("Thank you!", storeA.byId(item.id)!!.replyMessage)
    }

    @Test
    fun strangersCannotInject() = runBlocking {
        val (pairA, storeA, engineA) = party("xa", dirA)
        val (pairB, storeB, engineB) = party("xb", dirB)
        engineA.sendPairRequest(pairB.myCode)
        engineB.sendPairRequest(pairA.myCode)
        engineB.syncNow()
        engineA.syncNow()
        assertTrue(pairA.isPaired() && pairB.isPaired())

        // attacker forges a countdown directly onto the pair topic
        val forged = EventItem()
        forged.title = "Forged"
        forged.senderId = "attacker"
        forged.forPartner = true
        val env = "{\"v\":1,\"type\":\"countdown\",\"from\":\"attacker\"," +
            "\"id\":\"x1\",\"at\":1,\"data\":" + q(forged.toJson()) + "}"
        val topic = PairNet.pairTopic(pairA.myCode, pairB.myCode)
        val conn = java.net.URL("${PairNet.BASE}/$topic").openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.outputStream.use { it.write(env.toByteArray(Charsets.UTF_8)) }
            assertTrue(conn.responseCode in 200..299)
        } finally {
            conn.disconnect()
        }
        engineA.syncNow()
        engineB.syncNow()
        assertNull(storeA.items().firstOrNull { it.title == "Forged" })
        assertNull(storeB.items().firstOrNull { it.title == "Forged" })
    }

    private fun q(s: String): String = "\"" + JsonUtil.escape(s) + "\""
}
