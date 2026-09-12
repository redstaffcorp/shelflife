package hu.rsc.shelflife.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Nagyon egyszeru kliens az Open Food Facts nyilvanos, ingyenes, nem fizetos
 * API-jahoz (world.openfoodfacts.org). FONTOS elhatarolas: ez KIZAROLAG a
 * termeknev opcionalis, kenyelmi lekerdezesere valo, amikor egy ismeretlen
 * vonalkodot latunk. A kamerakepek / OCR / lejarati datum felismeres
 * tovabbra is 100%-ban a telefonon, halozat nelkul tortenik -- ide semmilyen
 * kep vagy elemzesi eredmeny nem kerul elkuldesre, csak a beolvasott
 * vonalkod-szam megy ki egy nyilt, kozossegi adatbazisnak.
 */
object OpenFoodFactsClient {

    private const val USER_AGENT = "ShelfLife-Pilot/1.0 (feasibility test app)"
    private const val TIMEOUT_MS = 4000

    /**
     * @return a talalt termeknev (esetleg markaval), vagy null, ha nincs
     * talalat, nincs net, vagy barmilyen hiba tortent. Soha nem dob kivetelt,
     * hogy a hivo oldal mindig biztonsagosan visszaeshessen a kezi bevitelre.
     */
    fun lookupProductName(barcode: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://world.openfoodfacts.net/api/v2/product/$barcode?fields=product_name,brands")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optInt("status", 0) != 1) return null

            val product = json.optJSONObject("product") ?: return null
            val name = product.optString("product_name").trim()
            val brand = product.optString("brands").trim()

            when {
                name.isNotEmpty() && brand.isNotEmpty() -> "$brand $name"
                name.isNotEmpty() -> name
                else -> null
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
