package hu.rsc.shelflife.notify

import hu.rsc.shelflife.data.ItemStatus
import hu.rsc.shelflife.data.PantryItem
import java.time.LocalDate

/**
 * Heti pontositas-emlekezteto logikaja (tiszta fuggvenyek, unit-tesztelheto).
 *
 * A becsult lejarat ("Kesobb", "~1 het", gyorsracs) csak akkor ertekes, ha a
 * felhasznalo idonkent visszater es a csomagolasrol beirja a valodit. Ezert
 * hetente legfeljebb egyszer szolunk, ha van mit pontositani.
 */
object RefineReminderPolicy {

    /** Legalabb ennyi nap teljen el ket emlekezteto kozott. */
    const val INTERVAL_DAYS = 7L

    /**
     * Ennyi napig "turelmi ido": a most felvett tetelt ne kerjuk rogton
     * pontositani (lehet, hogy epp most pakol ki).
     */
    const val GRACE_DAYS = 2L

    /**
     * A pontositasra erdemes tetelek: aktiv, becsult lejaratu, legalabb
     * GRACE_DAYS napja felvett, es meg nem jart le (a lejartat mar nem
     * pontositani kell, hanem lezarni -- arrol a lejarati ertesites szol).
     */
    fun itemsToRefine(items: List<PantryItem>, today: LocalDate): List<PantryItem> =
        items.filter {
            it.status == ItemStatus.ACTIVE &&
                it.expiryEstimated &&
                !it.recordedAt.isAfter(today.minusDays(GRACE_DAYS)) &&
                !it.expiry.isBefore(today)
        }

    /** Kell-e ma emlekeztetot kuldeni. */
    fun shouldSend(refineCount: Int, lastSentEpochDay: Long?, today: LocalDate): Boolean {
        if (refineCount <= 0) return false
        if (lastSentEpochDay == null) return true
        return today.toEpochDay() - lastSentEpochDay >= INTERVAL_DAYS
    }
}
