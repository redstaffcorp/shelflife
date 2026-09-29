package hu.rsc.shelflife.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ismeretlen vonalkod online termeknev-keresese: eloszor Open Food Facts,
 * utana tartalekkent UPCitemdb. Csak a vonalkod-szam megy ki, kep soha.
 * Soha nem dob kivetelt.
 */
object ProductLookup {

    enum class Source(val displayName: String) {
        OPEN_FOOD_FACTS("Open Food Facts"),
        UPCITEMDB("UPCitemdb")
    }

    sealed interface Result {
        data class Found(val name: String, val source: Source) : Result
        data object NotFound : Result
        data object Offline : Result
    }

    /** @param onSearching a hivo szalan hivodik, mielott egy forrast lekerdezunk. */
    suspend fun lookup(
        context: Context,
        barcode: String,
        onSearching: (Source) -> Unit = {}
    ): Result {
        if (!isOnline(context)) return Result.Offline

        onSearching(Source.OPEN_FOOD_FACTS)
        withContext(Dispatchers.IO) { OpenFoodFactsClient.lookupProductName(barcode) }
            ?.let { return Result.Found(it, Source.OPEN_FOOD_FACTS) }

        onSearching(Source.UPCITEMDB)
        withContext(Dispatchers.IO) { UpcItemDbClient.lookupProductName(barcode) }
            ?.let { return Result.Found(it, Source.UPCITEMDB) }

        return Result.NotFound
    }
}
