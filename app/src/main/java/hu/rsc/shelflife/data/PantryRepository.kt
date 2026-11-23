package hu.rsc.shelflife.data

import android.content.Context
import hu.rsc.shelflife.data.db.ShelfLifeDatabase
import hu.rsc.shelflife.data.legacy.LegacyPrefsImporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

/**
 * Az app egyetlen adat-belepesi pontja (UI es hatterfeladat egyarant ezt
 * hasznalja). Az elso hozzafereskor lefuttatja a pilot-adatok egyszeri
 * importjat (lasd LegacyPrefsImporter).
 */
class PantryRepository private constructor(private val appContext: Context) {

    private val db = ShelfLifeDatabase.get(appContext)
    private val itemDao = db.pantryItemDao()
    private val productDao = db.productDao()

    private val readyMutex = Mutex()
    @Volatile
    private var ready = false

    private suspend fun ensureReady() {
        if (ready) return
        readyMutex.withLock {
            if (!ready) {
                LegacyPrefsImporter.importIfNeeded(appContext, db)
                ready = true
            }
        }
    }

    // -- Tetelek --

    val items: Flow<List<PantryItem>> = flow {
        ensureReady()
        emitAll(itemDao.observeActive())
    }

    /** Lezart (elfogyott / kidobott) tetelek, a statisztika-kepernyohoz. */
    val finishedItems: Flow<List<PantryItem>> = flow {
        ensureReady()
        emitAll(itemDao.observeFinished())
    }

    /** A kamraban levo (aktiv) tetelek. */
    suspend fun getActiveItems(): List<PantryItem> {
        ensureReady()
        return itemDao.getActive()
    }

    suspend fun getItem(id: Long): PantryItem? {
        ensureReady()
        return itemDao.getById(id)
    }

    suspend fun upsertItem(item: PantryItem) {
        ensureReady()
        itemDao.upsert(item)
    }

    suspend fun deleteItem(id: Long) {
        ensureReady()
        itemDao.deleteById(id)
    }

    /**
     * Tetel lezarasa (elfogyott / kidobtuk). A sor megmarad az adatbazisban.
     * @return a lezaras elotti allapot (visszavonashoz), vagy null.
     */
    suspend fun finishItem(id: Long, status: ItemStatus): PantryItem? {
        ensureReady()
        val old = itemDao.getById(id) ?: return null
        if (old.status != ItemStatus.ACTIVE) return null
        itemDao.upsert(old.copy(status = status, finishedAt = LocalDate.now()))
        return old
    }

    suspend fun snooze(id: Long, until: LocalDate) {
        ensureReady()
        itemDao.snooze(id, until)
    }

    suspend fun markNotified(items: List<PantryItem>) {
        ensureReady()
        items.forEach { itemDao.markNotified(it.id, it.expiry) }
    }

    // -- Termekek (vonalkod-cache + ismert termekek) --

    // Az aktualis nyelv abc-rendje (getter: az app nyelve futas kozben valtozhat).
    private val collator: Collator get() = Collator.getInstance(Locale.getDefault())

    val knownProducts: Flow<List<KnownProduct>> = flow {
        ensureReady()
        emitAll(productDao.observeKnownProducts())
    }.map { list ->
        list.map { KnownProduct(it.name, it.unit) }.sortedWith(compareBy(collator) { it.name })
    }

    suspend fun productNameForBarcode(barcode: String): String? {
        ensureReady()
        return productDao.nameForBarcode(barcode)
    }

    suspend fun rememberBarcode(barcode: String, name: String) {
        ensureReady()
        productDao.upsertBarcode(BarcodeProduct(barcode, name.trim()))
    }

    suspend fun unitFor(name: String): String? {
        val key = KnownProductEntity.keyOf(name)
        if (key.isBlank()) return null
        ensureReady()
        return productDao.unitFor(key)
    }

    suspend fun rememberProduct(name: String, unit: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || unit.isBlank()) return
        ensureReady()
        val key = KnownProductEntity.keyOf(trimmed)
        // A felbontas utani eltarthatosag megmarad (az upsert az egesz sort irja).
        val keptDays = productDao.knownProduct(key)?.openedShelfDays
        productDao.upsertKnownProduct(
            KnownProductEntity(nameKey = key, name = trimmed, unit = unit.trim(), openedShelfDays = keptDays)
        )
    }

    /** A termeknel legutobb megadott "felbontas utan N nap", vagy null. */
    suspend fun openedShelfDaysFor(name: String): Int? {
        val key = KnownProductEntity.keyOf(name)
        if (key.isBlank()) return null
        ensureReady()
        return productDao.knownProduct(key)?.openedShelfDays
    }

    suspend fun rememberOpenedShelfDays(name: String, unit: String, days: Int) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        ensureReady()
        val key = KnownProductEntity.keyOf(trimmed)
        val existing = productDao.knownProduct(key)
        productDao.upsertKnownProduct(
            existing?.copy(openedShelfDays = days)
                ?: KnownProductEntity(nameKey = key, name = trimmed, unit = unit.trim(), openedShelfDays = days)
        )
    }

    companion object {
        @Volatile
        private var instance: PantryRepository? = null

        fun get(context: Context): PantryRepository =
            instance ?: synchronized(this) {
                instance ?: PantryRepository(context.applicationContext).also { instance = it }
            }
    }
}

/** Uj lejarati datum -> a korabbi ertesites mar nem szamit (ujra kuldheto). */
fun PantryItem.withExpiry(newExpiry: LocalDate): PantryItem =
    if (newExpiry == expiry) this else copy(expiry = newExpiry, notifiedForExpiry = null)

/** A csomagolason levo (felbontas elotti) lejarat. */
val PantryItem.packageExpiry: LocalDate get() = expiryBeforeOpening ?: expiry

val PantryItem.isOpened: Boolean get() = openedAt != null

/**
 * Felbontas: az ervenyes lejarat a csomagolason levo datum es a
 * `openedAt + shelfDays` kozul a korabbi. Ismetelt hivas (a felbontas
 * modositasa) mindig az eredeti csomagolasi datumbol szamol.
 */
fun PantryItem.opened(openedAt: LocalDate, shelfDays: Int): PantryItem {
    val pkg = packageExpiry
    val effective = minOf(pkg, openedAt.plusDays(shelfDays.toLong()))
    return withExpiry(effective).copy(
        openedAt = openedAt,
        openedShelfDays = shelfDays,
        expiryBeforeOpening = pkg
    )
}

/** A felbontas visszavonasa: vissza a csomagolason levo lejarathoz. */
fun PantryItem.unopened(): PantryItem =
    if (!isOpened) this
    else withExpiry(packageExpiry).copy(openedAt = null, openedShelfDays = null, expiryBeforeOpening = null)

/**
 * Darabolhato-e a mennyiseg (darab, csomag, doboz, uveg...). Suly- es
 * urmertek-egysegnel (500 g, 1 l) egyetlen csomagrol van szo, ott felbontaskor
 * nincs mit szetvalasztani.
 */
fun isCountUnit(unit: String): Boolean = unit.trim().lowercase() !in MEASURE_UNITS

private val MEASURE_UNITS = setOf("kg", "dkg", "g", "l", "dl", "cl", "ml")
