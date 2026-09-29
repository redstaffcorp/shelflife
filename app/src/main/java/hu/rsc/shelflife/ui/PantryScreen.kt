package hu.rsc.shelflife.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.rsc.shelflife.R
import hu.rsc.shelflife.data.NotificationSettingsStore
import hu.rsc.shelflife.data.PantryItem
import hu.rsc.shelflife.data.StorageLocation
import hu.rsc.shelflife.scanner.ScanPhase
import hu.rsc.shelflife.scanner.ScannerAnalyzer
import hu.rsc.shelflife.ui.components.ItemHighlight
import hu.rsc.shelflife.ui.components.LocationChips
import hu.rsc.shelflife.ui.components.SwipeablePantryItemCard
import hu.rsc.shelflife.ui.dialogs.ItemEditDialog
import hu.rsc.shelflife.ui.dialogs.QuickAddDialog
import hu.rsc.shelflife.ui.dialogs.NewProductDialog
import hu.rsc.shelflife.ui.dialogs.NotificationSettingsDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

/**
 * Fokepernyo: a kamra tetelei (lejarat vagy felvitel szerint rendezve),
 * felul a rogzitesi kor kamerapanelje (ha aktiv), valamint a kezi felvitel,
 * szerkesztes es az ertesitesi beallitasok dialogusai.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(
    onShowOnboarding: () -> Unit = {},
    vm: PantryViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val items by vm.items.collectAsStateWithLifecycle()
    val knownProducts by vm.knownProducts.collectAsStateWithLifecycle()
    val notificationSettings = remember { NotificationSettingsStore(context) }
    var showNotificationSettings by remember { mutableStateOf(false) }
    var showQuickAdd by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)

    // Keresés (név szerint) és hely-szűrő.
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var locationFilter by rememberSaveable { mutableStateOf<StorageLocation?>(null) }

    // Alapertelmezetten lejarat szerint rendezunk (a hamarosan lejarok elol).
    var sortMode by rememberSaveable { mutableStateOf(ItemSortMode.BY_EXPIRY) }
    // "Becsult" szuro: csak a meg pontositando (becsult lejaratu) tetelek.
    var showOnlyEstimated by rememberSaveable { mutableStateOf(false) }
    val sortedItems by remember {
        derivedStateOf {
            filterAndSort(items, sortMode, showOnlyEstimated, locationFilter, if (searchActive) query else "")
        }
    }
    val listState = rememberLazyListState()

    val ocrDebug = remember { OcrDebugRecorder() }
    val analyzer = remember {
        ScannerAnalyzer(
            context = context,
            phaseProvider = {
                when (vm.uiState) {
                    is UiState.ScanningBarcode -> ScanPhase.SCANNING_BARCODE
                    is UiState.ScanningDate -> ScanPhase.SCANNING_DATE
                    else -> ScanPhase.PAUSED
                }
            },
            onBarcodeDetected = {
                if (vm.onBarcodeSeen(it)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDateCandidate = {
                if (vm.onDateCandidate(it)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        )
    }

    LaunchedEffect(ocrDebug.enabled) {
        if (ocrDebug.enabled) {
            ocrDebug.reset()
            analyzer.debugListener = { ocrDebug.record(it) }
        } else {
            analyzer.debugListener = null
        }
    }

    // Visszavonhato muveletek: az ujabb snackbar lecsereli a regit.
    LaunchedEffect(vm) {
        vm.undoRequests.collectLatest { request ->
            val result = snackbarHostState.showSnackbar(
                message = request.message,
                actionLabel = undoLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) vm.undo(request.action)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) query = ""
                    }) {
                        Icon(
                            if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(R.string.cd_search)
                        )
                    }
                    IconButton(onClick = { showNotificationSettings = true }) {
                        Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.cd_notification_settings))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_show_onboarding)) },
                            onClick = {
                                menuOpen = false
                                onShowOnboarding()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_feedback)) },
                            onClick = {
                                menuOpen = false
                                sendFeedback(context)
                            }
                        )
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
                if (vm.cameraSessionActive) {
                    ExtendedFloatingActionButton(
                        onClick = { vm.endScanningSession() },
                        icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_finish_session)) }
                    )
                } else {
                    ExtendedFloatingActionButton(
                        onClick = { vm.startScanningSession() },
                        icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_start_scan)) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                FloatingActionButton(onClick = { showQuickAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_manual_add))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (vm.cameraSessionActive) {
                ScanSessionPanel(
                    vm = vm,
                    analyzer = analyzer,
                    ocrDebug = ocrDebug,
                    onOpenQuickAdd = { showQuickAdd = true }
                )
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
                        stringResource(R.string.scan_idle_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            HorizontalDivider()

            if (searchActive) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.items_header, items.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = sortMode == ItemSortMode.BY_EXPIRY,
                    onClick = { sortMode = ItemSortMode.BY_EXPIRY },
                    label = { Text(stringResource(R.string.sort_expiry)) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = sortMode == ItemSortMode.BY_RECORDED,
                    onClick = { sortMode = ItemSortMode.BY_RECORDED },
                    label = { Text(stringResource(R.string.sort_recorded)) }
                )
            }

            LocationChips(
                selected = locationFilter,
                onSelect = { locationFilter = it },
                allowNone = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            val estimatedCount = items.count { it.expiryEstimated }
            // Ha elfogytak a becsult tetelek, a szuro kapcsoljon ki magatol (de ne
            // az adatbazis betoltese elotti ures listara reagaljunk).
            LaunchedEffect(estimatedCount) {
                if (estimatedCount == 0 && items.isNotEmpty()) showOnlyEstimated = false
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
                        label = { Text(stringResource(R.string.filter_estimated, estimatedCount)) }
                    )
                }
            }

            // Uj tetel utan odagorgetunk, ahova a rendezes szerint bekerult. Az
            // adatbazis-iras aszinkron, ezert megvarjuk, amig a tetel megjelenik.
            LaunchedEffect(vm.lastAddedId, sortMode) {
                val id = vm.lastAddedId ?: return@LaunchedEffect
                val idx = snapshotFlow { sortedItems.indexOfFirst { it.id == id } }.first { it >= 0 }
                listState.animateScrollToItem(idx)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (vm.cameraSessionActive) 0.45f else 1f),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(sortedItems, key = { it.id }) { item ->
                    SwipeablePantryItemCard(
                        item = item,
                        highlight = when (item.id) {
                            vm.lastAddedId -> ItemHighlight.LATEST
                            in vm.sessionAddedIds -> ItemHighlight.SESSION
                            else -> ItemHighlight.NONE
                        },
                        onEdit = { vm.openEdit(item) },
                        onConsumeOne = { vm.consumeOne(item) },
                        onConsumed = { vm.markConsumed(item) },
                        onWasted = { vm.markWasted(item) },
                        onSwapDayMonth = { vm.swapDayMonth(item) }
                    )
                }
            }
        }

        val askState = vm.uiState
        if (askState is UiState.AskingProductName) {
            NewProductDialog(
                barcode = askState.barcode,
                knownProducts = knownProducts,
                onConfirm = { name -> vm.confirmProductName(askState.barcode, name) },
                onDismiss = { vm.cancelCurrentProduct() }
            )
        }

        vm.manualEdit?.let { editState ->
            ItemEditDialog(
                editState = editState,
                knownProducts = knownProducts,
                onSave = { name, expiry, quantity, unit, estimated, location ->
                    vm.saveManualEdit(editState, name, expiry, quantity, unit, estimated, location)
                },
                onDelete = { id -> vm.deleteItem(id) },
                onDismiss = { vm.closeManualEdit() }
            )
        }

        if (showQuickAdd) {
            val resources = context.resources
            QuickAddDialog(
                onPick = { product ->
                    showQuickAdd = false
                    vm.addQuickProduct(product, resources.getString(product.nameRes))
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onOther = {
                    showQuickAdd = false
                    vm.openManualAdd()
                },
                onDismiss = { showQuickAdd = false }
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

private fun filterAndSort(
    items: List<PantryItem>,
    sortMode: ItemSortMode,
    onlyEstimated: Boolean,
    location: StorageLocation?,
    query: String
): List<PantryItem> {
    var visible = items
    if (location != null) visible = visible.filter { it.location == location }
    val q = query.trim()
    if (q.isNotEmpty()) visible = visible.filter { it.productName.contains(q, ignoreCase = true) }
    // Csak akkor szurunk a becsultekre, ha van mit: ures lista helyett a szurt teljes lista latszik.
    if (onlyEstimated) visible = visible.filter { it.expiryEstimated }.ifEmpty { visible }
    return when (sortMode) {
        ItemSortMode.BY_EXPIRY -> visible.sortedWith(compareBy<PantryItem> { it.expiry }.thenByDescending { it.id })
        // Legutobb felvett elol (az id a felvitel idopontja ms-ban).
        ItemSortMode.BY_RECORDED -> visible.sortedByDescending { it.id }
    }
}
