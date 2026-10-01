package hu.rsc.shelflife.ui

import android.content.res.Resources
import hu.rsc.shelflife.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/**
 * Rovid datum (pl. "2026. 10. 25."), az aktualis nyelv szerint. Getter, nem
 * gyorsitotarazott ertek: az app nyelve futas kozben valtozhat (Android 13+
 * alkalmazasonkenti nyelv), a formatter pedig letrehozaskor rogziti a locale-t.
 */
internal val shortDateFormatter: DateTimeFormatter
    get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)

/** Hosszu datum (pl. "2026. oktober 25."), az aktualis nyelv szerint. */
internal val longDateFormatter: DateTimeFormatter
    get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)

/** Elvalaszto a tetelkartya informacios soraban. */
internal const val INFO_SEPARATOR = "  ·  "

fun daysUntil(expiry: LocalDate, today: LocalDate = LocalDate.now()): Long =
    ChronoUnit.DAYS.between(today, expiry)

/** "Lejárt" / "Ma jár le" / "Holnap jár le" / "N nap múlva jár le". */
fun Resources.expiryLabel(expiry: LocalDate, today: LocalDate = LocalDate.now()): String {
    val daysLeft = daysUntil(expiry, today)
    return when {
        daysLeft < 0 -> getString(R.string.expiry_overdue)
        daysLeft == 0L -> getString(R.string.expiry_today)
        daysLeft == 1L -> getString(R.string.expiry_tomorrow)
        else -> getQuantityString(R.plurals.expiry_in_days, daysLeft.toInt(), daysLeft.toInt())
    }
}
