package hu.rsc.shelflife.data

import java.time.LocalDate

/**
 * Egy kamraban/hutoben levo tetel. Perzisztalva a PantryItemStore-on
 * keresztul, hogy a lejarati ertesitesek akkor is mukodjenek, ha az app
 * epp nincs megnyitva.
 */
data class PantryItem(
    val id: Long,
    // Null, ha a tetel teljesen kezi uton, vonalkod-szkenneles nelkul jott
    // letre -- ekkor nincs mit a helyi cache-be menteni.
    val barcode: String?,
    val productName: String,
    val expiry: LocalDate,
    // Automatikusan toltott, amikor a tetel eloszor elmentesre kerul -- a
    // felhasznalo nem allithatja at, ez mindig a "most" pillanata.
    val recordedAt: LocalDate,
    // Opcionalis, csak szam -- szandekosan nincs mertekegyseg.
    val quantity: Int?,
    val enteredManually: Boolean,
    val ambiguousDayMonth: Boolean,
    // Melyik lejarati datumra kuldtunk mar ertesitest -- ha a felhasznalo
    // modositja a lejaratot, ez ervenyet veszti, es az app ujra kuldhet.
    val notifiedForExpiry: LocalDate? = null
)
