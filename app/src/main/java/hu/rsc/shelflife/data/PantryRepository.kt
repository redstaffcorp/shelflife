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

    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("hu"))

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
        productDao.upsertKnownProduct(
            KnownProductEntity(nameKey = KnownProductEntity.keyOf(trimmed), name = trimmed, unit = unit.trim())
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
