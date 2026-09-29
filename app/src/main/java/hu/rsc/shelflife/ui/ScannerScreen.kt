package hu.rsc.shelflife.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Size
import android.view.MotionEvent
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import hu.rsc.shelflife.R
import hu.rsc.shelflife.scanner.OcrDebugInfo
import hu.rsc.shelflife.scanner.ScannerAnalyzer
import hu.rsc.shelflife.ui.components.LocationChips
import hu.rsc.shelflife.ui.components.QuantityRow
import hu.rsc.shelflife.ui.dialogs.QuickDateDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

/**
 * Az aktiv rogzitesi kor felulete: kamerakep a feliratokkal/gombokkal, es a
 * datum-fazisban a kezi datum-/mennyiseg-vezerlok. A hivo Column-jaba
 * illeszkedik (a kamera-doboz 55%-os sulyt kap).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.ScanSessionPanel(
    vm: PantryViewModel,
    analyzer: ScannerAnalyzer,
    ocrDebug: OcrDebugRecorder,
    onOpenQuickAdd: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uiState = vm.uiState
    var hasFlashUnit by remember { mutableStateOf(false) }
    // Gyors (szamjegyes) datumbevitel dialogus a datum-fazisban.
    var showQuickDate by remember { mutableStateOf(false) }

    fun finish(date: LocalDate, estimated: Boolean) {
        showQuickDate = false
        if (vm.finishWithManualDate(date, estimated)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Ha elhagyjuk a datum-fazist (pl. az OCR kozben mentett, vagy Megse), a gyors
    // datumbevitel dialogus ne maradjon "felhuzva" a kovetkezo termekre.
    LaunchedEffect(uiState) {
        if (uiState !is UiState.ScanningDate) showQuickDate = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(0.55f)
            .padding(12.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        CameraPreview(
            analyzer = analyzer,
            torchEnabled = vm.torchOn,
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
                checked = ocrDebug.enabled,
                onCheckedChange = { on ->
                    // Kikapcsolaskor a teljes log automatikusan a vagolapra kerul.
                    if (!on && ocrDebug.log.isNotEmpty()) ocrDebug.copyToClipboard(context)
                    ocrDebug.enabled = on
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Icon(Icons.Filled.BugReport, contentDescription = stringResource(R.string.cd_ocr_debug))
            }

            if (ocrDebug.enabled) {
                OcrDebugPanel(
                    infos = ocrDebug.infos.values.sortedBy { it.variant },
                    logSize = ocrDebug.log.size,
                    onCopy = { ocrDebug.copyToClipboard(context) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, end = 8.dp, top = 60.dp)
                )
            }
        }

        if (hasFlashUnit) {
            FilledIconToggleButton(
                checked = vm.torchOn,
                onCheckedChange = { vm.torchOn = it },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    if (vm.torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = stringResource(if (vm.torchOn) R.string.cd_torch_off else R.string.cd_torch_on)
                )
            }
        }

        val dateMode = uiState is UiState.ScanningDate
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // Jobbra hely a lampa-gombnak, hogy a felirat ne takarja.
                .padding(start = 60.dp, end = 60.dp, top = 12.dp),
            color = if (dateMode) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (dateMode) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 2.dp
        ) {
            Text(
                text = when (uiState) {
                    is UiState.ScanningBarcode -> stringResource(R.string.scan_hint_barcode)
                    is UiState.ResolvingBarcode -> stringResource(R.string.scan_hint_resolving)
                    is UiState.AskingProductName -> stringResource(R.string.scan_hint_ask_name)
                    is UiState.ScanningDate -> stringResource(R.string.scan_hint_date, uiState.productName)
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        if (dateMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilledTonalButton(onClick = { finish(LocalDate.now().plusWeeks(1), estimated = true) }) {
                    Text(stringResource(R.string.estimate_week))
                }
                FilledTonalButton(onClick = { finish(LocalDate.now().plusMonths(1), estimated = true) }) {
                    Text(stringResource(R.string.estimate_month))
                }
                FilledTonalButton(onClick = { finish(LocalDate.now().plusYears(1), estimated = true) }) {
                    Text(stringResource(R.string.estimate_year))
                }
            }
        }
    }

    // A kor tetelei ide kerulnek; egyszer kell beallitani, a kor vegeig megmarad.
    LocationChips(
        selected = vm.sessionLocation,
        onSelect = { it?.let { loc -> vm.sessionLocation = loc } },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        leading = {
            if (uiState is UiState.ScanningBarcode) {
                TextButton(onClick = onOpenQuickAdd) {
                    Text(stringResource(R.string.action_no_barcode))
                }
            }
        }
    )

    if (uiState is UiState.ScanningDate) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { vm.cancelCurrentProduct() }) {
                Text(stringResource(R.string.action_cancel))
            }
            // Soha ne akadjon el a rogzites egy olvashatatlan datumon: fix becsult
            // datummal mentjuk, "becsult" jelolessel, kesobb pontosithato.
            TextButton(onClick = { finish(LocalDate.now().plusDays(DEFAULT_ESTIMATE_DAYS), estimated = true) }) {
                Text(stringResource(R.string.action_later))
            }
            OutlinedButton(onClick = { showQuickDate = true }) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_type_date))
            }
        }
        if (showQuickDate) {
            QuickDateDialog(
                calendarDefault = LocalDate.now().plusDays(DEFAULT_MANUAL_EXPIRY_DAYS),
                onDismiss = { showQuickDate = false },
                onConfirm = { finish(it, estimated = false) }
            )
        }

        QuantityRow(
            quantity = vm.quantity,
            onQuantityChange = { vm.quantity = it },
            unit = vm.quantityUnit,
            onUnitChange = { vm.quantityUnit = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )
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

    // Amikor ez a Composable kikerul a kompoziciobol (a felhasznalo lezarja a
    // rogzitesi kort), a kamerat tenylegesen el kell engedni (unbindAll).
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
                // 1080p-t kerunk, mert a datum-fazisban a kep kozepet vagjuk ki.
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

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { future ->
            future.addListener(
                { continuation.resume(future.get()) },
                ContextCompat.getMainExecutor(this)
            )
        }
    }

/**
 * Fejlesztoi OCR-diagnosztika: mit olvas ki az ML Kit az egyes kepvariansokbol.
 * Teljes szoveges log, kikapcsolaskor / gombra vagolapra kerul.
 */
