package hu.rsc.shelflife.data.legacy

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import hu.rsc.shelflife.data.BarcodeProduct
import hu.rsc.shelflife.data.KnownProductEntity
import hu.rsc.shelflife.data.PantryItem
import hu.rsc.shelflife.data.db.ShelfLifeDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Egyszeri atkoltoztetes a pilot SharedPreferences+JSON tarolojabol a Room
 * adatbazisba. Minden indulaskor olcson lefut: ha a regi kulcsok mar nincsenek
 * meg, azonnal visszater.
 *
 * Biztonsagi elvek:
 *  - Az import egy tranzakcioban tortenik; a regi kulcsokat csak SIKERES
 *    irasa utan toroljuk (commit(), szinkron).
 *  - Torles elott a nyers JSON-t egy fajlba mentjuk (filesDir/legacy_backup),
 *    hogy egy esetleges hibas import utan se vesszen el semmi.
 *  - Ha a JSON egesz egyszeruen olvashatatlan, NEM toroljuk a regi kulcsot.
 */
internal object LegacyPrefsImporter {

    private const val TAG = "LegacyPrefsImporter"

    private const val ITEMS_PREFS = "shelflife_pantry_items"
    private const val KEY_ITEMS = "items_json"

    private const val PRODUCTS_PREFS = "shelflife_products"
    private const val KEY_BARCODE_MAP = "barcode_to_name"
    private const val KEY_KNOWN_PRODUCTS = "known_products"

    // A pilot ezzel az ertekkel mentette a mertekegyseg nelkuli teteleket.
    private const val LEGACY_DEFAULT_UNIT = "darab"

    suspend fun importIfNeeded(context: Context, db: ShelfLifeDatabase) {
        val itemPrefs = context.getSharedPreferences(ITEMS_PREFS, Context.MODE_PRIVATE)
        val productPrefs = context.getSharedPreferences(PRODUCTS_PREFS, Context.MODE_PRIVATE)

        val itemsJson = itemPrefs.getString(KEY_ITEMS, null)
        val barcodeJson = productPrefs.getString(KEY_BARCODE_MAP, null)
        val knownJson = productPrefs.getString(KEY_KNOWN_PRODUCTS, null)
        if (itemsJson == null && barcodeJson == null && knownJson == null) return

        backup(context, itemsJson, barcodeJson, knownJson)

        val items = itemsJson?.let { parseItems(it) }
        val barcodes = barcodeJson?.let { parseBarcodes(it) }
        val known = knownJson?.let { parseKnownProducts(it) }

        try {
            db.withTransaction {
                items?.let { db.pantryItemDao().upsertAll(it) }
                barcodes?.let { db.productDao().upsertBarcodes(it) }
                known?.let { db.productDao().upsertKnownProducts(it) }
            }
        } catch (e: Exception) {
            // A regi adat megmarad, a kovetkezo indulaskor ujra probalkozunk.
            Log.e(TAG, "Legacy import failed", e)
            return
        }

        // Csak azt toroljuk, amit sikerult ertelmezni es beirni.
        itemPrefs.edit().also { if (items != null) it.remove(KEY_ITEMS) }.commit()
        productPrefs.edit().also {
            if (barcodes != null) it.remove(KEY_BARCODE_MAP)
            if (known != null) it.remove(KEY_KNOWN_PRODUCTS)
        }.commit()
        Log.i(TAG, "Imported ${items?.size ?: 0} items, ${barcodes?.size ?: 0} barcodes, ${known?.size ?: 0} known products")
    }

    private fun backup(context: Context, vararg parts: String?) {
        try {
            val dir = File(context.filesDir, "legacy_backup").apply { mkdirs() }
            val file = File(dir, "prefs_${System.currentTimeMillis()}.json")
            file.writeText(parts.joinToString("\n---\n") { it ?: "null" })
        } catch (e: Exception) {
            Log.w(TAG, "Backup failed", e)
        }
    }

    /** @return null, ha a JSON olvashatatlan (ekkor a regi kulcs megmarad). */
    private fun parseItems(json: String): List<PantryItem>? = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { parseItem(it) } }
    } catch (e: Exception) {
        null
    }

    private fun parseItem(obj: JSONObject): PantryItem? = try {
        PantryItem(
            id = obj.getLong("id"),
            barcode = if (obj.isNull("barcode")) null else obj.optString("barcode"),
            productName = obj.getString("productName"),
            expiry = LocalDate.parse(obj.getString("expiry")),
            recordedAt = LocalDate.parse(obj.getString("recordedAt")),
            quantity = if (obj.has("quantity") && !obj.isNull("quantity")) obj.getInt("quantity") else null,
            quantityUnit = if (obj.has("quantityUnit") && !obj.isNull("quantityUnit")) {
                obj.getString("quantityUnit")
            } else LEGACY_DEFAULT_UNIT,
            enteredManually = obj.optBoolean("enteredManually", false),
            ambiguousDayMonth = obj.optBoolean("ambiguousDayMonth", false),
            expiryEstimated = obj.optBoolean("expiryEstimated", false),
            notifiedForExpiry = if (obj.has("notifiedForExpiry") && !obj.isNull("notifiedForExpiry")) {
                LocalDate.parse(obj.getString("notifiedForExpiry"))
            } else null
        )
    } catch (e: Exception) {
        null
    }

    private fun parseBarcodes(json: String): List<BarcodeProduct>? = try {
        val obj = JSONObject(json)
        obj.keys().asSequence().mapNotNull { code ->
            obj.optString(code).takeIf { it.isNotBlank() }?.let { BarcodeProduct(code, it) }
        }.toList()
    } catch (e: Exception) {
        null
    }

    private fun parseKnownProducts(json: String): List<KnownProductEntity>? = try {
        val obj = JSONObject(json)
        obj.keys().asSequence().mapNotNull { key ->
            val entry = obj.optJSONObject(key) ?: return@mapNotNull null
            val name = entry.optString("name").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            KnownProductEntity(
                nameKey = KnownProductEntity.keyOf(name),
                name = name,
                unit = entry.optString("unit", LEGACY_DEFAULT_UNIT).ifBlank { LEGACY_DEFAULT_UNIT }
            )
        }.toList()
    } catch (e: Exception) {
        null
    }
}
