package hu.rsc.shelflife.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Masodik, tartalek forras a termeknev online lekerdezesehez, ha az Open
 * Food Facts nem talal semmit. Az UPCitemdb ingyenes "trial" vegpontjat
 * hasznalja, API-kulcs / regisztracio nelkul. Ugyanaz az elv, mint az
 * OFF-nal: csak a vonalkod-szam megy ki, kep vagy elemzesi eredmeny soha.
 *
 * Az ingyenes szint napi/perces kvotaval korlatozott -- ha eleri a
 * korlatot vagy barmi mas hiba tortenik, egyszeruen null-t ad vissza,
 * es a hivo oldal (ScannerScreen) ilyenkor a kezi bevitelre esik vissza.
 */
object UpcItemDbClient {

    private const val TIMEOUT_MS = 4000

    fun lookupProductName(barcode: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://api.upcitemdb.com/prod/trial/lookup?upc=$barcode")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optString("code") != "OK") return null

            val items = json.optJSONArray("items") ?: return null
            if (items.length() == 0) return null

            val item = items.getJSONObject(0)
            val title = item.optString("title").trim()
            val brand = item.optString("brand").trim()

            when {
                title.isNotEmpty() -> title
                brand.isNotEmpty() -> brand
                else -> null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
