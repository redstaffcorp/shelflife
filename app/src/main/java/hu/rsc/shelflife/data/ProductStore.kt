package hu.rsc.shelflife.data

import android.content.Context
import org.json.JSONObject

/**
 * Nagyon egyszeru, felho nelkuli, helyi tarolo: vonalkod -> termeknev, tovabba
 * egy nevalapu "ismert termek" lista (mertekegyseggel), hogy egy korabban
 * mar felvett termek a kezi bevitelnel is valaszthato legyen a listabol, es
 * a mertekegyseget se kelljen ujra megadni. Pilot celra SharedPreferences +
 * JSON, semmi halozati hivas.
 */
class ProductStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -- Vonalkod -> termeknev cache (barcode-szkenneles utan) --

    fun get(barcode: String): String? {
        val json = prefs.getString(KEY_MAP, null) ?: return null
        return try {
            val obj = JSONObject(json)
            if (obj.has(barcode)) obj.getString(barcode) else null
        } catch (e: Exception) {
            null
        }
    }

    fun save(barcode: String, name: String) {
        val existing = prefs.getString(KEY_MAP, null)
        val obj = if (existing != null) {
            try {
                JSONObject(existing)
            } catch (e: Exception) {
                JSONObject()
            }
        } else {
            JSONObject()
        }
        obj.put(barcode, name)
        prefs.edit().putString(KEY_MAP, obj.toString()).apply()
    }

    // -- Nevalapu "ismert termek" lista + mertekegyseg --
    // Kulcs: a nev kisbetus, trim-elt valtozata (case-insensitive egyezeshez),
    // ertek: {"name": eredeti iras szerinti nev, "unit": utoljara hasznalt
    // mertekegyseg}. Ez teszi lehetove, hogy kezi bevitelnel (ahol nincs
    // vonalkod) is fel lehessen ajanlani a korabban mar berogzitett
    // termekeket, a hozzajuk tartozo mertekegyseggel egyutt.

    data class KnownProduct(val name: String, val unit: String)

    fun allKnownProducts(): List<KnownProduct> {
        val json = prefs.getString(KEY_KNOWN_PRODUCTS, null) ?: return emptyList()
        return try {
            val obj = JSONObject(json)
            val result = mutableListOf<KnownProduct>()
            obj.keys().forEach { key ->
                val entry = obj.optJSONObject(key)
                val name = entry?.optString("name")?.takeIf { it.isNotBlank() }
                if (name != null) {
                    result.add(KnownProduct(name, entry.optString("unit", DEFAULT_UNIT)))
                }
            }
            result.sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getUnit(name: String): String? {
        val key = name.trim().lowercase()
        if (key.isBlank()) return null
        val json = prefs.getString(KEY_KNOWN_PRODUCTS, null) ?: return null
        return try {
            val obj = JSONObject(json)
            if (obj.has(key)) obj.getJSONObject(key).optString("unit", DEFAULT_UNIT) else null
        } catch (e: Exception) {
            null
        }
    }

    fun saveKnownProduct(name: String, unit: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        val key = trimmedName.lowercase()
        val existing = prefs.getString(KEY_KNOWN_PRODUCTS, null)
        val obj = if (existing != null) {
            try {
                JSONObject(existing)
            } catch (e: Exception) {
                JSONObject()
            }
        } else {
            JSONObject()
        }
        val entry = JSONObject()
        entry.put("name", trimmedName)
        entry.put("unit", unit.ifBlank { DEFAULT_UNIT })
        obj.put(key, entry)
        prefs.edit().putString(KEY_KNOWN_PRODUCTS, obj.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "shelflife_products"
        private const val KEY_MAP = "barcode_to_name"
        private const val KEY_KNOWN_PRODUCTS = "known_products"
        const val DEFAULT_UNIT = "darab"
    }
}
