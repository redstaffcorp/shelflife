package hu.rsc.shelflife.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.app.DatePickerDialog
import android.os.Build
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.util.Size
import android.view.MotionEvent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import hu.rsc.shelflife.data.NotificationSettingsStore
import hu.rsc.shelflife.data.OpenFoodFactsClient
import hu.rsc.shelflife.data.PantryItem
import hu.rsc.shelflife.data.PantryItemStore
import hu.rsc.shelflife.data.ProductStore
import hu.rsc.shelflife.data.UpcItemDbClient
import hu.rsc.shelflife.data.isOnline
import hu.rsc.shelflife.notify.NotificationHelper
import hu.rsc.shelflife.notify.ReminderScheduler
import hu.rsc.shelflife.ocr.DateCandidate
import hu.rsc.shelflife.ocr.QuickDateInput
import hu.rsc.shelflife.scanner.OcrDebugInfo
import hu.rsc.shelflife.scanner.ScanPhase
import hu.rsc.shelflife.scanner.ScannerAnalyzer
import hu.rsc.shelflife.ui.theme.ExpiryFresh
import hu.rsc.shelflife.ui.theme.ExpiryOverdue
import hu.rsc.shelflife.ui.theme.ExpirySoon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy. MM. dd.")
private val longDateFormatter = DateTimeFormatter.ofPattern("yyyy. MMMM d.", Locale.forLanguageTag("hu"))

// Rogzites utan ennyi ideig nem szabad latszania ugyanannak a vonalkodnak,
// hogy ujra beolvashato legyen (lasd recentlyRecorded).
private const val BARCODE_COOLDOWN_MS = 1500L

// "Kesobb" gombnal hasznalt fix becsult lejarat (ma + ennyi nap). Tudatosan NEM
// a termek korabbi rogzitesebol szamoljuk: a lejarat a gyartasbol kovetkezik,
// nem a vasarlas/rogzites napjabol, igy a korabbi "hatralevo napok" nem jelzik
// elore a mostanit. A tetel mindig "becsult" jelolest kap.
private const val DEFAULT_ESTIMATE_DAYS = 7

/** A felvett tetelek listajanak rendezese. */
enum class ItemSortMode { BY_EXPIRY, BY_RECORDED }

/** Friss rogzites kiemelese a listaban. */
private enum class ItemHighlight { NONE, SESSION, LATEST }

sealed interface UiState {
    data object ScanningBarcode : UiState
    data class AskingProductName(val barcode: String) : UiState
    data class ScanningDate(val barcode: String, val productName: String) : UiState
}

/**
 * Egy tetel letrehozasa VAGY szerkesztese kezzel, kamera nelkul. `id == null`
 * -> uj tetel; `id != null` -> egy meglevo tetel szerkesztese (a listaban
 * ra koppintva erheto el).
 */
