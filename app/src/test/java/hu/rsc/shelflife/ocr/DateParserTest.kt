package hu.rsc.shelflife.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Regresszios tesztkeszlet a kamera-OCR datumertelmezojehez. A bemenetek az
 * ML Kit altal visszaadott nyers szovegsorokat utanozzak (nagybetus/kisbetus
 * vegyesen, OCR-hibakkal). Uj, valos csomagolason talalt hibanal ide erdemes
 * felvenni a nyers sorokat (az OCR-diagnosztika logjabol), es csak utana
 * javitani a DateParsert.
 *
 * A "ma" rogzitett, igy a tesztek nem avulnak el.
 */
class DateParserTest {

    private val today = LocalDate.of(2026, 9, 30)

    private fun parse(vararg lines: String) = DateParser.findBestCandidate(lines.toList(), today)

    private fun assertDate(expected: LocalDate, vararg lines: String) {
        val c = parse(*lines)
        assertNotNull("nincs talalat erre: ${lines.toList()}", c)
        assertEquals("sorok: ${lines.toList()}", expected, c!!.date)
    }

    // -- Alapformatumok --

    @Test
    fun dmyFull_withHungarianKeyword() {
        val c = parse("MINŐSÉGÉT MEGŐRZI: 25.10.2026")!!
        assertEquals(LocalDate.of(2026, 10, 25), c.date)
        assertTrue(c.hasPositiveKeyword)
        assertFalse("25 > 12, nem lehet felcserelni", c.ambiguousDayMonth)
    }

    @Test
    fun dmyFull_lowercaseInput() {
        assertDate(LocalDate.of(2026, 10, 25), "minőségét megőrzi: 25.10.2026")
    }

    @Test
    fun ymd_withDashes() {
        assertDate(LocalDate.of(2027, 3, 15), "EXP 2027-03-15")
    }

    @Test
    fun ymd_hungarianStyleWithSpaces() {
        assertDate(LocalDate.of(2026, 11, 5), "Fogyasztható: 2026. 11. 05.")
    }

    @Test
    fun ymd_isNeverAmbiguous() {
        assertFalse(parse("2027.03.04")!!.ambiguousDayMonth)
    }

    @Test
    fun dmyShort_twoDigitYear() {
        assertDate(LocalDate.of(2026, 10, 11), "11.10.26")
    }

    @Test
    fun dayMonthName_english() {
        assertDate(LocalDate.of(2027, 9, 11), "BEST BEFORE 11 SEP 2027")
    }

    @Test
    fun dayMonthName_hungarian() {
        assertDate(LocalDate.of(2026, 10, 11), "11 OKT 2026")
    }

    @Test
    fun dayMonthName_shortYearNoSpaces() {
        assertDate(LocalDate.of(2026, 12, 3), "EXP 03DEC26")
    }

    // -- Pontmatrix / tintasugaras nyomat tipikus OCR-hibai --

    @Test
    fun pilosLid_spaceAsSeparator_lotAndTimeOnNextLine() {
        // Pilos tejfol fedele (lasd OCR-vizsgalat doksi): kulcsszo nincs.
        assertDate(LocalDate.of(2026, 10, 25), "25 10.2026", "L04N4 00:48")
    }

    @Test
    fun divisionSignAsSeparator() {
        assertDate(LocalDate.of(2026, 10, 25), "25÷10.2026")
    }

    @Test
    fun commaAndColonSeparators() {
        assertDate(LocalDate.of(2026, 10, 25), "25,10:2026")
    }

    @Test
    fun letterLookalikesFixedToDigits() {
        // S->5, O->0
        assertDate(LocalDate.of(2026, 10, 25), "2S.1O.2O26")
    }

    @Test
    fun monthNameNotDestroyedByLookalikeFix() {
        // Az "OKT"-bol nem lehet "0K7" (3+ betus szakaszhoz nem nyulunk).
        assertEquals("11 OKT 2026", DateParser.normalizeLine("11 OKT 2026"))
    }

    @Test
    fun backgroundTextGluedToDate() {
        // A mintas fedelen az OCR a hatter betuit a datumhoz ragaszthatja.
        assertDate(LocalDate.of(2026, 10, 25), "TEJ25 10.2026")
    }

    @Test
    fun dateSplitIntoTwoLines() {
        // Gorbe feluleten az OCR ket sorra tordelheti, akar forditott sorrendben.
        assertDate(LocalDate.of(2026, 10, 25), "2026", "25 10.")
    }

    @Test
    fun timeNextToDateIsRemoved() {
        assertDate(LocalDate.of(2026, 10, 11), "11.10.26 13:42")
    }

    @Test
    fun compactWithoutSeparators_onlyWithKeyword() {
        assertDate(LocalDate.of(2027, 1, 11), "EXP 110127")
        assertNull("kulcsszo nelkul egy 6 jegyu szam nem datum", parse("110127"))
    }

    // -- Kizaro kulcsszavak (tetelszam, gyartasi datum) --

