package hu.rsc.shelflife.ocr

import java.time.LocalDate

/**
 * Egy a kamera altal latott kepkockan talalt lejarati datum-jelolt.
 *
 * @param ambiguousDayMonth true, ha a nap/honap sorrend elviekben felcserelheto lett volna
 *                          (pl. 04/05/26 -> lehet aprilis 5 vagy majus 4 is).
 * @param hasPositiveKeyword true, ha a sor kornyeken volt EXP/BBE/LEJARAT-szeru kulcsszo.
 */
data class DateCandidate(
    val date: LocalDate,
    val rawMatch: String,
    val ambiguousDayMonth: Boolean,
    val hasPositiveKeyword: Boolean
)

/**
 * Az OCR altal visszaadott nyers szovegsorokbol probal lejarati datumot kiszurni.
 * Mintaillesztes + hasznos/kizaro kulcsszavak + logikai ervenyessegi szurok.
 *
 * Tintasugaras / pontmatrix nyomatnal (pl. vodros tejfol fedele) az OCR tipikusan:
 *  - betunek olvas szamjegyet (0->O, 1->I/l, 5->S, 8->B ...),
 *  - a pontot vesszonek / kettospontnak vagy szokoznek latja, vagy elhagyja,
 *  - a datum egy sorba kerul a LOT/tetelszammal es az idoponttal,
 *  - kulcsszo nincs a fedelen (az a doboz oldalan van: "lasd a fedelen").
 * Ezeket a normalizeLine() + a lazabb mintak kezelik.
 */
object DateParser {
    // Megj.: a datum-mintak szelen \b helyett "nem szamjegy" hatart hasznalunk, mert a
    // mintas fedelen az OCR a hatter betuit hozzaragaszthatja (pl. "TEJ25 10.2026").


    private val POSITIVE_KEYWORDS = listOf(
        "EXP", "EXPIRY", "EXPIRES", "BBE", "BEST BEFORE", "BEST-BEFORE",
        "LEJARAT", "LEJÁRAT", "FOGYASZTHATO", "FOGYASZTHATÓ",
        "MINOSEGET", "MINŐSÉGÉT", "FOGY", "USE BY", "USE-BY", "MIN.",
        "BEST BY",
        // nemet: MHD = Mindesthaltbarkeitsdatum, "zu verbrauchen bis"
        "MHD", "MINDESTENS HALTBAR", "HALTBAR BIS", "VERBRAUCHEN BIS",
        // francia: DLC / DDM / DLUO, "a consommer (de preference) avant / jusqu'au"
        "DLC", "DDM", "DLUO", "CONSOMMER", "À CONSOMMER", "A CONSOMMER"
    )

    // Kizaro kulcsszo + az utana kovetkezo token (tetelszam, gyartasi datum) -- ezt
    // kivagjuk a sorbol, de a sor tobbi reszet (amiben a lejarat lehet) megtartjuk.
    private val NEGATIVE_TOKEN = Regex(
        """\b(?:LOT|GYARTVA|GYÁRTVA|GYÁRTÁS|GYARTAS|MFG|PROD|TETEL|TÉTEL|BATCH|CH\.?-?B|HERGESTELLT|CHARGE|LOS|FABRIQUE|FABRIQUÉ|EMB)\s*[:.]?\s*[A-Z0-9./\-]+"""
    )

    // "L:2781", "L 2781", "L2781" -- tetelszam
    private val LOT_SHORT = Regex("""\bL\s?[:.]?\s?\d[0-9A-Z]{2,}\b""")

    // Idopont (pl. 13:42, 08:05) -- sokszor a datum mellett van a fedelen, zavarna a mintakat.
    // A lookahead miatt a "25 12:10 2026" / "12:10.2026" tipusu datumot nem nyeli el.
    private val TIME = Regex("""\b([01]?\d|2[0-3])\s?:\s?[0-5]\d(\s?:\s?[0-5]\d)?\b(?!\s?[.\-/,:+÷·*]?\s?\d)""")

