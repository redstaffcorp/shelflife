package hu.rsc.shelflife.notify

import hu.rsc.shelflife.data.ItemStatus
import hu.rsc.shelflife.data.PantryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RefineReminderPolicyTest {

    private val today = LocalDate.of(2026, 9, 30)
    private var nextId = 1L

    private fun item(
        estimated: Boolean = true,
        recordedDaysAgo: Long = 3,
        expiresInDays: Long = 5,
        status: ItemStatus = ItemStatus.ACTIVE
    ) = PantryItem(
        id = nextId++, barcode = null, productName = "Kenyér",
        expiry = today.plusDays(expiresInDays), recordedAt = today.minusDays(recordedDaysAgo),
        quantity = 1, quantityUnit = "darab", enteredManually = true, ambiguousDayMonth = false,
        expiryEstimated = estimated, status = status
    )

    @Test
    fun onlyActiveEstimatedOlderThanGraceAndNotExpired() {
        val keep = item()
        val items = listOf(
            keep,
            item(estimated = false),                   // valodi datum
            item(recordedDaysAgo = 1),                 // most vettuk fel (turelmi ido)
            item(expiresInDays = -1),                  // mar lejart -> lezarni kell, nem pontositani
            item(status = ItemStatus.CONSUMED),        // lezart
        )
        assertEquals(listOf(keep), RefineReminderPolicy.itemsToRefine(items, today))
    }

    @Test
    fun graceBoundaryAndExpiringToday() {
        val exactlyTwoDays = item(recordedDaysAgo = 2)
        val expiresToday = item(expiresInDays = 0)
        assertEquals(
            listOf(exactlyTwoDays, expiresToday),
            RefineReminderPolicy.itemsToRefine(listOf(exactlyTwoDays, expiresToday), today)
        )
    }

    @Test
    fun shouldSend_nothingToRefine() {
        assertFalse(RefineReminderPolicy.shouldSend(0, null, today))
    }

    @Test
    fun shouldSend_firstTime() {
        assertTrue(RefineReminderPolicy.shouldSend(3, null, today))
    }

    @Test
    fun shouldSend_atMostWeekly() {
        val sixDaysAgo = today.minusDays(6).toEpochDay()
        val sevenDaysAgo = today.minusDays(7).toEpochDay()
        assertFalse(RefineReminderPolicy.shouldSend(3, sixDaysAgo, today))
        assertTrue(RefineReminderPolicy.shouldSend(3, sevenDaysAgo, today))
        assertFalse(RefineReminderPolicy.shouldSend(3, today.toEpochDay(), today))
    }
}
