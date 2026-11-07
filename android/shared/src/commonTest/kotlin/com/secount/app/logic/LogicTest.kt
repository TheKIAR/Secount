package com.secount.app.logic

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import okio.FileSystem
import okio.Path.Companion.toPath

class LogicTest {

    private fun tempStore(): EventStore {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "secount-test-${System.nanoTime()}"
        return EventStore(dir.toString())
    }

    @Test
    fun seedsLoad() {
        // No samples/seeds: fresh store starts empty.
        val store = tempStore()
        store.load()
        assertEquals(0, store.items().size)
    }

    @Test
    fun malformedJsonDoesNotCreateUsableEvent() {
        val store = tempStore()
        store.load()
        val imported = store.importJson("[{\"id\":\"bad\",\"title\":\"\",\"date\":\"not-a-date\"}]")
        assertEquals(0, imported)
        assertEquals(0, store.items().size)
    }

    @Test
    fun jsonRoundTrip() {
        val dir = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "secount-reload-${System.nanoTime()}").toString()
        val store = EventStore(dir)
        store.load()
        val tricky = EventItem()
        tricky.title = "Quote \"test\" \\ backslash\nnewline"
        tricky.date = LocalDate.of(2030, 5, 9)
        tricky.message = "msg <with> & symbols"
        tricky.secretEnabled = true
        tricky.secretMessage = "A private message ❤\nwith \"quotes\" and \\slashes"
        tricky.featured = true
        tricky.repeatYearly = false
        tricky.soundEnabled = false
        tricky.icon = "🚀"
        tricky.accentHex = "#7C6CFF"
        tricky.category = "App Release"
        tricky.replyMessage = "Thanks!"
        assertTrue(store.addOrUpdate(tricky))
        val second = EventItem().apply {
            title = "Second countdown"
            date = LocalDate.of(2031, 6, 10)
        }
        assertTrue(store.addOrUpdate(second))

