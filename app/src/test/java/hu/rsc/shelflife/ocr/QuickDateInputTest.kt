package hu.rsc.shelflife.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** A szamjegyes gyors datumbevitel (QuickDateDialog) ertelmezesenek tesztjei. */
class QuickDateInputTest {

    private val today = LocalDate.of(2026, 9, 30)

    private fun parse(s: String, now: LocalDate = today) = QuickDateInput.parse(s, now)

    @Test
    fun fourDigits_dayMonth_thisYear() {
        assertEquals(LocalDate.of(2026, 10, 25), parse("2510"))
    }

    @Test
    fun sixDigits_withShortYear() {
        assertEquals(LocalDate.of(2026, 10, 25), parse("251026"))
    }

    @Test
    fun eightDigits_withFullYear() {
        assertEquals(LocalDate.of(2026, 10, 25), parse("25102026"))
    }

    @Test
    fun threeDigits_singleDigitDay() {
        assertEquals(LocalDate.of(2026, 10, 5), parse("510"))
    }

    @Test
    fun threeDigits_fallsBackToTwoDigitDay() {
        // "131" -> 1/31 ervenytelen -> 13/1 (januar 13, jovore, mert idén mar elmult)
        assertEquals(LocalDate.of(2027, 1, 13), parse("131"))
    }

    @Test
    fun separators() {
        assertEquals(LocalDate.of(2026, 10, 25), parse("25.10"))
        assertEquals(LocalDate.of(2026, 10, 25), parse("25 10 26"))
        assertEquals(LocalDate.of(2027, 1, 5), parse("5/1/2027"))
        assertEquals(LocalDate.of(2026, 10, 25), parse(" 25-10 "))
    }

    @Test
    fun withoutYear_passedMoreThanAWeekAgo_meansNextYear() {
        assertEquals(LocalDate.of(2027, 1, 5), parse("0501"))
        assertEquals(LocalDate.of(2027, 9, 20), parse("2009"))
    }

    @Test
    fun withoutYear_passedWithinAWeek_staysThisYear() {
        // Lejart termek rogzitese: 5 napja jart le -> idei datum, nem jovo evi.
        assertEquals(LocalDate.of(2026, 9, 25), parse("2509"))
    }

    @Test
    fun withoutYear_decemberInputForJanuary() {
        val december = LocalDate.of(2026, 12, 20)
        assertEquals(LocalDate.of(2027, 1, 5), parse("0501", december))
    }

    @Test
    fun invalidInputs() {
        assertNull(parse(""))
        assertNull(parse("abc"))
        assertNull(parse("12345"))
        assertNull(parse("1.2.3.4"))
        assertNull(parse("3102"))
        assertNull(parse("2510202"))
    }

    @Test
    fun leapDay() {
        assertEquals(LocalDate.of(2028, 2, 29), parse("290228"))
        // Ev nelkul: 2026 es 2027 sem szokoev -> nincs ervenyes datum.
        assertNull(parse("2902"))
    }
}