    private val MONTH_NAMES = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "MÁR" to 3, "APR" to 4, "ÁPR" to 4,
        "MAY" to 5, "MAJ" to 5, "MÁJ" to 5, "JUN" to 6, "JÚN" to 6,
        "JUL" to 7, "JÚL" to 7, "AUG" to 8, "SEP" to 9, "SZEPT" to 9, "SZE" to 9,
        "OCT" to 10, "OKT" to 10, "NOV" to 11, "DEC" to 12,
        // nemet
        "MÄR" to 3, "MAER" to 3, "MRZ" to 3, "MAI" to 5, "DEZ" to 12,
        // francia (JANV, MARS, SEPT, OCT, NOV mar lefedve a fenti elotagokkal;
        // a puszta "JUI" ketertelmu, ezert csak a JUIN/JUIL alak)
        "FÉV" to 2, "FEV" to 2, "AVR" to 4, "JUIN" to 6, "JUIL" to 7,
        "AOÛ" to 8, "AOU" to 8, "DÉC" to 12
    )

    // Datum-szeparator: . - / , : + ÷ · * -- a pontmatrix pontot / ketpontot az OCR
    // sokfele jelnek latja (vesszo, kettospont, osztasjel, plusz), vagy szokoznek.
    private const val SEP = """\s?[.\-/,:+÷·*]\s?"""
    private const val SEP_OR_SPACE = """(?:\s?[.\-/,:+÷·*]\s?|\s)"""

    // pl. 2026.09.11 / 2026-09-11 / 2026. 09. 11.
    private val YMD = Regex("""(?<!\d)(20\d{2})$SEP(0?[1-9]|1[0-2])$SEP(0?[1-9]|[12]\d|3[01])(?!\d)""")

    // pl. 11.09.2026
    // pl. 11.09.2026 / 25 10 2026 / 25÷10.2026 (Pilos tejfol fedele)
    private val DMY_FULL = Regex("""(?<!\d)(0?[1-9]|[12]\d|3[01])$SEP_OR_SPACE(0?[1-9]|1[0-2])$SEP_OR_SPACE(20\d{2})(?!\d)""")

    // pl. 11.09.26 / 11,09,26 / 11 09 26 (nap-honap-rovid ev)
    private val DMY_SHORT = Regex("""(?<!\d)(0?[1-9]|[12]\d|3[01])$SEP_OR_SPACE(0[1-9]|1[0-2]|[1-9])$SEP_OR_SPACE(\d{2})(?!\d)""")

    // pl. 110926 -- szeparator nelkul (a pontokat elnyelte az OCR). Csak kulcsszo mellett.
    private val DMY_COMPACT = Regex("""(?<!\d)(0[1-9]|[12]\d|3[01])(0[1-9]|1[0-2])(\d{2})(?!\d)""")

    // pl. 11 SEP 2026 / 11SZEPT26
    private val DAY_MONTHNAME_YEAR = Regex(
        """(?<!\d)(0?[1-9]|[12]\d|3[01])\s*[.\-]?\s*([A-ZÁÉÍÓÖŐÚÜŰÄÀÂÇÈÊËÎÏÔÛÙ]{3,6})\.?\s*(20\d{2}|\d{2})(?!\d)"""
    )

    private val DIGIT_LOOKALIKES = mapOf(
        'O' to '0', 'Q' to '0', 'D' to '0', 'Ö' to '0', 'Ó' to '0',
        'I' to '1', 'L' to '1', '|' to '1', '!' to '1', 'Í' to '1', 'J' to '1',
        'Z' to '2', 'S' to '5', 'B' to '8', 'G' to '6', 'T' to '7'
    )

    /**
     * @param today a "mai nap" az ervenyessegi idoablakhoz (tesztben rogzitheto).
     */
    fun findBestCandidate(lines: List<String>, today: LocalDate = LocalDate.now()): DateCandidate? {
        val candidates = mutableListOf<DateCandidate>()
        val upper = lines.map { it.uppercase() }
        for (index in upper.indices) {
            val raw = upper[index]
            val keywordNearby = POSITIVE_KEYWORDS.any { raw.contains(it) } ||
                (index > 0 && POSITIVE_KEYWORDS.any { upper[index - 1].contains(it) })

            val cleaned = normalizeLine(raw)
            extractFromLine(cleaned, keywordNearby, today)?.let { candidates.add(it) }
        }
        if (candidates.isEmpty()) {
            // A ferden / gorbe feluletre nyomott datumot az OCR ket sorra tordelheti
            // ("2026" es "25 10."): a szomszedos sorokat mindket sorrendben osszefuzve is probaljuk.
            for (i in 0 until upper.size - 1) {
                for (joined in listOf(upper[i] + " " + upper[i + 1], upper[i + 1] + " " + upper[i])) {
                    val kw = POSITIVE_KEYWORDS.any { joined.contains(it) }
                    extractFromLine(normalizeLine(joined), kw, today)?.let { candidates.add(it) }
                }
            }
        }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.hasPositiveKeyword } ?: candidates.first()
    }

    /** Visible for testing. */
    internal fun normalizeLine(upperLine: String): String {
        var s = upperLine
        s = NEGATIVE_TOKEN.replace(s, " ")
        s = LOT_SHORT.replace(s, " ")
        s = s.split(Regex("""\s+""")).joinToString(" ") { fixDigitToken(it) }
        s = TIME.replace(s, " ")
        return s.replace(Regex("""\s+"""), " ").trim()
    }

    /**
     * Egy "szamnak latszo" tokenben a hasonlo betuket szamjegyre csereli.
     * Csak akkor nyul hozza, ha a token mar most is tobbsegeben szamjegy, es nincs
     * benne 3+ betus szakasz (igy az "OKT", "SEP", "EXP" erintetlen marad).
     */
    private fun fixDigitToken(token: String): String {
        if (token.isEmpty()) return token
        if (Regex("""[A-ZÁÉÍÓÖŐÚÜŰÄÀÂÇÈÊËÎÏÔÛÙ]{3,}""").containsMatchIn(token)) return token
        val digits = token.count { it.isDigit() }
        val lookalikes = token.count { it in DIGIT_LOOKALIKES }
        if (digits == 0 || lookalikes == 0 || lookalikes > digits) return token
        // Csak akkor, ha a tokenben szamjegyen, hasonlo betun es datum-elvalasztón kivul nincs mas
        // (pl. "2S", "1O.2O26" igen; "L04N4" nem).
        if (token.any { !it.isDigit() && it !in DIGIT_LOOKALIKES && it !in ".-/,:+÷·*" }) return token
        return token.map { DIGIT_LOOKALIKES[it] ?: it }.joinToString("")
    }

    private fun extractFromLine(line: String, keywordNearby: Boolean, today: LocalDate): DateCandidate? {
        YMD.find(line)?.let { m ->
            val (y, mo, d) = m.destructured
            buildCandidate(y.toInt(), mo.toInt(), d.toInt(), m.value, false, keywordNearby, today)?.let { return it }
        }
        DMY_FULL.find(line)?.let { m ->
            val (d, mo, y) = m.destructured
            buildCandidate(y.toInt(), mo.toInt(), d.toInt(), m.value, isAmbiguous(d, mo), keywordNearby, today)
                ?.let { return it }
        }
        DAY_MONTHNAME_YEAR.find(line)?.let { m ->
            val (d, monName, y) = m.destructured
            val month = MONTH_NAMES.entries.firstOrNull { monName.startsWith(it.key) }?.value
            if (month != null) {
                buildCandidate(normalizeYear(y), month, d.toInt(), m.value, false, keywordNearby, today)?.let { return it }
            }
        }
        // Rovid ev: kulcsszo nelkul is elfogadjuk (a fedelen szinte sosem all kulcsszo),
        // de ilyenkor szukebb idoablakot engedunk (lasd buildCandidate), hogy egy
        // veletlen szamharmas ne jojjon at.
        for (m in DMY_SHORT.findAll(line)) {
            val (d, mo, y) = m.destructured
            buildCandidate(
                normalizeYear(y), mo.toInt(), d.toInt(), m.value, isAmbiguous(d, mo), keywordNearby, today,
                strict = !keywordNearby
            )?.let { return it }
        }
        if (keywordNearby) {
            DMY_COMPACT.find(line)?.let { m ->
                val (d, mo, y) = m.destructured
                buildCandidate(normalizeYear(y), mo.toInt(), d.toInt(), m.value, isAmbiguous(d, mo), true, today, strict = true)
                    ?.let { return it }
            }
        }
        return null
    }

    private fun isAmbiguous(d: String, mo: String): Boolean {
        val dayInt = d.toInt()
        val monthInt = mo.toInt()
        return dayInt <= 12 && monthInt <= 12 && dayInt != monthInt
    }

    private fun normalizeYear(y: String): Int {
        val yi = y.toInt()
        return if (yi < 100) 2000 + yi else yi
    }

    private fun buildCandidate(
        year: Int,
        month: Int,
        day: Int,
        raw: String,
        ambiguous: Boolean,
        keywordNearby: Boolean,
        today: LocalDate,
        strict: Boolean = false
    ): DateCandidate? {
        val date = try {
            LocalDate.of(year, month, day)
        } catch (e: Exception) {
            return null
        }
        // Szanity-check: lejarat ritkan van mar a multban, es tul messze a jovoben
        // (ilyenkor valoszinuleg vonalkod-toredeket vagy tetelszamot olvasott be tevesen).
        if (strict) {
            if (date.isBefore(today.minusDays(7))) return null
            if (date.isAfter(today.plusYears(3))) return null
        } else {
            if (date.isBefore(today.minusDays(30))) return null
            if (date.isAfter(today.plusYears(6))) return null
        }
        return DateCandidate(date, raw.trim(), ambiguous, keywordNearby)
    }
}
