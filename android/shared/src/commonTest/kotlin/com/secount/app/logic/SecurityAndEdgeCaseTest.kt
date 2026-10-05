package com.secount.app.logic

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurityAndEdgeCaseTest {
    @Test fun pairCryptoRoundTripUsesAuthenticatedEnvelope() {
        val key = PairCrypto.deriveKey("ABCDEF", "UVWXYZ")
        val cipher = PairCrypto.encryptToHex(key, """{"message":"private"}""")
        assertTrue(cipher.startsWith("ENC2."))
        assertEquals("""{"message":"private"}""", PairCrypto.decryptHex(key, cipher))
    }

    @Test fun pairCryptoRejectsTamperedCiphertext() {
        val key = PairCrypto.deriveKey("ABCDEF", "UVWXYZ")
        val cipher = PairCrypto.encryptToHex(key, "hello")
        val parts = cipher.split('.')
        val body = parts[2]
        val changed = if (body.first() == '0') "1" + body.drop(1) else "0" + body.drop(1)
        val tampered = parts[0] + "." + parts[1] + "." + changed + "." + parts[3]
        assertNull(PairCrypto.decryptHex(key, tampered))
    }

    @Test fun pairCryptoRejectsWrongKey() {
        val cipher = PairCrypto.encryptToHex(PairCrypto.deriveKey("ABCDEF", "UVWXYZ"), "hello")
        assertNull(PairCrypto.decryptHex(PairCrypto.deriveKey("ABCDEF", "QRSTUV"), cipher))
    }

    @Test fun backupCryptoRoundTripUsesAuthenticatedEnvelope() {
        val cipher = BackupCrypto.encrypt("stronger-test-password", """[{"title":"Secret"}]""")
        assertTrue(cipher.startsWith("ENC2."))
        assertEquals("""[{"title":"Secret"}]""", BackupCrypto.decrypt("stronger-test-password", cipher))
        assertNull(BackupCrypto.decrypt("wrong-password", cipher))
    }

    @Test fun eventItemHandlesDateRolloverAndExpiredOneTimeEvents() {
        val item = EventItem()
        item.repeatMode = "once"
        item.repeatYearly = false
        item.date = LocalDate.of(2024, 2, 29)
        item.hour = 23
        item.minute = 59
        assertEquals(LocalDate.of(2024, 2, 29), item.nextOccurrence(LocalDate.of(2024, 2, 28)))
        assertTrue(item.isPast(LocalDate.of(2024, 3, 1)))
        assertTrue(item.countdownText(LocalDateTime.of(2024, 2, 29, 23, 59, 1)).contains("HERE"))
    }

    @Test fun eventItemClampsInvalidTimeAndUsesSafeDefaults() {
        val item = EventItem()
        item.hour = 99
        item.minute = -4
        val restored = EventItem.fromJson(item.toJson())
        assertEquals(23, restored.hour)
        assertEquals(0, restored.minute)
        assertNotNull(restored.displayIcon())
        assertNotNull(restored.displayCategory())
    }

    @Test fun eventItemMonthlyOccurrenceHandlesShortMonths() {
        val item = EventItem()
        item.repeatMode = "monthly"
        item.repeatYearly = true
        item.date = LocalDate.of(2024, 1, 31)
        assertEquals(LocalDate.of(2024, 2, 29), item.nextOccurrence(LocalDate.of(2024, 2, 1)))
        assertEquals(LocalDate.of(2024, 4, 30), item.nextOccurrence(LocalDate.of(2024, 4, 1)))
    }

    @Test fun emptySecretIsNeverConsideredSecret() {
        val item = EventItem()
        item.secretMessage = "   "
        item.secretEnabled = true
        assertFalse(item.hasSecret())
    }
}