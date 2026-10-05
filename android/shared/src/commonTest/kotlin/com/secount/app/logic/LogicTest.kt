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
    fun jsonRoundTrip() {
        val store = tempStore()
        store.load()
        val tricky = EventItem()
        tricky.title = "Quote \"test\" \\ backslash\nnewline"
        tricky.date = LocalDate.of(2030, 5, 9)
        tricky.message = "msg <with> & symbols"
        tricky.secretEnabled = true
        tricky.secretMessage = "A private message ❤"
        tricky.featured = true
        tricky.repeatYearly = false
        tricky.soundEnabled = false
        tricky.icon = "🚀"
        tricky.accentHex = "#7C6CFF"
        tricky.category = "App Release"
        tricky.replyMessage = "Thanks!"
        store.addOrUpdate(tricky)

        val back = store.byId(tricky.id)
        assertNotNull(back)
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
}
