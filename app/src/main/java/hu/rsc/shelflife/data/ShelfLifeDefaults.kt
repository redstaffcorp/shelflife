package hu.rsc.shelflife.data

/**
 * Vonalkod nelkuli (gyorsracs) termekkategoriak becsult eltarthatosaga
 * TAROLASI HELYENKENT, a rogzites napjatol szamitva.
 *
 * Alapelv (lasd roadmap): a lejaratot soha nem tanuljuk korabbi vasarlasokbol.
 * Ezek fix, ovatos (inkabb rovidebb) altalanos ertekek -- a tetel mindig
 * "becsult" jelolest kap, es a heti pontositas-emlekezteto visszahozza.
 * Szandekosan az also becsles: ha tul korai, csak hamarabb szolunk; ha tul
 * keso lenne, pont a lenyeget (az idoben szolast) rontanank el.
 *
 * Nem fagyaszthato termeknel (tojas heja-ban, salata) a fagyaszto erteke a
 * hutoevel egyezik -- nem igerunk tobbet.
 * Romlando alapanyagnal (hus, hal, maradek) a kamra 0 nap = "ma jar le":
 * szoba-homersekleten aznap fel kell hasznalni.
 */
enum class FoodCategory(
    /** A termek szokasos helye (ha a felhasznalo nem valaszt mast). */
    val homeLocation: StorageLocation,
    private val fridgeDays: Int,
    private val freezerDays: Int,
    private val pantryDays: Int
) {
    BREAD(StorageLocation.PANTRY, fridgeDays = 5, freezerDays = 90, pantryDays = 3),
    PASTRY(StorageLocation.PANTRY, fridgeDays = 3, freezerDays = 60, pantryDays = 2),
    EGGS(StorageLocation.FRIDGE, fridgeDays = 21, freezerDays = 21, pantryDays = 14),
    MEAT(StorageLocation.FRIDGE, fridgeDays = 2, freezerDays = 120, pantryDays = 0),
    POULTRY(StorageLocation.FRIDGE, fridgeDays = 2, freezerDays = 180, pantryDays = 0),
    FISH(StorageLocation.FRIDGE, fridgeDays = 1, freezerDays = 90, pantryDays = 0),
    COLD_CUTS(StorageLocation.FRIDGE, fridgeDays = 5, freezerDays = 30, pantryDays = 0),
    CHEESE_DELI(StorageLocation.FRIDGE, fridgeDays = 10, freezerDays = 60, pantryDays = 1),
    VEGETABLES(StorageLocation.FRIDGE, fridgeDays = 5, freezerDays = 180, pantryDays = 4),
    SALAD(StorageLocation.FRIDGE, fridgeDays = 3, freezerDays = 3, pantryDays = 1),
    FRUIT(StorageLocation.PANTRY, fridgeDays = 7, freezerDays = 180, pantryDays = 5),
    LEFTOVERS(StorageLocation.FRIDGE, fridgeDays = 3, freezerDays = 60, pantryDays = 0);

    /** Becsult eltarthatosag napokban az adott helyen (null = szokasos hely). */
    fun estimateDays(location: StorageLocation? = null): Int = when (location ?: homeLocation) {
        StorageLocation.FRIDGE -> fridgeDays
        StorageLocation.FREEZER -> freezerDays
        StorageLocation.PANTRY -> pantryDays
    }
}

object ShelfLifeDefaults {
    /**
     * A "Kesobb" gomb (olvashatatlan datum a kamerakorben) becsult lejarata a
     * kor helye szerint. Ismeretlen termekrol van szo, ezert ovatos ertekek:
     * a fagyasztott aru valojaban honapokig eltarthato, a szaraz kamraaru is --
     * a becsles csak arra kell, hogy a tetel ne maradjon datum nelkul, es a
     * heti pontositas elott ne szoljunk feleslegesen tul koran.
     */
    fun laterEstimateDays(location: StorageLocation?): Int = when (location) {
        StorageLocation.FREEZER -> 60
        StorageLocation.PANTRY -> 14
        StorageLocation.FRIDGE, null -> 7
    }
}
