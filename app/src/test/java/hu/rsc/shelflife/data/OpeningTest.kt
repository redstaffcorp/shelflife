package hu.rsc.shelflife.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OpeningTest {

    private val today = LocalDate.of(2026, 11, 21)

    private fun item(expiry: LocalDate) = PantryItem(
        id = 1L,
        barcode = null,
        productName = "Tej",
        expiry = expiry,
        recordedAt = today.minusDays(2),
        quantity = 1,
        quantityUnit = "darab",
        enteredManually = true,
        ambiguousDayMonth = false,
        notifiedForExpiry = expiry
    )

    @Test
    fun openingShortensExpiry() {
        val opened = item(today.plusDays(10)).opened(today, 3)
        assertEquals(today.plusDays(3), opened.expiry)
        assertEquals(today.plusDays(10), opened.expiryBeforeOpening)
        assertTrue(opened.isOpened)
        // Uj lejarat -> ujra kuldheto ertesites.
        assertNull(opened.notifiedForExpiry)
    }

    @Test
    fun packageDateWinsWhenEarlier() {
        val opened = item(today.plusDays(2)).opened(today, 5)
        assertEquals(today.plusDays(2), opened.expiry)
    }

    @Test
    fun reOpeningRecomputesFromPackageDate() {
        val first = item(today.plusDays(10)).opened(today, 3)
        val changed = first.opened(today, 7)
        assertEquals(today.plusDays(7), changed.expiry)
        assertEquals(today.plusDays(10), changed.expiryBeforeOpening)
    }

    @Test
    fun unopenRestoresPackageDate() {
        val back = item(today.plusDays(10)).opened(today, 3).unopened()
        assertEquals(today.plusDays(10), back.expiry)
        assertFalse(back.isOpened)
        assertNull(back.expiryBeforeOpening)
    }

    @Test
    fun countUnits() {
        assertTrue(isCountUnit("darab"))
        assertTrue(isCountUnit("üveg"))
        assertTrue(isCountUnit("Stück"))
        assertFalse(isCountUnit("g"))
        assertFalse(isCountUnit(" L "))
        assertFalse(isCountUnit("dkg"))
    }
}