data class ManualEditState(
    val id: Long?,
    val barcode: String?,
    val name: String,
    val expiry: LocalDate,
    val quantity: Int?,
    val quantityUnit: String = ProductStore.DEFAULT_UNIT,
    val recordedAt: LocalDate,
    val expiryEstimated: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // Helyi cache: barcode -> nev. EZ AZ ELSO HELY, AHOL KERESUNK -- ha egy
    // vonalkodot mar egyszer megismertunk (akar online lekerdezesbol, akar
    // kezi bevitelbol), ide elmentjuk, es legkozelebb ugyanahhoz a
    // vonalkodhoz mar egyaltalan nem megy ki halozati hivas (lasd
    // handleBarcode: eloszor mindig ezt nezzuk meg).
    val productStore = remember { ProductStore(context) }
    // Gyors (szamjegyes) datumbevitel dialogus a datum-fazisban.
    var showQuickDateForScan by remember { mutableStateOf(false) }
    // A felvett tetelek listaja perzisztens (SharedPreferences+JSON), hogy a
    // lejarati ertesiteseket ellenorzo hatterfeladat (WorkManager) akkor is
    // lassa a tetelt, ha az app epp nincs megnyitva.
    val pantryStore = remember { PantryItemStore(context) }
    val notificationSettings = remember { NotificationSettingsStore(context) }

    var uiState by remember { mutableStateOf<UiState>(UiState.ScanningBarcode) }
    val items = remember { mutableStateListOf<PantryItem>().apply { addAll(pantryStore.loadAll()) } }
    fun persistItems() = pantryStore.saveAll(items.toList())

    val barcodeSighting = remember { mutableStateOf<Pair<String?, Int>>(null to 0) }
    // datum -> hanyszor lattuk az aktualis datum-fazisban (tobb kepvarians kozotti szavazas)
    val dateSighting = remember { mutableStateOf<Map<LocalDate, Int>>(emptyMap()) }
    // Az utoljara rogzitett tetel vonalkodja + mikor lattuk utoljara. Rogzites
    // utan (fokent kezi datummegadasnal) a termek meg a kamera elott van, igy
    // a vonalkod azonnal ujra beolvasodna, es az app visszaugrana ugyanannak a
    // termeknek a datum-rogzitesebe ("beragad"). Ezt a vonalkodot addig
    // figyelmen kivul hagyjuk, amig legalabb BARCODE_COOLDOWN_MS-ig nem latszik.
    val recentlyRecorded = remember { mutableStateOf<Pair<String?, Long>>(null to 0L) }
    // Az aktualisan felviteli fazisban levo tetel opcionalis mennyisege.
    // Minden uj ScanningDate-fazisba lepeskor nullazodik.
    var quantityValue by remember { mutableStateOf<Int?>(null) }
    // A quantityValue-hoz tartozo mertekegyseg -- alapertelmezetten "darab",
    // ismert termeknel a korabban elmentett mertekegyseg toltodik be (lasd
    // handleBarcode es az "Uj termek" dialogus Mentes gombja).
    var quantityUnitValue by remember { mutableStateOf(ProductStore.DEFAULT_UNIT) }
    // Kezi (kamera nelkuli) uj felvitel VAGY egy meglevo tetel szerkesztese.
    var manualEditState by remember { mutableStateOf<ManualEditState?>(null) }
    var showNotificationSettings by remember { mutableStateOf(false) }
    // Alapertelmezetten lejarat szerint rendezunk (a hamarosan lejarok elol).
    var sortMode by rememberSaveable { mutableStateOf(ItemSortMode.BY_EXPIRY) }
    val listState = rememberLazyListState()
    // A legutobb felvett tetel (erre gorgetunk es ezt emeljuk ki erosen), es
    // az aktualis rogzitesi korben felvett tetelek (enyhe kiemeles). A kor
    // kiemelese a kovetkezo "Termek beolvasasa" inditasakor torlodik.
    var lastAddedId by remember { mutableStateOf<Long?>(null) }
    val sessionAddedIds = remember { mutableStateListOf<Long>() }
    fun onItemAdded(id: Long) {
        sessionAddedIds.add(id)
        lastAddedId = id
    }
    // "Becsult" szuro: csak a meg pontositando (becsult lejaratu) tetelek.
    var showOnlyEstimated by rememberSaveable { mutableStateOf(false) }
    val sortedItems by remember {
        derivedStateOf {
            val visible = if (showOnlyEstimated) items.filter { it.expiryEstimated } else items.toList()
            when (sortMode) {
                ItemSortMode.BY_EXPIRY -> visible.sortedWith(
                    compareBy<PantryItem> { it.expiry }.thenByDescending { it.id }
                )
                // Legutobb felvett elol (az id a felvitel idopontja ms-ban).
                ItemSortMode.BY_RECORDED -> visible.sortedByDescending { it.id }
            }
        }
    }
    // Igazi kamerahasznalat csak akkor tortenik, ha a felhasznalo aktivan
    // elindit egy "rogzitesi kort" -- alapertelmezetten a teljes lista
    // latszik, a kamera nem fut feleslegesen a hatterben.
    var cameraSessionActive by remember { mutableStateOf(false) }
    // Zseblampa (vaku folyamatos fenye) a rogzitesi kor alatt. Amig az app
    // hasznalja a kamerat, a rendszer gyorsbeallitasbol nem kapcsolhato, ezert
    // az appbol kell tudni kapcsolni.
    var torchOn by remember { mutableStateOf(false) }
    // Fejlesztoi OCR-diagnosztika: mit olvas ki az ML Kit az egyes kepvariansokbol.
    var ocrDebugMode by remember { mutableStateOf(false) }
    val ocrDebugInfos = remember { mutableStateMapOf<String, OcrDebugInfo>() }
    // Teljes szoveges log a diagnosztika alatt; kikapcsolaskor vagolapra kerul.
    val ocrDebugLog = remember { mutableStateListOf<String>() }
    val ocrDebugLastRaw = remember { mutableMapOf<String, String>() }
    val ocrDebugTimeFmt = remember { DateTimeFormatter.ofPattern("HH:mm:ss.SSS") }

    fun copyOcrDebugLog() {
        val header = "ShelfLife OCR debug | ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})" +
            " | kepkocka: ${ocrDebugInfos.values.firstOrNull()?.frameSize ?: "?"}" +
            " | ${ocrDebugLog.size} bejegyzes"
        val text = (listOf(header) + ocrDebugLog).joinToString("\n")
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("ShelfLife OCR debug", text))
        Toast.makeText(context, "OCR log vagolapra masolva (${ocrDebugLog.size} sor)", Toast.LENGTH_SHORT).show()
    }
    var hasFlashUnit by remember { mutableStateOf(false) }

    fun markRecorded(barcode: String) {
        recentlyRecorded.value = barcode to System.currentTimeMillis()
        barcodeSighting.value = null to 0
        dateSighting.value = emptyMap()
    }

    fun startScanningSession() {
        uiState = UiState.ScanningBarcode
        recentlyRecorded.value = null to 0L
        barcodeSighting.value = null to 0
        dateSighting.value = emptyMap()
        quantityValue = 1
        quantityUnitValue = ProductStore.DEFAULT_UNIT
        torchOn = false
        sessionAddedIds.clear()
        lastAddedId = null
        cameraSessionActive = true
    }

    fun endScanningSession() {
        cameraSessionActive = false
        torchOn = false
        uiState = UiState.ScanningBarcode
        barcodeSighting.value = null to 0
        dateSighting.value = emptyMap()
    }

    fun handleBarcode(value: String) {
        if (uiState !is UiState.ScanningBarcode) return
        val now = System.currentTimeMillis()
        val (recentCode, recentSeenAt) = recentlyRecorded.value
        if (recentCode == value && now - recentSeenAt < BARCODE_COOLDOWN_MS) {
            // Meg mindig a most rogzitett termek van a kepen -> csusztatjuk a
            // turelmi idot, amig el nem tunik a kamera elol.
            recentlyRecorded.value = value to now
            return
        }
        val (lastVal, count) = barcodeSighting.value
        val newCount = if (lastVal == value) count + 1 else 1
        barcodeSighting.value = value to newCount
        if (newCount < 2) return
        barcodeSighting.value = null to 0
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        // 1. lepes: MINDIG eloszor a helyi cache-ben nezzuk meg. Csak akkor
        // nyilik meg az "ismeretlen termek" dialogus (es azzal egyutt az
        // online kereses), ha ez nem talal semmit.
        val known = productStore.get(value)
        quantityValue = 1
        quantityUnitValue = known?.let { productStore.getUnit(it) } ?: ProductStore.DEFAULT_UNIT
        uiState = if (known != null) {
            UiState.ScanningDate(barcode = value, productName = known)
        } else {
            UiState.AskingProductName(barcode = value)
        }
    }

    fun handleDateCandidate(candidate: DateCandidate) {
        val state = uiState
        if (state !is UiState.ScanningDate) return
        // Nem kell egymas utan 3x ugyanaz: a kepvariansok kozul nehany mast (vagy semmit)
        // lathat, ezert osszesitve szamolunk. Kulcsszo nelkuli talalatnal egy szavazattal tobb kell.
        val counts = dateSighting.value.toMutableMap()
        val newCount = (counts[candidate.date] ?: 0) + 1
        counts[candidate.date] = newCount
        dateSighting.value = counts
        val needed = if (candidate.hasPositiveKeyword) 3 else 4
        if (newCount < needed) return
        dateSighting.value = emptyMap()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        productStore.saveKnownProduct(state.productName, quantityUnitValue)
        items.add(
            0,
            PantryItem(
                id = System.currentTimeMillis(),
                barcode = state.barcode,
                productName = state.productName,
                expiry = candidate.date,
                recordedAt = LocalDate.now(),
                quantity = quantityValue,
                quantityUnit = quantityUnitValue,
                enteredManually = false,
                ambiguousDayMonth = candidate.ambiguousDayMonth
            )
        )
        persistItems()
        onItemAdded(items[0].id)
        markRecorded(state.barcode)
        uiState = UiState.ScanningBarcode
    }

    /**
     * Datum-fazis lezarasa nem-OCR uton. `estimated = true`: becsles ("~1 het",
     * "Kesobb") -- a tetel "becsult, pontositando" jelolest kap.
     */
    fun finishWithManualDate(date: LocalDate, estimated: Boolean = false) {
        val state = uiState
        if (state !is UiState.ScanningDate) return
        showQuickDateForScan = false
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        productStore.saveKnownProduct(state.productName, quantityUnitValue)
        items.add(
            0,
            PantryItem(
                id = System.currentTimeMillis(),
                barcode = state.barcode,
                productName = state.productName,
                expiry = date,
                recordedAt = LocalDate.now(),
                quantity = quantityValue,
                quantityUnit = quantityUnitValue,
                enteredManually = true,
                ambiguousDayMonth = false,
                expiryEstimated = estimated
            )
        )
        persistItems()
        onItemAdded(items[0].id)
        markRecorded(state.barcode)
        uiState = UiState.ScanningBarcode
    }

    val analyzer = remember {
        ScannerAnalyzer(
            context = context,
            phaseProvider = {
                when (uiState) {
                    is UiState.ScanningBarcode -> ScanPhase.SCANNING_BARCODE
                    is UiState.ScanningDate -> ScanPhase.SCANNING_DATE
                    else -> ScanPhase.PAUSED
                }
            },
            onBarcodeDetected = { handleBarcode(it) },
            onDateCandidate = { handleDateCandidate(it) }
        )
    }

    // Ha elhagyjuk a datum-fazist (pl. az OCR kozben mentett, vagy Megse), a gyors
    // datumbevitel dialogus ne maradjon "felhuzva" a kovetkezo termekre.
    LaunchedEffect(uiState) {
        if (uiState !is UiState.ScanningDate) showQuickDateForScan = false
    }

    LaunchedEffect(ocrDebugMode) {
        if (ocrDebugMode) {
            ocrDebugInfos.clear()
            ocrDebugLog.clear()
            ocrDebugLastRaw.clear()
            analyzer.debugListener = { info ->
                ocrDebugInfos[info.variant] = info
                // Csak akkor naplozunk, ha az adott varians kimenete valtozott (kulonben 5 sor/mp).
                val key = info.rawText + "#" + info.parsed
                if (ocrDebugLastRaw[info.variant] != key) {
                    ocrDebugLastRaw[info.variant] = key
                    ocrDebugLog.add(
                        "${java.time.LocalTime.now().format(ocrDebugTimeFmt)} ${info.variant}" +
                            " => ${info.parsed ?: "-"} | \"${info.rawText}\""
                    )
                    if (ocrDebugLog.size > 500) ocrDebugLog.removeAt(0)
                }
            }
        } else {
            analyzer.debugListener = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ShelfLife", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Kamra es lejarat-kovetes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showNotificationSettings = true }) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Ertesitesi beallitasok")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (cameraSessionActive) {
                    ExtendedFloatingActionButton(
                        onClick = { endScanningSession() },
                        icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                        text = { Text("Kesz - lezaras") }
                    )
                } else {
                    ExtendedFloatingActionButton(
                        onClick = { startScanningSession() },
                        icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                        text = { Text("Termek beolvasasa") }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                FloatingActionButton(onClick = {
                    manualEditState = ManualEditState(
                        id = null,
                        barcode = null,
                        name = "",
                        // Alapertelmezett lejarat "ma+3 nap" -- realisztikusabb
                        // kiindulopont, mint a mai nap, amit amugy is szinte
                        // mindig at kellene irni.
                        expiry = LocalDate.now().plusDays(3),
                        quantity = 1,
                        quantityUnit = ProductStore.DEFAULT_UNIT,
                        recordedAt = LocalDate.now()
                    )
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Kezi felvitel")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (cameraSessionActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                CameraPreview(
                    analyzer = analyzer,
                    torchEnabled = torchOn,
                    onHasFlashUnit = { hasFlashUnit = it },
                    dateMode = uiState is UiState.ScanningDate
                )

                if (uiState is UiState.ScanningDate) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.8f)
                            .fillMaxHeight(0.28f)
                            .border(3.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp))
                    )

                    FilledIconToggleButton(
                        checked = ocrDebugMode,
                        onCheckedChange = { on ->
                            // Kikapcsolaskor a teljes log automatikusan a vagolapra kerul.
                            if (!on && ocrDebugLog.isNotEmpty()) copyOcrDebugLog()
                            ocrDebugMode = on
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Filled.BugReport, contentDescription = "OCR diagnosztika")
                    }

                    if (ocrDebugMode) {
                        OcrDebugPanel(
                            infos = ocrDebugInfos.values.sortedBy { it.variant },
                            logSize = ocrDebugLog.size,
                            onCopy = { copyOcrDebugLog() },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 8.dp, end = 8.dp, top = 60.dp)
                        )
                    }
                }

                if (hasFlashUnit) {
                    FilledIconToggleButton(
                        checked = torchOn,
                        onCheckedChange = { torchOn = it },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = if (torchOn) "Lampa kikapcsolasa" else "Lampa bekapcsolasa"
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // Jobbra hely a lampa-gombnak, hogy a felirat ne takarja.
                        .padding(start = 60.dp, end = 60.dp, top = 12.dp),
                    color = if (uiState is UiState.ScanningDate) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (uiState is UiState.ScanningDate) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 2.dp
                ) {
                    Text(
                        text = when (val s = uiState) {
                            is UiState.ScanningBarcode -> "Mutasd a vonalkodot a kameranak"
                            is UiState.AskingProductName -> "Ismeretlen termek - add meg a nevet"
                            is UiState.ScanningDate -> "\"${s.productName}\" - tartsd a lejarati datumot a keretben"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                if (uiState is UiState.ScanningDate) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilledTonalButton(onClick = { finishWithManualDate(LocalDate.now().plusWeeks(1), estimated = true) }) {
                            Text("~1 het")
                        }
                        FilledTonalButton(onClick = { finishWithManualDate(LocalDate.now().plusMonths(1), estimated = true) }) {
                            Text("~1 honap")
                        }
                        FilledTonalButton(onClick = { finishWithManualDate(LocalDate.now().plusYears(1), estimated = true) }) {
                            Text("~1 ev")
                        }
                    }
                }
            }

            val dateState = uiState as? UiState.ScanningDate
            if (dateState != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { uiState = UiState.ScanningBarcode }) {
                        Text("Megse")
                    }
                    // Soha ne akadjon el a rogzites egy olvashatatlan datumon: fix becsult
                    // datummal mentjuk (ma + DEFAULT_ESTIMATE_DAYS), "becsult" jelolessel,
                    // es a lista "Becsult" szurojevel kesobb pontosithato.
                    TextButton(onClick = {
                        finishWithManualDate(
                            LocalDate.now().plusDays(DEFAULT_ESTIMATE_DAYS.toLong()),
                            estimated = true
                        )
                    }) {
                        Text("Kesobb")
                    }
                    OutlinedButton(onClick = { showQuickDateForScan = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Datum beirasa")
                    }
                }
                if (showQuickDateForScan) {
                    QuickDateDialog(
                        // A naptar kiindulo erteke: ma+3 nap, nem a mai nap (lasd FAB kezi felvitel).
                        calendarDefault = LocalDate.now().plusDays(3),
                        onDismiss = { showQuickDateForScan = false },
                        onConfirm = { finishWithManualDate(it) }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("Mennyiseg (opcionalis)", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QuantityStepper(value = quantityValue, onChange = { quantityValue = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        UnitSelector(value = quantityUnitValue, onChange = { quantityUnitValue = it })
                    }
                }
            }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "Uj termekek felvetelehez nyomd meg a \"Termek beolvasasa\" gombot -- a kamera csak akkor kapcsol be.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Felvett tetelek (${items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = sortMode == ItemSortMode.BY_EXPIRY,
                    onClick = { sortMode = ItemSortMode.BY_EXPIRY },
                    label = { Text("Lejarat") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = sortMode == ItemSortMode.BY_RECORDED,
                    onClick = { sortMode = ItemSortMode.BY_RECORDED },
                    label = { Text("Felvitel") }
                )
            }

            val estimatedCount = items.count { it.expiryEstimated }
            LaunchedEffect(estimatedCount) {
                if (estimatedCount == 0) showOnlyEstimated = false
            }
            if (estimatedCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = showOnlyEstimated,
                        onClick = { showOnlyEstimated = !showOnlyEstimated },
                        label = { Text("Becsult datum: $estimatedCount - pontositando") }
                    )
                }
            }

            // Uj tetel utan odagorgetunk, ahova a rendezes szerint bekerult --
            // igy rogzites kozben azonnal lathato, kezi gorgetes nelkul.
            LaunchedEffect(lastAddedId, sortMode) {
                val id = lastAddedId ?: return@LaunchedEffect
                val idx = sortedItems.indexOfFirst { it.id == id }
                if (idx >= 0) listState.animateScrollToItem(idx)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (cameraSessionActive) 0.45f else 1f),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(sortedItems, key = { it.id }) { item ->
                    PantryItemCard(
                        item = item,
                        highlight = when (item.id) {
                            lastAddedId -> ItemHighlight.LATEST
                            in sessionAddedIds -> ItemHighlight.SESSION
                            else -> ItemHighlight.NONE
                        },
                        onEdit = {
                            manualEditState = ManualEditState(
                                id = item.id,
                                barcode = item.barcode,
                                name = item.productName,
                                expiry = item.expiry,
                                quantity = item.quantity,
                                quantityUnit = item.quantityUnit,
                                recordedAt = item.recordedAt,
                                expiryEstimated = item.expiryEstimated
                            )
                        },
                        onDelete = {
                            items.removeAll { it.id == item.id }
                            persistItems()
                        },
                        onSwapDayMonth = {
                            val idx = items.indexOfFirst { it.id == item.id }
                            if (idx >= 0) {
                                val d = item.expiry
                                val swapped = try {
                                    LocalDate.of(d.year, d.dayOfMonth, d.monthValue)
                                } catch (e: Exception) {
                                    null
                                }
                                if (swapped != null) {
                                    // Uj lejarati datum -> a korabbi ertesites
                                    // erre a tetelre mar nem szamit, ujra
                                    // kuldheto, ha megint esedekesse valik.
                                    items[idx] = item.copy(
                                        expiry = swapped,
                                        ambiguousDayMonth = false,
                                        notifiedForExpiry = null
                                    )
                                    persistItems()
                                }
                            }
                        }
                    )
                }
            }
        }

        val askState = uiState
        if (askState is UiState.AskingProductName) {
            var nameInput by remember(askState.barcode) { mutableStateOf("") }
            val knownProducts = remember(askState.barcode) { productStore.allKnownProducts() }
            var isLookingUp by remember(askState.barcode) { mutableStateOf(true) }
            var statusText by remember(askState.barcode) { mutableStateOf("Kereses az Open Food Facts adatbazisban...") }
            var lookupNote by remember(askState.barcode) { mutableStateOf<String?>(null) }

            LaunchedEffect(askState.barcode) {
                isLookingUp = true

                if (!isOnline(context)) {
                    lookupNote = "Nincs internetkapcsolat - add meg kezzel."
                    isLookingUp = false
                    return@LaunchedEffect
                }

                // 2. lepes: eloszor az Open Food Facts.
                statusText = "Kereses az Open Food Facts adatbazisban..."
                var found = withContext(Dispatchers.IO) {
                    OpenFoodFactsClient.lookupProductName(askState.barcode)
                }
                var source = "Open Food Facts"

                // 3. lepes: ha ott nincs talalat, jon a masodik, tartalek
                // szolgaltatas (UPCitemdb).
                if (found == null) {
                    statusText = "Nincs talalat - proba a UPCitemdb-n..."
                    found = withContext(Dispatchers.IO) {
                        UpcItemDbClient.lookupProductName(askState.barcode)
                    }
                    source = "UPCitemdb"
                }

                if (found != null) {
                    nameInput = found
                    lookupNote = "Automatikusan kitoltve ($source) - ellenorizd, es javitsd, ha kell."
                } else {
                    // 4. lepes: egyik online forras sem talalt semmit -> kezi bevitel.
                    lookupNote = "Egyik online adatbazisban sem talalhato - add meg kezzel."
                }
                isLookingUp = false
            }

            AlertDialog(
                onDismissRequest = { uiState = UiState.ScanningBarcode },
                title = { Text("Uj termek") },
                text = {
                    Column {
                        Text("Vonalkod: ${askState.barcode}")
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isLookingUp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(statusText)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        } else {
                            lookupNote?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        ProductNameField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            knownProducts = knownProducts,
                            onProductSelected = { nameInput = it.name },
                            label = "Termek neve"
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = nameInput.isNotBlank(),
                        onClick = {
                            val name = nameInput.trim()
                            // A megerositett nev bekerul a helyi cache-be --
                            // legkozelebb mar innen jon, online kereses nelkul.
                            productStore.save(askState.barcode, name)
                            quantityValue = 1
                            quantityUnitValue = productStore.getUnit(name) ?: ProductStore.DEFAULT_UNIT
                            uiState = UiState.ScanningDate(barcode = askState.barcode, productName = name)
                        }
                    ) { Text("Mentes") }
                },
                dismissButton = {
                    TextButton(onClick = { uiState = UiState.ScanningBarcode }) { Text("Megse") }
                }
            )
        }

        // Teljesen kamera nelkuli felvitel VAGY egy meglevo tetel szerkesztese.
        val editState = manualEditState
        if (editState != null) {
            var nameInput by remember(editState) { mutableStateOf(editState.name) }
            var expiryInput by remember(editState) { mutableStateOf(editState.expiry) }
            var quantityInput by remember(editState) { mutableStateOf(editState.quantity) }
            var quantityUnitInput by remember(editState) { mutableStateOf(editState.quantityUnit) }
            // Ha a felhasznalo kezzel valaszt mertekegyseget, azt tobbe ne
            // iruk felul automatikusan, meg akkor sem, ha kozben egy ismert
            // termek nevet gepel be.
            var unitTouchedByUser by remember(editState) { mutableStateOf(false) }
            var showQuickDateInEdit by remember(editState) { mutableStateOf(false) }
            val knownProducts = remember { productStore.allKnownProducts() }
            var estimatedInput by remember(editState) { mutableStateOf(editState.expiryEstimated) }

            AlertDialog(
                onDismissRequest = { manualEditState = null },
                title = { Text(if (editState.id == null) "Uj tetel kezi felvitele" else "Tetel szerkesztese") },
                text = {
                    Column {
                        ProductNameField(
                            value = nameInput,
                            onValueChange = { newName ->
                                nameInput = newName
                                if (!unitTouchedByUser) {
                                    productStore.getUnit(newName)?.let { quantityUnitInput = it }
                                }
                            },
                            knownProducts = knownProducts,
                            onProductSelected = { product ->
                                nameInput = product.name
                                if (!unitTouchedByUser) {
                                    quantityUnitInput = product.unit
                                }
                            },
                            label = "Termek neve (vagy valassz a listabol)"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Lejarat: " + expiryInput.format(dateFormatter))
                            TextButton(onClick = { showQuickDateInEdit = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Modositas")
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(checked = estimatedInput, onCheckedChange = { estimatedInput = it })
                            Text(
                                "Becsult datum (kesobb pontositando)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (showQuickDateInEdit) {
                            QuickDateDialog(
                                calendarDefault = expiryInput,
                                onDismiss = { showQuickDateInEdit = false },
                                onConfirm = {
                                    expiryInput = it
                                    // Kezzel megadott datum -> mar nem becsles.
                                    estimatedInput = false
                                    showQuickDateInEdit = false
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Mennyiseg (opcionalis)", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                QuantityStepper(value = quantityInput, onChange = { quantityInput = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                UnitSelector(
                                    value = quantityUnitInput,
                                    onChange = {
                                        quantityUnitInput = it
                                        unitTouchedByUser = true
                                    }
                                )
                            }
                        }
                        if (editState.id != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Rogzitve: " + editState.recordedAt.format(dateFormatter),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = nameInput.isNotBlank(),
                        onClick = {
                            val name = nameInput.trim()
                            if (editState.barcode != null) {
                                productStore.save(editState.barcode, name)
                            }
                            productStore.saveKnownProduct(name, quantityUnitInput)
                            if (editState.id == null) {
                                items.add(
                                    0,
                                    PantryItem(
                                        id = System.currentTimeMillis(),
                                        barcode = editState.barcode,
                                        productName = name,
                                        expiry = expiryInput,
                                        recordedAt = editState.recordedAt,
                                        quantity = quantityInput,
                                        quantityUnit = quantityUnitInput,
                                        enteredManually = true,
                                        ambiguousDayMonth = false,
                                        expiryEstimated = estimatedInput
                                    )
                                )
                                onItemAdded(items[0].id)
                            } else {
                                val idx = items.indexOfFirst { it.id == editState.id }
                                if (idx >= 0) {
                                    val old = items[idx]
                                    items[idx] = old.copy(
                                        productName = name,
                                        expiry = expiryInput,
                                        quantity = quantityInput,
                                        quantityUnit = quantityUnitInput,
                                        ambiguousDayMonth = false,
                                        expiryEstimated = estimatedInput,
                                        // Ha a lejarat valtozott, a korabbi
                                        // ertesites mar nem szamit erre a
                                        // datumra -- ujra kuldheto.
                                        notifiedForExpiry = if (old.expiry != expiryInput) null else old.notifiedForExpiry
                                    )
                                }
                            }
                            persistItems()
                            manualEditState = null
                        }
                    ) { Text("Mentes") }
                },
                dismissButton = {
                    Row {
                        if (editState.id != null) {
                            TextButton(onClick = {
                                items.removeAll { it.id == editState.id }
                                persistItems()
                                manualEditState = null
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Torles", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = { manualEditState = null }) { Text("Megse") }
                    }
                }
            )
        }

        if (showNotificationSettings) {
            NotificationSettingsDialog(
                settingsStore = notificationSettings,
                onDismiss = { showNotificationSettings = false }
            )
        }
    }
}

/**
 * Lejarat-elotti ertesitesek be/kikapcsolasa es a napok szamanak
 * beallitasa. Az engedelykeres (Android 13+, POST_NOTIFICATIONS) tudatosan
 * ITT, a felhasznalo sajat kezdemenyezesere tortenik -- akkor kerdezunk
 * ra, amikor o eppen bekapcsolja az erintett funkciot -- ez a Google altal
 * ajanlott, kontextusban torteno engedelykeres mintaja (nem az app
 * inditasakor, minden magyarazat nelkul kerjuk be).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationSettingsDialog(
    settingsStore: NotificationSettingsStore,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(settingsStore.enabled) }
    var daysBefore by remember { mutableStateOf(settingsStore.daysBefore) }
    var showPermissionHint by remember { mutableStateOf(false) }

    // Ha kozben (a rendszerbeallitasokban) visszavontak az engedelyt, ezt itt
    // vegyuk eszre, es ne mutassunk "bekapcsolva" allapotot ertesites nelkul.
    LaunchedEffect(Unit) {
        if (enabled && !hasNotificationPermission(context)) {
            enabled = false
            settingsStore.enabled = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationHelper.ensureChannel(context)
            enabled = true
            settingsStore.enabled = true
            ReminderScheduler.schedule(context)
            showPermissionHint = false
        } else {
            enabled = false
            settingsStore.enabled = false
            showPermissionHint = true
        }
    }

    fun enableNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission(context)) {
            // Kontextusban kerjuk be a rendszer sajat engedelykero dialogusat
            // -- a felhasznalo eppen most kapcsolta be a funkciot, tehat
            // mar tudja, miert kerdezzuk.
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationHelper.ensureChannel(context)
            enabled = true
            settingsStore.enabled = true
            ReminderScheduler.schedule(context)
        }
    }

    fun disableNotifications() {
        enabled = false
        settingsStore.enabled = false
        showPermissionHint = false
        ReminderScheduler.cancel(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lejarati ertesitesek") },
        text = {
            Column {
                Text(
                    "Az app szolhat, mielott egy kamraban rogzitett termek lejar. " +
                        "A hatterben, naponta egyszer ellenorzi a listat -- nincs " +
                        "folyamatosan futo szolgaltatas, es a telefon kepe/kamerajanak " +
                        "hasznalata nelkul, csak a mar elmentett lejarati datumok alapjan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ertesites bekapcsolasa")
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            if (checked) enableNotifications() else disableNotifications()
                        }
                    )
                }

                if (showPermissionHint) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Az ertesitesi engedely nincs megadva, igy az app nem tud " +
                            "szolni. Engedelyezd a rendszerbeallitasokban, ha szeretnel " +
                            "lejarat-elotti emlekeztetot kapni.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }) {
                        Text("Beallitasok megnyitasa")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text("Hany nappal a lejarat elott szoljon?", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedIconButton(onClick = {
                        if (daysBefore > 0) {
                            daysBefore -= 1
                            settingsStore.daysBefore = daysBefore
                        }
                    }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Csokkentes")
                    }
                    Text(
                        text = if (daysBefore == 0) "aznap" else "$daysBefore nap",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .widthIn(min = 48.dp)
                    )
                    OutlinedIconButton(onClick = {
                        if (daysBefore < 30) {
                            daysBefore += 1
                            settingsStore.daysBefore = daysBefore
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Novekmenyes")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Kesz") }
        }
    )
}

private fun hasNotificationPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun PantryItemCard(
    item: PantryItem,
    highlight: ItemHighlight,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSwapDayMonth: () -> Unit
) {
    val today = LocalDate.now()
    val daysLeft = ChronoUnit.DAYS.between(today, item.expiry)
    val urgencyColor = when {
        daysLeft < 0 -> ExpiryOverdue
        daysLeft <= 3 -> ExpirySoon
        else -> ExpiryFresh
    }
    val urgencyLabel = when {
        daysLeft < 0 -> "Lejart"
        daysLeft == 0L -> "Ma jar le"
        daysLeft == 1L -> "Holnap jar le"
        else -> "$daysLeft nap mulva jar le"
    }

    val containerColor by animateColorAsState(
        when (highlight) {
            ItemHighlight.LATEST -> MaterialTheme.colorScheme.primaryContainer
            ItemHighlight.SESSION -> MaterialTheme.colorScheme.secondaryContainer
            ItemHighlight.NONE -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "itemHighlight"
    )

    Card(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (highlight == ItemHighlight.LATEST) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(urgencyColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
            ) {
                Text(item.productName, style = MaterialTheme.typography.titleMedium)
                if (highlight != ItemHighlight.NONE) {
                    Text(
                        if (highlight == ItemHighlight.LATEST) "UJ - most rogzitve" else "UJ - ebben a korben rogzitve",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    (if (item.expiryEstimated) "~ " else "") +
                        "$urgencyLabel  ·  ${item.expiry.format(dateFormatter)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = urgencyColor
                )
                if (item.expiryEstimated) {
                    Text(
                        "Becsult datum - koppints a pontositashoz",
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpirySoon
                    )
                }
                Text(
                    "Rogzitve: ${item.recordedAt.format(dateFormatter)}" +
                        (if (item.enteredManually) "  ·  kezi" else "") +
                        (item.quantity?.let { "  ·  Mennyiseg: $it ${item.quantityUnit}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.ambiguousDayMonth) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Nap/honap sorrend bizonytalan",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpirySoon
                        )
                        TextButton(onClick = onSwapDayMonth, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Csere")
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Szerkesztes")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Torles", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun QuantityStepper(value: Int?, onChange: (Int?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedIconButton(onClick = {
            onChange(
                when {
                    value == null -> null
                    value <= 1 -> null
                    else -> value - 1
                }
            )
        }) {
            Icon(Icons.Filled.Remove, contentDescription = "Mennyiseg csokkentese")
        }
        Text(
            text = value?.toString() ?: "-",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .widthIn(min = 24.dp)
        )
        OutlinedIconButton(onClick = { onChange((value ?: 0) + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "Mennyiseg novelese")
        }
    }
}

// Gyakori mennyisegi mertekegysegek gyors valasztashoz -- "Egyeb..." valasztva
// barmilyen szabad szoveg is megadhato.
private val commonQuantityUnits = listOf(
    "darab", "csomag", "doboz", "kg", "dkg", "g", "l", "dl", "ml", "uveg", "zacsko"
)

/**
 * Legordulo mertekegyseg-valaszto a QuantityStepper melle. Alapertelmezetten
 * "darab" (lasd ProductStore.DEFAULT_UNIT); ismert termeknel az utoljara
 * hasznalt mertekegyseg johet be `value`-kent kivulrol.
 */
@Composable
private fun UnitSelector(value: String, onChange: (String) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    var customDialogOpen by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf(value) }

    Box {
        OutlinedButton(onClick = { menuExpanded = true }) {
            Text(value)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Mertekegyseg valasztasa")
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            commonQuantityUnits.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit) },
                    onClick = {
                        onChange(unit)
                        menuExpanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Egyeb...") },
                onClick = {
                    menuExpanded = false
                    customText = value
                    customDialogOpen = true
                }
            )
        }
    }

    if (customDialogOpen) {
        AlertDialog(
            onDismissRequest = { customDialogOpen = false },
            title = { Text("Mertekegyseg") },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    label = { Text("Mertekegyseg") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (customText.isNotBlank()) onChange(customText.trim())
                    customDialogOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { customDialogOpen = false }) { Text("Megse") }
            }
        )
    }
}

/**
 * Termeknev-mezo beepitett, szures alapu javaslatlistaval a korabban mar
 * berogzitett (ismert) termekekbol -- ezek kozul lehet valasztani, vagy
 * tovabbra is szabadon be lehet gepelni egy uj termek nevet.
 */
@Composable
private fun ProductNameField(
    value: String,
    onValueChange: (String) -> Unit,
    knownProducts: List<ProductStore.KnownProduct>,
    onProductSelected: (ProductStore.KnownProduct) -> Unit,
    label: String
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val filtered = remember(value, knownProducts) {
        if (value.isBlank()) {
            knownProducts
        } else {
            knownProducts.filter { it.name.contains(value, ignoreCase = true) }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                menuExpanded = knownProducts.isNotEmpty()
            },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                if (knownProducts.isNotEmpty()) {
                    IconButton(onClick = { menuExpanded = !menuExpanded }) {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Meglevo termekek")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) menuExpanded = knownProducts.isNotEmpty() }
        )
        DropdownMenu(
            expanded = menuExpanded && filtered.isNotEmpty(),
            onDismissRequest = { menuExpanded = false }
        ) {
            filtered.take(8).forEach { product ->
                DropdownMenuItem(
                    text = { Text("${product.name}  ·  ${product.unit}") },
                    onClick = {
                        onProductSelected(product)
                        menuExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    analyzer: ScannerAnalyzer,
    torchEnabled: Boolean,
    onHasFlashUnit: (Boolean) -> Unit,
    dateMode: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val scope = rememberCoroutineScope()
    var camera by remember { mutableStateOf<Camera?>(null) }
    // Koppintasra fokuszalas: hol volt a koppintas (a jelzokarikahoz) es mikor.
    var focusIndicator by remember { mutableStateOf<Offset?>(null) }
    var lastManualFocusMs by remember { mutableLongStateOf(0L) }

    fun focusAt(x: Float, y: Float) {
        val cam = camera ?: return
        if (previewView.width == 0 || previewView.height == 0) return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        )
            // Utana visszaall a folyamatos autofokuszra.
            .setAutoCancelDuration(4, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    DisposableEffect(previewView) {
        previewView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                lastManualFocusMs = System.currentTimeMillis()
                focusIndicator = Offset(event.x, event.y)
                focusAt(event.x, event.y)
                v.performClick()
            }
            true
        }
        onDispose { previewView.setOnTouchListener(null) }
    }

    LaunchedEffect(focusIndicator) {
        if (focusIndicator != null) {
            delay(900)
            focusIndicator = null
        }
    }

    // Datum-fazisban a keret (kep kozepe) ele fokuszalunk, es idonkent
    // ujra, mert a folyamatos AF kis, kozeli feliratnal gyakran a hatterre
    // all be. Friss kezi koppintast nem irunk felul.
    LaunchedEffect(camera, dateMode) {
        if (camera == null || !dateMode) return@LaunchedEffect
        delay(300)
        while (true) {
            if (System.currentTimeMillis() - lastManualFocusMs > 5000) {
                focusAt(previewView.width / 2f, previewView.height / 2f)
            }
            delay(5000)
        }
    }

    // A lampa allapotat mindig a kivalasztott ertekhez igazitjuk -- akkor is,
    // ha a kamera csak kesobb (aszinkron) kotodik be.
    LaunchedEffect(camera, torchEnabled) {
        camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(torchEnabled)
            }
        }
    }

    // DisposableEffect, nem csak LaunchedEffect: amikor ez a Composable
    // kikerul a kompoziciobol (pl. a felhasznalo lezarja a rogzitesi
    // kort, es a kamera-doboz eltunik a kepernyorol), a kamerat is
    // tenylegesen el kell engedni (unbindAll), nem csak elrejteni a
    // nezetet -- kulonben feleslegesen tovabb futna a hatterben.
    DisposableEffect(Unit) {
        var boundProvider: ProcessCameraProvider? = null
        scope.launch {
            val cameraProvider = context.getCameraProvider()
            boundProvider = cameraProvider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                // Az alapertelmezett 640x480 kicsi az apro datum-felirathoz;
                // 1080p-t kerunk, mert a datum-fazisban a kep kozepet vagjuk ki, igy a
                // kivagas is eleg felbontasu marad az apro pontmatrix szamjegyekhez.
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(ContextCompat.getMainExecutor(context), analyzer) }

            cameraProvider.unbindAll()
            val bound = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
            camera = bound
            onHasFlashUnit(bound.cameraInfo.hasFlashUnit())
        }
        onDispose {
            // unbindAll a lampat is lekapcsolja.
            camera = null
            onHasFlashUnit(false)
            boundProvider?.unbindAll()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        focusIndicator?.let { pos ->
            val ringColor = MaterialTheme.colorScheme.primaryContainer
            Canvas(
                modifier = Modifier
                    .offset { IntOffset((pos.x - 60f).roundToInt(), (pos.y - 60f).roundToInt()) }
                    .size(40.dp)
            ) {
                drawCircle(color = ringColor, radius = 60f, center = Offset(60f, 60f), style = Stroke(width = 5f))
            }
        }
    }
}

private suspend fun android.content.Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { future ->
            future.addListener(
                { continuation.resume(future.get()) },
                ContextCompat.getMainExecutor(this)
            )
        }
    }

/**
 * Kompakt fejlesztoi sav: hany log-sor gyult, talalt-e mar datumot valamelyik varians,
 * es egy gomb a log vagolapra masolasahoz. A kamerakepet nem takarja ki.
 */
@Composable
private fun OcrDebugPanel(
    infos: List<OcrDebugInfo>,
    logSize: Int,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hit = infos.firstOrNull { it.parsed != null }
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Log: $logSize sor" + (hit?.let { " | talalat: ${it.parsed}" } ?: " | nincs talalat"),
            color = if (hit != null) Color(0xFF8BC34A) else Color.White,
            fontSize = 11.sp,
            maxLines = 1
        )
        TextButton(onClick = onCopy) {
            Text("Masolas", fontSize = 11.sp)
        }
    }
}

/**
 * Gyors lejarati datum bevitel szamjegyekkel ("2510" = okt. 25., "251026", "25102026"),
 * elo elonezettel. A naptaras valaszto egy gombnyomasra tovabbra is elerheto.
 */
@Composable
private fun QuickDateDialog(
    calendarDefault: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val today = LocalDate.now()
    val parsed = QuickDateInput.parse(text, today)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lejarati datum") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it in " ./-" }.take(10) },
                    label = { Text("Nap, honap (ev)") },
                    placeholder = { Text("pl. 2510 vagy 251026") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { parsed?.let(onConfirm) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    parsed != null -> {
                        val days = ChronoUnit.DAYS.between(today, parsed)
                        Text(
                            parsed.format(longDateFormatter) + when {
                                days < 0 -> " - mar lejart!"
                                days == 0L -> " - ma"
                                else -> " - $days nap mulva"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (days < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    text.isNotBlank() -> Text(
                        "Nem ertelmezheto datum",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Text(
                        "Evszam nelkul a legkozelebbi ilyen datumot veszi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = {
                    val start = parsed ?: calendarDefault
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> onConfirm(LocalDate.of(year, month + 1, day)) },
                        start.year,
                        start.monthValue - 1,
                        start.dayOfMonth
                    ).show()
                }) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Naptarbol")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let(onConfirm) }) { Text("Mentes") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Megse") }
        }
    )
}