    @Test
    fun lotTokenRemoved_expiryOnSameLineKept() {
        val c = parse("LOT 12.10.26 EXP 05.03.27")!!
        assertEquals(LocalDate.of(2027, 3, 5), c.date)
        assertTrue(c.ambiguousDayMonth)
    }

    @Test
    fun productionDateIgnored_expiryOnNextLine() {
        assertDate(
            LocalDate.of(2027, 3, 1),
            "GYÁRTVA: 01.09.2026",
            "MIN. MEGŐRZI: 01.03.2027"
        )
    }

    @Test
    fun shortLotNumberDoesNotBecomeDate() {
        assertNull(parse("L 2781 12"))
    }

    @Test
    fun keywordCandidatePreferredOverPlainDate() {
        assertDate(LocalDate.of(2026, 11, 20), "12.10.2026", "EXP 20.11.2026")
    }

    @Test
    fun keywordOnPreviousLineCounts() {
        val c = parse("Minőségét megőrzi:", "20.11.2026")!!
        assertTrue(c.hasPositiveKeyword)
    }

    // -- Nap/honap felcserelhetoseg --

    @Test
    fun ambiguousWhenBothPartsAreValidMonths() {
        assertTrue(parse("05.04.2027")!!.ambiguousDayMonth)
    }

    @Test
    fun notAmbiguousWhenDayEqualsMonth() {
        assertFalse(parse("05.05.2027")!!.ambiguousDayMonth)
    }

    // -- Ervenyessegi idoablak --

    @Test
    fun longExpiredDateRejected() {
        assertNull(parse("01.01.2026"))
    }

    @Test
    fun recentlyExpiredDateAccepted() {
        // A mar lejart (de 30 napon beluli) termeket is rogzitheti a felhasznalo.
        assertDate(LocalDate.of(2026, 9, 20), "20.09.2026")
    }

    @Test
    fun tooFarFutureRejected() {
        assertNull(parse("01.01.2040"))
    }

    @Test
    fun shortYearWithoutKeyword_usesStricterWindow() {
        // 4 even tuli rovid evszam kulcsszo nelkul gyanus (tetelszam-toredek) ...
        assertNull(parse("11.10.30"))
        // ... kulcsszoval viszont elfogadhato (pl. konzerv).
        assertDate(LocalDate.of(2030, 10, 11), "EXP 11.10.30")
    }

    @Test
    fun invalidCalendarDateRejected() {
        assertNull(parse("31.02.2027"))
    }

    // -- Nem datum szovegek (tomeg, ar, tapertek) --

    @Test
    fun nonDateTextsGiveNoCandidate() {
        assertNull(parse("NETTÓ 500 G", "1299 FT"))
        assertNull(parse("ENERGIA 1520 KJ / 363 KCAL"))
        assertNull(parse("ZSÍR 20%", "NUTRI-SCORE"))
        assertNull(parse("5 998765 432101"))
    }

    // -- Nemet es francia csomagolasok --

    @Test
    fun german_mhdKeyword() {
        val c = parse("MHD: 25.10.2026")!!
        assertEquals(LocalDate.of(2026, 10, 25), c.date)
        assertTrue(c.hasPositiveKeyword)
    }

    @Test
    fun german_mindestensHaltbarOnPreviousLine() {
        val c = parse("Mindestens haltbar bis:", "05.04.27")!!
        assertEquals(LocalDate.of(2027, 4, 5), c.date)
        assertTrue(c.hasPositiveKeyword)
    }

    @Test
    fun german_monthNames() {
        assertDate(LocalDate.of(2027, 3, 15), "15. MÄRZ 2027")
        assertDate(LocalDate.of(2027, 5, 1), "01 MAI 2027")
        assertDate(LocalDate.of(2026, 12, 24), "24 DEZ 26")
    }

    @Test
    fun german_chargeTokenRemoved() {
        assertDate(LocalDate.of(2027, 1, 20), "CHARGE 12.10.26 MHD 20.01.2027")
    }

    @Test
    fun french_keywords() {
        assertTrue(parse("À consommer de préférence avant le 25/10/2026")!!.hasPositiveKeyword)
        assertTrue(parse("DLC 03/11/26")!!.hasPositiveKeyword)
        assertTrue(parse("DDM : 01/2027 12/03/2027")!!.hasPositiveKeyword)
    }

    @Test
    fun french_monthNames() {
        assertDate(LocalDate.of(2027, 6, 12), "12 JUIN 2027")
        assertDate(LocalDate.of(2027, 7, 3), "03 JUIL. 2027")
        assertDate(LocalDate.of(2027, 8, 15), "15 AOÛT 2027")
        assertDate(LocalDate.of(2027, 2, 8), "08 FÉV 2027")
        assertDate(LocalDate.of(2026, 12, 2), "02 DÉC 2026")
        assertDate(LocalDate.of(2027, 4, 1), "01 AVR 27")
    }

    @Test
    fun french_packerCodeRemoved() {
        assertDate(LocalDate.of(2026, 11, 3), "EMB 29123 DLC 03/11/2026")
    }

    @Test
    fun emptyInput() {
        assertNull(parse())
        assertNull(parse("", "   "))
    }
}
