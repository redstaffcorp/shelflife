package hu.rsc.shelflife.data

import android.content.Context
import org.json.JSONObject

/**
 * Nagyon egyszeru, felho nelkuli, helyi tarolo: vonalkod -> termeknev.
 * Pilot celra SharedPreferences + JSON, semmi halozati hivas.
 */
class ProductStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    companion object {
        private const val PREFS_NAME = "shelflife_products"
        private const val KEY_MAP = "barcode_to_name"
    }
}
