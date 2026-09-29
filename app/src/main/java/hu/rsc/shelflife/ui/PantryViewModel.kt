package hu.rsc.shelflife.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hu.rsc.shelflife.R
import hu.rsc.shelflife.data.ItemStatus
import hu.rsc.shelflife.data.KnownProduct
import hu.rsc.shelflife.data.PantryItem
import hu.rsc.shelflife.data.PantryRepository
import hu.rsc.shelflife.data.StorageLocation
import hu.rsc.shelflife.data.withExpiry
import hu.rsc.shelflife.ocr.DateCandidate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

// Rogzites utan ennyi ideig nem szabad latszania ugyanannak a vonalkodnak,
// hogy ujra beolvashato legyen (lasd recentlyRecorded).
private const val BARCODE_COOLDOWN_MS = 1500L

// Ennyi egymas utani egyezo vonalkod-olvasas kell az elfogadashoz.
private const val BARCODE_SIGHTINGS_NEEDED = 2

// "Kesobb" gombnal hasznalt fix becsult lejarat (ma + ennyi nap). Tudatosan NEM
// a termek korabbi rogzitesebol szamoljuk: a lejarat a gyartasbol kovetkezik,
// nem a vasarlas/rogzites napjabol. A tetel mindig "becsult" jelolest kap.
const val DEFAULT_ESTIMATE_DAYS = 7L

// Kezi felvitelnel es a naptarban a kiindulo lejarat: ma + ennyi nap.
const val DEFAULT_MANUAL_EXPIRY_DAYS = 3L

/** A felvett tetelek listajanak rendezese. */
enum class ItemSortMode { BY_EXPIRY, BY_RECORDED }

/** A rogzitesi kor (kamera) aktualis fazisa. */
sealed interface UiState {
    data object ScanningBarcode : UiState
    /** Vonalkod elfogadva, a helyi cache-ben keresunk (par ms). */
    data class ResolvingBarcode(val barcode: String) : UiState
    data class AskingProductName(val barcode: String) : UiState
    data class ScanningDate(val barcode: String, val productName: String) : UiState
}

/**
 * Egy tetel letrehozasa VAGY szerkesztese kezzel, kamera nelkul. `id == null`
 * -> uj tetel; `id != null` -> egy meglevo tetel szerkesztese.
 */
data class ManualEditState(
    val id: Long?,
    val barcode: String?,
    val name: String,
    val expiry: LocalDate,
    val quantity: Int?,
    val quantityUnit: String,
    val recordedAt: LocalDate,
    val expiryEstimated: Boolean = false,
    val location: StorageLocation? = null
)

/**
 * Visszavonhato muvelet utani snackbar-kerelem. `message` mar a vegleges
 * (lokalizalt) szoveg.
 */
data class UndoRequest(val message: String, val action: UndoAction)

sealed interface UndoAction {
    /** A tetel korabbi allapotanak visszairasa (elfogyott, kidobva, -1, torles). */
    data class Restore(val snapshot: PantryItem) : UndoAction
    /** Egy most felvett tetel eltavolitasa (gyorsracs). */
    data class Remove(val itemId: Long) : UndoAction
}

/** Vonalkod nelkuli gyorsracs egy csempeje (lasd QuickAddDialog). */
data class QuickProduct(
    val nameRes: Int,
    val estimateDays: Long,
    val location: StorageLocation,
    val quantity: Int = 1
)

/**
 * A fokepernyo allapota es uzleti logikaja. A tetelek es az ismert termekek
 * a Room adatbazisbol jonnek (Flow); a rogzitesi kor allapota Compose-state,
 * mert a kamera-analizator (fo szalon) kozvetlenul olvassa.
 */
class PantryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PantryRepository.get(application)
    private val defaultUnit: String = application.getString(R.string.unit_default)

    private fun str(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    val items: StateFlow<List<PantryItem>> = repository.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val knownProducts: StateFlow<List<KnownProduct>> = repository.knownProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -- Rogzitesi kor allapota --

    var uiState by mutableStateOf<UiState>(UiState.ScanningBarcode)
        private set

    // Igazi kamerahasznalat csak akkor tortenik, ha a felhasznalo aktivan
    // elindit egy rogzitesi kort.
    var cameraSessionActive by mutableStateOf(false)
        private set

    var torchOn by mutableStateOf(false)

    // Az aktualisan felvitt tetel opcionalis mennyisege es mertekegysege.
    var quantity by mutableStateOf<Int?>(1)
    var quantityUnit by mutableStateOf(defaultUnit)

    // A legutobb felvett tetel (erre gorgetunk es ezt emeljuk ki erosen), es
    // az aktualis rogzitesi korben felvett tetelek (enyhe kiemeles).
    var lastAddedId by mutableStateOf<Long?>(null)
        private set
    val sessionAddedIds = mutableStateListOf<Long>()

    // A rogzitesi korben felvett tetelek helye -- egyszer kell beallitani, a
    // kor vegeig (es a kovetkezo korre is) megmarad.
    var sessionLocation by mutableStateOf(StorageLocation.FRIDGE)

    private val undoChannel = Channel<UndoRequest>(Channel.BUFFERED)
    /** Visszavonhato muveletek (a UI snackbart mutat hozzajuk). */
    val undoRequests: Flow<UndoRequest> = undoChannel.receiveAsFlow()

    // Kezi (kamera nelkuli) uj felvitel VAGY egy meglevo tetel szerkesztese.
    var manualEdit by mutableStateOf<ManualEditState?>(null)
        private set

    private var barcodeSighting: Pair<String?, Int> = null to 0
    // datum -> hanyszor lattuk az aktualis datum-fazisban (tobb kepvarians kozotti szavazas)
    private var dateSighting: Map<LocalDate, Int> = emptyMap()
    // Az utoljara rogzitett tetel vonalkodja + mikor lattuk utoljara. Rogzites
    // utan a termek meg a kamera elott van, igy a vonalkod azonnal ujra
    // beolvasodna ("beragad"). Ezt a vonalkodot addig figyelmen kivul hagyjuk,
    // amig legalabb BARCODE_COOLDOWN_MS-ig nem latszik.
    private var recentlyRecorded: Pair<String?, Long> = null to 0L
    private var lastIssuedId = 0L

    /** Egyedi, monoton novo azonosito (a felvitel idopontja ms-ban). */
    private fun nextId(): Long {
        lastIssuedId = maxOf(System.currentTimeMillis(), lastIssuedId + 1)
        return lastIssuedId
    }

    private fun resetSightings() {
        barcodeSighting = null to 0
        dateSighting = emptyMap()
    }

    fun startScanningSession() {
        uiState = UiState.ScanningBarcode
        recentlyRecorded = null to 0L
        resetSightings()
        quantity = 1
        quantityUnit = defaultUnit
        torchOn = false
        sessionAddedIds.clear()
        lastAddedId = null
        cameraSessionActive = true
    }

    fun endScanningSession() {
        cameraSessionActive = false
        torchOn = false
        uiState = UiState.ScanningBarcode
        resetSightings()
    }

    /** Vissza a vonalkod-fazisba (Megse a nev-dialogusban vagy a datum-fazisban). */
    fun cancelCurrentProduct() {
        uiState = UiState.ScanningBarcode
        resetSightings()
    }

    /**
     * A kamera-analizator hivja minden latott vonalkodnal.
     * @return true, ha a vonalkodot most elfogadtuk (a UI haptikus jelzest ad).
     */
    fun onBarcodeSeen(value: String): Boolean {
        if (uiState !is UiState.ScanningBarcode) return false
        val now = System.currentTimeMillis()
        val (recentCode, recentSeenAt) = recentlyRecorded
        if (recentCode == value && now - recentSeenAt < BARCODE_COOLDOWN_MS) {
            // Meg mindig a most rogzitett termek van a kepen -> csusztatjuk a
            // turelmi idot, amig el nem tunik a kamera elol.
            recentlyRecorded = value to now
            return false
        }
        val (lastVal, count) = barcodeSighting
        val newCount = if (lastVal == value) count + 1 else 1
        barcodeSighting = value to newCount
        if (newCount < BARCODE_SIGHTINGS_NEEDED) return false
        barcodeSighting = null to 0

        // MINDIG eloszor a helyi cache-ben nezzuk meg. Csak akkor nyilik meg
        // az "ismeretlen termek" dialogus (es azzal az online kereses), ha ez
        // nem talal semmit.
        val resolving = UiState.ResolvingBarcode(value)
        uiState = resolving
        viewModelScope.launch {
            val known = repository.productNameForBarcode(value)
            val unit = known?.let { repository.unitFor(it) } ?: defaultUnit
            if (uiState != resolving) return@launch // kozben lezartak a kort
            quantity = 1
            quantityUnit = unit
            uiState = if (known != null) {
                UiState.ScanningDate(barcode = value, productName = known)
            } else {
                UiState.AskingProductName(barcode = value)
            }
        }
        return true
    }

    /** Az "Uj termek" dialogus Mentes gombja. */
    fun confirmProductName(barcode: String, rawName: String) {
        val name = rawName.trim()
        if (name.isBlank()) return
        val next = UiState.ScanningDate(barcode = barcode, productName = name)
        quantity = 1
        quantityUnit = defaultUnit
        uiState = next
        viewModelScope.launch {
            // A megerositett nev bekerul a helyi cache-be -- legkozelebb mar
            // innen jon, online kereses nelkul.
            repository.rememberBarcode(barcode, name)
            val unit = repository.unitFor(name)
            if (unit != null && uiState == next) quantityUnit = unit
        }
    }

    /**
     * A kamera-analizator hivja minden datum-jeloltnel.
     * @return true, ha a datumot elfogadtuk es a tetel rogzitesre kerult.
     */
    fun onDateCandidate(candidate: DateCandidate): Boolean {
        val state = uiState as? UiState.ScanningDate ?: return false
        // Nem kell egymas utan 3x ugyanaz: a kepvariansok kozul nehany mast
        // (vagy semmit) lathat, ezert osszesitve szamolunk. Kulcsszo nelkuli
        // talalatnal egy szavazattal tobb kell.
        val counts = dateSighting.toMutableMap()
        val newCount = (counts[candidate.date] ?: 0) + 1
        counts[candidate.date] = newCount
        dateSighting = counts
        val needed = if (candidate.hasPositiveKeyword) 3 else 4
        if (newCount < needed) return false
        recordScannedItem(
            state,
            expiry = candidate.date,
            enteredManually = false,
            ambiguousDayMonth = candidate.ambiguousDayMonth,
            estimated = false
        )
        return true
    }

    /**
     * Datum-fazis lezarasa nem-OCR uton. `estimated = true`: becsles ("~1 het",
     * "Kesobb") -- a tetel "becsult, pontositando" jelolest kap.
     */
    fun finishWithManualDate(date: LocalDate, estimated: Boolean = false): Boolean {
        val state = uiState as? UiState.ScanningDate ?: return false
        recordScannedItem(
            state,
            expiry = date,
            enteredManually = true,
            ambiguousDayMonth = false,
            estimated = estimated
        )
        return true
    }

    private fun recordScannedItem(
        state: UiState.ScanningDate,
        expiry: LocalDate,
        enteredManually: Boolean,
        ambiguousDayMonth: Boolean,
        estimated: Boolean
    ) {
        val item = PantryItem(
            id = nextId(),
            barcode = state.barcode,
            productName = state.productName,
            expiry = expiry,
            recordedAt = LocalDate.now(),
            quantity = quantity,
            quantityUnit = quantityUnit,
            enteredManually = enteredManually,
            ambiguousDayMonth = ambiguousDayMonth,
            expiryEstimated = estimated,
            location = sessionLocation
        )
        onItemAdded(item.id)
        recentlyRecorded = state.barcode to System.currentTimeMillis()
        resetSightings()
        uiState = UiState.ScanningBarcode
        val unit = quantityUnit
        viewModelScope.launch {
            repository.upsertItem(item)
            repository.rememberProduct(item.productName, unit)
        }
    }

    private fun onItemAdded(id: Long) {
        sessionAddedIds.add(id)
        lastAddedId = id
    }

    // -- Kezi felvitel / szerkesztes --

    fun openManualAdd() {
        manualEdit = ManualEditState(
            id = null,
            barcode = null,
            name = "",
            // Realisztikusabb kiindulopont, mint a mai nap.
            expiry = LocalDate.now().plusDays(DEFAULT_MANUAL_EXPIRY_DAYS),
            quantity = 1,
            quantityUnit = defaultUnit,
            recordedAt = LocalDate.now(),
            location = sessionLocation
        )
    }

    fun openEdit(item: PantryItem) {
        manualEdit = ManualEditState(
            id = item.id,
            barcode = item.barcode,
            name = item.productName,
            expiry = item.expiry,
            quantity = item.quantity,
            quantityUnit = item.quantityUnit,
            recordedAt = item.recordedAt,
            expiryEstimated = item.expiryEstimated,
            location = item.location
        )
    }

    fun closeManualEdit() {
        manualEdit = null
    }

    fun saveManualEdit(
        edit: ManualEditState,
        rawName: String,
        expiry: LocalDate,
        quantity: Int?,
        unit: String,
        estimated: Boolean,
        location: StorageLocation?
    ) {
        val name = rawName.trim()
        if (name.isBlank()) return
        manualEdit = null
        val newId = if (edit.id == null) nextId().also { onItemAdded(it) } else null
        viewModelScope.launch {
            edit.barcode?.let { repository.rememberBarcode(it, name) }
            repository.rememberProduct(name, unit)
            if (newId != null) {
                repository.upsertItem(
                    PantryItem(
                        id = newId,
                        barcode = edit.barcode,
                        productName = name,
                        expiry = expiry,
                        recordedAt = edit.recordedAt,
                        quantity = quantity,
                        quantityUnit = unit,
                        enteredManually = true,
                        ambiguousDayMonth = false,
                        expiryEstimated = estimated,
                        location = location
                    )
                )
            } else {
                val old = repository.getItem(edit.id!!) ?: return@launch
                repository.upsertItem(
                    old.withExpiry(expiry).copy(
                        productName = name,
                        quantity = quantity,
                        quantityUnit = unit,
                        ambiguousDayMonth = false,
                        expiryEstimated = estimated,
                        location = location
                    )
                )
            }
        }
    }

    fun deleteItem(id: Long) {
        if (manualEdit?.id == id) manualEdit = null
        viewModelScope.launch {
            val snapshot = repository.getItem(id) ?: return@launch
            repository.deleteItem(id)
            offerUndo(str(R.string.undo_deleted, snapshot.productName), UndoAction.Restore(snapshot))
        }
    }

    // -- Elhasznalas --

    /** Az egesz tetel elfogyott (balra huzas, ertesites-akcio). */
    fun markConsumed(item: PantryItem) = finish(item, ItemStatus.CONSUMED, R.string.undo_consumed)

    /** Kidobtuk (jobbra huzas). */
    fun markWasted(item: PantryItem) = finish(item, ItemStatus.WASTED, R.string.undo_wasted)

    private fun finish(item: PantryItem, status: ItemStatus, messageRes: Int) {
        viewModelScope.launch {
            val snapshot = repository.finishItem(item.id, status) ?: return@launch
            offerUndo(str(messageRes, snapshot.productName), UndoAction.Restore(snapshot))
        }
    }

    /**
     * Egy egyseg elfogyott: a mennyiseg eggyel csokken; az utolso egysegnel
     * (vagy ha nincs mennyiseg megadva) az egesz tetel elfogyottnak szamit.
     */
    fun consumeOne(item: PantryItem) {
        val qty = item.quantity ?: 1
        if (qty <= 1) {
            markConsumed(item)
            return
        }
        viewModelScope.launch {
            repository.upsertItem(item.copy(quantity = qty - 1))
            offerUndo(
                str(R.string.undo_consumed_one, item.productName, qty - 1, item.quantityUnit),
                UndoAction.Restore(item)
            )
        }
    }

    fun undo(action: UndoAction) {
        viewModelScope.launch {
            when (action) {
                is UndoAction.Restore -> repository.upsertItem(action.snapshot)
                is UndoAction.Remove -> repository.deleteItem(action.itemId)
            }
        }
    }

    private fun offerUndo(message: String, action: UndoAction) {
        undoChannel.trySend(UndoRequest(message, action))
    }

    // -- Vonalkod nelkuli gyorsracs --

    /**
     * Egy koppintasos felvitel becsult lejarattal (mindig "becsult" jelolessel,
     * kesobb pontosithato). `name`: a csempe lokalizalt neve.
     */
    fun addQuickProduct(product: QuickProduct, name: String) {
        val item = PantryItem(
            id = nextId(),
            barcode = null,
            productName = name,
            expiry = LocalDate.now().plusDays(product.estimateDays),
            recordedAt = LocalDate.now(),
            quantity = product.quantity,
            quantityUnit = defaultUnit,
            enteredManually = true,
            ambiguousDayMonth = false,
            expiryEstimated = true,
            location = product.location
        )
        onItemAdded(item.id)
        viewModelScope.launch {
            repository.upsertItem(item)
            repository.rememberProduct(name, defaultUnit)
            offerUndo(str(R.string.undo_quick_added, name), UndoAction.Remove(item.id))
        }
    }

    /** Bizonytalan nap/honap sorrendnel a ket ertek felcserelese. */
    fun swapDayMonth(item: PantryItem) {
        val d = item.expiry
        val swapped = try {
            LocalDate.of(d.year, d.dayOfMonth, d.monthValue)
        } catch (e: Exception) {
            return
        }
        viewModelScope.launch {
            repository.upsertItem(item.withExpiry(swapped).copy(ambiguousDayMonth = false))
        }
    }
}
