package hu.rsc.shelflife.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import hu.rsc.shelflife.data.BarcodeProduct
import hu.rsc.shelflife.data.KnownProductEntity
import hu.rsc.shelflife.data.PantryItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PantryItemDao {

    /** Csak a kamraban levo (nem elfogyott / kidobott) tetelek. */
    @Query("SELECT * FROM pantry_items WHERE status = 'ACTIVE'")
    fun observeActive(): Flow<List<PantryItem>>

    @Query("SELECT * FROM pantry_items WHERE status = 'ACTIVE'")
    suspend fun getActive(): List<PantryItem>

    @Query("SELECT * FROM pantry_items WHERE id = :id")
    suspend fun getById(id: Long): PantryItem?

    @Upsert
    suspend fun upsert(item: PantryItem)

    @Upsert
    suspend fun upsertAll(items: List<PantryItem>)

    @Query("DELETE FROM pantry_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Csak akkor jeloli ertesitettnek, ha a lejarat kozben nem valtozott --
     * igy a hatterfeladat nem irhatja felul a felhasznalo kozbeni modositasat.
     */
    @Query("UPDATE pantry_items SET notifiedForExpiry = :expiry WHERE id = :id AND expiry = :expiry")
    suspend fun markNotified(id: Long, expiry: LocalDate)

    /** "Holnap szolj": a korabbi ertesites nem szamit, de `until`-ig csend. */
    @Query("UPDATE pantry_items SET snoozedUntil = :until, notifiedForExpiry = NULL WHERE id = :id")
    suspend fun snooze(id: Long, until: LocalDate)
}

@Dao
interface ProductDao {

    @Query("SELECT name FROM barcode_products WHERE barcode = :barcode")
    suspend fun nameForBarcode(barcode: String): String?

    @Upsert
    suspend fun upsertBarcode(product: BarcodeProduct)

    @Upsert
    suspend fun upsertBarcodes(products: List<BarcodeProduct>)

    @Query("SELECT * FROM known_products")
    fun observeKnownProducts(): Flow<List<KnownProductEntity>>

    @Query("SELECT unit FROM known_products WHERE nameKey = :nameKey")
    suspend fun unitFor(nameKey: String): String?

    @Upsert
    suspend fun upsertKnownProduct(product: KnownProductEntity)

    @Upsert
    suspend fun upsertKnownProducts(products: List<KnownProductEntity>)
}
