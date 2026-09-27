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
    // Opcionalis, csak szam. Alapertelmezetten 1 (lasd ScannerScreen), de a
    // felhasznalo torolheti/csokkentheti "nincs megadva" (null) allapotig.
    val quantity: Int?,
    // Mennyisegi mertekegyseg -- alapertelmezetten "darab". Ismert termeknel
    // (lasd ProductStore.KnownProduct) az utoljara hasznalt mertekegyseg
    // ujra felajanlodik legkozelebb ugyanahhoz a termeknevhez.
    val quantityUnit: String = ProductStore.DEFAULT_UNIT,
    val enteredManually: Boolean,
    val ambiguousDayMonth: Boolean,
    // Igaz, ha a lejarat nem a csomagolasrol lett leolvasva/beirva, hanem
    // becsles ("~1 het", "Kesobb" gomb). A listaban "~ becsult" jelolest kap,
    // es a "Becsult" szurovel kesobb egy helyen pontosithato. Becsult datumbol
    // az app nem tanul eltarthatosagot (lasd ProductStore.shelfLifeDays).
    val expiryEstimated: Boolean = false,
    // Melyik lejarati datumra kuldtunk mar ertesitest -- ha a felhasznalo
    // modositja a lejaratot, ez ervenyet veszti, es az app ujra kuldhet.
    val notifiedForExpiry: LocalDate? = null
)
