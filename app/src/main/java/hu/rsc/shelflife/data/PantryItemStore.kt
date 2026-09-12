package hu.rsc.shelflife.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * A felvett tetelek listajanak helyi, felho nelkuli perzisztalasa
 * (SharedPreferences + JSON). Erre azert van szukseg, mert a lejarati
 * ertesiteseket egy hatterben futo WorkManager-feladat ellenorzi, ami
 * akkor is lefut, amikor az app maga nincs megnyitva -- igy a listanak
 * tulelnie kell egy app- vagy telefon-ujrainditast.
 */
class PantryItemStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAll(): List<PantryItem> {
        val json = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                parseItem(obj)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAll(items: List<PantryItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    private fun parseItem(obj: JSONObject): PantryItem? {
        return try {
            PantryItem(
                id = obj.getLong("id"),
                barcode = if (obj.isNull("barcode")) null else obj.optString("barcode"),
                productName = obj.getString("productName"),
                expiry = LocalDate.parse(obj.getString("expiry")),
                recordedAt = LocalDate.parse(obj.getString("recordedAt")),
                quantity = if (obj.has("quantity") && !obj.isNull("quantity")) obj.getInt("quantity") else null,
                enteredManually = obj.optBoolean("enteredManually", false),
                ambiguousDayMonth = obj.optBoolean("ambiguousDayMonth", false),
                notifiedForExpiry = if (obj.has("notifiedForExpiry") && !obj.isNull("notifiedForExpiry")) {
                    LocalDate.parse(obj.getString("notifiedForExpiry"))
                } else null
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun toJson(item: PantryItem): JSONObject {
        val obj = JSONObject()
        obj.put("id", item.id)
        obj.put("barcode", item.barcode ?: JSONObject.NULL)
        obj.put("productName", item.productName)
        obj.put("expiry", item.expiry.toString())
        obj.put("recordedAt", item.recordedAt.toString())
        obj.put("quantity", item.quantity ?: JSONObject.NULL)
        obj.put("enteredManually", item.enteredManually)
        obj.put("ambiguousDayMonth", item.ambiguousDayMonth)
        obj.put("notifiedForExpiry", item.notifiedForExpiry?.toString() ?: JSONObject.NULL)
        return obj
    }

    companion object {
        private const val PREFS_NAME = "shelflife_pantry_items"
        private const val KEY_ITEMS = "items_json"
    }
}
