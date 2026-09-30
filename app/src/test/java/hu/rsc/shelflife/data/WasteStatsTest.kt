package hu.rsc.shelflife.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class WasteStatsTest {

    private val today = LocalDate.of(2026, 9, 30)
    private var nextId = 1L

    private fun item(
        name: String = "Joghurt",
        expiry: LocalDate = today.plusDays(10),
        status: ItemStatus = ItemStatus.ACTIVE,
        finishedAt: LocalDate? = null
    ) = PantryItem(
        id = nextId++, barcode = null, productName = name, expiry = expiry,
        recordedAt = today.minusDays(20), quantity = 1, quantityUnit = "darab",
        enteredManually = true, ambiguousDayMonth = false,
        status = status, finishedAt = finishedAt
    )

    private fun consumed(daysAgo: Long, expiry: LocalDate = today.plusDays(10), name: String = "Joghurt") =
        item(name, expiry, ItemStatus.CONSUMED, today.minusDays(daysAgo))

    private fun wasted(daysAgo: Long, name: String = "Kenyér") =
        item(name, today.minusDays(daysAgo + 1), ItemStatus.WASTED, today.minusDays(daysAgo))

    @Test
    fun empty_hasNoHistoryAndNoRate() {
        val s = WasteStats.compute(emptyList(), emptyList(), StatsPeriod.LAST_30_DAYS, today)
        assertFalse(s.hasAnyHistory)
        assertNull(s.saveRatePercent)
        assertNull(s.daysSinceLastWaste)
        assertEquals(6, s.months.size)
    }

    @Test
    fun rate_isRoundedDown_soHundredOnlyWhenNothingWasted() {
        val finished = List(199) { consumed(1) } + wasted(2)
        val s = WasteStats.compute(finished, emptyList(), StatsPeriod.LAST_30_DAYS, today)
        assertEquals(99, s.saveRatePercent)
        assertEquals(199, s.saved)
        assertEquals(1, s.wasted)
    }

    @Test
    fun period_last30_excludesOlder_allTimeIncludes() {
        val finished = listOf(consumed(0), consumed(29), consumed(30), wasted(45))
        val last30 = WasteStats.compute(finished, emptyList(), StatsPeriod.LAST_30_DAYS, today)
        assertEquals(2, last30.saved)
        assertEquals(0, last30.wasted)
        val all = WasteStats.compute(finished, emptyList(), StatsPeriod.ALL_TIME, today)
        assertEquals(3, all.saved)
        assertEquals(1, all.wasted)
        assertNull(all.previousRatePercent)
    }

    @Test
    fun improvement_onlyWhenBetterThanPreviousPeriod() {
        // Elozo 30 nap: 1 elfogyott, 1 kidobva (50%); most: 3 elfogyott (100%).
        val finished = listOf(consumed(40), wasted(35), consumed(1), consumed(2), consumed(3))
        val s = WasteStats.compute(finished, emptyList(), StatsPeriod.LAST_30_DAYS, today)
        assertEquals(50, s.previousRatePercent)
        assertEquals(50, s.improvementPoints)

        val worse = listOf(consumed(40), wasted(1), consumed(2))
        assertNull(WasteStats.compute(worse, emptyList(), StatsPeriod.LAST_30_DAYS, today).improvementPoints)
    }

    @Test
    fun rescued_countsConsumptionInLastThreeDaysOrLater() {
        val finished = listOf(
            consumed(0, expiry = today.plusDays(2)),   // lejarat elotti 3. nap -> megmentve
            consumed(0, expiry = today.plusDays(3)),   // meg 4 nap volt hatra -> nem
            consumed(0, expiry = today),               // lejarat napjan -> megmentve
            consumed(0, expiry = today.minusDays(2))   // MMI utan -> megmentve
        )
        val s = WasteStats.compute(finished, emptyList(), StatsPeriod.LAST_30_DAYS, today)
        assertEquals(3, s.rescued)
    }

    @Test
    fun streak_daysSinceLastWaste() {
        val s = WasteStats.compute(listOf(wasted(12), wasted(5), consumed(1)), emptyList(), StatsPeriod.ALL_TIME, today)
        assertEquals(5L, s.daysSinceLastWaste)
    }

    @Test
    fun frequentlyWasted_groupsCaseInsensitive_minTwo_last90Days() {
        val finished = listOf(
            wasted(1, "Kenyér"), wasted(10, "kenyér "), wasted(20, "KENYÉR"),
            wasted(3, "Tej"), wasted(100, "Tej"),   // a 100 napos kiesik -> csak 1
            wasted(4, "Saláta"), wasted(8, "saláta")
        )
        val s = WasteStats.compute(finished, emptyList(), StatsPeriod.ALL_TIME, today)
        assertEquals(listOf(WastedProduct("Kenyér", 3), WastedProduct("Saláta", 2)), s.frequentlyWasted)
    }

    @Test
    fun months_bucketByFinishDate() {
        val finished = listOf(consumed(0), consumed(29), wasted(31))  // szept 30, szept 1, aug 30
        val s = WasteStats.compute(finished, emptyList(), StatsPeriod.ALL_TIME, today)
        assertEquals(YearMonth.of(2026, 4), s.months.first().month)
        assertEquals(MonthBar(YearMonth.of(2026, 9), 2, 0), s.months.last())
        assertEquals(MonthBar(YearMonth.of(2026, 8), 0, 1), s.months[4])
        assertTrue(s.hasAnyHistory)
    }

    @Test
    fun expiringSoon_includesOverdueAndThreeDays() {
        val active = listOf(
            item(expiry = today.minusDays(1)), item(expiry = today),
            item(expiry = today.plusDays(3)), item(expiry = today.plusDays(4))
        )
        assertEquals(3, WasteStats.compute(emptyList(), active, StatsPeriod.LAST_30_DAYS, today).expiringSoon)
    }
}
