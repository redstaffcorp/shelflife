package hu.rsc.shelflife.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.app.DatePickerDialog
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
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
import hu.rsc.shelflife.scanner.ScanPhase
import hu.rsc.shelflife.scanner.ScannerAnalyzer
import hu.rsc.shelflife.ui.theme.ExpiryFresh
import hu.rsc.shelflife.ui.theme.ExpiryOverdue
import hu.rsc.shelflife.ui.theme.ExpirySoon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy. MM. dd.")

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
    val recordedAt: LocalDate
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
    // A felvett tetelek listaja perzisztens (SharedPreferences+JSON), hogy a
    // lejarati ertesiteseket ellenorzo hatterfeladat (WorkManager) akkor is
    // lassa a tetelt, ha az app epp nincs megnyitva.
    val pantryStore = remember { PantryItemStore(context) }
    val notificationSettings = remember { NotificationSettingsStore(context) }

    var uiState by remember { mutableStateOf<UiState>(UiState.ScanningBarcode) }
    val items = remember { mutableStateListOf<PantryItem>().apply { addAll(pantryStore.loadAll()) } }
    fun persistItems() = pantryStore.saveAll(items.toList())

    val barcodeSighting = remember { mutableStateOf<Pair<String?, Int>>(null to 0) }
    val dateSighting = remember { mutableStateOf<Pair<LocalDate?, Int>>(null to 0) }
    // Az aktualisan felviteli fazisban levo tetel opcionalis mennyisege.
    // Minden uj ScanningDate-fazisba lepeskor nullazodik.
    var quantityValue by remember { mutableStateOf<Int?>(null) }
    // Kezi (kamera nelkuli) uj felvitel VAGY egy meglevo tetel szerkesztese.
    var manualEditState by remember { mutableStateOf<ManualEditState?>(null) }
    var showNotificationSettings by remember { mutableStateOf(false) }

    fun handleBarcode(value: String) {
        if (uiState !is UiState.ScanningBarcode) return
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
        quantityValue = null
        uiState = if (known != null) {
            UiState.ScanningDate(barcode = value, productName = known)
        } else {
            UiState.AskingProductName(barcode = value)
        }
    }

    fun handleDateCandidate(candidate: DateCandidate) {
        val state = uiState
        if (state !is UiState.ScanningDate) return
        val (lastDate, count) = dateSighting.value
        val newCount = if (lastDate == candidate.date) count + 1 else 1
        dateSighting.value = candidate.date to newCount
        if (newCount < 3) return
        dateSighting.value = null to 0
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        items.add(
            0,
            PantryItem(
                id = System.currentTimeMillis(),
                barcode = state.barcode,
                productName = state.productName,
                expiry = candidate.date,
                recordedAt = LocalDate.now(),
                quantity = quantityValue,
                enteredManually = false,
                ambiguousDayMonth = candidate.ambiguousDayMonth
            )
        )
        persistItems()
        uiState = UiState.ScanningBarcode
    }

    fun finishWithManualDate(date: LocalDate) {
        val state = uiState
        if (state !is UiState.ScanningDate) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        items.add(
            0,
            PantryItem(
                id = System.currentTimeMillis(),
                barcode = state.barcode,
                productName = state.productName,
                expiry = date,
                recordedAt = LocalDate.now(),
                quantity = quantityValue,
                enteredManually = true,
                ambiguousDayMonth = false
            )
        )
        persistItems()
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
            FloatingActionButton(onClick = {
                manualEditState = ManualEditState(
                    id = null,
                    barcode = null,
                    name = "",
                    expiry = LocalDate.now(),
                    quantity = null,
                    recordedAt = LocalDate.now()
                )
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Kezi felvitel")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                CameraPreview(analyzer = analyzer)

                if (uiState is UiState.ScanningDate) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.8f)
                            .fillMaxHeight(0.28f)
                            .border(3.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp))
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp),
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
                        FilledTonalButton(onClick = { finishWithManualDate(LocalDate.now().plusWeeks(1)) }) {
                            Text("+1 het")
                        }
                        FilledTonalButton(onClick = { finishWithManualDate(LocalDate.now().plusMonths(1)) }) {
                            Text("+1 honap")
                        }
                        FilledTonalButton(onClick = { finishWithManualDate(LocalDate.now().plusYears(1)) }) {
                            Text("+1 ev")
                        }
                    }
                }
            }

            if (uiState is UiState.ScanningDate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { uiState = UiState.ScanningBarcode }) {
                        Text("Megse")
                    }
                    OutlinedButton(onClick = {
                        val today = LocalDate.now()
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                finishWithManualDate(LocalDate.of(year, month + 1, day))
                            },
                            today.year,
                            today.monthValue - 1,
                            today.dayOfMonth
                        ).show()
                    }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nem olvashato - datum megadasa")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Mennyiseg (opcionalis)", style = MaterialTheme.typography.bodyMedium)
                    QuantityStepper(value = quantityValue, onChange = { quantityValue = it })
                }
            }

            HorizontalDivider()

            Text(
                text = "Felvett tetelek (${items.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    PantryItemCard(
                        item = item,
                        onEdit = {
                            manualEditState = ManualEditState(
                                id = item.id,
                                barcode = item.barcode,
                                name = item.productName,
                                expiry = item.expiry,
                                quantity = item.quantity,
                                recordedAt = item.recordedAt
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
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Termek neve") },
                            singleLine = true
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
                            quantityValue = null
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

            AlertDialog(
                onDismissRequest = { manualEditState = null },
                title = { Text(if (editState.id == null) "Uj tetel kezi felvitele" else "Tetel szerkesztese") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Termek neve") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Lejarat: " + expiryInput.format(dateFormatter))
                            TextButton(onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        expiryInput = LocalDate.of(year, month + 1, day)
                                    },
                                    expiryInput.year,
                                    expiryInput.monthValue - 1,
                                    expiryInput.dayOfMonth
                                ).show()
                            }) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Modositas")
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mennyiseg (opcionalis)", style = MaterialTheme.typography.bodyMedium)
                            QuantityStepper(value = quantityInput, onChange = { quantityInput = it })
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
                                        enteredManually = true,
                                        ambiguousDayMonth = false
                                    )
                                )
                            } else {
                                val idx = items.indexOfFirst { it.id == editState.id }
                                if (idx >= 0) {
                                    val old = items[idx]
                                    items[idx] = old.copy(
                                        productName = name,
                                        expiry = expiryInput,
                                        quantity = quantityInput,
                                        ambiguousDayMonth = false,
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

    Card(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
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
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "$urgencyLabel  ·  ${item.expiry.format(dateFormatter)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = urgencyColor
                )
                Text(
                    "Rogzitve: ${item.recordedAt.format(dateFormatter)}" +
                        (if (item.enteredManually) "  ·  kezi" else "") +
                        (item.quantity?.let { "  ·  Mennyiseg: $it" } ?: ""),
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

@Composable
private fun CameraPreview(analyzer: ScannerAnalyzer) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        val cameraProvider = context.getCameraProvider()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(ContextCompat.getMainExecutor(context), analyzer) }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis
        )
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
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