@Stable
class OcrDebugRecorder {
    var enabled by mutableStateOf(false)
    val infos = mutableStateMapOf<String, OcrDebugInfo>()
    val log = mutableStateListOf<String>()
    private val lastRaw = mutableMapOf<String, String>()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun reset() {
        infos.clear()
        log.clear()
        lastRaw.clear()
    }

    fun record(info: OcrDebugInfo) {
        infos[info.variant] = info
        // Csak akkor naplozunk, ha az adott varians kimenete valtozott (kulonben 5 sor/mp).
        val key = info.rawText + "#" + info.parsed
        if (lastRaw[info.variant] != key) {
            lastRaw[info.variant] = key
            log.add("${LocalTime.now().format(timeFormatter)} ${info.variant} => ${info.parsed ?: "-"} | \"${info.rawText}\"")
            if (log.size > 500) log.removeAt(0)
        }
    }

    fun copyToClipboard(context: Context) {
        // A fejlec fejlesztoi celu, nem forditjuk.
        val header = "ShelfLife OCR debug | ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})" +
            " | frame: ${infos.values.firstOrNull()?.frameSize ?: "?"}" +
            " | ${log.size} entries"
        val text = (listOf(header) + log).joinToString("\n")
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("ShelfLife OCR debug", text))
        Toast.makeText(context, context.getString(R.string.ocr_log_copied, log.size), Toast.LENGTH_SHORT).show()
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
            text = stringResource(R.string.ocr_debug_status, logSize) + " | " + (
                hit?.let { stringResource(R.string.ocr_debug_hit, it.parsed.toString()) }
                    ?: stringResource(R.string.ocr_debug_no_hit)
                ),
            color = if (hit != null) Color(0xFF8BC34A) else Color.White,
            fontSize = 11.sp,
            maxLines = 1
        )
        TextButton(onClick = onCopy) {
            Text(stringResource(R.string.action_copy), fontSize = 11.sp)
        }
    }
}