        // A fresh store instance models closing and reopening the app.
        val reloaded = EventStore(dir).also { it.load() }
        val back = reloaded.byId(tricky.id)
        assertNotNull(back)
        assertEquals(2, reloaded.items().size)
        assertEquals("Second countdown", reloaded.byId(second.id)?.title)
        assertEquals(tricky.title, back.title)
        assertEquals(tricky.message, back.message)
        assertTrue(back.secretEnabled && back.secretMessage == tricky.secretMessage)
        assertEquals(tricky.date, back.date)
        assertTrue(back.featured && !back.repeatYearly && !back.soundEnabled)
        assertEquals("🚀", back.icon)
        assertEquals("#7C6CFF", back.accentHex)
        assertEquals("App Release", back.category)
        assertEquals("Thanks!", back.replyMessage)
    }

    @Test
    fun partnerCountdownIsHiddenUntilItsDueDate() {
        val receiverId = "receiver"
        val dDay = LocalDate.of(2030, 5, 9)
        val surprise = EventItem().apply {
            id = "partner-surprise"
            title = "Anniversary surprise"
            date = dDay
            senderId = "sender"
            forPartner = true
            secretMessage = "A secret note"
        }
        val personal = EventItem().apply {
            id = "personal-countdown"
            title = "Personal event"
            date = dDay.plusDays(1)
        }

        assertTrue(surprise.isHiddenUntilDue(receiverId, dDay.minusDays(1)))
        assertEquals(personal, nextVisibleCountdown(listOf(surprise, personal), receiverId, dDay.minusDays(1)))
        assertFalse(surprise.isHiddenUntilDue(receiverId, dDay))
        assertEquals(surprise, nextVisibleCountdown(listOf(surprise, personal), receiverId, dDay))
        assertTrue(surprise.isHiddenUntilDue(receiverId, dDay.plusDays(1)))
    }

    @Test
    fun eventArrayParserPreservesOneObjectAndRejectsMalformedSeparators() {
        val one = EventItem().apply { id = "one"; title = "One" }.toJson()
        assertEquals("one", parseEventArray("[$one]")?.single()?.id)
        assertEquals(null, parseEventArray("[$one,]"))
        assertEquals(emptyList(), parseEventArray("[]"))
    }

    @Test
    fun failedSaveDoesNotLeaveNewCountdownInMemory() {
        val blockedPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "secount-blocked-${System.nanoTime()}"
        FileSystem.SYSTEM.write(blockedPath) { writeUtf8("not a directory") }
        val store = EventStore((blockedPath / "store").toString()).also { it.load() }
        val item = EventItem().apply {
            id = "unsaved-countdown"
            title = "Must be saved"
            date = LocalDate.of(2030, 5, 9)
        }

        assertFalse(store.addOrUpdate(item))
        assertTrue(store.items().isEmpty())
        assertEquals(null, store.byId(item.id))
    }

    @Test
    fun loadRecoversCompletedPendingSave() {
        val base = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "secount-pending-${System.nanoTime()}").toString()
        FileSystem.SYSTEM.createDirectories(base.toPath())
        val current = EventItem().apply {
            id = "same-event"
            title = "Previous countdown"
            date = LocalDate.of(2030, 5, 9)
        }
        val pending = current.copyFromJson().apply {
            title = "New countdown"
            secretMessage = "Saved before restart"
            secretEnabled = true
        }
        FileSystem.SYSTEM.write((base + "/events.json").toPath()) {
            writeUtf8("[${current.toJson()}]\n")
        }
        FileSystem.SYSTEM.write((base + "/events.json.tmp").toPath()) {
            writeUtf8("[${pending.toJson()}]\n")
        }

        val store = EventStore(base).also { it.load() }
        val recovered = store.byId("same-event")
        assertNotNull(recovered)
        assertEquals("New countdown", recovered.title)
        assertEquals("Saved before restart", recovered.secretMessage)

        val restarted = EventStore(base).also { it.load() }
        assertEquals("Saved before restart", restarted.byId("same-event")?.secretMessage)
    }

    @Test
    fun loadRecoversBackupWhenPrimaryIsCorrupt() {
        val base = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "secount-backup-${System.nanoTime()}").toString()
        FileSystem.SYSTEM.createDirectories(base.toPath())
        val saved = EventItem().apply {
            id = "backup-event"
            title = "Recovered countdown"
            date = LocalDate.of(2031, 6, 10)
            secretMessage = "Backup secret"
            secretEnabled = true
        }
        FileSystem.SYSTEM.write((base + "/events.json").toPath()) { writeUtf8("not valid JSON") }
        FileSystem.SYSTEM.write((base + "/events.json.bak").toPath()) {
            writeUtf8("[${saved.toJson()}]\n")
        }

        val store = EventStore(base).also { it.load() }
        val recovered = store.byId("backup-event")
        assertNotNull(recovered)
        assertEquals("Recovered countdown", recovered.title)
        assertEquals("Backup secret", recovered.secretMessage)
        assertTrue(FileSystem.SYSTEM.exists((base + "/events.json").toPath()))
    }

    @Test
    fun legacyDefaults() {
        val legacy = EventItem.fromJson(
            "{\"id\":\"abc\",\"title\":\"Old\",\"date\":\"2031-01-02\",\"message\":\"hi\"," +
                "\"secretMessage\":\"\",\"secretEnabled\":false,\"featured\":false," +
                "\"repeatYearly\":true,\"soundEnabled\":true," +
                "\"createdAt\":\"2030-01-01T00:00:00\"}"
        )
        assertTrue(legacy.icon.isNotEmpty())
        assertEquals("Countdown", legacy.category)
    }

    @Test
    fun countdownMath() {
        val week = EventItem()
        week.repeatYearly = false
        week.date = LocalDate.now().plusDays(7)
        val text = week.countdownText(LocalDateTime.now())
        assertTrue(text.startsWith("06d ") || text.startsWith("07d "), text)
        assertFalse(week.isDueToday(LocalDate.now()))
        assertTrue(week.daysUntil(LocalDate.now()) in 6..7)

        val yearly = EventItem()
        yearly.repeatYearly = true
        yearly.date = LocalDate.of(2000, LocalDate.now().month, LocalDate.now().dayOfMonth)
        assertTrue(yearly.isDueToday(LocalDate.now()))
        // Due today: either already here, or counting down to today's hour.
        val yText = yearly.countdownText(LocalDateTime.now())
        assertTrue(yText.contains("HERE") || yText.contains("TODAY"), yText)
        assertEquals("Today!", yearly.shortCountdown(LocalDate.now()))
    }

    @Test
    fun featuredSortsFirst() {
        val store = tempStore()
        store.load()
        val a = EventItem()
        a.title = "Plain"
        a.date = LocalDate.now().plusDays(5)
        a.featured = false
        store.addOrUpdate(a)
        val b = EventItem()
        b.title = "Starred"
        b.date = LocalDate.now().plusDays(10)
        b.featured = true
        store.addOrUpdate(b)
        assertTrue(store.sortedByNext(LocalDate.now())[0].featured)
    }


    @Test
    fun recurringNextOccurrenceHandlesMonthEnd() {
        val e = EventItem()
        e.date = LocalDate.of(2026, 1, 31)
        e.setRepeat("monthly")
        assertEquals(LocalDate.of(2026, 2, 28), e.nextOccurrence(LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2026, 3, 31), e.nextOccurrence(LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun weeklyOccurrencePreservesWeekday() {
        val e = EventItem()
        e.date = LocalDate.of(2026, 1, 5)
        e.setRepeat("weekly")
        assertEquals(LocalDate.of(2026, 1, 12), e.nextOccurrence(LocalDate.of(2026, 1, 6)))
    }

    @Test
    fun countdownNeverUsesInvalidTime() {
        val e = EventItem()
        e.date = LocalDate.of(2030, 1, 1)
        e.hour = 99
        e.minute = -20
        val target = e.targetDateTime(LocalDate.of(2029, 12, 31))
        assertEquals(23, target.hour)
        assertEquals(0, target.minute)
    }

    @Test
    fun malformedJsonFallsBackToSafeDefaults() {
        val e = EventItem.fromJson(
            "{\"id\":\"x\",\"date\":\"not-a-date\",\"hour\":\"bad\",\"minute\":\"bad\"}"
        )
        assertEquals("x", e.id)
        assertTrue(e.hour in 0..23)
        assertTrue(e.minute in 0..59)
        assertTrue(e.category.isNotBlank())
        assertTrue(e.icon.isNotBlank())
    }

    @Test
    fun passwordHashing() {
        // pure function: 64-char hex, deterministic, distinct per input
        val h1 = PinLock.hash("1234")
        assertEquals(64, h1.length)
        assertEquals(h1, PinLock.hash("1234"))
        assertFalse(h1 == PinLock.hash("1235"))
        assertTrue(h1.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun storeSurvivesCorruptFile() {
        val base = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / ("secount-corrupt-" + System.nanoTime())).toString()
        FileSystem.SYSTEM.createDirectories(base.toPath())
        FileSystem.SYSTEM.write((base + "/events.json").toPath()) { writeUtf8("{{{{not json at all") }
        val store = EventStore(base)
        store.load()
        assertEquals(0, store.items().size)

        FileSystem.SYSTEM.write((base + "/events.json").toPath()) { writeUtf8("[{\"id\":\"a\",\"title\":\"Half\"") }
        store.load()
        assertEquals(0, store.items().size)
    }

    @Test
    fun duplicateImportMergesById() {
        val store = tempStore()
        store.load()
        val one = "{\"id\":\"dup1\",\"title\":\"First\",\"date\":\"2030-06-01\"}"
        assertEquals(1, store.importJson("[$one]"))
        val two = "{\"id\":\"dup1\",\"title\":\"Second\",\"date\":\"2030-06-01\"}"
        assertEquals(1, store.importJson("[$two]"))
        assertEquals(1, store.items().size)
        assertEquals("Second", store.byId("dup1")!!.title)
    }

    @Test
    fun largeCollectionStaysConsistent() {
        val store = tempStore()
        store.load()
        val today = LocalDate.now()
        for (i in 0 until 500) {
            val e = EventItem()
            e.title = "Bulk $i"
            e.date = today.plusDays((i % 365).toLong())
            store.addOrUpdate(e)
        }
        assertEquals(500, store.items().size)
        assertEquals(500, store.sortedByNext(today).size)
        val json = store.exportJson()
        val store2 = tempStore()
        store2.load()
        assertEquals(500, store2.importJson(json))
        assertEquals(500, store2.items().size)
    }

    @Test
    fun recurrenceAcrossDstBoundary() {
        // Date math is timezone-free: DST transitions must not shift occurrences.
        val e = EventItem()
        e.date = LocalDate.of(2026, 3, 8)
        e.setRepeat("yearly")
        assertEquals(LocalDate.of(2027, 3, 8), e.nextOccurrence(LocalDate.of(2026, 3, 9)))
        val m = EventItem()
        m.date = LocalDate.of(2026, 1, 31)
        m.setRepeat("monthly")
        assertEquals(LocalDate.of(2026, 2, 28), m.nextOccurrence(LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2026, 3, 31), m.nextOccurrence(LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun leapDayAnniversaryIsDueOnFeb28InNonLeapYears() {
        // Anniversary gift case: a Feb 29 date must still remind on Feb 28
        // when there is no Feb 29, matching nextOccurrence().
        val e = EventItem()
        e.date = LocalDate.of(2024, 2, 29)
        e.setRepeat("yearly")
        assertEquals(LocalDate.of(2025, 2, 28), e.nextOccurrence(LocalDate.of(2025, 2, 1)))
        assertTrue(e.isDueToday(LocalDate.of(2025, 2, 28)))
        assertEquals(0L, e.daysUntil(LocalDate.of(2025, 2, 28)))
        assertEquals("Today!", e.shortCountdown(LocalDate.of(2025, 2, 28)))
        assertFalse(e.isDueToday(LocalDate.of(2025, 2, 27)))
        // Leap year itself still fires on Feb 29.
        assertTrue(e.isDueToday(LocalDate.of(2024, 2, 29)))
    }
}
